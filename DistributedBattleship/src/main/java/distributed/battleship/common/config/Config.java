package distributed.battleship.common.config;

/**
 * Static network configuration shared across client and server bootstrapping.
 */
public final class Config {

    public static final String SERVER_IP = "127.0.0.1";
    public static final String SERVER_BIND_IP = "0.0.0.0";

    public static final int PRIMARY_SERVER_PORT = 5000;
    public static final int BACKUP_SERVER_PORT = 5001;

    /** How often (seconds) the primary broadcasts its state to all connected backups. */
    public static final int STATE_BROADCAST_INTERVAL_SECONDS = 10;

    /** How long (seconds) the primary waits for SS_ACK before considering a backup dead. */
    public static final int BACKUP_ACK_TIMEOUT_SECONDS = 5;

    /** How long (seconds) a backup waits without receiving state before considering the primary dead. */
    public static final int BACKUP_PRIMARY_TIMEOUT_SECONDS = STATE_BROADCAST_INTERVAL_SECONDS;

    /** Maximum number of reconnection attempts the client makes when the primary server goes down. */
    public static final int CLIENT_RECONNECT_MAX_ATTEMPTS = 5;

    /** Seconds to wait between each client reconnection attempt. */
    public static final int CLIENT_RECONNECT_DELAY_SECONDS = 3;

    private Config() {
        throw new UnsupportedOperationException("Utility class");
    }
}
