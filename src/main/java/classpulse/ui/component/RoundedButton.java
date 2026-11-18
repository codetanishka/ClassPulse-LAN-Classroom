package classpulse.ui.component;

import classpulse.brand.Brand;

import javax.swing.JButton;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Flat rounded button with a small amount of hover feedback. Five variants
 * cover every action in the application.
 */
public class RoundedButton extends JButton {
    public enum Style {
        PRIMARY, SECONDARY, GHOST, ACCENT, DANGER
    }

    private final Style style;

    public RoundedButton(String text, Style style) {
        super(text);
        this.style = style;
        setFont(Brand.font(java.awt.Font.BOLD, 13));
        setForeground(foregroundFor(style));
        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setRolloverEnabled(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setBorder(new EmptyBorder(10, 17, 10, 17));
    }

    private Color foregroundFor(Style style) {
        return switch (style) {
            case PRIMARY, DANGER -> Color.WHITE;
            case SECONDARY, GHOST -> Brand.INK;
            case ACCENT -> Brand.INK;
        };
    }

    private Color backgroundFor(boolean hover, boolean pressed) {
        if (!isEnabled()) {
            return new Color(0xE4E9EB);
        }
        return switch (style) {
            case PRIMARY -> pressed ? Brand.PRIMARY_DARK
                    : hover ? new Color(0x0C6C6B) : Brand.PRIMARY;
            case SECONDARY -> pressed ? new Color(0xECF1F3)
                    : hover ? new Color(0xF4F8F9) : Brand.SURFACE;
            case GHOST -> pressed ? new Color(0xD7ECEB)
                    : hover ? Brand.PRIMARY_SOFT : new Color(0, 0, 0, 0);
            case ACCENT -> pressed ? new Color(0xD8913A)
                    : hover ? new Color(0xF0AF5F) : Brand.ACCENT;
            case DANGER -> pressed ? new Color(0x973737)
                    : hover ? new Color(0xA63F3F) : Brand.DANGER;
        };
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean hover = getModel().isRollover();
            boolean pressed = getModel().isPressed();
            g.setColor(backgroundFor(hover, pressed));
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            if (style == Style.SECONDARY) {
                g.setColor(Brand.BORDER_STRONG);
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            }
            if (isFocusOwner() && isEnabled()) {
                g.setColor(new Color(14, 124, 123, 65));
                g.drawRoundRect(2, 2, getWidth() - 5, getHeight() - 5, 10, 10);
            }
        } finally {
            g.dispose();
        }
        if (!isEnabled()) {
            setForeground(new Color(0x9AA6AC));
        } else {
            setForeground(foregroundFor(style));
        }
        super.paintComponent(graphics);
    }
}
