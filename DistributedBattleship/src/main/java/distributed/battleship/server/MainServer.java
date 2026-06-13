package distributed.battleship.server;

import distributed.battleship.common.config.Config;
import distributed.battleship.common.helper.AppIconHelper;
import distributed.battleship.common.helper.AppLogger;
import distributed.battleship.server.backup.controller.BackupServerController;
import distributed.battleship.server.primary.controller.PrimaryServerController;
import distributed.battleship.common.model.server.BackupServer;
import distributed.battleship.common.model.server.PrimaryServer;

/**
 * Main class for server startup.
 *
 * <p>Usage:
 * <br>{@code java MainServer PRIMARY}
 * <br>{@code java MainServer BACKUP [<primary-ip>] [debug]}
 */
public class MainServer {
    private MainServer() {
    }

    /**
     * Server entry point.
     *
     * @param args Arguments: PRIMARY | BACKUP [&lt;primary-ip&gt;] [debug]
     *             For BACKUP, the second parameter is treated as an IP address unless it equals "debug".
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: java MainServer PRIMARY | BACKUP [<primary-ip>] [debug]");
            System.exit(1);
        }

        String mode = args[0].toUpperCase();
        AppIconHelper.install();

        if ("PRIMARY".equals(mode)) {
            PrimaryServer primaryServer = new PrimaryServer(Config.SERVER_IP, Config.PRIMARY_SERVER_PORT, Config.BACKUP_SERVER_PORT);
            PrimaryServerController controller = new PrimaryServerController(primaryServer);
            controller.start();
            return;
        }

        if ("BACKUP".equals(mode)) {
            String primaryIp = Config.SERVER_IP;
            boolean debugMode = false;

            if (args.length >= 2) {
                if ("debug".equalsIgnoreCase(args[1])) {
                    debugMode = true;
                } else {
                    primaryIp = args[1];
                    if (args.length >= 3) {
                        if ("debug".equalsIgnoreCase(args[2])) {
                            debugMode = true;
                        } else {
                            System.err.println("Usage: java MainServer BACKUP [<primary-ip>] [debug]");
                            System.exit(1);
                        }
                    }
                }
            }

            AppLogger.setDebugEnabled(debugMode);
            BackupServer backupServer = new BackupServer(Config.SERVER_IP);
            PrimaryServer primaryServer = new PrimaryServer(primaryIp, Config.PRIMARY_SERVER_PORT, Config.BACKUP_SERVER_PORT);
            BackupServerController backupServerController = new BackupServerController(backupServer, primaryServer);
            backupServerController.start();
            return;
        }

        System.err.println("Unknown server mode '" + args[0] + "'. Use PRIMARY or BACKUP.");
        System.exit(1);
    }
}

