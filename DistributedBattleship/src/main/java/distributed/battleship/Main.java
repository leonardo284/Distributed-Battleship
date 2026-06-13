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
 * - java Main client [&lt;server-ip&gt;] [debug]
 * - java Main primary
 * - java Main backup [&lt;primary-ip&gt;] [debug]
 */
public class Main {
    public static void main(String[] args) {
        if (args.length == 0 || args.length > 3) {
            printUsage();
            System.exit(1);
        }

        String mode = args[0].toLowerCase();

        // args[1] can be an IP or "debug"; args[2] can only be "debug"
        String serverIp = null;
        boolean debugMode = false;

        if (args.length >= 2) {
            if ("debug".equalsIgnoreCase(args[1])) {
                debugMode = true;
            } else {
                serverIp = args[1];
                if (args.length == 3) {
                    if ("debug".equalsIgnoreCase(args[2])) {
                        debugMode = true;
                    } else {
                        System.err.println("ERROR: third argument must be 'debug'");
                        printUsage();
                        System.exit(1);
                    }
                }
            }
        }

        AppLogger.setDebugEnabled(debugMode);
        AppIconHelper.install();

        try {
            switch (mode) {
                case "client":
                    System.out.println("Starting in CLIENT mode...");
                    MainClient.main(buildSubArgs(serverIp, debugMode));
                    break;

                case "primary":
                    System.out.println("Starting in PRIMARY mode...");
                    MainServer.main(new String[]{"PRIMARY"});
                    break;

                case "backup":
                    System.out.println("Starting in BACKUP mode...");
                    String[] subArgs = buildSubArgs(serverIp, debugMode);
                    String[] backupArgs = new String[1 + subArgs.length];
                    backupArgs[0] = "BACKUP";
                    System.arraycopy(subArgs, 0, backupArgs, 1, subArgs.length);
                    MainServer.main(backupArgs);
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
     * Builds the sub-args array from optional IP and debug flag.
     * Possible results: [], ["debug"], ["&lt;ip&gt;"], ["&lt;ip&gt;", "debug"]
     */
    private static String[] buildSubArgs(String ip, boolean debug) {
        if (ip != null && debug) return new String[]{ip, "debug"};
        if (ip != null)          return new String[]{ip};
        if (debug)               return new String[]{"debug"};
        return new String[0];
    }

    /**
     * Prints the command usage message.
     */
    private static void printUsage() {
        System.out.println("=====================================");
        System.out.println("Battaglia Navale - Distributed Application");
        System.out.println("=====================================");
        System.out.println("Usage:");
        System.out.println("  java Main client [<server-ip>] [debug]");
        System.out.println("  java Main primary");
        System.out.println("  java Main backup [<primary-ip>] [debug]");
        System.out.println("=====================================");
        System.out.println("Examples:");
        System.out.println("  java Main client debug");
        System.out.println("  java Main client 192.168.1.10");
        System.out.println("  java Main client 192.168.1.10 debug");
        System.out.println("  java Main primary");
        System.out.println("  java Main backup 192.168.1.10 debug");
        System.out.println("=====================================");
    }
}