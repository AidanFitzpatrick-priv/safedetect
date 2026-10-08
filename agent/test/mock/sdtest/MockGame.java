package sdtest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiNewChat;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Runs on the test's main thread, which plays the client thread: ticks players and drains scheduled tasks
 * the way Minecraft's game loop does.
 */
public final class MockGame {
    private static final int RED = 0xB02E26;

    private MockGame() {
    }

    public static List<String> run(File dataDir) throws Exception {
        Minecraft mc = new Minecraft(dataDir);
        for (int i = 0; i < 30; i++) {
            mc.runScheduledTasks();
            Thread.sleep(50);
        }

        WorldClient world = new WorldClient();
        EntityPlayerSP self = new EntityPlayerSP("Aidan");
        self.worldObj = world;
        self.inventory.armorInventory[2] = new ItemStack(Items.LEATHER_CHEST, RED);
        world.playerEntities.add(self);
        mc.theWorld = world;
        mc.thePlayer = self;

        EntityOtherPlayerMP saved = other(world, "noahhh727_alt", UUID.fromString("b30a4714-cbba-48e0-b335-8d04cbdc1392"), 10, 0);
        EntityOtherPlayerMP blocker = other(world, "Blocker", null, 5, 5);
        blocker.inventory.mainInventory[0] = new ItemStack(Items.SWORD);
        EntityOtherPlayerMP blockerAlt = other(world, "Blocker2", null, 6, 6);
        blocker.inventory.armorInventory[2] = new ItemStack(Items.IRON_CHEST);
        EntityOtherPlayerMP mate = other(world, "Mate", null, 3, 3);
        mate.inventory.mainInventory[0] = new ItemStack(Items.SWORD);
        mate.inventory.armorInventory[2] = new ItemStack(Items.LEATHER_CHEST, RED);
        EntityOtherPlayerMP far = other(world, "FarAway", null, 300, 0);
        far.inventory.mainInventory[0] = new ItemStack(Items.SWORD);
        EntityOtherPlayerMP creative = other(world, "Creative", null, 4, 4);
        creative.inventory.mainInventory[0] = new ItemStack(Items.SWORD);
        creative.capabilities.isCreativeMode = true;
        EntityOtherPlayerMP slow = other(world, "Slowpoke", null, -5, 0);
        slow.inventory.mainInventory[0] = new ItemStack(Items.APPLE);
        EntityOtherPlayerMP eater = other(world, "Eater", null, 0, -5);
        eater.inventory.mainInventory[0] = new ItemStack(Items.APPLE);
        EntityOtherPlayerMP aura = other(world, "Aura", null, 12, 0);
        aura.inventory.mainInventory[0] = new ItemStack(Items.SWORD);
        aura.rotationYaw = 0.0f;
        aura.rotationYawHead = 0.0f;
        EntityOtherPlayerMP bag = other(world, "Bag", null, 15, 0);
        EntityOtherPlayerMP boxer = other(world, "Boxer", null, 8, 8);
        boxer.inventory.mainInventory[0] = new ItemStack(Items.SWORD);
        boxer.rotationYaw = 0.0f;
        boxer.rotationYawHead = 0.0f;
        EntityOtherPlayerMP dummy = other(world, "Dummy", null, 8, 10);
        EntityOtherPlayerMP stiff = other(world, "Stiff", null, 88, 0);
        EntityOtherPlayerMP godder = other(world, "Godder", null, 94, 0);
        godder.inventory.mainInventory[0] = new ItemStack(Items.WOOL);
        godder.rotationPitch = 80.0f;
        godder.rotationYaw = 0.0f;
        EntityOtherPlayerMP telly = other(world, "Telly", null, 96, 0);
        telly.inventory.mainInventory[0] = new ItemStack(Items.WOOL);
        EntityOtherPlayerMP clicker = other(world, "Clicker", null, 70, 0);
        clicker.inventory.mainInventory[0] = new ItemStack(Items.SWORD);
        EntityOtherPlayerMP jitter = other(world, "JitterClick", null, 72, 0);
        jitter.inventory.mainInventory[0] = new ItemStack(Items.SWORD);
        int jitterNext = 0;
        EntityOtherPlayerMP reacher = other(world, "Reacher", null, 80, 0);
        reacher.inventory.mainInventory[0] = new ItemStack(Items.SWORD);
        reacher.rotationYaw = 0.0f;
        reacher.rotationYawHead = 0.0f;
        EntityOtherPlayerMP stretched = other(world, "Stretched", null, 80, 4);
        EntityOtherPlayerMP nuker = other(world, "Nuker", null, 100, 100);
        nuker.rotationYaw = 0.0f;
        nuker.rotationYawHead = 0.0f;
        nuker.rotationPitch = 20.0f;
        nuker.posY = 64.0;
        EntityOtherPlayerMP miner = other(world, "Miner", null, 110, 110);
        miner.rotationYaw = 0.0f;
        miner.rotationYawHead = 0.0f;
        miner.rotationPitch = 20.0f;
        miner.posY = 64.0;
        world.blocks.put(new BlockPos(100, 64, 101), Blocks.wool);
        world.blocks.put(new BlockPos(100, 65, 101), Blocks.wool);
        world.blocks.put(new BlockPos(100, 64, 102), Blocks.bed);
        world.blocks.put(new BlockPos(110, 64, 112), Blocks.bed);
        EntityOtherPlayerMP bridger = other(world, "Bridger", null, 7, -7);
        bridger.inventory.mainInventory[0] = new ItemStack(Items.WOOL);
        bridger.rotationPitch = 78.0f;
        EntityOtherPlayerMP walker = other(world, "Walker", null, 2, 2);
        walker.inventory.mainInventory[0] = new ItemStack(Items.SWORD);

        EntityOtherPlayerMP speeder = other(world, "Speeder", null, 20, 0);
        EntityOtherPlayerMP hopper = other(world, "Hopper", null, 20, 10);
        EntityOtherPlayerMP knocked = other(world, "Knocked", null, 20, -10);
        EntityOtherPlayerMP potion = other(world, "PotionRunner", null, 20, 20);
        potion.speed = new PotionEffect(1);
        EntityOtherPlayerMP flyer = other(world, "Flyer", null, -20, 0);
        EntityOtherPlayerMP hypixelBot = other(world, "7w0392l04b", null, -22, 5);
        EntityOtherPlayerMP hypixelBot2 = other(world, "11i4xodxih", null, -22, 8);
        EntityOtherPlayerMP trnsmt = other(world, "trnsmt", null, -24, 5);
        EntityOtherPlayerMP zoxide = other(world, "zoxide", null, -24, 8);
        EntityOtherPlayerMP swimmer = other(world, "Swimmer", null, -20, 10);
        swimmer.inWater = true;
        EntityOtherPlayerMP towerer = other(world, "Towerer", null, 30, 30);
        towerer.inventory.mainInventory[0] = new ItemStack(Items.WOOL);
        towerer.rotationPitch = 85.0f;
        EntityOtherPlayerMP slowTower = other(world, "SlowTower", null, 35, 30);
        slowTower.inventory.mainInventory[0] = new ItemStack(Items.WOOL);
        slowTower.rotationPitch = 85.0f;
        EntityOtherPlayerMP sprintBridger = other(world, "SprintBridger", null, 40, 0);
        sprintBridger.inventory.mainInventory[0] = new ItemStack(Items.WOOL);
        sprintBridger.rotationPitch = 80.0f;
        EntityOtherPlayerMP backBridger = other(world, "BackBridger", null, 45, 0);
        backBridger.inventory.mainInventory[0] = new ItemStack(Items.WOOL);
        backBridger.rotationPitch = 80.0f;
        EntityOtherPlayerMP spammer = bridger(world, "CrouchSpammer", 50, 10);
        EntityOtherPlayerMP shifter = bridger(world, "ShiftBridger", 55, 10);
        EntityOtherPlayerMP fighter = bridger(world, "Fighter", 60, 10);
        fighter.inventory.mainInventory[0] = new ItemStack(Items.SWORD);
        EntityOtherPlayerMP sloppy = bridger(world, "Sloppy", 65, 10);
        EntityOtherPlayerMP snapper = other(world, "Snapper", null, -30, -30);
        EntityOtherPlayerMP spinner = other(world, "Spinner", null, -35, -30);
        EntityOtherPlayerMP diag = other(world, "DiagBridger", null, 42, 42);
        diag.inventory.mainInventory[0] = new ItemStack(Items.WOOL);
        diag.rotationPitch = 80.0f;
        diag.rotationYaw = 0.0f;

        for (int t = 0; t < 220; t++) {
            tick(world);
            speeder.posX += 0.6;
            hopper.sprinting = true;
            hopper.posX += 0.355;
            int jump = t % 12;
            hopper.onGround = jump == 0;
            hopper.posY = 64.0 + 0.42 * jump - 0.035 * jump * jump;
            knocked.hurtTime = t % 30 < 10 ? 10 - t % 30 : 0;
            knocked.posX += 0.8;
            potion.sprinting = true;
            potion.posX += 0.48;
            flyer.onGround = false;
            flyer.posX += 0.2;
            hypixelBot.onGround = false;
            hypixelBot.posX += 0.2;
            hypixelBot2.onGround = false;
            hypixelBot2.posY += 0.01;
            trnsmt.onGround = false;
            trnsmt.posX += 0.2;
            zoxide.onGround = false;
            zoxide.posX += 0.2;
            swimmer.onGround = false;
            swimmer.posX += 0.1;
            towerer.posY += 0.4;
            towerer.onGround = t % 3 == 0;
            slowTower.posY += 0.1;
            slowTower.onGround = t % 10 == 0;
            sprintBridger.sprinting = true;
            sprintBridger.posZ -= 0.28;
            backBridger.posZ -= 0.2;
            boolean snap = t % 10 == 0;
            snapper.rotationYawHead += snap ? 150.0f : 0.0f;
            snapper.isSwingInProgress = t % 10 < 6;
            snapper.swingProgressInt = t % 10;
            spinner.rotationYawHead += 120.0f;
            int s = t % 6;
            spammer.sneaking = s < 2;
            spammer.swingProgressInt = s == 2 ? 1 : 0;
            int h = t % 8;
            shifter.sneaking = h < 4;
            shifter.swingProgressInt = h == 4 ? 1 : 0;
            shifter.posZ -= 0.1;
            fighter.sneaking = s < 2;
            fighter.swingProgressInt = s == 2 ? 1 : 0;
            fighter.posZ -= 0.12;
            sloppy.sneaking = s < 2;
            sloppy.swingProgressInt = s == 2 && t % 24 < 18 ? 1 : 0;
            sloppy.posZ -= 0.12;
            for (EntityOtherPlayerMP p : new EntityOtherPlayerMP[]{blocker, mate, far, creative}) {
                p.isSwingInProgress = true;
                p.usingItem = true;
            }
            slow.sprinting = true;
            slow.usingItem = true;
            slow.posX += 0.2;
            eater.usingItem = true;
            eater.swingProgressInt = 1;
            int beat = t % 12;
            aura.isSwingInProgress = beat == 0;
            aura.swingProgressInt = beat == 0 ? 1 : 0;
            bag.hurtTime = beat < 10 ? 10 - beat : 0;
            boxer.isSwingInProgress = beat == 0;
            boxer.swingProgressInt = beat == 0 ? 1 : 0;
            dummy.hurtTime = beat < 10 ? 10 - beat : 0;
            stiff.sprinting = true;
            stiff.hurtTime = beat < 10 ? 10 - beat : 0;
            godder.sprinting = true;
            godder.onGround = true;
            godder.posZ += 0.28;
            telly.posZ += 0.2;
            telly.rotationPitch = t % 6 < 3 ? 80.0f : 20.0f;
            clicker.isSwingInProgress = true;
            clicker.swingProgressInt = 1;
            if (t >= jitterNext) {
                jitter.isSwingInProgress = true;
                jitter.swingProgressInt = 1;
                jitterNext = t + 1 + ((t * 19 + 4) % 3 == 0 ? 1 : 0);
            } else {
                jitter.isSwingInProgress = false;
                jitter.swingProgressInt = 0;
            }
            reacher.isSwingInProgress = beat == 0;
            reacher.swingProgressInt = beat == 0 ? 1 : 0;
            stretched.hurtTime = beat < 10 ? 10 - beat : 0;
            nuker.isSwingInProgress = true;
            nuker.swingProgressInt = 1;
            miner.isSwingInProgress = true;
            miner.swingProgressInt = 1;
            if (t == 40) {
                world.blocks.remove(new BlockPos(100, 64, 102));
                world.blocks.remove(new BlockPos(110, 64, 112));
            }
            int c = t % 6;
            bridger.sneaking = c < 2;
            bridger.swingProgressInt = c == 2 ? 1 : 0;
            bridger.posZ -= 0.12;
            walker.sprinting = true;
            walker.posX += 0.28;
            walker.isSwingInProgress = t % 3 == 0;
            diag.sprinting = true;
            diag.onGround = true;
            diag.posX += 0.2;
            diag.posZ += 0.2;
            frames(mc);
        }

        GuiNewChat chatGui = mc.ingameGUI.getChatGUI();
        for (int i = 0; i < 105; i++) {
            chatGui.printChatMessage(new ChatComponentText("Filler server line " + i));
        }
        chatGui.printChatMessage(new ChatComponentText("[MVP+] Ghosty has joined (2/16)!"));
        chatGui.printChatMessage(new ChatComponentText("[MVP+] Spoofer: VICTORY!"));
        ticks(mc, world, 3);
        self.chat.add("HUD_GAME " + hud(dataDir));

        WorldClient lobby = new WorldClient();
        world.playerEntities.remove(self);
        self.worldObj = lobby;
        self.inventory.mainInventory[0] = new ItemStack(Items.COMPASS);
        lobby.playerEntities.add(self);
        mc.theWorld = lobby;
        EntityOtherPlayerMP lobbyFlyer = other(lobby, "LobbyFlyer", null, 5, 0);
        EntityOtherPlayerMP lobbySpeeder = other(lobby, "LobbySpeeder", null, -5, 0);
        for (int t = 0; t < 120; t++) {
            tick(lobby);
            lobbyFlyer.onGround = false;
            lobbyFlyer.posX += 0.3;
            lobbySpeeder.posX += 0.8;
            frames(mc);
        }
        self.chat.add("HUD_LOBBY " + hud(dataDir));
        send(mc, self, "/sd");
        send(mc, self, "/sd friends");
        send(mc, self, "/sd friend UnitTest");
        send(mc, self, "/sd list");
        send(mc, self, "/sd list");
        send(mc, self, "/sd check Wemzy_on_top");
        send(mc, self, "/sd clear");

        File cmdDir = new File(dataDir, "config/safedetect-cmd");
        cmdDir.mkdirs();
        Files.write(new File(cmdDir, "0000000000001-000001.json").toPath(),
                "{\"keys\":{\"sniperUrl\":\"http://127.0.0.1:1/SECRET123/{{name}}\"}}".getBytes(StandardCharsets.UTF_8));
        for (int i = 0; i < 5; i++) {
            frames(mc);
            Thread.sleep(40);
        }
        String copy = null;
        for (String line : self.chat) {
            String plain = line.replaceAll("\u00a7.", "");
            if (plain.contains("Blocker failed AutoBlock")) {
                copy = "/wdr Blocker cheating";
                break;
            }
        }
        net.minecraft.client.gui.GuiChat box = mc.openChat();
        box.inputField.setText(copy);
        frames(mc);
        if (box.inputField.getText().isEmpty()) {
            self.chat.add("COPY_OK");
        }
        mc.currentScreen = null;

        send(mc, self, "/sd bl add Baddie");
        Files.write(new File(cmdDir, "0000000000002-000001.json").toPath(),
                "{\"set\":{\"check.SP\":false}}".getBytes(StandardCharsets.UTF_8));
        frames(mc);
        WorldClient arena = new WorldClient();
        lobby.playerEntities.remove(self);
        self.worldObj = arena;
        self.inventory.mainInventory[0] = new ItemStack(Items.SWORD);
        arena.playerEntities.add(self);
        mc.theWorld = arena;
        EntityOtherPlayerMP baddie = other(arena, "Baddie", UUID.randomUUID(), 8, 0);
        EntityOtherPlayerMP speeder2 = other(arena, "Speeder2", UUID.randomUUID(), -8, 0);
        NetHandlerPlayClient net = new NetHandlerPlayClient();
        net.players.add(new NetworkPlayerInfo(new GameProfile(self.getUniqueID(), "Aidan")));
        NetworkPlayerInfo baddieInfo = new NetworkPlayerInfo(new GameProfile(baddie.getUniqueID(), "Baddie"));
        baddieInfo.team = new ScorePlayerTeam("\u00a7c");
        net.players.add(baddieInfo);
        NetworkPlayerInfo speederInfo = new NetworkPlayerInfo(new GameProfile(speeder2.getUniqueID(), "Speeder2"));
        speederInfo.team = new ScorePlayerTeam("\u00a79");
        net.players.add(speederInfo);
        mc.netHandler = net;
        for (int t = 0; t < 120; t++) {
            tick(arena);
            speeder2.posX += 0.6;
            frames(mc);
        }
        self.chat.add("HUD_ARENA " + hud(dataDir));
        self.chat.add("TITLES " + mc.ingameGUI.titles);
        send(mc, self, "/sd export");
        send(mc, self, "/sd reload");
        send(mc, self, "/sd checks");

        long flushBy = System.currentTimeMillis() + 2600L;
        while (System.currentTimeMillis() < flushBy) {
            frames(mc);
        }
        System.out.println("Scheduled tasks run on client thread: " + mc.tasksRun);
        return new ArrayList<String>(self.chat);
    }

    private static EntityOtherPlayerMP bridger(WorldClient world, String name, double x, double z) {
        EntityOtherPlayerMP player = other(world, name, null, x, z);
        player.inventory.mainInventory[0] = new ItemStack(Items.WOOL);
        player.rotationPitch = 78.0f;
        return player;
    }

    private static void tick(WorldClient world) {
        for (Object o : world.playerEntities) {
            ((net.minecraft.entity.Entity) o).ticksExisted++;
        }
    }

    /** Same order as vanilla GuiChat: record history, send, close the screen. */
    private static void send(Minecraft mc, EntityPlayerSP self, String text) throws InterruptedException {
        GuiChat box = mc.openChat();
        box.inputField.setText(text);
        frames(mc);
        mc.ingameGUI.getChatGUI().addToSentMessages(text);
        self.sendChatMessage(text);
        mc.currentScreen = null;
        frames(mc);
    }

    private static void ticks(Minecraft mc, WorldClient world, int count) throws InterruptedException {
        for (int i = 0; i < count; i++) {
            tick(world);
            frames(mc);
        }
    }

    /** HUD writes go through a background writer, so give it a moment before reading. */
    private static String hud(File dataDir) throws Exception {
        Thread.sleep(300);
        File file = new File(dataDir, "config/safedetect-hud.json");
        return file.isFile() ? new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)
                .replace('\n', ' ') : "";
    }

    private static void frames(Minecraft mc) throws InterruptedException {
        for (int frame = 0; frame < 4; frame++) {
            mc.runScheduledTasks();
            Thread.sleep(12);
        }
    }

    private static EntityOtherPlayerMP other(WorldClient world, String name, UUID id, double x, double z) {
        EntityOtherPlayerMP player = new EntityOtherPlayerMP(name, id);
        player.worldObj = world;
        player.posX = x;
        player.posZ = z;
        world.playerEntities.add(player);
        return player;
    }
}
