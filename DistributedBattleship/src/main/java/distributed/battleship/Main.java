package distributed.battleship;

import distributed.battleship.client.MainClient;
import distributed.battleship.common.helper.AppIconHelper;
import distributed.battleship.common.helper.AppLogger;
import distributed.battleship.server.MainServer;

/**
 * Application entry point.
 * Dispatches execution as client or server.
 *
 * Command line usage:
 * - java Main client [debug]
 * - java Main primary [debug]
 * - java Main backup [debug]
 */
public class Main {
    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }

        String mode = args[0].toLowerCase();
        boolean debugMode = args.length >= 2 && "debug".equalsIgnoreCase(args[1]);
        AppLogger.setDebugEnabled(debugMode);
        AppIconHelper.install();

        try {
            switch (mode) {
                case "client":
                    System.out.println("Starting in CLIENT mode...");
                    MainClient.main(debugMode ? new String[]{"debug"} : new String[0]);
                    break;

                case "primary":
                    System.out.println("Starting in PRIMARY mode...");
                    MainServer.main(new String[]{"PRIMARY"});
                    break;

                case "backup":
                    System.out.println("Starting in BACKUP mode...");
                    MainServer.main(new String[]{"BACKUP"});
                    break;

                default:
                    System.err.println("ERROR: Unknown mode: " + mode);
                    printUsage();
                    System.exit(1);
            }
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Prints the command usage message.
     */
    private static void printUsage() {
        System.out.println("=====================================");
        System.out.println("Battaglia Navale - Distributed Application");
        System.out.println("=====================================");
        System.out.println("Usage:");
        System.out.println("  java Main client [debug]");
        System.out.println("  java Main primary [debug]");
        System.out.println("  java Main backup [debug]");
        System.out.println("=====================================");
        System.out.println("Examples:");
        System.out.println("  java Main client debug");
        System.out.println("  java Main primary");
        System.out.println("  java Main backup debug");
        System.out.println("=====================================");
    }
}