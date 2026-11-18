package classpulse.ui.component;

import classpulse.brand.Brand;

import javax.swing.JToggleButton;
import javax.swing.border.EmptyBorder;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;

/**
 * A full-width answer choice used on the student question screen.
 */
public class OptionButton extends JToggleButton {
    private final String letter;
    private final String optionText;

    public OptionButton(String letter, String optionText) {
        this.letter = letter;
        this.optionText = optionText;
        setText("");
        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setBorder(new EmptyBorder(11, 14, 11, 14));
        setToolTipText(letter + ". " + optionText);
        getAccessibleContext().setAccessibleName("Option " + letter + ": " + optionText);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean selected = isSelected();
            boolean hover = getModel().isRollover();
            Color fill = !isEnabled() ? new Color(0xF0F3F4)
                    : selected ? Brand.PRIMARY_SOFT
                    : hover ? new Color(0xF7FBFB) : new Color(0xFAFCFC);
            Color border = selected ? Brand.PRIMARY : Brand.BORDER_STRONG;
            g.setColor(fill);
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 13, 13);
            g.setColor(border);
            g.setStroke(new BasicStroke(selected ? 1.6f : 1f));
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 13, 13);

            int circle = 24;
            int cx = 14;
            int cy = (getHeight() - circle) / 2;
            g.setColor(selected ? Brand.PRIMARY : new Color(0xEEF3F4));
            g.fill(new Ellipse2D.Float(cx, cy, circle, circle));
            g.setColor(selected ? Color.WHITE : Brand.PRIMARY_DARK);
            g.setFont(Brand.font(Font.BOLD, 11));
            FontMetrics metrics = g.getFontMetrics();
            g.drawString(letter, cx + (circle - metrics.stringWidth(letter)) / 2,
                    cy + (circle - metrics.getHeight()) / 2 + metrics.getAscent());

            int textX = cx + circle + 12;
            int textWidth = getWidth() - textX - 22;
            g.setFont(Brand.font(selected ? Font.BOLD : Font.PLAIN, 13));
            g.setColor(isEnabled() ? Brand.INK : new Color(0x8C999F));
            String draw = optionText;
            FontMetrics fm = g.getFontMetrics();
            if (fm.stringWidth(draw) > textWidth) {
                while (draw.length() > 3 && fm.stringWidth(draw + "...") > textWidth) {
                    draw = draw.substring(0, draw.length() - 1);
                }
                draw = draw + "...";
            }
            g.drawString(draw, textX,
                    (getHeight() - fm.getHeight()) / 2 + fm.getAscent());

            if (selected) {
                int checkX = getWidth() - 28;
                int checkY = getHeight() / 2;
                g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(Brand.PRIMARY);
                g.drawLine(checkX, checkY, checkX + 4, checkY + 4);
                g.drawLine(checkX + 4, checkY + 4, checkX + 11, checkY - 5);
            }
        } finally {
            g.dispose();
        }
    }
}
