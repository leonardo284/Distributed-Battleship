package distributed.battleship.server.view;

import distributed.battleship.common.model.client.Client;
import distributed.battleship.common.model.server.BackupServer;
import distributed.battleship.common.model.server.ServerType;
import distributed.battleship.common.model.room.Room;
import distributed.battleship.common.view.IViewLoggable;

import java.util.List;

/**
 * Interface for the server view.
 * Defines the methods the server view must implement.
 * Extends {@link IViewLoggable} so every server view supports debug logging.
 */
public interface ServerView extends IViewLoggable {
    /**
     * Shows a message to the user.
     *
     * @param message Message to show
     */
    void showMessage(String message);

    /**
     * Shows an error to the user.
     *
     * @param error Error message
     */
    void showError(String error);

    /**
     * Starts the server view.
     */
    void start();

    /**
     * Stops the server view.
     */
    void stop();

    /**
     * Shows the number of connected clients.
     *
     * @param count Number of connected clients
     */
    void showConnectedClients(int count);

    /**
     * Shows the number of currently tracked rooms.
     *
     * @param count Number of rooms
     */
    void showRoomsCount(int count);

    /**
     * Shows the active server role (PRIMARY/BACKUP).
     *
     * @param serverType server role label
     */
    void showServerType(ServerType serverType);

    /**
     * Hides the backup-order indicator (used when a backup promotes to primary).
     */
    void hideBackupOrder();

    /**
     * Shows the number of connected backup servers.
     *
     * @param count number of backup servers connected to the primary
     */
    void showConnectedBackupServers(int count);

    /**
     * Shows the current list of backup server endpoints.
     *
     * @param backupServers list of connected backup server objects
     */
    void showBackupServersList(List<BackupServer> backupServers);

    /**
     * Registers the callback executed when the user asks to inspect rooms.
     *
     * @param showRoomsAction action invoked by the view button
     */
    void setShowRoomsAction(Runnable showRoomsAction);

    /**
     * Registers the callback and makes the "Show Backups" button visible.
     * Only called by the primary server; backup server never calls this.
     *
     * @param action action invoked when the user clicks "Show Backups"
     */
    void setShowBackupsAction(Runnable action);

    /**
     * Opens a popup table containing all currently connected backup servers.
     *
     * @param backups list of backup server objects to display
     */
    void showBackupsTable(List<BackupServer> backups);

    /**
     * Shows the connection order assigned by the primary server on the backup GUI.
     * No-op on the primary view.
     *
     * @param order connection order (1 = first connected)
     */
    void setBackupOrder(int order);

    /**
     * Registers the callback executed when the user clicks "Show Users".
     *
     * @param action action invoked by the view button
     */
    void setShowUsersAction(Runnable action);

    /**
     * Opens a popup table containing all currently connected users.
     *
     * @param users list of connected client objects
     */
    void showUsersTable(List<Client> users);

    /**
     * Registers the callback executed when the server window is closed.
     *
     * @param closeAction The shutdown action managed by the server controller
     */
    void setCloseAction(Runnable closeAction);

    /**
     * Opens a popup table containing all current rooms.
     *
     * @param rooms Snapshot of rooms to display
     */
    void showRoomsTable(List<Room> rooms);
}

