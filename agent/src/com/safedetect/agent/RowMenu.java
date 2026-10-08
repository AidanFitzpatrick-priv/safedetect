package com.safedetect.agent;

import javax.swing.BorderFactory;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URI;
import java.net.URLEncoder;

/** Right-click actions for one player row. Changes go to the game as commands. */
final class RowMenu {
    private RowMenu() {
    }

    static void show(Component owner, int x, int y, final Hud.Row row, Theme theme) {
        if (row == null || row.name == null || row.name.isEmpty()) {
            return;
        }
        final String name = row.name;
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(theme.card);
        menu.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(theme.line),
                BorderFactory.createEmptyBorder(4, 0, 4, 0)));
        menu.add(item(theme, "Copy name", new Runnable() {
            @Override
            public void run() {
                copy(name);
            }
        }));
        menu.add(item(theme, "Copy /wdr", new Runnable() {
            @Override
            public void run() {
                copy(Hud.wdr(name));
            }
        }));
        menu.addSeparator();
        menu.add(item(theme, row.blacklisted ? "Remove from blacklist" : "Blacklist", new Runnable() {
            @Override
            public void run() {
                if (row.blacklisted) {
                    Hud.writeAction("unblacklist", name);
                } else {
                    Hud.writeCommand(false, name);
                }
            }
        }));
        menu.add(item(theme, row.friend ? "Unskip (check again)" : "Skip (friend)", new Runnable() {
            @Override
            public void run() {
                Hud.writeAction(row.friend ? "unfriend" : "friend", name);
            }
        }));
        menu.addSeparator();
        menu.add(item(theme, "Open Plancke", new Runnable() {
            @Override
            public void run() {
                browse("https://plancke.io/hypixel/player/stats/" + encode(name));
            }
        }));
        menu.add(item(theme, "Open NameMC", new Runnable() {
            @Override
            public void run() {
                browse("https://namemc.com/profile/" + encode(name));
            }
        }));
        menu.show(owner, x, y);
    }

    private static JMenuItem item(Theme theme, String label, final Runnable run) {
        JMenuItem item = new JMenuItem(label);
        item.setFont(theme.small);
        item.setBackground(theme.card);
        item.setForeground(theme.fg);
        item.setBorder(BorderFactory.createEmptyBorder(6, 4, 6, 20));
        item.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                run.run();
            }
        });
        return item;
    }

    static void copy(String text) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        } catch (Throwable thrown) {
            Log.once("overlay clipboard", thrown);
        }
    }

    /** Opens the browser when the desktop supports it; otherwise the URL is copied. */
    static void browse(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                return;
            }
        } catch (Throwable thrown) {
            Log.once("overlay browse", thrown);
        }
        copy(url);
    }

    private static String encode(String name) {
        try {
            return URLEncoder.encode(name, "UTF-8");
        } catch (Exception impossible) {
            return name;
        }
    }
}
