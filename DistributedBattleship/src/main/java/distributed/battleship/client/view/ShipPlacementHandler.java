package distributed.battleship.client.view;

import distributed.battleship.common.model.room.grid.Position;

/**
 * Handler for ship placement requests from the user.
 */
@FunctionalInterface
public interface ShipPlacementHandler {
    /**
     * Attempts to place a ship at the specified position with the given length and orientation.
     *
     * @param start the starting position of the ship
     * @param length the length of the ship
     * @param horizontal true for horizontal, false for vertical
     * @return true if placement was successful, false otherwise
     */
    boolean placeShip(Position start, int length, boolean horizontal);
}

