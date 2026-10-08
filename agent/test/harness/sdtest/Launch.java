package sdtest;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * Loads the fake game in its own class loader (as Lunar does) so the agent can only reach it by reflection.
 */
public final class Launch {
    private Launch() {
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("safedetect.nogui", "true");
        File mockClasses = new File(args[0]);
        File dataDir = new File(args[1]);
        URLClassLoader game = new URLClassLoader(new URL[]{mockClasses.toURI().toURL()}, null);
        Class<?> driver = Class.forName("sdtest.MockGame", true, game);
        @SuppressWarnings("unchecked")
        List<String> chat = (List<String>) driver.getMethod("run", File.class).invoke(null, dataDir);

        String all = String.join("\n", chat).replaceAll("\u00a7.", "");
        boolean ok = true;
        ok &= expect(all, "SafeDetect is running. Saved players: ", true);
        ok &= expect(all, "noahhh727_alt marked [SN]", true);
        ok &= expect(all, "Blocker failed AutoBlock", true);
        ok &= expect(all, "Slowpoke failed NoSlow", true);
        ok &= expect(all, "Aura failed Silent Aura", true);
        ok &= expect(all, "Aura failed Killaura", false);
        ok &= expect(all, "Clicker failed Autoclicker", true);
        ok &= expect(all, "JitterClick failed", false);
        ok &= expect(all, "Reacher failed Reach", true);
        ok &= expect(all, "Nuker failed BedBreaker", false);
        ok &= expect(all, "Stiff failed Velocity", true);
        ok &= expect(all, "Godder failed God Bridge", true);
        ok &= expect(all, "DiagBridger failed Diagonal Scaffold", true);
        ok &= expect(all, "possible alt of Blocker", true);
        ok &= expect(all, "Telly failed Telly", true);
        ok &= expect(all, "Eater failed", false);
        ok &= expect(all, "Miner failed", false);
        ok &= expect(all, "Stretched failed", false);
        ok &= expect(all, "Boxer failed", false);
        ok &= expect(all, "Bag failed", false);
        ok &= expect(all, "Dummy failed", false);
        ok &= expect(all, "Bridger failed Legit Scaffold", true);
        ok &= expect(all, "Mate failed AutoBlock", true);
        ok &= expect(all, "FarAway failed", false);
        ok &= expect(all, "Creative failed", false);
        ok &= expect(all, "Walker failed", false);
        ok &= expect(all, "Speeder failed Speed", true);
        ok &= expect(all, "Flyer failed Fly", false);
        ok &= expect(all, "7w0392l04b failed", false);
        ok &= expect(all, "11i4xodxih failed", false);
        ok &= expect(all, "trnsmt failed", false);
        ok &= expect(all, "zoxide failed", false);
        ok &= expect(all, "Towerer failed Tower", true);
        ok &= expect(all, "SprintBridger failed Sprint Scaffold", true);
        ok &= expect(all, "Snapper failed Snap Aim", true);
        ok &= expect(all, "Hopper failed", false);
        ok &= expect(all, "Knocked failed", false);
        ok &= expect(all, "PotionRunner failed", false);
        ok &= expect(all, "Swimmer failed", false);
        ok &= expect(all, "SlowTower failed", false);
        ok &= expect(all, "BackBridger failed", false);
        ok &= expect(all, "Spinner failed", false);
        ok &= expect(all, "LobbyFlyer failed", false);
        ok &= expect(all, "LobbySpeeder failed", false);
        ok &= expect(all, "[SD] Bridger failed Sprint Scaffold", false);
        ok &= expect(all, "CrouchSpammer failed", false);
        ok &= expect(all, "ShiftBridger failed", false);
        ok &= expect(all, "Fighter failed", false);
        ok &= expect(all, "Sloppy failed", false);
        ok &= expect(all, "/sd list [page]", true);
        ok &= expect(all, "/sd gui", true);
        ok &= expect(all, "/sd update", true);
        ok &= expect(all, "/sd plugins", true);
        ok &= expect(all, "/sd friend", true);
        ok &= expect(all, "trnsmt", true);
        ok &= expect(all, "UnitTest", true);
        ok &= expect(all, "Saved players", true);
        ok &= expect(all, "Wemzy_on_top", true);
        ok &= expect(all, "Type /sd clear confirm", true);
        ok &= expect(all, "Deleted ", false);
        ok &= expect(all, "COPY_OK", true);

        ok &= expect(line(chat, "HUD_GAME "), "\"name\":\"Ghosty\"", true);
        ok &= expect(line(chat, "HUD_LOBBY "), "Ghosty", false);
        ok &= expect(line(chat, "HUD_LOBBY "), "\"players\": ", true);
        ok &= count(all, "(page 1/", 2);
        ok &= expect(all, "Keys updated: sniperUrl set", true);
        ok &= expect(all, "SECRET123", false);

        Thread.sleep(500);
        String json = new String(Files.readAllBytes(new File(dataDir, "config/safedetect-flags.json").toPath()),
                StandardCharsets.UTF_8);
        ok &= expect(json, "\"Wemzy_on_top\"", true);
        ok &= expect(json, "\"9 stars, 3.0 FKDR\"", true);
        ok &= expect(json, "\"Blocker\"", true);
        ok &= expect(json, "\"reach 3.", true);

        String arena = line(chat, "HUD_ARENA ");
        ok &= count(all, "DODGE? Baddie", 1);
        ok &= expect(line(chat, "TITLES "), "Dodge?", true);
        ok &= expect(arena, "\"name\":\"Baddie\"", true);
        ok &= expect(arena, "\"team\":\"c\"", true);
        ok &= expect(arena, "\"seen\":1", true);
        ok &= expect(arena, "\"threat\":\"blacklisted\"", true);
        ok &= expect(all, "Speeder2 failed", false);
        ok &= expect(all, "Exported ", true);
        ok &= expect(all, "Reloaded: ", true);
        ok &= expect(all, "Reloaded: 0 saved", false);
        ok &= expect(all, "Checks:", true);
        String[] files = new File(dataDir, "config").list();
        boolean flagsCsv = false;
        boolean seenCsv = false;
        for (String name : files == null ? new String[0] : files) {
            flagsCsv |= name.startsWith("safedetect-export-") && name.endsWith(".csv");
            seenCsv |= name.startsWith("safedetect-encounters-") && name.endsWith(".csv");
        }
        ok &= expect(flagsCsv && seenCsv ? "csv" : "", "csv", true);
        System.out.println(ok ? "ALL CHECKS PASSED" : "SOME CHECKS FAILED");
        System.exit(ok ? 0 : 1);
    }

    private static String line(List<String> chat, String prefix) {
        for (String line : chat) {
            if (line.startsWith(prefix)) {
                return line;
            }
        }
        System.out.println("FAIL  missing line " + prefix.trim());
        return "";
    }

    private static boolean count(String haystack, String needle, int wanted) {
        int found = 0;
        for (int at = haystack.indexOf(needle); at >= 0; at = haystack.indexOf(needle, at + 1)) {
            found++;
        }
        boolean pass = found == wanted;
        System.out.println((pass ? "PASS  " : "FAIL  ") + "has " + wanted + "x   " + needle + " (found " + found + ")");
        return pass;
    }

    private static boolean expect(String haystack, String needle, boolean present) {
        boolean found = haystack.contains(needle);
        boolean pass = found == present;
        System.out.println((pass ? "PASS  " : "FAIL  ") + (present ? "has     " : "lacks   ") + needle);
        return pass;
    }
}
