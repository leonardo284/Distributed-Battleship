package distributed.battleship.common.model.node;

import java.util.UUID;

/**
 * Represents a generic node in the network.
 * Contains the basic node information: immutable node ID, IP address and port.
 */
public class Node {
    private UUID nodeId;
    protected String ip;

    /**
     * Node constructor.
     *
     * @param ip Node IP address
     */
    public Node(String ip) {
        this.nodeId = UUID.randomUUID();
        this.ip = ip;
    }

    protected Node(UUID nodeId, String ip) {
        this.nodeId = nodeId;
        this.ip = ip;
    }



    public UUID getNodeId() {
        return nodeId;
    }

    /**
     * Gets the node IP address.
     *
     * @return Node IP
     */
    public String getIp() {
        return ip;
    }

    /**
     * Sets the node IP address.
     *
     * @param ip IP address
     */
    public void setIp(String ip) {
        this.ip = ip;
    }

    @Override
    public String toString() {
        return "Node{" +
                "nodeId=" + nodeId +
                ", ip='" + ip + '\'' +
                '}';
    }
}
