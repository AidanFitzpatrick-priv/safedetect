package com.safedetect.agent;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Arc2D;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lobby overlay window, run in its own process. Reads {@code safedetect-hud.json}, sends commands through
 * {@code safedetect-cmd/}, and keeps its own look in {@link OverlayPrefs}.
 */
public final class Overlay implements SettingsPanel.Host {
    /** Game options changed here are trusted over the game's copy for this long, so controls don't jump back. */
    static final long OPTION_GRACE_MS = 1500L;
    /** Window corner radius in pixels. */
    private static final int CORNER = 10;

    private static final Map<String, Object> GAME_OPTIONS = new ConcurrentHashMap<String, Object>();
    private static final Map<String, Object> SENT_OPTIONS = new ConcurrentHashMap<String, Object>();
    private static final Map<String, Long> SENT_AT = new ConcurrentHashMap<String, Long>();
    private static final Map<String, Boolean> KEYS_SET = new ConcurrentHashMap<String, Boolean>();
    private static final List<Hud.PluginCard> PLUGIN_CARDS = new ArrayList<Hud.PluginCard>();
    private static volatile String sessionText = "";
    private static volatile String noticeText = "";
    private static Overlay instance;
    private static File dataDir;

    private final JFrame frame;
    private final OverlayPrefs prefs;
    private Theme theme;

    private boolean showingLobby = true;
    private boolean compact;
    private boolean userHidden;
    private boolean settingsOpen;
    private String settingsCard = SettingsPanel.CARDS[0];
    private String searchText = "";
    private String savedSort = "";
    private boolean savedAscending;
    private int players;
    private List<Hud.Row> lobby = new ArrayList<Hud.Row>();
    private List<Hud.Row> saved = new ArrayList<Hud.Row>();
    private String lastKey = "";

    private JLabel brand;
    private JLabel lobbyTab;
    private JLabel savedTab;
    private JLabel banner;
    private JPanel bannerBox;
    private int hoverRow = -1;
    private JTextField search;
    private JTable table;
    private RowsModel model;
    private JScrollPane scroll;
    private JPanel chrome;
    private JPanel center;
    private CardLayout centerCards;
    private SettingsPanel settings;
    private JPanel footer;
    private JLabel tabPref;
    private JLabel chatPref;
    private JLabel soundPref;
    private Glyph miniIcon;
    private Point drag;
    private Point resize;
    private boolean resizedHeader;

    public static void main(String[] args) {
        System.setProperty("safedetect.overlay", "true");
        File dir = new File(args != null && args.length > 0 ? args[0] : ".");
        dataDir = dir;
        Hud.open(dir);
        long lastReopen = -1L;
        long lastRefresh = -1L;
        long lastToggle = -1L;
        while (true) {
            try {
                Hud.Snapshot snap = Hud.readState();
                if (snap != null) {
                    sessionText = snap.session == null ? "" : snap.session;
                    noticeText = snap.notice == null ? "" : snap.notice;
                    GAME_OPTIONS.clear();
                    GAME_OPTIONS.putAll(snap.options);
                    KEYS_SET.clear();
                    KEYS_SET.putAll(snap.keysSet);
                    synchronized (PLUGIN_CARDS) {
                        PLUGIN_CARDS.clear();
                        if (snap.plugins != null) {
                            PLUGIN_CARDS.addAll(snap.plugins);
                        }
                    }
                    boolean reopenNow = lastReopen >= 0L && snap.reopen != lastReopen;
                    boolean refreshNow = lastRefresh >= 0L && snap.refresh != lastRefresh;
                    boolean toggleNow = lastToggle >= 0L && snap.toggle != lastToggle;
                    lastReopen = snap.reopen;
                    lastRefresh = snap.refresh;
                    lastToggle = snap.toggle;
                    if (reopenNow) {
                        reopen();
                    } else if (toggleNow) {
                        toggleHidden();
                    }
                    show(snap.players, snap.lobby, snap.saved, reopenNow || refreshNow);
                }
                Thread.sleep(150L);
            } catch (InterruptedException ignored) {
                return;
            } catch (Throwable thrown) {
                Log.once("overlay process", thrown);
            }
        }
    }

    // ---- game options shared with the settings panel ----

    static boolean optionPending(String name) {
        Long at = SENT_AT.get(name);
        return at != null && System.currentTimeMillis() - at.longValue() < OPTION_GRACE_MS;
    }

    /** The game's value, or the one just sent while the game catches up. */
    static Object option(String name) {
        if (optionPending(name)) {
            return SENT_OPTIONS.get(name);
        }
        if (name.startsWith("check.")) {
            Object disabled = GAME_OPTIONS.get("disabledChecks");
            if (!(disabled instanceof List)) {
                return GAME_OPTIONS.isEmpty() ? null : Boolean.TRUE;
            }
            return Boolean.valueOf(!((List<?>) disabled).contains(name.substring(6)));
        }
        return GAME_OPTIONS.get(name);
    }

    static boolean optionOn(String name, boolean fallback) {
        Object value = option(name);
        return value instanceof Boolean ? ((Boolean) value).booleanValue() : fallback;
    }

    static double optionNumber(String name, double fallback) {
        Object value = option(name);
        return value instanceof Number ? ((Number) value).doubleValue() : fallback;
    }

    static void setOption(String name, Object value) {
        SENT_OPTIONS.put(name, value);
        SENT_AT.put(name, Long.valueOf(System.currentTimeMillis()));
        Hud.writeSet(name, value);
    }

    static boolean keySet(String name) {
        return Boolean.TRUE.equals(KEYS_SET.get(name));
    }

    static List<Hud.PluginCard> plugins() {
        synchronized (PLUGIN_CARDS) {
            return new ArrayList<Hud.PluginCard>(PLUGIN_CARDS);
        }
    }

    static void setPlugin(String id, boolean on) {
        Hud.writePlugin(id, on);
        synchronized (PLUGIN_CARDS) {
            for (Hud.PluginCard card : PLUGIN_CARDS) {
                if (id.equals(card.id)) {
                    card.on = on;
                }
            }
        }
    }

    static String notice() {
        return noticeText == null ? "" : noticeText;
    }

    // ---- window ----

    private Overlay() {
        prefs = OverlayPrefs.load(dataDir == null ? new File(".") : dataDir);
        theme = Theme.of(prefs.theme, prefs.accent, prefs.fontScale);
        frame = new JFrame("SafeDetect");
        frame.setUndecorated(true);
        frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        frame.setAlwaysOnTop(true);
        frame.setAutoRequestFocus(false);
        frame.setFocusableWindowState(true);
        frame.setType(JFrame.Type.NORMAL);
        frame.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent event) {
                roundCorners();
            }
        });
        build();
        if (prefs.hasBounds()) {
            frame.setSize(Math.max(420, prefs.w), Math.max(120, prefs.h));
            frame.setLocation(prefs.x, prefs.y);
        } else {
            frame.setSize(760, 500);
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            frame.setLocation(Math.max(0, screen.width - 780), 36);
        }
        Timer topmost = new Timer(2500, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                if (drag != null || resize != null || !frame.isVisible()) {
                    return;
                }
                if (optionOn("borderless", true) && !frame.isAlwaysOnTop()) {
                    frame.setAlwaysOnTop(true);
                }
            }
        });
        topmost.setRepeats(true);
        topmost.start();
        opacityChanged();
        ensureOnScreen();
        frame.setVisible(true);
    }

    /** (Re)creates every widget from the current theme and prefs, keeping the view state. */
    private void build() {
        Ui.install(theme);
        frame.setBackground(theme.bg);
        JPanel root = new JPanel(new BorderLayout()) {
            private static final long serialVersionUID = 1L;

            @Override
            public void paint(Graphics g) {
                super.paint(g);
                Graphics2D gfx = Ui.smooth(g);
                gfx.setColor(theme.light ? theme.line : Theme.mix(theme.line, theme.fg, 0.08));
                gfx.draw(new java.awt.geom.RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1,
                        CORNER * 2, CORNER * 2));
                gfx.dispose();
            }
        };
        root.setBackground(theme.bg);
        root.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        chrome = chrome();
        top.add(chrome, BorderLayout.NORTH);
        banner = new JLabel();
        banner.setFont(theme.small);
        banner.setForeground(theme.fg);
        banner.setIcon(new WarnIcon());
        banner.setIconTextGap(8);
        banner.setBorder(BorderFactory.createEmptyBorder(7, 10, 7, 10));
        Ui.Round bannerCard = new Ui.Round(Theme.mix(theme.bg, theme.danger, theme.light ? 0.08 : 0.13),
                Theme.mix(theme.bg, theme.danger, theme.light ? 0.3 : 0.38));
        bannerCard.add(banner, BorderLayout.CENTER);
        bannerBox = new JPanel(new BorderLayout());
        bannerBox.setBackground(theme.bg);
        bannerBox.setBorder(BorderFactory.createEmptyBorder(8, 10, 2, 10));
        bannerBox.add(bannerCard, BorderLayout.CENTER);
        bannerBox.setVisible(false);
        top.add(bannerBox, BorderLayout.SOUTH);
        root.add(top, BorderLayout.NORTH);

        model = new RowsModel();
        table = table();
        scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(theme.bg);
        scroll.getVerticalScrollBar().setUI(new ThinBar(theme));
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(10, 0));
        scroll.getVerticalScrollBar().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBackground(theme.bg);
        scroll.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

        centerCards = new CardLayout();
        center = new JPanel(centerCards);
        center.setBackground(theme.bg);
        center.add(scroll, "table");
        settings = null;
        root.add(center, BorderLayout.CENTER);
        footer = footer();
        root.add(footer, BorderLayout.SOUTH);
        frame.setContentPane(root);

        if (settingsOpen) {
            openSettings(settingsCard);
        }
        restructure();
        refill();
        applyModes();
        frame.validate();
        frame.repaint();
    }

    private JPanel chrome() {
        JPanel bar = new JPanel(new BorderLayout(14, 0));
        bar.setBackground(theme.header);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, theme.line),
                BorderFactory.createEmptyBorder(8, 12, 8, 8)));
        brand = new JLabel(brandText());
        brand.setFont(theme.title);
        brand.setForeground(theme.fg);
        brand.setIcon(new LogoIcon());
        brand.setIconTextGap(8);
        if (!notice().isEmpty()) {
            brand.setToolTipText(notice());
        }
        JPanel left = new JPanel(new BorderLayout(14, 0));
        left.setOpaque(false);
        left.add(brand, BorderLayout.WEST);
        Ui.Round tabs = new Ui.Round(theme.field, null);
        tabs.setLayout(new GridLayout(1, 2, 2, 0));
        tabs.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
        lobbyTab = tab("Lobby");
        savedTab = tab("Saved");
        lobbyTab.addMouseListener(clickTab(true));
        savedTab.addMouseListener(clickTab(false));
        tabs.add(lobbyTab);
        tabs.add(savedTab);
        JPanel tabsHolder = new JPanel(new java.awt.GridBagLayout());
        tabsHolder.setOpaque(false);
        tabsHolder.add(tabs);
        left.add(tabsHolder, BorderLayout.CENTER);
        bar.add(left, BorderLayout.WEST);

        search = new Ui.Field(theme, "Search players");
        search.setBorder(BorderFactory.createEmptyBorder(6, 2, 6, 10));
        if (!searchText.isEmpty()) {
            search.setText(searchText);
        }
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                typed();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                typed();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                typed();
            }

            private void typed() {
                String text = search.getText();
                searchText = text == null ? "" : text.trim();
                refill();
            }
        });
        Ui.Round searchBox = Ui.boxed(theme, search);
        JLabel lens = new JLabel(new SearchIcon());
        lens.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 6));
        searchBox.add(lens, BorderLayout.WEST);
        searchBox.setPreferredSize(new Dimension(190, Math.round(30 * theme.fontPercent / 100f)));
        JPanel searchHolder = new JPanel(new java.awt.GridBagLayout());
        searchHolder.setOpaque(false);
        java.awt.GridBagConstraints fill = new java.awt.GridBagConstraints();
        fill.fill = java.awt.GridBagConstraints.HORIZONTAL;
        fill.weightx = 1;
        searchHolder.add(searchBox, fill);
        bar.add(searchHolder, BorderLayout.CENTER);

        JPanel actions = new JPanel(new GridLayout(1, 5, 2, 0));
        actions.setOpaque(false);
        actions.add(new Glyph(Glyph.REFRESH, "Refresh stats", new Runnable() {
            @Override
            public void run() {
                lastKey = "";
                Hud.writeCommand(true, null);
            }
        }));
        miniIcon = new Glyph(Glyph.MINI, "Mini mode: only threats, small rows", new Runnable() {
            @Override
            public void run() {
                prefs.mini = !prefs.mini;
                prefs.saveLater();
                miniIcon.on = prefs.mini;
                applyModes();
            }
        });
        miniIcon.on = prefs.mini;
        actions.add(miniIcon);
        actions.add(new Glyph(Glyph.GEAR, "Settings", new Runnable() {
            @Override
            public void run() {
                if (settingsOpen) {
                    closeSettings();
                } else {
                    openSettings(settingsCard);
                }
            }
        }));
        actions.add(new Glyph(Glyph.MINIMIZE, "Collapse to the title bar", new Runnable() {
            @Override
            public void run() {
                compact = !compact;
                applyModes();
            }
        }));
        actions.add(new Glyph(Glyph.CLOSE, "Hide (Right Shift or /sd gui)", new Runnable() {
            @Override
            public void run() {
                userHidden = true;
                frame.setVisible(false);
            }
        }));
        JPanel actionsHolder = new JPanel(new java.awt.GridBagLayout());
        actionsHolder.setOpaque(false);
        actionsHolder.add(actions);
        bar.add(actionsHolder, BorderLayout.EAST);
        enableDrag(bar);
        enableDrag(left);
        enableDrag(brand);
        enableDrag(tabsHolder);
        enableDrag(searchHolder);
        enableDrag(actionsHolder);
        paintTabs();
        return bar;
    }

    private JTable table() {
        final JTable view = new JTable(model);
        view.setBackground(theme.bg);
        view.setForeground(theme.fg);
        view.setGridColor(theme.bg);
        view.setShowGrid(false);
        view.setIntercellSpacing(new Dimension(0, 0));
        view.setFont(theme.body);
        view.setSelectionBackground(theme.select);
        view.setSelectionForeground(theme.fg);
        view.setFillsViewportHeight(true);
        view.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        view.setDefaultRenderer(Object.class, new Cell());
        final JTableHeader header = view.getTableHeader();
        header.setBackground(theme.bg);
        header.setForeground(theme.muted);
        header.setFont(theme.caption);
        header.setReorderingAllowed(true);
        header.setResizingAllowed(true);
        header.setBorder(BorderFactory.createEmptyBorder());
        header.setPreferredSize(new Dimension(0, Math.round(28 * Math.max(1f, theme.fontPercent / 100f))));
        final java.awt.Font captionFont = Ui.tracked(theme.caption, 0.06f);
        DefaultTableCellRenderer headerCells = new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                    boolean focus, int row, int column) {
                super.getTableCellRendererComponent(table, value, selected, focus, row, column);
                OverlayColumns.Column col = model.column(table.convertColumnIndexToModel(column));
                String sort = showingLobby ? prefs.sortColumn : savedSort;
                boolean sorted = col != null && col.id.equals(sort);
                setText(value == null ? "" : value.toString().toUpperCase(Locale.ROOT));
                setBackground(theme.bg);
                setForeground(sorted ? theme.accent : theme.muted);
                setFont(captionFont);
                setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, theme.line),
                        BorderFactory.createEmptyBorder(0, column == 0 ? 14 : 8, 0, 8)));
                setHorizontalAlignment(col != null && col.right ? SwingConstants.RIGHT : SwingConstants.LEFT);
                return this;
            }
        };
        header.setDefaultRenderer(headerCells);
        view.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                int row = view.rowAtPoint(event.getPoint());
                if (row != hoverRow) {
                    hoverRow = row;
                    view.repaint();
                }
            }
        });
        header.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (!SwingUtilities.isLeftMouseButton(event) || header.getCursor().getType() == Cursor.E_RESIZE_CURSOR) {
                    return;
                }
                int column = header.columnAtPoint(event.getPoint());
                if (column >= 0) {
                    sortBy(String.valueOf(view.getColumnModel().getColumn(column).getIdentifier()));
                }
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                final boolean resized = resizedHeader;
                resizedHeader = false;
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        syncColumns(resized);
                    }
                });
            }
        });
        header.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent event) {
                resizedHeader |= header.getResizingColumn() != null;
            }
        });
        view.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent event) {
                int row = view.getSelectedRow();
                if (row >= 0 && model.row(row) == null) {
                    view.clearSelection();
                }
            }
        });
        view.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                popup(event);
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                popup(event);
            }

            @Override
            public void mouseExited(MouseEvent event) {
                hoverRow = -1;
                view.repaint();
            }

            @Override
            public void mouseClicked(MouseEvent event) {
                if (SwingUtilities.isLeftMouseButton(event) && event.getClickCount() == 2) {
                    Hud.Row row = model.row(view.rowAtPoint(event.getPoint()));
                    if (row != null) {
                        RowMenu.copy(Hud.wdr(row.name));
                    }
                }
            }

            private void popup(MouseEvent event) {
                if (!event.isPopupTrigger()) {
                    return;
                }
                int index = view.rowAtPoint(event.getPoint());
                Hud.Row row = model.row(index);
                if (row == null) {
                    return;
                }
                view.setRowSelectionInterval(index, index);
                RowMenu.show(view, event.getX(), event.getY(), row, theme);
            }
        });
        return view;
    }

    private JPanel footer() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(theme.header);
        bar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, theme.line),
                BorderFactory.createEmptyBorder(6, 10, 6, 6)));
        JPanel options = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        options.setOpaque(false);
        tabPref = pref("tabMarks", "Tab marks", "flag marks on tab");
        chatPref = pref("alertsChat", "Chat", "chat alerts");
        soundPref = pref("alertSound", "Sound", "alert sound");
        options.add(tabPref);
        options.add(chatPref);
        options.add(soundPref);
        JComponent divider = new JComponent() {
            private static final long serialVersionUID = 1L;

            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(theme.line);
                g.fillRect(getWidth() / 2, 4, 1, getHeight() - 8);
            }
        };
        divider.setPreferredSize(new Dimension(9, 22));
        options.add(divider);
        options.add(action("Clear", "Delete every saved player", true, new Runnable() {
            @Override
            public void run() {
                if (Ui.confirm(frame, theme, "Clear saved players?",
                        "Every saved player and their flags will be deleted. This can't be undone.", "Delete", true)) {
                    Hud.writeClear();
                }
            }
        }));
        options.add(action("Keys", "Set API keys without typing them in chat", false, new Runnable() {
            @Override
            public void run() {
                openSettings("Keys");
            }
        }));
        options.add(action("Update", "Download the latest SafeDetect and restart this window", false, new Runnable() {
            @Override
            public void run() {
                Hud.writeUpdate();
            }
        }));
        paintPrefs();
        bar.add(options, BorderLayout.CENTER);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.setOpaque(false);
        JLabel label = new JLabel(new OpacityIcon());
        label.setToolTipText("Window opacity");
        final JSlider opacity = Ui.slider(theme, 40, 100, prefs.opacity);
        opacity.setPreferredSize(new Dimension(84, 22));
        opacity.setEnabled(translucencySupported());
        opacity.setToolTipText(translucencySupported() ? "Window opacity" : "Translucent windows are not supported here");
        opacity.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent event) {
                prefs.opacity = opacity.getValue();
                opacityChanged();
            }
        });
        right.add(label);
        right.add(opacity);
        right.add(resizeGrip());
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private JLabel pref(final String option, final String label, final String what) {
        final Ui.Pill chip = new Ui.Pill(label, 12);
        chip.setFont(theme.small);
        chip.setIconTextGap(6);
        chip.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 11));
        chip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        chip.putClientProperty("label", label);
        chip.putClientProperty("what", what);
        chip.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                setOption(option, Boolean.valueOf(!optionOn(option, true)));
                paintPrefs();
            }
        });
        chip.putClientProperty("option", option);
        return chip;
    }

    private void paintPrefs() {
        for (JLabel chip : new JLabel[] { tabPref, chatPref, soundPref }) {
            if (chip == null) {
                continue;
            }
            boolean on = optionOn((String) chip.getClientProperty("option"), true);
            Ui.Pill pill = (Ui.Pill) chip;
            pill.setIcon(Ui.dot(on ? theme.success : theme.thumb, 7));
            pill.fill = on ? theme.accentSoft : null;
            pill.outline = on ? null : theme.line;
            chip.setForeground(on ? theme.fg : theme.muted);
            chip.setToolTipText((on ? "Turn off " : "Turn on ") + chip.getClientProperty("what"));
            chip.repaint();
        }
    }

    private JLabel action(String label, String tip, final boolean destructive, final Runnable run) {
        final Ui.Pill chip = new Ui.Pill(label, 12);
        chip.setFont(theme.small);
        chip.setForeground(theme.muted);
        chip.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        chip.setToolTipText(tip);
        chip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        chip.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                run.run();
            }

            @Override
            public void mouseEntered(MouseEvent event) {
                chip.fill = destructive ? Theme.mix(theme.header, theme.danger, 0.14) : theme.hover;
                chip.setForeground(destructive ? theme.danger : theme.fg);
                chip.repaint();
            }

            @Override
            public void mouseExited(MouseEvent event) {
                chip.fill = null;
                chip.setForeground(theme.muted);
                chip.repaint();
            }
        });
        return chip;
    }

    private JLabel tab(String text) {
        Ui.Pill label = new Ui.Pill(text, 6);
        label.setFont(theme.small);
        label.setBorder(BorderFactory.createEmptyBorder(4, 14, 4, 14));
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return label;
    }

    private MouseAdapter clickTab(final boolean toLobby) {
        return new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                showingLobby = toLobby;
                if (settingsOpen) {
                    closeSettings();
                }
                paintTabs();
                restructure();
                refill();
                applyModes();
            }
        };
    }

    private void paintTabs() {
        Color selected = theme.light ? theme.card : Theme.mix(theme.field, theme.fg, 0.09);
        for (JLabel tab : new JLabel[] { lobbyTab, savedTab }) {
            boolean on = (tab == lobbyTab) == showingLobby;
            ((Ui.Pill) tab).fill = on ? selected : null;
            ((Ui.Pill) tab).outline = on && theme.light ? theme.line : null;
            tab.setForeground(on ? theme.fg : theme.muted);
            tab.setFont(on ? theme.bold.deriveFont(theme.small.getSize2D()) : theme.small);
            tab.repaint();
        }
    }

    private String tabText(String label, int count) {
        if (count <= 0) {
            return label;
        }
        return "<html>" + label + "&nbsp;&nbsp;<font color='" + Theme.hex(theme.muted) + "'>" + count + "</font></html>";
    }

    private String brandText() {
        String session = sessionText;
        if (session.isEmpty()) {
            return "SafeDetect";
        }
        return "<html>SafeDetect&nbsp;&nbsp;<span style='font-weight:normal'><font color='" + Theme.hex(theme.muted)
                + "'>" + session + "</font></span></html>";
    }

    // ---- settings host ----

    @Override
    public OverlayPrefs prefs() {
        return prefs;
    }

    @Override
    public Theme theme() {
        return theme;
    }

    @Override
    public void appearanceChanged() {
        prefs.saveLater();
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (settings != null) {
                    settingsCard = settings.card();
                }
                theme = Theme.of(prefs.theme, prefs.accent, prefs.fontScale);
                build();
            }
        });
    }

    @Override
    public void layoutChanged() {
        prefs.saveLater();
        restructure();
        refill();
        applyModes();
    }

    @Override
    public void opacityChanged() {
        prefs.saveLater();
        if (translucencySupported()) {
            try {
                frame.setOpacity(Math.max(0.4f, Math.min(1f, prefs.opacity / 100f)));
            } catch (Throwable thrown) {
                Log.once("overlay opacity", thrown);
            }
        }
    }

    @Override
    public boolean translucencySupported() {
        try {
            return GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                    .isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT);
        } catch (Throwable thrown) {
            return false;
        }
    }

    @Override
    public void closeSettings() {
        if (settings != null) {
            settingsCard = settings.card();
        }
        settingsOpen = false;
        centerCards.show(center, "table");
        applyModes();
    }

    private void openSettings(String card) {
        settingsOpen = true;
        if (settings == null) {
            settings = new SettingsPanel(this, card);
            center.add(settings, "settings");
        } else {
            settings.show(card);
            settings.refresh();
        }
        settingsCard = settings.card();
        centerCards.show(center, "settings");
        if (compact) {
            compact = false;
        }
        applyModes();
    }

    // ---- columns, sorting, rows ----

    private List<String> visibleColumns() {
        return showingLobby ? prefs.columns : java.util.Arrays.asList(OverlayColumns.SAVED);
    }

    /** Rebuilds the table's columns from prefs when the visible set or order changed. */
    private void restructure() {
        List<String> clean = OverlayColumns.sanitize(prefs.columns);
        if (!clean.equals(prefs.columns)) {
            prefs.columns.clear();
            prefs.columns.addAll(clean);
        }
        List<OverlayColumns.Column> next = new ArrayList<OverlayColumns.Column>();
        for (String id : visibleColumns()) {
            OverlayColumns.Column column = OverlayColumns.byId(id);
            if (column != null) {
                next.add(column);
            }
        }
        model.columns = next;
        model.fireTableStructureChanged();
        for (int i = 0; i < table.getColumnModel().getColumnCount(); i++) {
            TableColumn column = table.getColumnModel().getColumn(i);
            OverlayColumns.Column spec = next.get(column.getModelIndex());
            column.setIdentifier(spec.id);
            Integer width = showingLobby ? prefs.widths.get(spec.id) : null;
            column.setPreferredWidth(width != null ? width.intValue() : Math.round(spec.width * theme.fontPercent / 100f));
        }
        table.getTableHeader().setReorderingAllowed(showingLobby);
        paintHeaders();
    }

    /** After a header drag or resize, saves the new order and widths. */
    private void syncColumns(boolean resized) {
        if (!showingLobby || table == null) {
            return;
        }
        List<String> order = new ArrayList<String>();
        boolean changed = false;
        for (int i = 0; i < table.getColumnModel().getColumnCount(); i++) {
            TableColumn column = table.getColumnModel().getColumn(i);
            String id = String.valueOf(column.getIdentifier());
            order.add(id);
            if (resized) {
                prefs.widths.put(id, Integer.valueOf(column.getWidth()));
                changed = true;
            }
        }
        if (!order.equals(prefs.columns)) {
            prefs.columns.clear();
            prefs.columns.addAll(order);
            changed = true;
            restructure();
            refill();
        }
        if (changed) {
            prefs.saveLater();
        }
    }

    /** Descending first for numbers, ascending first for text; a third click returns to threat order. */
    private void sortBy(String id) {
        boolean text = "name".equals(id) || "team".equals(id);
        String current = showingLobby ? prefs.sortColumn : savedSort;
        boolean ascending = showingLobby ? prefs.sortAscending : savedAscending;
        String nextId = id;
        boolean nextAscending = text;
        if (id.equals(current)) {
            if (ascending != text) {
                nextId = "";
                nextAscending = false;
            } else {
                nextAscending = !ascending;
            }
        }
        if (showingLobby) {
            prefs.sortColumn = nextId;
            prefs.sortAscending = nextAscending;
            prefs.saveLater();
        } else {
            savedSort = nextId;
            savedAscending = nextAscending;
        }
        paintHeaders();
        refill();
    }

    private void paintHeaders() {
        String sort = showingLobby ? prefs.sortColumn : savedSort;
        boolean ascending = showingLobby ? prefs.sortAscending : savedAscending;
        for (int i = 0; i < table.getColumnModel().getColumnCount(); i++) {
            TableColumn column = table.getColumnModel().getColumn(i);
            OverlayColumns.Column spec = OverlayColumns.byId(String.valueOf(column.getIdentifier()));
            if (spec == null) {
                continue;
            }
            column.setHeaderValue(spec.id.equals(sort) ? spec.header + (ascending ? " \u25b2" : " \u25bc") : spec.header);
        }
        table.getTableHeader().repaint();
    }

    /** Applies search, mini filter, sort, pinning and team grouping to the current tab's rows. */
    private void refill() {
        if (model == null) {
            return;
        }
        List<Hud.Row> source = showingLobby ? lobby : saved;
        String needle = searchText.toLowerCase(Locale.ROOT);
        boolean mini = showingLobby && prefs.mini && OverlayPrefs.MINI_THREATS.equals(prefs.miniFilter);
        double fkdrLine = optionNumber("dodgeFkdr", 8.0);
        List<Hud.Row> rows = new ArrayList<Hud.Row>();
        for (Hud.Row row : source) {
            if (!needle.isEmpty() && (row.name == null || !row.name.toLowerCase(Locale.ROOT).contains(needle))) {
                continue;
            }
            if (mini && !OverlayColumns.flagged(row) && !(row.fkdr >= 0 && row.fkdr >= fkdrLine)) {
                continue;
            }
            rows.add(row);
        }
        String sort = showingLobby ? prefs.sortColumn : savedSort;
        boolean ascending = showingLobby ? prefs.sortAscending : savedAscending;
        Comparator<Hud.Row> order = OverlayColumns.THREAT_ORDER;
        if (!sort.isEmpty()) {
            final Comparator<Hud.Row> by = OverlayColumns.comparator(sort);
            final Comparator<Hud.Row> directed = ascending ? by : Collections.reverseOrder(by);
            order = new Comparator<Hud.Row>() {
                @Override
                public int compare(Hud.Row a, Hud.Row b) {
                    int first = directed.compare(a, b);
                    return first != 0 ? first : OverlayColumns.THREAT_ORDER.compare(a, b);
                }
            };
        }
        Collections.sort(rows, order);
        if (prefs.pinFlagged) {
            List<Hud.Row> pinned = new ArrayList<Hud.Row>();
            List<Hud.Row> rest = new ArrayList<Hud.Row>();
            for (Hud.Row row : rows) {
                (OverlayColumns.flagged(row) ? pinned : rest).add(row);
            }
            rows = pinned;
            rows.addAll(rest);
        }
        List<Object> entries = new ArrayList<Object>();
        if (showingLobby && prefs.groupByTeam && hasTeams(rows)) {
            Map<String, List<Hud.Row>> groups = new LinkedHashMap<String, List<Hud.Row>>();
            for (Hud.Row row : rows) {
                String team = row.team == null ? "" : row.team;
                List<Hud.Row> group = groups.get(team);
                if (group == null) {
                    group = new ArrayList<Hud.Row>();
                    groups.put(team, group);
                }
                group.add(row);
            }
            List<String> teams = new ArrayList<String>(groups.keySet());
            Collections.sort(teams, new Comparator<String>() {
                @Override
                public int compare(String a, String b) {
                    if (a.isEmpty() != b.isEmpty()) {
                        return a.isEmpty() ? 1 : -1;
                    }
                    return a.compareTo(b);
                }
            });
            for (String team : teams) {
                entries.add(new Separator(team, groups.get(team).size()));
                entries.addAll(groups.get(team));
            }
        } else {
            entries.addAll(rows);
        }
        model.entries = entries;
        model.fireTableDataChanged();
        int threats = 0;
        StringBuilder text = new StringBuilder();
        StringBuilder plain = new StringBuilder();
        String muted = Theme.hex(theme.muted);
        for (Hud.Row row : lobby) {
            if (row.threat == null || row.threat.isEmpty()) {
                continue;
            }
            if (threats++ == 0) {
                text.append("<html><b><font color='").append(Theme.hex(theme.danger)).append("'>Dodge?</font></b>&nbsp;&nbsp;");
            } else {
                text.append("<font color='").append(muted).append("'>&nbsp;\u00b7&nbsp;</font>");
                plain.append(", ");
            }
            text.append("<b>").append(html(row.name)).append("</b>&nbsp;<font color='").append(muted).append("'>")
                    .append(html(row.threat)).append("</font>");
            plain.append(row.name).append(" (").append(row.threat).append(')');
        }
        banner.setText(threats > 0 ? text.append("</html>").toString() : "");
        banner.setToolTipText(threats > 0 ? "Dodge? " + plain : null);
        bannerBox.setVisible(threats > 0 && !compact);
        int flagged = 0;
        for (Hud.Row row : lobby) {
            if (!row.tags.isEmpty()) {
                flagged++;
            }
        }
        lobbyTab.setText(tabText("Lobby", flagged));
        savedTab.setText(tabText("Saved", saved.size()));
        if (prefs.mini && !compact && !settingsOpen) {
            fitMini();
        }
    }

    private static String html(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static boolean hasTeams(List<Hud.Row> rows) {
        for (Hud.Row row : rows) {
            if (row.team != null && !row.team.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Row height, footer and window height for the compact / mini / settings states. */
    private void applyModes() {
        if (table == null) {
            return;
        }
        boolean mini = prefs.mini && !settingsOpen;
        table.setRowHeight(theme.rowHeight(prefs.density, mini));
        if (miniIcon != null) {
            miniIcon.on = prefs.mini;
            miniIcon.repaint();
        }
        center.setVisible(!compact);
        footer.setVisible(!compact && !mini);
        bannerBox.setVisible(!compact && banner.getText() != null && !banner.getText().isEmpty());
        if (compact) {
            frame.setSize(frame.getWidth(), chrome.getPreferredSize().height + 2);
        } else if (mini) {
            refill();
            fitMini();
        } else {
            frame.setSize(frame.getWidth(), Math.max(200, prefs.h));
        }
        frame.validate();
    }

    /** Shrinks or grows the window to exactly fit the visible rows, never smaller than the title bar. */
    private void fitMini() {
        int rows = model.getRowCount();
        int height = chrome.getPreferredSize().height + 2;
        if (bannerBox.isVisible()) {
            height += bannerBox.getPreferredSize().height;
        }
        if (rows > 0) {
            height += table.getTableHeader().getPreferredSize().height + rows * table.getRowHeight() + 2;
        }
        Rectangle screen = frame.getGraphicsConfiguration() == null ? null : frame.getGraphicsConfiguration().getBounds();
        if (screen != null) {
            height = Math.min(height, screen.height - 40);
        }
        if (frame.getHeight() != height) {
            frame.setSize(frame.getWidth(), height);
        }
        center.setVisible(rows > 0);
    }

    // ---- data from the game ----

    static void show(final int players, List<Hud.Row> lobby, List<Hud.Row> saved, final boolean force) {
        if (Boolean.getBoolean("safedetect.nogui")) {
            return;
        }
        final List<Hud.Row> lobbyCopy = new ArrayList<Hud.Row>(lobby);
        final List<Hud.Row> savedCopy = new ArrayList<Hud.Row>(saved);
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                Game.useGameLoader();
                try {
                    instance().apply(players, lobbyCopy, savedCopy, force);
                } catch (Throwable thrown) {
                    Log.once("overlay", thrown);
                }
            }
        });
    }

    static void reopen() {
        if (Boolean.getBoolean("safedetect.nogui")) {
            return;
        }
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                Game.useGameLoader();
                try {
                    Overlay overlay = instance();
                    overlay.userHidden = false;
                    overlay.compact = false;
                    overlay.lastKey = "";
                    overlay.applyModes();
                    overlay.ensureOnScreen();
                    overlay.frame.setVisible(true);
                    overlay.frame.toFront();
                    overlay.frame.setAlwaysOnTop(true);
                } catch (Throwable thrown) {
                    Log.once("overlay", thrown);
                }
            }
        });
    }

    static void toggleHidden() {
        if (Boolean.getBoolean("safedetect.nogui")) {
            return;
        }
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                Game.useGameLoader();
                try {
                    Overlay overlay = instance();
                    overlay.userHidden = !overlay.userHidden;
                    if (overlay.userHidden) {
                        overlay.frame.setVisible(false);
                    } else {
                        overlay.applyModes();
                        overlay.ensureOnScreen();
                        overlay.frame.setVisible(true);
                        overlay.frame.toFront();
                        overlay.frame.setAlwaysOnTop(true);
                    }
                } catch (Throwable thrown) {
                    Log.once("overlay toggle", thrown);
                }
            }
        });
    }

    private static Overlay instance() {
        if (instance == null) {
            instance = new Overlay();
        }
        return instance;
    }

    private void apply(int count, List<Hud.Row> nextLobby, List<Hud.Row> nextSaved, boolean force) {
        paintPrefs();
        brand.setText(brandText());
        brand.setToolTipText(notice().isEmpty() ? null : notice());
        if (settings != null && settingsOpen) {
            settings.refresh();
        }
        StringBuilder key = new StringBuilder().append(count).append('|').append(sessionText)
                .append('|').append(notice());
        for (List<Hud.Row> list : java.util.Arrays.asList(nextLobby, nextSaved)) {
            key.append('#');
            for (Hud.Row row : list) {
                key.append('|').append(row.name).append(':').append(row.stars).append(':').append(row.fkdr)
                        .append(':').append(row.wlr).append(':').append(row.sniper).append(':').append(row.tags)
                        .append(':').append(row.team).append(':').append(row.threat).append(':').append(row.seen)
                        .append(':').append(row.blacklisted).append(':').append(row.friend).append(':')
                        .append(row.party).append(':').append(row.nick).append(':')
                        .append(row.winstreak).append(':').append(row.last);
            }
        }
        key.append('#').append(optionNumber("dodgeFkdr", 8.0));
        String next = key.toString();
        if (!force && next.equals(lastKey) && (frame.isVisible() || userHidden)) {
            return;
        }
        lastKey = next;
        players = count;
        lobby = nextLobby;
        saved = nextSaved;
        int selected = table.getSelectedRow();
        Hud.Row before = selected >= 0 ? model.row(selected) : null;
        refill();
        if (before != null) {
            for (int i = 0; i < model.getRowCount(); i++) {
                Hud.Row row = model.row(i);
                if (row != null && row.name.equals(before.name)) {
                    table.setRowSelectionInterval(i, i);
                    break;
                }
            }
        }
        if (userHidden && !force) {
            return;
        }
        if (force) {
            userHidden = false;
        }
        if (!frame.isVisible()) {
            ensureOnScreen();
            frame.setVisible(true);
        }
    }

    // ---- moving and resizing ----

    private JComponent resizeGrip() {
        JPanel grip = new JPanel() {
            private static final long serialVersionUID = 1L;

            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(theme.muted);
                int w = getWidth();
                int h = getHeight();
                for (int i = 0; i < 3; i++) {
                    g.fillRect(w - 4 - i * 4, h - 4, 2, 2);
                    g.fillRect(w - 4, h - 4 - i * 4, 2, 2);
                }
            }
        };
        grip.setOpaque(false);
        grip.setPreferredSize(new Dimension(16, 14));
        grip.setCursor(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR));
        grip.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                resize = event.getLocationOnScreen();
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                resize = null;
                persistBounds();
            }
        });
        grip.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent event) {
                if (resize == null) {
                    return;
                }
                Point now = event.getLocationOnScreen();
                frame.setSize(Math.max(420, frame.getWidth() + now.x - resize.x),
                        Math.max(120, frame.getHeight() + now.y - resize.y));
                resize = now;
            }
        });
        return grip;
    }

    private void enableDrag(Component component) {
        component.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                drag = event.getLocationOnScreen();
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                drag = null;
                ensureOnScreen();
                persistBounds();
            }
        });
        component.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent event) {
                if (drag == null) {
                    return;
                }
                Point now = event.getLocationOnScreen();
                Point loc = frame.getLocation();
                frame.setLocation(loc.x + now.x - drag.x, loc.y + now.y - drag.y);
                drag = now;
            }
        });
    }

    /** Height is only remembered from the full view; mini and collapsed heights are derived. */
    private void persistBounds() {
        if (!frame.isShowing() || drag != null) {
            return;
        }
        Rectangle now = frame.getBounds();
        boolean fullHeight = !compact && !(prefs.mini && !settingsOpen);
        if (now.x == prefs.x && now.y == prefs.y && now.width == prefs.w && (!fullHeight || now.height == prefs.h)) {
            return;
        }
        prefs.x = now.x;
        prefs.y = now.y;
        prefs.w = now.width;
        if (fullHeight) {
            prefs.h = now.height;
        }
        prefs.saveLater();
    }

    private void ensureOnScreen() {
        Rectangle win = frame.getBounds();
        if (win.width < 200) {
            win.width = 520;
        }
        if (win.height < 40) {
            win.height = 280;
        }
        Rectangle vis = new Rectangle();
        GraphicsDevice[] screens = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
        for (int i = 0; i < screens.length; i++) {
            vis = vis.union(screens[i].getDefaultConfiguration().getBounds());
        }
        if (vis.isEmpty()) {
            return;
        }
        int x = Math.min(Math.max(win.x, vis.x), vis.x + vis.width - Math.min(win.width, vis.width));
        int y = Math.min(Math.max(win.y, vis.y), vis.y + vis.height - Math.min(win.height, vis.height));
        if (x != win.x || y != win.y || win.width != frame.getWidth() || win.height != frame.getHeight()) {
            frame.setBounds(x, y, win.width, win.height);
        }
    }

    // ---- table model and painting ----

    /** A team heading inside the lobby list; not selectable and has no menu. */
    private static final class Separator {
        final String team;
        final int count;

        Separator(String team, int count) {
            this.team = team;
            this.count = count;
        }

        @Override
        public String toString() {
            String name = team.isEmpty() ? "No team" : Theme.teamName(team);
            return name.toUpperCase(Locale.ROOT) + "   " + count;
        }
    }

    private static final class RowsModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        List<OverlayColumns.Column> columns = new ArrayList<OverlayColumns.Column>();
        List<Object> entries = new ArrayList<Object>();

        @Override
        public int getRowCount() {
            return entries.size();
        }

        @Override
        public int getColumnCount() {
            return columns.size();
        }

        @Override
        public String getColumnName(int column) {
            return columns.get(column).header;
        }

        OverlayColumns.Column column(int index) {
            return index >= 0 && index < columns.size() ? columns.get(index) : null;
        }

        Object entry(int row) {
            return row >= 0 && row < entries.size() ? entries.get(row) : null;
        }

        Hud.Row row(int row) {
            Object entry = entry(row);
            return entry instanceof Hud.Row ? (Hud.Row) entry : null;
        }

        @Override
        public Object getValueAt(int row, int column) {
            Object entry = entry(row);
            if (entry instanceof Hud.Row) {
                return OverlayColumns.text(columns.get(column).id, (Hud.Row) entry);
            }
            return entry != null && "name".equals(columns.get(column).id) ? entry.toString() : "";
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    }

    private final class Cell extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;
        private final Chips chips = new Chips();
        private final java.awt.Font captionFont = Ui.tracked(theme.caption, 0.06f);

        @Override
        public Component getTableCellRendererComponent(JTable view, Object value, boolean selected, boolean focus,
                int row, int column) {
            super.getTableCellRendererComponent(view, value, selected, false, row, column);
            Object entry = model.entry(row);
            OverlayColumns.Column spec = model.column(view.convertColumnIndexToModel(column));
            setIcon(null);
            if (entry instanceof Separator) {
                Color team = theme.team(((Separator) entry).team);
                boolean labelHere = spec != null && "name".equals(spec.id);
                setBackground(theme.bg);
                setForeground(theme.muted);
                setFont(captionFont);
                setHorizontalAlignment(SwingConstants.LEFT);
                setVerticalAlignment(SwingConstants.BOTTOM);
                setText(labelHere ? entry.toString() : "");
                setIcon(labelHere ? Ui.dot(team != null ? team : theme.thumb, 8) : null);
                setIconTextGap(8);
                setToolTipText(null);
                setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, theme.line),
                        BorderFactory.createEmptyBorder(0, 8, 5, 8)));
                return this;
            }
            setVerticalAlignment(SwingConstants.CENTER);
            Hud.Row data = (Hud.Row) entry;
            String id = spec == null ? "" : spec.id;
            boolean threat = data != null && data.threat != null && !data.threat.isEmpty();
            Color background = selected ? theme.select : row == hoverRow ? theme.hover : threat ? theme.threatRow : theme.bg;
            setBackground(background);
            setFont("name".equals(id) ? theme.bold : "last".equals(id) || "seen".equals(id) ? theme.small : theme.body);
            setForeground(data == null ? theme.fg : OverlayColumns.color(id, data, theme));
            setHorizontalAlignment(spec != null && spec.right ? SwingConstants.RIGHT : SwingConstants.LEFT);
            String tip = null;
            if (data != null && "flags".equals(id) && data.evidence != null && !data.evidence.isEmpty()) {
                tip = data.evidence;
            } else if (data != null && "name".equals(id) && threat) {
                tip = "Dodge? " + data.threat;
            }
            setToolTipText(tip);
            if (data != null && "name".equals(id)) {
                Color team = theme.team(data.team);
                setIcon(Ui.dot(team != null ? team : Ui.alpha(theme.muted, 0), 8));
                setIconTextGap(8);
            }
            int left = column == 0 ? 14 : 8;
            javax.swing.border.Border edge = BorderFactory.createMatteBorder(0, column == 0 ? 3 : 0, 1, 0,
                    theme.rowLine);
            if (column == 0) {
                edge = BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, theme.rowLine),
                        BorderFactory.createMatteBorder(0, 3, 0, 0, threat ? theme.danger : background));
                left -= 3;
            }
            setBorder(BorderFactory.createCompoundBorder(edge, BorderFactory.createEmptyBorder(0, left, 0, 8)));
            if (data != null && "flags".equals(id) && data.tags != null && !data.tags.isEmpty()) {
                chips.set(data.tags, background, getBorder(), tip);
                return chips;
            }
            return this;
        }
    }

    /** Flags drawn as small coloured chips: red for cheats and blacklist, amber for nick/sniper/high stats. */
    private final class Chips extends JComponent {
        private static final long serialVersionUID = 1L;
        private final List<String> tags = new ArrayList<String>();
        private Color background;

        Chips() {
            setOpaque(true);
        }

        void set(String text, Color bg, javax.swing.border.Border border, String tip) {
            tags.clear();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\[([^\\]]+)\\]|([^\\[\\]]+)").matcher(text);
            while (m.find()) {
                String tag = (m.group(1) != null ? m.group(1) : m.group(2)).trim();
                if (!tag.isEmpty()) {
                    tags.add(tag);
                }
            }
            background = bg;
            setBorder(border);
            String legend = "SD = SafeDetect   U = Urchin";
            setToolTipText(tip == null || tip.isEmpty() ? legend : tip + "  \u00b7  " + legend);
        }

        private Color tone(String tag) {
            if (tag.startsWith("U:")) {
                return theme.accent;
            }
            String inner = tag.startsWith("SD:") ? tag.substring(3) : tag;
            if (inner.startsWith("NK") || inner.startsWith("SN") || inner.startsWith("FK")) {
                return theme.warn;
            }
            return theme.danger;
        }

        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(background);
            g.fillRect(0, 0, getWidth(), getHeight());
            Graphics2D gfx = Ui.smooth(g);
            java.awt.Insets in = getInsets();
            gfx.setFont(theme.caption);
            java.awt.FontMetrics fm = gfx.getFontMetrics();
            int h = fm.getAscent() + 6;
            int y = (getHeight() - h) / 2;
            int x = in.left;
            for (String tag : tags) {
                int w = fm.stringWidth(tag) + 12;
                if (x + w > getWidth() - in.right && x > in.left) {
                    gfx.setColor(theme.muted);
                    gfx.drawString("\u2026", x, y + h - 4);
                    break;
                }
                Color tone = theme.readable(tone(tag));
                gfx.setColor(Theme.mix(background, tone, theme.light ? 0.14 : 0.2));
                gfx.fill(new java.awt.geom.RoundRectangle2D.Float(x, y, w, h, 8, 8));
                gfx.setColor(tone);
                gfx.drawString(tag, x + 6, y + h - 4);
                x += w + 4;
            }
            gfx.dispose();
        }
    }

    /** Header button painted with Java2D so it looks the same whatever fonts are installed. */
    private final class Glyph extends JComponent {
        private static final long serialVersionUID = 1L;
        static final int REFRESH = 0;
        static final int MINI = 1;
        static final int GEAR = 2;
        static final int MINIMIZE = 3;
        static final int CLOSE = 4;
        final int kind;
        boolean on;
        boolean hover;

        Glyph(int kind, String tip, final Runnable action) {
            this.kind = kind;
            setToolTipText(tip);
            setPreferredSize(new Dimension(30, 28));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    action.run();
                }

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
            boolean danger = kind == CLOSE && hover;
            if (on || hover) {
                gfx.setColor(danger ? Theme.mix(theme.header, theme.danger, 0.2) : on ? theme.accentSoft : theme.hover);
                gfx.fill(new java.awt.geom.RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, 12, 12));
            }
            gfx.setColor(danger ? theme.danger : on ? theme.accent : hover ? theme.fg : theme.muted);
            gfx.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int cx = getWidth() / 2;
            int cy = getHeight() / 2;
            int r = 6;
            if (kind == REFRESH) {
                gfx.draw(new Arc2D.Double(cx - r, cy - r, r * 2, r * 2, 70, 270, Arc2D.OPEN));
                gfx.fillPolygon(new int[] { cx + 1, cx + 6, cx + 2 }, new int[] { cy - 9, cy - 6, cy - 3 }, 3);
            } else if (kind == MINI) {
                gfx.draw(new java.awt.geom.RoundRectangle2D.Float(cx - r - 0.5f, cy - r + 0.5f, r * 2 + 1, r * 2 - 1, 5, 5));
                gfx.drawLine(cx - 3, cy - 2, cx + 3, cy - 2);
                gfx.drawLine(cx - 3, cy + 2, cx + 1, cy + 2);
            } else if (kind == GEAR) {
                for (int i = 0; i < 6; i++) {
                    double a = Math.PI * i / 3;
                    gfx.drawLine(cx + (int) Math.round(Math.cos(a) * 5), cy + (int) Math.round(Math.sin(a) * 5),
                            cx + (int) Math.round(Math.cos(a) * 7), cy + (int) Math.round(Math.sin(a) * 7));
                }
                gfx.drawOval(cx - 5, cy - 5, 10, 10);
                gfx.drawOval(cx - 2, cy - 2, 4, 4);
            } else if (kind == MINIMIZE) {
                gfx.drawLine(cx - 5, cy + 1, cx + 5, cy + 1);
            } else {
                gfx.drawLine(cx - 4, cy - 4, cx + 4, cy + 4);
                gfx.drawLine(cx - 4, cy + 4, cx + 4, cy - 4);
            }
            gfx.dispose();
        }
    }

    /** Rounded window corners, where the platform allows shaped windows. */
    private void roundCorners() {
        try {
            GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
            if (device.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSPARENT)) {
                frame.setShape(new java.awt.geom.RoundRectangle2D.Double(0, 0, frame.getWidth(), frame.getHeight(),
                        CORNER * 2, CORNER * 2));
            }
        } catch (Throwable thrown) {
            Log.once("overlay shape", thrown);
        }
    }

    /** Accent tile with a stylised eye. */
    private final class LogoIcon implements javax.swing.Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D gfx = Ui.smooth(g);
            int s = getIconWidth();
            gfx.setPaint(new java.awt.GradientPaint(x, y, Theme.mix(theme.accent, Color.WHITE, 0.15), x + s, y + s,
                    Theme.mix(theme.accent, Color.BLACK, 0.2)));
            gfx.fill(new java.awt.geom.RoundRectangle2D.Float(x, y, s, s, 10, s * 0.55f));
            gfx.setColor(theme.onAccent);
            gfx.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            float cx = x + s / 2f;
            float cy = y + s / 2f;
            java.awt.geom.Path2D.Float eye = new java.awt.geom.Path2D.Float();
            eye.moveTo(cx - 6, cy);
            eye.quadTo(cx, cy - 6, cx + 6, cy);
            eye.quadTo(cx, cy + 6, cx - 6, cy);
            gfx.draw(eye);
            gfx.fill(new java.awt.geom.Ellipse2D.Float(cx - 2, cy - 2, 4, 4));
            gfx.dispose();
        }

        @Override
        public int getIconWidth() {
            return 20;
        }

        @Override
        public int getIconHeight() {
            return 20;
        }
    }

    private final class SearchIcon implements javax.swing.Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D gfx = Ui.smooth(g);
            gfx.setColor(theme.muted);
            gfx.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            gfx.drawOval(x + 1, y + 1, 8, 8);
            gfx.drawLine(x + 8, y + 8, x + 11, y + 11);
            gfx.dispose();
        }

        @Override
        public int getIconWidth() {
            return 12;
        }

        @Override
        public int getIconHeight() {
            return 12;
        }
    }

    private final class WarnIcon implements javax.swing.Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D gfx = Ui.smooth(g);
            java.awt.geom.Path2D.Float triangle = new java.awt.geom.Path2D.Float();
            triangle.moveTo(x + 8, y + 1);
            triangle.lineTo(x + 15, y + 14);
            triangle.lineTo(x + 1, y + 14);
            triangle.closePath();
            gfx.setColor(theme.danger);
            gfx.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            gfx.draw(triangle);
            gfx.drawLine(x + 8, y + 6, x + 8, y + 9);
            gfx.fillOval(x + 7, y + 11, 2, 2);
            gfx.dispose();
        }

        @Override
        public int getIconWidth() {
            return 16;
        }

        @Override
        public int getIconHeight() {
            return 15;
        }
    }

    private final class OpacityIcon implements javax.swing.Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D gfx = Ui.smooth(g);
            gfx.setColor(theme.muted);
            gfx.setStroke(new BasicStroke(1.4f));
            gfx.drawOval(x + 1, y + 1, 11, 11);
            gfx.fill(new Arc2D.Float(x + 1, y + 1, 11, 11, 90, 180, Arc2D.PIE));
            gfx.dispose();
        }

        @Override
        public int getIconWidth() {
            return 14;
        }

        @Override
        public int getIconHeight() {
            return 14;
        }
    }

    static final class ThinBar extends BasicScrollBarUI {
        private final Theme theme;

        ThinBar(Theme theme) {
            this.theme = theme;
        }

        @Override
        protected void configureScrollBarColors() {
            thumbColor = theme == null ? Color.GRAY : theme.thumb;
            trackColor = theme == null ? Color.BLACK : theme.bg;
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return empty();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return empty();
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
            g.setColor(theme.bg);
            g.fillRect(r.x, r.y, r.width, r.height);
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            if (r.isEmpty()) {
                return;
            }
            Graphics2D gfx = Ui.smooth(g);
            gfx.setColor(isThumbRollover() ? Theme.mix(theme.thumb, theme.fg, 0.2) : theme.thumb);
            int w = Math.max(4, r.width - 5);
            gfx.fill(new java.awt.geom.RoundRectangle2D.Float(r.x + 2, r.y + 2, w, Math.max(10, r.height - 4), w, w));
            gfx.dispose();
        }

        private static JButton empty() {
            JButton button = new JButton();
            button.setPreferredSize(new Dimension(0, 0));
            button.setBorder(null);
            return button;
        }
    }
}
