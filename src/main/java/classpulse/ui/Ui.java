package classpulse.ui;

import classpulse.brand.Brand;
import classpulse.ui.component.RoundedButton;
import classpulse.ui.component.RoundedPanel;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import javax.swing.text.JTextComponent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared Swing styling helpers. The goal is a consistent, calm visual
 * language rather than a different design on every screen.
 */
public final class Ui {
    public static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("hh:mm a").withZone(ZoneId.systemDefault());
    public static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("dd MMM, hh:mm a").withZone(ZoneId.systemDefault());
    public static final EmptyBorder EMPTY = new EmptyBorder(0, 0, 0, 0);
    private static final boolean DIALOGS_SUPPRESSED = Boolean.getBoolean("classpulse.test");

    private Ui() {
    }

    public static void applyGlobalDefaults() {
        Font base = Brand.font(Font.PLAIN, 13);
        UIManager.put("Label.font", base);
        UIManager.put("Button.font", Brand.font(Font.BOLD, 13));
        UIManager.put("TextField.font", base);
        UIManager.put("TextArea.font", base);
        UIManager.put("TextPane.font", base);
        UIManager.put("ComboBox.font", base);
        UIManager.put("Spinner.font", base);
        UIManager.put("List.font", base);
        UIManager.put("Table.font", base);
        UIManager.put("TabbedPane.font", Brand.font(Font.BOLD, 13));
        UIManager.put("OptionPane.messageFont", base);
        UIManager.put("OptionPane.buttonFont", Brand.font(Font.BOLD, 13));
        UIManager.put("ToolTip.font", Brand.font(Font.PLAIN, 12));
        UIManager.put("Label.foreground", Brand.INK);
        UIManager.put("Panel.background", Brand.BG);
        UIManager.put("TextField.background", Brand.SURFACE);
        UIManager.put("TextArea.background", Brand.SURFACE);
        UIManager.put("List.background", Brand.SURFACE);
        UIManager.put("List.selectionBackground", Brand.PRIMARY_SOFT);
        UIManager.put("List.selectionForeground", Brand.INK);
        UIManager.put("TabbedPane.selected", Brand.SURFACE);
        UIManager.put("TabbedPane.contentAreaColor", Brand.BG);
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("ScrollBar.thumb", Brand.BORDER_STRONG);
        UIManager.put("ScrollBar.track", Brand.BG);
    }

    public static JPanel transparent(LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }

    public static JPanel page() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Brand.BG);
        return panel;
    }

    public static RoundedPanel card() {
        return card(new BorderLayout(0, 12));
    }

    public static RoundedPanel card(LayoutManager layout) {
        RoundedPanel panel = new RoundedPanel();
        panel.setLayout(layout);
        panel.setBorder(new EmptyBorder(18, 18, 18, 18));
        return panel;
    }

    public static JLabel heading(String text, int size) {
        JLabel label = new JLabel(text);
        label.setFont(Brand.font(Font.BOLD, size));
        label.setForeground(Brand.INK);
        return label;
    }

    public static JLabel subheading(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Brand.font(Font.PLAIN, 12));
        label.setForeground(Brand.MUTED);
        return label;
    }

    public static JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text.toUpperCase());
        label.setFont(Brand.font(Font.BOLD, 10));
        label.setForeground(Brand.MUTED);
        return label;
    }

    public static JLabel body(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Brand.font(Font.PLAIN, 13));
        label.setForeground(Brand.INK_SOFT);
        return label;
    }

    public static JLabel wrap(String text, int width) {
        return wrap(text, width, Brand.font(Font.PLAIN, 13), Brand.INK_SOFT);
    }

    public static JLabel wrap(String text, int width, Font font, Color color) {
        String safe = escapeHtml(text == null ? "" : text).replace("\n", "<br>");
        JLabel label = new JLabel("<html><body style='width:" + width + "px'>" + safe + "</body></html>");
        label.setFont(font);
        label.setForeground(color);
        return label;
    }

    public static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public static JTextField field() {
        JTextField field = new JTextField();
        field.setFont(Brand.font(Font.PLAIN, 13));
        field.setForeground(Brand.INK);
        field.setBackground(Brand.SURFACE);
        field.setCaretColor(Brand.PRIMARY);
        field.setBorder(fieldBorder(false));
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                field.setBorder(fieldBorder(true));
            }

            @Override
            public void focusLost(FocusEvent e) {
                field.setBorder(fieldBorder(false));
            }
        });
        return field;
    }

    private static javax.swing.border.Border fieldBorder(boolean focused) {
        Color color = focused ? Brand.PRIMARY : Brand.BORDER_STRONG;
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(color, 1, true),
                new EmptyBorder(8, 10, 8, 10));
    }

    public static JTextArea textArea(int rows) {
        JTextArea area = new JTextArea(rows, 20);
        area.setFont(Brand.font(Font.PLAIN, 13));
        area.setForeground(Brand.INK);
        area.setBackground(Brand.SURFACE);
        area.setCaretColor(Brand.PRIMARY);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(new EmptyBorder(9, 10, 9, 10));
        return area;
    }

    public static JScrollPane textAreaScroll(JTextArea area) {
        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Brand.BORDER_STRONG, 1, true),
                new EmptyBorder(0, 0, 0, 0)));
        scroll.getViewport().setBackground(Brand.SURFACE);
        scroll.setBackground(Brand.SURFACE);
        return scroll;
    }

    public static JScrollPane scroll(Component view) {
        JScrollPane scroll = new JScrollPane(view);
        scroll.setBorder(EMPTY);
        scroll.getViewport().setBackground(Brand.BG);
        scroll.setBackground(Brand.BG);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    public static JScrollPane cardScroll(Component view) {
        JScrollPane scroll = scroll(view);
        scroll.getViewport().setBackground(Brand.SURFACE);
        scroll.setBackground(Brand.SURFACE);
        return scroll;
    }

    public static void styleTextComponent(JTextComponent component) {
        component.setFont(Brand.font(Font.PLAIN, 13));
        component.setForeground(Brand.INK);
        component.setBackground(Brand.SURFACE);
        component.setCaretColor(Brand.PRIMARY);
    }

    public static JButton primaryButton(String text) {
        return new RoundedButton(text, RoundedButton.Style.PRIMARY);
    }

    public static JButton secondaryButton(String text) {
        return new RoundedButton(text, RoundedButton.Style.SECONDARY);
    }

    public static JButton ghostButton(String text) {
        return new RoundedButton(text, RoundedButton.Style.GHOST);
    }

    public static JButton accentButton(String text) {
        return new RoundedButton(text, RoundedButton.Style.ACCENT);
    }

    public static JButton dangerButton(String text) {
        return new RoundedButton(text, RoundedButton.Style.DANGER);
    }

    public static void styleSpinner(JSpinner spinner) {
        spinner.setFont(Brand.font(Font.PLAIN, 13));
        spinner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Brand.BORDER_STRONG, 1, true),
                new EmptyBorder(3, 7, 3, 4)));
        JComponent editor = spinner.getEditor();
        if (editor instanceof JSpinner.DefaultEditor defaultEditor) {
            styleTextComponent(defaultEditor.getTextField());
            defaultEditor.getTextField().setBorder(new EmptyBorder(5, 3, 5, 3));
            defaultEditor.getTextField().setHorizontalAlignment(SwingConstants.LEFT);
        }
    }

    public static void styleTabs(JTabbedPane tabs) {
        tabs.setOpaque(false);
        tabs.setBackground(Brand.BG);
        tabs.setForeground(Brand.MUTED);
        tabs.setFont(Brand.font(Font.BOLD, 13));
        tabs.setBorder(EMPTY);
        tabs.setUI(new BasicTabbedPaneUI() {
            @Override
            protected int calculateTabHeight(int tabPlacement, int tabIndex, int fontHeight) {
                return 42;
            }

            @Override
            protected int calculateTabWidth(int tabPlacement, int tabIndex, java.awt.FontMetrics metrics) {
                return super.calculateTabWidth(tabPlacement, tabIndex, metrics) + 26;
            }

            @Override
            protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex,
                                              int x, int y, int w, int h, boolean isSelected) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(isSelected ? Brand.SURFACE : Brand.BG);
                    g2.fillRoundRect(x + 2, y + 5, w - 4, h - 8, 11, 11);
                    if (isSelected) {
                        g2.setColor(Brand.PRIMARY);
                        g2.fillRoundRect(x + 16, y + h - 5, w - 32, 3, 3, 3);
                    }
                } finally {
                    g2.dispose();
                }
            }

            @Override
            protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex,
                                          int x, int y, int w, int h, boolean isSelected) {
                // Deliberately flat.
            }

            @Override
            protected void paintFocusIndicator(Graphics g, int tabPlacement,
                                               java.awt.Rectangle[] rects, int tabIndex,
                                               java.awt.Rectangle iconRect,
                                               java.awt.Rectangle textRect,
                                               boolean isSelected) {
                // Deliberately flat.
            }

            @Override
            protected void paintContentBorder(Graphics g, int tabPlacement, int selectedIndex) {
                // The content is placed in its own card.
            }
        });
    }

    public static JSeparator divider() {
        JSeparator separator = new JSeparator();
        separator.setForeground(Brand.BORDER);
        separator.setBackground(Brand.BORDER);
        return separator;
    }

    public static JPanel emptyState(String title, String message) {
        JPanel panel = transparent(new BorderLayout(0, 8));
        JLabel titleLabel = new JLabel(title, SwingConstants.CENTER);
        titleLabel.setFont(Brand.font(Font.BOLD, 15));
        titleLabel.setForeground(Brand.INK);
        JLabel messageLabel = new JLabel("<html><div style='text-align:center;width:320px'>"
                + escapeHtml(message) + "</div></html>", SwingConstants.CENTER);
        messageLabel.setFont(Brand.font(Font.PLAIN, 12));
        messageLabel.setForeground(Brand.MUTED);
        JPanel text = transparent(new BorderLayout(0, 6));
        text.add(titleLabel, BorderLayout.NORTH);
        text.add(messageLabel, BorderLayout.CENTER);
        panel.add(text, BorderLayout.CENTER);
        return panel;
    }

    public static void showError(Component parent, String title, String message) {
        if (DIALOGS_SUPPRESSED) {
            System.err.println("TEST DIALOG [" + title + "]: " + message);
            return;
        }
        JOptionPane.showMessageDialog(parent, message, title, JOptionPane.ERROR_MESSAGE);
    }

    public static void showInfo(Component parent, String title, String message) {
        if (DIALOGS_SUPPRESSED) {
            System.out.println("TEST INFO [" + title + "]: " + message);
            return;
        }
        JOptionPane.showMessageDialog(parent, message, title, JOptionPane.INFORMATION_MESSAGE);
    }

    public static boolean confirm(Component parent, String title, String message) {
        if (DIALOGS_SUPPRESSED) {
            System.out.println("TEST CONFIRM [" + title + "]: auto-yes");
            return true;
        }
        return JOptionPane.showConfirmDialog(parent, message, title,
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }

    public static String formatTime(long millis) {
        return TIME.format(Instant.ofEpochMilli(millis));
    }

    public static String formatDateTime(long millis) {
        return DATE_TIME.format(Instant.ofEpochMilli(millis));
    }

    public static String bytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    public static String safeFileName(String name) {
        String cleaned = name == null ? "class-notes" : name.replaceAll("[\\\\/:*?\"<>|]", "_");
        return cleaned.isBlank() ? "class-notes" : cleaned;
    }

    public static List<Image> windowIcons() {
        List<Image> icons = new ArrayList<>();
        for (int size : new int[]{16, 24, 32, 48, 64, 128, 256}) {
            icons.add(Brand.createLogo(size));
        }
        return icons;
    }

    public static void setWindowIcon(Window window) {
        if (window instanceof javax.swing.JFrame frame) {
            frame.setIconImages(windowIcons());
        } else if (window instanceof javax.swing.JDialog dialog) {
            dialog.setIconImages(windowIcons());
        }
    }
}
