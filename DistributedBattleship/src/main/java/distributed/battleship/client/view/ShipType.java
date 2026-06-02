package distributed.battleship.client.view;

/** Defines the fleet that each player must place at the start of a game. */
public enum ShipType {
    CARRIER ("Carrier", 5),
    BATTLESHIP ("Battleship", 4),
    CRUISER ("Cruiser", 3),
    SUBMARINE ("Submarine", 3),
    DESTROYER ("Destroyer", 2);

    public final String displayName;
    public final int size;

    ShipType(String displayName, int size) {
        this.displayName = displayName;
        this.size = size;
    }
}
