package distributed.battleship.client.service;

import distributed.battleship.client.controller.ClientController;
import distributed.battleship.common.model.client.Client;
import distributed.battleship.common.model.client.PlayingRoom;
import distributed.battleship.client.view.GameView;
import distributed.battleship.common.model.message.MessageConstants;
import distributed.battleship.common.model.room.grid.Grid;
import distributed.battleship.common.model.room.grid.Position;
import distributed.battleship.common.model.server.BackupServer;
import distributed.battleship.common.service.MessageHandlerService;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Handles incoming protocol messages for the client by dispatching to specific handlers.
 */
public class ClientMessageHandlerService implements MessageHandlerService {

    private static final int PEER_CONNECT_TIMEOUT_SECONDS = 5;
    private static final int BATTLE_TURN_GRACE_SECONDS = 5;
    private static final int BATTLE_TURN_TIMEOUT_SECONDS = GameView.SHOT_SELECTION_TIME_SECONDS + BATTLE_TURN_GRACE_SECONDS;

    public void waitForNextBattleMessage() {
        waitForPeerMessageWithTimeout(BATTLE_TURN_TIMEOUT_SECONDS, "pp-battle-wait", "PP_HITTED or PP_MISSED", this::handleMessage);
    }

    public void waitForPeerStartMessage(int timeoutSeconds) {
        waitForPeerMessageWithTimeout(timeoutSeconds, "pp-start-wait", "PP_START", this::handleMessage);
    }

    private final ClientController clientController;
    private final ServerConnectionService serverConnectionService;
    private final PeerConnectionService peerConnectionService;

    public ClientMessageHandlerService(
            ClientController clientController,
            ServerConnectionService serverConnectionService,
            PeerConnectionService peerConnectionService) {
        this.clientController = clientController;
        this.serverConnectionService = serverConnectionService;
        this.peerConnectionService = peerConnectionService;
    }

    @Override
    public void handleMessage(MessageConstants.MessageTuple msg) {
        clientController.log("Received message: " + msg.getType());
        switch (msg.getType()) {
            case CS_REQUEST_HEARTBEAT -> handleServerHeartbeatRequest((MessageConstants.CSRequestHeartbeat) msg);
            case CS_RESPONSE_CREATE_ROOM -> handleCreateRoomResponse((MessageConstants.CSResponseCreateRoom) msg);
            case CS_RESPONSE_JOIN_ROOM -> handleJoinRoomResponse((MessageConstants.CSResponseJoinRoom) msg);
            case PP_CONNECT -> handlePeerConnectMessage((MessageConstants.PPConnect) msg);
            case PP_READY -> handlePeerReadyMessage((MessageConstants.PPReady) msg);
            case PP_START -> handlePeerStartMessage((MessageConstants.PPStart) msg);
            case PP_SHOT -> handlePeerShotMessage((MessageConstants.PPShot) msg);
            case PP_HITTED -> handlePeerHittedMessage((MessageConstants.PPHitted) msg);
            case PP_MISSED -> handlePeerMissedMessage((MessageConstants.PPMissed) msg);
            case PP_WIN -> handlePeerWinMessage((MessageConstants.PPWin) msg);
            case PP_EXIT -> handlePeerExitMessage((MessageConstants.PPExit) msg);
            case CS_ROOM_TIMEOUT -> handleRoomTimeoutMessage((MessageConstants.CSRoomTimeout) msg);
            case CS_ROOM_INTERRUPTED -> handleRoomInterruptedMessage((MessageConstants.CSRoomInterrupted) msg);
            case CS_BACKUP_JOINED -> handleBackupJoined((MessageConstants.CSBackupJoined) msg);
            case CS_BACKUP_EXIT -> handleBackupExit((MessageConstants.CSBackupExit) msg);
            default -> {
                clientController.log("Unhandled message type: " + msg.getType());
                clientController.showError("Unexpected message: " + msg.getType());
            }
        }
    }

    private void handleServerHeartbeatRequest(MessageConstants.CSRequestHeartbeat message) {
        try {
            serverConnectionService.sendMessageToServer(new MessageConstants.CSResponseHeartbeat(
                    clientController.getClient().getNodeId()));
            clientController.log("[SEND] CS_RESPONSE_HEARTBEAT to server (request from " + message.senderNodeId() + ")");
        } catch (IOException ex) {
            clientController.log("Failed to send CS_RESPONSE_HEARTBEAT: " + ex.getMessage());
        }
    }

    private void handleCreateRoomResponse(MessageConstants.CSResponseCreateRoom response) {
        String roomId = response.roomId();
        clientController.log("Room created successfully. roomId=" + roomId + ". Waiting for an opponent...");

        if (response.connectedBackups() != null) {
            response.connectedBackups().forEach(clientController::addBackupServer);
        }

        PlayingRoom room = new PlayingRoom(roomId, clientController.getClient(), null, new Grid(), new Grid());
        clientController.setCurrentRoom(room);

        SwingUtilities.invokeLater(() -> {
            String menuLogSnapshot = clientController.getMenuView() != null ? clientController.getMenuView().getViewLog() : "";
            if (clientController.getMenuView() != null) {
                clientController.getMenuView().setVisible(false);
            }

            GameView gameView = new GameView(clientController, clientController.isDebugMode());
            gameView.prependLog(menuLogSnapshot);
            gameView.setPlacementHandlers(clientController::placePlayerShip, clientController::autoPlaceRemainingPlayerShips);
            gameView.setShotHandler(clientController::fireShot);
            gameView.setCloseAction(clientController::shutdownClient);
            gameView.setBackToMenuAction(clientController::returnToMenu);
            gameView.setVisible(true);
            clientController.setGameView(gameView);
            clientController.showGameLoadingIndicator("Waiting for an opponent...");
            gameView.addLog("CLIENT", "Room created. Room ID: " + roomId + " – waiting for an opponent to join...");
        });

        Thread waitPeerThread = new Thread(() -> {
            try {
                peerConnectionService.waitForPeerConnection();
                // Not a timeout scenario – just wait indefinitely for the opponent to connect after creating the room.
                MessageConstants.MessageTuple message = peerConnectionService.waitForMessageFromPeer();
                handleMessage(message);
            } catch (IOException ex) {
                clientController.log("Peer wait failed: " + ex.getMessage());
                clientController.showError("Unable to receive peer message: " + ex.getMessage());
            }
        }, "client-wait-peer-message");
        waitPeerThread.setDaemon(true);
        waitPeerThread.start();
        clientController.ensureServerMessageListenerRunning();
    }

    private void handleJoinRoomResponse(MessageConstants.CSResponseJoinRoom response) {
        if (response.connectedBackups() != null) {
            response.connectedBackups().forEach(clientController::addBackupServer);
        }

        Client opponent = new Client(
                response.opponentNodeId(),
                response.opponentIp(),
                response.opponentName());
        opponent.setPeerConnectionPort(response.opponentPort());

        PlayingRoom room = clientController.getCurrentRoom();
        if (room == null) {
            room = new PlayingRoom(response.roomId(), clientController.getClient(), opponent, new Grid(), new Grid());
            clientController.setCurrentRoom(room);
        } else {
            room.setCurrentClient(clientController.getClient());
            room.setOpponent(opponent);
            if (room.getCurrentGrid() == null) {
                room.setCurrentGrid(new Grid());
            }
            if (room.getOpponentGrid() == null) {
                room.setOpponentGrid(new Grid());
            }
        }
        room.setStartingPlayerId(response.startingPlayerId());
        final PlayingRoom finalRoom = room;

        try {
            FutureTask<Void> connectPeerTask = new FutureTask<>(() -> {
                int attempts = 0;
                while (attempts < 5) {
                    try {
                        clientController.log("Connecting to opponent peer " + opponent.getIp() + ":" + opponent.getPeerConnectionPort());
                        peerConnectionService.connectToPeer(opponent);
                        return null;
                    } catch (IOException e) {
                        attempts++;
                        Thread.sleep(1000);
                        clientController.log("Attempt " + attempts + " to connect to peer failed, retrying...");
                    }
                }
                throw new TimeoutException("Failed to connect to peer after multiple attempts");
            });

            Thread connectPeerThread = new Thread(connectPeerTask, "client-peer-connect");
            connectPeerThread.setDaemon(true);
            connectPeerThread.start();

            connectPeerTask.get(PEER_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            peerConnectionService.sendMessageToPeer(new MessageConstants.PPConnect(
                    clientController.getClient().getNodeId(),
                    clientController.getClient().getName(),
                    clientController.getClient().getIp(),
                    clientController.getClient().getPeerConnectionPort(),
                    room.getStartingPlayerId()));
            clientController.log("PP_CONNECT sent. Waiting for PP_READY from opponent.");
            waitForPeerMessageWithTimeout(
                    ClientController.PEER_START_TIMEOUT_SECONDS,
                    "pp-ready-wait",
                    "PP_READY",
                    this::handleMessage
            );

            serverConnectionService.sendMessageToServer(new MessageConstants.CSRoomOpened(
                    clientController.getClient().getNodeId(),
                    response.roomId(),
                    clientController.getClient().getName()));
        } catch (TimeoutException ex) {
            peerConnectionService.disconnectFromPeer();
            clientController.log("Peer connection timeout after " + PEER_CONNECT_TIMEOUT_SECONDS + " seconds.");
            clientController.showError("Unable to connect to opponent within timeout.");
            return;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            peerConnectionService.disconnectFromPeer();
            throw new IllegalStateException("Peer connection interrupted", ex);
        } catch (ExecutionException ex) {
            peerConnectionService.disconnectFromPeer();
            throw new IllegalStateException("Unable to establish peer connection", ex.getCause());
        } catch (IOException ex) {
            peerConnectionService.disconnectFromPeer();
            throw new IllegalStateException("Unable to establish peer connection", ex);
        }

        clientController.log("Joined room " + response.roomId() + " as " + clientController.getClient().getName());

        SwingUtilities.invokeLater(() -> {
            if (clientController.getGameView() == null) {
                String menuLogSnapshot = clientController.getMenuView() != null ? clientController.getMenuView().getViewLog() : "";
                if (clientController.getMenuView() != null) {
                    clientController.getMenuView().setVisible(false);
                }

                GameView gameView = new GameView(clientController, clientController.isDebugMode());
                gameView.prependLog(menuLogSnapshot);
                gameView.setPlacementHandlers(clientController::placePlayerShip, clientController::autoPlaceRemainingPlayerShips);
                gameView.setShotHandler(clientController::fireShot);
                gameView.setCloseAction(clientController::shutdownClient);
                gameView.setBackToMenuAction(clientController::returnToMenu);
                gameView.setVisible(true);
                clientController.setGameView(gameView);
            }
            clientController.getGameView().refreshView();
            clientController.hideGameLoadingIndicator();
            clientController.getGameView().addLog("CLIENT", clientController.getClient().getNodeId().equals(finalRoom.getStartingPlayerId())
                    ? "Match loaded. You start first."
                    : "Match loaded. Opponent starts first.");
        });

        clientController.ensureServerMessageListenerRunning();
    }

    private void handlePeerConnectMessage(MessageConstants.PPConnect peerConnect) {
        PlayingRoom room = clientController.getCurrentRoom();
        if (room == null) {
            clientController.log("PP_CONNECT received without an active room");
            return;
        }

        clientController.log("[RECV] PP_CONNECT from " + peerConnect.playerName() + " at " + peerConnect.ip() + ":" + peerConnect.port());

        room.setStartingPlayerId(peerConnect.startingPlayerId());
        Client opponent = new Client(
                peerConnect.senderNodeId(),
                peerConnect.ip(),
                peerConnect.playerName());
        opponent.setPeerConnectionPort(peerConnect.port());

        if (room.getCurrentClient() == null) {
            room.setCurrentClient(clientController.getClient());
        }
        if (room.getOpponent() == null) {
            room.setOpponent(opponent);
        }

        try {
            peerConnectionService.sendMessageToPeer(new MessageConstants.PPReady(
                    clientController.getClient().getNodeId(),
                    room.getRoomId(),
                    clientController.getClient().getName()));
            clientController.log("PP_READY sent after receiving PP_CONNECT.");
        } catch (IOException ex) {
            clientController.log("Failed to send PP_READY: " + ex.getMessage());
            clientController.showError("Unable to notify opponent readiness.");
            return;
        }

        clientController.startPlacementHandshakeSequence();

        SwingUtilities.invokeLater(() -> {
            clientController.showGameLoadingIndicator(peerConnect.playerName() + " is connecting...");
            if (clientController.getGameView() != null) {
                clientController.getGameView().refreshView();
                clientController.getGameView().addLog("CLIENT", peerConnect.playerName() + " is connecting...");
                clientController.getGameView().addLog("CLIENT", clientController.getClient().getNodeId().equals(room.getStartingPlayerId())
                        ? "You start first."
                        : "Opponent starts first.");
            }
            clientController.hideGameLoadingIndicator();
        });
    }

    private void handlePeerReadyMessage(MessageConstants.PPReady peerReady) {
        if (clientController.getCurrentRoom() == null) {
            clientController.log("PP_READY received without an active room.");
            return;
        }

        clientController.log("PP_READY received from " + peerReady.playerName() + ". Starting placement sequence.");
        clientController.startPlacementHandshakeSequence();
    }
}

