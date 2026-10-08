package com.safedetect.agent;

import java.io.File;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;

/** Pure-logic checks that need no game. Lives in the agent package to reach package-private code. */
public final class UnitChecks {
    private static boolean ok = true;

    private UnitChecks() {
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        Object activity = Json.parse(DiscordPresence.activityJson(3, 1, 1234));
        check("discord activity is valid JSON", activity instanceof Map);
        Map<String, Object> body = (Map<String, Object>) activity;
        check("discord nonce is a string", "sd3".equals(body.get("nonce")));
        check("discord handshake is valid JSON", Json.parse(DiscordPresence.handshakeJson("123")) instanceof Map);

        LobbyChat chat = new LobbyChat();
        chat.line("[MVP+] Spoofer: VICTORY!");
        check("player chat does not count as a win", !chat.won);
        chat.line("[TEAM] [VIP] Someone: you won");
        check("team chat does not count as a win", !chat.won);
        chat.line("Party > [MVP++] Friend: the game starts in 1 second");
        check("party chat does not trigger /who", !chat.takeAutoWho());
        chat.line("VICTORY!");
        check("victory banner counts as a win", chat.won);
        chat.line("The game starts in 1 second!");
        check("game start triggers /who", chat.takeAutoWho());
        chat.line("[MVP+] Ghosty has joined (2/16)!");
        chat.line("ONLINE: Alpha, Beta");
        check("join and /who names are collected", chat.fromChat.contains("Ghosty") && chat.fromChat.contains("Beta"));
        chat.reset();
        check("reset clears lobby names", chat.fromChat.isEmpty() && !chat.won);
        check("ranked player chat is detected", LobbyChat.playerChat("[123\u272b] [MVP+] Name: hello"));
        check("server line is not player chat", !LobbyChat.playerChat("Ghosty has joined (2/16)!"));

        String url = AntiSniper.expand("https://example.com/api", "", UUID.randomUUID(), "a b&c");
        check("fallback sniper name is encoded", url.endsWith("&name=a+b%26c"));

        check("digit suffix alt", Detector.similarName("Blocker2", "Blocker"));
        check("different names are not alts", !Detector.similarName("Alpha", "Bravo"));
        check("hypixel bot name", Detector.hypixelBotName("7w0392l04b"));
        check("bot name starting with a letter", Detector.hypixelBotName("a0xs6blwe3"));
        check("bot name with few digits", Detector.hypixelBotName("khwdftg0s5"));
        check("real name with number suffix", !Detector.hypixelBotName("john123456"));
        check("coloured npc name", Detector.notPlayer(null, "\u00a7eGeneral Daku"));
        check("npc name with space", Detector.notPlayer(null, "Firework Frank"));
        check("version 2 uuid is npc",
                Detector.notPlayer(UUID.fromString("0f8e2d1c-3b4a-2c5d-8e6f-7a8b9c0d1e2f"), "Steve"));
        check("normal player", !Detector.notPlayer(UUID.randomUUID(), "xXSweatXx"));

        try {
            File dir = Files.createTempDirectory("sdunit").toFile();
            dodge();
            checkConfig(dir);
            liveChecks();
            encounters(dir);
            export();
            themesAndPrefs(dir);
            updater();
            chatHover();
            plugins(dir);
        } catch (Exception thrown) {
            thrown.printStackTrace();
            check("new unit checks ran", false);
        }

        System.out.println(ok ? "ALL UNIT CHECKS PASSED" : "SOME UNIT CHECKS FAILED");
        System.exit(ok ? 0 : 1);
    }

    private static void dodge() {
        Dodge rules = new Dodge(true, true, true, true, 8.0, 100, 60);
        check("dodge: blacklisted", "blacklisted".equals(rules.evaluate(false, true, false, "", -1, -1, -1)));
        check("dodge: flagged", "flagged".equals(rules.evaluate(false, false, true, "", -1, -1, -1)));
        check("dodge: tagged", rules.evaluate(false, false, false, "Closet", -1, -1, -1).startsWith("tagged"));
        check("dodge: urchin", "Urchin [U:Closet]".equals(rules.evaluate(false, false, false, "[U:Closet]", -1, -1, -1)));
        check("check meaning killaura", CheckConfig.meaning(FlagStore.Flag.KA).length() > 8);
        check("tag brand urchin", "[U:Cheater]".equals(Tags.brand("U", "[Cheater]")));
        check("tag brand sd", "[SD:AB] [SD:SN]".equals(Tags.brand("SD", "[AB] [SN]")));
        check("tag brand already prefixed", "[U:Cheater]".equals(Tags.brand("U", "[U:Cheater]")));
        check("dodge: sniper", "sniper 75".equals(rules.evaluate(false, false, false, "", 75, -1, -1)));
        check("dodge: high FKDR", "9.5 FKDR".equals(rules.evaluate(false, false, false, "", -1, 9.5, 300)));
        check("dodge: FKDR below star minimum", rules.evaluate(false, false, false, "", -1, 9.5, 50) == null);
        check("dodge: unknown stats never trigger", rules.evaluate(false, false, false, "", -1, -1, -1) == null);
        check("dodge: friends never trigger", rules.evaluate(true, true, true, "x", 99, 20, 900) == null);
        check("dodge: party never trigger",
                rules.evaluate(false, true, true, true, "x", 99, 20, 900) == null);
        LobbyChat partyChat = new LobbyChat();
        partyChat.line("You have joined [MVP+] Pal's party");
        check("party: joined you", LobbyIntel.inParty(partyChat, "Pal"));
        partyChat.line("Sweat joined the party");
        check("party: member joined", LobbyIntel.inParty(partyChat, "Sweat"));
        check("nick: version-1 uuid",
                LobbyIntel.nickConfidence(UUID.fromString("11111111-1111-1111-1111-111111111111"), "shown", null, false)
                        .startsWith("high"));
        check("nick: saved NK only",
                LobbyIntel.nickConfidence(UUID.randomUUID(), "shown", null, true).startsWith("mid"));
        check("nick: real account empty",
                LobbyIntel.nickConfidence(UUID.randomUUID(), "Steve", null, false).isEmpty());
        check("overlay key default is none", "None".equals(OverlayKeys.name(OverlayKeys.NONE)));
        check("overlay key ignores legacy Right Shift", OverlayKeys.clamp(54) == OverlayKeys.NONE);
        Dodge off = new Dodge(false, true, true, true, 8.0, 0, 60);
        check("dodge: disabled", off.evaluate(false, true, true, "x", 99, 20, 900) == null);
        Dodge noBl = new Dodge(true, false, true, true, 0, 0, 0);
        check("dodge: blacklist rule off, FKDR and sniper 0 mean off",
                noBl.evaluate(false, true, false, "", 99, 50, 900) == null);
    }

    private static void checkConfig(File dir) {
        Settings settings = new Settings(dir);
        check("settings: unknown option rejected", !settings.set("nope", Boolean.TRUE));
        check("settings: wrong type rejected", !settings.set("tabMarks", "yes"));
        check("settings: out of range rejected", !settings.set("reachFlag", 9.0));
        check("BB is retired", CheckConfig.retired(FlagStore.Flag.BB) && !CheckConfig.live(FlagStore.Flag.BB));
        check("FL is retired", CheckConfig.retired(FlagStore.Flag.FL) && !CheckConfig.live(FlagStore.Flag.FL));
        check("KY is live", CheckConfig.live(FlagStore.Flag.KY));
        check("settings: retired check cannot toggle", !settings.set("check.BB", Boolean.FALSE));
        check("settings: check toggle accepted", settings.set("check.SP", Boolean.FALSE));
        check("settings: sensitivity accepted", settings.set("sensitivity", "strict"));
        CheckConfig config = CheckConfig.from(settings);
        check("checks: disabled check is off", !config.enabled(FlagStore.Flag.SP));
        check("checks: other checks stay on", config.enabled(FlagStore.Flag.KA));
        check("checks: strict scales down", config.scaled(20) == 15);
        settings.set("sensitivity", "lenient");
        check("checks: lenient scales up", CheckConfig.from(settings).scaled(20) == 30);
        check("checks: scaled never below 1", CheckConfig.from(settings).scaled(0) >= 1);
        Files2.drain(3000L);
        Settings again = new Settings(dir);
        check("settings: disabled check survives reload", !CheckConfig.from(again).enabled(FlagStore.Flag.SP));
    }

    private static PlayerView pv() {
        PlayerView view = new PlayerView();
        view.uuid = UUID.randomUUID();
        view.onGround = true;
        view.speedAmplifier = -1;
        view.jumpAmplifier = -1;
        return view;
    }

    private static void liveChecks() {
        CheckConfig.use(new CheckConfig(java.util.Collections.<String>emptySet(), CheckConfig.DEFAULT_REACH,
                CheckConfig.DEFAULT_KA_ANGLE, CheckConfig.DEFAULT_AC_CPS, CheckConfig.DEFAULT_SPEED, "normal"));

        PlayerView src = pv();
        src.posX = 1.5;
        src.swingStart = true;
        src.held = Game.HELD_BLOCK;
        PlayerView dest = pv();
        dest.copyFrom(src);
        check("view: copyFrom keeps new fields", dest.posX == 1.5 && dest.swingStart && dest.held == Game.HELD_BLOCK);

        MoveChecks speed = new MoveChecks();
        PlayerView runner = pv();
        for (int t = 0; t < 80; t++) {
            runner.posX = t * 0.50;
            speed.update(runner, t, 1, true, true);
        }
        check("speed: sustained 0.50 b/t flags", speed.failedSpeed());

        MoveChecks lobby = new MoveChecks();
        PlayerView flyer = pv();
        for (int t = 0; t < 80; t++) {
            flyer.posX = t * 0.50;
            lobby.update(flyer, t, 1, false, true);
        }
        check("speed: lobby movement does not flag", !lobby.failedSpeed());

        MoveChecks teleport = new MoveChecks();
        PlayerView jumper = pv();
        jumper.posX = 0;
        teleport.update(jumper, 0, 1, true, true);
        jumper.posX = 8;
        teleport.update(jumper, 1, 1, true, true);
        for (int t = 2; t < 30; t++) {
            jumper.posX = 8 + (t - 2) * 0.50;
            teleport.update(jumper, t, 1, true, true);
        }
        check("speed: teleport gap resets before it can flag", !teleport.failedSpeed());

        Checks reach = new Checks();
        PlayerView attacker = pv();
        attacker.posY = 0;
        attacker.swingStart = true;
        attacker.swinging = true;
        PlayerView victim = pv();
        victim.posX = 4.2;
        victim.hurtTime = 10;
        victim.prevHurtTime = 0;
        reach.melee(attacker, new PlayerView[] { attacker, victim });
        attacker.swingStart = true;
        reach.melee(attacker, new PlayerView[] { attacker, victim });
        attacker.swingStart = true;
        reach.melee(attacker, new PlayerView[] { attacker, victim });
        check("reach: 3.7+ over three swings flags", reach.failedReach());

        Checks vanilla = new Checks();
        PlayerView close = pv();
        close.swingStart = true;
        close.swinging = true;
        PlayerView near = pv();
        near.posX = 2.4;
        near.hurtTime = 10;
        vanilla.melee(close, new PlayerView[] { close, near });
        close.swingStart = true;
        vanilla.melee(close, new PlayerView[] { close, near });
        close.swingStart = true;
        vanilla.melee(close, new PlayerView[] { close, near });
        check("reach: vanilla distance does not flag", !vanilla.failedReach());

        MoveChecks snap = new MoveChecks();
        PlayerView look = pv();
        float yaw = 0;
        for (int i = 0; i < 5; i++) {
            int tick = 1 + i * 10;
            look.headYaw = yaw;
            look.swinging = true;
            look.swingProgressInt = 1;
            snap.update(look, tick, 1, true, false);
            yaw += 120;
            look.headYaw = yaw;
            snap.update(look, tick + 1, 1, true, false);
            look.swinging = false;
            look.swingProgressInt = 6;
            snap.update(look, tick + 2, 1, true, false);
        }
        check("snap: five 100+ deg swing snaps flag", snap.failedSnapAim());

        MoveChecks telly = new MoveChecks();
        PlayerView flick = pv();
        flick.held = Game.HELD_BLOCK;
        flick.pitch = 0;
        for (int t = 0; t < 20; t++) {
            flick.posX = t * 0.20;
            flick.pitch = t % 2 == 0 ? 0 : 70;
            telly.update(flick, t, 1, true, true);
        }
        check("telly: eight 55+ pitch flicks while moving flag", telly.failedTelly());

        MoveChecks velocity = new MoveChecks();
        PlayerView still = pv();
        still.sprinting = true;
        int tick = 0;
        for (int hit = 0; hit < 2; hit++) {
            still.hurtTime = 0;
            still.prevHurtTime = 0;
            velocity.update(still, tick++, 1, true, true);
            still.hurtTime = 10;
            still.prevHurtTime = 0;
            for (int wait = 0; wait < 10; wait++) {
                velocity.update(still, tick++, 1, true, true);
                still.prevHurtTime = 10;
            }
        }
        check("velocity: two cancelled knockbacks flag", velocity.failedVelocity());

        MoveChecks kb = new MoveChecks();
        PlayerView pushed = pv();
        pushed.sprinting = true;
        pushed.hurtTime = 10;
        pushed.prevHurtTime = 0;
        kb.update(pushed, 0, 1, true, true);
        for (int t = 1; t <= 10; t++) {
            pushed.posX = t * 0.08;
            pushed.prevHurtTime = 10;
            kb.update(pushed, t, 1, true, true);
        }
        check("velocity: real knockback does not flag on one hit", !kb.failedVelocity());

        MoveChecks keepY = new MoveChecks();
        PlayerView flat = pv();
        flat.held = Game.HELD_BLOCK;
        flat.pitch = 75;
        for (int t = 0; t < 50; t++) {
            flat.posZ = t * 0.22;
            keepY.update(flat, t, 1, true, true);
        }
        check("keep-y: unsneaking flat bridge flags", keepY.failedKeepY());

        MoveChecks air = new MoveChecks();
        PlayerView hover = pv();
        hover.held = Game.HELD_BLOCK;
        hover.pitch = 75;
        hover.onGround = false;
        for (int t = 0; t < 45; t++) {
            hover.posX = t * 0.20;
            air.update(hover, t, 1, true, true);
        }
        check("air scaffold: long air-place flags", air.failedAirScaffold());
    }

    private static void encounters(File dir) {
        final long[] now = {1000000000000L};
        Encounters.Clock clock = new Encounters.Clock() {
            @Override
            public long now() {
                return now[0];
            }
        };
        File file = new File(dir, "seen.json");
        Encounters seen = new Encounters(file, clock, 3);
        UUID alpha = UUID.randomUUID();
        seen.saw(alpha, "Alpha", 1);
        seen.saw(alpha, "Alpha", 1);
        check("encounters: once per world", seen.get(alpha, null).count == 1);
        now[0] += 60000L;
        Encounters.Entry second = seen.saw(alpha, "Alpha", 2);
        check("encounters: counts a new world", second.count == 2 && second.previous == 1000000000000L);
        check("encounters: find by name", seen.get(null, "alpha") == second);
        seen.saw(null, "Nameless", 2);
        seen.saw(null, "Other", 2);
        seen.saw(null, "Fourth", 2);
        check("encounters: capped", seen.size() == 3 && seen.get(alpha, null) == null);
        seen.flush(true);
        Files2.drain(3000L);
        now[0] += Encounters.PRUNE_MS + 1;
        check("encounters: old entries pruned on load", new Encounters(file, clock, 3).size() == 0);
    }

    private static void export() {
        check("csv: plain", "abc".equals(Export.escape("abc")));
        check("csv: comma quoted", "\"a,b\"".equals(Export.escape("a,b")));
        check("csv: quotes doubled", "\"say \"\"hi\"\"\"".equals(Export.escape("say \"hi\"")));
        check("csv: newline quoted", "\"a\nb\"".equals(Export.escape("a\nb")));
        check("csv: formula defused", "'=1+1".equals(Export.escape("=1+1")));
        check("csv: line", "a,,\"b,c\"\r\n".equals(Export.line("a", null, "b,c")));
    }

    private static void themesAndPrefs(File dir) {
        check("theme: unknown name falls back to Dark", Theme.DARK.equals(Theme.of("Neon", "", 100).name));
        check("theme: light theme loads", "Light".equals(Theme.of("Light", "", 100).name));
        check("theme: font clamped", Theme.clampFont(500) == 150 && Theme.clampFont(10) == 80);

        File legacyDir = new File(dir, "legacy");
        new File(legacyDir, "config").mkdirs();
        Files2.writeAtomic(new File(legacyDir, "config/safedetect-settings.json"),
                "{\"overlayX\": 12, \"overlayY\": 34, \"overlayW\": 800, \"overlayH\": 400}");
        OverlayPrefs migrated = OverlayPrefs.load(legacyDir);
        check("prefs: migrates old bounds", migrated.x == 12 && migrated.y == 34 && migrated.w == 800 && migrated.h == 400);

        OverlayPrefs prefs = OverlayPrefs.load(dir);
        prefs.theme = "Light";
        prefs.opacity = 70;
        prefs.columns.clear();
        prefs.columns.add("name");
        prefs.columns.add("seen");
        prefs.widths.put("name", 222);
        prefs.groupByTeam = true;
        prefs.save();
        OverlayPrefs back = OverlayPrefs.load(dir);
        check("prefs: round trip", "Light".equals(back.theme) && back.opacity == 70 && back.groupByTeam
                && back.columns.equals(prefs.columns) && Integer.valueOf(222).equals(back.widths.get("name")));
        Map<String, Object> old = new java.util.HashMap<String, Object>();
        old.put("x", 5L);
        old.put("y", 6L);
        old.put("w", 700L);
        old.put("h", 300L);
        OverlayPrefs bounds = new OverlayPrefs();
        bounds.apply(old);
        check("prefs: accepts bounds-only file", bounds.x == 5 && bounds.w == 700 && Theme.DARK.equals(bounds.theme));
    }

    private static void updater() throws Exception {
        check("update: newer patch", Updater.newer("v1.0.1", "1.0.0"));
        check("update: same version", !Updater.newer("v1.0.0", "1.0.0"));
        check("update: older", !Updater.newer("0.9", "1.0.0"));
        check("update: 1.10 beats 1.9", Updater.newer("1.10", "1.9"));
        check("update: junk tag", !Updater.newer("nightly", "1.0.0"));
        check("update: daily", !Updater.due(1000L, 1000L + 60000L) && Updater.due(0L, Updater.INTERVAL_MS));

        Updater.Release rel = Updater.parseRelease(
                "{\"tag_name\":\"v1.0.1\",\"assets\":["
                        + "{\"name\":\"SafeDetect-v1.0.1.zip\",\"browser_download_url\":\"https://x/z\"},"
                        + "{\"name\":\"safedetect-agent.jar\",\"browser_download_url\":\"https://x/safedetect-agent.jar\"}]}");
        check("update: parses jar asset", rel != null && "v1.0.1".equals(rel.tag)
                && "https://x/safedetect-agent.jar".equals(rel.jarUrl));
        Updater.Release fallback = Updater.parseRelease("{\"tag_name\":\"v2.0.0\",\"assets\":[]}");
        check("update: fallback download url",
                fallback != null && fallback.jarUrl != null && fallback.jarUrl.contains("/v2.0.0/safedetect-agent.jar"));
        check("update: junk json", Updater.parseRelease("{") == null);

        File dir = Files.createTempDirectory("sdupd").toFile();
        File jar = new File(dir, "ok.jar");
        writeManifestJar(jar, "SafeDetect Agent", "1.0.1");
        check("update: safedetect jar", Updater.isSafeDetectJar(jar));
        File other = new File(dir, "other.jar");
        writeManifestJar(other, "Something Else", "1.0.0");
        check("update: other jar rejected", !Updater.isSafeDetectJar(other));

        File dest = new File(dir, "safedetect-agent.jar");
        File pending = new File(dir, "safedetect-agent.jar.new");
        Files.copy(jar.toPath(), pending.toPath());
        check("update: apply pending", Updater.applyPending(pending, dest) && dest.isFile() && !pending.exists());

        File dest2 = new File(dir, "live.jar");
        File pending2 = new File(dir, "next.jar");
        Files.copy(other.toPath(), dest2.toPath());
        Files.copy(jar.toPath(), pending2.toPath());
        check("update: replace installed",
                Updater.replaceInstalled(pending2, dest2) && dest2.isFile() && Updater.isSafeDetectJar(dest2));
        check("update: apply bat names the pending jar", Updater.applyBatText().contains("safedetect-agent.jar.new"));
    }

    private static void writeManifestJar(File file, String title, String version) throws Exception {
        java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(file));
        zip.putNextEntry(new java.util.zip.ZipEntry("META-INF/MANIFEST.MF"));
        String mf = "Manifest-Version: 1.0\nImplementation-Title: " + title + "\nImplementation-Version: " + version
                + "\n";
        zip.write(mf.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        zip.closeEntry();
        zip.close();
    }

    private static void chatHover() {
        java.util.Map<String, String> names = new java.util.HashMap<String, String>();
        names.put("sopira", "Sopira");
        check("hover: name after rank", "Sopira".equals(ChatHover.matchName("[MVP+] Sopira: hi", names)));
        check("hover: ignores other words", ChatHover.matchName("hello there", names) == null);

        FlagStore.Record record = new FlagStore.Record();
        record.flags.add("AB");
        record.evidence.put("AB", "swung while blocking 11 ticks");
        record.counts.put("AB", Integer.valueOf(20));
        record.lastCheckAt = System.currentTimeMillis() - 4L * 30L * 86400L * 1000L;
        ChatHover.Info info = new ChatHover.Info();
        info.record = record;
        info.extra = "[U:Sniper]";
        info.blacklisted = true;
        String tip = ChatHover.build("Sopira", info);
        check("hover: titled", tip != null && ChatHover.ours(tip));
        check("hover: safedetect", tip.contains("SafeDetect") && tip.contains("AutoBlock") && tip.contains("[AB]"));
        check("hover: urchin", tip.contains("Urchin") && tip.contains("Sniper"));
        check("hover: blacklist", tip.contains("Blacklist") && tip.contains("[BL]"));
        check("hover: empty", ChatHover.build("x", new ChatHover.Info()) == null);
        long now = 1_000_000_000_000L;
        check("hover: months ago", ChatHover.ago(now - 40L * 86400L * 1000L, now).contains("month"));

        String branded = Tags.brand("U", "[Closet Cheater]");
        check("tags: brand urchin closet", "[U:Closet Cheater]".equals(branded));
        String colored = Tags.colored(branded, '5');
        check("tags: colour for tab/chat", colored.contains("\u00a75[U:Closet Cheater]\u00a7r"));
        check("tags: merge does not duplicate",
                Tags.merge("[U:Cheater]", "[U:Cheater]").equals("[U:Cheater]"));
    }

    private static void plugins(File dir) {
        check("nickfind: version-1 uuid is nicked",
                NickFindPlugin.nicked(java.util.UUID.fromString("11111111-1111-1111-1111-111111111111")));
        check("nickfind: version-4 uuid is real", !NickFindPlugin.nicked(java.util.UUID.randomUUID()));
        check("height: lighthouse", "86".equals(PluginPack.HeightCallPlugin.lookup("Lighthouse")));
        check("height: unknown", PluginPack.HeightCallPlugin.lookup("notamap") == null);

        PluginHost host = new PluginHost();
        File root = new File(dir, "plug");
        new File(root, "config").mkdirs();
        host.open(root, null, null, null);
        String catalog = host.catalogJson();
        check("plugins: catalog has nickfind", catalog.contains("\"id\":\"nickfind\""));
        check("plugins: catalog has play", catalog.contains("\"id\":\"play\""));
        check("plugins: catalog has talk", catalog.contains("\"id\":\"talk\""));
        check("plugins: catalog has startab", catalog.contains("\"id\":\"startab\""));
        check("plugins: catalog has heat", catalog.contains("\"id\":\"heat\""));
        check("plugins: catalog has hunt", catalog.contains("\"id\":\"hunt\""));
        check("plugins: catalog has joins", catalog.contains("\"id\":\"joins\""));
        check("plugins: catalog has beep", catalog.contains("\"id\":\"beep\""));
        check("plugins: catalog has names", catalog.contains("\"id\":\"names\""));
        check("plugins: catalog has staff", catalog.contains("\"id\":\"staff\""));
        check("plugins: catalog has freq", catalog.contains("\"id\":\"freq\""));
        check("plugins: catalog has pchat", catalog.contains("\"id\":\"pchat\""));
        check("plugins: catalog has gens", catalog.contains("\"id\":\"gens\""));
        check("plugins: catalog has calc", catalog.contains("\"id\":\"calc\""));
        check("plugins: catalog has afk", catalog.contains("\"id\":\"afk\""));
        check("plugins: catalog has nickping", catalog.contains("\"id\":\"nickping\""));
        check("plugins: catalog has rq", catalog.contains("\"id\":\"rq\""));
        check("plugins: catalog has remind", catalog.contains("\"id\":\"remind\""));
        check("plugins: catalog has maps", catalog.contains("\"id\":\"maps\""));
        check("calc: 4+4*2", Math.abs(CalcPlugin.eval("4+4*2") - 12.0) < 1e-9);
        check("calc: (4+4)*2", Math.abs(CalcPlugin.eval("(4+4)*2") - 16.0) < 1e-9);
        check("calc: 4x4", Math.abs(CalcPlugin.eval("4x4") - 16.0) < 1e-9);
        check("calc: 4/6", Math.abs(CalcPlugin.eval("4/6") - (4.0 / 6.0)) < 1e-9);
        boolean calcBad = false;
        try {
            CalcPlugin.eval("not-math");
        } catch (IllegalArgumentException expected) {
            calcBad = true;
        }
        check("calc: invalid", calcBad);
        java.util.List<String> names = NameLogPlugin.parse(
                "{\"username_history\":[{\"username\":\"Old\"},{\"username\":\"New\"}]}");
        check("names: ashcon history", names.size() == 2 && "Old".equals(names.get(0)) && "New".equals(names.get(1)));
        check("joins: doubles party is 2", JoinPadPlugin.partySize(16) == 2);
        check("joins: 3s party is 3", JoinPadPlugin.partySize(12) == 3);
        check("gens: 90s is 3 diamonds", GenPadPlugin.spawned(90000L, 30000) == 3);
        check("gens: next diamond", GenPadPlugin.nextMs(10000L, 30000) == 20000);
        check("remind: 90s", RemindPlugin.parseDuration("90s") == 90000);
        check("remind: 2m", RemindPlugin.parseDuration("2m") == 120000);
        check("remind: 1m30s", RemindPlugin.parseDuration("1m30s") == 90000);
        check("staff: admin rank", StaffWatchPlugin.staffRank("\u00a7c[ADMIN] "));
        check("staff: team letter is not staff", !StaffWatchPlugin.staffRank("\u00a7c[R] "));
        String sample = "{\"success\":true,\"player\":{\"achievements\":{\"bedwars_level\":12},"
                + "\"stats\":{\"Bedwars\":{\"final_kills_bedwars\":24,\"final_deaths_bedwars\":8,"
                + "\"wins_bedwars\":10,\"losses_bedwars\":5,\"beds_broken_bedwars\":7,\"winstreak\":3}}}}";
        BedStats bed = BedStats.parse(sample, "Steve");
        check("bedstats: parse stars", bed != null && bed.stars == 12 && bed.beds == 7);
        check("bedstats: parse fkdr", bed != null && Math.abs(bed.fkdr - 3.0) < 0.01);
        check("bedstats: chat line", bed != null && bed.chatLine().contains("12") && bed.chatLine().contains("3.00"));
        check("bedstats: tab suffix", bed != null && bed.tabSuffix().contains("\u272b"));
        check("bedstats: higher fkdr is hotter",
                new BedStats("a", 100, 0, 0, 0, 8.0, 2.0, false).threat() > new BedStats("b", 100, 0, 0, 0, 1.0, 2.0,
                        false).threat());
        check("bedstats: team letter from bracket", "R".equals(BedStats.teamLetter("\u00a7c[R] ")));
        check("bedstats: team letter from colour", "B".equals(BedStats.teamLetter("\u00a79")));
        check("bedstats: nicked player json", BedStats.parse("{\"success\":true,\"player\":null}", "x").nicked);
        check("plugins: play on by default", host.enabled("play"));
        check("plugins: rq on by default", host.enabled("rq"));
        check("plugins: nickfind off by default", !host.enabled("nickfind"));
        host.setEnabled("nickfind", true);
        check("plugins: can enable nickfind", host.enabled("nickfind"));
        Object parsed = Json.parse(catalog);
        check("plugins: catalog is JSON array", parsed instanceof java.util.List);
        @SuppressWarnings("unchecked")
        java.util.List<Object> list = (java.util.List<Object>) parsed;
        check("plugins: at least 30 bundled", list.size() >= 30);
        java.util.List<Hud.PluginCard> cards = Hud.readPlugins(parsed);
        check("plugins: card has id", !cards.isEmpty() && cards.get(0).id != null && !cards.get(0).id.isEmpty());
    }

    private static void check(String what, boolean pass) {
        ok &= pass;
        System.out.println((pass ? "PASS  " : "FAIL  ") + what);
    }
}
