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
            encounters(dir);
            export();
            themesAndPrefs(dir);
            updater();
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

    private static void updater() {
        check("update: newer patch", Updater.newer("v1.0.1", "1.0.0"));
        check("update: same version", !Updater.newer("v1.0.0", "1.0.0"));
        check("update: older", !Updater.newer("0.9", "1.0.0"));
        check("update: 1.10 beats 1.9", Updater.newer("1.10", "1.9"));
        check("update: junk tag", !Updater.newer("nightly", "1.0.0"));
        check("update: daily", !Updater.due(1000L, 1000L + 60000L) && Updater.due(0L, Updater.INTERVAL_MS));
    }

    private static void check(String what, boolean pass) {
        ok &= pass;
        System.out.println((pass ? "PASS  " : "FAIL  ") + what);
    }
}
