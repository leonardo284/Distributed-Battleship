package distributed.battleship.common.model.room;

import distributed.battleship.common.model.client.Client;

import java.util.UUID;

/**
 * Represents a game room between two players.
 */
public class Room {
    private final String roomId;
    private Client playerOne;
    private Client playerTwo;
    private UUID startingPlayerId;

    public Room(String roomId, Client playerOne, Client playerTwo) {
        this.roomId = roomId;
        this.playerOne = playerOne;
        this.playerTwo = playerTwo;
    }

    public Room(Client playerOne, Client playerTwo) {
        this(UUID.randomUUID().toString(), playerOne, playerTwo);
    }

    public String getRoomId() {
        return roomId;
    }

    public Client getPlayerOne() {
        return playerOne;
    }

    public void setPlayerOne(Client playerOne) {
        this.playerOne = playerOne;
    }

    public Client getPlayerTwo() {
        return playerTwo;
    }

    public void setPlayerTwo(Client playerTwo) {
        this.playerTwo = playerTwo;
    }

    public Client getPlayerById(UUID nodeId) {
        if (nodeId == null) {
            return null;
        }
        if (playerOne != null && nodeId.equals(playerOne.getNodeId())) {
            return playerOne;
        }
        if (playerTwo != null && nodeId.equals(playerTwo.getNodeId())) {
            return playerTwo;
        }
        return null;
    }

    public boolean hasTwoPlayers() {
        return playerOne != null && playerTwo != null;
    }

    /**
     * Returns the opponent of a given player in this room.
     *
     * @param playerNodeId the node ID of the player
     * @return the opponent client, or null if not found
     */
    public Client getOpponent(UUID playerNodeId) {
        if (playerOne != null && playerOne.getNodeId().equals(playerNodeId)) {
            return playerTwo;
        } else if (playerTwo != null && playerTwo.getNodeId().equals(playerNodeId)) {
            return playerOne;
        }
        return null;
    }


    public UUID getStartingPlayerId() {
        return startingPlayerId;
    }

    public void setStartingPlayerId(UUID startingPlayerId) {
        this.startingPlayerId = startingPlayerId;
    }
}
