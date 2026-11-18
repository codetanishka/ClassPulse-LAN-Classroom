package classpulse.ui.component;

import classpulse.brand.Brand;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * A slim animated-looking result bar. Repainting is cheap and avoids the
 * inconsistent native progress bar styling.
 */
public class MiniBar extends JComponent {
    private int value;
    private int maximum = 1;
    private Color fillColor;

    public MiniBar(Color fillColor) {
        this.fillColor = fillColor;
        setPreferredSize(new Dimension(10, 8));
        setMinimumSize(new Dimension(10, 8));
    }

    public void setValues(int value, int maximum) {
        this.value = Math.max(0, value);
        this.maximum = Math.max(1, maximum);
        repaint();
    }

    public void setFillColor(Color fillColor) {
        this.fillColor = fillColor;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(0xEDF2F4));
            g.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
            int width = (int) Math.round(getWidth() * Math.min(1.0, value / (double) maximum));
            if (width > 0) {
                g.setColor(fillColor == null ? Brand.PRIMARY : fillColor);
                g.fillRoundRect(0, 0, Math.max(getHeight(), width), getHeight(),
                        getHeight(), getHeight());
            }
        } finally {
            g.dispose();
        }
    }
}
