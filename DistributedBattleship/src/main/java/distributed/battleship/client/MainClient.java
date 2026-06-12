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
     * @param args Arguments: [debug]
     */
    public static void main(String[] args) {
        if (args.length > 1) {
            System.err.println("Usage: java MainClient [debug]");
            System.exit(1);
        }

        boolean debugMode = args.length == 1 && "debug".equalsIgnoreCase(args[0]);
        AppIconHelper.install();

        Client client = new Client(Config.SERVER_IP, null);
        ClientController controller = new ClientController(client, Config.SERVER_IP, Config.PRIMARY_SERVER_PORT, debugMode);
        controller.start();
    }


}
