package classpulse.ui.component;

import classpulse.brand.Brand;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * A compact circular avatar that shows the first letter of a student's name.
 */
public class Avatar extends JComponent {
    private final String initials;
    private final Color fill;
    private final Color text;

    public Avatar(String name, int size) {
        this(name, size, Brand.PRIMARY_SOFT, Brand.PRIMARY_DARK);
    }

    public Avatar(String name, int size, Color fill, Color text) {
        this.initials = firstLetter(name);
        this.fill = fill;
        this.text = text;
        setPreferredSize(new Dimension(size, size));
        setMinimumSize(new Dimension(size, size));
        setMaximumSize(new Dimension(size, size));
    }

    private static String firstLetter(String name) {
        if (name == null || name.isBlank()) {
            return "?";
        }
        return name.trim().substring(0, 1).toUpperCase();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(fill);
            g.fillOval(0, 0, getWidth() - 1, getHeight() - 1);
            g.setColor(text);
            g.setFont(Brand.font(Font.BOLD, Math.max(11, getHeight() / 2 - 2)));
            var metrics = g.getFontMetrics();
            int x = (getWidth() - metrics.stringWidth(initials)) / 2;
            int y = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
            g.drawString(initials, x, y);
        } finally {
            g.dispose();
        }
    }
}
