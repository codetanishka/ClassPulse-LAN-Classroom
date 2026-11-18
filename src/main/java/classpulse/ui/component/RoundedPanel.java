package classpulse.ui.component;

import classpulse.brand.Brand;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * A simple rounded surface used for cards and panels.
 */
public class RoundedPanel extends JPanel {
    private Color fillColor;
    private Color borderColor;
    private int arc;
    private boolean shadow;

    public RoundedPanel() {
        this(Brand.SURFACE, Brand.BORDER, 18, true);
    }

    public RoundedPanel(Color fillColor, Color borderColor, int arc, boolean shadow) {
        this.fillColor = fillColor;
        this.borderColor = borderColor;
        this.arc = arc;
        this.shadow = shadow;
        setOpaque(false);
    }

    public void setFillColor(Color fillColor) {
        this.fillColor = fillColor;
        repaint();
    }

    public void setBorderColor(Color borderColor) {
        this.borderColor = borderColor;
        repaint();
    }

    public void setArc(int arc) {
        this.arc = arc;
        repaint();
    }

    public void setShadow(boolean shadow) {
        this.shadow = shadow;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int width = getWidth();
            int height = getHeight();
            if (shadow) {
                g.setColor(new Color(17, 38, 52, 14));
                g.fillRoundRect(2, 3, width - 3, height - 3, arc, arc);
            }
            g.setColor(fillColor);
            g.fillRoundRect(0, 0, width - 1, height - 1, arc, arc);
            if (borderColor != null) {
                g.setColor(borderColor);
                g.drawRoundRect(0, 0, width - 1, height - 1, arc, arc);
            }
        } finally {
            g.dispose();
        }
        super.paintComponent(graphics);
    }
}
