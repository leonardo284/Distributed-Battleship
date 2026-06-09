package distributed.battleship.common.model.node;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NodeTest {

    @Test
    void constructorWithIp_assignsRandomNodeId() {
        Node node = new Node("192.168.1.1") {};
        assertNotNull(node.getNodeId());
        assertEquals("192.168.1.1", node.getIp());
    }

    @Test
    void twoNodesWithIp_haveDifferentNodeIds() {
        Node a = new Node("10.0.0.1") {};
        Node b = new Node("10.0.0.1") {};
        assertNotEquals(a.getNodeId(), b.getNodeId());
    }

    @Test
    void constructorWithUuidAndIp_preservesId() {
        UUID id = UUID.randomUUID();
        // Use a concrete subclass (Client) that exposes the protected constructor
        distributed.battleship.common.model.client.Client client =
                new distributed.battleship.common.model.client.Client(id, "127.0.0.1", "test");
        assertEquals(id, client.getNodeId());
        assertEquals("127.0.0.1", client.getIp());
    }

    @Test
    void setIp_updatesIp() {
        Node node = new Node("1.2.3.4") {};
        node.setIp("9.9.9.9");
        assertEquals("9.9.9.9", node.getIp());
    }

    @Test
    void toString_containsNodeIdAndIp() {
        Node node = new Node("1.2.3.4") {};
        String s = node.toString();
        assertTrue(s.contains(node.getNodeId().toString()));
        assertTrue(s.contains("1.2.3.4"));
    }
}
