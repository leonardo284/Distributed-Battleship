package distributed.battleship.server.primary.service;

import distributed.battleship.common.model.message.MessageConstants;
import distributed.battleship.common.model.node.Node;
import distributed.battleship.common.model.server.PrimaryServer;
import distributed.battleship.common.service.TcpNodeConnectionService;

import java.io.IOException;
import java.net.Socket;

/**
 * Represents a single client connection from the server's perspective.
 * Manages bidirectional communication with a connected client.
 * Each client connection is handled by a dedicated instance of this service.
 */
public class ClientConnectionService extends TcpNodeConnectionService {

    private final PrimaryServer primaryServer;

    /**
     * Creates a client connection service bound to a local server node.
     *
     * @param primaryServer the local server node configuration
     */
    public ClientConnectionService(PrimaryServer primaryServer) {
        super(false);
        this.primaryServer = primaryServer;
    }

    @Override
    protected Node getCurrentNode() { return primaryServer; }

    @Override
    protected String getLogTag() { return "CLIENT"; }

    public void attachToClientSocket(Socket acceptedSocket) throws IOException {
        attachAcceptedSocket(acceptedSocket);
    }

    public void sendMessage(MessageConstants.MessageTuple msg) throws IOException {
        super.sendMessage(msg);
    }

    public void disconnect() {
        disconnectFromNode();
    }

    public boolean isConnected() {
        return isConnectedToNode();
    }

    public Node getClientNode() {
        return getConnectedNode();
    }
}