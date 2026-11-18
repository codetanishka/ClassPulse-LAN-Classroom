package classpulse;

import classpulse.brand.Brand;
import classpulse.ui.LauncherFrame;
import classpulse.ui.Ui;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.GraphicsEnvironment;

/**
 * Application entry point.
 */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println(Brand.NAME + " needs a desktop environment to run.");
            System.exit(1);
        }

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // The cross-platform look and feel is a safe fallback.
        }
        Ui.applyGlobalDefaults();

        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            error.printStackTrace();
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null,
                    "An unexpected error occurred:\n"
                            + (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()),
                    Brand.NAME + " - Unexpected error", JOptionPane.ERROR_MESSAGE));
        });

        SwingUtilities.invokeLater(() -> {
            LauncherFrame launcher = new LauncherFrame();
            launcher.setVisible(true);
        });
    }
}
