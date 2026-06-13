package distributed.battleship.client;

import distributed.battleship.client.controller.ClientController;
import distributed.battleship.common.config.Config;
import distributed.battleship.common.helper.AppIconHelper;
import distributed.battleship.common.model.client.Client;

/**
 * Main class for client startup.
 * Delegates startup and interaction flow to the client controller.
 */
public class MainClient {
    /**
     * Private constructor to prevent instantiation of the entry point class.
     */
    private MainClient() {
    }

    /**
     * Client entry point.
     * Shows the main menu at startup; the connection to the server is established
     * only after the player fills in the menu form and clicks "Join Game".
     *
     * @param args Arguments: [&lt;server-ip&gt;] [debug]
     *             The second parameter is treated as an IP address unless it equals "debug".
     */
    public static void main(String[] args) {
        if (args.length > 2) {
            System.err.println("Usage: java MainClient [<server-ip>] [debug]");
            System.exit(1);
        }

        String serverIp = Config.SERVER_IP;
        boolean debugMode = false;

        if (args.length >= 1) {
            if ("debug".equalsIgnoreCase(args[0])) {
                debugMode = true;
            } else {
                serverIp = args[0];
                if (args.length == 2) {
                    if ("debug".equalsIgnoreCase(args[1])) {
                        debugMode = true;
                    } else {
                        System.err.println("Usage: java MainClient [<server-ip>] [debug]");
                        System.exit(1);
                    }
                }
            }
        }

        AppIconHelper.install();

        Client client = new Client(null, null);
        ClientController controller = new ClientController(client, serverIp, Config.PRIMARY_SERVER_PORT, debugMode);
        controller.start();
    }


}
