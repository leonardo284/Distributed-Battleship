package distributed.battleship.common.model.client;

import distributed.battleship.common.model.room.grid.Cell;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayingRoomTest {

    private Client current;
    private Client opponent;

    @BeforeEach
    void setUp() {
        current = new Client("1.1.1.1", "Alice");
        opponent = new Client("2.2.2.2", "Bob");
    }

    @Test
    void constructor_setsPlayersAndRoomId() {
        PlayingRoom room = new PlayingRoom("room-1", current, opponent);
        assertEquals("room-1", room.getRoomId());
        assertEquals(current, room.getCurrentClient());
        assertEquals(opponent, room.getOpponent());
    }

    @Test
    void constructor_currentGridInitializedEmpty() {
        PlayingRoom room = new PlayingRoom("room-1", current, opponent);
        assertNotNull(room.getCurrentGrid());
        // All cells in the current grid must be EMPTY
        for (var row : room.getCurrentGrid().getCells()) {
            for (var cell : row) {
                assertEquals(Cell.CellState.EMPTY, cell.getState());
            }
        }
    }

    @Test
    void constructor_opponentGridInitializedUnknown() {
        PlayingRoom room = new PlayingRoom("room-1", current, opponent);
        assertNotNull(room.getOpponentGrid());
        // All cells in the opponent grid must be UNKNOW
        for (var row : room.getOpponentGrid().getCells()) {
            for (var cell : row) {
                assertEquals(Cell.CellState.UNKNOW, cell.getState());
            }
        }
    }

    @Test
    void constructorWithoutRoomId_generatesId() {
        PlayingRoom room = new PlayingRoom(current, opponent);
        assertNotNull(room.getRoomId());
        assertFalse(room.getRoomId().isBlank());
    }

    @Test
    void getCurrentId_returnsCurrentClientNodeId() {
        PlayingRoom room = new PlayingRoom("r1", current, opponent);
        assertEquals(current.getNodeId(), room.getCurrentId());
    }

    @Test
    void getOpponentId_returnsOpponentNodeId() {
        PlayingRoom room = new PlayingRoom("r1", current, opponent);
        assertEquals(opponent.getNodeId(), room.getOpponentId());
    }

    @Test
    void getCurrentName_returnsCurrentClientName() {
        PlayingRoom room = new PlayingRoom("r1", current, opponent);
        assertEquals("Alice", room.getCurrentName());
    }

    @Test
    void getOpponentName_returnsOpponentName() {
        PlayingRoom room = new PlayingRoom("r1", current, opponent);
        assertEquals("Bob", room.getOpponentName());
    }

    @Test
    void setCurrentClient_updatesPlayerOneAndCurrentClient() {
        PlayingRoom room = new PlayingRoom("r1", current, opponent);
        Client newClient = new Client("3.3.3.3", "Charlie");
        room.setCurrentClient(newClient);
        assertEquals(newClient, room.getCurrentClient());
        assertEquals(newClient, room.getPlayerOne());
    }

    @Test
    void setOpponent_updatesPlayerTwoAndOpponent() {
        PlayingRoom room = new PlayingRoom("r1", current, opponent);
        Client newOpponent = new Client("4.4.4.4", "Dave");
        room.setOpponent(newOpponent);
        assertEquals(newOpponent, room.getOpponent());
        assertEquals(newOpponent, room.getPlayerTwo());
    }
}
