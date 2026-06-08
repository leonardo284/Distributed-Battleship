package distributed.battleship.server.primary.service;

import distributed.battleship.common.model.client.Client;
import distributed.battleship.common.model.message.MessageConstants;
import distributed.battleship.common.model.node.Node;
import distributed.battleship.common.model.room.Room;
import distributed.battleship.common.model.server.BackupServer;
import distributed.battleship.common.service.MessageHandlerService;
import distributed.battleship.server.primary.controller.PrimaryServerController;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Handles incoming protocol messages for the server by dispatching to specific handlers.
 * Analogous to MessageHandlerService in the client package.
 */
public class ServerMessageHandlerService implements MessageHandlerService {

    private static final int CLIENT_MESSAGE_TIMEOUT_SECONDS = 60;
    private static final int HEARTBEAT_TIMEOUT_SECONDS = 5;

    private final PrimaryServerController primaryServerController;
    private final ClientConnectionService clientConnectionService;
    private final BackupConnectionService backupConnectionService;
    private final String logTag;

    /**
     * Creates a server message handler service.
     *
     * @param primaryServerController the primary server controller
     * @param clientConnectionService the client communication service used by the primary server controller
     */
    public ServerMessageHandlerService(PrimaryServerController primaryServerController, ClientConnectionService clientConnectionService) {
        this.primaryServerController = primaryServerController;
        this.clientConnectionService = clientConnectionService;
        this.backupConnectionService = null;
        this.logTag = "CLIENT";
    }

    /**
     * Creates a server message handler service bound to a backup-server connection.
     *
     * @param primaryServerController the primary server controller
     * @param backupConnectionService the backup communication service used by the primary server controller
     */
    public ServerMessageHandlerService(PrimaryServerController primaryServerController, BackupConnectionService backupConnectionService) {
        this.primaryServerController = primaryServerController;
        this.clientConnectionService = null;
        this.backupConnectionService = backupConnectionService;
        this.logTag = "BACKUP";
    }

    @Override
    public void handleMessage(MessageConstants.MessageTuple msg) {
        log("Received message: " + msg.getType());
        switch (msg.getType()) {
            case CS_RESPONSE_HEARTBEAT -> handleHeartbeatResponse((MessageConstants.CSResponseHeartbeat) msg);
            case CS_REQUEST_JOIN_ROOM -> handleJoinRoomRequest((MessageConstants.CSRequestJoinRoom) msg);
            case CS_ROOM_OPENED -> handleRoomOpened((MessageConstants.CSRoomOpened) msg);
            case CS_ROOM_CLOSED -> handleRoomClosed((MessageConstants.CSRoomClosed) msg);
            case CS_ROOM_INTERRUPTED -> handleRoomInterrupted((MessageConstants.CSRoomInterrupted) msg);
            case CS_ROOM_TIMEOUT -> handleRoomTimeout((MessageConstants.CSRoomTimeout) msg);
            case CS_ROOM_TIMEOUT_ACK -> handleRoomTimeoutAck((MessageConstants.CSRoomTimeoutAck) msg);
            case CS_CLIENT_EXIT -> handleClientExit((MessageConstants.CSClientExit) msg);
            case CS_CLIENT_RECONNECTED -> handleClientReconnected((MessageConstants.CSClientReconnected) msg);
            case SS_HELLO_FROM_BACKUP -> handleHelloFromBackup((MessageConstants.SSHelloFromBackup) msg);
            case SS_BACKUP_EXIT -> handleBackupExit((MessageConstants.SSBackupExit) msg);
            case SS_ACK -> handleSsAck((MessageConstants.SSAck) msg);
            default -> log("Unhandled message type: " + msg.getType());
        }
    }

    private void handleClientReconnected(MessageConstants.CSClientReconnected msg) {
        if (clientConnectionService == null) {
            log("Ignoring CS_CLIENT_RECONNECTED: no client connection service available.");
            return;
        }
        Node clientNode = clientConnectionService.getClientNode();
        String ip = clientNode != null ? clientNode.getIp() : "unknown";
        Client client = new Client(msg.senderNodeId(), ip, msg.playerName());
        primaryServerController.registerClientConnection(msg.senderNodeId(), clientConnectionService, client);
        log("Client reconnected: " + msg.playerName() + " (nodeId=" + msg.senderNodeId() + ", room=" + msg.roomId() + ")");
    }

    private void handleHelloFromBackup(MessageConstants.SSHelloFromBackup helloFromBackup) {
        if (backupConnectionService == null) {
            log("Ignoring SS_HELLO_FROM_BACKUP: backup connection service is not configured.");
            return;
        }

        primaryServerController.registerBackupConnection(helloFromBackup, backupConnectionService);
        primaryServerController.broadcastToAllClients(new MessageConstants.CSBackupJoined(
                helloFromBackup.senderNodeId(),
                helloFromBackup.backupIp(),
                helloFromBackup.backupPort()));
        log("Broadcasted CS_BACKUP_JOINED for backup "
                + helloFromBackup.backupIp() + ":" + helloFromBackup.backupPort());
    }

    private void handleBackupExit(MessageConstants.SSBackupExit backupExit) {
        if (backupConnectionService == null) {
            log("Ignoring SS_BACKUP_EXIT: backup connection service is not configured.");
            return;
        }

        primaryServerController.unregisterBackupConnection(backupExit.senderNodeId());
        primaryServerController.broadcastToAllClients(new MessageConstants.CSBackupExit(
                backupExit.senderNodeId(),
                backupExit.backupIp(),
                backupExit.backupPort()));
        log("Broadcasted CS_BACKUP_EXIT for backup "
                + backupExit.backupIp() + ":" + backupExit.backupPort());
    }

    private void handleSsAck(MessageConstants.SSAck ack) {
        primaryServerController.notifyBackupAck(ack.senderNodeId());
        log("SS_ACK processed for backup nodeId=" + ack.senderNodeId());
    }

    private void handleJoinRoomRequest(MessageConstants.CSRequestJoinRoom req) {
        Client joiningPlayer = new Client(
                req.senderNodeId(),
                req.peerIp(),
                req.playerName());
        joiningPlayer.setPeerConnectionPort(req.peerConnectionPort());
        joiningPlayer.setServerConnectionPort(req.serverConnectionPort());

        primaryServerController.registerClientConnection(req.senderNodeId(), clientConnectionService, joiningPlayer);

        java.util.List<distributed.battleship.common.model.server.BackupServer> backups =
                primaryServerController.getConnectedBackupsSnapshot();

        Room availableRoom = primaryServerController.findRandomAvailableRoom();

        if (availableRoom != null) {
            availableRoom.setPlayerTwo(joiningPlayer);
            Client playerOne = availableRoom.getPlayerOne();

            java.util.UUID startingPlayerId = Math.random() < 0.5
                    ? playerOne.getNodeId()
                    : joiningPlayer.getNodeId();

            log("Player " + req.playerName() + " joined room " + availableRoom.getRoomId()
                    + " – playerOneId=" + playerOne.getNodeId()
                    + ", playerTwoId=" + joiningPlayer.getNodeId()
                    + ", startingPlayerId=" + startingPlayerId);

            sendMessage(new MessageConstants.CSResponseJoinRoom(
                    primaryServerController.getServer().getNodeId(),
                    availableRoom.getRoomId(),
                    playerOne.getName(),
                    playerOne.getIp(),
                    playerOne.getPeerConnectionPort(),
                    playerOne.getNodeId(),
                    startingPlayerId,
                    backups));
        } else {
            Room newRoom = new Room(joiningPlayer, null);
            primaryServerController.getServer().addRoom(newRoom);
            primaryServerController.updateRoomsCount();
            log("No available room. Created room " + newRoom.getRoomId()
                    + " for player " + req.playerName());

            sendMessage(new MessageConstants.CSResponseCreateRoom(primaryServerController.getServer().getNodeId(), newRoom.getRoomId(), backups));
        }
    }

    private void handleRoomOpened(MessageConstants.CSRoomOpened req) {
        log("Room opened: room=" + req.roomId() + " player=" + req.playerName());
    }

    private void handleRoomClosed(MessageConstants.CSRoomClosed req) {
        primaryServerController.removeRoomById(req.roomId());
    }

    private void handleRoomInterrupted(MessageConstants.CSRoomInterrupted req) {
        log("Received CS_ROOM_INTERRUPTED from nodeId=" + req.senderNodeId() + " for room=" + req.roomId());
        Room room = primaryServerController.findRoomById(req.roomId());
        Client opponent = room != null ? room.getOpponent(req.senderNodeId()) : null;
        java.util.UUID opponentNodeId = opponent != null ? opponent.getNodeId() : null;

        primaryServerController.removeRoomById(req.roomId());

        ClientConnectionService opponentConnection = primaryServerController.getClientConnection(opponentNodeId);
        if (opponentConnection == null || !opponentConnection.isConnected()) {
            log("Opponent connection not active for room=" + req.roomId() + " nodeId=" + opponentNodeId);
            return;
        }

        try {
            opponentConnection.sendMessage(new MessageConstants.CSRoomInterrupted(
                    primaryServerController.getServer().getNodeId(),
                    req.roomId(),
                    req.playerName()));
            log("Forwarded CS_ROOM_INTERRUPTED to opponent for room=" + req.roomId());
        } catch (java.io.IOException ex) {
            log("Failed to forward CS_ROOM_INTERRUPTED to opponent: " + ex.getMessage());
        }
    }

    private void handleRoomTimeout(MessageConstants.CSRoomTimeout req) {
        Room room = primaryServerController.findRoomById(req.roomId());
        Client opponent = room != null ? room.getOpponent(req.senderNodeId()) : null;
        java.util.UUID opponentNodeId = opponent != null ? opponent.getNodeId() : null;

        ClientConnectionService opponentConnection = primaryServerController.getClientConnection(opponentNodeId);
        if (opponentConnection == null || !opponentConnection.isConnected()) {
            log("Opponent connection not active for room=" + req.roomId() + " nodeId=" + opponentNodeId);
            return;
        }

        try {
            opponentConnection.sendMessage(new MessageConstants.CSRoomTimeout(
                    primaryServerController.getServer().getNodeId(),
                    req.roomId(),
                    req.playerName()));
            log("Sent CS_ROOM_TIMEOUT to opponent for room=" + req.roomId());
        } catch (java.io.IOException ex) {
            log("Failed to send CS_ROOM_TIMEOUT to opponent: " + ex.getMessage());
            return;
        }

        Thread ackWaitThread = new Thread(() -> {
            try {
                FutureTask<MessageConstants.MessageTuple> waitTask = new FutureTask<>(opponentConnection::waitForMessage);
                Thread waitThread = new Thread(waitTask, "server-ack-wait-io-" + opponentNodeId);
                waitThread.setDaemon(true);
                waitThread.start();

                MessageConstants.MessageTuple response = waitTask.get(HEARTBEAT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (response.getType() == MessageConstants.MessageType.CS_ROOM_TIMEOUT_ACK
                        && opponentNodeId.equals(response.senderNodeId())
                        && req.roomId().equals(((MessageConstants.CSRoomTimeoutAck) response).roomId())) {
                    log("CS_ROOM_TIMEOUT_ACK received in time from opponent nodeId=" + opponentNodeId);
                } else {
                    log("Invalid ACK response from opponent nodeId=" + opponentNodeId + " type=" + response.getType());
                    primaryServerController.removeActiveClientByNodeId(opponentNodeId, "invalid ACK response");
                }
            } catch (TimeoutException ex) {
                log("ACK timeout for opponent nodeId=" + opponentNodeId);
                primaryServerController.removeActiveClientByNodeId(opponentNodeId, "ACK timeout");
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                log("ACK wait interrupted for nodeId=" + opponentNodeId);
            } catch (ExecutionException ex) {
                log("ACK wait failed for nodeId=" + opponentNodeId + ": " + ex.getMessage());
                primaryServerController.removeActiveClientByNodeId(opponentNodeId, "ACK wait failed");
            }
        }, "server-ack-wait-" + opponentNodeId);

        ackWaitThread.setDaemon(true);
        ackWaitThread.start();
    }

    private void handleRoomTimeoutAck(MessageConstants.CSRoomTimeoutAck req) {
        log("Received CS_ROOM_TIMEOUT_ACK from nodeId=" + req.senderNodeId() + " for room=" + req.roomId() + " - opponent is still connected");
    }

    private void handleClientExit(MessageConstants.CSClientExit req) {
        primaryServerController.removeRoomById(req.roomId());
        log("Client exited: room=" + req.roomId() + " player=" + req.playerName());
    }

    private void handleHeartbeatResponse(MessageConstants.CSResponseHeartbeat response) {
        log("Heartbeat response received on main handler from nodeId=" + response.senderNodeId());
    }

    public void waitForNextClientMessage() {
        waitForClientMessageWithTimeout(
                CLIENT_MESSAGE_TIMEOUT_SECONDS,
                "server-client-wait",
                "Client Message",
                this::handleMessage);
    }

    private void waitForClientMessageWithTimeout(
            int timeoutSeconds,
            String threadName,
            String expectedMessageName,
            Consumer<MessageConstants.MessageTuple> onMessage) {
        clientConnectionService.waitForMessageWithTimeout(
                timeoutSeconds,
                threadName,
                onMessage,
                (timeout, threadNameCallback) -> handleClientMessageTimeout(timeout, expectedMessageName));
    }

    private void handleClientMessageTimeout(
            int timeoutSeconds,
            String expectedMessageName) {
        log("Timeout while waiting for " + expectedMessageName + " after " + timeoutSeconds + " seconds.");
    }

    private void sendMessage(MessageConstants.MessageTuple message) {
        if (clientConnectionService == null) {
            log("Failed to send message " + message.getType() + ": client connection service is not configured.");
            return;
        }
        try {
            clientConnectionService.sendMessage(message);
        } catch (java.io.IOException ex) {
            log("Failed to send message " + message.getType() + ": " + ex.getMessage());
        }
    }

    private void log(String message) {
        primaryServerController.log(logTag, message);
    }
}
