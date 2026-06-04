package distributed.battleship.common.view;

/**
 * Interface for views that support debug logging.
 * Views implementing this interface can display timestamped log messages
 * in a dedicated debug area when the application is running in debug mode.
 */
public interface IViewLoggable {

    /**
     * Appends a log entry to the debug log area.
     * Implementations should prefix the message with a timestamp.
     * If debug mode is disabled, the call may be ignored.
     *
     * @param tag The context tag (e.g. CLIENT, PRIMARY, BACKUP)
     * @param log The log message to append
     */
    void addLog(String tag, String log);

    /**
     * Returns the current textual content of the view log area.
     *
     * @return Full log text, or an empty string when unavailable
     */
    String getViewLog();
}

