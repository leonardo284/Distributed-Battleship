package distributed.battleship.client.view;

/**
 * Handler for ship placement timeout - invoked when the placement phase timer expires.
 */
@FunctionalInterface
public interface PlacementTimeoutHandler {
    /**
     * Handles automatic ship placement when the placement phase time expires.
     *
     * @param fromShipIndex the starting index in the fleet to continue placement from
     * @param fleetShipLengths array of ship lengths to place
     * @return the number of ships successfully placed
     */
    int handleTimeout(int fromShipIndex, int[] fleetShipLengths);
}
