package com.safedetect.agent;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cubelify-style lobby overlay: dense stats, search, always-on-top for borderless Lunar.
 */
public final class Overlay {
    static final class Row {
        final String name;
        final String tags;
        final int times;
        final String last;
        final long lastAt;
        final int stars;
        final double fkdr;
        final double wlr;
        final int winstreak;
        final int finals;
        final int wins;
        final int sniper;

        Row(String name, String tags, int times, String last, long lastAt) {
            this(name, tags, times, last, lastAt, -1, -1.0, -1.0, -1, -1, -1, -1);
        }

        Row(String name, String tags, int times, String last, long lastAt, int stars, double fkdr, double wlr,
                int winstreak, int finals, int wins, int sniper) {
            this.name = name;
            this.tags = tags == null ? "" : tags;
            this.times = times;
            this.last = last == null ? "" : last;
            this.lastAt = lastAt;
            this.stars = stars;
            this.fkdr = fkdr;
            this.wlr = wlr;
            this.winstreak = winstreak;
            this.finals = finals;
            this.wins = wins;
            this.sniper = sniper;
        }
    }

    private static final Color BG = new Color(16, 16, 18);
    private static final Color HEADER_BG = new Color(20, 20, 22);
    private static final Color LINE = new Color(38, 38, 42);
    private static final Color FG = new Color(235, 235, 238);
    private static final Color MUTED = new Color(118, 118, 126);
    private static final Color ACCENT = new Color(232, 140, 64);
    private static final Color SELECT = new Color(40, 42, 48);
    private static final Font TITLE = Os.font(Font.BOLD, 13);
    private static final Font BODY = Os.font(Font.PLAIN, 13);
    private static final Font SMALL = Os.font(Font.PLAIN, 11);
    private static final Font NAME = Os.font(Font.BOLD, 13);
    private static final AtomicBoolean REFRESH = new AtomicBoolean();
    private static final java.util.concurrent.ConcurrentLinkedQueue<String> REPORTS =
            new java.util.concurrent.ConcurrentLinkedQueue<String>();
    private static String sessionText = "";
    private static Overlay instance;
    private static Settings settings;

    private final JFrame frame;
    private JLabel brand;
    private JTextField search;
    private JLabel lobbyTab;
    private JLabel savedTab;
    private final DefaultTableModel lobbyModel;
    private final DefaultTableModel savedModel;
    private final JTable table;
    private TableRowSorter<DefaultTableModel> sorter;
    private final JScrollPane scroll;
    private JPanel footer;
    private JLabel tabPref;
    private JLabel chatPref;
    private JLabel soundPref;
    private boolean showingLobby = true;
    private boolean compact;
    private boolean userHidden;
    private String lastKey = "";
    private Point drag;
    private Point resize;

    static void bind(Settings next) {
        settings = next;
    }

    static boolean takeRefresh() {
        return REFRESH.getAndSet(false);
    }

    static String takeReport() {
        return REPORTS.poll();
    }

    static void session(int games, int wins) {
        sessionText = games <= 0 ? "" : games + "g \u00b7 " + wins + "w";
    }

    public static void main(String[] args) {
        System.setProperty("safedetect.overlay", "true");
        File dir = new File(args != null && args.length > 0 ? args[0] : ".");
        Hud.open(dir);
        bind(new Settings(dir));
        while (true) {
            try {
                Hud.Snapshot snap = Hud.readState();
                if (snap != null) {
                    sessionText = snap.session == null ? "" : snap.session;
                    if (snap.reopen) {
                        reopen();
                    }
                    show(snap.players, copyRows(snap.lobby), copyRows(snap.saved),
                            snap.reopen || snap.refresh);
                }
                Thread.sleep(150L);
            } catch (InterruptedException ignored) {
                return;
            } catch (Throwable thrown) {
                Log.once("overlay process", thrown);
            }
        }
    }

    private static List<Row> copyRows(List<Hud.Row> rows) {
        List<Row> out = new ArrayList<Row>();
        if (rows == null) {
            return out;
        }
        for (int i = 0; i < rows.size(); i++) {
            Hud.Row row = rows.get(i);
            out.add(new Row(row.name, row.tags, row.times, row.last, row.lastAt, row.stars, row.fkdr, row.wlr,
                    row.winstreak, row.finals, row.wins, row.sniper));
        }
        return out;
    }

    private Overlay() {
        frame = new JFrame("SafeDetect");
        frame.setUndecorated(true);
        frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        frame.setAlwaysOnTop(true);
        frame.setAutoRequestFocus(false);
        frame.setFocusableWindowState(true);
        frame.setBackground(BG);
        frame.setType(JFrame.Type.NORMAL);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG);
        root.setBorder(BorderFactory.createLineBorder(LINE, 1));
        frame.setContentPane(root);
        root.add(chrome(), BorderLayout.NORTH);

        lobbyModel = model();
        savedModel = model();
        table = new JTable(lobbyModel);
        table.setBackground(BG);
        table.setForeground(FG);
        table.setGridColor(BG);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setRowHeight(26);
        table.setFont(BODY);
        table.setSelectionBackground(SELECT);
        table.setSelectionForeground(FG);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(false);
        JTableHeader header = table.getTableHeader();
        header.setBackground(HEADER_BG);
        header.setForeground(MUTED);
        header.setFont(SMALL);
        header.setReorderingAllowed(false);
        header.setResizingAllowed(true);
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, LINE));
        ((DefaultTableCellRenderer) header.getDefaultRenderer()).setHorizontalAlignment(SwingConstants.LEFT);
        widths();
        table.setDefaultRenderer(Object.class, new Cell());
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                int view = table.rowAtPoint(event.getPoint());
                if (view < 0) {
                    return;
                }
                int row = table.convertRowIndexToModel(view);
                Object name = table.getModel().getValueAt(row, 1);
                if (name != null && !name.toString().isEmpty()) {
                    String ign = name.toString();
                    Toolkit.getDefaultToolkit().getSystemClipboard()
                            .setContents(new StringSelection(wdr(ign)), null);
                    Hud.writeCommand(false, ign);
                }
            }
        });
        sorter = new TableRowSorter<DefaultTableModel>(lobbyModel);
        lockSort(sorter);
        table.setRowSorter(sorter);

        scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(BG);
        scroll.getVerticalScrollBar().setUI(new ThinBar());
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        root.add(scroll, BorderLayout.CENTER);
        root.add(footer(), BorderLayout.SOUTH);

        if (settings != null && settings.hasOverlay()) {
            frame.setSize(Math.max(520, settings.overlayW), Math.max(280, settings.overlayH));
            frame.setLocation(settings.overlayX, settings.overlayY);
        } else {
            frame.setSize(760, 500);
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            frame.setLocation(Math.max(0, screen.width - 780), 36);
        }
        frame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                applyShape();
            }
        });
        Timer topmost = new Timer(2500, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                if (drag != null || resize != null || !frame.isVisible()) {
                    return;
                }
                if (settings == null || settings.borderless) {
                    if (!frame.isAlwaysOnTop()) {
                        frame.setAlwaysOnTop(true);
                    }
                }
            }
        });
        topmost.setRepeats(true);
        topmost.start();
        applyShape();
        ensureOnScreen();
        frame.setVisible(true);
    }

    private JPanel chrome() {
        JPanel bar = new JPanel(new BorderLayout(10, 0));
        bar.setBackground(HEADER_BG);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, LINE),
                BorderFactory.createEmptyBorder(8, 12, 8, 8)));
        brand = new JLabel("SafeDetect");
        brand.setFont(TITLE);
        brand.setForeground(ACCENT);
        JPanel left = new JPanel(new BorderLayout(12, 0));
        left.setOpaque(false);
        left.add(brand, BorderLayout.WEST);
        JPanel tabs = new JPanel(new GridLayout(1, 2, 8, 0));
        tabs.setOpaque(false);
        lobbyTab = tab("Lobby");
        savedTab = tab("Saved");
        lobbyTab.addMouseListener(clickTab(true));
        savedTab.addMouseListener(clickTab(false));
        tabs.add(lobbyTab);
        tabs.add(savedTab);
        left.add(tabs, BorderLayout.CENTER);
        bar.add(left, BorderLayout.WEST);

        search = new JTextField();
        search.setFont(SMALL);
        search.setForeground(MUTED);
        search.setCaretColor(FG);
        search.setBackground(new Color(28, 28, 32));
        search.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(LINE),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)));
        search.setText("Search player(s)");
        search.setPreferredSize(new Dimension(180, 28));
        search.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent event) {
                if ("Search player(s)".equals(search.getText())) {
                    search.setText("");
                    search.setForeground(FG);
                }
            }

            @Override
            public void focusLost(FocusEvent event) {
                if (search.getText().trim().isEmpty()) {
                    search.setText("Search player(s)");
                    search.setForeground(MUTED);
                    filter("");
                }
            }
        });
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
                if ("Search player(s)".equals(text)) {
                    return;
                }
                filter(text);
            }
        });
        bar.add(search, BorderLayout.CENTER);

        JPanel actions = new JPanel(new GridLayout(1, 3, 2, 0));
        actions.setOpaque(false);
        actions.add(icon("\u21bb", "Refresh", new Runnable() {
            @Override
            public void run() {
                lastKey = "";
                Hud.writeCommand(true, null);
            }
        }));
        actions.add(icon("\u2013", "Minimize", new Runnable() {
            @Override
            public void run() {
                compact = !compact;
                scroll.setVisible(!compact);
                footer.setVisible(!compact);
                frame.setSize(frame.getWidth(), compact ? 86 : Math.max(280, settings != null ? settings.overlayH : 500));
            }
        }));
        actions.add(icon("\u00d7", "Close", new Runnable() {
            @Override
            public void run() {
                userHidden = true;
                frame.setVisible(false);
            }
        }));
        bar.add(actions, BorderLayout.EAST);
        enableDrag(left);
        enableDrag(brand);
        paintTabs();
        return bar;
    }

    private JPanel footer() {
        footer = new JPanel(new BorderLayout());
        footer.setBackground(HEADER_BG);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, LINE),
                BorderFactory.createEmptyBorder(4, 8, 4, 6)));
        JPanel options = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        options.setOpaque(false);
        tabPref = pref("tab", "Tab");
        chatPref = pref("chat", "Chat");
        soundPref = pref("sound", "Sound");
        options.add(tabPref);
        options.add(chatPref);
        options.add(soundPref);
        options.add(action("Clear", "Delete every saved tracker name", new Runnable() {
            @Override
            public void run() {
                Hud.writeClear();
            }
        }));
        paintPrefs();
        footer.add(options, BorderLayout.CENTER);
        footer.add(resizeGrip(), BorderLayout.EAST);
        return footer;
    }

    private JLabel pref(final String key, String label) {
        final JLabel chip = new JLabel(label);
        chip.setFont(SMALL);
        chip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        chip.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (settings == null) {
                    return;
                }
                boolean on;
                if ("tab".equals(key)) {
                    settings.tabMarks = !settings.tabMarks;
                    on = settings.tabMarks;
                } else if ("chat".equals(key)) {
                    settings.alertsChat = !settings.alertsChat;
                    on = settings.alertsChat;
                } else {
                    settings.alertSound = !settings.alertSound;
                    on = settings.alertSound;
                }
                Hud.writePref(key, on);
                paintPrefs();
            }
        });
        return chip;
    }

    private JLabel action(String label, String tip, final Runnable run) {
        final JLabel chip = new JLabel(label);
        chip.setFont(SMALL);
        chip.setForeground(ACCENT);
        chip.setToolTipText(tip);
        chip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        chip.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                run.run();
            }

            @Override
            public void mouseEntered(MouseEvent event) {
                chip.setForeground(FG);
            }

            @Override
            public void mouseExited(MouseEvent event) {
                chip.setForeground(ACCENT);
            }
        });
        return chip;
    }

    private void paintPrefs() {
        boolean tab = settings == null || settings.tabMarks;
        boolean chat = settings == null || settings.alertsChat;
        boolean sound = settings == null || settings.alertSound;
        if (tabPref != null) {
            tabPref.setText(tab ? "Tab on" : "Tab off");
            tabPref.setForeground(tab ? ACCENT : MUTED);
            tabPref.setToolTipText(tab ? "Hide flag marks on tab" : "Show flag marks on tab");
        }
        if (chatPref != null) {
            chatPref.setText(chat ? "Chat on" : "Chat off");
            chatPref.setForeground(chat ? ACCENT : MUTED);
            chatPref.setToolTipText(chat ? "Mute chat alerts" : "Show chat alerts");
        }
        if (soundPref != null) {
            soundPref.setText(sound ? "Sound on" : "Sound off");
            soundPref.setForeground(sound ? ACCENT : MUTED);
            soundPref.setToolTipText(sound ? "Mute alert sound" : "Play alert sound");
        }
    }

    private JLabel tab(String text) {
        JLabel label = new JLabel(text);
        label.setFont(SMALL);
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return label;
    }

    private MouseAdapter clickTab(final boolean lobby) {
        return new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                showingLobby = lobby;
                table.setModel(lobby ? lobbyModel : savedModel);
                sorter = new TableRowSorter<DefaultTableModel>((DefaultTableModel) table.getModel());
                lockSort(sorter);
                table.setRowSorter(sorter);
                widths();
                paintTabs();
                filter(query());
            }
        };
    }

    private void paintTabs() {
        lobbyTab.setForeground(showingLobby ? FG : MUTED);
        savedTab.setForeground(showingLobby ? MUTED : FG);
    }

    private JLabel icon(String glyph, String tip, final Runnable action) {
        final JLabel label = new JLabel(glyph, SwingConstants.CENTER);
        label.setFont(TITLE);
        label.setForeground(MUTED);
        label.setToolTipText(tip);
        label.setPreferredSize(new Dimension(28, 24));
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                action.run();
            }

            @Override
            public void mouseEntered(MouseEvent event) {
                label.setForeground(FG);
            }

            @Override
            public void mouseExited(MouseEvent event) {
                label.setForeground(MUTED);
            }
        });
        return label;
    }

    private String query() {
        String text = search.getText();
        if (text == null || "Search player(s)".equals(text)) {
            return "";
        }
        return text.trim();
    }

    private void filter(String query) {
        if (query == null || query.isEmpty()) {
            sorter.setRowFilter(null);
            return;
        }
        final String needle = query.toLowerCase();
        sorter.setRowFilter(new RowFilter<DefaultTableModel, Integer>() {
            @Override
            public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                Object name = entry.getValue(1);
                return name != null && name.toString().toLowerCase().contains(needle);
            }
        });
    }

    private JComponent resizeGrip() {
        JPanel grip = new JPanel() {
            private static final long serialVersionUID = 1L;

            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(MUTED);
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
                frame.setSize(Math.max(520, frame.getWidth() + now.x - resize.x),
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

    static void show(int players, List<Row> lobby, List<Row> saved) {
        show(players, lobby, saved, false);
    }

    static void show(int players, List<Row> lobby, List<Row> saved, final boolean force) {
        if (Boolean.getBoolean("safedetect.nogui")) {
            return;
        }
        final List<Row> lobbyCopy = sorted(lobby);
        final List<Row> savedCopy = sorted(saved);
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
                    overlay.scroll.setVisible(true);
                    overlay.footer.setVisible(true);
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

    static String lastLabel(FlagStore.Record record) {
        if (record == null || record.lastCheck == null || record.lastCheck.isEmpty()) {
            return "";
        }
        long ago = System.currentTimeMillis() - record.lastCheckAt;
        if (ago < 0L) {
            ago = 0L;
        }
        long seconds = ago / 1000L;
        String when = seconds < 2L ? "now" : seconds < 60L ? seconds + "s" : (seconds / 60L) + "m";
        return record.lastCheck + " · " + when;
    }

    static String wdr(String name) {
        return "/wdr " + name + " cheating";
    }

    private static Overlay instance() {
        if (instance == null) {
            instance = new Overlay();
        }
        return instance;
    }

    private static List<Row> sorted(List<Row> rows) {
        List<Row> copy = new ArrayList<Row>(rows);
        Collections.sort(copy, new Comparator<Row>() {
            @Override
            public int compare(Row a, Row b) {
                boolean aTag = a.tags != null && !a.tags.isEmpty();
                boolean bTag = b.tags != null && !b.tags.isEmpty();
                if (aTag != bTag) {
                    return aTag ? -1 : 1;
                }
                double ia = index(a);
                double ib = index(b);
                if (Double.compare(ia, ib) != 0) {
                    return Double.compare(ib, ia);
                }
                if (a.sniper != b.sniper) {
                    return b.sniper - a.sniper;
                }
                return a.name.compareToIgnoreCase(b.name);
            }
        });
        return copy;
    }

    static double index(Row row) {
        double fkdr = row.fkdr < 0 ? 0.0 : row.fkdr;
        int stars = row.stars < 1 ? 1 : row.stars;
        return fkdr * fkdr * stars;
    }

    private static void lockSort(TableRowSorter<DefaultTableModel> next) {
        if (next == null) {
            return;
        }
        for (int i = 0; i < 9; i++) {
            next.setSortable(i, false);
        }
    }

    private static DefaultTableModel model() {
        return new DefaultTableModel(
                new Object[] { "Lvl", "Name", "Flags", "WS", "FKDR", "WLR", "Finals", "Wins", "Sniper" }, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private void widths() {
        int[] w = { 48, 140, 90, 40, 52, 48, 64, 56, 56 };
        for (int i = 0; i < w.length && i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(w[i]);
        }
    }

    private void persistBounds() {
        if (settings == null || !frame.isShowing() || compact || drag != null) {
            return;
        }
        settings.setOverlay(frame.getX(), frame.getY(), frame.getWidth(), frame.getHeight());
    }

    private void applyShape() {
        frame.setShape(null);
    }

    private void ensureOnScreen() {
        Rectangle win = frame.getBounds();
        if (win.width < 200) {
            win.width = 520;
        }
        if (win.height < 80) {
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

    private void apply(int players, List<Row> lobby, List<Row> saved, boolean force) {
        StringBuilder key = new StringBuilder().append(players).append('|').append(lobby.size()).append('|')
                .append(saved.size());
        for (Row row : lobby) {
            key.append('|').append(row.name).append(':').append(row.stars).append(':').append(row.fkdr)
                    .append(':').append(row.sniper).append(':').append(row.tags);
        }
        String next = key.toString();
        if (!force && next.equals(lastKey) && (frame.isVisible() || userHidden)) {
            return;
        }
        lastKey = next;
        int flagged = 0;
        for (Row row : lobby) {
            if (!row.tags.isEmpty()) {
                flagged++;
            }
        }
        brand.setText(sessionText.isEmpty() ? "SafeDetect" : "SafeDetect  \u00b7  " + sessionText);
        lobbyTab.setText(flagged > 0 ? "Lobby · " + flagged : "Lobby");
        savedTab.setText("Saved");
        fill(lobbyModel, lobby);
        fill(savedModel, saved);
        paintTabs();
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

    private static void fill(DefaultTableModel model, List<Row> rows) {
        model.setRowCount(0);
        for (Row row : rows) {
            model.addRow(new Object[] {
                    star(row.stars),
                    row.name,
                    row.tags,
                    dash(row.winstreak),
                    dec(row.fkdr),
                    dec(row.wlr),
                    count(row.finals),
                    count(row.wins),
                    sniper(row.sniper)
            });
        }
    }

    private static String star(int stars) {
        return stars < 0 ? "" : Integer.toString(stars);
    }

    private static String dash(int value) {
        return value < 0 ? "?" : Integer.toString(value);
    }

    private static String count(int value) {
        if (value < 0) {
            return "";
        }
        if (value >= 1000) {
            return String.format("%,d", Integer.valueOf(value));
        }
        return Integer.toString(value);
    }

    private static String dec(double value) {
        return value < 0 ? "" : String.format("%.2f", value);
    }

    private static String sniper(int value) {
        return value < 0 ? "" : Integer.toString(value);
    }

    private static Color prestige(int stars) {
        if (stars < 0) {
            return FG;
        }
        if (stars < 100) {
            return MUTED;
        }
        if (stars < 200) {
            return FG;
        }
        if (stars < 300) {
            return new Color(255, 170, 0);
        }
        if (stars < 400) {
            return new Color(85, 255, 255);
        }
        if (stars < 500) {
            return new Color(0, 170, 0);
        }
        if (stars < 600) {
            return new Color(0, 170, 170);
        }
        if (stars < 700) {
            return new Color(170, 0, 0);
        }
        if (stars < 800) {
            return new Color(170, 0, 170);
        }
        if (stars < 900) {
            return new Color(85, 85, 255);
        }
        if (stars < 1000) {
            return new Color(255, 85, 255);
        }
        return new Color(255, 85, 85);
    }

    private static Color heat(double value, double low, double mid, double high) {
        if (value < 0) {
            return MUTED;
        }
        if (value >= high) {
            return new Color(255, 90, 140);
        }
        if (value >= mid) {
            return new Color(255, 120, 70);
        }
        if (value >= low) {
            return new Color(255, 200, 70);
        }
        return new Color(180, 220, 120);
    }

    private static int parseStars(Object value) {
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return -1;
        }
    }

    private static double parseDec(Object value) {
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ignored) {
            return -1;
        }
    }

    private final class Cell extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getTableCellRendererComponent(JTable view, Object value, boolean selected, boolean focus,
                int row, int column) {
            super.getTableCellRendererComponent(view, value, selected, focus, row, column);
            setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
            setBackground(selected ? SELECT : BG);
            setFont(column == 1 ? NAME : BODY);
            int model = view.convertRowIndexToModel(row);
            Object star = view.getModel().getValueAt(model, 0);
            int stars = parseStars(star);
            if (column == 0) {
                setForeground(prestige(stars));
                setHorizontalAlignment(SwingConstants.RIGHT);
            } else if (column == 1) {
                setForeground(prestige(stars));
                setHorizontalAlignment(SwingConstants.LEFT);
            } else if (column == 2) {
                setForeground(value == null || value.toString().isEmpty() ? MUTED : ACCENT);
                setFont(SMALL);
                setHorizontalAlignment(SwingConstants.LEFT);
            } else if (column == 4) {
                setForeground(heat(parseDec(value), 2.0, 6.0, 12.0));
                setHorizontalAlignment(SwingConstants.RIGHT);
            } else if (column == 5) {
                setForeground(heat(parseDec(value), 1.5, 4.0, 8.0));
                setHorizontalAlignment(SwingConstants.RIGHT);
            } else if (column == 8) {
                setForeground(heat(parseDec(value), 15, 40, 70));
                setHorizontalAlignment(SwingConstants.RIGHT);
            } else {
                setForeground("?".equals(String.valueOf(value)) ? MUTED : FG);
                setHorizontalAlignment(SwingConstants.RIGHT);
            }
            return this;
        }
    }

    private static final class ThinBar extends BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            thumbColor = new Color(56, 56, 62);
            trackColor = BG;
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
        protected void paintTrack(Graphics g, JComponent c, java.awt.Rectangle r) {
            g.setColor(BG);
            g.fillRect(r.x, r.y, r.width, r.height);
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, java.awt.Rectangle r) {
            Graphics2D gfx = (Graphics2D) g.create();
            gfx.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            gfx.setColor(thumbColor);
            gfx.fillRoundRect(r.x + 2, r.y + 2, Math.max(4, r.width - 4), Math.max(8, r.height - 4), 6, 6);
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
