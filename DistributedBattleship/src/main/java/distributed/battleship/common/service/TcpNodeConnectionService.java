package distributed.battleship.service;

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
}
