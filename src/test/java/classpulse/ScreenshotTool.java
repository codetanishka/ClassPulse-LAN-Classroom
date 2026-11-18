package classpulse;

import classpulse.ui.LauncherFrame;
import classpulse.ui.StudentFrame;
import classpulse.ui.TeacherFrame;
import classpulse.ui.component.OptionButton;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Component;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 * Captures the real Swing windows to PNG files for visual review.
 */
public final class ScreenshotTool {
    private static final int PORT = 5063;
    private static final Path OUTPUT = Path.of("screenshots");
    private static LauncherFrame launcher;
    private static TeacherFrame teacher;
    private static StudentFrame student;

    public static void main(String[] args) throws Exception {
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            error.printStackTrace();
            System.exit(1);
        });
        Files.createDirectories(OUTPUT);
        edt(() -> {
            launcher = new LauncherFrame();
            launcher.setVisible(true);
        });
        waitFor(() -> findVisibleButton(launcher, "Start a class") != null, "launcher");
        settle();
        capture(launcher, "01-launcher");

        edt(() -> findVisibleButton(launcher, "Start a class").doClick());
        waitFor(() -> findWindow("ClassPulse - Teacher") != null, "teacher setup");
        teacher = (TeacherFrame) findWindow("ClassPulse - Teacher");
        waitFor(() -> findVisibleButton(teacher, "Start class") != null, "teacher form");
        settle();
        capture(teacher, "02-teacher-setup");

        edt(() -> {
            List<JTextField> fields = visibleFields(teacher);
            fields.get(2).setText(String.valueOf(PORT));
            findVisibleButton(teacher, "Start class").doClick();
        });
        waitFor(() -> findVisibleButton(teacher, "Send to class") != null, "dashboard");
        settle();
        capture(teacher, "03-teacher-dashboard-empty");

        edt(() -> {
            JTextArea area = visibleTextArea(teacher);
            area.setText("Welcome to ClassPulse. Today we revise networking basics "
                    + "and finish the worksheet from page 42.");
            findVisibleButton(teacher, "Send to class").doClick();
        });
        waitFor(() -> hasLabelContaining(teacher, "networking basics"), "announcement");

        edt(() -> {
            student = new StudentFrame(launcher);
            student.setVisible(true);
        });
        waitFor(() -> findVisibleButton(student, "Join class") != null, "student form");
        settle();
        capture(student, "05-student-join");
        edt(() -> {
            List<JTextField> fields = visibleFields(student);
            fields.get(0).setText("Ananya");
            fields.get(1).setText("127.0.0.1");
            fields.get(2).setText(String.valueOf(PORT));
            findVisibleButton(student, "Join class").doClick();
        });
        waitFor(() -> findVisibleButton(student, "Leave class") != null, "student lobby");
        waitFor(() -> hasLabelContaining(student, "networking basics"), "student announcement");
        settle();
        capture(student, "06-student-announcements");

        edt(() -> {
            JTabbedPane tabs = findVisible(teacher, JTabbedPane.class);
            tabs.setSelectedIndex(1);
        });
        settle();
        capture(teacher, "07-teacher-mcq-composer");

        edt(() -> {
            JTextField question = null;
            List<JTextField> options = new ArrayList<>();
            for (JTextField field : visibleFields(teacher)) {
                if ("Question".equals(field.getToolTipText())) {
                    question = field;
                } else if (field.getToolTipText() != null
                        && field.getToolTipText().startsWith("Option")) {
                    options.add(field);
                }
            }
            question.setText("Which protocol delivers web pages to a browser?");
            options.get(0).setText("FTP");
            options.get(1).setText("HTTP");
            options.get(2).setText("SMTP");
            options.get(3).setText("DNS");
            for (JComponent component : visibleComponents(teacher)) {
                if (component instanceof javax.swing.JRadioButton radio
                        && "B".equals(radio.getText())) {
                    radio.setSelected(true);
                }
            }
            findVisibleButton(teacher, "Start question").doClick();
        });
        waitFor(() -> findVisibleButton(teacher, "End question & reveal") != null, "live quiz");

        edt(() -> {
            JTabbedPane tabs = findVisible(student, JTabbedPane.class);
            tabs.setSelectedIndex(1);
        });
        waitFor(() -> hasLabelContaining(student, "web pages"), "student question");
        settle();
        capture(student, "08-student-question");
        edt(() -> {
            List<OptionButton> choices = new ArrayList<>();
            for (JComponent component : visibleComponents(student)) {
                if (component instanceof OptionButton choice) {
                    choices.add(choice);
                }
            }
            choices.get(1).doClick();
            findVisibleButton(student, "Submit answer").doClick();
        });
        waitFor(() -> hasLabelContaining(student, "Answer submitted"), "answer receipt");
        waitFor(() -> hasLabelContaining(teacher, "1 of 1 answered"), "live count");
        settle();
        capture(teacher, "09-teacher-live-results");

        edt(() -> findVisibleButton(teacher, "End question & reveal").doClick());
        waitFor(() -> hasLabelContaining(student, "Correct!"), "reveal");
        settle();
        capture(teacher, "10-teacher-final-results");
        capture(student, "11-student-result");

        System.out.println("Screenshots written to " + OUTPUT.toAbsolutePath());
        edt(() -> {
            student.dispose();
            teacher.dispose();
            launcher.dispose();
        });
        System.exit(0);
    }

    private static void capture(JFrame frame, String name) throws Exception {
        edt(() -> {
            BufferedImage image = new BufferedImage(frame.getWidth(), frame.getHeight(),
                    BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            frame.paintAll(g);
            g.dispose();
            try {
                ImageIO.write(image, "png", OUTPUT.resolve(name + ".png").toFile());
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        });
    }

    private static void settle() throws Exception {
        Thread.sleep(450);
    }

    private static List<JTextField> visibleFields(Container root) {
        List<JTextField> fields = new ArrayList<>();
        for (JComponent component : visibleComponents(root)) {
            if (component instanceof JTextField field) {
                fields.add(field);
            }
        }
        return fields;
    }

    private static JTextArea visibleTextArea(Container root) {
        for (JComponent component : visibleComponents(root)) {
            if (component instanceof JTextArea area) {
                return area;
            }
        }
        return null;
    }

    private static List<JComponent> visibleComponents(Container root) {
        List<JComponent> result = new ArrayList<>();
        collect(root, result);
        return result;
    }

    private static void collect(Container root, List<JComponent> result) {
        for (Component child : root.getComponents()) {
            if (child instanceof JComponent component && component.isShowing()) {
                result.add(component);
            }
            if (child instanceof Container container) {
                collect(container, result);
            }
        }
    }

    private static AbstractButton findVisibleButton(Container root, String text) {
        for (JComponent component : visibleComponents(root)) {
            if (component instanceof AbstractButton button && text.equals(button.getText())
                    && button.isEnabled()) {
                return button;
            }
        }
        return null;
    }

    private static boolean hasLabelContaining(Container root, String text) {
        for (JComponent component : visibleComponents(root)) {
            if (component instanceof JLabel label && label.getText() != null
                    && label.getText().contains(text)) {
                return true;
            }
        }
        return false;
    }

    private static <T extends JComponent> T findVisible(Container root, Class<T> type) {
        for (JComponent component : visibleComponents(root)) {
            if (type.isInstance(component)) {
                return type.cast(component);
            }
        }
        return null;
    }

    private static JFrame findWindow(String title) {
        for (java.awt.Window window : java.awt.Window.getWindows()) {
            if (window instanceof JFrame frame && frame.isVisible() && title.equals(frame.getTitle())) {
                return frame;
            }
        }
        return null;
    }

    private static void waitFor(BooleanSupplier condition, String failure) throws Exception {
        long deadline = System.currentTimeMillis() + 12_000;
        while (System.currentTimeMillis() < deadline) {
            AtomicReference<Boolean> value = new AtomicReference<>(false);
            SwingUtilities.invokeAndWait(() -> value.set(condition.getAsBoolean()));
            if (value.get()) {
                return;
            }
            Thread.sleep(80);
        }
        throw new AssertionError("Timed out waiting for " + failure);
    }

    private static void edt(Runnable action) throws Exception {
        SwingUtilities.invokeAndWait(action);
    }
}
