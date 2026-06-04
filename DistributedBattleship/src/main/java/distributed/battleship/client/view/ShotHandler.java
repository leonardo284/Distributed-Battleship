package distributed.battleship.client.view;

import distributed.battleship.common.model.room.grid.Position;

/**
 * Handler for opponent cell shot selection during the battle phase.
 */
@FunctionalInterface
public interface ShotHandler {
    /**
     * Fires a shot at the specified target position on the opponent's board.
     *
     * @param target the target position to fire at
     * @return true if the shot was successful (hit), false if missed
     */
    boolean fireShot(Position target);
}
