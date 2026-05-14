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

}
