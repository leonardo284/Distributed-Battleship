package distributed.battleship.common.model.server;

/**
 * Domain model for a backup server node.
 * The backup has a single port — the local port it uses to connect to the primary server.
 * This port is set by the controller via {@link #setConnectionPort(int)} after connecting.
 */
public class BackupServer extends Server {

    /**
     * The local port used by this backup to connect to the primary server.
     * Set by the controller after the TCP connection is established.
     */
    private int connectionPort;

    /**
     * Connection order assigned by the primary server (1 = first connected).
     * The backup with order 1 has priority to become the new primary.
     */
    private int order;

    /**
     * Creates a backup server with only its IP known.
     * The connection port must be set later via {@link #setConnectionPort(int)}.
     *
     * @param ip IP address of this backup server
     */
    public BackupServer(String ip) {
        super(ip);
    }

    public BackupServer(java.util.UUID nodeId, String ip, int connectionPort) {
        super(nodeId, ip);
        this.connectionPort = connectionPort;
    }


    public int getConnectionPort() {
        return connectionPort;
    }

    /**
     * Sets the port used by this backup to connect to the primary server.
     * Called by the controller after the TCP connection is established.
     *
     * @param connectionPort local port of the established connection
     */
    public void setConnectionPort(int connectionPort) {
        this.connectionPort = connectionPort;
    }

    /** Returns the connection order assigned by the primary (1 = first connected). */
    public int getOrder() {
        return order;
    }

    /** Sets the connection order received from the primary via SS_RESPONSE_HELLO. */
    public void setOrder(int order) {
        this.order = order;
    }
}
