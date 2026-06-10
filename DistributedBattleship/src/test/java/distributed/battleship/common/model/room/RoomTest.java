package distributed.battleship.common.model.room;

import distributed.battleship.common.model.client.Client;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RoomTest {

    private Client playerOne;
    private Client playerTwo;

    @BeforeEach
    void setUp() {
        playerOne = new Client("1.1.1.1", "Alice");
        playerTwo = new Client("2.2.2.2", "Bob");
    }

    @Test
    void constructorWithRoomId_setsAllFields() {
        Room room = new Room("room-42", playerOne, playerTwo);
        assertEquals("room-42", room.getRoomId());
        assertEquals(playerOne, room.getPlayerOne());
        assertEquals(playerTwo, room.getPlayerTwo());
    }

    @Test
    void constructorWithoutRoomId_generatesNonNullId() {
        Room room = new Room(playerOne, playerTwo);
        assertNotNull(room.getRoomId());
        assertFalse(room.getRoomId().isBlank());
    }

    @Test
    void hasTwoPlayers_bothPresent_returnsTrue() {
        Room room = new Room("r1", playerOne, playerTwo);
        assertTrue(room.hasTwoPlayers());
    }

    @Test
    void hasTwoPlayers_oneMissing_returnsFalse() {
        Room room = new Room("r1", playerOne, null);
        assertFalse(room.hasTwoPlayers());
    }

    @Test
    void getPlayerById_returnsCorrectPlayer() {
        Room room = new Room("r1", playerOne, playerTwo);
        assertEquals(playerOne, room.getPlayerById(playerOne.getNodeId()));
        assertEquals(playerTwo, room.getPlayerById(playerTwo.getNodeId()));
    }

    @Test
    void getPlayerById_unknownId_returnsNull() {
        Room room = new Room("r1", playerOne, playerTwo);
        assertNull(room.getPlayerById(UUID.randomUUID()));
    }

    @Test
    void getPlayerById_nullId_returnsNull() {
        Room room = new Room("r1", playerOne, playerTwo);
        assertNull(room.getPlayerById(null));
    }

    @Test
    void getOpponent_ofPlayerOne_returnsPlayerTwo() {
        Room room = new Room("r1", playerOne, playerTwo);
        assertEquals(playerTwo, room.getOpponent(playerOne.getNodeId()));
    }

    @Test
    void getOpponent_ofPlayerTwo_returnsPlayerOne() {
        Room room = new Room("r1", playerOne, playerTwo);
        assertEquals(playerOne, room.getOpponent(playerTwo.getNodeId()));
    }

    @Test
    void getOpponent_unknownId_returnsNull() {
        Room room = new Room("r1", playerOne, playerTwo);
        assertNull(room.getOpponent(UUID.randomUUID()));
    }

    @Test
    void setStartingPlayerId_storesValue() {
        Room room = new Room("r1", playerOne, playerTwo);
        UUID startId = playerOne.getNodeId();
        room.setStartingPlayerId(startId);
        assertEquals(startId, room.getStartingPlayerId());
    }

    @Test
    void setPlayerOne_updatesPlayer() {
        Room room = new Room("r1", playerOne, playerTwo);
        Client newPlayer = new Client("3.3.3.3", "Charlie");
        room.setPlayerOne(newPlayer);
        assertEquals(newPlayer, room.getPlayerOne());
    }
}
