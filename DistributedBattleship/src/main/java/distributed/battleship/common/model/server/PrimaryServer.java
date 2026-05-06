package distributed.battleship.common.model.server;

/**
 * Domain model for the primary server node.
 * Holds two ports: one for client connections and one for backup-server connections.
 * Both ports are known at construction time (taken from {@link org.example.common.config.Config}).
 */
public class PrimaryServer extends Server {

    private final int clientPort;
    private final int backupPort;

    /**
     * Full constructor used when starting the primary server.
     *
     * @param ip         bind IP address
     * @param clientPort port that accepts client connections
     * @param backupPort port that accepts backup-server connections
     */
    public PrimaryServer(String ip, int clientPort, int backupPort) {
        super(ip);
        this.clientPort = clientPort;
        this.backupPort = backupPort;
    }

    /**
     * Convenience constructor for references where only the client port is known
     * (e.g. when a client builds a reference to the remote server).
     *
     * @param ip         server IP address
     * @param clientPort port that accepts client connections
     */
    public PrimaryServer(String ip, int clientPort) {
        this(ip, clientPort, 0);
    }

    public int getClientPort() {
        return clientPort;
    }

    public int getBackupPort() {
        return backupPort;
    }
}

