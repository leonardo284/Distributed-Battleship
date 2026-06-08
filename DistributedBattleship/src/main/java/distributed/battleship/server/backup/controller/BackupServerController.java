package distributed.battleship.server.backup.controller;

import distributed.battleship.common.config.Config;
import distributed.battleship.common.model.client.Client;
import distributed.battleship.common.model.message.MessageConstants;
import distributed.battleship.common.model.room.Room;
import distributed.battleship.common.model.server.BackupServer;
import distributed.battleship.common.model.server.PrimaryServer;
import distributed.battleship.server.backup.service.BackupServerMessageHandlerService;
import distributed.battleship.server.backup.service.PrimaryServerConnectionService;
import distributed.battleship.server.primary.controller.PrimaryServerController;
import distributed.battleship.server.view.ServerView;
import distributed.battleship.server.view.SimpleServerView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Controls the backup server lifecycle.
 *
 * <p>It starts a dedicated backup server runtime and opens a connection to the primary server.
 */
public class BackupServerController {

    private final BackupServer backupServer;
    private final PrimaryServer primaryServer;
    private final ServerView serverView;
    private PrimaryServerConnectionService primaryServerConnectionService;
    private final BackupServerMessageHandlerService backupServerMessageHandlerService;

    private final List<Client> connectedClients = new ArrayList<>();
    private final List<BackupServer> connectedBackups = new ArrayList<>();
    private volatile long lastPrimaryContact = System.currentTimeMillis();

    public BackupServerController(BackupServer backupServer, PrimaryServer primaryServer) {
        this.backupServer = backupServer;
        this.primaryServer = primaryServer;
        this.serverView = new SimpleServerView();
        this.primaryServerConnectionService = new PrimaryServerConnectionService(backupServer);
        this.backupServerMessageHandlerService = new BackupServerMessageHandlerService(this);
    }

    public BackupServerController(BackupServer backupServer, PrimaryServer primaryServer, ServerView existingView) {
        this.backupServer = backupServer;
        this.primaryServer = primaryServer;
        this.serverView = existingView;
        this.primaryServerConnectionService = new PrimaryServerConnectionService(backupServer);
        this.backupServerMessageHandlerService = new BackupServerMessageHandlerService(this);
    }

    public void start() {
        serverView.start();
        serverView.showServerType(backupServer.getServerType());
        serverView.showConnectedBackupServers(0);
        serverView.showBackupServersList(java.util.List.of());
        serverView.showConnectedClients(0);
        serverView.showRoomsCount(0);
        log("Backup server starting on " + backupServer.getIp() + ":" + backupServer.getConnectionPort());
        log("Backup server type: BACKUP");
        log("Primary endpoint: " + primaryServer.getIp() + ":" + primaryServer.getBackupPort());

        serverView.setCloseAction(this::shutdown);
        serverView.setShowUsersAction(() -> serverView.showUsersTable(new ArrayList<>(connectedClients)));
        serverView.setShowBackupsAction(() -> serverView.showBackupsTable(new ArrayList<>(connectedBackups)));
        serverView.setShowRoomsAction(() ->
                serverView.showRoomsTable(backupServer.getRooms()));

        Thread connectionThread = new Thread(() -> {
            try {
                primaryServerConnectionService.connectToPrimaryServer(primaryServer);
            } catch (IOException ex) {
                log("PRIMARY", "Cannot connect to primary server (" + ex.getMessage() + ") — primary may be down, taking its place");
                promoteToNewPrimary();
                return;
            }

            try {
                backupServer.setConnectionPort(primaryServerConnectionService.getLocalPort());
                log("Connected to primary server " + primaryServer.getIp() + ":" + primaryServer.getBackupPort());
                primaryServerConnectionService.sendMessageToPrimaryServer(
                        new MessageConstants.SSHelloFromBackup(
                                backupServer.getNodeId(),
                                backupServer.getIp(),
                                backupServer.getConnectionPort()));
                log("Sent SS_HELLO_FROM_BACKUP to primary server");

                while (primaryServerConnectionService.isConnectedToPrimaryServer()) {
                    try {
                        backupServerMessageHandlerService.handleMessage(primaryServerConnectionService.waitForMessageFromPrimaryServer());
                        lastPrimaryContact = System.currentTimeMillis();
                    } catch (java.net.SocketTimeoutException ignored) {
                        long elapsed = (System.currentTimeMillis() - lastPrimaryContact) / 1000;
                        if (elapsed >= Config.BACKUP_PRIMARY_TIMEOUT_SECONDS) {
                            log("PRIMARY", "Primary server has not sent any message for " + elapsed + "s — primary appears to be down");
                        }
                    }
                }
            } catch (IOException ex) {
                log("Backup-primary connection closed: " + ex.getMessage());
                handlePrimaryFailover();
            } finally {
                primaryServerConnectionService.disconnectFromPrimaryServer();
            }
        }, "backup-primary-connection-loop");

        connectionThread.setDaemon(true);
        connectionThread.start();
    }

    /**
     * Starts this backup using an already-established connection to the new primary.
     * Used during failover when a backup reconnects to the order-1 backup that promoted itself.
     */
    /**
     * Same as {@link #startWithExistingConnection} but reuses the already-visible view
     * (no {@code serverView.start()} call — the window stays open).
     */
    public void startWithExistingViewAndConnection(PrimaryServerConnectionService existingConnection) {
        this.primaryServerConnectionService = existingConnection;
        serverView.showServerType(backupServer.getServerType());
        serverView.showConnectedBackupServers(0);
        serverView.showBackupServersList(java.util.List.of());
        serverView.showConnectedClients(0);
        serverView.showRoomsCount(0);
        serverView.setCloseAction(this::shutdown);
        serverView.setShowUsersAction(() -> serverView.showUsersTable(new ArrayList<>(connectedClients)));
        serverView.setShowBackupsAction(() -> serverView.showBackupsTable(new ArrayList<>(connectedBackups)));
        serverView.setShowRoomsAction(() -> serverView.showRoomsTable(backupServer.getRooms()));
        log("Backup reconnected to new primary — resuming backup operation (same window)");

        Thread connectionThread = new Thread(() -> {
            try {
                while (existingConnection.isConnectedToPrimaryServer()) {
                    try {
                        backupServerMessageHandlerService.handleMessage(existingConnection.waitForMessageFromPrimaryServer());
                        lastPrimaryContact = System.currentTimeMillis();
                    } catch (java.net.SocketTimeoutException ignored) {
                        long elapsed = (System.currentTimeMillis() - lastPrimaryContact) / 1000;
                        if (elapsed >= Config.BACKUP_PRIMARY_TIMEOUT_SECONDS) {
                            log("PRIMARY", "New primary has not sent any message for " + elapsed + "s — primary appears to be down again");
                        }
                    }
                }
            } catch (IOException ex) {
                log("Backup-primary connection closed: " + ex.getMessage());
                handlePrimaryFailover();
            } finally {
                existingConnection.disconnectFromPrimaryServer();
            }
        }, "backup-primary-connection-loop");

        connectionThread.setDaemon(true);
        connectionThread.start();
    }

    public void startWithExistingConnection(PrimaryServerConnectionService existingConnection) {
        this.primaryServerConnectionService = existingConnection;
        serverView.start();
        serverView.showServerType(backupServer.getServerType());
        serverView.showConnectedBackupServers(0);
        serverView.showBackupServersList(java.util.List.of());
        serverView.showConnectedClients(0);
        serverView.showRoomsCount(0);
        serverView.setCloseAction(this::shutdown);
        serverView.setShowUsersAction(() -> serverView.showUsersTable(new ArrayList<>(connectedClients)));
        serverView.setShowBackupsAction(() -> serverView.showBackupsTable(new ArrayList<>(connectedBackups)));
        serverView.setShowRoomsAction(() -> serverView.showRoomsTable(backupServer.getRooms()));
        log("Backup reconnected to new primary \u2014 resuming backup operation");

        Thread connectionThread = new Thread(() -> {
            try {
                while (existingConnection.isConnectedToPrimaryServer()) {
                    try {
                        backupServerMessageHandlerService.handleMessage(existingConnection.waitForMessageFromPrimaryServer());
                        lastPrimaryContact = System.currentTimeMillis();
                    } catch (java.net.SocketTimeoutException ignored) {
                        long elapsed = (System.currentTimeMillis() - lastPrimaryContact) / 1000;
                        if (elapsed >= Config.BACKUP_PRIMARY_TIMEOUT_SECONDS) {
                            log("PRIMARY", "New primary has not sent any message for " + elapsed + "s \u2014 primary appears to be down again");
                        }
                    }
                }
            } catch (IOException ex) {
                log("Backup-primary connection closed: " + ex.getMessage());
                handlePrimaryFailover();
            } finally {
                existingConnection.disconnectFromPrimaryServer();
            }
        }, "backup-primary-connection-loop");

        connectionThread.setDaemon(true);
        connectionThread.start();
    }


    public void log(String message) {
        log("PRIMARY", message);
    }

    /**
     * Called when connection to the primary is lost.
     * Order-1 backup promotes itself to primary.
     * Other backups try to reconnect to the new primary (order-1 backup) 3 times.
     */
    private void handlePrimaryFailover() {
        log("PRIMARY", "Primary server is down — starting failover procedure (my order=" + backupServer.getOrder() + ")");
        primaryServerConnectionService.disconnectFromPrimaryServer();

        if (backupServer.getOrder() == 1) {
            promoteToNewPrimary();
        } else {
            reconnectToNewPrimary();
        }
    }

    /** Promotes this backup (order=1) to act as the new primary. */
    private void promoteToNewPrimary() {
        log("PRIMARY", "I am order-1 — promoting to new primary");

        PrimaryServer newPrimary = new PrimaryServer(
                backupServer.getIp(),
                Config.PRIMARY_SERVER_PORT,
                Config.BACKUP_SERVER_PORT);
        PrimaryServerController newController = new PrimaryServerController(newPrimary, serverView);

        // Transfer known state to the new primary
        for (Room room : backupServer.getRooms()) {
            newPrimary.addRoom(room);
        }

        log("PRIMARY", "Starting as new primary on port " + Config.PRIMARY_SERVER_PORT);
        newController.startWithExistingView();
    }

    /** Backups with order > 1: try to connect to the new primary (former order-1 backup) up to 3 times. */
    private void reconnectToNewPrimary() {
        BackupServer newPrimaryBackup = connectedBackups.stream()
                .filter(b -> b.getOrder() == 1)
                .findFirst()
                .orElse(null);

        if (newPrimaryBackup == null) {
            log("PRIMARY", "Cannot find order-1 backup to reconnect to — giving up");
            return;
        }

        String newPrimaryIp = newPrimaryBackup.getIp();
        log("PRIMARY", "Attempting to reconnect to new primary at " + newPrimaryIp + ":" + Config.BACKUP_SERVER_PORT);

        PrimaryServer newPrimaryRef = new PrimaryServer(newPrimaryIp, Config.PRIMARY_SERVER_PORT, Config.BACKUP_SERVER_PORT);
        int maxAttempts = 3;
        int delaySeconds = 3;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                Thread.sleep(delaySeconds * 1000L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }

            try {
                log("PRIMARY", "Reconnect attempt " + attempt + "/" + maxAttempts + " to new primary " + newPrimaryIp + ":" + Config.BACKUP_SERVER_PORT);
                PrimaryServerConnectionService newConn = new PrimaryServerConnectionService(backupServer);
                newConn.connectToPrimaryServer(newPrimaryRef);
                backupServer.setConnectionPort(newConn.getLocalPort());
                log("PRIMARY", "Reconnected to new primary — restarting as backup");

                // Re-register with new primary
                newConn.sendMessageToPrimaryServer(
                        new MessageConstants.SSHelloFromBackup(
                                backupServer.getNodeId(),
                                backupServer.getIp(),
                                backupServer.getConnectionPort()));

                // Hand off to a new BackupServerController reusing the existing visible window
                BackupServerController newController = new BackupServerController(backupServer, newPrimaryRef, serverView);
                newController.startWithExistingViewAndConnection(newConn);
                return;
            } catch (IOException ex) {
                log("PRIMARY", "Attempt " + attempt + " failed: " + ex.getMessage());
            }
        }

        log("PRIMARY", "All " + maxAttempts + " reconnect attempts failed — shutting down");
        System.exit(1);
    }

    public void log(String tag, String message) {
        serverView.addLog(tag, message);
    }

    public void shutdown() {
        Thread shutdownThread = new Thread(() -> {
            log("Shutting down backup server, notifying primary...");
            if (primaryServerConnectionService.isConnectedToPrimaryServer()) {
                try {
                    primaryServerConnectionService.sendMessageToPrimaryServer(
                            new MessageConstants.SSBackupExit(
                                    backupServer.getNodeId(),
                                    backupServer.getIp(),
                                    backupServer.getConnectionPort()));
                    log("Sent SS_BACKUP_EXIT to primary server");
                } catch (IOException ex) {
                    log("Failed to send SS_BACKUP_EXIT: " + ex.getMessage());
                }
            }
            primaryServerConnectionService.disconnectFromPrimaryServer();
            System.exit(0);
        }, "backup-shutdown");
        shutdownThread.setDaemon(true);
        shutdownThread.start();
    }

    /** Sets the connection order assigned by the primary via SS_RESPONSE_HELLO. */
    public void setBackupOrder(int order) {
        backupServer.setOrder(order);
        log("PRIMARY", "Backup order assigned by primary: " + order);
        serverView.setBackupOrder(order);
    }

    /** Sends SS_ACK back to the primary in response to SS_SEND_STATE_TO_BACKUP. */
    public void sendAckToPrimary() {
        try {
            primaryServerConnectionService.sendMessageToPrimaryServer(new MessageConstants.SSAck(backupServer.getNodeId()));
            //log("Sent SS_ACK to primary server");
        } catch (IOException ex) {
            log("Failed to send SS_ACK: " + ex.getMessage());
        }
    }

    /** Replaces the backup's state with the snapshot received from the primary. */
    public void applyStateSnapshot(MessageConstants.SSSendStateToBackup stateMsg) {
        MessageConstants.PrimaryStateSnapshot state = stateMsg.state();

        // Replace rooms
        backupServer.clearRooms();
        if (state.rooms != null) {
            for (Room room : state.rooms) {
                backupServer.addRoom(room);
            }
        }

        // Replace connected clients
        connectedClients.clear();
        if (state.connectedClients != null) connectedClients.addAll(state.connectedClients);

        // Replace connected backups
        connectedBackups.clear();
        if (state.connectedBackups != null) connectedBackups.addAll(state.connectedBackups);

        // Update view counters
        serverView.showRoomsCount(state.rooms != null ? state.rooms.size() : 0);
        serverView.showConnectedClients(connectedClients.size());
        serverView.showConnectedBackupServers(connectedBackups.size());
        serverView.showBackupServersList(new ArrayList<>(connectedBackups));

        log("State received: rooms=" + (state.rooms != null ? state.rooms.size() : 0)
                + " clients=" + connectedClients.size()
                + " backups=" + connectedBackups.size());
    }
}
