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
        ok &= expect(all, "/sd friend", true);
        ok &= expect(all, "trnsmt", true);
        ok &= expect(all, "UnitTest", true);
        ok &= expect(all, "Saved players", true);
        ok &= expect(all, "Wemzy_on_top", true);
        ok &= expect(all, "Type /sd clear confirm", true);
        ok &= expect(all, "Deleted ", false);
        ok &= expect(all, "COPY_OK", true);

        String json = new String(Files.readAllBytes(new File(dataDir, "config/safedetect-flags.json").toPath()),
                StandardCharsets.UTF_8);
        ok &= expect(json, "\"Wemzy_on_top\"", true);
        ok &= expect(json, "\"9 stars, 3.0 FKDR\"", true);
        ok &= expect(json, "\"Blocker\"", true);
        System.out.println(ok ? "ALL CHECKS PASSED" : "SOME CHECKS FAILED");
        System.exit(ok ? 0 : 1);
    }

    private static boolean expect(String haystack, String needle, boolean present) {
        boolean found = haystack.contains(needle);
        boolean pass = found == present;
        System.out.println((pass ? "PASS  " : "FAIL  ") + (present ? "has     " : "lacks   ") + needle);
        return pass;
    }
}
