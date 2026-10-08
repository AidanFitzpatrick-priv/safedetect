package com.safedetect.agent;

import java.io.File;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Live cheat checks plus a chat line when a saved player is nearby. Detection still ignores scoreboard
 * and tab; tab is only written to so saved flags appear next to names.
 * Called on Minecraft's client thread only; checks wait for a new client tick, commands do not.
 */
final class Detector {
    private static final double RANGE = 256.0;
    private static final String PREFIX = "\u00a78[\u00a7bSD\u00a78] \u00a7r";
    private static final int COMPASS = 345;
    private static final int LIST_PAGE = 8;
    private static final String TAB_MARK = "\u00a7r \u00a78\u00a7lSD\u00a7r ";
    private static final double SWEAT_FKDR = 4.0;
    private static final int SWEAT_FINALS = 50;

    private static final class Tracked {
        final Checks combat = new Checks();
        final MoveChecks movement = new MoveChecks();
    }

    private final Game game;
    private final Map<UUID, Tracked> checks = new HashMap<UUID, Tracked>();
    private final Set<UUID> announced = new HashSet<UUID>();
    private final Set<UUID> present = new HashSet<UUID>();
    private final PlayerView view = new PlayerView();
    private final List<PlayerView> nearby = new ArrayList<PlayerView>();
    private final Map<UUID, Object> nearbyEntities = new HashMap<UUID, Object>();
    private final Map<UUID, Integer> prevHurt = new HashMap<UUID, Integer>();
    private final Map<UUID, Boolean> prevSwing = new HashMap<UUID, Boolean>();
    private final Set<String> copyTargets = new LinkedHashSet<String>();
    private final Map<UUID, Object> tabOriginal = new HashMap<UUID, Object>();
    private final Map<UUID, Object> tabApplied = new HashMap<UUID, Object>();
    private WeakReference<Object> lastWorld = new WeakReference<Object>(null);
    private int lastSelfTicks = Integer.MIN_VALUE;
    private long tick;
    private FlagStore store;
    private boolean greeted;
    private volatile boolean inWorld;
    private int sentAt;
    private String lastCopied;
    private final Set<String> lookedUp = new HashSet<String>();
    private final Map<String, Intel> intelByName = new HashMap<String, Intel>();
    private final Hypixel hypixel = new Hypixel();
    private final AntiSniper snipers = new AntiSniper();
    private final Tags tagsApi = new Tags();
    private final LobbyChat lobbyChat = new LobbyChat();
    private final DiscordPresence discord = new DiscordPresence();
    private final Map<UUID, Intel> intel = new HashMap<UUID, Intel>();
    private final Set<UUID> sniperLooked = new HashSet<UUID>();
    private final Set<UUID> tagLooked = new HashSet<UUID>();
    private final Set<UUID> tagAnnounced = new HashSet<UUID>();
    private final Set<UUID> auroraLooked = new HashSet<UUID>();
    private Settings settings;
    private Blacklist blacklist;
    private Denick denick;
    private int receivedAt;
    private int sessionGames;
    private int sessionWins;
    private long lastWhoAt = Long.MIN_VALUE / 2;

    private static final class Intel {
        int stars = -1;
        double fkdr = -1;
        double wlr = -1;
        int finals = -1;
        int wins = -1;
        int winstreak = -1;
        int sniper = -1;
        String extra = "";
        boolean nicked;
    }

    Detector(Game game) {
        this.game = game;
    }

    boolean inWorld() {
        return inWorld;
    }

    void frame() throws Exception {
        Object mc = game.minecraft();
        if (mc == null || !game.onClientThread(mc)) {
            return;
        }
        Object self = game.player(mc);
        Object world = game.world(mc);
        if (self == null || world == null) {
            inWorld = false;
            Hud.show(0, java.util.Collections.<Hud.Row>emptyList(),
                    java.util.Collections.<Hud.Row>emptyList());
            return;
        }
        inWorld = true;
        if (store == null) {
            File dir = game.dataDir(mc);
            File root = dir != null ? dir : new File(".");
            store = new FlagStore(root);
            settings = new Settings(root);
            blacklist = new Blacklist(root);
            denick = new Denick(root);
            FlagLog.open(root);
            Hud.open(root);
            Hud.bind(settings);
            if (settings.discordAppId != null && !settings.discordAppId.isEmpty()) {
                discord.setAppId(settings.discordAppId);
            }
            Log.info("Flag file loaded: " + store.size() + " saved players.");
        }
        commands(mc, self);
        copyIfClicked(mc);
        boolean overlayCmd = overlayHud(self);
        incoming(mc, self);
        int selfTicks = game.ticksExisted(self);
        boolean sameWorld = lastWorld.get() == world;
        if (sameWorld && selfTicks == lastSelfTicks && !overlayCmd) {
            return;
        }
        int elapsed = sameWorld && selfTicks > lastSelfTicks ? Math.min(selfTicks - lastSelfTicks, 5) : 1;
        lastSelfTicks = selfTicks;
        if (!sameWorld) {
            lastWorld = new WeakReference<Object>(world);
            checks.clear();
            announced.clear();
            prevHurt.clear();
            prevSwing.clear();
            nearby.clear();
            nearbyEntities.clear();
            lookedUp.clear();
            intel.clear();
            intelByName.clear();
            sniperLooked.clear();
            tagLooked.clear();
            tagAnnounced.clear();
            auroraLooked.clear();
            receivedAt = 0;
        }
        tick += elapsed;
        if (!greeted) {
            greeted = true;
            chat(self, "\u00a77SafeDetect is running. Saved players: " + store.size()
                    + ". \u00a78Type \u00a7f/sd list\u00a78, \u00a7f/sd gui\u00a78, \u00a7f/sd clear\u00a78.");
            if (settings != null && !settings.hasKey()) {
                chat(self, "\u00a77Stats are off. \u00a78/sd key <hypixel-api-key> \u00a77from developer.hypixel.net");
            }
        }

        int selfColor = 0;
        double selfX;
        double selfZ;
        try {
            selfColor = game.armorColor(self);
        } catch (Throwable thrown) {
            Log.once("own armor", thrown);
        }
        selfX = game.posX(self);
        selfZ = game.posZ(self);
        boolean inGame = false;
        try {
            inGame = game.canReadItemIds() && game.hotbarItemId(self, 0) != COMPASS;
        } catch (Throwable thrown) {
            Log.once("lobby check", thrown);
        }
        boolean movementData = game.canCheckMovement();

        present.clear();
        nearby.clear();
        nearbyEntities.clear();
        Object[] players;
        try {
            players = game.players(world);
        } catch (Throwable thrown) {
            Log.once("player list", thrown);
            return;
        }
        UUID selfId = game.uuid(self);
        Set<UUID> tabIds = tabPlayerIds(mc, selfId);
        try {
            if (selfId != null) {
                present.add(selfId);
                snapshot(self, selfId, movementData);
            }
        } catch (Throwable thrown) {
            Log.once("self snapshot", thrown);
        }
        for (Object player : players) {
            if (player == null || player == self) {
                continue;
            }
            try {
                track(self, world, player, selfColor, selfX, selfZ, elapsed, inGame, movementData, tabIds);
            } catch (Throwable thrown) {
                Log.once("player", thrown);
            }
        }
        PlayerView[] snaps = nearby.toArray(new PlayerView[nearby.size()]);
        for (PlayerView snap : snaps) {
            if (snap.uuid == null || snap.uuid.equals(selfId)) {
                continue;
            }
            Tracked data = checks.get(snap.uuid);
            Object entity = nearbyEntities.get(snap.uuid);
            if (data == null || entity == null) {
                continue;
            }
            try {
                data.combat.melee(snap, snaps);
                if (data.combat.failedKillaura()) {
                    mark(self, snap.uuid, entity, FlagStore.Flag.KA, "Killaura");
                    data.combat.resetKillaura();
                }
                if (data.combat.failedSilentAura()) {
                    mark(self, snap.uuid, entity, FlagStore.Flag.SI, "Silent Aura");
                    data.combat.resetSilentAura();
                }
                if (data.combat.failedReach()) {
                    mark(self, snap.uuid, entity, FlagStore.Flag.RE, "Reach");
                    data.combat.resetReach();
                }
            } catch (Throwable thrown) {
                Log.once("melee", thrown);
            }
        }
        applyHypixel(self);
        applySniper();
        applyTags(self);
        applyDenick(self);
        Iterator<UUID> hurtGone = prevHurt.keySet().iterator();
        while (hurtGone.hasNext()) {
            if (!present.contains(hurtGone.next())) {
                hurtGone.remove();
            }
        }
        Iterator<UUID> swingGone = prevSwing.keySet().iterator();
        while (swingGone.hasNext()) {
            if (!present.contains(swingGone.next())) {
                swingGone.remove();
            }
        }
        Iterator<UUID> it = checks.keySet().iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            if (!present.contains(id)) {
                it.remove();
                announced.remove(id);
            }
        }
        tab(mc);
        lobbyWindow(mc, selfId);
    }

    private void track(Object self, Object world, Object player, int selfColor, double selfX, double selfZ,
                       int elapsed, boolean inGame, boolean movementData, Set<UUID> tabIds) throws Exception {
        if (game.dead(player) || game.worldOf(player) != world) {
            return;
        }
        double x = game.posX(player);
        double z = game.posZ(player);
        double dx = x - selfX;
        double dz = z - selfZ;
        if (dx * dx + dz * dz > RANGE * RANGE) {
            return;
        }
        UUID id = game.uuid(player);
        if (id == null) {
            return;
        }
        String npcName = game.name(player);
        if (skipped(npcName) || npc(id, npcName, tabIds)) {
            return;
        }
        present.add(id);
        PlayerView snap = snapshot(player, id, movementData);
        if (game.creative(player)) {
            return;
        }
        if (announced.add(id)) {
            String name = npcName != null ? npcName : game.name(player);
            String tags = store.brackets(store.get(id, name));
            if (name != null && !tags.isEmpty()) {
                alert(self, name + " \u00a77marked " + tags, Hud.wdr(name));
            } else if (name != null && blacklist != null && blacklist.contains(name)) {
                alert(self, "tagged: \u00a7f" + name + " \u00a7c[BL]", Hud.wdr(name));
            }
            requestIntel(id, name);
        }
        maybeAlt(self, id, npcName);

        view.posX = snap.posX;
        view.posY = snap.posY;
        view.posZ = snap.posZ;
        view.onGround = snap.onGround;
        view.pitch = snap.pitch;
        view.yaw = snap.yaw;
        view.headYaw = snap.headYaw;
        view.sneaking = snap.sneaking;
        view.sprinting = snap.sprinting;
        view.usingItem = snap.usingItem;
        view.swinging = snap.swinging;
        view.swingProgressInt = snap.swingProgressInt;
        view.riding = snap.riding;
        view.held = snap.held;
        view.speedAmplifier = snap.speedAmplifier;
        view.jumpAmplifier = snap.jumpAmplifier;
        view.hurtTime = snap.hurtTime;
        view.prevHurtTime = snap.prevHurtTime;
        view.climbingOrSwimming = snap.climbingOrSwimming;
        view.invisible = snap.invisible;
        view.uuid = snap.uuid;

        Tracked data = checks.get(id);
        if (data == null) {
            data = new Tracked();
            checks.put(id, data);
        }
        Checks combat = data.combat;
        MoveChecks movement = data.movement;
        combat.update(view, tick, elapsed);
        movement.update(view, tick, elapsed, inGame, movementData);
        if (combat.failedAutoBlock()) {
            mark(self, id, player, FlagStore.Flag.AB, "AutoBlock");
            combat.resetAutoBlock();
        }
        if (combat.failedNoSlow()) {
            mark(self, id, player, FlagStore.Flag.NS, "NoSlow");
            combat.resetNoSlow();
        }
        if (combat.failedLegitScaffold()) {
            mark(self, id, player, FlagStore.Flag.LS, "Legit Scaffold");
            combat.resetLegitScaffold();
        }
        if (combat.failedAutoclicker()) {
            mark(self, id, player, FlagStore.Flag.AC, "Autoclicker");
            combat.resetAutoclicker();
        }
        if (movement.failedSprintScaffold()) {
            mark(self, id, player, FlagStore.Flag.SS, "Sprint Scaffold");
            movement.resetSprintScaffold();
        }
        if (movement.failedTower()) {
            mark(self, id, player, FlagStore.Flag.TW, "Tower");
            movement.resetTower();
        }
        if (movement.failedSpeed()) {
            mark(self, id, player, FlagStore.Flag.SP, "Speed");
            movement.resetSpeed();
        }
        if (movement.failedSnapAim()) {
            mark(self, id, player, FlagStore.Flag.SA, "Snap Aim");
            movement.resetSnapAim();
        }
        if (movement.failedVelocity()) {
            mark(self, id, player, FlagStore.Flag.VL, "Velocity");
            movement.resetVelocity();
        }
        if (movement.failedGodBridge()) {
            mark(self, id, player, FlagStore.Flag.GB, "God Bridge");
            movement.resetGodBridge();
        }
        if (movement.failedDiagonal()) {
            mark(self, id, player, FlagStore.Flag.DS, "Diagonal Scaffold");
            movement.resetDiagonal();
        }
        if (movement.failedTelly()) {
            mark(self, id, player, FlagStore.Flag.TL, "Telly");
            movement.resetTelly();
        }
    }

    private PlayerView snapshot(Object player, UUID id, boolean movementData) throws Exception {
        PlayerView snap = new PlayerView();
        snap.uuid = id;
        snap.posX = game.posX(player);
        snap.posY = game.posY(player);
        snap.posZ = game.posZ(player);
        snap.onGround = game.onGround(player);
        snap.pitch = game.pitch(player);
        snap.yaw = game.yaw(player);
        snap.headYaw = game.headYaw(player);
        snap.sneaking = game.sneaking(player);
        snap.sprinting = game.sprinting(player);
        snap.usingItem = game.usingItem(player);
        snap.swinging = game.swinging(player);
        snap.swingProgressInt = game.swingProgressInt(player);
        Boolean wasSwing = prevSwing.get(id);
        snap.swingStart = snap.swingProgressInt == 1 || (snap.swinging && !Boolean.TRUE.equals(wasSwing));
        prevSwing.put(id, snap.swinging);
        snap.riding = game.riding(player);
        snap.held = game.held(player);
        snap.speedAmplifier = game.speedAmplifier(player);
        snap.jumpAmplifier = snap.held == Game.HELD_BLOCK ? game.jumpAmplifier(player) : -1;
        snap.hurtTime = game.hurtTime(player);
        Integer previous = prevHurt.get(id);
        snap.prevHurtTime = previous == null ? 0 : previous.intValue();
        prevHurt.put(id, snap.hurtTime);
        snap.climbingOrSwimming = movementData && game.climbingOrSwimming(player);
        snap.invisible = game.invisible(player);
        nearby.add(snap);
        nearbyEntities.put(id, player);
        return snap;
    }

    private void mark(Object self, UUID id, Object player, FlagStore.Flag flag, String check) throws Exception {
        String name = player == null ? null : game.name(player);
        if (store.add(id, name, flag, check)) {
            String wdr = name != null ? Hud.wdr(name) : PREFIX + name + " failed " + check;
            alert(self, name + " \u00a77failed \u00a7c" + check, wdr);
            FlagLog.line(name, check);
            Log.info(name + " failed " + check);
        }
    }

    private void note(Object self, UUID id, String name, FlagStore.Flag flag, String detail) {
        if (!store.add(id, name, flag, detail)) {
            return;
        }
        alert(self, name + " \u00a77" + detail, name);
        FlagLog.line(name, detail);
        Log.info(name + " " + detail);
    }

    private void ping(Object self) {
        if (settings != null && !settings.alertSound) {
            return;
        }
        try {
            game.playSound(self, "note.pling", 3.0f, 2.0f);
            game.playSound(self, "random.successful_hit", 2.0f, 0.7f);
            game.playSound(self, "note.pling", 3.0f, 1.4f);
        } catch (Throwable thrown) {
            Log.once("sound", thrown);
        }
    }

    private void alert(Object self, String text, String copy) {
        if (settings == null || settings.alertsChat) {
            chat(self, text, copy);
        }
        ping(self);
    }

    private void chat(Object self, String text) {
        chat(self, text, null);
    }

    private void chat(Object self, String text, String copy) {
        String shown = PREFIX + text;
        String plain = copy != null ? copy : shown.replaceAll("\u00a7.", "");
        copyTargets.add(plain);
        if (copyTargets.size() > 80) {
            Iterator<String> extra = copyTargets.iterator();
            extra.next();
            extra.remove();
        }
        try {
            String hover = copy != null && copy.startsWith("/wdr") ? "\u00a77Click to copy /wdr" : "\u00a77Click to copy";
            game.chatClickable(self, shown, plain, hover);
        } catch (Throwable thrown) {
            Log.once("chat", thrown);
        }
    }

    private void commands(Object mc, Object self) {
        try {
            List<?> sent = game.sentChat(mc);
            if (sent == null) {
                return;
            }
            if (sentAt > sent.size()) {
                sentAt = 0;
            }
            while (sentAt < sent.size()) {
                Object line = sent.get(sentAt++);
                if (line instanceof String) {
                    handleCommand(self, ((String) line).trim());
                }
            }
        } catch (Throwable thrown) {
            Log.once("command", thrown);
        }
    }

    private void handleCommand(Object self, String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        if (!lower.equals("/sd") && !lower.startsWith("/sd ")) {
            return;
        }
        String[] parts = line.split("\\s+");
        String sub = parts.length < 2 ? "help" : parts[1].toLowerCase(Locale.ROOT);
        if ("help".equals(sub) || "?".equals(sub)) {
            chat(self, "\u00a77/sd list [page] \u00a78saved flags");
            chat(self, "\u00a77/sd check <name> \u00a78one player");
            chat(self, "\u00a77/sd clear confirm \u00a78delete every saved player");
            chat(self, "\u00a77/sd gui \u00a78reopen the lobby window");
            chat(self, "\u00a77/sd friend <name> \u00a78never check them");
            chat(self, "\u00a77/sd unfriend <name>");
            chat(self, "\u00a77/sd key <hypixel-api-key> \u00a78nicks + FKDR");
            chat(self, "\u00a77/sd sniper <key|url|off> \u00a78anti-sniper");
            chat(self, "\u00a77/sd borderless \u00a78keep overlay on top");
            chat(self, "\u00a77/sd urchin <key> \u00a78Prism cheater tags");
            chat(self, "\u00a77/sd seraph <key> \u00a78Seraph tags");
            chat(self, "\u00a77/sd nick <shown> <real> \u00a78denick a teammate");
            chat(self, "\u00a77/sd discord <app-id> \u00a78session presence");
            chat(self, "\u00a77/sd bl <add|remove|list|import> \u00a78local blacklist");
            chat(self, "\u00a77/sd aurora <key> \u00a78number denick");
            return;
        }
        if ("gui".equals(sub) || "window".equals(sub) || "panel".equals(sub)) {
            Hud.reopen();
            chat(self, "\u00a77Lobby window opened.");
            return;
        }
        if ("list".equals(sub)) {
            int page = 1;
            if (parts.length >= 3) {
                try {
                    page = Integer.parseInt(parts[2]);
                } catch (NumberFormatException ignored) {
                }
            }
            list(self, page);
            return;
        }
        if ("check".equals(sub)) {
            if (parts.length < 3) {
                chat(self, "\u00a77Usage: /sd check <name>");
                return;
            }
            String name = parts[2];
            FlagStore.Record record = store.get(null, name);
            if (record == null) {
                chat(self, "\u00a77No flags for \u00a7f" + name + "\u00a77.");
                return;
            }
            chat(self, recordLine(record));
            return;
        }
        if ("friend".equals(sub) || "friends".equals(sub) || "ignore".equals(sub)) {
            friendsCommand(self, parts);
            return;
        }
        if ("unfriend".equals(sub)) {
            if (parts.length < 3) {
                chat(self, "\u00a77Usage: /sd unfriend <name>");
                return;
            }
            unfriend(self, parts[2]);
            return;
        }
        if ("key".equals(sub) || "apikey".equals(sub)) {
            if (settings == null) {
                return;
            }
            if (parts.length < 3) {
                chat(self, settings.hasKey() ? "\u00a77API key is set. \u00a78/sd key clear to remove it."
                        : "\u00a77Usage: /sd key <hypixel-api-key>");
                return;
            }
            if ("clear".equalsIgnoreCase(parts[2]) || "none".equalsIgnoreCase(parts[2])) {
                settings.setKey("");
                chat(self, "\u00a77API key removed.");
                return;
            }
            settings.setKey(parts[2]);
            chat(self, "\u00a77API key saved. Nicks and high FKDR will be checked.");
            return;
        }
        if ("sniper".equals(sub) || "antisniper".equals(sub)) {
            if (settings == null) {
                return;
            }
            if (parts.length < 3) {
                chat(self, settings.hasSniper()
                        ? "\u00a77Anti-sniper on. \u00a78/sd sniper off  |  /sd sniper url <url>"
                        : "\u00a77Usage: /sd sniper <api-key>  or  /sd sniper url <url>");
                return;
            }
            if ("off".equalsIgnoreCase(parts[2]) || "clear".equalsIgnoreCase(parts[2])) {
                settings.setSniperKey("");
                settings.setSniperUrl("");
                chat(self, "\u00a77External anti-sniper off. Local scores still show.");
                return;
            }
            if ("url".equalsIgnoreCase(parts[2])) {
                if (parts.length < 4) {
                    chat(self, "\u00a77Usage: /sd sniper url https://...{{name}}...");
                    return;
                }
                settings.setSniperUrl(parts[3]);
                chat(self, "\u00a77Custom anti-sniper URL saved.");
                return;
            }
            settings.setSniperKey(parts[2]);
            chat(self, "\u00a77Anti-sniper key saved.");
            return;
        }
        if ("borderless".equals(sub)) {
            if (settings == null) {
                return;
            }
            boolean on = parts.length < 3 || "on".equalsIgnoreCase(parts[2]) || "true".equalsIgnoreCase(parts[2]);
            if (parts.length >= 3 && ("off".equalsIgnoreCase(parts[2]) || "false".equalsIgnoreCase(parts[2]))) {
                on = false;
            }
            settings.setBorderless(on);
            chat(self, on ? "\u00a77Overlay stays on top (borderless fullscreen)."
                    : "\u00a77Borderless topmost off.");
            return;
        }
        if ("urchin".equals(sub) || "coral".equals(sub)) {
            if (settings == null) {
                return;
            }
            if (parts.length < 3) {
                chat(self, "\u00a77Usage: /sd urchin <api-key> \u00a78from urchin Discord /dashboard");
                return;
            }
            if ("off".equalsIgnoreCase(parts[2]) || "clear".equalsIgnoreCase(parts[2])) {
                settings.setUrchin("");
                chat(self, "\u00a77Urchin off.");
                return;
            }
            settings.setUrchin(parts[2]);
            chat(self, "\u00a77Urchin key saved. Lobby players will be tagged.");
            return;
        }
        if ("seraph".equals(sub)) {
            if (settings == null) {
                return;
            }
            if (parts.length < 3) {
                chat(self, "\u00a77Usage: /sd seraph <api-key>");
                return;
            }
            if ("off".equalsIgnoreCase(parts[2]) || "clear".equalsIgnoreCase(parts[2])) {
                settings.setSeraph("");
                chat(self, "\u00a77Seraph off.");
                return;
            }
            settings.setSeraph(parts[2]);
            chat(self, "\u00a77Seraph key saved.");
            return;
        }
        if ("nick".equals(sub) || "denick".equals(sub)) {
            if (settings == null || parts.length < 4) {
                chat(self, "\u00a77Usage: /sd nick <shown> <real>");
                return;
            }
            settings.mapNick(parts[2], parts[3]);
            chat(self, "\u00a77" + parts[2] + " \u00a78-> \u00a7f" + parts[3]);
            return;
        }
        if ("discord".equals(sub)) {
            if (settings == null) {
                return;
            }
            if (parts.length < 3) {
                chat(self, "\u00a77Usage: /sd discord <application-id> \u00a78from discord.com/developers");
                return;
            }
            if ("off".equalsIgnoreCase(parts[2]) || "clear".equalsIgnoreCase(parts[2])) {
                settings.setDiscord("");
                discord.setAppId("");
                chat(self, "\u00a77Discord presence off.");
                return;
            }
            settings.setDiscord(parts[2]);
            discord.setAppId(parts[2]);
            discord.session(sessionGames, sessionWins);
            chat(self, "\u00a77Discord presence on.");
            return;
        }
        if ("bl".equals(sub) || "blacklist".equals(sub)) {
            blacklistCommand(self, parts);
            return;
        }
        if ("aurora".equals(sub)) {
            if (settings == null) {
                return;
            }
            if (parts.length < 3) {
                chat(self, settings.hasAurora() ? "\u00a77Aurora key is set. \u00a78/sd aurora clear"
                        : "\u00a77Usage: /sd aurora <api-key> \u00a78from Vega Discord /api view");
                return;
            }
            if ("off".equalsIgnoreCase(parts[2]) || "clear".equalsIgnoreCase(parts[2])) {
                settings.setAurora("");
                chat(self, "\u00a77Aurora denick off.");
                return;
            }
            settings.setAurora(parts[2]);
            chat(self, "\u00a77Aurora key saved. Nicked players will be looked up.");
            return;
        }
        if ("clear".equals(sub) || "wipe".equals(sub)) {
            if (parts.length < 3 || !"confirm".equalsIgnoreCase(parts[2])) {
                chat(self, "\u00a77Type \u00a7f/sd clear confirm \u00a77to delete \u00a7f" + store.size()
                        + " \u00a77saved players.");
                return;
            }
            int n = store.clear();
            announced.clear();
            tabApplied.clear();
            chat(self, "\u00a77Deleted \u00a7f" + n + " \u00a77saved players.");
            Log.info("Cleared " + n + " saved players.");
            return;
        }
        chat(self, "\u00a77Unknown. Try \u00a7f/sd help\u00a77.");
    }

    private void list(Object self, int page) {
        List<FlagStore.Record> records = store.records();
        if (records.isEmpty()) {
            chat(self, "\u00a77No saved players.");
            return;
        }
        int pages = (records.size() + LIST_PAGE - 1) / LIST_PAGE;
        if (page < 1) {
            page = 1;
        }
        if (page > pages) {
            page = pages;
        }
        int from = (page - 1) * LIST_PAGE;
        int to = Math.min(from + LIST_PAGE, records.size());
        chat(self, "\u00a77Saved players \u00a7f" + records.size() + " \u00a78(page " + page + "/" + pages + ")");
        for (int i = from; i < to; i++) {
            chat(self, recordLine(records.get(i)));
        }
    }

    private String recordLine(FlagStore.Record record) {
        String name = record.name != null ? record.name : record.uuid;
        String tags = store.brackets(record);
        StringBuilder extra = new StringBuilder();
        for (String detail : record.details.values()) {
            if (detail != null && !detail.isEmpty()) {
                extra.append(extra.length() == 0 ? " \u00a78" : "\u00a78, ").append("\u00a77").append(detail);
            }
        }
        String when = record.last > 0L
                ? " \u00a78" + new SimpleDateFormat("MMM d HH:mm", Locale.ENGLISH).format(new Date(record.last))
                : "";
        String times = record.times > 0 ? " \u00a78x" + record.times : "";
        return name + " " + tags + extra + times + when;
    }

    private void copyIfClicked(Object mc) {
        try {
            String text = game.chatBoxText(mc);
            if (text == null || text.isEmpty() || text.equals(lastCopied) || !copyTargets.contains(text)) {
                return;
            }
            Hud.clip(text);
            lastCopied = text;
            game.setChatBoxText(mc, "");
        } catch (Throwable thrown) {
            Log.once("clipboard", thrown);
        }
    }

    private void lobbyWindow(Object mc, UUID selfId) {
        if (store == null) {
            return;
        }
        if (Hud.takeRefresh()) {
            lookedUp.clear();
            intel.clear();
            intelByName.clear();
            sniperLooked.clear();
            tagLooked.clear();
        }
        List<Hud.Row> rows = new ArrayList<Hud.Row>();
        int players = 0;
        try {
            if (game.canUseTab()) {
                for (Object info : game.tabEntries(mc)) {
                    if (info == null) {
                        continue;
                    }
                    UUID id = game.tabUuid(info);
                    String name = game.tabName(info);
                    if (id == null || id.equals(selfId)) {
                        continue;
                    }
                    players++;
                    addLobbyRow(rows, id, name);
                    learnSkin(info, id, name);
                }
            }
        } catch (Throwable thrown) {
            Log.once("lobby window tab", thrown);
        }
        java.util.HashSet<String> seen = new java.util.HashSet<String>();
        for (Hud.Row row : rows) {
            seen.add(row.name.toLowerCase(Locale.ROOT));
        }
        for (String extra : lobbyChat.fromChat) {
            if (seen.add(extra.toLowerCase(Locale.ROOT))) {
                addLobbyRow(rows, null, extra);
                players++;
            }
        }
        if (players == 0) {
            for (PlayerView snap : nearby) {
                if (snap.uuid == null || snap.uuid.equals(selfId)) {
                    continue;
                }
                players++;
                Object entity = nearbyEntities.get(snap.uuid);
                String name = null;
                try {
                    name = entity == null ? null : game.name(entity);
                } catch (Throwable ignored) {
                }
                addLobbyRow(rows, snap.uuid, name != null ? name : snap.uuid.toString());
            }
        }
        List<Hud.Row> saved = new ArrayList<Hud.Row>();
        for (FlagStore.Record record : store.records()) {
            if (record.flags.isEmpty()) {
                continue;
            }
            String label = record.name != null ? record.name : record.uuid;
            int times = record.times > 0 ? record.times : record.flags.size();
            saved.add(new Hud.Row(label, store.plainTags(record), times, Hud.lastLabel(record),
                    record.lastCheckAt));
        }
        Hud.show(players, rows, saved);
    }

    private void learnSkin(Object info, UUID id, String name) {
        if (denick == null || info == null || name == null) {
            return;
        }
        String hash = game.tabTextureHash(info);
        if (hash == null) {
            return;
        }
        String known = denick.realForSkin(hash);
        Intel infoRow = intelFor(id, name);
        boolean nicked = infoRow != null && infoRow.nicked;
        if (known != null && !known.equalsIgnoreCase(name)) {
            if (settings != null) {
                settings.mapNick(name, known);
            }
        } else if (!nicked) {
            denick.learn(hash, name);
        }
    }

    /**
     * Tab is membership only (real connected players vs Hypixel NPCs), not a cheat signal.
     * Empty tab — tests, or tab not ready — falls back to name / UUID version.
     */
    private Set<UUID> tabPlayerIds(Object mc, UUID selfId) {
        Set<UUID> ids = new HashSet<UUID>();
        if (!game.canUseTab()) {
            return ids;
        }
        try {
            for (Object info : game.tabEntries(mc)) {
                if (info == null) {
                    continue;
                }
                UUID id = game.tabUuid(info);
                if (id == null || id.equals(selfId)) {
                    continue;
                }
                ids.add(id);
            }
        } catch (Throwable thrown) {
            Log.once("tab ids", thrown);
        }
        return ids;
    }

    private boolean skipped(String name) {
        if (name == null) {
            return false;
        }
        if (settings != null && settings.isFriend(name)) {
            return true;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return "trnsmt".equals(lower) || "zoxide".equals(lower);
    }

    private void incoming(Object mc, Object self) {
        try {
            List<String> lines = game.receivedChat(mc);
            if (lines.size() < receivedAt) {
                receivedAt = 0;
            }
            while (receivedAt < lines.size()) {
                lobbyChat.line(lines.get(receivedAt++));
            }
            if (lobbyChat.won) {
                lobbyChat.won = false;
                sessionWins++;
            }
            if (lobbyChat.takeAutoWho() && tick - lastWhoAt > 80L) {
                lastWhoAt = tick;
                sessionGames++;
                game.sendChat(self, "/who");
                Hud.session(sessionGames, sessionWins);
                discord.session(sessionGames, sessionWins);
            } else {
                Hud.session(sessionGames, sessionWins);
            }
        } catch (Throwable thrown) {
            Log.once("incoming chat", thrown);
        }
    }

    private void friendsCommand(Object self, String[] parts) {
        if (settings == null) {
            return;
        }
        if (parts.length < 3 || "list".equalsIgnoreCase(parts[2])) {
            if (settings.friends.isEmpty()) {
                chat(self, "\u00a77No skipped players. \u00a78/sd friend <name>");
                return;
            }
            StringBuilder line = new StringBuilder("\u00a77Skipped:");
            for (String name : settings.friends) {
                line.append(" \u00a7f").append(name);
            }
            chat(self, line.toString());
            return;
        }
        if ("remove".equalsIgnoreCase(parts[2]) || "del".equalsIgnoreCase(parts[2])) {
            if (parts.length < 4) {
                chat(self, "\u00a77Usage: /sd friend remove <name>");
                return;
            }
            unfriend(self, parts[3]);
            return;
        }
        if (settings.addFriend(parts[2])) {
            chat(self, "\u00a77Will not check \u00a7f" + parts[2] + "\u00a77.");
        } else {
            chat(self, "\u00a7f" + parts[2] + " \u00a77is already skipped.");
        }
    }

    private void unfriend(Object self, String name) {
        if (settings == null) {
            return;
        }
        if (settings.removeFriend(name)) {
            chat(self, "\u00a77Now checking \u00a7f" + name + "\u00a77.");
        } else {
            chat(self, "\u00a7f" + name + " \u00a77was not skipped.");
        }
    }

    private void maybeAlt(Object self, UUID id, String name) {
        if (name == null || store == null) {
            return;
        }
        for (FlagStore.Record record : store.records()) {
            if (record == null || !store.hasCheat(record) || record.name == null) {
                continue;
            }
            if (id != null && id.toString().equals(record.uuid)) {
                continue;
            }
            if (name.equalsIgnoreCase(record.name)) {
                continue;
            }
            if (!similarName(name, record.name)) {
                continue;
            }
            note(self, id, name, FlagStore.Flag.AL, "possible alt of " + record.name);
            return;
        }
    }

    static boolean similarName(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        String x = a.toLowerCase(Locale.ROOT);
        String y = b.toLowerCase(Locale.ROOT);
        if (x.equals(y)) {
            return false;
        }
        if (digitSuffix(x, y) || digitSuffix(y, x)) {
            return true;
        }
        if (x.length() >= 6 && y.length() >= 6 && levenshtein(x, y) <= 1) {
            return true;
        }
        return false;
    }

    private static boolean digitSuffix(String longer, String shorter) {
        if (!longer.startsWith(shorter) || longer.length() <= shorter.length()) {
            return false;
        }
        String extra = longer.substring(shorter.length());
        if (extra.startsWith("_")) {
            extra = extra.substring(1);
        }
        if (extra.equals("alt") || extra.equals("alt2")) {
            return true;
        }
        if (extra.isEmpty()) {
            return false;
        }
        for (int i = 0; i < extra.length(); i++) {
            char c = extra.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    private static int levenshtein(String a, String b) {
        int n = a.length();
        int m = b.length();
        if (Math.abs(n - m) > 1) {
            return 99;
        }
        int[] prev = new int[m + 1];
        int[] cur = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= n; i++) {
            cur[0] = i;
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                int del = prev[j] + 1;
                int ins = cur[j - 1] + 1;
                int sub = prev[j - 1] + cost;
                cur[j] = Math.min(del, Math.min(ins, sub));
            }
            int[] swap = prev;
            prev = cur;
            cur = swap;
        }
        return prev[m];
    }

    private void applyHypixel(Object self) {
        Hypixel.Result result;
        while ((result = hypixel.poll()) != null) {
            Intel info = intelPut(result.id, result.name);
            info.stars = result.stars;
            info.fkdr = result.fkdr;
            info.wlr = result.wlr;
            info.finals = result.finals;
            info.wins = result.wins;
            info.winstreak = result.winstreak;
            if (info.sniper < 0) {
                info.sniper = AntiSniper.localScore(result.fkdr, result.wlr);
            }
            info.sniper = AntiSniper.withSession(info.sniper, result.lastLogin);
            info.nicked = result.nicked;
            if (result.nicked) {
                note(self, result.id, result.name, FlagStore.Flag.NK, "nicked");
                if (settings != null && settings.hasAurora() && denick != null && result.id != null
                        && auroraLooked.add(result.id)) {
                    denick.submitAurora(settings.auroraKey, result.id, result.name);
                }
            } else if (result.fkdr >= SWEAT_FKDR && result.finals >= SWEAT_FINALS) {
                String detail = (result.stars > 0 ? result.stars + " stars, " : "")
                        + String.format(Locale.US, "%.1f FKDR", result.fkdr);
                note(self, result.id, result.name, FlagStore.Flag.FK, detail);
            }
        }
    }

    private void applySniper() {
        AntiSniper.Result result;
        while ((result = snipers.poll()) != null) {
            Intel info = intel.get(result.id);
            if (info == null) {
                info = new Intel();
                intel.put(result.id, info);
            }
            info.sniper = result.score;
        }
    }

    private void applyTags(Object self) {
        Tags.Result result;
        while ((result = tagsApi.poll()) != null) {
            Intel info = intel.get(result.id);
            if (info == null) {
                info = new Intel();
                intel.put(result.id, info);
            }
            if (result.labels != null && !result.labels.isEmpty()) {
                info.extra = result.labels;
                if (tagAnnounced.add(result.id)) {
                    String who = result.name != null ? result.name : result.id.toString();
                    alert(self, "tagged: \u00a7f" + who + " \u00a7c" + result.labels, Hud.wdr(who));
                }
            }
            if (result.score > info.sniper) {
                info.sniper = result.score;
            }
        }
    }

    private void applyDenick(Object self) {
        if (denick == null) {
            return;
        }
        Denick.Result result;
        while ((result = denick.poll()) != null) {
            if (result.real == null || result.shown == null) {
                continue;
            }
            if (settings != null) {
                settings.mapNick(result.shown, result.real);
            }
            chat(self, "\u00a7f" + result.shown + " \u00a78-> \u00a7f" + result.real + " \u00a78(" + result.how + ")");
        }
    }

    private boolean overlayHud(Object self) {
        boolean dirty = false;
        Boolean tab = Hud.takeTabMarks();
        if (tab != null && settings != null) {
            settings.setTabMarks(tab);
            chat(self, tab ? "\u00a77Tab marks on." : "\u00a77Tab marks off.");
            dirty = true;
        }
        Boolean chatOn = Hud.takeAlertsChat();
        if (chatOn != null && settings != null) {
            settings.setAlertsChat(chatOn);
            chat(self, chatOn ? "\u00a77Chat alerts on." : "\u00a77Chat alerts off.");
            dirty = true;
        }
        Boolean sound = Hud.takeAlertSound();
        if (sound != null && settings != null) {
            settings.setAlertSound(sound);
            chat(self, sound ? "\u00a77Alert sound on." : "\u00a77Alert sound off.");
            dirty = true;
        }
        if (Hud.takeClear() && store != null) {
            int n = store.clear();
            announced.clear();
            tabApplied.clear();
            tagAnnounced.clear();
            chat(self, "\u00a77Deleted \u00a7f" + n + " \u00a77saved players.");
            Log.info("Cleared " + n + " saved players.");
            dirty = true;
        }
        if (Hud.takeRefresh()) {
            lookedUp.clear();
            intel.clear();
            intelByName.clear();
            sniperLooked.clear();
            tagLooked.clear();
            Hud.bump();
            dirty = true;
        }
        if (blacklist == null) {
            return dirty;
        }
        String name;
        while ((name = Hud.takeReport()) != null) {
            if (blacklist.add(name)) {
                chat(self, "\u00a7f" + name + " \u00a77blacklisted. \u00a78/wdr copied.");
            }
            dirty = true;
        }
        return dirty;
    }

    private void blacklistCommand(Object self, String[] parts) {
        if (blacklist == null) {
            return;
        }
        String action = parts.length < 3 ? "list" : parts[2].toLowerCase(Locale.ROOT);
        if ("list".equals(action)) {
            if (blacklist.names.isEmpty()) {
                chat(self, "\u00a77Blacklist empty. \u00a78/sd bl add <name>  or  /sd bl import");
                return;
            }
            StringBuilder line = new StringBuilder("\u00a77Blacklist \u00a7f" + blacklist.names.size() + "\u00a77:");
            int n = 0;
            for (String name : blacklist.names) {
                if (n++ >= 12) {
                    line.append(" \u00a78...");
                    break;
                }
                line.append(" \u00a7f").append(name);
            }
            chat(self, line.toString());
            return;
        }
        if ("add".equals(action)) {
            if (parts.length < 4) {
                chat(self, "\u00a77Usage: /sd bl add <name>");
                return;
            }
            chat(self, blacklist.add(parts[3]) ? "\u00a7f" + parts[3] + " \u00a77blacklisted."
                    : "\u00a7f" + parts[3] + " \u00a77is already on the blacklist.");
            return;
        }
        if ("remove".equals(action) || "del".equals(action)) {
            if (parts.length < 4) {
                chat(self, "\u00a77Usage: /sd bl remove <name>");
                return;
            }
            chat(self, blacklist.remove(parts[3]) ? "\u00a7f" + parts[3] + " \u00a77removed from blacklist."
                    : "\u00a7f" + parts[3] + " \u00a77was not on the blacklist.");
            return;
        }
        if ("import".equals(action)) {
            java.io.File from = blacklist.file();
            if (parts.length >= 4) {
                from = new java.io.File(parts[3]);
                if (!from.isAbsolute() && settings != null) {
                    from = new java.io.File(blacklist.file().getParentFile(), parts[3]);
                }
            }
            if (!from.isFile()) {
                chat(self, "\u00a77No file at \u00a7f" + from.getPath()
                        + "\u00a77. Drop a .txt or .json there, then \u00a7f/sd bl import");
                return;
            }
            int added = blacklist.importFile(from);
            chat(self, "\u00a77Imported \u00a7f" + added + " \u00a77names. Blacklist is \u00a7f"
                    + blacklist.names.size() + "\u00a77.");
            return;
        }
        chat(self, "\u00a77Usage: /sd bl add|remove|list|import [file]");
    }

    private static boolean npc(UUID id, String name, Set<UUID> tabIds) {
        if (id.version() == 2) {
            return true;
        }
        if (hypixelBotName(name)) {
            return true;
        }
        return tabIds != null && !tabIds.isEmpty() && !tabIds.contains(id);
    }

    /** Hypixel NPC names like {@code 7w0392l04b}: 10 lowercase alphanumerics, starts with a digit. */
    static boolean hypixelBotName(String name) {
        if (name == null || name.length() != 10) {
            return false;
        }
        if (name.charAt(0) < '0' || name.charAt(0) > '9') {
            return false;
        }
        boolean letter = false;
        for (int i = 0; i < 10; i++) {
            char c = name.charAt(i);
            if (c >= '0' && c <= '9') {
                continue;
            }
            if (c >= 'a' && c <= 'z') {
                letter = true;
                continue;
            }
            return false;
        }
        return letter;
    }

    private void addLobbyRow(List<Hud.Row> rows, UUID id, String name) {
        if (hypixelBotName(name) || skipped(name)) {
            return;
        }
        if (settings != null) {
            name = settings.realName(name);
        }
        requestIntel(id, name);
        FlagStore.Record record = store.get(id, name);
        String tags = store.plainTags(record);
        if (blacklist != null && blacklist.contains(name)) {
            tags = tags == null || tags.isEmpty() ? "[BL]" : "[BL] " + tags;
        }
        Intel info = intelFor(id, name);
        if (info != null && info.extra != null && !info.extra.isEmpty()) {
            tags = tags == null || tags.isEmpty() ? info.extra : tags + " " + info.extra;
        }
        String label = name != null ? name : id.toString();
        int times = record != null && record.times > 0 ? record.times : 0;
        long lastAt = record != null ? record.lastCheckAt : 0L;
        int stars = info == null ? -1 : info.stars;
        double fkdr = info == null ? -1 : info.fkdr;
        double wlr = info == null ? -1 : info.wlr;
        int streak = info == null ? -1 : info.winstreak;
        int finals = info == null ? -1 : info.finals;
        int wins = info == null ? -1 : info.wins;
        int sniper = info == null ? -1 : info.sniper;
        if (sniper < 0) {
            sniper = AntiSniper.localScore(fkdr, wlr);
        }
        rows.add(new Hud.Row(label, tags, times, Hud.lastLabel(record), lastAt, stars, fkdr, wlr, streak,
                finals, wins, sniper));
    }

    private Intel intelFor(UUID id, String name) {
        Intel info = id == null ? null : intel.get(id);
        if (info == null && name != null) {
            info = intelByName.get(name.toLowerCase(Locale.ROOT));
        }
        return info;
    }

    private Intel intelPut(UUID id, String name) {
        Intel info = intelFor(id, name);
        if (info == null) {
            info = new Intel();
        }
        if (id != null) {
            intel.put(id, info);
        }
        if (name != null && !name.isEmpty()) {
            intelByName.put(name.toLowerCase(Locale.ROOT), info);
        }
        return info;
    }

    private void requestIntel(UUID id, String name) {
        if (name == null || settings == null) {
            return;
        }
        String token = (id != null ? id.toString() : name).toLowerCase(Locale.ROOT);
        if (settings.hasKey() && lookedUp.add(token)) {
            hypixel.submit(settings.hypixelKey, id, name);
        }
        if (id == null) {
            return;
        }
        if (settings.hasSniper() && sniperLooked.add(id)) {
            snipers.submit(settings.sniperRequest(id, name), id, name);
        }
        if (tagLooked.add(id)) {
            if (settings.urchinKey != null && !settings.urchinKey.isEmpty()) {
                tagsApi.urchin(settings.urchinKey, id, name);
            }
            if (settings.seraphKey != null && !settings.seraphKey.isEmpty()) {
                tagsApi.seraph(settings.seraphKey, id, name);
            }
        }
    }

    private void tab(Object mc) {
        if (!game.canUseTab() || store == null) {
            return;
        }
        if (settings != null && !settings.tabMarks) {
            restoreTab(mc);
            return;
        }
        try {
            Set<UUID> seen = new HashSet<UUID>();
            for (Object info : game.tabEntries(mc)) {
                if (info == null) {
                    continue;
                }
                UUID id = game.tabUuid(info);
                String name = game.tabName(info);
                if (id == null) {
                    continue;
                }
                seen.add(id);
                Object current = game.tabDisplayName(info);
                if (current != tabApplied.get(id) && !ourTab(current)) {
                    tabOriginal.put(id, current);
                }
                String tags = store.brackets(store.get(id, name));
                if (tags.isEmpty()) {
                    if (tabApplied.containsKey(id) || ourTab(current)) {
                        game.setTabDisplayName(info, tabOriginal.get(id));
                        tabApplied.remove(id);
                    }
                    continue;
                }
                String label = (name != null ? name : id.toString()) + TAB_MARK + tags;
                Object shown = game.textComponent(label);
                game.setTabDisplayName(info, shown);
                tabApplied.put(id, shown);
            }
            tabApplied.keySet().retainAll(seen);
            tabOriginal.keySet().retainAll(seen);
        } catch (Throwable thrown) {
            Log.once("tab", thrown);
        }
    }

    private void restoreTab(Object mc) {
        if (tabApplied.isEmpty()) {
            return;
        }
        try {
            for (Object info : game.tabEntries(mc)) {
                if (info == null) {
                    continue;
                }
                UUID id = game.tabUuid(info);
                if (id == null || !tabApplied.containsKey(id)) {
                    continue;
                }
                game.setTabDisplayName(info, tabOriginal.get(id));
            }
        } catch (Throwable thrown) {
            Log.once("tab restore", thrown);
        }
        tabApplied.clear();
    }

    private boolean ourTab(Object component) {
        if (component == null) {
            return false;
        }
        try {
            String text = game.unformatted(component);
            return text != null && text.contains(TAB_MARK);
        } catch (Throwable thrown) {
            return false;
        }
    }
}
