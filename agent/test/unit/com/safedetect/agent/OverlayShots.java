package com.safedetect.agent;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Renders the overlay in several states to PNGs without leaving a window on screen.
 * Usage: java -cp classes;unit com.safedetect.agent.OverlayShots <data dir> <out dir>
 */
public final class OverlayShots {
    private static Object overlay;
    private static JFrame frame;
    private static File out;

    private OverlayShots() {
    }

    public static void main(String[] args) throws Exception {
        final File data = new File(args[0]);
        out = new File(args[1]);
        out.mkdirs();
        System.setProperty("safedetect.overlay", "true");
        new File(data, "config/safedetect-overlay.json").delete();
        Field dir = Overlay.class.getDeclaredField("dataDir");
        dir.setAccessible(true);
        dir.set(null, data);
        Hud.open(data);
        final Hud.Snapshot snap = Hud.readState();
        if (snap == null) {
            System.out.println("No HUD file in " + data);
            System.exit(1);
        }
        Field options = Overlay.class.getDeclaredField("GAME_OPTIONS");
        options.setAccessible(true);
        ((java.util.Map<String, Object>) options.get(null)).putAll(snap.options);
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                try {
                    Method instance = Overlay.class.getDeclaredMethod("instance");
                    instance.setAccessible(true);
                    overlay = instance.invoke(null);
                    frame = (JFrame) field("frame");
                    frame.setVisible(false);
                    Field hidden = Overlay.class.getDeclaredField("userHidden");
                    hidden.setAccessible(true);
                    hidden.set(overlay, Boolean.TRUE);
                    call("apply", new Class<?>[] { int.class, java.util.List.class, java.util.List.class, boolean.class },
                            snap.players, snap.lobby, snap.saved, Boolean.FALSE);
                } catch (Exception failed) {
                    throw new RuntimeException(failed);
                }
            }
        });
        shot("1-lobby-dark");
        onEdt("clickTabSaved");
        shot("2-saved");
        onEdt("clickTabLobby");
        for (String theme : Theme.NAMES) {
            prefs().theme = theme;
            onEdt("appearance");
            Thread.sleep(300);
            shot("3-theme-" + theme.replace(' ', '-').toLowerCase());
        }
        Thread.sleep(800);
        String saved = new String(Files.readAllBytes(new File(data, "config/safedetect-overlay.json").toPath()),
                StandardCharsets.UTF_8);
        System.out.println(saved.contains("\"High contrast\"") ? "PASS  prefs saved theme" : "FAIL  prefs not saved: " + saved);
        prefs().theme = Theme.DARK;
        prefs().groupByTeam = true;
        prefs().columns.add(2, "team");
        prefs().columns.add("seen");
        onEdt("appearance");
        Thread.sleep(300);
        shot("4-grouped-team-seen");
        prefs().mini = true;
        onEdt("modes");
        shot("5-mini");
        prefs().mini = false;
        onEdt("modes");
        for (String card : SettingsPanel.CARDS) {
            onEdt("settings:" + card);
            shot("6-settings-" + card.toLowerCase());
        }
        System.out.println("Shots in " + out.getAbsolutePath());
        System.exit(0);
    }

    private static OverlayPrefs prefs() throws Exception {
        return (OverlayPrefs) field("prefs");
    }

    private static Object field(String name) throws Exception {
        Field field = Overlay.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(overlay);
    }

    private static Object call(String name, Class<?>[] types, Object... args) throws Exception {
        Method method = Overlay.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(overlay, args);
    }

    private static void onEdt(final String action) throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                try {
                    if ("appearance".equals(action)) {
                        ((SettingsPanel.Host) overlay).appearanceChanged();
                    } else if ("modes".equals(action)) {
                        call("applyModes", new Class<?>[0]);
                    } else if (action.startsWith("clickTab")) {
                        Field lobby = Overlay.class.getDeclaredField("showingLobby");
                        lobby.setAccessible(true);
                        lobby.set(overlay, Boolean.valueOf(action.endsWith("Lobby")));
                        call("paintTabs", new Class<?>[0]);
                        call("restructure", new Class<?>[0]);
                        call("refill", new Class<?>[0]);
                        call("applyModes", new Class<?>[0]);
                    } else if (action.startsWith("settings:")) {
                        call("openSettings", new Class<?>[] { String.class }, action.substring(9));
                    }
                } catch (Exception failed) {
                    throw new RuntimeException(failed);
                }
            }
        });
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
            }
        });
    }

    private static void shot(final String name) throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                try {
                    frame.validate();
                    int w = Math.max(1, frame.getWidth());
                    int h = Math.max(1, frame.getHeight());
                    BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = image.createGraphics();
                    g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setClip(new java.awt.geom.RoundRectangle2D.Float(0, 0, w, h, 20, 20));
                    frame.getRootPane().printAll(g);
                    g.dispose();
                    ImageIO.write(image, "png", new File(out, name + ".png"));
                } catch (Exception failed) {
                    throw new RuntimeException(failed);
                }
            }
        });
    }
}
