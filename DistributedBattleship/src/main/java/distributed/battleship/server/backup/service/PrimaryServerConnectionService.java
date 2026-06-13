package distributed.battleship.server.backup.service;

import distributed.battleship.common.model.message.MessageConstants;
import distributed.battleship.common.model.node.Node;
import distributed.battleship.common.service.TcpNodeConnectionService;
import distributed.battleship.common.model.server.BackupServer;
import distributed.battleship.common.model.server.PrimaryServer;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

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
        if (socket != null) {
            InetAddress localAddr = socket.getLocalAddress();
            if (localAddr.isLoopbackAddress()) {
                InetAddress resolved = resolveNonLoopbackIpv4();
                if (resolved != null) localAddr = resolved;
            }
            backupServer.setIp(localAddr.getHostAddress());
        }
    }

    /**
     * Returns the first non-loopback, non-virtual, up IPv4 address found on this machine,
     * or {@code null} if none is available.
     */
    private static InetAddress resolveNonLoopbackIpv4() {
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces != null && ifaces.hasMoreElements()) {
                NetworkInterface iface = ifaces.nextElement();
                if (!iface.isUp() || iface.isLoopback() || iface.isVirtual()) continue;
                Enumeration<InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return addr;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
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
