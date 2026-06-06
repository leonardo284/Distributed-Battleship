package distributed.battleship.common.model.client;

import distributed.battleship.common.model.room.Room;
import distributed.battleship.common.model.room.grid.Cell;
import distributed.battleship.common.model.room.grid.Grid;
import distributed.battleship.common.model.room.grid.Position;

import java.util.UUID;

/**
 * Client-side room model that extends the shared room with local match state.
 */
public class PlayingRoom extends Room {

    private Client currentClient;
    private Client opponent;
    private Grid currentGrid;
    private Grid opponentGrid;
    private UUID turnPlayerId;
    private boolean lastShotWon;

    public PlayingRoom(String roomId, Client currentClient, Client opponent) {
        this(roomId, currentClient, opponent, new Grid(), new Grid());
    }

    public PlayingRoom(String roomId, Client currentClient, Client opponent, Grid currentGrid, Grid opponentGrid) {
        super(roomId, currentClient, opponent);
        this.currentClient = currentClient;
        this.opponent = opponent;
        this.currentGrid = currentGrid;
        this.opponentGrid = opponentGrid;
        if (this.currentGrid != null) {
            this.currentGrid.setAllCellsState(Cell.CellState.EMPTY);
        }
        if (this.opponentGrid != null) {
            this.opponentGrid.setAllCellsState(Cell.CellState.UNKNOW);
        }
    }

    public PlayingRoom(Client currentClient, Client opponent) {
        this(java.util.UUID.randomUUID().toString(), currentClient, opponent);
    }

    public Client getCurrentClient() {
        return currentClient;
    }

    public void setCurrentClient(Client currentClient) {
        this.currentClient = currentClient;
        super.setPlayerOne(currentClient);
    }

    public Client getOpponent() {
        return opponent;
    }

    public void setOpponent(Client opponent) {
        this.opponent = opponent;
        super.setPlayerTwo(opponent);
    }

    public Grid getCurrentGrid() {
        return currentGrid;
    }

    public void setCurrentGrid(Grid currentGrid) {
        this.currentGrid = currentGrid;
        if (this.currentGrid != null) {
            this.currentGrid.setAllCellsState(Cell.CellState.EMPTY);
        }
    }

    public Grid getOpponentGrid() {
        return opponentGrid;
    }

    public void setOpponentGrid(Grid opponentGrid) {
        this.opponentGrid = opponentGrid;
        if (this.opponentGrid != null) {
            this.opponentGrid.setAllCellsState(Cell.CellState.UNKNOW);
        }
    }

    public UUID getCurrentId() {
        return currentClient != null ? currentClient.getNodeId() : null;
    }

    public String getCurrentName() {
        return currentClient != null ? currentClient.getName() : null;
    }

    public UUID getOpponentId() {
        return opponent != null ? opponent.getNodeId() : null;
    }

    public String getOpponentName() {
        return opponent != null ? opponent.getName() : null;
    }

    @Override
    public void setStartingPlayerId(UUID startingPlayerId) {
        super.setStartingPlayerId(startingPlayerId);
        this.turnPlayerId = startingPlayerId;
    }

    public UUID getTurnPlayerId() {
        return turnPlayerId;
    }

    /**
     * Applies a shot and advances the turn.
     */
    public boolean fireShot(Position position) {
        Grid targetGrid;
        boolean hit;
        if (turnPlayerId != null && turnPlayerId.equals(currentClient.getNodeId())) {
            targetGrid = opponentGrid;
            hit = opponentGrid.hit(position);
        } else {
            targetGrid = currentGrid;
            hit = currentGrid.hit(position);
        }

        lastShotWon = targetGrid != null && targetGrid.areAllShipsSunk();

        if (!hit) {
            changeTurn();
        }
        return hit;
    }

    public boolean isLastShotWon() {
        return lastShotWon;
    }

    private void changeTurn() {
        if (turnPlayerId == null || currentClient == null || opponent == null) {
            return;
        }
        turnPlayerId = turnPlayerId.equals(currentClient.getNodeId())
                ? opponent.getNodeId()
                : currentClient.getNodeId();
    }
}
