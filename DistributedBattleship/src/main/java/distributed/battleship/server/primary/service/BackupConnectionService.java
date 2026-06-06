package distributed.battleship.server.primary.service;

import distributed.battleship.common.model.message.MessageConstants;
import distributed.battleship.common.model.node.Node;
import distributed.battleship.common.model.server.PrimaryServer;
import distributed.battleship.common.service.TcpNodeConnectionService;

import java.io.IOException;
import java.net.Socket;

/**
 * Represents a single backup-server connection from the primary server perspective.
 */
public class BackupConnectionService extends TcpNodeConnectionService {

    private final PrimaryServer serverPrimary;

    public BackupConnectionService(PrimaryServer serverPrimary) {
        super(false);
        this.serverPrimary = serverPrimary;
    }

    @Override
    protected Node getCurrentNode() { return serverPrimary; }

    @Override
    protected String getLogTag() { return "BACKUP"; }

    public void attachToBackupSocket(Socket acceptedSocket) throws IOException {
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

    public Node getBackupNode() {
        return getConnectedNode();
    }
}
