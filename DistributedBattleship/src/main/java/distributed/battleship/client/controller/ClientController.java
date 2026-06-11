package distributed.battleship.client.controller;

import distributed.battleship.common.model.client.Client;
import distributed.battleship.common.model.client.PlayingRoom;
import distributed.battleship.client.service.ClientMessageHandlerService;
import distributed.battleship.client.service.PeerConnectionService;
import distributed.battleship.client.service.ServerConnectionService;
import distributed.battleship.client.view.GameView;
import distributed.battleship.client.view.MenuView;
import distributed.battleship.common.config.Config;
import distributed.battleship.common.model.message.MessageConstants;
import distributed.battleship.common.model.room.grid.Cell;
import distributed.battleship.common.model.room.grid.Grid;
import distributed.battleship.common.model.room.grid.Position;
import distributed.battleship.common.model.server.BackupServer;
import distributed.battleship.common.model.server.PrimaryServer;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.Objects;

/**
 * Coordinates the client model and views and manages TCP communication with the server.
 */
public class ClientController /*implements IClientConnectionController*/ {

    public static final int PEER_CONNECT_TIMEOUT_SECONDS = 5;
    public static final int PEER_START_GRACE_SECONDS = 10;
    public static final int SERVER_RESPONSE_TIMEOUT_SECONDS = 10;
    public static final int PLACEMENT_TIME_SECONDS = 30;
    public static final int PEER_START_TIMEOUT_SECONDS = PLACEMENT_TIME_SECONDS + PEER_START_GRACE_SECONDS;

    //region Fields
    private final Client client;
    private final PrimaryServer primaryServer;
    private final boolean debugMode;
    private final ServerConnectionService serverConnectionService;
    private final PeerConnectionService peerConnectionService;
    private final ClientMessageHandlerService messageHandlerService;

    private MenuView menuView;
    private GameView gameView;
    private PlayingRoom currentRoom;
    private volatile boolean localPlacementDone;
    private volatile boolean opponentStartReceived;
    private volatile boolean placementSequenceStarted;
    private volatile boolean roomClosureNotified;
    private volatile boolean serverMessageListenerStarted;
    private volatile Thread serverMessageListenerThread;
    private final Set<BackupServer> backupServers = new LinkedHashSet<>();
    //endregion

    /**
     * Creates a client controller.
     *
     * @param client Client model
     * @param serverIp Server IP address
     * @param serverPort Server port
     * @param debugMode Whether debug logs should be shown in the views
     */
    public ClientController(Client client, String serverIp, int serverPort, boolean debugMode) {
        this.client = client;
        this.primaryServer = new PrimaryServer(serverIp, serverPort);
        this.debugMode = debugMode;
        this.serverConnectionService = new ServerConnectionService(client);
        this.peerConnectionService = new PeerConnectionService(client);
        this.messageHandlerService = new ClientMessageHandlerService(this, serverConnectionService, peerConnectionService);
    }

    /**
     * Starts the client flow and opens the main menu.
     */
    public void start() {
        SwingUtilities.invokeLater(() -> {
            menuView = createMenuView("");
            menuView.showBackupServers(getBackupServersSnapshot());
            menuView.setVisible(true);
            log("Client started. Waiting for player action from menu view");
        });
    }


    /**
     * Attempts to join a room with the given player name and optional room ID.
     * Sends a CSRequestJoinRoom message to the server via the service and waits for a response.
     *
     * @param playerName name of the player attempting to join
     * @param roomId optional target room ID (null/blank for random public room)
     */
    public void joinRoom(String playerName) {
        final String normalizedName = playerName == null ? "" : playerName.trim();
        if (normalizedName.isEmpty()) {
            showError("Player name is required to join a room.");
            return;
        }

        client.setName(normalizedName);

        int freshPeerConnectionPort = findAvailablePort();
        peerConnectionService.resetLocalPort(freshPeerConnectionPort);
        log("Allocated fresh peer connection port: " + freshPeerConnectionPort);

        Thread joinThread = new Thread(() -> {
            try {
                suspendServerListenerForJoin();
                serverConnectionService.disconnectFromServer();

                // Send join request to server (reconnects automatically if previously disconnected)
                log("Connecting to server...");
                serverConnectionService.connectToServer(
                        serverConnectionService.getConnectedServer() != null
                                ? serverConnectionService.getConnectedServer()
                                : primaryServer
                );
                serverConnectionService.sendMessageToServer(
                        new MessageConstants.CSRequestJoinRoom(
                                client.getNodeId(),
                                normalizedName,
                                client.getIp(),
                                client.getPeerConnectionPort(),
                                client.getServerConnectionPort()));

                // Wait for server response and handle it
                serverConnectionService.waitForMessageWithTimeout(
                        SERVER_RESPONSE_TIMEOUT_SECONDS,
                        "server-join-room-response-wait",
                        messageHandlerService::handleMessage,
                        (timeoutSeconds, threadName) -> {
                            log("Join room response timeout after " + timeoutSeconds + " seconds.");
                            showError("Server response timeout while joining room.");
                        }
                );
            } catch (java.net.ConnectException ex) {
                log("Join room request failed (server offline): " + ex.getMessage());
                showError("Cannot connect to the server. The server may be offline or unreachable.");
            } catch (IOException ex) {
                log("Join room request failed: " + ex.getMessage());
                showError("Unable to join room: " + ex.getMessage());
            }
        }, "client-join-room");
        joinThread.setDaemon(true);
        joinThread.start();
    }

    private void suspendServerListenerForJoin() {
        Thread runningListener = serverMessageListenerThread;
        if (runningListener == null || !runningListener.isAlive()) {
            serverMessageListenerStarted = false;
            return;
        }

        log("Suspending server listener before join request.");
        runningListener.interrupt();
        serverMessageListenerStarted = false;
        serverMessageListenerThread = null;
    }

    /**
     * Starts the local ship-placement phase and concurrently waits for PP_START from the peer.
     *
     * <p>This method is intentionally shared by both roles:
     * one side calls it right after sending PP_READY, the other right after receiving PP_READY.
     */
    public void startPlacementHandshakeSequence() {
        if (placementSequenceStarted) {
            log("Placement handshake already started. Ignoring duplicate trigger.");
            return;
        }
        placementSequenceStarted = true;
        localPlacementDone = false;
        opponentStartReceived = false;

        log("Entering ship placement mode.");
        SwingUtilities.invokeLater(() -> {
            if (gameView == null) {
                log("Cannot start placement mode because game view is not initialized.");
                return;
            }
            gameView.startPlacementMode(() -> {
                localPlacementDone = true;
                log("Local placement completed. Sending PP_START to opponent.");
                completePlacementHandshakeAsync();
            });
        });

        messageHandlerService.waitForPeerStartMessage(PEER_START_TIMEOUT_SECONDS);
    }

    /**
     * Sends PP_START on a dedicated worker thread so the Swing event thread never blocks
     * on the peer connection monitor while another thread is reading from the same socket.
     */
    private void completePlacementHandshakeAsync() {
        Thread sendStartThread = new Thread(() -> {
            Optional.ofNullable(currentRoom)
                    .filter(room -> room.getRoomId() != null && !room.getRoomId().isBlank())
                    .ifPresent(room -> {
                        if (!peerConnectionService.isConnectedToPeer()) {
                            log("Skipping PP_START send: peer connection is no longer active.");
                            return;
                        }

                        try {
                            peerConnectionService.sendMessageToPeer(new MessageConstants.PPStart(
                                    client.getNodeId(),
                                    room.getRoomId(),
                                    client.getName(),
                                    room.getCurrentGrid() != null ? room.getCurrentGrid().getShipPositions() : java.util.List.of()));
                            log("PP_START sent.");
                        } catch (IOException ex) {
                            log("Failed to send PP_START: " + ex.getMessage());
                            if (currentRoom != null && peerConnectionService.isConnectedToPeer()) {
                                showError("Unable to notify opponent that placement is complete.");
                            }
                            return;
                        }

                        if (opponentStartReceived) {
                            hideGameLoadingIndicator();  // non dovrebbe servire perché dovrebbe essere già nascosto quando è arrivato il PP_START dell'avversario, ma meglio metterlo per sicurezza
                            log("Both players completed placement (local + remote PP_START).");
                            startBattleTurnPhase();
                        } else {
                            showGameLoadingIndicator("Waiting for opponent to finish ship placement...");
                            log("Waiting for opponent PP_START...");
                        }
                    });
        }, "client-send-pp-start");

        sendStartThread.setDaemon(true);
        sendStartThread.start();
    }

    public void showGameLoadingIndicator(String caption) {
        SwingUtilities.invokeLater(() -> {
            if (gameView != null) {
                gameView.showLoadingIndicator(caption);
            }
        });
    }

    public void hideGameLoadingIndicator() {
        SwingUtilities.invokeLater(() -> {
            if (gameView != null) {
                gameView.hideLoadingIndicator();
            }
        });
    }

    public boolean placePlayerShip(distributed.battleship.common.model.room.grid.Position start, int length, boolean horizontal) {
        return Optional.ofNullable(currentRoom)
                .map(PlayingRoom::getCurrentGrid)
                .filter(Objects::nonNull)
                .map(grid -> grid.placeShip(start, length, horizontal))
                .orElse(false);
    }

    public boolean fireShot(Position target) {
        return Optional.ofNullable(currentRoom)
                .map(PlayingRoom::getOpponentGrid)
                .filter(Objects::nonNull)
                .map(grid -> {
                    Cell.CellState state = grid.getCell(target);
                    if (state == Cell.CellState.HIT || state == Cell.CellState.SUNK) {
                        log("Shot ignored: cell (" + target.getX() + "," + target.getY() + ") was already targeted.");
                        return true;
                    }

                    boolean hit = currentRoom.fireShot(target);
                    boolean won = currentRoom.isLastShotWon();

                    try {
                        if (hit) {
                            peerConnectionService.sendMessageToPeer(new MessageConstants.PPHitted(
                                    client.getNodeId(),
                                    target.getX(),
                                    target.getY()));
                            log("[SEND] PP_HITTED at (" + target.getX() + "," + target.getY() + ")");
                        } else {
                            peerConnectionService.sendMessageToPeer(new MessageConstants.PPMissed(
                                    client.getNodeId(),
                                    target.getX(),
                                    target.getY()));
                            log("[SEND] PP_MISSED at (" + target.getX() + "," + target.getY() + ")");
                        }

                        if (won) {
                            peerConnectionService.sendMessageToPeer(new MessageConstants.PPWin(client.getNodeId()));
                            log("[SEND] PP_WIN");
                            notifyServerRoomClosed();
                            peerConnectionService.disconnectFromPeer();
                        }
                    } catch (IOException ex) {
                        log("Peer disconnected while sending shot result: " + ex.getMessage());
                        notifyServerPeerDisconnected();
                        peerConnectionService.disconnectFromPeer();
                        markRoomClosedByOpponent();
                        if (gameView != null) {
                            gameView.disableShotSelectionMode();
                            gameView.refreshView();
                            gameView.showMatchEndPopup(
                                    "Connection with the opponent was lost. Back to menu to start a new match.");
                        }
                        return false;
                    }

                    if (gameView != null) {
                        gameView.refreshView();
                        if (won) {
                            gameView.disableShotSelectionMode();
                            hideGameLoadingIndicator();
                            gameView.showMatchEndPopup("You won! Back to menu to start a new match.");
                            log("You won the match.");
                            return true;
                        }
                    }

                    if (!hit) {
                        startOpponentTurn();
                    }
                    return hit;
                })
                .orElse(false);
    }

    /**
     * Chooses a random available position to shoot at on the opponent's grid.
     *
     * @return a random available position, or null if no positions are available
     */
    public Position chooseRandomAvailableShotTarget() {
        return Optional.ofNullable(currentRoom)
                .map(PlayingRoom::getOpponentGrid)
                .filter(Objects::nonNull)
                .map(Grid::chooseRandomAvailableShotTarget)
                .orElse(null);
    }

    /**
     * Checks if the cell at the given position on the opponent's grid is already targeted.
     *
     * @param target the position to check
     * @return true if the cell is already HIT or SUNK, false otherwise
     */
    public boolean isAlreadyTargetedCell(Position target) {
        return Optional.ofNullable(currentRoom)
                .map(PlayingRoom::getOpponentGrid)
                .filter(Objects::nonNull)
                .map(grid -> grid.isAlreadyTargetedCell(target))
                .orElse(false);
    }

    /**
     * Handles placement-timeout requests from the view by delegating random placement to the grid model.
     */
    public int autoPlaceRemainingPlayerShips(int fromShipIndex, int[] fleetShipLengths) {
        return Optional.ofNullable(currentRoom)
                .map(PlayingRoom::getCurrentGrid)
                .filter(Objects::nonNull)
                .map(grid -> {
                    int[] fleet = (fleetShipLengths == null || fleetShipLengths.length == 0)
                            ? Grid.getDefaultFleetShipLengths()
                            : fleetShipLengths;
                    int placed = grid.placeRemainingShipsRandomly(fleet, fromShipIndex);
                    log("Auto placement requested from index " + fromShipIndex + ": placed " + placed + " ships.");
                    return placed;
                })
                .orElse(0);
    }

    public void shutdownClient() {
        notifyPeerExit(currentRoom);
        notifyServerClientDisconnected();
        peerConnectionService.disconnectFromPeer();
        serverConnectionService.disconnectFromServer();

        SwingUtilities.invokeLater(() -> {
            if (gameView != null) {
                gameView.dispose();
                gameView = null;
            }
            if (menuView != null) {
                menuView.dispose();
                menuView = null;
            }

            System.exit(0);
        });
    }

    public void returnToMenu() {
        // Snapshot log and reset state on the calling thread (may be EDT).
        String gameLogSnapshot = gameView != null ? gameView.getViewLog() : "";

        // Capture room snapshot BEFORE nullifying currentRoom
        PlayingRoom roomSnapshot = currentRoom;

        localPlacementDone = false;
        opponentStartReceived = false;
        placementSequenceStarted = false;
        currentRoom = null;

        // Switch views immediately so the user is not blocked waiting for network ops.
        SwingUtilities.invokeLater(() -> {
            if (menuView == null || !menuView.isDisplayable()) {
                menuView = createMenuView(gameLogSnapshot);
            } else {
                menuView.setViewLog(gameLogSnapshot);
            }

            menuView.setVisible(true);
            menuView.toFront();
            menuView.requestFocus();

            if (gameView != null) {
                gameView.setVisible(false);
                gameView.dispose();
                gameView = null;
            }
        });

        // Network teardown runs on a background thread so the EDT is never blocked.
        Thread cleanupThread = new Thread(() -> {
            log("Starting network cleanup after return to menu (abandon game).");
            notifyServerPeerDisconnected(roomSnapshot);
            peerConnectionService.disconnectFromPeer();
            log("Network cleanup completed.");
        }, "client-return-to-menu-cleanup");
        cleanupThread.setDaemon(true);
        cleanupThread.start();
    }

    private MenuView createMenuView(String logSnapshot) {
        MenuView view = new MenuView(debugMode);
        view.setJoinGameAction(() -> joinRoom(view.getJoinGamePlayerName()));
        view.setCloseAction(this::shutdownClient);
        view.setViewLog(logSnapshot);
        view.showBackupServers(getBackupServersSnapshot());
        return view;
    }

    private void notifyServerPeerDisconnected() {
        notifyServerPeerDisconnected(currentRoom);
    }

    private void notifyServerPeerDisconnected(PlayingRoom roomSnapshot) {
        notifyPeerExit(roomSnapshot);

        if (roomClosureNotified || roomSnapshot == null || roomSnapshot.getRoomId() == null || roomSnapshot.getRoomId().isBlank()) {
            return;
        }

        try {
            if (!serverConnectionService.isConnectedToServer()) {
                serverConnectionService.connectToServer(primaryServer);
            }

            serverConnectionService.sendMessageToServer(new MessageConstants.CSRoomInterrupted(
                    client.getNodeId(),
                    roomSnapshot.getRoomId(),
                    client.getName()));
            roomClosureNotified = true;
            log("Sent CS_ROOM_INTERRUPTED for room=" + roomSnapshot.getRoomId());
        } catch (IOException ex) {
            log("Failed to notify server about peer disconnection: " + ex.getMessage());
        }
    }

    private void notifyServerClientDisconnected() {
        try {
            if (!serverConnectionService.isConnectedToServer()) {
                serverConnectionService.connectToServer(primaryServer);
            }

            serverConnectionService.sendMessageToServer(new MessageConstants.CSClientExit(
                    client.getNodeId(),
                    client.getRoom() != null ? client.getRoom().getRoomId() : null,
                    client.getName()));
            log("Sent CS_CLIENT_EXIT for nodeId=" + client.getNodeId());
        } catch (IOException ex) {
            log("Failed to notify server about client disconnect: " + ex.getMessage());
        }
    }

    private void notifyPeerExit(PlayingRoom roomSnapshot) {
        if (roomSnapshot == null || roomSnapshot.getRoomId() == null || roomSnapshot.getRoomId().isBlank()) {
            return;
        }

        if (!peerConnectionService.isConnectedToPeer()) {
            return;
        }

        try {
            peerConnectionService.sendMessageToPeer(new MessageConstants.CSRoomInterrupted(
                    client.getNodeId(),
                    roomSnapshot.getRoomId(),
                    client.getName()));
            log("Sent CS_ROOM_INTERRUPTED to peer for room=" + roomSnapshot.getRoomId());
        } catch (IOException ex) {
            log("Failed to send CS_ROOM_INTERRUPTED to peer: " + ex.getMessage());
        }
    }

    public void notifyServerRoomClosed() {
        if (roomClosureNotified) {
            return;
        }

        Optional.ofNullable(currentRoom)
                .filter(room -> room.getRoomId() != null && !room.getRoomId().isBlank())
                .ifPresent(room -> {
                    try {
                        if (!serverConnectionService.isConnectedToServer()) {
                            serverConnectionService.connectToServer(primaryServer);
                        }

                        serverConnectionService.sendMessageToServer(new MessageConstants.CSRoomClosed(
                                client.getNodeId(),
                                room.getRoomId(),
                                client.getName()));

                        roomClosureNotified = true;
                    } catch (IOException ex) {
                        log("Failed to notify server about room closure: " + ex.getMessage());
                    }
                });
    }

    public void markRoomClosedByOpponent() {
        roomClosureNotified = true;
    }

    private List<BackupServer> getBackupServersSnapshot() {
        synchronized (backupServers) {
            return new ArrayList<>(backupServers);
        }
    }

    public void addBackupServer(BackupServer backup) {
        synchronized (backupServers) {
            backupServers.removeIf(b -> b.getNodeId().equals(backup.getNodeId()));
            backupServers.add(backup);
        }
        log("Backup server registered: " + backup.getNodeId() + "@" + backup.getIp() + ":" + backup.getConnectionPort());
        if (menuView != null) {
            SwingUtilities.invokeLater(() -> menuView.showBackupServers(getBackupServersSnapshot()));
        }
    }

    public void removeBackupServer(java.util.UUID backupNodeId) {
        synchronized (backupServers) {
            backupServers.removeIf(b -> b.getNodeId().equals(backupNodeId));
        }
        log("Backup server removed: " + backupNodeId);
        if (menuView != null) {
            SwingUtilities.invokeLater(() -> menuView.showBackupServers(getBackupServersSnapshot()));
        }
    }

    public void showError(String message) {
        log("ERROR: " + message);
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                menuView,
                message,
                "Client error",
                JOptionPane.ERROR_MESSAGE));
    }

    public void log(String message) {
        log("CLIENT", message);
    }

    public void log(String tag, String message) {
        if (menuView != null) {
            menuView.addLog(tag, message);
        }
        if (gameView != null) {
            gameView.addLog(tag, message);
        }
    }

    public Client getClient() { return client; }

    public boolean isDebugMode() { return debugMode; }

    public MenuView getMenuView() { return menuView; }

    private static int findAvailablePort() {
        try (java.net.ServerSocket probe = new java.net.ServerSocket(0)) {
            return probe.getLocalPort();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to allocate a local port", ex);
        }
    }

    public GameView getGameView() { return gameView; }

    public void setGameView(GameView gameView) { this.gameView = gameView; }

    public PlayingRoom getCurrentRoom() { return currentRoom; }

    public void setCurrentRoom(PlayingRoom currentRoom) {
        this.currentRoom = currentRoom;
        client.setRoom(currentRoom);
        roomClosureNotified = false;
    }

    public boolean isLocalPlacementDone() { return localPlacementDone;}

    public void setOpponentStartReceived(boolean opponentStartReceived) {
        this.opponentStartReceived = opponentStartReceived;
    }

    public void startBattleTurnPhase() {
        Optional.ofNullable(currentRoom)
                .filter(room -> room.getStartingPlayerId() != null)
                .ifPresent(room -> {
                    if (client.getNodeId().equals(room.getStartingPlayerId())) {
                        startLocalTurn();
                    } else {
                        startOpponentTurn();
                    }
                });
    }

    public void startLocalTurn() {
        hideGameLoadingIndicator();
        if (gameView != null) {
            gameView.enableShotSelectionMode();
            gameView.addLog("CLIENT", "Your turn: select a cell on the opponent board.");
        }
    }

    public void startOpponentTurn() {
        if (gameView != null) {
            gameView.disableShotSelectionMode();
        }
        showGameLoadingIndicator("Waiting for opponent shot...");
        messageHandlerService.waitForNextBattleMessage();
    }

    private void handleServerDisconnected() {

        log("Server disconnected. Attempting automatic reconnection...");

        Thread reconnectThread = new Thread(this::attemptServerReconnection, "client-reconnect");
        reconnectThread.setDaemon(true);
        reconnectThread.start();
    }

    /**
     * Tries to reach the server again after a disconnection.
     * <ul>
     *   <li>If no backup servers are known: retries the original primary.</li>
     *   <li>If backup servers are known: tries each one in order on {@link Config#PRIMARY_SERVER_PORT},
     *       assuming the order-1 backup has promoted itself.</li>
     * </ul>
     * On success the server listener is restarted transparently.
     * On total failure a warning dialog is shown.
     */
    private void attemptServerReconnection() {
        List<BackupServer> backupSnapshot = getBackupServersSnapshot();
        backupSnapshot.sort(java.util.Comparator.comparingInt(BackupServer::getOrder));

        if (!backupSnapshot.isEmpty()) {
            for (BackupServer backup : backupSnapshot) {
                if (tryReconnectToNewPrimary(backup)) return;
            }
        } else {
            if (tryReconnectToOldPrimary()) return;
        }

        // All targets exhausted — show warning
        log("All reconnection attempts failed. Server is unreachable.");
        SwingUtilities.invokeLater(() -> {
            java.awt.Window parent = gameView != null ? gameView : menuView;
            JOptionPane.showMessageDialog(
                    parent,
                    "The server has disconnected and could not be reached.\nPlease wait for the server to come back online.",
                    "Server disconnected",
                    JOptionPane.WARNING_MESSAGE);
        });
    }

    private boolean tryReconnectToNewPrimary(BackupServer backup) {
        String ip = backup.getIp();
        PrimaryServer candidate = new PrimaryServer(ip, Config.PRIMARY_SERVER_PORT, Config.BACKUP_SERVER_PORT);
        log("Trying to connect to backup (order=" + backup.getOrder() + ") at " + ip + ":" + Config.PRIMARY_SERVER_PORT + " as new primary...");
        if (tryReconnect(candidate, "backup " + ip + " (order=" + backup.getOrder() + ")")) {
            removeBackupServer(backup.getNodeId());
            return true;
        }
        return false;
    }

    private boolean tryReconnectToOldPrimary() {
        log("No backup servers known. Retrying current server " + primaryServer.getIp() + ":" + primaryServer.getClientPort() + "...");
        return tryReconnect(primaryServer, "primary " + primaryServer.getIp() + ":" + primaryServer.getClientPort());
    }

    /**
     * Attempts to reconnect to {@code target} up to {@link Config#CLIENT_RECONNECT_MAX_ATTEMPTS} times,
     * waiting {@link Config#CLIENT_RECONNECT_DELAY_SECONDS} seconds between each try.
     *
     * @return {@code true} if connection was established, {@code false} otherwise
     */
    private boolean tryReconnect(PrimaryServer target, String label) {
        for (int attempt = 1; attempt <= Config.CLIENT_RECONNECT_MAX_ATTEMPTS; attempt++) {
            try {
                Thread.sleep(Config.CLIENT_RECONNECT_DELAY_SECONDS * 1000L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return false;
            }
            try {
                log("Reconnect attempt " + attempt + "/" + Config.CLIENT_RECONNECT_MAX_ATTEMPTS + " to " + label + "...");
                serverConnectionService.disconnectFromServer();
                serverConnectionService.connectToServer(target);
                log("Reconnected to " + label + " successfully.");
                try {
                    String roomId = currentRoom != null ? currentRoom.getRoomId() : null;
                    serverConnectionService.sendMessage(new MessageConstants.CSClientReconnected(
                            client.getNodeId(), roomId, client.getName()));
                } catch (IOException sendEx) {
                    log("Failed to send CS_CLIENT_RECONNECTED: " + sendEx.getMessage());
                }
                ensureServerMessageListenerRunning();
                return true;
            } catch (IOException ex) {
                log("Attempt " + attempt + " to " + label + " failed: " + ex.getMessage());
            }
        }
        return false;
    }

    public void ensureServerMessageListenerRunning() {
        if (serverMessageListenerStarted) {
            return;
        }

        synchronized (this) {
            if (serverMessageListenerStarted) {
                return;
            }
            serverMessageListenerStarted = true;
        }

        Thread serverListenerThread = new Thread(() -> {
            try {
                while (serverConnectionService.isConnectedToServer() && !Thread.currentThread().isInterrupted()) {
                    try {
                        MessageConstants.MessageTuple serverMessage = serverConnectionService.waitForMessageFromServer();
                        messageHandlerService.handleMessage(serverMessage);
                    } catch (java.net.SocketTimeoutException timeout) {
                        // Continue loop to check interrupt flag and restart condition
                        continue;
                    }
                }
            } catch (IOException ex) {
                log("Server message listener stopped: " + ex.getMessage());
                handleServerDisconnected();
            } finally {
                serverMessageListenerStarted = false;
                serverMessageListenerThread = null;
            }
        }, "client-server-message-listener");

        serverListenerThread.setDaemon(true);
        serverMessageListenerThread = serverListenerThread;
        serverListenerThread.start();
    }

}