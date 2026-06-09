package distributed.battleship.common.model.client;

import distributed.battleship.common.model.room.Room;
import distributed.battleship.common.model.server.PrimaryServer;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ClientTest {

    @Test
    void constructor_setsNameAndIp() {
        Client client = new Client("10.0.0.1", "Alice");
        assertEquals("Alice", client.getName());
        assertEquals("10.0.0.1", client.getIp());
        assertNotNull(client.getNodeId());
    }

    @Test
    void constructorWithUuid_preservesId() {
        UUID id = UUID.randomUUID();
        Client client = new Client(id, "10.0.0.2", "Bob");
        assertEquals(id, client.getNodeId());
        assertEquals("Bob", client.getName());
    }

    @Test
    void defaultPorts_areZero() {
        Client client = new Client("1.1.1.1", "Player");
        assertEquals(0, client.getPeerConnectionPort());
        assertEquals(0, client.getServerConnectionPort());
    }

    @Test
    void setters_updateValues() {
        Client client = new Client("1.1.1.1", "Player");
        client.setName("NewName");
        client.setPeerConnectionPort(8080);
        client.setServerConnectionPort(9090);

        assertEquals("NewName", client.getName());
        assertEquals(8080, client.getPeerConnectionPort());
        assertEquals(9090, client.getServerConnectionPort());
    }

    @Test
    void setServer_storesReference() {
        Client client = new Client("1.1.1.1", "Player");
        PrimaryServer server = new PrimaryServer("2.2.2.2", 5000);
        client.setServer(server);
        assertEquals(server, client.getServer());
    }

    @Test
    void getOpponentFromRoom_noRoom_returnsNull() {
        Client client = new Client("1.1.1.1", "Alice");
        assertNull(client.getOpponentFromRoom());
    }

    @Test
    void getOpponentFromRoom_returnsOpponent() {
        Client alice = new Client("1.1.1.1", "Alice");
        Client bob = new Client("2.2.2.2", "Bob");
        Room room = new Room("room-1", alice, bob);
        alice.setRoom(room);

        Client opponent = alice.getOpponentFromRoom();
        assertNotNull(opponent);
        assertEquals(bob.getNodeId(), opponent.getNodeId());
    }

    @Test
    void getOpponentFromRoom_withOnlyPlayerOne_returnsNull() {
        Client alice = new Client("1.1.1.1", "Alice");
        Room room = new Room("room-1", alice, null);
        alice.setRoom(room);
        // alice is playerOne; playerTwo is null — no opponent
        assertNull(alice.getOpponentFromRoom());
    }

    @Test
    void setRoom_updatesRoomReference() {
        Client client = new Client("1.1.1.1", "Player");
        assertNull(client.getRoom());
        Room room = new Room("r1", client, null);
        client.setRoom(room);
        assertEquals(room, client.getRoom());
    }
}
