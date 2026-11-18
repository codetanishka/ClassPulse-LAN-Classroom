package classpulse.brand;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Shared identity for the application: colours, fonts and the generated logo.
 * Keeping the palette here keeps every screen visually consistent.
 */
public final class Brand {
    public static final String NAME = "ClassPulse";
    public static final String TAGLINE = "LAN Classroom Interaction System";
    public static final String VERSION = "1.0.0";

    public static final Color INK = new Color(0x182B3A);
    public static final Color INK_SOFT = new Color(0x3A5060);
    public static final Color MUTED = new Color(0x6E7E88);
    public static final Color BG = new Color(0xF3F6F7);
    public static final Color SURFACE = Color.WHITE;
    public static final Color BORDER = new Color(0xDDE5E8);
    public static final Color BORDER_STRONG = new Color(0xC8D4D9);
    public static final Color PRIMARY = new Color(0x0E7C7B);
    public static final Color PRIMARY_DARK = new Color(0x0A5D5C);
    public static final Color PRIMARY_SOFT = new Color(0xE4F3F1);
    public static final Color ACCENT = new Color(0xE9A44C);
    public static final Color ACCENT_SOFT = new Color(0xFDF2E2);
    public static final Color SUCCESS = new Color(0x2F7D5A);
    public static final Color SUCCESS_SOFT = new Color(0xE8F4EE);
    public static final Color DANGER = new Color(0xB74747);
    public static final Color DANGER_SOFT = new Color(0xFAEAEA);

    public static final String FONT_FAMILY = chooseFont();

    private Brand() {
    }

    private static String chooseFont() {
        String[] preferred = {"Segoe UI", "Aptos", "Calibri", "SansSerif"};
        var available = new ArrayList<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String family : preferred) {
            if (available.stream().anyMatch(name -> name.equalsIgnoreCase(family))) {
                return family;
            }
        }
        return Font.SANS_SERIF;
    }

    public static Font font(int style, int size) {
        return new Font(FONT_FAMILY, style, size).deriveFont((float) size);
    }

    public static BufferedImage createLogo(int size) {
        int s = Math.max(16, size);
        BufferedImage image = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            float inset = Math.max(0.5f, s * 0.025f);
            float arc = s * 0.26f;
            RoundRectangle2D background = new RoundRectangle2D.Float(
                    inset, inset, s - inset * 2, s - inset * 2, arc, arc);
            g.setPaint(new GradientPaint(0, 0, new Color(0x12918B), s, s, new Color(0x123748)));
            g.fill(background);

            g.setColor(new Color(255, 255, 255, 38));
            g.setStroke(new BasicStroke(Math.max(1f, s * 0.012f)));
            g.draw(new RoundRectangle2D.Float(
                    s * 0.075f, s * 0.075f, s * 0.85f, s * 0.85f,
                    s * 0.21f, s * 0.21f));

            // White speech bubble with a small tail.
            Color bubble = new Color(0xFF, 0xFF, 0xFF, 246);
            g.setColor(bubble);
            RoundRectangle2D bubbleShape = new RoundRectangle2D.Float(
                    s * 0.19f, s * 0.22f, s * 0.62f, s * 0.43f, s * 0.19f, s * 0.19f);
            g.fill(bubbleShape);
            int[] tailX = {Math.round(s * 0.31f), Math.round(s * 0.43f), Math.round(s * 0.37f)};
            int[] tailY = {Math.round(s * 0.62f), Math.round(s * 0.62f), Math.round(s * 0.73f)};
            g.fillPolygon(tailX, tailY, 3);

            // Three dots: the middle one is the amber accent.
            float dot = s * 0.062f;
            float dotY = s * 0.405f;
            g.setColor(new Color(0x0E7C7B));
            fillCircle(g, s * 0.335f, dotY, dot);
            fillCircle(g, s * 0.665f, dotY, dot);
            g.setColor(ACCENT);
            fillCircle(g, s * 0.50f, dotY, dot);

            // A small pulse line gives the mark its own identity.
            g.setStroke(new BasicStroke(Math.max(1.2f, s * 0.026f),
                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(0xE9A44C));
            g.drawLine(Math.round(s * 0.30f), Math.round(s * 0.815f),
                    Math.round(s * 0.42f), Math.round(s * 0.815f));
            g.drawLine(Math.round(s * 0.42f), Math.round(s * 0.815f),
                    Math.round(s * 0.47f), Math.round(s * 0.765f));
            g.drawLine(Math.round(s * 0.47f), Math.round(s * 0.765f),
                    Math.round(s * 0.53f), Math.round(s * 0.855f));
            g.drawLine(Math.round(s * 0.53f), Math.round(s * 0.855f),
                    Math.round(s * 0.58f), Math.round(s * 0.815f));
            g.drawLine(Math.round(s * 0.58f), Math.round(s * 0.815f),
                    Math.round(s * 0.70f), Math.round(s * 0.815f));
        } finally {
            g.dispose();
        }
        return image;
    }

    private static void fillCircle(Graphics2D g, float centerX, float centerY, float diameter) {
        g.fill(new java.awt.geom.Ellipse2D.Float(centerX - diameter / 2,
                centerY - diameter / 2, diameter, diameter));
    }

    /**
     * Writes build assets. Run after compiling to regenerate the PNG and ICO.
     */
    public static void main(String[] args) throws IOException {
        Path output = Path.of(args.length > 0 ? args[0] : "assets");
        Files.createDirectories(output);
        writePng(output.resolve("ClassPulse-logo.png"), createLogo(512));
        writePng(output.resolve("ClassPulse-64.png"), createLogo(64));
        writeIco(output.resolve("ClassPulse.ico"));
        System.out.println("Brand assets written to " + output.toAbsolutePath());
    }

    private static void writePng(Path path, BufferedImage image) throws IOException {
        ImageIO.write(image, "png", path.toFile());
    }

    private static void writeIco(Path path) throws IOException {
        int[] sizes = {16, 32, 48, 64, 128, 256};
        List<byte[]> frames = new ArrayList<>();
        for (int size : sizes) {
            BufferedImage image = createLogo(size);
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            ImageIO.write(image, "png", buffer);
            frames.add(buffer.toByteArray());
        }

        int headerSize = 6 + frames.size() * 16;
        int totalSize = headerSize + frames.stream().mapToInt(frame -> frame.length).sum();
        ByteBuffer data = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN);
        data.putShort((short) 0);
        data.putShort((short) 1);
        data.putShort((short) frames.size());

        int offset = headerSize;
        for (int i = 0; i < frames.size(); i++) {
            int size = sizes[i];
            data.put((byte) (size == 256 ? 0 : size));
            data.put((byte) (size == 256 ? 0 : size));
            data.put((byte) 0);
            data.put((byte) 0);
            data.putShort((short) 1);
            data.putShort((short) 32);
            data.putInt(frames.get(i).length);
            data.putInt(offset);
            offset += frames.get(i).length;
        }
        for (byte[] frame : frames) {
            data.put(frame);
        }
        Files.write(path, data.array());
    }
}
