package com.safedetect.agent;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.BasicSliderUI;
import javax.swing.plaf.basic.BasicSpinnerUI;
import javax.swing.plaf.basic.ComboPopup;
import javax.swing.text.JTextComponent;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Collections;
import java.util.Map;

/** Flat, rounded Swing widgets drawn from a {@link Theme}, so the overlay never shows the stock look and feel. */
final class Ui {
    static final int RADIUS = 8;

    private Ui() {
    }

    static Graphics2D smooth(Graphics g) {
        Graphics2D gfx = (Graphics2D) g.create();
        gfx.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gfx.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        gfx.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return gfx;
    }

    static Color alpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    /** Font with a little letter spacing, for uppercase captions. */
    static Font tracked(Font font, float tracking) {
        Map<TextAttribute, Object> attrs = Collections.<TextAttribute, Object>singletonMap(TextAttribute.TRACKING,
                Float.valueOf(tracking));
        return font.deriveFont(attrs);
    }

    /** Menus, tooltips and combo popups pick their colours from these defaults. */
    static void install(Theme theme) {
        UIManager.put("ToolTip.background", theme.card);
        UIManager.put("ToolTip.foreground", theme.fg);
        UIManager.put("ToolTip.font", theme.small);
        UIManager.put("ToolTip.border", BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(theme.line),
                BorderFactory.createEmptyBorder(5, 8, 5, 8)));
        UIManager.put("PopupMenu.background", theme.card);
        UIManager.put("PopupMenu.border", BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(theme.line),
                BorderFactory.createEmptyBorder(4, 0, 4, 0)));
        UIManager.put("MenuItem.background", theme.card);
        UIManager.put("MenuItem.foreground", theme.fg);
        UIManager.put("MenuItem.selectionBackground", theme.accentSoft);
        UIManager.put("MenuItem.selectionForeground", theme.fg);
        UIManager.put("MenuItem.font", theme.small);
        UIManager.put("MenuItem.border", BorderFactory.createEmptyBorder(6, 6, 6, 18));
        UIManager.put("Separator.foreground", theme.line);
        UIManager.put("Separator.background", theme.card);
        UIManager.put("PopupMenuSeparator.foreground", theme.line);
        UIManager.put("PopupMenuSeparator.background", theme.card);
        UIManager.put("ComboBox.selectionBackground", theme.accentSoft);
        UIManager.put("ComboBox.selectionForeground", theme.fg);
    }

    // ---- containers ----

    /** Rounded panel; children should be non-opaque. Highlights its outline while a child has focus. */
    static class Round extends JPanel {
        private static final long serialVersionUID = 1L;
        Color fill;
        Color outline;
        Color focusLine;
        int radius = RADIUS;
        private boolean focused;

        Round(Color fill, Color outline) {
            super(new BorderLayout());
            this.fill = fill;
            this.outline = outline;
            setOpaque(false);
        }

        void watchFocus(Component child, Color focusColor) {
            focusLine = focusColor;
            child.addFocusListener(new FocusListener() {
                @Override
                public void focusGained(FocusEvent event) {
                    focused = true;
                    repaint();
                }

                @Override
                public void focusLost(FocusEvent event) {
                    focused = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D gfx = smooth(g);
            int w = getWidth();
            int h = getHeight();
            if (fill != null) {
                gfx.setColor(fill);
                gfx.fill(new RoundRectangle2D.Float(0, 0, w, h, radius * 2, radius * 2));
            }
            Color line = focused && focusLine != null ? focusLine : outline;
            if (line != null) {
                gfx.setColor(line);
                gfx.setStroke(new BasicStroke(1f));
                gfx.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, radius * 2, radius * 2));
            }
            gfx.dispose();
        }
    }

    /** Wraps an input in a rounded field background with a focus ring. */
    static Round boxed(Theme theme, JComponent input) {
        Round box = new Round(theme.field, theme.line);
        box.add(input, BorderLayout.CENTER);
        Component focusable = input;
        if (input instanceof JSpinner && ((JSpinner) input).getEditor() instanceof JSpinner.DefaultEditor) {
            focusable = ((JSpinner.DefaultEditor) ((JSpinner) input).getEditor()).getTextField();
        }
        box.watchFocus(focusable, theme.accent);
        return box;
    }

    static JPanel row(int gap) {
        FlowLayout layout = new FlowLayout(FlowLayout.LEFT, gap, 0);
        layout.setAlignOnBaseline(true);
        JPanel row = new JPanel(layout);
        row.setOpaque(false);
        return row;
    }

    // ---- text ----

    /** Text field with a placeholder drawn while it is empty. */
    static final class Field extends JTextField {
        private static final long serialVersionUID = 1L;
        private final String hint;
        private final Theme theme;

        Field(Theme theme, String hint) {
            this.theme = theme;
            this.hint = hint;
            style(this, theme);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (hint != null && getText().isEmpty()) {
                Graphics2D gfx = smooth(g);
                gfx.setFont(getFont());
                gfx.setColor(theme.muted);
                FontMetrics fm = gfx.getFontMetrics();
                java.awt.Insets in = getInsets();
                gfx.drawString(hint, in.left, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
                gfx.dispose();
            }
        }
    }

    static JPasswordField password(Theme theme) {
        JPasswordField field = new JPasswordField();
        style(field, theme);
        return field;
    }

    static void style(JTextComponent field, Theme theme) {
        field.setOpaque(false);
        field.setFont(theme.small);
        field.setForeground(theme.fg);
        field.setCaretColor(theme.fg);
        field.setSelectionColor(theme.accentSoft);
        field.setSelectedTextColor(theme.fg);
        field.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
    }

    static JLabel label(Theme theme, String text, Font font, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        label.setForeground(color);
        return label;
    }

    // ---- toggles and buttons ----

    /** A switch-style checkbox; use the label next to it rather than the checkbox text. */
    static JCheckBox toggle(Theme theme, boolean on) {
        JCheckBox box = new JCheckBox();
        box.setSelected(on);
        box.setOpaque(false);
        box.setFocusPainted(false);
        box.setBorder(BorderFactory.createEmptyBorder());
        box.setRolloverEnabled(true);
        box.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        Icon icon = new SwitchIcon(theme);
        box.setIcon(icon);
        box.setSelectedIcon(icon);
        box.setDisabledIcon(icon);
        box.setDisabledSelectedIcon(icon);
        box.setRolloverIcon(icon);
        box.setRolloverSelectedIcon(icon);
        return box;
    }

    private static final class SwitchIcon implements Icon {
        private final Theme theme;

        SwitchIcon(Theme theme) {
            this.theme = theme;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            AbstractButton button = (AbstractButton) c;
            boolean on = button.isSelected();
            boolean hover = button.getModel().isRollover();
            Graphics2D gfx = smooth(g);
            if (!button.isEnabled()) {
                gfx.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.4f));
            }
            int w = getIconWidth();
            int h = getIconHeight();
            Color track = on ? theme.accent : hover ? Theme.mix(theme.thumb, theme.fg, 0.12) : theme.thumb;
            gfx.setColor(track);
            gfx.fill(new RoundRectangle2D.Float(x, y, w, h, h, h));
            int knob = h - 6;
            int kx = on ? x + w - knob - 3 : x + 3;
            gfx.setColor(on ? theme.onAccent : Theme.mix(theme.fg, theme.thumb, 0.1));
            gfx.fillOval(kx, y + 3, knob, knob);
            gfx.dispose();
        }

        @Override
        public int getIconWidth() {
            return 34;
        }

        @Override
        public int getIconHeight() {
            return 20;
        }
    }

    static final int PRIMARY = 0;
    static final int GHOST = 1;
    static final int DANGER = 2;

    static JButton button(final Theme theme, String text, final int kind) {
        JButton button = new JButton(text) {
            private static final long serialVersionUID = 1L;

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D gfx = smooth(g);
                boolean hover = getModel().isRollover();
                boolean down = getModel().isPressed();
                Color fill;
                Color line = null;
                if (kind == PRIMARY) {
                    fill = hover ? Theme.mix(theme.accent, Color.WHITE, 0.1) : theme.accent;
                } else if (kind == DANGER) {
                    fill = hover ? Theme.mix(theme.danger, Color.WHITE, 0.1) : theme.danger;
                } else {
                    fill = hover ? theme.hover : theme.card;
                    line = theme.line;
                }
                if (down) {
                    fill = Theme.mix(fill, Color.BLACK, 0.12);
                }
                gfx.setColor(fill);
                gfx.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), RADIUS * 2, RADIUS * 2));
                if (line != null) {
                    gfx.setColor(line);
                    gfx.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, RADIUS * 2,
                            RADIUS * 2));
                }
                gfx.dispose();
                super.paintComponent(g);
            }
        };
        button.setFont(theme.bold.deriveFont(theme.small.getSize2D()));
        button.setForeground(kind == GHOST ? theme.fg : kind == DANGER ? Color.WHITE : theme.onAccent);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setOpaque(false);
        button.setRolloverEnabled(true);
        button.setBorder(BorderFactory.createEmptyBorder(7, 14, 7, 14));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    /** Label with an optional rounded background, used for tabs, chips and badges. */
    static class Pill extends JLabel {
        private static final long serialVersionUID = 1L;
        Color fill;
        Color outline;
        int radius;

        Pill(String text, int radius) {
            super(text);
            this.radius = radius;
            setOpaque(false);
            setHorizontalAlignment(SwingConstants.CENTER);
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (fill != null || outline != null) {
                Graphics2D gfx = smooth(g);
                int arc = Math.min(radius * 2, getHeight());
                if (fill != null) {
                    gfx.setColor(fill);
                    gfx.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), arc, arc));
                }
                if (outline != null) {
                    gfx.setColor(outline);
                    gfx.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, arc, arc));
                }
                gfx.dispose();
            }
            super.paintComponent(g);
        }
    }

    /** Small filled circle, e.g. a team colour before a name. */
    static Icon dot(final Color color, final int size) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D gfx = smooth(g);
                gfx.setColor(color);
                gfx.fillOval(x, y, size, size);
                gfx.dispose();
            }

            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }
        };
    }

    // ---- chevrons, combos, spinners, sliders ----

    static void chevron(Graphics2D gfx, int cx, int cy, int size, boolean up) {
        Path2D.Float path = new Path2D.Float();
        int dir = up ? -1 : 1;
        path.moveTo(cx - size, cy - dir * size / 2f);
        path.lineTo(cx, cy + dir * size / 2f);
        path.lineTo(cx + size, cy - dir * size / 2f);
        gfx.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        gfx.draw(path);
    }

    private static JButton chevronButton(final Theme theme, final boolean up, final int size) {
        JButton button = new JButton() {
            private static final long serialVersionUID = 1L;

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D gfx = smooth(g);
                gfx.setColor(getModel().isRollover() ? theme.fg : theme.muted);
                chevron(gfx, getWidth() / 2, getHeight() / 2, size, up);
                gfx.dispose();
            }
        };
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setFocusable(false);
        button.setOpaque(false);
        button.setRolloverEnabled(true);
        button.setBorder(BorderFactory.createEmptyBorder());
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    /** Flat combo box; put it inside {@link #boxed}. */
    static JComboBox<String> combo(final Theme theme, String[] values) {
        final JComboBox<String> box = new JComboBox<String>(values);
        box.setUI(new BasicComboBoxUI() {
            @Override
            protected JButton createArrowButton() {
                JButton arrow = chevronButton(theme, false, 4);
                arrow.setPreferredSize(new Dimension(26, 20));
                return arrow;
            }

            @Override
            public void paint(Graphics g, JComponent c) {
                Rectangle area = rectangleForCurrentValue();
                paintCurrentValue(g, area, false);
            }

            @Override
            protected ComboPopup createPopup() {
                BasicComboPopup popup = new BasicComboPopup(comboBox) {
                    private static final long serialVersionUID = 1L;

                    @Override
                    protected void configurePopup() {
                        super.configurePopup();
                        setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(theme.line),
                                BorderFactory.createEmptyBorder(3, 0, 3, 0)));
                        setBackground(theme.card);
                    }
                };
                popup.getList().setBackground(theme.card);
                return popup;
            }
        });
        box.setRenderer(new ListCellRenderer<String>() {
            private final JLabel cell = new JLabel();

            @Override
            public Component getListCellRendererComponent(JList<? extends String> list, String value, int index,
                    boolean selected, boolean focus) {
                cell.setText(value == null ? "" : value);
                cell.setFont(theme.small);
                cell.setForeground(theme.fg);
                cell.setOpaque(index >= 0);
                cell.setBackground(selected ? theme.accentSoft : theme.card);
                cell.setBorder(index >= 0 ? BorderFactory.createEmptyBorder(6, 10, 6, 10)
                        : BorderFactory.createEmptyBorder(0, 10, 0, 4));
                return cell;
            }
        });
        box.setOpaque(false);
        box.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
        box.setFont(theme.small);
        box.setForeground(theme.fg);
        box.setBackground(theme.field);
        box.setFocusable(false);
        box.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return box;
    }

    /** Flat spinner with chevron steppers; call again after replacing the editor. Put it inside {@link #boxed}. */
    static void style(final JSpinner spinner, final Theme theme) {
        spinner.setUI(new BasicSpinnerUI() {
            @Override
            protected Component createNextButton() {
                JButton next = chevronButton(theme, true, 3);
                next.setPreferredSize(new Dimension(22, 12));
                installNextButtonListeners(next);
                return next;
            }

            @Override
            protected Component createPreviousButton() {
                JButton previous = chevronButton(theme, false, 3);
                previous.setPreferredSize(new Dimension(22, 12));
                installPreviousButtonListeners(previous);
                return previous;
            }
        });
        spinner.setOpaque(false);
        spinner.setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 2));
        JComponent editor = spinner.getEditor();
        editor.setOpaque(false);
        if (editor instanceof JSpinner.DefaultEditor) {
            JTextField text = ((JSpinner.DefaultEditor) editor).getTextField();
            style(text, theme);
            text.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 4));
            text.setHorizontalAlignment(SwingConstants.LEFT);
        }
    }

    static JSlider slider(final Theme theme, int min, int max, int value) {
        final JSlider slider = new JSlider(min, max, Math.max(min, Math.min(max, value)));
        slider.setOpaque(false);
        slider.setFocusable(false);
        slider.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        slider.setUI(new BasicSliderUI(slider) {
            @Override
            protected Dimension getThumbSize() {
                return new Dimension(14, 14);
            }

            @Override
            public void paintTrack(Graphics g) {
                Graphics2D gfx = smooth(g);
                int y = trackRect.y + trackRect.height / 2 - 2;
                gfx.setColor(theme.thumb);
                gfx.fill(new RoundRectangle2D.Float(trackRect.x, y, trackRect.width, 4, 4, 4));
                int filled = thumbRect.x + thumbRect.width / 2 - trackRect.x;
                gfx.setColor(slider.isEnabled() ? theme.accent : theme.muted);
                gfx.fill(new RoundRectangle2D.Float(trackRect.x, y, Math.max(0, filled), 4, 4, 4));
                gfx.dispose();
            }

            @Override
            public void paintThumb(Graphics g) {
                Graphics2D gfx = smooth(g);
                int size = 14;
                int x = thumbRect.x + (thumbRect.width - size) / 2;
                int y = thumbRect.y + (thumbRect.height - size) / 2;
                gfx.setColor(alpha(Color.BLACK, 60));
                gfx.fillOval(x, y + 1, size, size);
                gfx.setColor(slider.isEnabled() ? Color.WHITE : theme.muted);
                gfx.fillOval(x, y, size, size);
                gfx.setColor(slider.isEnabled() ? theme.accent : theme.line);
                gfx.setStroke(new BasicStroke(2f));
                gfx.drawOval(x + 1, y + 1, size - 2, size - 2);
                gfx.dispose();
            }

            @Override
            public void paintFocus(Graphics g) {
            }
        });
        return slider;
    }

    // ---- dialogs ----

    /** Themed yes/no dialog; true when the user confirmed. */
    static boolean confirm(Component owner, Theme theme, String title, String message, String yes, boolean danger) {
        Window window = owner == null ? null : SwingUtilities.getWindowAncestor(owner);
        final JDialog dialog = new JDialog(window, title, java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setUndecorated(true);
        dialog.setAlwaysOnTop(true);
        final boolean[] answer = { false };
        Round panel = new Round(theme.card, theme.line);
        panel.radius = 12;
        panel.setLayout(new BorderLayout(0, 14));
        panel.setBorder(BorderFactory.createEmptyBorder(18, 20, 16, 20));
        JPanel text = new JPanel(new BorderLayout(0, 6));
        text.setOpaque(false);
        text.add(label(theme, title, theme.title, theme.fg), BorderLayout.NORTH);
        text.add(label(theme, "<html><div style='width:240px'>" + message + "</div></html>", theme.small, theme.muted),
                BorderLayout.CENTER);
        panel.add(text, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        JButton cancel = button(theme, "Cancel", GHOST);
        JButton ok = button(theme, yes, danger ? DANGER : PRIMARY);
        cancel.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                dialog.dispose();
            }
        });
        ok.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                answer[0] = true;
                dialog.dispose();
            }
        });
        buttons.add(cancel);
        buttons.add(ok);
        panel.add(buttons, BorderLayout.SOUTH);
        dialog.setContentPane(panel);
        try {
            dialog.setBackground(new Color(0, 0, 0, 0));
        } catch (Throwable unsupported) {
            panel.radius = 0;
        }
        dialog.pack();
        dialog.setLocationRelativeTo(window);
        dialog.getRootPane().setDefaultButton(ok);
        dialog.setVisible(true);
        return answer[0];
    }

    /** Recursively makes plain panels transparent so a rounded parent shows through. */
    static void clear(Container container) {
        for (Component child : container.getComponents()) {
            if (child instanceof JPanel && !(child instanceof Round)) {
                ((JPanel) child).setOpaque(false);
            }
            if (child instanceof Container) {
                clear((Container) child);
            }
        }
    }

    static MouseAdapter hoverRepaint(final JComponent target) {
        return new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
                target.repaint();
            }

            @Override
            public void mouseExited(MouseEvent event) {
                target.repaint();
            }
        };
    }
}
