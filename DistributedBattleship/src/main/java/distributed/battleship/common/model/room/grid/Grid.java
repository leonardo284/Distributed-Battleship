package distributed.battleship.common.model.room.grid;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Represents a player's Battleship game board.
 */
public class Grid {
    public static final int DEFAULT_WIDTH = 10;
    public static final int DEFAULT_HEIGHT = 10;
    private static final int[] DEFAULT_FLEET_SHIP_LENGTHS = {5, 4, 3, 3, 2};

    private final int width;
    private final int height;
    private final Cell[][] cells;
    private final Random random = new Random();

    public Grid() {
        this.width = DEFAULT_WIDTH;
        this.height = DEFAULT_HEIGHT;
        this.cells = new Cell[height][width];
        initialize();
    }

    private void initialize() {
        for (int x = 0; x < height; x++) {
            for (int y = 0; y < width; y++) {
                cells[x][y] = new Cell(new Position(x, y), Cell.CellState.EMPTY);
            }
        }
    }

    public boolean isInside(Position position) {
        return position != null
                && position.getX() >= 0
                && position.getX() < height
                && position.getY() >= 0
                && position.getY() < width;
    }

    public Cell.CellState getCell(Position position) {
        if (!isInside(position)) {
            throw new IllegalArgumentException("Position out of grid bounds: " + position);
        }
        Cell cell = cells[position.getX()][position.getY()];
        return cell.getState();
    }

    public void setCell(Position position, Cell.CellState state) {
        if (!isInside(position)) {
            throw new IllegalArgumentException("Position out of grid bounds: " + position);
        }
        Cell cell = cells[position.getX()][position.getY()];
        cell.setState(state);
    }

    public int getWidth() { return width; }

    public int getHeight() { return height; }

    public Cell[][] getCells() { return cells; }

    public List<Position> getShipPositions() {
        List<Position> shipPositions = new ArrayList<>();
        for (int x = 0; x < height; x++) {
            for (int y = 0; y < width; y++) {
                if (cells[x][y].getState() == Cell.CellState.SHIP || cells[x][y].getState() == Cell.CellState.SUNK) {
                    shipPositions.add(new Position(x, y));
                }
            }
        }
        return shipPositions;
    }

    public void setPlacementShips(List<Position> shipPositions) {
        setAllCellsState(Cell.CellState.EMPTY);

        if (shipPositions == null) return;

        for (Position shipPosition : shipPositions) {
            if (isInside(shipPosition)) {
                setCell(shipPosition, Cell.CellState.SHIP);
            }
        }
    }

    public boolean hit(Position position) {
        if (!isInside(position)) {
            throw new IllegalArgumentException("Position out of grid bounds: " + position);
        }

        Cell cell = cells[position.getX()][position.getY()];
        if (cell.getState() == Cell.CellState.SHIP) {
            cell.setState(Cell.CellState.SUNK);
            return true;
        }
        else if (cell.getState() == Cell.CellState.EMPTY || cell.getState() == Cell.CellState.UNKNOW) {
            cell.setState(Cell.CellState.HIT);
        }
        return false;
    }

    public boolean areAllShipsSunk() {
        for (int x = 0; x < height; x++) {
            for (int y = 0; y < width; y++) {
                if (cells[x][y].getState() == Cell.CellState.SHIP) return false;
            }
        }
        return true;
    }

    public void setAllCellsState(Cell.CellState state) {
        for (int x = 0; x < height; x++) {
            for (int y = 0; y < width; y++) {
                cells[x][y].setState(state);
            }
        }
    }

    public static int[] getDefaultFleetShipLengths() { return DEFAULT_FLEET_SHIP_LENGTHS.clone(); }

    /**
     * Checks whether a ship of the given length can be placed starting at {@code start}.
     *
     * @param start      top-left/top cell of the ship
     * @param length     number of cells the ship occupies
     * @param horizontal {@code true} for horizontal placement, {@code false} for vertical
     * @return {@code true} if all cells are inside the grid and currently empty
     */
    public boolean canPlaceShip(Position start, int length, boolean horizontal) {
        for (int i = 0; i < length; i++) {
            Position pos = horizontal
                    ? new Position(start.getX(), start.getY() + i)
                    : new Position(start.getX() + i, start.getY());
            if (!isInside(pos)
                    || cells[pos.getX()][pos.getY()].getState() != Cell.CellState.EMPTY
                    || hasAdjacentShip(pos)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true if any neighboring cell (including diagonals) already contains a ship.
     */
    private boolean hasAdjacentShip(Position position) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                int nx = position.getX() + dx;
                int ny = position.getY() + dy;
                Position neighbor = new Position(nx, ny);
                if (isInside(neighbor)
                        && (cells[nx][ny].getState() == Cell.CellState.SHIP
                        || cells[nx][ny].getState() == Cell.CellState.SUNK)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Places a ship on the grid if the placement is valid.
     *
     * @param start top-left/top cell of the ship
     * @param length number of cells the ship occupies
     * @param horizontal {@code true} for horizontal placement, {@code false} for vertical
     * @return {@code true} if the ship was placed, {@code false} if the placement was invalid
     */
    public boolean placeShip(Position start, int length, boolean horizontal) {
        if (!canPlaceShip(start, length, horizontal)) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            Position pos = horizontal
                    ? new Position(start.getX(), start.getY() + i)
                    : new Position(start.getX() + i, start.getY());
            cells[pos.getX()][pos.getY()].setState(Cell.CellState.SHIP);
        }
        return true;
    }

    /**
     * Places remaining ships randomly starting from the given fleet index.
     *
     * @param shipLengths ordered fleet ship lengths (e.g. [5,4,3,3,2])
     * @param fromIndex first fleet index to auto-place
     * @return number of ships successfully placed
     */
    public int placeRemainingShipsRandomly(int[] shipLengths, int fromIndex) {
        if (shipLengths == null || fromIndex < 0 || fromIndex >= shipLengths.length) {
            return 0;
        }

        int placedShips = 0;
        for (int i = fromIndex; i < shipLengths.length; i++) {
            int shipLength = shipLengths[i];
            boolean placed = false;

            // Bounded retries to avoid infinite loops on impossible layouts.
            for (int attempt = 0; attempt < 1000 && !placed; attempt++) {
                boolean horizontal = random.nextBoolean();
                int maxX = horizontal ? height - 1 : height - shipLength;
                int maxY = horizontal ? width - shipLength : width - 1;
                if (maxX < 0 || maxY < 0) {
                    break;
                }

                Position start = new Position(random.nextInt(maxX + 1), random.nextInt(maxY + 1));
                placed = placeShip(start, shipLength, horizontal);
            }

            if (!placed) {
                break;
            }
            placedShips++;
        }

        return placedShips;
    }

    /**
     * Chooses a random available position to shoot at (not HIT or SUNK).
     *
     * @return a random available position, or null if no positions are available
     */
    public Position chooseRandomAvailableShotTarget() {
        List<Position> availableTargets = new ArrayList<>();
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                Cell.CellState state = cells[row][col].getState();
                if (state != Cell.CellState.HIT && state != Cell.CellState.SUNK) {
                    availableTargets.add(new Position(row, col));
                }
            }
        }

        if (availableTargets.isEmpty()) {
            return null;
        }

        int randomIndex = random.nextInt(availableTargets.size());
        return availableTargets.get(randomIndex);
    }

    /**
     * Checks if the cell at the given position is already targeted (HIT or SUNK).
     *
     * @param position the position to check
     * @return true if the cell is HIT or SUNK, false otherwise
     */
    public boolean isAlreadyTargetedCell(Position position) {
        if (!isInside(position)) {
            return false;
        }
        Cell.CellState state = cells[position.getX()][position.getY()].getState();
        return state == Cell.CellState.HIT || state == Cell.CellState.SUNK;
    }
}
