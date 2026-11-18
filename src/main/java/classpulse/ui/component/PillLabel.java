package classpulse.ui.component;

import classpulse.brand.Brand;

import javax.swing.JLabel;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Small rounded status label, for example "Live" or "Connected".
 */
public class PillLabel extends JLabel {
    private Color fill;
    private Color textColor;

    public PillLabel(String text, Color fill, Color textColor) {
        super(text);
        this.fill = fill;
        this.textColor = textColor;
        setOpaque(false);
        setFont(Brand.font(Font.BOLD, 11));
        setForeground(textColor);
        setBorder(new EmptyBorder(4, 10, 4, 10));
    }

    public void setColors(Color fill, Color textColor) {
        this.fill = fill;
        this.textColor = textColor;
        setForeground(textColor);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(fill);
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1,
                    getHeight(), getHeight());
        } finally {
            g.dispose();
        }
        super.paintComponent(graphics);
    }
}
