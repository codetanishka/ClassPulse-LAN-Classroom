package classpulse;

import classpulse.ui.LauncherFrame;
import classpulse.ui.StudentFrame;
import classpulse.ui.TeacherFrame;
import classpulse.ui.component.OptionButton;

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 * Opens the real Swing windows, starts a real class, joins as a student through
 * the actual form, sends an announcement and completes one MCQ.
 */
public final class UiSmokeTest {
    private static final int PORT = 5061;
    private static LauncherFrame launcher;
    private static TeacherFrame teacher;
    private static StudentFrame student;

    public static void main(String[] args) throws Exception {
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            error.printStackTrace();
            System.exit(1);
        });
        edt(() -> {
            launcher = new LauncherFrame();
            launcher.setVisible(true);
        });
        waitFor(() -> findVisibleButton(launcher, "Start a class") != null, "Launcher did not show its buttons.");
        System.out.println("PASS  launcher window opened");

        edt(() -> findVisibleButton(launcher, "Join a class").doClick());
        waitFor(() -> findWindow("ClassPulse - Student") != null, "Student role did not open from the launcher.");
        StudentFrame probeStudent = (StudentFrame) findWindow("ClassPulse - Student");
        waitFor(() -> findVisibleButton(probeStudent, "Join class") != null,
                "Student join form did not open from the launcher.");
        edt(() -> findVisibleButton(probeStudent, "Back to home").doClick());
        waitFor(launcher::isVisible, "Launcher did not return after closing the student role.");
        System.out.println("PASS  launcher opened the student role and came back");

        edt(() -> findVisibleButton(launcher, "Start a class").doClick());
        waitFor(() -> findWindow("ClassPulse - Teacher") != null, "Teacher frame did not open.");
        teacher = (TeacherFrame) findWindow("ClassPulse - Teacher");
        waitFor(() -> findVisibleButton(teacher, "Start class") != null, "Teacher setup card is missing.");
        System.out.println("PASS  teacher setup card opened");

        edt(() -> {
            List<JTextField> fields = visibleFields(teacher);
            require(fields.size() >= 3, "Teacher setup fields not found.");
            fields.get(2).setText(String.valueOf(PORT));
            findVisibleButton(teacher, "Start class").doClick();
        });
        waitFor(() -> findVisibleButton(teacher, "Send to class") != null,
                "Teacher dashboard did not appear after starting the class.");
        waitFor(() -> hasLabelContaining(teacher, ":" + PORT), "Server address was not displayed.");
        System.out.println("PASS  class started and teacher dashboard opened on port " + PORT);
        assertLayout(teacher);

        edt(() -> {
            student = new StudentFrame(launcher);
            student.setVisible(true);
        });
        waitFor(() -> findVisibleButton(student, "Join class") != null, "Student setup card is missing.");
        edt(() -> {
            List<JTextField> fields = visibleFields(student);
            require(fields.size() >= 3, "Student join fields not found.");
            fields.get(0).setText("GUI Student");
            fields.get(1).setText("127.0.0.1");
            fields.get(2).setText(String.valueOf(PORT));
            findVisibleButton(student, "Join class").doClick();
        });
        waitFor(() -> findVisibleButton(student, "Leave class") != null,
                "Student lobby did not open after joining.");
        waitFor(() -> hasLabelContaining(student, "GUI Student"), "Student name did not reach the lobby.");
        waitFor(() -> hasLabelContaining(student, "Class 12 - Computer Science"),
                "Class name did not reach the student lobby.");
        System.out.println("PASS  second app instance joined the class over a real socket");
        assertLayout(student);

        edt(() -> {
            JTextArea announcement = visibleTextArea(teacher);
            require(announcement != null, "Announcement input not found.");
            announcement.setText("Remember to bring your lab notebook tomorrow.");
            findVisibleButton(teacher, "Send to class").doClick();
        });
        waitFor(() -> hasLabelContaining(student, "lab notebook"),
                "Announcement did not reach the student screen.");
        System.out.println("PASS  announcement appeared live on the student screen");

        edt(() -> {
            JTabbedPane tabs = findVisible(teacher, JTabbedPane.class);
            require(tabs != null, "Teacher tabs not found.");
            tabs.setSelectedIndex(1);
        });
        waitFor(() -> findVisibleButton(teacher, "Start question") != null, "MCQ form did not appear.");
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
            require(question != null, "Question field not found.");
            require(options.size() >= 2, "Option fields not found.");
            question.setText("Which planet is known as the Red Planet?");
            options.get(0).setText("Venus");
            options.get(1).setText("Mars");
            options.get(2).setText("Jupiter");
            options.get(3).setText("Mercury");
            for (JComponent component : visibleComponents(teacher)) {
                if (component instanceof javax.swing.JRadioButton radio
                        && "B".equals(radio.getText())) {
                    radio.setSelected(true);
                }
            }
            findVisibleButton(teacher, "Start question").doClick();
        });
        waitFor(() -> findVisibleButton(teacher, "End question & reveal") != null,
                "Live teacher result panel did not appear.");
        edt(() -> {
            JTabbedPane tabs = findVisible(student, JTabbedPane.class);
            require(tabs != null, "Student tabs not found.");
            tabs.setSelectedIndex(1);
        });
        waitFor(() -> hasLabelContaining(student, "Red Planet"), "Question did not reach the student.");
        edt(() -> {
            List<OptionButton> choices = new ArrayList<>();
            for (JComponent component : visibleComponents(student)) {
                if (component instanceof OptionButton choice) {
                    choices.add(choice);
                }
            }
            require(choices.size() >= 2, "Student answer options not found.");
            choices.get(1).doClick();
            findVisibleButton(student, "Submit answer").doClick();
        });
        waitFor(() -> hasLabelContaining(student, "Answer submitted"),
                "Student did not receive an answer confirmation.");
        waitFor(() -> hasLabelContaining(teacher, "1 of 1 answered"),
                "Teacher did not see the live answer count.");
        System.out.println("PASS  student submitted an answer and teacher saw it live");

        edt(() -> findVisibleButton(teacher, "End question & reveal").doClick());
        waitFor(() -> hasLabelContaining(student, "Correct!"), "Student did not see the revealed result.");
        waitFor(() -> hasLabelContaining(teacher, "Correct answer: B"),
                "Teacher did not see the correct answer summary.");
        System.out.println("PASS  results were revealed to both screens");
        System.out.println("ALL UI SMOKE TESTS PASSED");

        edt(() -> {
            teacher.dispose();
            student.dispose();
            launcher.dispose();
        });
        System.exit(0);
    }

    private static void assertLayout(Container root) {
        for (JComponent component : visibleComponents(root)) {
            if (component instanceof JButton || component instanceof JTextField
                    || component instanceof JLabel || component instanceof JTextArea) {
                String text = component instanceof JLabel label ? label.getText()
                        : component instanceof AbstractButton button ? button.getText() : "";
                if (text != null && !text.isBlank()) {
                    require(component.getWidth() > 0 && component.getHeight() > 0,
                            "Visible component has no size: " + text);
                }
            }
        }
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

    private static JButton findVisibleButton(Container root, String text) {
        for (JComponent component : visibleComponents(root)) {
            if (component instanceof JButton button && text.equals(button.getText()) && button.isEnabled()) {
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

    private static Window findWindow(String title) {
        for (Window window : Window.getWindows()) {
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
        throw new AssertionError(failure);
    }

    private static void edt(Runnable action) throws Exception {
        SwingUtilities.invokeAndWait(action);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
