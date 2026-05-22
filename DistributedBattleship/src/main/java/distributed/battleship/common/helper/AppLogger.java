package distributed.battleship.common.helper;

import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Shared application logger that can be used from any module.
 * Debug logging is enabled by default.
 */
public final class AppLogger {

    private static final Logger LOGGER = Logger.getLogger("BattagliaNavale");
    private static volatile boolean debugEnabled = true;

    static {
        LOGGER.setUseParentHandlers(false);
        LOGGER.setLevel(Level.ALL);

        Handler consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(Level.ALL);
        consoleHandler.setFormatter(new Formatter() {
            @Override
            public String format(LogRecord record) {
                return String.format("[%1$tT] [%2$s] %3$s%n",
                        record.getMillis(),
                        record.getLevel().getName(),
                        record.getMessage());
            }
        });

        LOGGER.addHandler(consoleHandler);
    }

    private AppLogger() {
    }

    public static void setDebugEnabled(boolean enabled) {
        debugEnabled = enabled;
    }

    public static void debug(String message) {
        if (debugEnabled) {
            LOGGER.fine(message);
        }
    }

    public static void info(String message) {
        LOGGER.info(message);
    }

    public static void error(String message) {
        LOGGER.severe(message);
    }
}
