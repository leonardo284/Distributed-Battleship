package distributed.battleship.common.helper;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import java.awt.AWTEvent;
import java.awt.Toolkit;
import java.awt.event.WindowEvent;
import java.io.InputStream;

/**
 * Installs the application icon on every {@link JFrame} that is opened,
 * without requiring each view to call {@code setIconImage} individually.
 *
 * <p>Call {@link #install()} once from any {@code main} method before any
 * Swing window is created.  The icon is loaded from {@code /icon.png} on the
 * classpath (i.e. {@code src/main/resources/icon.png}).
 */
public final class AppIconHelper {

    private AppIconHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Registers a global AWT event listener that automatically sets the
     * application icon on every {@link JFrame} window as soon as it opens.
     * Safe to call multiple times — only the first call has any effect.
     */
    public static void install() {
        java.awt.Image icon = loadIcon();
        if (icon == null) {
            return;
        }

        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (event.getID() == WindowEvent.WINDOW_OPENED
                    && event.getSource() instanceof JFrame frame
                    && frame.getIconImages().isEmpty()) {
                frame.setIconImage(icon);
            }
        }, AWTEvent.WINDOW_EVENT_MASK);
    }

    private static java.awt.Image loadIcon() {
        try (InputStream is = AppIconHelper.class.getResourceAsStream("/icon.png")) {
            if (is == null) return null;
            return new ImageIcon(is.readAllBytes()).getImage();
        } catch (Exception e) {
            return null;
        }
    }
}
