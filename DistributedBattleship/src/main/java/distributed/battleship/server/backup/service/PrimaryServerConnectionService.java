package distributed.battleship.server.backup.service;

import distributed.battleship.common.model.message.MessageConstants;
import distributed.battleship.common.model.node.Node;
import distributed.battleship.common.service.TcpNodeConnectionService;
import distributed.battleship.common.model.server.BackupServer;
import distributed.battleship.common.model.server.PrimaryServer;

import java.io.IOException;

/**
 * Connection service used by a backup server to communicate with the primary server.
 */
public class PrimaryServerConnectionService extends TcpNodeConnectionService {

    private final BackupServer backupServer;

    public PrimaryServerConnectionService(BackupServer backupServer) {
        super(true);
        this.backupServer = backupServer;
    }

    @Override
    protected Node getCurrentNode() { return backupServer; }

    @Override
    protected String getLogTag() { return "PRIMARY"; }

    public void connectToPrimaryServer(PrimaryServer primaryServer) throws IOException {
        connectToNode(primaryServer, backupServer.getConnectionPort(), primaryServer.getBackupPort());
    }

    /** Returns the local port of the established connection (0 if not connected). */
    public int getLocalPort() {
        return socket != null ? socket.getLocalPort() : 0;
    }

    public void sendMessageToPrimaryServer(MessageConstants.MessageTuple msg) throws IOException {
        sendMessage(msg);
    }

    public MessageConstants.MessageTuple waitForMessageFromPrimaryServer() throws IOException {
        return super.waitForMessage();
    }

    public boolean isConnectedToPrimaryServer() {
        return isConnectedToNode();
    }

    public void disconnectFromPrimaryServer() {
        disconnectFromNode();
    }
}
