package distributed.battleship.common.model.room.grid;

/**
 * Represents one board cell tracked by position and a single logical state.
 */
public class Cell {
    public enum CellState {
        EMPTY,
        SHIP,
        HIT,
        SUNK,
        UNKNOW
    }

    private final Position position;
    private CellState state;

    public Cell(Position position) {
        this(position, CellState.EMPTY);
    }

    public Cell(Position position, CellState state) {
        this.position = position;
        this.state = state;
    }

    public Position getPosition() {
        return position;
    }

    public CellState getState() {
        return state;
    }

    void setState(CellState state) {
        this.state = state;
    }
}
