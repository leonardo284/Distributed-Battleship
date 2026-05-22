package distributed.battleship.common.service;

import distributed.battleship.common.model.message.MessageConstants;
import distributed.battleship.common.model.node.Node;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Base TCP connection service shared by concrete client-side and server-side connections.
 * It stores the current connected node and the socket streams used to
 * exchange protocol messages.
 */
public abstract class TcpNodeConnectionService {

    private final Object connectionStateLock = new Object();
    private final Object writeLock = new Object();
    // Some flows need blocking reads to wake up periodically so the owning thread
    // can observe interrupt/restart state. For plain request/response sockets this
    // stays false to avoid turning idle periods into artificial read errors.
    protected final boolean useSoTimeout;
    private int connectedPort;
    protected abstract String getLogTag();
    protected abstract Node getCurrentNode();

    protected Node connectedNode;
    protected Socket socket;
    protected BufferedReader reader;
    protected PrintWriter writer;

    protected TcpNodeConnectionService(boolean useSoTimeout) {
        this.useSoTimeout = useSoTimeout;
    }

    /**
     * Opens an outgoing TCP connection to a target node.
     *
     * @param node target network node
     * @throws IOException if the socket cannot be opened
     */
    public void connectToNode(Node node, int localPort, int targetPort) throws IOException {
        if (node == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }

        synchronized (connectionStateLock) {
            if (isConnectedToNode()) {
                AppLogger.debug("[" + getLogTag() + "] TCP service already connected, skipping connectToNode");
                return;
            }

            AppLogger.debug("[" + getLogTag() + "] Connecting to node " + node.getIp() + ":" + targetPort);

            this.connectedNode = node;
            this.connectedPort = targetPort;
            this.socket = new Socket();
            if (localPort != 0) {
                this.socket.bind(new InetSocketAddress(getCurrentNode().getIp(), localPort));
            }
            this.socket.connect(new InetSocketAddress(node.getIp(), targetPort));
            if (useSoTimeout) {
                // A short read timeout keeps the blocking read interruptible for
                // long-lived listener threads, especially on the client-server link.
                this.socket.setSoTimeout(1000);
            }
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.writer = new PrintWriter(socket.getOutputStream(), true);
        }
        AppLogger.debug("[" + getLogTag() + "] Connected to node " + node.getIp() + ":" + targetPort);
    }

    /**
     * Waits for an incoming TCP connection on the configured local node endpoint.
     * Once accepted, this service starts using the accepted socket as the main socket.
     *
     * @throws IOException if listening or accept fails
     */
    public void waitForConnection(int port) throws IOException {
        synchronized (connectionStateLock) {
            if (isConnectedToNode()) {
                AppLogger.debug("[" + getLogTag() + "] TCP service already connected, skipping waitForConnection");
                return;
            }
        }

        AppLogger.debug("[" + getLogTag() + "] Listening for incoming connection on " + getCurrentNode().getIp() + ":" + port);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (!serverSocket.isClosed() && !isConnectedToNode()) {
                Socket acceptedSocket = serverSocket.accept();
                attachAcceptedSocket(acceptedSocket);
                AppLogger.debug("[" + getLogTag() + "] Accepted connection from " + acceptedSocket.getInetAddress().getHostAddress() + ":" + acceptedSocket.getPort());
            }
        }
    }

    protected void attachAcceptedSocket(Socket acceptedSocket) throws IOException {
        synchronized (connectionStateLock) {
            this.socket = acceptedSocket;
            if (useSoTimeout) {
                // Apply the same timeout policy to accepted sockets when the caller
                // needs periodic wake-ups from blocking reads.
                this.socket.setSoTimeout(1000);
            }
            this.reader = new BufferedReader(new InputStreamReader(acceptedSocket.getInputStream()));
            this.writer = new PrintWriter(acceptedSocket.getOutputStream(), true);
            this.connectedNode = new Node(acceptedSocket.getInetAddress().getHostAddress());
            this.connectedPort = acceptedSocket.getPort();
        }
    }

    /**
     * Sends one protocol message over the active TCP connection.
     *
     * @param msg protocol message to send
     * @throws IOException if not connected or write fails
     */
    public void sendMessage(MessageConstants.MessageTuple msg) throws IOException {
        PrintWriter currentWriter;
        Node targetNode;
        int targetPort;
        synchronized (connectionStateLock) {
            if (!isConnectedToNode() || writer == null || connectedNode == null) {
                throw new IOException("Node is not connected");
            }
            currentWriter = writer;
            targetNode = connectedNode;
            targetPort = connectedPort;
        }

        synchronized (writeLock) {
            AppLogger.debug("[" + getLogTag() + "] Sending message to node " + targetNode.getIp() + ":" + targetPort + " type=" + msg.getType());
            currentWriter.println(ProtocolMessageJsonHelper.serialize(msg));
            if (currentWriter.checkError()) {
                throw new IOException("Socket write failed");
            }
        }
    }

    /**
     * Waits for one protocol message from the active TCP connection.
     *
     * @return received protocol message
     * @throws IOException if not connected or read fails
     */
    public MessageConstants.MessageTuple waitForMessage() throws IOException {
        BufferedReader currentReader;
        Node sourceNode;
        int sourcePort;
        synchronized (connectionStateLock) {
            if (!isConnectedToNode() || reader == null || connectedNode == null) {
                throw new IOException("Node is not connected");
            }
            currentReader = reader;
            sourceNode = connectedNode;
            sourcePort = connectedPort;
        }

        String rawMessage = currentReader.readLine();
        if (rawMessage == null) {
            throw new IOException("Remote node closed the connection");
        }

        MessageConstants.MessageTuple message = ProtocolMessageJsonHelper.deserialize(rawMessage);
        AppLogger.debug("[" + getLogTag() + "] Received message from node " + sourceNode.getIp() + ":" + sourcePort + " type=" + message.getType());
        return message;
    }

    /**
     * Closes the active TCP connection and clears node state.
     */
    public void disconnectFromNode() {
        BufferedReader currentReader;
        PrintWriter currentWriter;
        Socket currentSocket;
        Node previousConnectedNode;
        int previousConnectedPort;

        synchronized (connectionStateLock) {
            currentReader = reader;
            currentWriter = writer;
            currentSocket = socket;
            previousConnectedNode = connectedNode;
            previousConnectedPort = connectedPort;
            reader = null;
            writer = null;
            socket = null;
            connectedNode = null;
            connectedPort = 0;
        }

        if (previousConnectedNode != null) {
            AppLogger.debug("[" + getLogTag() + "] Disconnecting from node " + previousConnectedNode.getIp() + ":" + previousConnectedPort);
        }
        try {
            if (currentReader != null) {
                currentReader.close();
            }
        } catch (IOException ignored) {
            // Best-effort shutdown.
        }

        if (currentWriter != null) {
            currentWriter.close();
        }

        try {
            if (currentSocket != null && !currentSocket.isClosed()) {
                currentSocket.close();
            }
        } catch (IOException ignored) {
            // Best-effort shutdown.
        }
    }

    /**
     * @return true when a live TCP connection is active
     */
    public boolean isConnectedToNode() {
        synchronized (connectionStateLock) {
            return socket != null && !socket.isClosed() && socket.isConnected();
        }
    }

    /**
     * @return currently connected node, or null when disconnected
     */
    public Node getConnectedNode() {
        synchronized (connectionStateLock) {
            return connectedNode;
        }
    }

    /**
     * Waits asynchronously for a message with timeout on a dedicated thread.
     * Handles timeout, interruption, and execution errors gracefully.
     *
     * @param timeoutSeconds maximum wait duration in seconds
     * @param threadName name for the internal wait thread
     * @param onMessage callback to invoke when a message is received
     * @param onTimeout callback to invoke on timeout
     */
    public void waitForMessageWithTimeout(
            int timeoutSeconds,
            String threadName,
            java.util.function.Consumer<MessageConstants.MessageTuple> onMessage,
            java.util.function.BiConsumer<Integer, String> onTimeout) {

        waitForMessageWithTimeout(
                timeoutSeconds,
                threadName,
                onMessage,
                onTimeout,
                throwable -> {
                    Throwable cause = throwable != null ? throwable : new IllegalStateException("Unknown wait error");
                    AppLogger.debug("[" + getLogTag() + "] Error while waiting for message: " + cause.getMessage());
                });
    }

    /**
     * Waits asynchronously for a message with timeout on a dedicated thread.
     * Handles timeout, interruption, and execution errors with explicit callbacks.
     *
     * @param timeoutSeconds maximum wait duration in seconds
     * @param threadName name for the internal wait thread
     * @param onMessage callback to invoke when a message is received
     * @param onTimeout callback to invoke on timeout
     * @param onError callback to invoke when wait fails before timeout
     */
    public void waitForMessageWithTimeout(
            int timeoutSeconds,
            String threadName,
            java.util.function.Consumer<MessageConstants.MessageTuple> onMessage,
            java.util.function.BiConsumer<Integer, String> onTimeout,
            java.util.function.Consumer<Throwable> onError) {

        Thread waitThread = new Thread(() -> {
            java.util.concurrent.FutureTask<MessageConstants.MessageTuple> waitTask =
                    new java.util.concurrent.FutureTask<>(this::waitForMessage);

            Thread ioThread = new Thread(waitTask, threadName + "-io");
            ioThread.setDaemon(true);
            ioThread.start();

            try {
                MessageConstants.MessageTuple message = waitTask.get(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
                onMessage.accept(message);
            } catch (java.util.concurrent.TimeoutException ex) {
                onTimeout.accept(timeoutSeconds, threadName);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                AppLogger.debug("[" + getLogTag() + "] Interrupted while waiting for message on " + threadName);
            } catch (java.util.concurrent.ExecutionException ex) {
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                onError.accept(cause);
            }
        }, threadName);

        waitThread.setDaemon(true);
        waitThread.start();
    }
}
