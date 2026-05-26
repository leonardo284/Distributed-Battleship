package distributed.battleship.client.service;

import distributed.battleship.common.model.client.Client;
import distributed.battleship.common.model.message.MessageConstants;
import distributed.battleship.common.model.node.Node;
import distributed.battleship.common.service.TcpNodeConnectionService;

import java.io.IOException;

/**
 * TCP service dedicated to peer-to-peer communication between two clients.
 */
public class PeerConnectionService extends TcpNodeConnectionService {

    private final Client currentClient;

    public PeerConnectionService(Client client) {
        // Peer reads should block normally; enabling SoTimeout here would surface
        // idle gameplay phases as false disconnection errors.
        super(false);
        this.currentClient = client;
    }

    @Override
    protected Node getCurrentNode() { return currentClient; }

    @Override
    protected String getLogTag() { return "CLIENT"; }

    public void resetLocalPort(int port) {
        currentClient.setPeerConnectionPort(port);
    }

    /**
     * Opens an outgoing TCP connection to another client.
     *
     * @param client target peer client
     * @throws IOException if the socket cannot be opened
     */
    public void connectToPeer(Client client) throws IOException {
        connectToNode(client, 0, client.getPeerConnectionPort());
    }

    /**
     * Waits for an incoming peer connection on the local client endpoint.
     * @throws IOException if listening or accept fails
     */
    public void waitForPeerConnection() throws IOException {
        waitForConnection(currentClient.getPeerConnectionPort());
    }

    /**
     * Sends one protocol message to the connected peer.
     *
     * @param msg protocol message to send
     * @throws IOException if the peer is not connected or sending fails
     */
    public void sendMessageToPeer(MessageConstants.MessageTuple msg) throws IOException {
        sendMessage(msg);
    }

    /**
     * Closes the current peer connection.
     */
    public void disconnectFromPeer() {
        disconnectFromNode();
    }

    /**
     * @return true when the peer socket is connected
     */
    public boolean isConnectedToPeer() {
        return isConnectedToNode();
    }

    /**
     * Waits for one response message from the connected peer.
     *
     * @return incoming protocol message
     * @throws IOException if read fails or socket is closed
     */
    public MessageConstants.MessageTuple waitForMessageFromPeer() throws IOException {
        return waitForMessage();
    }

    /**
     * Retrieves the currently connected peer client.
     *
     * @return connected Client, or null when disconnected
     */
    public Client getConnectedClient() {
        return (Client) getConnectedNode();
    }
}
