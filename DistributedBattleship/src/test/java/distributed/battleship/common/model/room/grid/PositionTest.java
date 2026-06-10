package distributed.battleship.common.model.room.grid;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PositionTest {

    @Test
    void constructor_setsXAndY() {
        Position pos = new Position(3, 7);
        assertEquals(3, pos.getX());
        assertEquals(7, pos.getY());
    }

    @Test
    void setX_updatesX() {
        Position pos = new Position(0, 0);
        pos.setX(5);
        assertEquals(5, pos.getX());
    }

    @Test
    void setY_updatesY() {
        Position pos = new Position(0, 0);
        pos.setY(9);
        assertEquals(9, pos.getY());
    }

    @Test
    void toString_containsCoordinates() {
        Position pos = new Position(2, 8);
        String s = pos.toString();
        assertTrue(s.contains("2"));
        assertTrue(s.contains("8"));
    }
}
