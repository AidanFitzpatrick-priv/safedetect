package com.safedetect.agent;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Reads Minecraft through reflection so the agent never links against game classes and Lunar can stay in
 * its normal (non-Forge) mode. Member names are the ones Lunar's normal-mode mapping resolves to at runtime.
 */
final class Game {
    static final int HELD_NONE = 0;
    static final int HELD_SWORD = 1;
    static final int HELD_BLOCK = 2;
    static final int HELD_CONSUMABLE = 3;
    static final int HELD_OTHER = 4;

    private final List<String> missing = new ArrayList<String>();
    private final ClassLoader loader;
    private static volatile ClassLoader gameLoader;

    private final Field theMinecraft;
    private final Field thePlayer;
    private final Field theWorld;
    private final Field dataDir;
    private final Field playerEntities;
    private final Method addScheduledTask;
    private final Method callingFromMinecraftThread;

    private final Field posX;
    private final Field posY;
    private final Field posZ;
    private final Field onGround;
    private final Field rotationPitch;
    private final Field rotationYaw;
    private final Field rotationYawHead;
    private final Field hurtTime;
    private final Field isInWeb;
    private final Method isInWater;
    private final Method isInLava;
    private final Method isOnLadder;
    private final Method isInvisible;
    private final Object jumpBoost;
    private final Method getIdFromItem;
    private final Field isDead;
    private final Field worldObj;
    private final Field ridingEntity;
    private final Field ticksExisted;
    private final Method getUniqueID;
    private final Method getName;
    private final Method isSneaking;
    private final Method isSprinting;

    private final Field isSwingInProgress;
    private final Field swingProgressInt;
    private final Method getActivePotionEffect;
    private final Object moveSpeed;
    private final Method getAmplifier;

    private final Field inventory;
    private final Field mainInventory;
    private final Field armorInventory;
    private final Field currentItem;
    private final Field capabilities;
    private final Field isCreativeMode;
    private final Method isUsingItem;

    private final Method getItem;
    private final Class<?> itemSword;
    private final Class<?> itemBlock;
    private final Class<?> itemFood;
    private final Class<?> itemPotion;
    private final Class<?> itemMilk;
    private final Class<?> itemArmor;
    private final Method getArmorMaterial;
    private final Method getColor;
    private final Object leather;

    private final Constructor<?> chatText;
    private final Method addChatMessage;
    private final Method sendChatMessage;
    private final Method playSound;
    private final Class<?> minecraftClass;

    private Field currentScreen;
    private Field ingameGUI;
    private Method getChatGUI;
    private Method getSentMessages;
    private Field chatInputField;
    private Method textFieldGetText;
    private Method textFieldSetText;
    private Class<?> guiChat;
    private Method getChatStyle;
    private Method setChatStyle;
    private Constructor<?> chatStyleCtor;
    private Method setChatClickEvent;
    private Method setChatHoverEvent;
    private Method getChatClickEvent;
    private Method getSiblings;
    private Method getUnformattedTextForChat;
    private Method setInsertion;
    private Constructor<?> clickEventCtor;
    private Constructor<?> hoverEventCtor;
    private Object suggestCommand;
    private Object showText;
    private Method getNetHandler;
    private Method getPlayerInfoMap;
    private Method getGameProfile;
    private Method getDisplayName;
    private Method setDisplayName;
    private Method getUnformattedText;
    private Method profileId;
    private Method profileName;
    private Method profileGetProperties;
    private Method propertyGetValue;
    private Field chatLines;
    private Method chatLineComponent;
    private Method getPlayerTeam;
    private Method getColorPrefix;
    private Method getColorSuffix;
    private Method appendSibling;
    private Method createCopy;
    private Method displayTitle;
    private boolean uiResolved;
    private Constructor<?> blockPosCtor;
    private Constructor<?> vec3Ctor;
    private Method getBlockState;
    private Method getBlock;
    private Method rayTraceBlocks;
    private Field mopBlockPos;
    private Method posXGet;
    private Method posYGet;
    private Method posZGet;
    private Object bedBlock;
    private boolean worldResolved;

    /**
     * Lunar's Minecraft lives on a custom loader. JNI FindClass from our worker/AWT threads uses the
     * thread context loader, so pin it to the game or those attaches report no instance.
     */
    static void useGameLoader() {
        useGameLoader(Thread.currentThread());
    }

    static void useGameLoader(Thread thread) {
        ClassLoader next = gameLoader;
        if (thread != null && next != null) {
            thread.setContextClassLoader(next);
        }
    }

    private Game(Class<?> minecraft) {
        minecraftClass = minecraft;
        loader = minecraft.getClassLoader();
        gameLoader = loader;
        Class<?> world = type("net.minecraft.world.World");
        Class<?> entity = type("net.minecraft.entity.Entity");
        Class<?> living = type("net.minecraft.entity.EntityLivingBase");
        Class<?> player = type("net.minecraft.entity.player.EntityPlayer");
        Class<?> playerInventory = type("net.minecraft.entity.player.InventoryPlayer");
        Class<?> playerCapabilities = type("net.minecraft.entity.player.PlayerCapabilities");
        Class<?> itemStack = type("net.minecraft.item.ItemStack");
        Class<?> item = type("net.minecraft.item.Item");
        Class<?> potion = type("net.minecraft.potion.Potion");
        Class<?> potionEffect = type("net.minecraft.potion.PotionEffect");
        Class<?> chatComponent = type("net.minecraft.util.IChatComponent");
        Class<?> chatComponentText = type("net.minecraft.util.ChatComponentText");
        Class<?> playerSp = type("net.minecraft.client.entity.EntityPlayerSP");
        itemSword = type("net.minecraft.item.ItemSword");
        itemBlock = type("net.minecraft.item.ItemBlock");
        itemFood = type("net.minecraft.item.ItemFood");
        itemPotion = type("net.minecraft.item.ItemPotion");
        itemMilk = type("net.minecraft.item.ItemBucketMilk");
        itemArmor = type("net.minecraft.item.ItemArmor");
        Class<?> armorMaterial = type("net.minecraft.item.ItemArmor$ArmorMaterial");

        theMinecraft = field(minecraft, "theMinecraft");
        thePlayer = field(minecraft, "thePlayer");
        theWorld = field(minecraft, "theWorld");
        dataDir = field(minecraft, "mcDataDir");
        addScheduledTask = method(minecraft, "addScheduledTask", Runnable.class);
        callingFromMinecraftThread = method(minecraft, "isCallingFromMinecraftThread");
        playerEntities = field(world, "playerEntities");

        posX = field(entity, "posX");
        posY = field(entity, "posY");
        posZ = field(entity, "posZ");
        onGround = field(entity, "onGround");
        rotationPitch = field(entity, "rotationPitch");
        rotationYaw = field(entity, "rotationYaw");
        rotationYawHead = field(living, "rotationYawHead");
        hurtTime = field(living, "hurtTime");
        isInWeb = field(entity, "isInWeb");
        isInWater = method(entity, "isInWater");
        isInLava = method(entity, "isInLava");
        isOnLadder = method(living, "isOnLadder");
        isInvisible = method(entity, "isInvisible");
        jumpBoost = staticValue(potion, "jump");
        getIdFromItem = method(item, "getIdFromItem", item);
        isDead = field(entity, "isDead");
        worldObj = field(entity, "worldObj");
        ridingEntity = field(entity, "ridingEntity");
        ticksExisted = field(entity, "ticksExisted");
        getUniqueID = method(entity, "getUniqueID");
        getName = method(entity, "getName");
        isSneaking = method(entity, "isSneaking");
        isSprinting = method(entity, "isSprinting");

        isSwingInProgress = field(living, "isSwingInProgress");
        swingProgressInt = field(living, "swingProgressInt");
        getActivePotionEffect = method(living, "getActivePotionEffect", potion);
        moveSpeed = staticValue(potion, "moveSpeed");
        getAmplifier = method(potionEffect, "getAmplifier");

        inventory = field(player, "inventory");
        capabilities = field(player, "capabilities");
        isUsingItem = method(player, "isUsingItem");
        mainInventory = field(playerInventory, "mainInventory");
        armorInventory = field(playerInventory, "armorInventory");
        currentItem = field(playerInventory, "currentItem");
        isCreativeMode = field(playerCapabilities, "isCreativeMode");

        getItem = method(itemStack, "getItem");
        getArmorMaterial = method(itemArmor, "getArmorMaterial");
        getColor = method(itemArmor, "getColor", itemStack);
        leather = staticValue(armorMaterial, "LEATHER");

        chatText = constructor(chatComponentText, String.class);
        addChatMessage = method(playerSp, "addChatMessage", chatComponent);
        sendChatMessage = method(playerSp, "sendChatMessage", String.class);
        playSound = method(entity, "playSound", String.class, float.class, float.class);
    }

    /**
     * Returns null until the game has loaded WorldClient, which happens on the first world join. Waiting that
     * long means reflection here never makes Lunar's class loader load game classes during startup.
     */
    static Game find(Instrumentation instrumentation) {
        Class<?> minecraft = null;
        boolean worldLoaded = false;
        for (Class<?> loaded : instrumentation.getAllLoadedClasses()) {
            String name = loaded.getName();
            if ("net.minecraft.client.Minecraft".equals(name)) {
                minecraft = loaded;
            } else if ("net.minecraft.client.multiplayer.WorldClient".equals(name)) {
                worldLoaded = true;
            }
        }
        return minecraft != null && worldLoaded ? new Game(minecraft) : null;
    }

    boolean usable() {
        return theMinecraft != null && thePlayer != null && theWorld != null && playerEntities != null
                && addScheduledTask != null && worldObj != null && posX != null && posZ != null
                && getUniqueID != null && getName != null && ticksExisted != null;
    }

    List<String> missing() {
        return missing;
    }

    Object minecraft() throws Exception {
        return theMinecraft.get(null);
    }

    boolean schedule(Runnable task) throws Exception {
        Object mc = minecraft();
        if (mc == null) {
            return false;
        }
        addScheduledTask.invoke(mc, task);
        return true;
    }

    boolean onClientThread(Object mc) throws Exception {
        return callingFromMinecraftThread == null || Boolean.TRUE.equals(callingFromMinecraftThread.invoke(mc));
    }

    Object player(Object mc) throws Exception {
        return thePlayer.get(mc);
    }

    Object world(Object mc) throws Exception {
        return theWorld.get(mc);
    }

    File dataDir(Object mc) throws Exception {
        Object dir = dataDir == null ? null : dataDir.get(mc);
        return dir instanceof File ? (File) dir : null;
    }

    Object[] players(Object world) throws Exception {
        Object list = playerEntities.get(world);
        return list instanceof List ? ((List<?>) list).toArray() : new Object[0];
    }

    double posX(Object entity) throws Exception {
        return posX.getDouble(entity);
    }

    double posZ(Object entity) throws Exception {
        return posZ.getDouble(entity);
    }

    boolean onGround(Object entity) throws Exception {
        return onGround != null && onGround.getBoolean(entity);
    }

    float pitch(Object entity) throws Exception {
        return rotationPitch == null ? 0.0f : rotationPitch.getFloat(entity);
    }

    boolean dead(Object entity) throws Exception {
        return isDead != null && isDead.getBoolean(entity);
    }

    Object worldOf(Object entity) throws Exception {
        return worldObj.get(entity);
    }

    boolean riding(Object entity) throws Exception {
        return ridingEntity != null && ridingEntity.get(entity) != null;
    }

    int ticksExisted(Object entity) throws Exception {
        return ticksExisted.getInt(entity);
    }

    UUID uuid(Object entity) throws Exception {
        Object id = getUniqueID.invoke(entity);
        return id instanceof UUID ? (UUID) id : null;
    }

    String name(Object entity) throws Exception {
        Object name = getName.invoke(entity);
        return name instanceof String ? (String) name : null;
    }

    boolean sneaking(Object entity) throws Exception {
        return isSneaking != null && Boolean.TRUE.equals(isSneaking.invoke(entity));
    }

    boolean sprinting(Object entity) throws Exception {
        return isSprinting != null && Boolean.TRUE.equals(isSprinting.invoke(entity));
    }

    boolean swinging(Object entity) throws Exception {
        return isSwingInProgress != null && isSwingInProgress.getBoolean(entity);
    }

    int swingProgressInt(Object entity) throws Exception {
        return swingProgressInt == null ? 0 : swingProgressInt.getInt(entity);
    }

    boolean usingItem(Object player) throws Exception {
        return isUsingItem != null && Boolean.TRUE.equals(isUsingItem.invoke(player));
    }

    double posY(Object entity) throws Exception {
        return posY == null ? 0.0 : posY.getDouble(entity);
    }

    float yaw(Object entity) throws Exception {
        return rotationYaw == null ? 0.0f : rotationYaw.getFloat(entity);
    }

    float headYaw(Object entity) throws Exception {
        return rotationYawHead == null ? yaw(entity) : rotationYawHead.getFloat(entity);
    }

    int hurtTime(Object entity) throws Exception {
        return hurtTime == null ? 0 : hurtTime.getInt(entity);
    }

    /** Water, lava, ladders, vines and webs all let players hover or climb legitimately. */
    boolean climbingOrSwimming(Object entity) throws Exception {
        return (isInWater != null && Boolean.TRUE.equals(isInWater.invoke(entity)))
                || (isInLava != null && Boolean.TRUE.equals(isInLava.invoke(entity)))
                || (isOnLadder != null && Boolean.TRUE.equals(isOnLadder.invoke(entity)))
                || (isInWeb != null && isInWeb.getBoolean(entity));
    }

    boolean invisible(Object entity) throws Exception {
        return isInvisible != null && Boolean.TRUE.equals(isInvisible.invoke(entity));
    }

    /** False when movement members are missing, so Speed and Tower stay off rather than guess. */
    boolean canCheckMovement() {
        return posY != null && isInWater != null && isOnLadder != null && hurtTime != null;
    }

    boolean canReadItemIds() {
        return getIdFromItem != null;
    }

    /** Item id in a hotbar slot, or -1 when empty or unknown. */
    int hotbarItemId(Object player, int slot) throws Exception {
        if (getIdFromItem == null || inventory == null || mainInventory == null) {
            return -1;
        }
        Object inv = inventory.get(player);
        Object slots = inv == null ? null : mainInventory.get(inv);
        if (!(slots instanceof Object[]) || slot >= ((Object[]) slots).length) {
            return -1;
        }
        Object item = item(((Object[]) slots)[slot]);
        if (item == null) {
            return -1;
        }
        Object id = getIdFromItem.invoke(null, item);
        return id instanceof Number ? ((Number) id).intValue() : -1;
    }

    int jumpAmplifier(Object player) throws Exception {
        return amplifier(player, jumpBoost);
    }

    /** Amplifier of the Speed effect, or -1 without one. */
    int speedAmplifier(Object player) throws Exception {
        return amplifier(player, moveSpeed);
    }

    private int amplifier(Object player, Object potion) throws Exception {
        if (getActivePotionEffect == null || potion == null || getAmplifier == null) {
            return -1;
        }
        Object effect = getActivePotionEffect.invoke(player, potion);
        if (effect == null) {
            return -1;
        }
        Object amplifier = getAmplifier.invoke(effect);
        return amplifier instanceof Number ? ((Number) amplifier).intValue() : 0;
    }

    int held(Object player) throws Exception {
        Object item = item(heldStack(player));
        if (item == null) {
            return HELD_NONE;
        }
        if (itemSword != null && itemSword.isInstance(item)) {
            return HELD_SWORD;
        }
        if (itemBlock != null && itemBlock.isInstance(item)) {
            return HELD_BLOCK;
        }
        if ((itemFood != null && itemFood.isInstance(item)) || (itemPotion != null && itemPotion.isInstance(item))
                || (itemMilk != null && itemMilk.isInstance(item))) {
            return HELD_CONSUMABLE;
        }
        return HELD_OTHER;
    }

    boolean creative(Object player) throws Exception {
        if (capabilities == null || isCreativeMode == null) {
            return false;
        }
        Object caps = capabilities.get(player);
        return caps != null && isCreativeMode.getBoolean(caps);
    }

    /** Leather chestplate color, 0 without one. Undyed leather counts as 1 so it still pairs teammates. */
    int armorColor(Object player) throws Exception {
        if (armorInventory == null || inventory == null || getArmorMaterial == null || getColor == null || leather == null) {
            return 0;
        }
        Object inv = inventory.get(player);
        if (inv == null) {
            return 0;
        }
        Object slots = armorInventory.get(inv);
        if (!(slots instanceof Object[]) || ((Object[]) slots).length <= 2) {
            return 0;
        }
        Object stack = ((Object[]) slots)[2];
        Object item = item(stack);
        if (item == null || itemArmor == null || !itemArmor.isInstance(item)) {
            return 0;
        }
        if (getArmorMaterial.invoke(item) != leather) {
            return 0;
        }
        Object color = getColor.invoke(item, stack);
        int value = color instanceof Number ? ((Number) color).intValue() : 0;
        return value == 0 ? 1 : value;
    }

    void playSound(Object player, String name, float volume, float pitch) throws Exception {
        if (player == null || playSound == null || name == null) {
            return;
        }
        playSound.invoke(player, name, Float.valueOf(volume), Float.valueOf(pitch));
    }

    void chat(Object player, String text) throws Exception {
        if (player == null || chatText == null || addChatMessage == null) {
            return;
        }
        addChatMessage.invoke(player, chatText.newInstance(text));
    }

    /**
     * Same as {@link #chat} but the line can be clicked (with chat open) to copy the plain text.
     * 1.8 has no clipboard click action, so this uses suggest-command plus insertion; {@link Detector}
     * then copies when that text appears in the chat box.
     */
    void chatClickable(Object player, String shown, String copyText, String hover) throws Exception {
        resolveUi();
        if (player == null || chatText == null || addChatMessage == null) {
            return;
        }
        Object component = chatText.newInstance(shown);
        if (suggestCommand != null && clickEventCtor != null && getChatStyle != null && setChatClickEvent != null) {
            Object style = getChatStyle.invoke(component);
            if (style == null && chatStyleCtor != null && setChatStyle != null) {
                style = chatStyleCtor.newInstance();
                setChatStyle.invoke(component, style);
            }
            if (style != null) {
                setChatClickEvent.invoke(style, clickEventCtor.newInstance(suggestCommand, copyText));
                if (setInsertion != null) {
                    setInsertion.invoke(style, copyText);
                }
                if (setChatHoverEvent != null && hoverEventCtor != null && showText != null && hover != null) {
                    setChatHoverEvent.invoke(style, hoverEventCtor.newInstance(showText, chatText.newInstance(hover)));
                }
            }
        }
        addChatMessage.invoke(player, component);
    }

    void sendChat(Object player, String message) throws Exception {
        if (player == null || sendChatMessage == null || message == null || message.isEmpty()) {
            return;
        }
        sendChatMessage.invoke(player, message);
    }

    /** Received chat, newest first: vanilla inserts at index 0 and trims the list to 100 lines. */
    Object[] chatLines(Object mc) {
        try {
            resolveUi();
            if (ingameGUI == null || getChatGUI == null || chatLines == null || mc == null) {
                return new Object[0];
            }
            Object gui = ingameGUI.get(mc);
            Object chat = gui == null ? null : getChatGUI.invoke(gui);
            Object lines = chat == null ? null : chatLines.get(chat);
            return lines instanceof java.util.List ? ((java.util.List<?>) lines).toArray() : new Object[0];
        } catch (Throwable thrown) {
            Log.once("received chat", thrown);
            return new Object[0];
        }
    }

    String chatLineText(Object line) {
        try {
            if (line == null || chatLineComponent == null) {
                return null;
            }
            return unformatted(chatLineComponent.invoke(line));
        } catch (Throwable thrown) {
            Log.once("chat line", thrown);
            return null;
        }
    }

    Object chatLineComponent(Object line) {
        try {
            return line == null || chatLineComponent == null ? null : chatLineComponent.invoke(line);
        } catch (Throwable thrown) {
            Log.once("chat component", thrown);
            return null;
        }
    }

    /** This component and every nested sibling, depth-first. */
    void walkChat(Object component, java.util.List<Object> out) {
        if (component == null || out == null) {
            return;
        }
        out.add(component);
        if (getSiblings == null) {
            return;
        }
        try {
            Object siblings = getSiblings.invoke(component);
            if (siblings instanceof java.util.List) {
                for (Object sibling : (java.util.List<?>) siblings) {
                    walkChat(sibling, out);
                }
            }
        } catch (Throwable thrown) {
            Log.once("chat siblings", thrown);
        }
    }

    String chatOwnText(Object component) {
        try {
            if (component == null) {
                return null;
            }
            if (getUnformattedTextForChat != null) {
                Object text = getUnformattedTextForChat.invoke(component);
                return text instanceof String ? (String) text : null;
            }
            return unformatted(component);
        } catch (Throwable thrown) {
            Log.once("chat own text", thrown);
            return null;
        }
    }

    boolean hasClick(Object component) {
        try {
            Object style = chatStyle(component);
            if (style == null || getChatClickEvent == null) {
                return false;
            }
            return getChatClickEvent.invoke(style) != null;
        } catch (Throwable thrown) {
            return false;
        }
    }

    void setHoverText(Object component, String hover) {
        try {
            if (component == null || hover == null || chatText == null || setChatHoverEvent == null
                    || hoverEventCtor == null || showText == null) {
                return;
            }
            Object style = ensureStyle(component);
            if (style == null) {
                return;
            }
            setChatHoverEvent.invoke(style, hoverEventCtor.newInstance(showText, chatText.newInstance(hover)));
        } catch (Throwable thrown) {
            Log.once("chat hover", thrown);
        }
    }

    void setClickSuggest(Object component, String copyText) {
        try {
            if (component == null || copyText == null || hasClick(component) || suggestCommand == null
                    || clickEventCtor == null || setChatClickEvent == null) {
                return;
            }
            Object style = ensureStyle(component);
            if (style == null) {
                return;
            }
            setChatClickEvent.invoke(style, clickEventCtor.newInstance(suggestCommand, copyText));
            if (setInsertion != null) {
                setInsertion.invoke(style, copyText);
            }
        } catch (Throwable thrown) {
            Log.once("chat click", thrown);
        }
    }

    private Object chatStyle(Object component) throws Exception {
        return component == null || getChatStyle == null ? null : getChatStyle.invoke(component);
    }

    private Object ensureStyle(Object component) throws Exception {
        Object style = chatStyle(component);
        if (style == null && chatStyleCtor != null && setChatStyle != null) {
            style = chatStyleCtor.newInstance();
            setChatStyle.invoke(component, style);
        }
        return style;
    }

    List<?> sentChat(Object mc) throws Exception {
        resolveUi();
        if (ingameGUI == null || getChatGUI == null || getSentMessages == null || mc == null) {
            return null;
        }
        Object gui = ingameGUI.get(mc);
        Object chat = gui == null ? null : getChatGUI.invoke(gui);
        Object messages = chat == null ? null : getSentMessages.invoke(chat);
        return messages instanceof List ? (List<?>) messages : null;
    }

    String chatBoxText(Object mc) throws Exception {
        resolveUi();
        Object field = chatInput(mc);
        if (field == null || textFieldGetText == null) {
            return null;
        }
        Object text = textFieldGetText.invoke(field);
        return text instanceof String ? (String) text : null;
    }

    void setChatBoxText(Object mc, String text) throws Exception {
        resolveUi();
        Object field = chatInput(mc);
        if (field != null && textFieldSetText != null) {
            textFieldSetText.invoke(field, text);
        }
    }

    boolean canUseTab() {
        resolveUi();
        return getNetHandler != null && getPlayerInfoMap != null && getGameProfile != null && profileId != null
                && profileName != null && setDisplayName != null && getDisplayName != null && chatText != null;
    }

    Object[] tabEntries(Object mc) throws Exception {
        resolveUi();
        if (!canUseTab() || mc == null) {
            return new Object[0];
        }
        Object handler = getNetHandler.invoke(mc);
        Object map = handler == null ? null : getPlayerInfoMap.invoke(handler);
        if (!(map instanceof java.util.Collection)) {
            return new Object[0];
        }
        return ((java.util.Collection<?>) map).toArray();
    }

    java.util.UUID tabUuid(Object info) throws Exception {
        Object profile = getGameProfile.invoke(info);
        Object id = profile == null ? null : profileId.invoke(profile);
        return id instanceof java.util.UUID ? (java.util.UUID) id : null;
    }

    String tabName(Object info) throws Exception {
        Object profile = getGameProfile.invoke(info);
        Object name = profile == null ? null : profileName.invoke(profile);
        return name instanceof String ? (String) name : null;
    }

    String tabTextureHash(Object info) {
        try {
            if (profileGetProperties == null || info == null) {
                return null;
            }
            Object profile = getGameProfile.invoke(info);
            if (profile == null) {
                return null;
            }
            Object props = profileGetProperties.invoke(profile);
            Object textures = invokeKeyed(props, "textures");
            Object property = firstOf(textures);
            if (property == null) {
                return null;
            }
            Object value = propertyGetValue != null ? propertyGetValue.invoke(property) : invokeKeyed(property, "value");
            return value instanceof String ? Denick.hashFromTextures((String) value) : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object invokeKeyed(Object target, String key) {
        if (target == null) {
            return null;
        }
        try {
            java.lang.reflect.Method get = target.getClass().getMethod("get", Object.class);
            return get.invoke(target, key);
        } catch (Throwable ignored) {
        }
        try {
            java.lang.reflect.Method get = target.getClass().getMethod("get", String.class);
            return get.invoke(target, key);
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Object firstOf(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.util.Collection) {
            java.util.Iterator<?> it = ((java.util.Collection<?>) value).iterator();
            return it.hasNext() ? it.next() : null;
        }
        if (value instanceof Object[]) {
            Object[] array = (Object[]) value;
            return array.length == 0 ? null : array[0];
        }
        return value;
    }

    /**
     * Colour code char of the tab entry's scoreboard team prefix, or the nearest chat colour of the
     * player's leather chestplate when there is no coloured team. Empty when neither is known.
     */
    String tabTeamColor(Object info, Object entity) {
        try {
            resolveUi();
            if (info != null && getPlayerTeam != null && getColorPrefix != null) {
                Object team = getPlayerTeam.invoke(info);
                Object prefix = team == null ? null : getColorPrefix.invoke(team);
                char code = firstColorCode(prefix instanceof String ? (String) prefix : null);
                if (code != 0) {
                    return String.valueOf(code);
                }
            }
            if (entity != null) {
                int rgb = armorColor(entity);
                if (rgb > 1) {
                    return String.valueOf(nearestColorCode(rgb));
                }
            }
        } catch (Throwable thrown) {
            Log.once("team colour", thrown);
        }
        return "";
    }

    private static final String COLOR_CODES = "0123456789abcdef";
    static final int[] CHAT_RGB = { 0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF };

    /** First colour (not format) code in a section-sign formatted string, or 0. */
    static char firstColorCode(String text) {
        if (text == null) {
            return 0;
        }
        for (int i = 0; i + 1 < text.length(); i++) {
            if (text.charAt(i) == '\u00a7') {
                char c = Character.toLowerCase(text.charAt(i + 1));
                if (COLOR_CODES.indexOf(c) >= 0) {
                    return c;
                }
            }
        }
        return 0;
    }

    /** Nearest of the 16 chat colours, skipping black and the greys that rarely mark a team. */
    static char nearestColorCode(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int best = 15;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < CHAT_RGB.length; i++) {
            int c = CHAT_RGB[i];
            long dr = r - ((c >> 16) & 0xFF);
            long dg = g - ((c >> 8) & 0xFF);
            long db = b - (c & 0xFF);
            long distance = dr * dr + dg * dg + db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return COLOR_CODES.charAt(best);
    }

    /** Big on-screen title through GuiIngame.displayTitle; does nothing when it cannot be resolved. */
    void title(Object mc, String title, String subtitle) {
        try {
            resolveUi();
            if (mc == null || ingameGUI == null || displayTitle == null) {
                return;
            }
            Object gui = ingameGUI.get(mc);
            if (gui == null) {
                return;
            }
            displayTitle.invoke(gui, null, subtitle, Integer.valueOf(5), Integer.valueOf(40), Integer.valueOf(10));
            displayTitle.invoke(gui, title, null, Integer.valueOf(5), Integer.valueOf(40), Integer.valueOf(10));
        } catch (Throwable thrown) {
            Log.once("title", thrown);
        }
    }

    Object tabDisplayName(Object info) throws Exception {
        return getDisplayName.invoke(info);
    }

    void setTabDisplayName(Object info, Object component) throws Exception {
        setDisplayName.invoke(info, component);
    }

    Object textComponent(String text) throws Exception {
        return chatText == null ? null : chatText.newInstance(text);
    }

    /**
     * Tab overlay uses {@code displayName} when set, otherwise the scoreboard team prefix/suffix.
     * Keep that colour on the name and only append the flag mark after a reset.
     */
    Object markedTabName(Object info, String name, String mark, String tags, Object original) throws Exception {
        resolveUi();
        if (original != null && createCopy != null && appendSibling != null) {
            try {
                Object copy = createCopy.invoke(original);
                Object extra = textComponent((mark == null ? "" : mark) + (tags == null ? "" : tags));
                if (copy != null && extra != null) {
                    appendSibling.invoke(copy, extra);
                    return copy;
                }
            } catch (Throwable thrown) {
                Log.once("tab copy", thrown);
            }
        }
        return textComponent(teamDecorated(info, name) + (mark == null ? "" : mark) + (tags == null ? "" : tags));
    }

    /** Scoreboard team colour codes around a raw name, or the name alone. */
    String teamDecorated(Object info, String name) {
        String prefix = "";
        String suffix = "";
        try {
            resolveUi();
            if (info != null && getPlayerTeam != null) {
                Object team = getPlayerTeam.invoke(info);
                if (team != null) {
                    if (getColorPrefix != null) {
                        Object value = getColorPrefix.invoke(team);
                        if (value instanceof String) {
                            prefix = (String) value;
                        }
                    }
                    if (getColorSuffix != null) {
                        Object value = getColorSuffix.invoke(team);
                        if (value instanceof String) {
                            suffix = (String) value;
                        }
                    }
                }
            }
        } catch (Throwable thrown) {
            Log.once("tab team", thrown);
        }
        return prefix + (name == null ? "" : name) + suffix;
    }

    String unformatted(Object component) throws Exception {
        if (component == null || getUnformattedText == null) {
            return null;
        }
        Object text = getUnformattedText.invoke(component);
        return text instanceof String ? (String) text : null;
    }

    boolean canScanBlocks() {
        resolveWorld();
        return getBlockState != null && getBlock != null && blockPosCtor != null && bedBlock != null;
    }

    boolean isBed(Object world, int x, int y, int z) {
        Object block = blockAt(world, x, y, z);
        return block != null && block == bedBlock;
    }

    /**
     * True when a ray from the eyes to the bed hits some other block first — mining the bed through cover.
     */
    boolean throughBlocks(Object world, double eyeX, double eyeY, double eyeZ, int x, int y, int z) {
        resolveWorld();
        if (world == null || rayTraceBlocks == null || vec3Ctor == null || mopBlockPos == null) {
            return false;
        }
        try {
            Object start = vec3Ctor.newInstance(eyeX, eyeY, eyeZ);
            Object end = vec3Ctor.newInstance(x + 0.5, y + 0.5, z + 0.5);
            Object hit = rayTraceBlocks.invoke(world, start, end);
            if (hit == null) {
                return false;
            }
            Object pos = mopBlockPos.get(hit);
            if (pos == null || posXGet == null) {
                return true;
            }
            int hx = ((Number) posXGet.invoke(pos)).intValue();
            int hy = ((Number) posYGet.invoke(pos)).intValue();
            int hz = ((Number) posZGet.invoke(pos)).intValue();
            return hx != x || hy != y || hz != z;
        } catch (Throwable thrown) {
            Log.once("raytrace", thrown);
            return false;
        }
    }

    private Object blockAt(Object world, int x, int y, int z) {
        resolveWorld();
        if (world == null || getBlockState == null || getBlock == null || blockPosCtor == null) {
            return null;
        }
        try {
            Object state = getBlockState.invoke(world, blockPosCtor.newInstance(x, y, z));
            return state == null ? null : getBlock.invoke(state);
        } catch (Throwable thrown) {
            Log.once("getBlock", thrown);
            return null;
        }
    }

    private void resolveWorld() {
        if (worldResolved) {
            return;
        }
        worldResolved = true;
        Class<?> world = type("net.minecraft.world.World");
        Class<?> blockPos = type("net.minecraft.util.BlockPos");
        Class<?> vec3 = type("net.minecraft.util.Vec3");
        Class<?> vec3i = type("net.minecraft.util.Vec3i");
        Class<?> state = type("net.minecraft.block.state.IBlockState");
        Class<?> mop = type("net.minecraft.util.MovingObjectPosition");
        Class<?> blocks = type("net.minecraft.init.Blocks");
        blockPosCtor = constructor(blockPos, int.class, int.class, int.class);
        vec3Ctor = constructor(vec3, double.class, double.class, double.class);
        getBlockState = method(world, "getBlockState", blockPos);
        getBlock = method(state, "getBlock");
        rayTraceBlocks = method(world, "rayTraceBlocks", vec3, vec3);
        mopBlockPos = field(mop, "blockPos");
        posXGet = method(blockPos, "getX");
        if (posXGet == null) {
            posXGet = method(vec3i, "getX");
        }
        posYGet = method(blockPos, "getY");
        if (posYGet == null) {
            posYGet = method(vec3i, "getY");
        }
        posZGet = method(blockPos, "getZ");
        if (posZGet == null) {
            posZGet = method(vec3i, "getZ");
        }
        bedBlock = staticValue(blocks, "bed");
    }

    private Object chatInput(Object mc) throws Exception {
        if (currentScreen == null || guiChat == null || chatInputField == null || mc == null) {
            return null;
        }
        Object screen = currentScreen.get(mc);
        if (screen == null || !guiChat.isInstance(screen)) {
            return null;
        }
        return chatInputField.get(screen);
    }

    private void resolveUi() {
        if (uiResolved) {
            return;
        }
        uiResolved = true;
        Class<?> ingame = type("net.minecraft.client.gui.GuiIngame");
        Class<?> newChat = type("net.minecraft.client.gui.GuiNewChat");
        guiChat = type("net.minecraft.client.gui.GuiChat");
        Class<?> textField = type("net.minecraft.client.gui.GuiTextField");
        Class<?> chatStyle = type("net.minecraft.util.ChatStyle");
        Class<?> chatComponent = type("net.minecraft.util.IChatComponent");
        Class<?> clickEvent = type("net.minecraft.event.ClickEvent");
        Class<?> clickAction = type("net.minecraft.event.ClickEvent$Action");
        Class<?> hoverEvent = type("net.minecraft.event.HoverEvent");
        Class<?> hoverAction = type("net.minecraft.event.HoverEvent$Action");
        Class<?> netHandler = type("net.minecraft.client.network.NetHandlerPlayClient");
        Class<?> playerInfo = type("net.minecraft.client.network.NetworkPlayerInfo");
        Class<?> gameProfile = type("com.mojang.authlib.GameProfile");

        currentScreen = field(minecraftClass, "currentScreen");
        ingameGUI = field(minecraftClass, "ingameGUI");
        getChatGUI = method(ingame, "getChatGUI");
        getSentMessages = method(newChat, "getSentMessages");
        Class<?> chatLine = type("net.minecraft.client.gui.ChatLine");
        chatLines = field(newChat, "chatLines");
        chatLineComponent = method(chatLine, "getChatComponent");
        chatInputField = field(guiChat, "inputField");
        textFieldGetText = method(textField, "getText");
        textFieldSetText = method(textField, "setText", String.class);
        getChatStyle = method(chatComponent, "getChatStyle");
        setChatStyle = method(chatComponent, "setChatStyle", chatStyle);
        chatStyleCtor = constructor(chatStyle);
        setChatClickEvent = method(chatStyle, "setChatClickEvent", clickEvent);
        setChatHoverEvent = method(chatStyle, "setChatHoverEvent", hoverEvent);
        getChatClickEvent = method(chatStyle, "getChatClickEvent");
        getSiblings = method(chatComponent, "getSiblings");
        getUnformattedTextForChat = method(chatComponent, "getUnformattedTextForChat");
        setInsertion = method(chatStyle, "setInsertion", String.class);
        clickEventCtor = constructor(clickEvent, clickAction, String.class);
        hoverEventCtor = constructor(hoverEvent, hoverAction, chatComponent);
        suggestCommand = staticValue(clickAction, "SUGGEST_COMMAND");
        showText = staticValue(hoverAction, "SHOW_TEXT");
        getNetHandler = method(minecraftClass, "getNetHandler");
        getPlayerInfoMap = method(netHandler, "getPlayerInfoMap");
        getGameProfile = method(playerInfo, "getGameProfile");
        getDisplayName = method(playerInfo, "getDisplayName");
        setDisplayName = method(playerInfo, "setDisplayName", chatComponent);
        getUnformattedText = method(chatComponent, "getUnformattedText");
        appendSibling = method(chatComponent, "appendSibling", chatComponent);
        createCopy = method(chatComponent, "createCopy");
        profileId = method(gameProfile, "getId");
        profileName = method(gameProfile, "getName");
        profileGetProperties = method(gameProfile, "getProperties");
        Class<?> property = type("com.mojang.authlib.properties.Property");
        propertyGetValue = method(property, "getValue");
        Class<?> team = type("net.minecraft.scoreboard.ScorePlayerTeam");
        getPlayerTeam = method(playerInfo, "getPlayerTeam");
        getColorPrefix = method(team, "getColorPrefix");
        getColorSuffix = method(team, "getColorSuffix");
        displayTitle = method(ingame, "displayTitle", String.class, String.class, int.class, int.class, int.class);
    }

    private Object heldStack(Object player) throws Exception {
        if (inventory == null || mainInventory == null || currentItem == null) {
            return null;
        }
        Object inv = inventory.get(player);
        if (inv == null) {
            return null;
        }
        Object slots = mainInventory.get(inv);
        int slot = currentItem.getInt(inv);
        if (!(slots instanceof Object[]) || slot < 0 || slot >= ((Object[]) slots).length) {
            return null;
        }
        return ((Object[]) slots)[slot];
    }

    private Object item(Object stack) throws Exception {
        return stack == null || getItem == null ? null : getItem.invoke(stack);
    }

    private Class<?> type(String name) {
        try {
            return Class.forName(name, false, loader);
        } catch (Throwable thrown) {
            missing.add(name);
            return null;
        }
    }

    private Field field(Class<?> owner, String name) {
        for (Class<?> c = owner; c != null; c = c.getSuperclass()) {
            try {
                Field found = c.getDeclaredField(name);
                found.setAccessible(true);
                return found;
            } catch (NoSuchFieldException ignored) {
            } catch (Throwable thrown) {
                break;
            }
        }
        missing.add((owner == null ? "?" : owner.getSimpleName()) + "." + name);
        return null;
    }

    private Method method(Class<?> owner, String name, Class<?>... parameters) {
        for (Class<?> parameter : parameters) {
            if (parameter == null) {
                missing.add((owner == null ? "?" : owner.getSimpleName()) + "." + name + "()");
                return null;
            }
        }
        for (Class<?> c = owner; c != null; c = c.getSuperclass()) {
            try {
                Method found = c.getDeclaredMethod(name, parameters);
                found.setAccessible(true);
                return found;
            } catch (NoSuchMethodException ignored) {
            } catch (Throwable thrown) {
                break;
            }
        }
        missing.add((owner == null ? "?" : owner.getSimpleName()) + "." + name + "()");
        return null;
    }

    private Constructor<?> constructor(Class<?> owner, Class<?>... parameters) {
        if (owner == null) {
            missing.add("?.<init>");
            return null;
        }
        try {
            Constructor<?> found = owner.getDeclaredConstructor(parameters);
            found.setAccessible(true);
            return found;
        } catch (Throwable thrown) {
            missing.add((owner == null ? "?" : owner.getSimpleName()) + ".<init>");
            return null;
        }
    }

    private Object staticValue(Class<?> owner, String name) {
        Field found = field(owner, name);
        if (found == null || !Modifier.isStatic(found.getModifiers())) {
            return null;
        }
        try {
            return found.get(null);
        } catch (Throwable thrown) {
            missing.add((owner == null ? "?" : owner.getSimpleName()) + "." + name + " (value)");
            return null;
        }
    }
}
