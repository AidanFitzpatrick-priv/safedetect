package com.safedetect.agent;

import java.io.File;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.Arrays;
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

    private static final int CHAT_MEMORY = 8;
    private static final int LOOKUP_RETRIES = 3;

    private static final class Tracked {
        final Checks combat = new Checks();
        final MoveChecks movement = new MoveChecks();
        int prevHurt;
        boolean prevSwing;
        /** Tick the checks last ran; melee only judges players whose checks ran this tick. */
        long checkedTick = Long.MIN_VALUE;
    }

    private final Game game;
    private final Map<UUID, Tracked> checks = new HashMap<UUID, Tracked>();
    private final Set<UUID> announced = new HashSet<UUID>();
    private final Set<UUID> present = new HashSet<UUID>();
    private final PlayerView view = new PlayerView();
    private final List<PlayerView> nearby = new ArrayList<PlayerView>();
    private final Map<UUID, Object> nearbyEntities = new HashMap<UUID, Object>();
    private final Map<String, Integer> lookupFailures = new HashMap<String, Integer>();
    private Object[] seenChat;
    private boolean chatPrimed;
    private String lastBox;
    private int lastSentSize;
    private final Set<String> copyTargets = new LinkedHashSet<String>();
    private final Map<UUID, Object> tabOriginal = new HashMap<UUID, Object>();
    private final Map<UUID, Object> tabApplied = new HashMap<UUID, Object>();
    private final Map<UUID, String> tabLabels = new HashMap<UUID, String>();
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
    /** Lower-case names already warned about in this world. */
    private final Set<String> dodgeWarned = new HashSet<String>();
    /** Lower-case names flagged in this world; the flag alert already covered them, so no dodge warning. */
    private final Set<String> flaggedHere = new HashSet<String>();
    private Settings settings;
    private Blacklist blacklist;
    private Denick denick;
    private Encounters encounters;
    private File root;
    private final Updater updater = new Updater();
    private long worldSeq;
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
            root = dir != null ? dir : new File(".");
            store = new FlagStore(root);
            settings = new Settings(root);
            blacklist = new Blacklist(root);
            denick = new Denick(root);
            encounters = new Encounters(root);
            CheckConfig.use(CheckConfig.from(settings));
            FlagLog.open(root);
            Hud.open(root);
            Hud.bind(settings);
            if (settings.discordAppId != null && !settings.discordAppId.isEmpty()) {
                discord.setAppId(settings.discordAppId);
            }
            Log.info("Flag file loaded: " + store.size() + " saved players.");
        }
        Hud.pollCommands();
        applyKeys(self);
        store.flush(false);
        encounters.flush(false);
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
            nearby.clear();
            nearbyEntities.clear();
            lookedUp.clear();
            intel.clear();
            intelByName.clear();
            sniperLooked.clear();
            tagLooked.clear();
            tagAnnounced.clear();
            auroraLooked.clear();
            lookupFailures.clear();
            lobbyChat.reset();
            dodgeWarned.clear();
            flaggedHere.clear();
            worldSeq++;
        }
        tick += elapsed;
        if (!greeted) {
            greeted = true;
            chat(self, "\u00a77SafeDetect is running. Saved players: " + store.size()
                    + ". \u00a78Type \u00a7f/sd list\u00a78, \u00a7f/sd gui\u00a78, \u00a7f/sd clear\u00a78.");
            if (settings != null && !settings.hasKey()) {
                chat(self, "\u00a77Stats are off. \u00a78/sd key <hypixel-api-key> \u00a77from developer.hypixel.net");
            }
            long now = System.currentTimeMillis();
            String version = Updater.currentVersion();
            if (settings != null && settings.updateCheck && version != null
                    && Updater.due(settings.lastUpdateCheck, now)) {
                settings.setLastUpdateCheck(now);
                updater.start(version);
            }
        }
        String newer = updater.take();
        if (newer != null) {
            chat(self, "\u00a77SafeDetect \u00a7f" + newer + "\u00a77 is out \u00a78(you have " + Updater.currentVersion()
                    + "). Click to copy the download link.", Updater.RELEASES);
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
        Object[] tabs = tabEntries(mc);
        Set<UUID> tabIds = tabPlayerIds(tabs, selfId);
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
            if (data == null || entity == null || data.checkedTick != tick) {
                continue;
            }
            try {
                data.combat.melee(snap, snaps);
                if (data.combat.failedKillaura()) {
                    mark(self, snap.uuid, entity, FlagStore.Flag.KA, data.combat.evidence(FlagStore.Flag.KA));
                    data.combat.resetKillaura();
                }
                if (data.combat.failedSilentAura()) {
                    mark(self, snap.uuid, entity, FlagStore.Flag.SI, data.combat.evidence(FlagStore.Flag.SI));
                    data.combat.resetSilentAura();
                }
                if (data.combat.failedReach()) {
                    mark(self, snap.uuid, entity, FlagStore.Flag.RE, data.combat.evidence(FlagStore.Flag.RE));
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
        Iterator<UUID> it = checks.keySet().iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            if (!present.contains(id)) {
                it.remove();
                announced.remove(id);
            }
        }
        tab(tabs);
        lobbyWindow(self, tabs, selfId);
    }

    private Object[] tabEntries(Object mc) {
        if (!game.canUseTab()) {
            return new Object[0];
        }
        try {
            return game.tabEntries(mc);
        } catch (Throwable thrown) {
            Log.once("tab entries", thrown);
            return new Object[0];
        }
    }

    private Tracked tracked(UUID id) {
        Tracked data = checks.get(id);
        if (data == null) {
            data = new Tracked();
            checks.put(id, data);
        }
        return data;
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

        Tracked data = tracked(id);
        data.checkedTick = tick;
        Checks combat = data.combat;
        MoveChecks movement = data.movement;
        combat.update(view, tick, elapsed);
        movement.update(view, tick, elapsed, inGame, movementData);
        if (combat.failedAutoBlock()) {
            mark(self, id, player, FlagStore.Flag.AB, combat.evidence(FlagStore.Flag.AB));
            combat.resetAutoBlock();
        }
        if (combat.failedNoSlow()) {
            mark(self, id, player, FlagStore.Flag.NS, combat.evidence(FlagStore.Flag.NS));
            combat.resetNoSlow();
        }
        if (combat.failedLegitScaffold()) {
            mark(self, id, player, FlagStore.Flag.LS, combat.evidence(FlagStore.Flag.LS));
            combat.resetLegitScaffold();
        }
        if (combat.failedAutoclicker()) {
            mark(self, id, player, FlagStore.Flag.AC, combat.evidence(FlagStore.Flag.AC));
            combat.resetAutoclicker();
        }
        if (movement.failedSprintScaffold()) {
            mark(self, id, player, FlagStore.Flag.SS, movement.evidence(FlagStore.Flag.SS));
            movement.resetSprintScaffold();
        }
        if (movement.failedTower()) {
            mark(self, id, player, FlagStore.Flag.TW, movement.evidence(FlagStore.Flag.TW));
            movement.resetTower();
        }
        if (movement.failedSpeed()) {
            mark(self, id, player, FlagStore.Flag.SP, movement.evidence(FlagStore.Flag.SP));
            movement.resetSpeed();
        }
        if (movement.failedSnapAim()) {
            mark(self, id, player, FlagStore.Flag.SA, movement.evidence(FlagStore.Flag.SA));
            movement.resetSnapAim();
        }
        if (movement.failedVelocity()) {
            mark(self, id, player, FlagStore.Flag.VL, movement.evidence(FlagStore.Flag.VL));
            movement.resetVelocity();
        }
        if (movement.failedGodBridge()) {
            mark(self, id, player, FlagStore.Flag.GB, movement.evidence(FlagStore.Flag.GB));
            movement.resetGodBridge();
        }
        if (movement.failedDiagonal()) {
            mark(self, id, player, FlagStore.Flag.DS, movement.evidence(FlagStore.Flag.DS));
            movement.resetDiagonal();
        }
        if (movement.failedTelly()) {
            mark(self, id, player, FlagStore.Flag.TL, movement.evidence(FlagStore.Flag.TL));
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
        Tracked data = tracked(id);
        snap.swingStart = snap.swingProgressInt == 1 || (snap.swinging && !data.prevSwing);
        data.prevSwing = snap.swinging;
        snap.riding = game.riding(player);
        snap.held = game.held(player);
        snap.speedAmplifier = game.speedAmplifier(player);
        snap.jumpAmplifier = snap.held == Game.HELD_BLOCK ? game.jumpAmplifier(player) : -1;
        snap.hurtTime = game.hurtTime(player);
        snap.prevHurtTime = data.prevHurt;
        data.prevHurt = snap.hurtTime;
        snap.climbingOrSwimming = movementData && game.climbingOrSwimming(player);
        snap.invisible = game.invisible(player);
        nearby.add(snap);
        nearbyEntities.put(id, player);
        return snap;
    }

    /** Disabled checks are skipped here; callers still reset them so their state does not build up. */
    private void mark(Object self, UUID id, Object player, FlagStore.Flag flag, String evidence) throws Exception {
        if (!CheckConfig.current().enabled(flag)) {
            return;
        }
        String check = CheckConfig.label(flag);
        String name = player == null ? null : game.name(player);
        if (store.add(id, name, flag, check, evidence)) {
            String who = name != null ? name : id.toString();
            flaggedHere.add(who.toLowerCase(Locale.ROOT));
            String why = evidence == null || evidence.isEmpty() ? "" : " (" + evidence + ")";
            if (name != null) {
                alert(self, name + " \u00a77failed \u00a7c" + check + (why.isEmpty() ? "" : " \u00a78" + why.trim()),
                        Hud.wdr(name));
            }
            FlagLog.line(who, check + why);
            Log.info(who + " failed " + check + why);
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

    /**
     * Vanilla skips adding a message to the sent history when it repeats the previous one. A repeat shows
     * up as the chat screen closing on that same text while the history did not grow.
     */
    private void commands(Object mc, Object self) {
        try {
            List<?> sent = game.sentChat(mc);
            if (sent == null) {
                return;
            }
            String box = game.chatBoxText(mc);
            int size = sent.size();
            if (sentAt > size) {
                sentAt = 0;
            }
            boolean grew = sentAt < size;
            while (sentAt < size) {
                Object line = sent.get(sentAt++);
                if (line instanceof String) {
                    handleCommand(self, ((String) line).trim());
                }
            }
            if (!grew && box == null && lastBox != null && size > 0 && size == lastSentSize) {
                Object last = sent.get(size - 1);
                if (last instanceof String && lastBox.equals(((String) last).trim())) {
                    handleCommand(self, lastBox);
                }
            }
            lastBox = box == null || box.trim().isEmpty() ? null : box.trim();
            lastSentSize = size;
        } catch (Throwable thrown) {
            Log.once("command", thrown);
        }
    }

    private void keyWarning(Object self) {
        chat(self, "\u00a78Chat commands are also sent to the server. Use the overlay \u00a77Keys \u00a78button instead.");
    }

    private void applyKeys(Object self) {
        Map<String, String> keys;
        while ((keys = Hud.takeKeys()) != null) {
            if (settings == null) {
                return;
            }
            List<String> changed = new ArrayList<String>();
            for (Map.Entry<String, String> entry : keys.entrySet()) {
                String value = entry.getValue() == null ? "" : entry.getValue().trim();
                if (!settings.setKeyNamed(entry.getKey(), value)) {
                    continue;
                }
                if ("discord".equals(entry.getKey())) {
                    discord.setAppId(value);
                    discord.session(sessionGames, sessionWins);
                }
                if ("hypixel".equals(entry.getKey())) {
                    lookedUp.clear();
                    lookupFailures.clear();
                } else if ("urchin".equals(entry.getKey()) || "seraph".equals(entry.getKey())) {
                    tagLooked.clear();
                } else if (entry.getKey().startsWith("sniper")) {
                    sniperLooked.clear();
                }
                changed.add(entry.getKey() + (value.isEmpty() ? " cleared" : " set"));
            }
            if (!changed.isEmpty()) {
                StringBuilder line = new StringBuilder("\u00a77Keys updated: \u00a7f");
                for (int i = 0; i < changed.size(); i++) {
                    line.append(i == 0 ? "" : "\u00a77, \u00a7f").append(changed.get(i));
                }
                chat(self, line.toString());
            }
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
            chat(self, "\u00a77/sd check <name> \u00a78one player, evidence and encounters");
            chat(self, "\u00a77/sd clear confirm \u00a78delete every saved player");
            chat(self, "\u00a77/sd gui \u00a78reopen the lobby window \u00a78(gear button: themes, columns, alerts)");
            chat(self, "\u00a77/sd checks [code on|off] \u00a78list or toggle checks");
            chat(self, "\u00a77/sd dodge [on|off] \u00a78dodge warnings in lobbies");
            chat(self, "\u00a77/sd export \u00a78flags and encounters to CSV");
            chat(self, "\u00a77/sd reload \u00a78re-read config files");
            chat(self, "\u00a77/sd friend <name> \u00a78never check them");
            chat(self, "\u00a77/sd unfriend <name>");
            chat(self, "\u00a77API keys: \u00a7fKeys \u00a77button in the overlay \u00a78(chat commands also reach the server)");
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
            chat(self, record != null ? recordLine(record) : "\u00a77No flags for \u00a7f" + name + "\u00a77.");
            if (record != null) {
                for (Map.Entry<String, String> detail : record.evidence.entrySet()) {
                    chat(self, "\u00a78  " + detail.getKey() + ": \u00a77" + detail.getValue());
                }
            }
            Encounters.Entry seen = encounters == null ? null
                    : encounters.get(record != null && record.uuid != null ? parseUuid(record.uuid) : null, name);
            if (seen != null) {
                SimpleDateFormat day = new SimpleDateFormat("MMM d yyyy", Locale.ENGLISH);
                chat(self, "\u00a77Seen \u00a7f" + seen.count + "x\u00a77, first \u00a7f" + day.format(new Date(seen.first))
                        + "\u00a77, last \u00a7f" + day.format(new Date(seen.last)));
            }
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
            keyWarning(self);
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
            keyWarning(self);
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
            keyWarning(self);
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
            keyWarning(self);
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
        if ("checks".equals(sub)) {
            checksCommand(self, parts);
            return;
        }
        if ("export".equals(sub)) {
            File out = Export.write(root, store, encounters, new Date());
            chat(self, "\u00a77Exported " + store.size() + " players to \u00a7f" + out.getName()
                    + "\u00a77. \u00a78Click to copy the path.", out.getAbsolutePath());
            return;
        }
        if ("reload".equals(sub)) {
            reload(self);
            return;
        }
        if ("dodge".equals(sub)) {
            if (settings == null) {
                return;
            }
            if (parts.length >= 3) {
                boolean on = "on".equalsIgnoreCase(parts[2]);
                if (!on && !"off".equalsIgnoreCase(parts[2])) {
                    chat(self, "\u00a77Usage: /sd dodge [on|off]");
                    return;
                }
                settings.set("dodgeEnabled", on);
            }
            chat(self, String.format(Locale.US, "\u00a77Dodge warnings %s\u00a77: FKDR \u00a7f%s\u00a77 (%d+ stars), sniper \u00a7f%s"
                    + "\u00a77, blacklist %s\u00a77, flagged %s\u00a77, tags %s",
                    settings.dodgeEnabled ? "\u00a7aon" : "\u00a7coff",
                    settings.dodgeFkdr > 0 ? String.format(Locale.US, "%.1f", settings.dodgeFkdr) : "off",
                    settings.dodgeStars, settings.dodgeSniper > 0 ? String.valueOf(settings.dodgeSniper) : "off",
                    onOff(settings.dodgeBlacklist), onOff(settings.dodgeFlagged), onOff(settings.dodgeTags)));
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
            keyWarning(self);
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

    private void lobbyWindow(Object self, Object[] tabs, UUID selfId) {
        if (store == null) {
            return;
        }
        List<Hud.Row> rows = new ArrayList<Hud.Row>();
        int players = 0;
        try {
            for (Object info : tabs) {
                if (info == null) {
                    continue;
                }
                UUID id = game.tabUuid(info);
                String name = game.tabName(info);
                if (id == null || id.equals(selfId) || notPlayer(id, name)) {
                    continue;
                }
                players++;
                addLobbyRow(rows, id, name, info);
                learnSkin(info, id, name);
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
                addLobbyRow(rows, null, extra, null);
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
                addLobbyRow(rows, snap.uuid, name != null ? name : snap.uuid.toString(), null);
            }
        }
        dodgeWarnings(self, rows);
        List<Hud.Row> saved = new ArrayList<Hud.Row>();
        for (FlagStore.Record record : store.records()) {
            if (record.flags.isEmpty()) {
                continue;
            }
            String label = record.name != null ? record.name : record.uuid;
            if (record.name != null && notPlayer(record.uuid == null ? null : parseUuid(record.uuid), record.name)) {
                continue;
            }
            int times = record.times > 0 ? record.times : record.flags.size();
            Hud.Row row = new Hud.Row(label, store.plainTags(record), times, Hud.lastLabel(record),
                    record.lastCheckAt);
            row.blacklisted = blacklist != null && blacklist.contains(label);
            row.friend = skipped(label);
            row.evidence = newestDetail(record);
            saved.add(row);
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
    private Set<UUID> tabPlayerIds(Object[] tabs, UUID selfId) {
        Set<UUID> ids = new HashSet<UUID>();
        try {
            for (Object info : tabs) {
                if (info == null) {
                    continue;
                }
                UUID id = game.tabUuid(info);
                if (id == null || id.equals(selfId) || notPlayer(id, game.tabName(info))) {
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
        return settings != null && settings.isFriend(name);
    }

    private void incoming(Object mc, Object self) {
        try {
            Object[] lines = game.chatLines(mc);
            for (int i = freshChat(lines) - 1; i >= 0; i--) {
                lobbyChat.line(game.chatLineText(lines[i]));
            }
            seenChat = Arrays.copyOf(lines, Math.min(CHAT_MEMORY, lines.length));
            if (lobbyChat.won) {
                lobbyChat.won = false;
                sessionWins++;
                discord.session(sessionGames, sessionWins);
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

    /**
     * How many lines at the front of the newest-first chat list arrived since the last frame. Several
     * recent lines are remembered because a server can delete a single line by id. The first call only
     * primes, so chat from before the agent started is not replayed.
     */
    private int freshChat(Object[] lines) {
        if (!chatPrimed) {
            chatPrimed = true;
            return 0;
        }
        if (seenChat == null || seenChat.length == 0) {
            return lines.length;
        }
        for (int i = 0; i < lines.length; i++) {
            for (Object seen : seenChat) {
                if (lines[i] == seen) {
                    return i;
                }
            }
        }
        return lines.length;
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
        for (FlagStore.Record record : store.cheaters()) {
            if (record.name == null) {
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
        String failed;
        while ((failed = hypixel.pollFailed()) != null) {
            Integer tries = lookupFailures.get(failed);
            int next = tries == null ? 1 : tries.intValue() + 1;
            lookupFailures.put(failed, next);
            if (next < LOOKUP_RETRIES) {
                lookedUp.remove(failed);
            }
        }
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
            if (result.nicked && !notPlayer(result.id, result.name)) {
                note(self, result.id, result.name, FlagStore.Flag.NK, "nicked");
                if (settings != null && settings.hasAurora() && denick != null && result.id != null
                        && auroraLooked.add(result.id)
                        && !denick.submitAurora(settings.auroraKey, result.id, result.name)) {
                    auroraLooked.remove(result.id);
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
        Map<String, Object> values;
        while ((values = Hud.takeSet()) != null) {
            if (settings == null) {
                continue;
            }
            for (Map.Entry<String, Object> entry : values.entrySet()) {
                if (!settings.set(entry.getKey(), entry.getValue())) {
                    Log.info("Overlay sent an unknown or invalid option: " + entry.getKey());
                    continue;
                }
                dirty = true;
                String label = OPTION_LABELS.get(entry.getKey());
                if (label != null && entry.getValue() instanceof Boolean) {
                    chat(self, "\u00a77" + label + (Boolean.TRUE.equals(entry.getValue()) ? " on." : " off."));
                }
            }
            optionsChanged();
        }
        String[] action;
        while ((action = Hud.takeAction()) != null) {
            rowAction(self, action[0], action[1]);
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
            lookupFailures.clear();
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

    private static final Map<String, String> OPTION_LABELS = new HashMap<String, String>();

    static {
        OPTION_LABELS.put("tabMarks", "Tab marks");
        OPTION_LABELS.put("alertsChat", "Chat alerts");
        OPTION_LABELS.put("alertSound", "Alert sound");
        OPTION_LABELS.put("dodgeEnabled", "Dodge warnings");
        OPTION_LABELS.put("updateCheck", "Update check");
    }

    /** Called after any game option changes so derived state follows. */
    private void optionsChanged() {
        if (settings != null) {
            CheckConfig.use(CheckConfig.from(settings));
        }
    }

    /** Re-reads every config file after saving pending changes, for edits made outside the game. */
    private void reload(Object self) {
        store.flush(true);
        encounters.flush(true);
        Files2.drain(3000L);
        settings = new Settings(root);
        blacklist.reload();
        store.reload();
        encounters.reload();
        CheckConfig.use(CheckConfig.from(settings));
        Hud.bind(settings);
        Hud.bump();
        chat(self, "\u00a77Reloaded: \u00a7f" + store.size() + "\u00a77 saved players, \u00a7f" + blacklist.names.size()
                + "\u00a77 blacklisted, \u00a7f" + encounters.size() + "\u00a77 encounters.");
    }

    private static String onOff(boolean on) {
        return on ? "\u00a7aon" : "\u00a78off";
    }

    private void checksCommand(Object self, String[] parts) {
        if (settings == null) {
            return;
        }
        if (parts.length >= 4) {
            FlagStore.Flag flag = Settings.flag(parts[2]);
            boolean on = "on".equalsIgnoreCase(parts[3]) || "true".equalsIgnoreCase(parts[3]);
            boolean off = "off".equalsIgnoreCase(parts[3]) || "false".equalsIgnoreCase(parts[3]);
            if (flag == null || !Arrays.asList(CheckConfig.CHECKS).contains(flag) || (!on && !off)) {
                chat(self, "\u00a77Usage: /sd checks <code> on|off \u00a78e.g. /sd checks SP off");
                return;
            }
            settings.setCheckEnabled(flag.name(), on);
            optionsChanged();
            chat(self, "\u00a77" + CheckConfig.label(flag) + (on ? " on." : " off."));
            return;
        }
        CheckConfig config = CheckConfig.current();
        StringBuilder line = new StringBuilder("\u00a77Checks:");
        for (FlagStore.Flag flag : CheckConfig.CHECKS) {
            line.append(config.enabled(flag) ? " \u00a7a" : " \u00a78").append(flag.name());
        }
        chat(self, line.toString());
        chat(self, String.format(Locale.US, "\u00a77Sensitivity \u00a7f%s\u00a77, reach \u00a7f%.2f\u00a77, aura angle \u00a7f%.0f"
                + "\u00a77, CPS \u00a7f%d\u00a77, speed \u00a7f%.2f \u00a78/sd checks <code> on|off", config.sensitivity,
                config.reachFlag, config.kaAngle, config.acMinCps, config.speedLimit));
    }

    private void rowAction(Object self, String action, String name) {
        if ("unblacklist".equals(action)) {
            if (blacklist != null && blacklist.remove(name)) {
                chat(self, "\u00a7f" + name + " \u00a77removed from blacklist.");
            }
        } else if ("friend".equals(action)) {
            if (settings != null && settings.addFriend(name)) {
                chat(self, "\u00a77Will not check \u00a7f" + name + "\u00a77.");
            }
        } else if ("unfriend".equals(action)) {
            unfriend(self, name);
        }
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
        if (notPlayer(id, name)) {
            return true;
        }
        return tabIds != null && !tabIds.isEmpty() && !tabIds.contains(id);
    }

    /** Hypixel NPCs: version-2 UUIDs, names Mojang would never allow, or generated bot names. */
    static boolean notPlayer(UUID id, String name) {
        if (id != null && id.version() == 2) {
            return true;
        }
        return name != null && (!validName(name) || hypixelBotName(name));
    }

    /** Minecraft account names: 1-16 of letters, digits and underscore. */
    static boolean validName(String name) {
        if (name.isEmpty() || name.length() > 16) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!(c >= 'a' && c <= 'z') && !(c >= 'A' && c <= 'Z') && !(c >= '0' && c <= '9') && c != '_') {
                return false;
            }
        }
        return true;
    }

    /**
     * Hypixel NPC names like {@code 7w0392l04b} or {@code a0xs6blwe3}: 10 lowercase alphanumerics where
     * letters and digits switch back and forth at least three times, which real names like
     * {@code john123456} don't.
     */
    static boolean hypixelBotName(String name) {
        if (name == null || name.length() != 10) {
            return false;
        }
        int switches = 0;
        for (int i = 0; i < 10; i++) {
            char c = name.charAt(i);
            boolean digit = c >= '0' && c <= '9';
            if (!digit && (c < 'a' || c > 'z')) {
                return false;
            }
            if (i > 0) {
                char prev = name.charAt(i - 1);
                if (digit != (prev >= '0' && prev <= '9')) {
                    switches++;
                }
            }
        }
        return switches >= 3;
    }

    private Hud.Row addLobbyRow(List<Hud.Row> rows, UUID id, String name, Object tabInfo) {
        if (notPlayer(id, name)) {
            return null;
        }
        boolean friend = skipped(name);
        if (settings != null) {
            name = settings.realName(name);
        }
        if (!friend) {
            requestIntel(id, name);
        }
        FlagStore.Record record = store.get(id, name);
        String tags = store.plainTags(record);
        boolean listed = blacklist != null && blacklist.contains(name);
        if (listed) {
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
        Hud.Row row = new Hud.Row(label, tags, times, Hud.lastLabel(record), lastAt, stars, fkdr, wlr, streak,
                finals, wins, sniper);
        row.blacklisted = listed;
        row.friend = friend;
        row.team = game.tabTeamColor(tabInfo, id == null ? null : nearbyEntities.get(id));
        row.evidence = newestDetail(record);
        boolean cheat = store.hasCheat(record) && !flaggedHere.contains(label.toLowerCase(Locale.ROOT));
        row.threat = Dodge.from(settings).evaluate(friend, listed, cheat,
                info == null ? "" : info.extra, info == null ? -1 : sniper, fkdr, stars);
        Encounters.Entry seen = encounters == null ? null : encounters.saw(id, label, worldSeq);
        if (seen != null) {
            row.seen = seen.count;
            row.seenAt = seen.previous;
        }
        rows.add(row);
        return row;
    }

    /** One chat line, sound and title per threatening player per world. */
    private void dodgeWarnings(Object self, List<Hud.Row> rows) {
        for (Hud.Row row : rows) {
            if (row.threat == null || row.threat.isEmpty() || !dodgeWarned.add(row.name.toLowerCase(Locale.ROOT))) {
                continue;
            }
            chat(self, "\u00a7c\u00a7lDODGE? \u00a7f" + row.name + " \u00a77- \u00a7c" + row.threat, Hud.wdr(row.name));
            Log.info("Dodge warning: " + row.name + " - " + row.threat);
            try {
                if (settings == null || settings.alertSound) {
                    game.playSound(self, "mob.wither.spawn", 0.4f, 1.2f);
                }
                game.title(game.minecraft(), "\u00a7cDodge?", "\u00a7f" + row.name + " \u00a77" + row.threat);
            } catch (Throwable thrown) {
                Log.once("dodge alert", thrown);
            }
        }
    }

    private static UUID parseUuid(String text) {
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException bad) {
            return null;
        }
    }

    /** Detail text of the flag saved most recently, or empty. */
    private static String newestDetail(FlagStore.Record record) {
        if (record == null) {
            return "";
        }
        if (record.lastCheck != null && !record.lastCheck.isEmpty()) {
            return record.lastCheck;
        }
        String newest = "";
        for (String detail : record.details.values()) {
            if (detail != null && !detail.isEmpty()) {
                newest = detail;
            }
        }
        return newest;
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
        String token = Hypixel.token(id, name);
        if (settings.hasKey() && lookedUp.add(token) && !hypixel.submit(settings.hypixelKey, id, name)) {
            lookedUp.remove(token);
        }
        if (id == null) {
            return;
        }
        if (settings.hasSniper() && sniperLooked.add(id) && !snipers.submit(settings.sniperRequest(id, name), id, name)) {
            sniperLooked.remove(id);
        }
        if (tagLooked.add(id)) {
            boolean queued = true;
            if (settings.urchinKey != null && !settings.urchinKey.isEmpty()) {
                queued &= tagsApi.urchin(settings.urchinKey, id, name);
            }
            if (settings.seraphKey != null && !settings.seraphKey.isEmpty()) {
                queued &= tagsApi.seraph(settings.seraphKey, id, name);
            }
            if (!queued) {
                tagLooked.remove(id);
            }
        }
    }

    private void tab(Object[] tabs) {
        if (!game.canUseTab() || store == null) {
            return;
        }
        if (settings != null && !settings.tabMarks) {
            restoreTab(tabs);
            return;
        }
        try {
            Set<UUID> seen = new HashSet<UUID>();
            for (Object info : tabs) {
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
                        tabLabels.remove(id);
                    }
                    continue;
                }
                String label = (name != null ? name : id.toString()) + TAB_MARK + tags;
                if (current != null && current == tabApplied.get(id) && label.equals(tabLabels.get(id))) {
                    continue;
                }
                Object shown = game.textComponent(label);
                game.setTabDisplayName(info, shown);
                tabApplied.put(id, shown);
                tabLabels.put(id, label);
            }
            tabApplied.keySet().retainAll(seen);
            tabOriginal.keySet().retainAll(seen);
            tabLabels.keySet().retainAll(seen);
        } catch (Throwable thrown) {
            Log.once("tab", thrown);
        }
    }

    private void restoreTab(Object[] tabs) {
        if (tabApplied.isEmpty()) {
            return;
        }
        try {
            for (Object info : tabs) {
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
        tabLabels.clear();
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
