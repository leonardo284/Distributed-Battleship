package distributed.battleship.server;

import distributed.battleship.common.config.Config;
import distributed.battleship.server.backup.controller.BackupServerController;
import distributed.battleship.server.primary.controller.PrimaryServerController;
import distributed.battleship.common.model.server.BackupServer;
import distributed.battleship.common.model.server.PrimaryServer;

/**
 * Main class for server startup.
 *
 * <p>Usage:
 * <br>{@code java MainServer PRIMARY}
 * <br>{@code java MainServer BACKUP}
 */
public class MainServer {
    private MainServer() {
    }

    /**
     * Server entry point.
     *
     * @param args Arguments: PRIMARY | BACKUP
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: java MainServer PRIMARY | BACKUP");
            System.exit(1);
        }

        String mode = args[0].toUpperCase();

        if ("PRIMARY".equals(mode)) {
            PrimaryServer primaryServer = new PrimaryServer(Config.SERVER_IP, Config.PRIMARY_SERVER_PORT, Config.BACKUP_SERVER_PORT);
            PrimaryServerController controller = new PrimaryServerController(primaryServer);
            controller.start();
            return;
        }

        if ("BACKUP".equals(mode)) {
            BackupServer backupServer = new BackupServer(Config.SERVER_IP);
            PrimaryServer primaryServer = new PrimaryServer(Config.SERVER_IP, Config.PRIMARY_SERVER_PORT, Config.BACKUP_SERVER_PORT);
            BackupServerController backupServerController = new BackupServerController(backupServer, primaryServer);
            backupServerController.start();
            return;
        }

        System.err.println("Unknown server mode '" + args[0] + "'. Use PRIMARY or BACKUP.");
        System.exit(1);
    }
}

