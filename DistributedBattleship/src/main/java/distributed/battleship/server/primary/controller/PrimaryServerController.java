package distributed.battleship.server.primary.controller;

import distributed.battleship.common.config.Config;
import distributed.battleship.common.model.client.Client;
import distributed.battleship.common.model.message.MessageConstants;
import distributed.battleship.common.model.room.Room;
import distributed.battleship.common.model.server.BackupServer;
import distributed.battleship.common.model.server.PrimaryServer;
import distributed.battleship.server.primary.service.BackupConnectionService;
import distributed.battleship.server.primary.service.ClientConnectionService;
import distributed.battleship.server.primary.service.ServerMessageHandlerService;
import distributed.battleship.server.view.ServerView;
import distributed.battleship.server.view.SimpleServerView;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Controls the server lifecycle and handles all client TCP connections.
 * Each client connection is managed in a dedicated daemon thread so the
 * server can serve multiple clients concurrently.
 */
public class PrimaryServerController {

    private final PrimaryServer server;
    private final ServerView serverView;
    private ServerSocket clientServerSocket;
    private ServerSocket backupServerSocket;
    private final AtomicInteger connectedClientsCount = new AtomicInteger(0);
    private final AtomicInteger connectedBackupsCount = new AtomicInteger(0);
    private final AtomicInteger backupOrderCounter = new AtomicInteger(0);
    private final ConcurrentMap<UUID, ClientConnectionService> clientConnectionsByNodeId = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Client> clientsByNodeId = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, BackupConnectionService> backupConnectionsByNodeId = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, BackupServer> backupServersByNodeId = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Integer> backupOrderByNodeId = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, ServerMessageHandlerService> backupMessageHandlersByNodeId = new ConcurrentHashMap<>();

    /**
     * Creates a server controller for the given server model.
     *
     * @param server Domain model carrying the IP and port to bind to
     */
    public PrimaryServerController(PrimaryServer server) {
        this.server = server;
        this.serverView = new SimpleServerView();
    }

    /**
     * Creates a server controller that reuses an already-open view (e.g. when a backup promotes to primary).
     *
     * @param server       Domain model carrying the IP and port to bind to
     * @param existingView The view window to reuse instead of opening a new one
     */
    public PrimaryServerController(PrimaryServer server, ServerView existingView) {
        this.server = server;
        this.serverView = existingView;
    }

    /**
     * Starts the server: shows the GUI, then begins accepting client connections
     * in an infinite loop on a background thread.
     */
    public void start() {
        serverView.start();
        startWithExistingView();
    }

    /**
     * Starts the server reusing an already-open view (called when a backup promotes to primary).
     * The view is updated in-place: backup-order label is hidden and the server type label is refreshed.
     */
    public void startWithExistingView() {
        serverView.hideBackupOrder();
        serverView.showServerType(server.getServerType());
        serverView.showConnectedBackupServers(backupServersByNodeId.size());
        serverView.showBackupServersList(new ArrayList<>(backupServersByNodeId.values()));
        serverView.showConnectedClients(connectedClientsCount.get());
        serverView.showRoomsCount(server.getRooms().size());
        serverView.setShowRoomsAction(() -> serverView.showRoomsTable(getRoomsSnapshot()));
        serverView.setShowBackupsAction(() -> {
            List<BackupServer> backupList = backupOrderByNodeId.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue())
                    .map(e -> backupServersByNodeId.get(e.getKey()))
                    .filter(b -> b != null)
                    .collect(Collectors.toList());
            serverView.showBackupsTable(backupList);
        });
        serverView.setShowUsersAction(() -> serverView.showUsersTable(
                new ArrayList<>(clientsByNodeId.values())));
        log("PRIMARY", "Server starting on port " + server.getClientPort());

        startStateBroadcast();

        Thread clientAcceptThread = new Thread(() -> {
            try {
                clientServerSocket = new ServerSocket(server.getClientPort());
                log("PRIMARY", "Listening for clients on port " + server.getClientPort());
                logReachableAddresses("PRIMARY", server.getClientPort());

                while (!clientServerSocket.isClosed()) {
                    Socket clientSocket = clientServerSocket.accept();
                    int count = connectedClientsCount.incrementAndGet();
                    serverView.showConnectedClients(count);
                    String clientAddr = clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort();
                    log("CLIENT", "New client connected: " + clientAddr);

                    Thread clientThread = new Thread(
                            () -> handleClient(clientSocket),
                            "client-handler-" + clientAddr);
                    clientThread.setDaemon(true);
                    clientThread.start();
                }
            } catch (IOException e) {
                log("PRIMARY", "Server socket error: " + e.getMessage());
            }
        }, "server-accept-loop");

        clientAcceptThread.setDaemon(false);
        clientAcceptThread.start();

        Thread backupAcceptThread = new Thread(() -> {
            try {
                backupServerSocket = new ServerSocket(server.getBackupPort());
                log("BACKUP", "Listening for backup servers on port " + server.getBackupPort());
                logReachableAddresses("BACKUP", server.getBackupPort());

                while (!backupServerSocket.isClosed()) {
                    Socket backupSocket = backupServerSocket.accept();
                    String backupAddr = backupSocket.getInetAddress().getHostAddress() + ":" + backupSocket.getPort();
                    log("BACKUP", "New backup server connected: " + backupAddr);

                    Thread backupThread = new Thread(
                            () -> handleBackupServer(backupSocket),
                            "backup-handler-" + backupAddr);
                    backupThread.setDaemon(true);
                    backupThread.start();
                }
            } catch (IOException e) {
                log("BACKUP", "Backup server socket error: " + e.getMessage());
            }
        }, "backup-server-accept-loop");

        backupAcceptThread.setDaemon(false);
        backupAcceptThread.start();
    }

    private void handleClient(Socket clientSocket) {
        String clientAddr = clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort();
        //Node localServerNode = new Node(server.getIp(), server.getPort());
        ClientConnectionService clientConnectionService = new ClientConnectionService(server/*localServerNode*/);

        try {
            clientConnectionService.attachToClientSocket(clientSocket);
            ServerMessageHandlerService serverMessageHandlerService = new ServerMessageHandlerService(this, clientConnectionService);

            while (clientConnectionService.isConnected()) {
                serverMessageHandlerService.handleMessage(clientConnectionService.waitForMessage());
            }
            log("CLIENT", "Client disconnected: " + clientAddr);
        } catch (IOException e) {
            log("CLIENT", "Client " + clientAddr + " " + e.getMessage());
        } finally {
            int count = connectedClientsCount.decrementAndGet();
            serverView.showConnectedClients(count);
            unregisterClientConnection(clientConnectionService);
            clientConnectionService.disconnect();
        }
    }

    private void handleBackupServer(Socket backupSocket) {
        String backupAddr = backupSocket.getInetAddress().getHostAddress() + ":" + backupSocket.getPort();
        BackupConnectionService backupConnectionService = new BackupConnectionService(server);
        ServerMessageHandlerService serverMessageHandlerService = new ServerMessageHandlerService(this, backupConnectionService);
        UUID[] registeredId = {null};

        try {
            backupConnectionService.attachToBackupSocket(backupSocket);
            // Initial handshake: backup sends SS_HELLO_FROM_BACKUP
            MessageConstants.MessageTuple firstMessage = backupConnectionService.waitForMessage();
            serverMessageHandlerService.handleMessage(firstMessage);
            // Store the handler keyed by nodeId (registered during handleMessage above)
            backupConnectionsByNodeId.entrySet().stream()
                    .filter(e -> e.getValue() == backupConnectionService)
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .ifPresent(id -> {
                        backupMessageHandlersByNodeId.put(id, serverMessageHandlerService);
                        registeredId[0] = id;
                    });
            log("BACKUP", "Backup " + backupAddr + " registered after initial handshake");

            // Continuous loop: receives SS_ACK, SS_BACKUP_EXIT, SS_RESPONSE_HELLO, etc.
            while (backupConnectionService.isConnected()) {
                serverMessageHandlerService.handleMessage(backupConnectionService.waitForMessage());
            }
            log("BACKUP", "Backup " + backupAddr + " disconnected");
        } catch (IOException e) {
            log("BACKUP", "Backup " + backupAddr + " connection error: " + e.getMessage());
        } finally {
            // If backup disappeared without sending SS_BACKUP_EXIT, clean up here
            UUID backupId = registeredId[0];
            if (backupId != null && backupConnectionsByNodeId.containsKey(backupId)) {
                BackupServer bs = backupServersByNodeId.get(backupId);
                String ip = bs != null ? bs.getIp() : "?";
                int port = bs != null ? bs.getConnectionPort() : 0;
                unregisterBackupConnection(backupId, null);
                broadcastToAllClients(new MessageConstants.CSBackupExit(server.getNodeId(), ip, port));
                log("BACKUP", "Backup " + backupAddr + " unregistered due to disconnection");
            }
            backupConnectionService.disconnect();
        }
    }

    public void registerBackupConnection(
            MessageConstants.SSHelloFromBackup helloFromBackup,
            BackupConnectionService backupConnectionService) {
        UUID backupNodeId = helloFromBackup.senderNodeId();
        if (backupNodeId == null) {
            return;
        }

        int order = backupOrderCounter.incrementAndGet();
        BackupServer backupServer = new BackupServer(helloFromBackup.backupIp());
        backupServer.setConnectionPort(helloFromBackup.backupPort());
        backupServer.setOrder(order);
        backupConnectionsByNodeId.put(backupNodeId, backupConnectionService);
        backupServersByNodeId.put(backupNodeId, backupServer);
        backupOrderByNodeId.put(backupNodeId, order);
        refreshBackupServerViewState();

        try {
            backupConnectionService.sendMessage(
                    new MessageConstants.SSResponseHello(server.getNodeId(), order));
            log("BACKUP", "Sent SS_RESPONSE_HELLO to backup "
                    + helloFromBackup.backupIp() + ":" + helloFromBackup.backupPort()
                    + " with order=" + order);
        } catch (IOException ex) {
            log("BACKUP", "Failed to send SS_RESPONSE_HELLO to backup "
                    + helloFromBackup.backupIp() + ":" + helloFromBackup.backupPort()
                    + ": " + ex.getMessage());
        }
    }

    private void unregisterBackupConnection(UUID backupNodeId, BackupConnectionService backupConnectionService) {
        if (backupNodeId != null) {
            backupConnectionsByNodeId.remove(backupNodeId);
            backupServersByNodeId.remove(backupNodeId);
            backupOrderByNodeId.remove(backupNodeId);
            backupMessageHandlersByNodeId.remove(backupNodeId);
        } else if (backupConnectionService != null) {
            backupConnectionsByNodeId.entrySet().removeIf(entry -> entry.getValue() == backupConnectionService);
            backupServersByNodeId.entrySet().removeIf(entry -> !backupConnectionsByNodeId.containsKey(entry.getKey()));
            backupOrderByNodeId.entrySet().removeIf(entry -> !backupConnectionsByNodeId.containsKey(entry.getKey()));
            backupMessageHandlersByNodeId.entrySet().removeIf(entry -> !backupConnectionsByNodeId.containsKey(entry.getKey()));
        }
        refreshBackupServerViewState();
    }

    public void unregisterBackupConnection(UUID backupNodeId) {
        unregisterBackupConnection(backupNodeId, null);
    }

    /** Returns a snapshot of all currently connected backup servers. */
    public List<BackupServer> getConnectedBackupsSnapshot() {
        return new ArrayList<>(backupServersByNodeId.values());
    }

    private void refreshBackupServerViewState() {
        int count = backupConnectionsByNodeId.size();
        connectedBackupsCount.set(count);
        serverView.showConnectedBackupServers(connectedBackupsCount.get());
        serverView.showBackupServersList(new ArrayList<>(backupServersByNodeId.values()));
    }

    public void broadcastToAllClients(MessageConstants.MessageTuple message) {
        if (message == null) {
            return;
        }

        for (Map.Entry<UUID, ClientConnectionService> entry : clientConnectionsByNodeId.entrySet()) {
            ClientConnectionService clientConnectionService = entry.getValue();
            if (clientConnectionService == null || !clientConnectionService.isConnected()) {
                continue;
            }
            try {
                clientConnectionService.sendMessage(message);
            } catch (IOException ex) {
                log("CLIENT", "Failed broadcast " + message.getType() + " to client nodeId=" + entry.getKey() + ": " + ex.getMessage());
            }
        }
    }

    public List<Room> getRoomsSnapshot() {
        synchronized (server) {
            return new ArrayList<>(server.getRooms());
        }
    }

    public void updateRoomsCount() {
        showRoomsCount(getRoomsSnapshot().size());
    }

    public Room findRoomById(String roomId) {
        synchronized (server) {
            return server.getRooms().stream()
                    .filter(r -> r.getRoomId().equals(roomId))
                    .findFirst()
                    .orElse(null);
        }
    }

    public Room findRandomAvailableRoom() {
        synchronized (server) {
            return server.getRooms().stream()
                    .filter(r -> !r.hasTwoPlayers())
                    .findFirst()
                    .orElse(null);
        }
    }

    public boolean removeRoomById(String roomId) {
        boolean removed = server.removeRoomById(roomId);
        if (removed) {
            updateRoomsCount();
            log("PRIMARY", "Room removed: roomId=" + roomId);
        }
        return removed;
    }

    public int removeRoomsByPlayerId(UUID playerNodeId) {
        if (playerNodeId == null) {
            return 0;
        }

        List<Room> snapshot = getRoomsSnapshot();
        int removedCount = 0;
        for (Room room : snapshot) {
            if (room == null) {
                continue;
            }
            if (room.getPlayerById(playerNodeId) != null && removeRoomById(room.getRoomId())) {
                removedCount++;
            }
        }
        return removedCount;
    }

    public void showRoomsCount(int roomsCount) {
        serverView.showRoomsCount(roomsCount);
    }

    public PrimaryServer getServer() {
        return server;
    }

    public void registerClientConnection(UUID nodeId, ClientConnectionService clientConnectionService, Client client) {
        if (nodeId == null || clientConnectionService == null) {
            return;
        }
        clientConnectionsByNodeId.put(nodeId, clientConnectionService);
        if (client != null) {
            clientsByNodeId.put(nodeId, client);
        }
    }

    public ClientConnectionService getClientConnection(UUID nodeId) {
        if (nodeId == null) {
            return null;
        }
        return clientConnectionsByNodeId.get(nodeId);
    }

    public void unregisterClientConnection(ClientConnectionService clientConnectionService) {
        if (clientConnectionService == null) {
            return;
        }
        clientConnectionsByNodeId.entrySet().removeIf(entry -> {
            if (entry.getValue() == clientConnectionService) {
                clientsByNodeId.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }

    public void removeActiveClientByNodeId(UUID nodeId, String reason) {
        if (nodeId == null) {
            return;
        }

        ClientConnectionService removedConnection = clientConnectionsByNodeId.remove(nodeId);
        if (removedConnection != null) {
            clientsByNodeId.remove(nodeId);
            log("CLIENT", "Removing active client nodeId=" + nodeId
                    + (reason == null || reason.isBlank() ? "" : " reason=" + reason));
            removedConnection.disconnect();
        }
    }

    public void log(String tag, String message) {
        serverView.addLog(tag, message);
    }

    /**
     * Logs all non-loopback IPv4 addresses on which the server can be reached
     * from other nodes on the same network.
     */
    private void logReachableAddresses(String tag, int port) {
        try {
            StringBuilder sb = new StringBuilder("Reachable from other nodes at:");
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            boolean found = false;
            while (ifaces != null && ifaces.hasMoreElements()) {
                NetworkInterface iface = ifaces.nextElement();
                if (!iface.isUp() || iface.isLoopback() || iface.isVirtual()) continue;
                Enumeration<InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address) {
                        sb.append(" ").append(addr.getHostAddress()).append(":").append(port);
                        found = true;
                    }
                }
            }
            if (found) {
                log(tag, sb.toString());
            } else {
                log(tag, "No non-loopback IPv4 interfaces found — server may only be reachable locally");
            }
        } catch (Exception e) {
            log(tag, "Could not enumerate network interfaces: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // State broadcast
    // -------------------------------------------------------------------------

    private void startStateBroadcast() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "state-broadcast");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(
                this::broadcastStateToBackups,
                Config.STATE_BROADCAST_INTERVAL_SECONDS,
                Config.STATE_BROADCAST_INTERVAL_SECONDS,
                TimeUnit.SECONDS);
    }

    private void broadcastStateToBackups() {
        reorderBackupsIfNeeded();
        Map<UUID, BackupConnectionService> snapshot = new java.util.HashMap<>(backupConnectionsByNodeId);
        if (snapshot.isEmpty()) {
            return;
        }

        List<Room> rooms = getRoomsSnapshot();
        List<Client> clients = new ArrayList<>(clientsByNodeId.values());
        List<BackupServer> backups = new ArrayList<>(backupServersByNodeId.values());

        MessageConstants.SSSendStateToBackup stateMsg = new MessageConstants.SSSendStateToBackup(
                server.getNodeId(),
                new MessageConstants.PrimaryStateSnapshot(rooms, clients, backups));

        log("BACKUP", "Broadcasting state to " + snapshot.size() + " backup(s): rooms=" + rooms.size()
                + " clients=" + clients.size() + " backups=" + backups.size());

        for (Map.Entry<UUID, BackupConnectionService> entry : snapshot.entrySet()) {
            UUID backupNodeId = entry.getKey();
            BackupConnectionService conn = entry.getValue();
            if (!conn.isConnected()) {
                continue;
            }

            try {
                conn.sendMessage(stateMsg);
                log("BACKUP", "State sent to backup " + backupNodeId);
            } catch (IOException e) {
                log("BACKUP", "Failed to send state to backup " + backupNodeId + " — assuming dead: " + e.getMessage());
                BackupServer bs = backupServersByNodeId.get(backupNodeId);
                String ip = bs != null ? bs.getIp() : "?";
                int port = bs != null ? bs.getConnectionPort() : 0;
                unregisterBackupConnection(backupNodeId, null);
                broadcastToAllClients(new MessageConstants.CSBackupExit(server.getNodeId(), ip, port));
                continue;
            }
        }
    }

    /** Called by the message handler when an SS_ACK arrives from a backup. */
    public void notifyBackupAck(UUID backupNodeId) {
        // ACKs are now read directly in broadcastStateToBackups — no-op kept for compatibility.
    }

    /**
     * Compacts backup orders removing gaps (e.g. 1,3,5 → 1,2,3).
     * Notifies each backup whose order changed via SS_RESPONSE_HELLO.
     * Resets backupOrderCounter to the new maximum so future connections
     * receive the correct next index.
     */
    private void reorderBackupsIfNeeded() {
        if (backupOrderByNodeId.isEmpty()) {
            return;
        }

        List<Map.Entry<UUID, Integer>> sorted = new ArrayList<>(backupOrderByNodeId.entrySet());
        sorted.sort(Map.Entry.comparingByValue());

        boolean hasGap = false;
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).getValue() != i + 1) {
                hasGap = true;
                break;
            }
        }

        int newMax = sorted.size();
        backupOrderCounter.set(newMax);

        if (!hasGap) {
            return;
        }

        for (int i = 0; i < sorted.size(); i++) {
            UUID backupNodeId = sorted.get(i).getKey();
            int oldOrder = sorted.get(i).getValue();
            int newOrder = i + 1;
            if (oldOrder == newOrder) {
                continue;
            }
            backupOrderByNodeId.put(backupNodeId, newOrder);
            BackupServer bs = backupServersByNodeId.get(backupNodeId);
            if (bs != null) {
                bs.setOrder(newOrder);
            }
            BackupConnectionService conn = backupConnectionsByNodeId.get(backupNodeId);
            if (conn != null && conn.isConnected()) {
                try {
                    conn.sendMessage(new MessageConstants.SSResponseHello(server.getNodeId(), newOrder));
                    log("BACKUP", "Reordered backup " + backupNodeId
                            + ": order " + oldOrder + " → " + newOrder);
                } catch (IOException ex) {
                    log("BACKUP", "Failed to notify backup " + backupNodeId
                            + " of new order: " + ex.getMessage());
                }
            }
        }
    }
}
