package distributed.battleship.common.model.room.grid;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CellTest {

    @Test
    void constructorWithPosition_defaultStateIsEmpty() {
        Cell cell = new Cell(new Position(0, 0));
        assertEquals(Cell.CellState.EMPTY, cell.getState());
        assertNotNull(cell.getPosition());
    }

    @Test
    void constructorWithPositionAndState_setsState() {
        Cell cell = new Cell(new Position(1, 2), Cell.CellState.SHIP);
        assertEquals(Cell.CellState.SHIP, cell.getState());
        assertEquals(1, cell.getPosition().getX());
        assertEquals(2, cell.getPosition().getY());
    }

    @Test
    void getPosition_returnsCorrectPosition() {
        Position pos = new Position(4, 6);
        Cell cell = new Cell(pos);
        assertEquals(pos, cell.getPosition());
    }

    @Test
    void allCellStates_areAccessible() {
        for (Cell.CellState state : Cell.CellState.values()) {
            Cell cell = new Cell(new Position(0, 0), state);
            assertEquals(state, cell.getState());
        }
    }
}
