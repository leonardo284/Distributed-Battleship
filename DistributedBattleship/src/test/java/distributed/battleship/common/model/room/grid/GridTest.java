package distributed.battleship.common.model.room.grid;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GridTest {

    private Grid grid;

    @BeforeEach
    void setUp() {
        grid = new Grid();
    }

    // -----------------------------------------------------------------------
    // Basic construction
    // -----------------------------------------------------------------------

    @Test
    void newGrid_hasCorrectedDimensions() {
        assertEquals(Grid.DEFAULT_WIDTH, grid.getWidth());
        assertEquals(Grid.DEFAULT_HEIGHT, grid.getHeight());
    }

    @Test
    void newGrid_allCellsAreEmpty() {
        for (Cell[] row : grid.getCells()) {
            for (Cell cell : row) {
                assertEquals(Cell.CellState.EMPTY, cell.getState());
            }
        }
    }

    // -----------------------------------------------------------------------
    // isInside
    // -----------------------------------------------------------------------

    @Test
    void isInside_topLeftCorner_returnsTrue() {
        assertTrue(grid.isInside(new Position(0, 0)));
    }

    @Test
    void isInside_bottomRightCorner_returnsTrue() {
        assertTrue(grid.isInside(new Position(9, 9)));
    }

    @Test
    void isInside_negativeCoordinates_returnsFalse() {
        assertFalse(grid.isInside(new Position(-1, 0)));
        assertFalse(grid.isInside(new Position(0, -1)));
    }

    @Test
    void isInside_outOfBounds_returnsFalse() {
        assertFalse(grid.isInside(new Position(10, 0)));
        assertFalse(grid.isInside(new Position(0, 10)));
    }

    @Test
    void isInside_nullPosition_returnsFalse() {
        assertFalse(grid.isInside(null));
    }

    // -----------------------------------------------------------------------
    // getCell / setCell
    // -----------------------------------------------------------------------

    @Test
    void getCell_validPosition_returnsEmptyByDefault() {
        assertEquals(Cell.CellState.EMPTY, grid.getCell(new Position(3, 4)));
    }

    @Test
    void setCell_changesState() {
        Position pos = new Position(2, 5);
        grid.setCell(pos, Cell.CellState.SHIP);
        assertEquals(Cell.CellState.SHIP, grid.getCell(pos));
    }

    @Test
    void getCell_outOfBounds_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> grid.getCell(new Position(-1, 0)));
    }

    @Test
    void setCell_outOfBounds_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> grid.setCell(new Position(10, 0), Cell.CellState.SHIP));
    }

    // -----------------------------------------------------------------------
    // hit
    // -----------------------------------------------------------------------

    @Test
    void hit_onShipCell_returnsTrueAndStateIsSunk() {
        Position pos = new Position(1, 1);
        grid.setCell(pos, Cell.CellState.SHIP);
        assertTrue(grid.hit(pos));
        assertEquals(Cell.CellState.SUNK, grid.getCell(pos));
    }

    @Test
    void hit_onEmptyCell_returnsFalseAndStateIsHit() {
        Position pos = new Position(4, 4);
        assertFalse(grid.hit(pos));
        assertEquals(Cell.CellState.HIT, grid.getCell(pos));
    }

    @Test
    void hit_outOfBounds_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> grid.hit(new Position(10, 10)));
    }

    // -----------------------------------------------------------------------
    // areAllShipsSunk
    // -----------------------------------------------------------------------

    @Test
    void areAllShipsSunk_noShips_returnsTrue() {
        assertTrue(grid.areAllShipsSunk());
    }

    @Test
    void areAllShipsSunk_withUnsunkShip_returnsFalse() {
        grid.setCell(new Position(0, 0), Cell.CellState.SHIP);
        assertFalse(grid.areAllShipsSunk());
    }

    @Test
    void areAllShipsSunk_afterHittingAllShips_returnsTrue() {
        Position pos = new Position(5, 5);
        grid.setCell(pos, Cell.CellState.SHIP);
        grid.hit(pos);
        assertTrue(grid.areAllShipsSunk());
    }

    // -----------------------------------------------------------------------
    // setPlacementShips
    // -----------------------------------------------------------------------

    @Test
    void setPlacementShips_marksCellsAsShip() {
        List<Position> positions = List.of(new Position(0, 0), new Position(0, 1), new Position(0, 2));
        grid.setPlacementShips(positions);
        assertEquals(Cell.CellState.SHIP, grid.getCell(new Position(0, 0)));
        assertEquals(Cell.CellState.SHIP, grid.getCell(new Position(0, 1)));
        assertEquals(Cell.CellState.SHIP, grid.getCell(new Position(0, 2)));
    }

    @Test
    void setPlacementShips_clearsPreviousShips() {
        grid.setCell(new Position(9, 9), Cell.CellState.SHIP);
        grid.setPlacementShips(List.of(new Position(0, 0)));
        assertEquals(Cell.CellState.EMPTY, grid.getCell(new Position(9, 9)));
        assertEquals(Cell.CellState.SHIP, grid.getCell(new Position(0, 0)));
    }

    @Test
    void setPlacementShips_nullList_clearsAllShips() {
        grid.setCell(new Position(3, 3), Cell.CellState.SHIP);
        grid.setPlacementShips(null);
        assertEquals(Cell.CellState.EMPTY, grid.getCell(new Position(3, 3)));
    }

    // -----------------------------------------------------------------------
    // getShipPositions
    // -----------------------------------------------------------------------

    @Test
    void getShipPositions_returnsOnlyShipAndSunkCells() {
        grid.setCell(new Position(0, 0), Cell.CellState.SHIP);
        grid.setCell(new Position(1, 1), Cell.CellState.SUNK);
        grid.setCell(new Position(2, 2), Cell.CellState.HIT);

        List<Position> ships = grid.getShipPositions();
        assertEquals(2, ships.size());
    }

    // -----------------------------------------------------------------------
    // setAllCellsState
    // -----------------------------------------------------------------------

    @Test
    void setAllCellsState_setsEveryCell() {
        grid.setAllCellsState(Cell.CellState.SHIP);
        for (Cell[] row : grid.getCells()) {
            for (Cell cell : row) {
                assertEquals(Cell.CellState.SHIP, cell.getState());
            }
        }
    }

    // -----------------------------------------------------------------------
    // getDefaultFleetShipLengths
    // -----------------------------------------------------------------------

    @Test
    void getDefaultFleetShipLengths_returnsDefensiveCopy() {
        int[] first = Grid.getDefaultFleetShipLengths();
        int[] second = Grid.getDefaultFleetShipLengths();
        assertNotSame(first, second);
        assertArrayEquals(first, second);
    }
}
