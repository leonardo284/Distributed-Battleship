package distributed.battleship.client.service;

import distributed.battleship.common.model.client.Client;
import distributed.battleship.common.model.message.MessageConstants;
import distributed.battleship.common.model.node.Node;
import distributed.battleship.common.service.TcpNodeConnectionService;
import distributed.battleship.common.model.server.PrimaryServer;

import java.io.IOException;

/**
 * TCP service dedicated to client-server communication.
 */
public class ServerConnectionService extends TcpNodeConnectionService {

    private final Client client;

    public ServerConnectionService(Client client) {
        // The client-side server listener is suspended and restarted across joins,
        // so its blocking read must wake up periodically to observe interrupts.
        super(true);
        this.client = client;
    }

    @Override
    protected Node getCurrentNode() { return client; }

    @Override
    protected String getLogTag() { return "PRIMARY"; }

    /**
     * Opens an outgoing TCP connection to the server.
     *
     * @param server target server endpoint
     * @throws IOException if the socket cannot be opened
     */
    public void connectToServer(PrimaryServer server) throws IOException {
        connectToNode(server, 0, server.getClientPort());
        if (socket != null) {
            client.setServerConnectionPort(socket.getLocalPort());
        }
    }

    /**
     * Waits for an incoming TCP connection on the provided server endpoint.
     *
     * @param localServer local endpoint used to accept the connection
     * @throws IOException if listening or accept fails
     */
    public void waitForServerConnection(PrimaryServer localServer) throws IOException {
        waitForConnection(client.getServerConnectionPort());
    }

    /**
     * Sends one protocol message to the connected server.
     *
     * @param msg protocol message to send
     * @throws IOException if the server is not connected or sending fails
     */
    public void sendMessageToServer(MessageConstants.MessageTuple msg) throws IOException {
        sendMessage(msg);
    }

    /**
     * Waits for one incoming message from the connected server.
     *
     * @return incoming protocol message
     * @throws IOException if read fails or socket is closed
     */
    public MessageConstants.MessageTuple waitForMessageFromServer() throws IOException {
        return waitForMessage();
    }

    /**
     * @return true when the server socket is connected
     */
    public boolean isConnectedToServer() {
        return isConnectedToNode();
    }


    /**
     * Closes the current server connection.
     */
    public void disconnectFromServer() {
        disconnectFromNode();
    }

    /**
     * Retrieves the currently connected server.
     *
     * @return connected Server, or null when disconnected
     */
    public PrimaryServer getConnectedServer() {
        return (PrimaryServer) getConnectedNode();
    }
}
