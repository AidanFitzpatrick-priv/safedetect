package com.safedetect.agent;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Settings shown in place of the table. Appearance and Columns change the overlay's own prefs; Alerts and
 * Checks send {@code set} commands to the game; Keys sends keys without ever reading them back.
 */
final class SettingsPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    static final String[] CARDS = { "Appearance", "Columns", "Alerts", "Checks", "Keys", "Plugins" };
    private static final String[] SUBTITLES = {
            "Theme, colours and how the list is laid out.",
            "Pick which stats show in the lobby list and in what order.",
            "What happens in game when someone is flagged or worth dodging. Update installs a new jar.",
            "What each flag means, plus Urchin tags. Turn checks on or off here.",
            "Keys go straight to the game and are never shown again.",
            "Enable bundled add-ons or drop a jar into config/safedetect-plugins.",
    };
    static final String[][] KEY_FIELDS = {
            { "hypixel", "Hypixel API key" },
            { "sniperKey", "Anti-sniper key" },
            { "sniperUrl", "Anti-sniper URL" },
            { "urchin", "Urchin key" },
            { "aurora", "Aurora key" },
            { "discord", "Discord app id" },
    };

    interface Host {
        OverlayPrefs prefs();

        Theme theme();

        /** Theme, accent, font or density changed; the overlay rebuilds its widgets. */
        void appearanceChanged();

        void layoutChanged();

        void opacityChanged();

        boolean translucencySupported();

        void closeSettings();
    }

    /** Loads one game-owned control from the latest options. */
    private interface Binding {
        String name();

        void load();
    }

    private final Host host;
    private final Theme theme;
    private final CardLayout cards = new CardLayout();
    private final JPanel deck = new JPanel(cards);
    private final Map<String, Ui.Pill> nav = new LinkedHashMap<String, Ui.Pill>();
    private final List<Binding> bindings = new ArrayList<Binding>();
    private final List<Ui.Pill> keyStates = new ArrayList<Ui.Pill>();
    private JPanel columnList;
    private JPanel pluginList;
    private String pluginKey = "";
    private String card;
    private boolean loading;

    SettingsPanel(Host host, String startCard) {
        super(new BorderLayout());
        this.host = host;
        this.theme = host.theme();
        setBackground(theme.bg);
        deck.setBackground(theme.bg);
        add(navigation(), BorderLayout.WEST);
        deck.add(scrolled(appearance()), CARDS[0]);
        deck.add(scrolled(columns()), CARDS[1]);
        deck.add(scrolled(alerts()), CARDS[2]);
        deck.add(scrolled(checks()), CARDS[3]);
        deck.add(scrolled(keys()), CARDS[4]);
        deck.add(scrolled(plugins()), CARDS[5]);
        add(deck, BorderLayout.CENTER);
        show(startCard);
        refresh();
    }

    String card() {
        return card;
    }

    void show(String name) {
        card = name;
        boolean known = false;
        for (String each : CARDS) {
            known |= each.equals(name);
        }
        if (!known) {
            card = CARDS[0];
        }
        cards.show(deck, card);
        for (Map.Entry<String, Ui.Pill> entry : nav.entrySet()) {
            paintNav(entry.getValue(), entry.getKey().equals(card), false);
        }
    }

    private void paintNav(Ui.Pill item, boolean on, boolean hover) {
        item.fill = on ? theme.accentSoft : hover ? theme.hover : null;
        item.setForeground(on ? theme.light ? theme.accent : theme.fg : hover ? theme.fg : theme.muted);
        item.setFont(on ? theme.bold.deriveFont(theme.small.getSize2D() + 0.5f) : theme.small.deriveFont(theme.small.getSize2D() + 0.5f));
        item.repaint();
    }

    /** Pulls the latest game options and key states into the controls, skipping ones still in grace. */
    void refresh() {
        loading = true;
        try {
            for (Binding binding : bindings) {
                if (!Overlay.optionPending(binding.name())) {
                    binding.load();
                }
            }
            for (int i = 0; i < keyStates.size() && i < KEY_FIELDS.length; i++) {
                paintKeyState(keyStates.get(i), Overlay.keySet(KEY_FIELDS[i][0]));
            }
            fillPlugins();
        } finally {
            loading = false;
        }
    }

    private void paintKeyState(Ui.Pill state, boolean set) {
        state.setText(set ? "Set" : "Not set");
        state.setForeground(set ? theme.success : theme.muted);
        state.fill = set ? Theme.mix(theme.card, theme.success, 0.16) : null;
        state.outline = set ? null : theme.line;
        state.repaint();
    }

    // ---- frame ----

    private JComponent navigation() {
        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBackground(theme.header);
        side.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, theme.line),
                BorderFactory.createEmptyBorder(12, 10, 12, 10)));
        final Ui.Pill back = new Ui.Pill("\u2190  Back to list", 8);
        back.setFont(theme.small);
        back.setForeground(theme.muted);
        back.setHorizontalAlignment(JLabel.LEFT);
        back.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        back.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        back.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                host.closeSettings();
            }

            @Override
            public void mouseEntered(MouseEvent event) {
                back.setForeground(theme.fg);
            }

            @Override
            public void mouseExited(MouseEvent event) {
                back.setForeground(theme.muted);
            }
        });
        side.add(stretch(back));
        side.add(Box.createVerticalStrut(10));
        JLabel caption = Ui.label(theme, "SETTINGS", Ui.tracked(theme.caption, 0.08f), theme.muted);
        caption.setBorder(BorderFactory.createEmptyBorder(0, 10, 6, 10));
        side.add(stretch(caption));
        for (final String name : CARDS) {
            final Ui.Pill item = new Ui.Pill(name, 8);
            item.setHorizontalAlignment(JLabel.LEFT);
            item.setBorder(BorderFactory.createEmptyBorder(7, 10, 7, 26));
            item.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            item.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    show(name);
                }

                @Override
                public void mouseEntered(MouseEvent event) {
                    paintNav(item, name.equals(card), true);
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    paintNav(item, name.equals(card), false);
                }
            });
            nav.put(name, item);
            side.add(stretch(item));
            side.add(Box.createVerticalStrut(2));
        }
        side.add(Box.createVerticalGlue());
        return side;
    }

    private static JComponent stretch(JComponent component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
        return component;
    }

    /** Page that always matches the viewport width, so cards shrink with the window instead of being cut off. */
    private static final class WidthTracking extends JPanel implements javax.swing.Scrollable {
        private static final long serialVersionUID = 1L;

        WidthTracking() {
            super(new BorderLayout());
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(java.awt.Rectangle visible, int orientation, int direction) {
            return 20;
        }

        @Override
        public int getScrollableBlockIncrement(java.awt.Rectangle visible, int orientation, int direction) {
            return Math.max(20, visible.height - 40);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    private JScrollPane scrolled(JComponent body) {
        JPanel holder = new WidthTracking();
        holder.setBackground(theme.bg);
        holder.add(body, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(holder);
        scroll.setBackground(theme.bg);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(theme.bg);
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        scroll.getVerticalScrollBar().setUI(new Overlay.ThinBar(theme));
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(10, 0));
        scroll.getVerticalScrollBar().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        return scroll;
    }

    // ---- pages ----

    private JPanel appearance() {
        final OverlayPrefs prefs = host.prefs();
        Form form = new Form(0);
        form.section("Look");
        final JComboBox<String> themes = Ui.combo(theme, Theme.NAMES);
        themes.setSelectedItem(prefs.theme);
        themes.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                String next = String.valueOf(themes.getSelectedItem());
                if (!next.equals(prefs.theme)) {
                    prefs.theme = next;
                    host.appearanceChanged();
                }
            }
        });
        form.row("Theme", null, sized(Ui.boxed(theme, themes), 160));

        form.row("Accent", "Tabs, toggles and links", accentPicker(prefs));
        form.row("Custom accent", "Any #RRGGBB colour, then press Enter", sized(Ui.boxed(theme, hexField(prefs)), 110));

        final JSlider font = Ui.slider(theme, 80, 150, prefs.fontScale);
        font.setMajorTickSpacing(10);
        font.setSnapToTicks(true);
        final JLabel fontValue = value(prefs.fontScale + "%");
        font.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent event) {
                fontValue.setText(font.getValue() + "%");
                if (!font.getValueIsAdjusting() && font.getValue() != prefs.fontScale) {
                    prefs.fontScale = font.getValue();
                    host.appearanceChanged();
                }
            }
        });
        form.row("Text size", null, withValue(font, fontValue));

        final JComboBox<String> density = Ui.combo(theme, new String[] { "Compact", "Normal", "Comfortable" });
        density.setSelectedIndex(indexOf(Theme.DENSITIES, prefs.density, 1));
        density.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                String next = Theme.DENSITIES[Math.max(0, density.getSelectedIndex())];
                if (!next.equals(prefs.density)) {
                    prefs.density = next;
                    host.appearanceChanged();
                }
            }
        });
        form.row("Row density", null, sized(Ui.boxed(theme, density), 160));

        final JSlider opacity = Ui.slider(theme, 40, 100, prefs.opacity);
        final JLabel opacityValue = value(prefs.opacity + "%");
        boolean translucent = host.translucencySupported();
        opacity.setEnabled(translucent);
        opacity.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent event) {
                opacityValue.setText(opacity.getValue() + "%");
                prefs.opacity = opacity.getValue();
                host.opacityChanged();
            }
        });
        form.row("Window opacity", translucent ? null : "Not supported on this display", withValue(opacity, opacityValue));

        form.section("Hotkey");
        final JComboBox<String> hideKey = Ui.combo(theme, OverlayKeys.NAMES);
        hideKey.setSelectedIndex(OverlayKeys.indexOf((int) Overlay.optionNumber("overlayKey", OverlayKeys.NONE)));
        hideKey.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                if (!loading) {
                    Overlay.setOption("overlayKey", Integer.valueOf(OverlayKeys.codeAt(hideKey.getSelectedIndex())));
                }
            }
        });
        bindings.add(new Binding() {
            @Override
            public String name() {
                return "overlayKey";
            }

            @Override
            public void load() {
                hideKey.setSelectedIndex(OverlayKeys.indexOf((int) Overlay.optionNumber("overlayKey", OverlayKeys.NONE)));
            }
        });
        form.row("Hide overlay", "Off unless you pick a key. Ignored while chat is open. /sd gui reopens it",
                sized(Ui.boxed(theme, hideKey), 160));

        form.section("Layout");
        final JComboBox<String> miniFilter = Ui.combo(theme, new String[] { "Threats only", "Everyone" });
        miniFilter.setSelectedIndex(OverlayPrefs.MINI_ALL.equals(prefs.miniFilter) ? 1 : 0);
        miniFilter.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                prefs.miniFilter = miniFilter.getSelectedIndex() == 1 ? OverlayPrefs.MINI_ALL : OverlayPrefs.MINI_THREATS;
                host.layoutChanged();
            }
        });
        form.row("Mini mode shows", "The compact list from the title bar button", sized(Ui.boxed(theme, miniFilter), 160));
        final JCheckBox group = Ui.toggle(theme, prefs.groupByTeam);
        group.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                prefs.groupByTeam = group.isSelected();
                host.layoutChanged();
            }
        });
        form.row("Group by team", "Uses tab list team colours", group);
        final JCheckBox pin = Ui.toggle(theme, prefs.pinFlagged);
        pin.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                prefs.pinFlagged = pin.isSelected();
                host.layoutChanged();
            }
        });
        form.row("Pin flagged players", "Flagged and dodge rows stay on top whatever the sort", pin);
        return form.page;
    }

    /** Colour swatches; the first is the theme's own accent. */
    private JComponent accentPicker(final OverlayPrefs prefs) {
        JPanel row = Ui.row(2);
        final List<Swatch> swatches = new ArrayList<Swatch>();
        Swatch auto = new Swatch(Theme.of(prefs.theme, "", prefs.fontScale).accent, "", "Theme default");
        swatches.add(auto);
        for (String[] preset : Theme.ACCENTS) {
            swatches.add(new Swatch(Theme.parseHex(preset[1]), preset[1], preset[0]));
        }
        for (final Swatch swatch : swatches) {
            swatch.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    if (!swatch.hex.equalsIgnoreCase(prefs.accent)) {
                        prefs.accent = swatch.hex;
                        host.appearanceChanged();
                    }
                }
            });
            row.add(swatch);
        }
        row.setMinimumSize(row.getPreferredSize());
        return row;
    }

    private JTextField hexField(final OverlayPrefs prefs) {
        final Ui.Field hex = new Ui.Field(theme, "#RRGGBB");
        hex.setText(prefs.accent == null ? "" : prefs.accent);
        ActionListener applyHex = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                String text = hex.getText().trim();
                if (text.isEmpty() || Theme.parseHex(text) != null) {
                    String next = text.isEmpty() ? "" : Theme.hex(Theme.parseHex(text));
                    if (!next.equalsIgnoreCase(prefs.accent)) {
                        prefs.accent = next;
                        host.appearanceChanged();
                    }
                }
            }
        };
        hex.addActionListener(applyHex);
        return hex;
    }

    private final class Swatch extends JComponent {
        private static final long serialVersionUID = 1L;
        final Color color;
        final String hex;
        private boolean hover;

        Swatch(Color color, String hex, String tip) {
            this.color = color;
            this.hex = hex;
            setToolTipText(tip);
            setPreferredSize(new Dimension(22, 22));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent event) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    hover = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D gfx = Ui.smooth(g);
            String current = host.prefs().accent == null ? "" : host.prefs().accent;
            boolean on = hex.equalsIgnoreCase(current);
            gfx.setColor(color);
            gfx.fillOval(3, 3, 16, 16);
            if (on || hover) {
                gfx.setColor(on ? theme.fg : theme.muted);
                gfx.setStroke(new java.awt.BasicStroke(1.5f));
                gfx.drawOval(1, 1, 19, 19);
            }
            if (hex.isEmpty()) {
                gfx.setColor(theme.onAccent);
                gfx.setFont(theme.caption);
                gfx.drawString("A", 7, 15);
            }
            gfx.dispose();
        }
    }

    private JPanel columns() {
        Form form = new Form(1);
        form.section("Lobby columns");
        columnList = new JPanel(new GridBagLayout());
        columnList.setOpaque(false);
        fillColumnList();
        form.full(columnList);
        JButton reset = Ui.button(theme, "Reset to default", Ui.GHOST);
        reset.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                host.prefs().resetColumns();
                fillColumnList();
                host.layoutChanged();
            }
        });
        form.after(reset, "You can also drag column headers to reorder and their edges to resize.");
        return form.page;
    }

    private void fillColumnList() {
        columnList.removeAll();
        final OverlayPrefs prefs = host.prefs();
        final List<String> order = new ArrayList<String>(prefs.columns);
        for (OverlayColumns.Column column : OverlayColumns.ALL) {
            if (!order.contains(column.id)) {
                order.add(column.id);
            }
        }
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        for (int i = 0; i < order.size(); i++) {
            final String id = order.get(i);
            final int at = i;
            OverlayColumns.Column column = OverlayColumns.byId(id);
            boolean shown = prefs.columns.contains(id);
            final JCheckBox box = Ui.toggle(theme, shown);
            box.setEnabled(!"name".equals(id));
            box.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent event) {
                    applyColumns(order, id, box.isSelected());
                }
            });
            JLabel up = arrow(true, at > 0, new Runnable() {
                @Override
                public void run() {
                    order.add(at - 1, order.remove(at));
                    applyColumns(order, null, false);
                }
            });
            JLabel down = arrow(false, at < order.size() - 1, new Runnable() {
                @Override
                public void run() {
                    order.add(at + 1, order.remove(at));
                    applyColumns(order, null, false);
                }
            });
            JPanel line = new JPanel(new BorderLayout(10, 0));
            line.setOpaque(false);
            line.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(i == 0 ? 0 : 1, 0, 0, 0, theme.rowLine),
                    BorderFactory.createEmptyBorder(7, 0, 7, 0)));
            JLabel name = Ui.label(theme, column.label, theme.small, shown ? theme.fg : theme.muted);
            JPanel left = Ui.row(10);
            left.add(box);
            left.add(name);
            line.add(left, BorderLayout.WEST);
            JPanel arrows = new JPanel(new GridLayout(1, 2, 2, 0));
            arrows.setOpaque(false);
            arrows.add(up);
            arrows.add(down);
            line.add(arrows, BorderLayout.EAST);
            c.gridy = i;
            columnList.add(line, c);
        }
        columnList.revalidate();
        columnList.repaint();
    }

    private JLabel arrow(final boolean up, final boolean enabled, final Runnable run) {
        final JLabel label = new JLabel();
        label.setPreferredSize(new Dimension(26, 24));
        final boolean[] hover = { false };
        label.setIcon(new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D gfx = Ui.smooth(g);
                if (hover[0] && enabled) {
                    gfx.setColor(theme.hover);
                    gfx.fill(new java.awt.geom.RoundRectangle2D.Float(x, y, 26, 24, 10, 10));
                }
                gfx.setColor(!enabled ? Ui.alpha(theme.muted, 70) : hover[0] ? theme.fg : theme.muted);
                Ui.chevron(gfx, x + 13, y + 12, 4, up);
                gfx.dispose();
            }

            @Override
            public int getIconWidth() {
                return 26;
            }

            @Override
            public int getIconHeight() {
                return 24;
            }
        });
        if (enabled) {
            label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            label.setToolTipText(up ? "Move up" : "Move down");
            label.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    run.run();
                }

                @Override
                public void mouseEntered(MouseEvent event) {
                    hover[0] = true;
                    label.repaint();
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    hover[0] = false;
                    label.repaint();
                }
            });
        }
        return label;
    }

    /** Rebuilds the visible list from the full order, toggling one column when {@code toggled} is set. */
    private void applyColumns(List<String> order, String toggled, boolean on) {
        OverlayPrefs prefs = host.prefs();
        List<String> visible = new ArrayList<String>();
        for (String id : order) {
            boolean show = id.equals(toggled) ? on : prefs.columns.contains(id);
            if (show) {
                visible.add(id);
            }
        }
        prefs.columns.clear();
        prefs.columns.addAll(OverlayColumns.sanitize(visible));
        fillColumnList();
        host.layoutChanged();
    }

    private JPanel alerts() {
        Form form = new Form(2);
        form.section("In game");
        form.row("Tab marks", "SD flags and Urchin tags next to names in tab", gameToggle("tabMarks"));
        form.row("Chat hovers", "Tags next to names in chat, plus a flags tooltip on hover", gameToggle("chatHovers"));
        form.row("Chat alerts", "A chat line when someone is flagged", gameToggle("alertsChat"));
        form.row("Alert sound", null, gameToggle("alertSound"));
        form.row("Stay on top", "Keep the overlay above borderless Minecraft", gameToggle("borderless"));
        form.row("Update check", "Look for a new release once a day", gameToggle("updateCheck"));
        form.section("Install");
        JButton update = Ui.button(theme, "Check for updates", Ui.PRIMARY);
        final JLabel updateStatus = Ui.label(theme,
                Updater.currentVersion() == null ? "Downloads the latest jar and restarts this window."
                        : "This window is " + Updater.currentVersion()
                                + ". Overlay restarts now; restart Lunar for checks.",
                theme.small, theme.muted);
        update.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                showStatus(updateStatus, "Checking GitHub\u2026", theme.muted);
                Hud.writeUpdate();
            }
        });
        JPanel install = new JPanel(new BorderLayout(12, 0));
        install.setOpaque(false);
        install.add(update, BorderLayout.WEST);
        install.add(updateStatus, BorderLayout.CENTER);
        form.full(install);
        bindings.add(new Binding() {
            @Override
            public String name() {
                return "updateNotice";
            }

            @Override
            public void load() {
                String text = Overlay.notice();
                if (text != null && !text.isEmpty()) {
                    showStatus(updateStatus, text, theme.fg);
                }
            }
        });
        form.section("Dodge warnings");
        form.row("Dodge warnings", "One chat line, sound and title per player per lobby", gameToggle("dodgeEnabled"));
        form.row("Blacklisted players", null, gameToggle("dodgeBlacklist"));
        form.row("Flagged in an earlier game", null, gameToggle("dodgeFlagged"));
        form.row("Urchin tags", "Dodge when Urchin has tagged them", gameToggle("dodgeTags"));
        form.row("FKDR at least", "0 turns this rule off", gameSpinner("dodgeFkdr", 0, 100, 0.5, false));
        form.row("Stars at least", "Only for the FKDR rule", gameSpinner("dodgeStars", 0, 5000, 50, true));
        form.row("Sniper score at least", "0 turns this rule off", gameSpinner("dodgeSniper", 0, 100, 5, true));
        return form.page;
    }

    private JPanel checks() {
        Form form = new Form(3);
        form.section("Checks");
        JPanel grid = new JPanel(new GridLayout(0, 2, 24, 0));
        grid.setOpaque(false);
        for (int i = 0; i < CheckConfig.CHECKS.length; i++) {
            FlagStore.Flag flag = CheckConfig.CHECKS[i];
            JPanel cell = new JPanel(new BorderLayout(8, 0));
            cell.setOpaque(false);
            cell.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(i < 2 ? 0 : 1, 0, 0, 0, theme.rowLine),
                    BorderFactory.createEmptyBorder(8, 0, 8, 0)));
            JPanel names = new JPanel(new GridLayout(3, 1, 0, 1));
            names.setOpaque(false);
            names.add(Ui.label(theme, CheckConfig.label(flag), theme.small, theme.fg));
            names.add(Ui.label(theme, "SD:" + flag.name(), theme.caption, theme.muted));
            names.add(Ui.label(theme, CheckConfig.meaning(flag),
                    theme.small.deriveFont(theme.small.getSize2D() - 1f), theme.muted));
            cell.add(names, BorderLayout.CENTER);
            cell.add(gameToggle("check." + flag.name()), BorderLayout.EAST);
            grid.add(cell);
        }
        form.full(grid);
        form.section("Thresholds");
        final JComboBox<String> sensitivity = Ui.combo(theme, new String[] { "Lenient", "Normal", "Strict" });
        sensitivity.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                if (!loading) {
                    Overlay.setOption("sensitivity", Settings.SENSITIVITIES[Math.max(0, sensitivity.getSelectedIndex())]);
                }
            }
        });
        bindings.add(new Binding() {
            @Override
            public String name() {
                return "sensitivity";
            }

            @Override
            public void load() {
                Object value = Overlay.option("sensitivity");
                sensitivity.setSelectedIndex(indexOf(Settings.SENSITIVITIES, value == null ? "normal" : value.toString(), 1));
            }
        });
        form.row("Sensitivity", "Scales how long a check must fail before it flags", sized(Ui.boxed(theme, sensitivity), 140));
        form.row("Reach flag", "Blocks", gameSpinner("reachFlag", 3.0, 6.0, 0.05, false));
        form.row("Killaura angle", "Degrees", gameSpinner("kaAngle", 30, 180, 5, true));
        form.row("Autoclicker CPS", "Minimum clicks per second", gameSpinner("acMinCps", 8, 30, 1, true));
        form.row("Speed limit", "Blocks per tick", gameSpinner("speedLimit", 0.3, 1.5, 0.01, false));
        form.section("Other SafeDetect tags");
        for (int i = 0; i < CheckConfig.OTHER_SD.length; i++) {
            form.gloss(CheckConfig.OTHER_SD[i][0], CheckConfig.OTHER_SD[i][1]);
        }
        form.section("Urchin tags");
        form.gloss("Needs an Urchin key", "Chips start with U:. Most lobby players have none. Short labels on the overlay are the start of the full tag name.");
        for (int i = 0; i < CheckConfig.URCHIN.length; i++) {
            form.gloss(CheckConfig.URCHIN[i][0], CheckConfig.URCHIN[i][1]);
        }
        return form.page;
    }

    private JPanel keys() {
        Form form = new Form(4);
        form.section("API keys");
        final JTextField[] fields = new JTextField[KEY_FIELDS.length];
        for (int i = 0; i < KEY_FIELDS.length; i++) {
            boolean secret = !"sniperUrl".equals(KEY_FIELDS[i][0]) && !"discord".equals(KEY_FIELDS[i][0]);
            fields[i] = secret ? Ui.password(theme) : new Ui.Field(theme, "https://...");
            fields[i].addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent event) {
                    ((JTextField) event.getComponent()).selectAll();
                }
            });
            Ui.Pill state = new Ui.Pill("Not set", 10);
            state.setFont(theme.caption);
            state.setBorder(BorderFactory.createEmptyBorder(3, 9, 3, 9));
            keyStates.add(state);
            JPanel row = new JPanel(new BorderLayout(10, 0));
            row.setOpaque(false);
            row.add(sized(Ui.boxed(theme, fields[i]), 220), BorderLayout.CENTER);
            JPanel stateBox = new JPanel(new GridBagLayout());
            stateBox.setOpaque(false);
            stateBox.setPreferredSize(new Dimension(64, 10));
            stateBox.add(state);
            row.add(stateBox, BorderLayout.EAST);
            form.row(KEY_FIELDS[i][1], null, row);
        }
        JButton save = Ui.button(theme, "Save keys", Ui.PRIMARY);
        JButton clear = Ui.button(theme, "Clear all", Ui.GHOST);
        final JLabel[] status = new JLabel[1];
        ActionListener saveKeys = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                Map<String, String> keys = new LinkedHashMap<String, String>();
                List<String> names = new ArrayList<String>();
                for (int i = 0; i < KEY_FIELDS.length; i++) {
                    String value = fields[i] instanceof JPasswordField
                            ? new String(((JPasswordField) fields[i]).getPassword()) : fields[i].getText();
                    if (value != null && !value.trim().isEmpty()) {
                        keys.put(KEY_FIELDS[i][0], value.trim());
                        names.add(KEY_FIELDS[i][1]);
                    }
                    fields[i].setText("");
                }
                if (keys.isEmpty()) {
                    showStatus(status[0], "Paste a key into a field first.", theme.warn);
                    return;
                }
                Hud.writeKeys(keys);
                showStatus(status[0], "Saved " + join(names) + ". The badge turns green once the game picks it up.",
                        theme.success);
            }
        };
        save.addActionListener(saveKeys);
        for (JTextField field : fields) {
            field.addActionListener(saveKeys);
        }
        clear.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                if (!Ui.confirm(SettingsPanel.this, theme, "Remove all keys?",
                        "Every saved API key will be removed from the game.", "Remove", true)) {
                    return;
                }
                Map<String, String> keys = new LinkedHashMap<String, String>();
                for (String[] field : KEY_FIELDS) {
                    keys.put(field[0], "");
                }
                Hud.writeKeys(keys);
                showStatus(status[0], "All keys removed.", theme.muted);
            }
        });
        JPanel buttons = Ui.row(8);
        buttons.add(save);
        buttons.add(clear);
        status[0] = form.after(buttons, "Paste a key and press Enter or Save keys. Blank fields are left unchanged.");
        return form.page;
    }

    private JPanel plugins() {
        Form form = new Form(5);
        form.section("Marketplace");
        pluginList = new JPanel();
        pluginList.setLayout(new BoxLayout(pluginList, BoxLayout.Y_AXIS));
        pluginList.setOpaque(false);
        form.full(pluginList);
        form.section("Share your own");
        form.gloss("Drop-in jars",
                "Put a jar in config/safedetect-plugins with Plugin-Class in the manifest. It must implement com.safedetect.agent.Plugin. Jars run with the same access as SafeDetect.");
        form.gloss("Commands", "Every add-on lives under /sd. Toggle with /sd plugins on|off <id>.");
        fillPlugins();
        return form.page;
    }

    private void fillPlugins() {
        if (pluginList == null) {
            return;
        }
        List<Hud.PluginCard> cards = Overlay.plugins();
        StringBuilder key = new StringBuilder();
        for (Hud.PluginCard each : cards) {
            key.append(each.id).append(each.on ? '1' : '0').append('|');
        }
        String next = key.toString();
        if (next.equals(pluginKey) && pluginList.getComponentCount() > 0) {
            return;
        }
        pluginKey = next;
        pluginList.removeAll();
        if (cards.isEmpty()) {
            pluginList.add(Ui.label(theme, "Join a world so the game can list plugins.", theme.small, theme.muted));
        }
        for (int i = 0; i < cards.size(); i++) {
            pluginList.add(pluginCard(cards.get(i), i > 0));
        }
        pluginList.revalidate();
        pluginList.repaint();
    }

    private JComponent pluginCard(final Hud.PluginCard plugin, boolean line) {
        JPanel card = new JPanel(new BorderLayout(12, 0));
        card.setOpaque(false);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(line ? 1 : 0, 0, 0, 0, theme.rowLine),
                BorderFactory.createEmptyBorder(10, 0, 10, 0)));
        JPanel labels = new JPanel();
        labels.setLayout(new BoxLayout(labels, BoxLayout.Y_AXIS));
        labels.setOpaque(false);
        JLabel title = Ui.label(theme, plugin.name + "  " + plugin.version, theme.small, theme.fg);
        JLabel meta = Ui.label(theme, plugin.author + " · /sd " + plugin.id, theme.caption, theme.muted);
        JLabel blurb = Ui.label(theme, plugin.blurb, theme.small.deriveFont(theme.small.getSize2D() - 1f), theme.muted);
        labels.add(title);
        labels.add(Box.createVerticalStrut(2));
        labels.add(meta);
        labels.add(Box.createVerticalStrut(4));
        labels.add(blurb);
        if (plugin.needs != null && !plugin.needs.isEmpty()) {
            labels.add(Box.createVerticalStrut(2));
            labels.add(Ui.label(theme, plugin.needs, theme.caption, theme.warn));
        }
        if (plugin.commands != null && !plugin.commands.isEmpty()) {
            labels.add(Ui.label(theme, plugin.commands, theme.caption, theme.muted));
        }
        card.add(labels, BorderLayout.CENTER);
        final JButton toggle = Ui.button(theme, plugin.on ? "On" : "Off", plugin.on ? Ui.PRIMARY : Ui.GHOST);
        toggle.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                Overlay.setPlugin(plugin.id, !plugin.on);
                plugin.on = !plugin.on;
                pluginKey = "";
                fillPlugins();
            }
        });
        JPanel control = new JPanel(new GridBagLayout());
        control.setOpaque(false);
        control.add(toggle);
        card.add(control, BorderLayout.EAST);
        return card;
    }

    private static void showStatus(JLabel label, String text, Color color) {
        label.setText(text);
        label.setForeground(color);
    }

    private static String join(List<String> names) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            out.append(i == 0 ? "" : i == names.size() - 1 ? " and " : ", ").append(names.get(i));
        }
        return out.toString();
    }

    // ---- game-owned controls ----

    private JCheckBox gameToggle(final String name) {
        final JCheckBox box = Ui.toggle(theme, true);
        box.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                if (!loading) {
                    Overlay.setOption(name, Boolean.valueOf(box.isSelected()));
                }
            }
        });
        bindings.add(new Binding() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public void load() {
                box.setSelected(Overlay.optionOn(name, true));
            }
        });
        return box;
    }

    private JComponent gameSpinner(final String name, double min, double max, double step, final boolean whole) {
        double start = Overlay.optionNumber(name, min);
        SpinnerNumberModel model = whole
                ? new SpinnerNumberModel((int) Math.max(min, Math.min(max, start)), (int) min, (int) max, (int) step)
                : new SpinnerNumberModel(Math.max(min, Math.min(max, start)), min, max, step);
        final JSpinner spinner = new JSpinner(model);
        if (!whole) {
            spinner.setEditor(new JSpinner.NumberEditor(spinner, step < 0.1 ? "0.00" : "0.0"));
        }
        Ui.style(spinner, theme);
        spinner.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent event) {
                if (!loading) {
                    Overlay.setOption(name, (Number) spinner.getValue());
                }
            }
        });
        bindings.add(new Binding() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public void load() {
                double value = Overlay.optionNumber(name, Double.NaN);
                if (!Double.isNaN(value)) {
                    spinner.setValue(whole ? (Object) Integer.valueOf((int) Math.round(value)) : (Object) Double.valueOf(value));
                }
            }
        });
        return sized(Ui.boxed(theme, spinner), 104);
    }

    // ---- small helpers ----

    private static int indexOf(String[] values, String value, int fallback) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equalsIgnoreCase(value)) {
                return i;
            }
        }
        return fallback;
    }

    private static JComponent sized(JComponent component, int width) {
        Dimension size = component.getPreferredSize();
        Dimension fixed = new Dimension(width, Math.max(30, size.height));
        component.setPreferredSize(fixed);
        component.setMinimumSize(fixed);
        return component;
    }

    private JLabel value(String text) {
        JLabel label = Ui.label(theme, text, theme.small, theme.muted);
        label.setPreferredSize(new Dimension(40, label.getPreferredSize().height));
        return label;
    }

    private JPanel withValue(JComponent control, JLabel value) {
        control.setPreferredSize(new Dimension(170, 24));
        JPanel row = Ui.row(10);
        ((FlowLayout) row.getLayout()).setAlignOnBaseline(false);
        row.add(control);
        row.add(value);
        row.setMinimumSize(row.getPreferredSize());
        return row;
    }

    /** One settings page: a title, then labelled sections each drawn as a rounded card of rows. */
    private final class Form {
        final JPanel page = new JPanel(new GridBagLayout());
        private Ui.Round card;
        private int pageRow;
        private int cardRow;

        Form(int index) {
            page.setBackground(theme.bg);
            page.setBorder(BorderFactory.createEmptyBorder(16, 18, 18, 18));
            JLabel title = Ui.label(theme, CARDS[index], theme.title.deriveFont(theme.title.getSize2D() + 3f), theme.fg);
            add(title, new Insets(0, 2, 2, 0));
            JLabel subtitle = Ui.label(theme, SUBTITLES[index], theme.small, theme.muted);
            add(subtitle, new Insets(0, 2, 4, 0));
        }

        private void add(JComponent component, Insets insets) {
            GridBagConstraints c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = pageRow++;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.anchor = GridBagConstraints.WEST;
            c.insets = insets;
            page.add(component, c);
        }

        void section(String text) {
            JLabel label = Ui.label(theme, text.toUpperCase(Locale.ROOT), Ui.tracked(theme.caption, 0.08f), theme.muted);
            add(label, new Insets(16, 4, 6, 0));
            card = new Ui.Round(theme.card, theme.line);
            card.radius = 10;
            card.setLayout(new GridBagLayout());
            card.setBorder(BorderFactory.createEmptyBorder(4, 14, 4, 14));
            cardRow = 0;
            add(card, new Insets(0, 0, 0, 0));
        }

        /** Label (with an optional muted description) on the left, control on the right. */
        void row(String text, String description, JComponent control) {
            JPanel line = new JPanel(new BorderLayout(16, 0));
            line.setOpaque(false);
            JPanel labels = new JPanel(new GridLayout(description == null ? 1 : 2, 1, 0, 1));
            labels.setOpaque(false);
            labels.add(Ui.label(theme, text, theme.small.deriveFont(theme.small.getSize2D() + 0.5f), theme.fg));
            if (description != null) {
                labels.add(Ui.label(theme, description, theme.small.deriveFont(theme.small.getSize2D() - 1f), theme.muted));
            }
            JPanel labelBox = new JPanel(new GridBagLayout());
            labelBox.setOpaque(false);
            GridBagConstraints c = new GridBagConstraints();
            c.anchor = GridBagConstraints.WEST;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            labelBox.add(labels, c);
            Dimension natural = labelBox.getPreferredSize();
            labelBox.setPreferredSize(new Dimension(Math.min(natural.width, 120), natural.height));
            labelBox.setMinimumSize(new Dimension(60, natural.height));
            line.add(labelBox, BorderLayout.CENTER);
            JPanel controlBox = new JPanel(new GridBagLayout());
            controlBox.setOpaque(false);
            controlBox.add(control);
            line.add(controlBox, BorderLayout.EAST);
            addToCard(line, 9);
        }

        void gloss(String title, String body) {
            JPanel line = new JPanel(new BorderLayout(12, 0));
            line.setOpaque(false);
            JPanel labels = new JPanel(new GridLayout(body == null ? 1 : 2, 1, 0, 1));
            labels.setOpaque(false);
            labels.add(Ui.label(theme, title, theme.small.deriveFont(theme.small.getSize2D() + 0.5f), theme.fg));
            if (body != null) {
                labels.add(Ui.label(theme, body, theme.small.deriveFont(theme.small.getSize2D() - 1f), theme.muted));
            }
            line.add(labels, BorderLayout.CENTER);
            addToCard(line, 8);
        }

        void full(JComponent control) {
            JPanel line = new JPanel(new BorderLayout());
            line.setOpaque(false);
            line.add(control, BorderLayout.CENTER);
            addToCard(line, 4);
        }

        /** Buttons or notes below the current card. */
        JLabel after(JComponent control, String note) {
            JPanel line = new JPanel(new BorderLayout(12, 0));
            line.setOpaque(false);
            line.add(control, BorderLayout.WEST);
            JLabel label = null;
            if (note != null) {
                label = Ui.label(theme, note, theme.small, theme.muted);
                line.add(label, BorderLayout.CENTER);
            }
            add(line, new Insets(12, 0, 0, 0));
            return label;
        }

        private void addToCard(JPanel line, int pad) {
            line.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(cardRow == 0 ? 0 : 1, 0, 0, 0, theme.rowLine),
                    BorderFactory.createEmptyBorder(pad, 0, pad, 0)));
            GridBagConstraints c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = cardRow++;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            card.add(line, c);
        }
    }
}
