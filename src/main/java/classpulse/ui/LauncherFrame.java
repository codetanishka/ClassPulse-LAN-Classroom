package classpulse.ui;

import classpulse.brand.Brand;
import classpulse.ui.component.RoundedPanel;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;

/**
 * First window: choose the teacher or student role.
 */
public final class LauncherFrame extends JFrame {
    public LauncherFrame() {
        super(Brand.NAME + " - " + Brand.TAGLINE);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(920, 620));
        setSize(1040, 690);
        setLocationRelativeTo(null);
        setIconImages(Ui.windowIcons());

        JPanel root = Ui.page();
        root.setBorder(new EmptyBorder(24, 28, 24, 28));
        root.add(buildTopBar(), BorderLayout.NORTH);
        root.add(buildBody(), BorderLayout.CENTER);
        root.add(buildFooter(), BorderLayout.SOUTH);
        setContentPane(root);
    }

    private JPanel buildTopBar() {
        JPanel bar = Ui.transparent(new BorderLayout());
        bar.setBorder(new EmptyBorder(0, 2, 20, 2));

        JPanel identity = Ui.transparent(new BorderLayout(11, 0));
        JLabel logo = new JLabel(new ImageIcon(Brand.createLogo(44)));
        identity.add(logo, BorderLayout.WEST);

        JPanel words = Ui.transparent(new GridLayout(2, 1, 0, 1));
        JLabel name = Ui.heading(Brand.NAME, 19);
        JLabel tagline = Ui.subheading(Brand.TAGLINE);
        words.add(name);
        words.add(tagline);
        identity.add(words, BorderLayout.CENTER);
        bar.add(identity, BorderLayout.WEST);

        JLabel local = new JLabel("LOCAL NETWORK  •  NO INTERNET REQUIRED");
        local.setFont(Brand.font(Font.BOLD, 10));
        local.setForeground(Brand.MUTED);
        bar.add(local, BorderLayout.EAST);
        return bar;
    }

    private JPanel buildBody() {
        JPanel body = Ui.transparent(new BorderLayout(20, 0));
        body.add(buildHero(), BorderLayout.CENTER);
        body.add(buildRoleColumn(), BorderLayout.EAST);
        return body;
    }

    private JPanel buildHero() {
        JPanel hero = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setPaint(new GradientPaint(0, 0, new Color(0x174B56),
                            getWidth(), getHeight(), new Color(0x102C3A)));
                    g.fillRoundRect(0, 0, getWidth(), getHeight(), 26, 26);
                    g.setColor(new Color(255, 255, 255, 12));
                    g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 26, 26);
                } finally {
                    g.dispose();
                }
            }
        };
        hero.setOpaque(false);
        hero.setBorder(new EmptyBorder(38, 38, 34, 38));

        JPanel content = Ui.transparent(new BorderLayout(0, 0));

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));

        JLabel logo = new JLabel(new ImageIcon(Brand.createLogo(82)));
        logo.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(logo);
        text.add(Box.createVerticalStrut(20));

        JLabel kicker = new JLabel("CLASSROOM, IN REAL TIME");
        kicker.setFont(Brand.font(Font.BOLD, 10));
        kicker.setForeground(new Color(0xE9A44C));
        kicker.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(kicker);
        text.add(Box.createVerticalStrut(8));

        JLabel headline = new JLabel("<html>Keep every student<br>on the same page.</html>");
        headline.setFont(Brand.font(Font.BOLD, 31));
        headline.setForeground(Color.WHITE);
        headline.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(headline);
        text.add(Box.createVerticalStrut(14));

        JLabel sub = new JLabel("<html><div style='width:360px'>"
                + "Announcements, live multiple-choice questions and class notes over your local network. "
                + "One teacher starts the class; students join from their own computers."
                + "</div></html>");
        sub.setFont(Brand.font(Font.PLAIN, 13));
        sub.setForeground(new Color(0xC9DDE0));
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(sub);
        text.add(Box.createVerticalStrut(24));

        text.add(feature("01", "Send announcements instantly"));
        text.add(Box.createVerticalStrut(10));
        text.add(feature("02", "Ask MCQs with a live countdown"));
        text.add(Box.createVerticalStrut(10));
        text.add(feature("03", "Share notes for students to download"));

        content.add(text, BorderLayout.CENTER);

        JLabel footer = new JLabel("JAVA SWING  •  SOCKETS  •  SERIALIZATION");
        footer.setFont(Brand.font(Font.BOLD, 9));
        footer.setForeground(new Color(0x8FB1B5));
        content.add(footer, BorderLayout.SOUTH);

        hero.add(content, BorderLayout.CENTER);
        return hero;
    }

    private JPanel feature(String number, String text) {
        JPanel row = Ui.transparent(new BorderLayout(12, 0));
        JLabel n = new JLabel(number);
        n.setFont(Brand.font(Font.BOLD, 10));
        n.setForeground(new Color(0xE9A44C));
        n.setPreferredSize(new Dimension(22, 18));
        row.add(n, BorderLayout.WEST);
        JLabel label = new JLabel(text);
        label.setFont(Brand.font(Font.PLAIN, 13));
        label.setForeground(new Color(0xE2EFF0));
        row.add(label, BorderLayout.CENTER);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(390, 22));
        return row;
    }

    private JPanel buildRoleColumn() {
        JPanel column = Ui.transparent(new GridLayout(2, 1, 0, 14));
        column.setPreferredSize(new Dimension(355, 0));
        column.add(roleCard("Teacher", "Start a class",
                "Run the classroom server, send announcements, ask questions and share files.",
                true));
        column.add(roleCard("Student", "Join a class",
                "Connect with the teacher's IP address, answer questions and download notes.",
                false));
        return column;
    }

    private JPanel roleCard(String role, String action, String description, boolean teacher) {
        RoundedPanel card = Ui.card(new BorderLayout(0, 14));
        card.setBorder(new EmptyBorder(20, 20, 20, 20));

        JPanel header = Ui.transparent(new BorderLayout(12, 0));
        JLabel badge = new JLabel(teacher ? "T" : "S", SwingConstants.CENTER);
        badge.setOpaque(true);
        badge.setBackground(teacher ? Brand.PRIMARY_SOFT : Brand.ACCENT_SOFT);
        badge.setForeground(teacher ? Brand.PRIMARY_DARK : new Color(0x8A5A18));
        badge.setFont(Brand.font(Font.BOLD, 17));
        badge.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        badge.setPreferredSize(new Dimension(42, 42));
        header.add(badge, BorderLayout.WEST);

        JPanel titleBox = Ui.transparent(new BorderLayout(0, 3));
        JLabel title = Ui.heading(role + " mode", 17);
        JLabel sub = Ui.subheading(teacher ? "You host this class" : "You are joining a class");
        titleBox.add(title, BorderLayout.NORTH);
        titleBox.add(sub, BorderLayout.CENTER);
        header.add(titleBox, BorderLayout.CENTER);
        card.add(header, BorderLayout.NORTH);

        JLabel copy = Ui.wrap(description, 305, Brand.font(Font.PLAIN, 12), Brand.MUTED);
        card.add(copy, BorderLayout.CENTER);

        JButton button = teacher ? Ui.primaryButton(action) : Ui.secondaryButton(action);
        button.setPreferredSize(new Dimension(0, 40));
        button.addActionListener(e -> openRole(teacher));
        card.add(button, BorderLayout.SOUTH);
        return card;
    }

    private void openRole(boolean teacher) {
        setVisible(false);
        if (teacher) {
            new TeacherFrame(this).setVisible(true);
        } else {
            new StudentFrame(this).setVisible(true);
        }
    }

    private JPanel buildFooter() {
        JPanel footer = Ui.transparent(new BorderLayout());
        footer.setBorder(new EmptyBorder(15, 4, 0, 4));
        JLabel version = new JLabel("Version " + Brand.VERSION + "  •  Made for classroom LANs");
        version.setFont(Brand.font(Font.PLAIN, 10));
        version.setForeground(Brand.MUTED);
        footer.add(version, BorderLayout.WEST);

        JLabel hint = new JLabel("Teacher starts first, then students join.");
        hint.setFont(Brand.font(Font.PLAIN, 10));
        hint.setForeground(Brand.MUTED);
        footer.add(hint, BorderLayout.EAST);
        return footer;
    }

    public void showAgain() {
        setVisible(true);
        toFront();
        requestFocus();
    }
}
