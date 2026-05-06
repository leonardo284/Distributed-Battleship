package distributed.battleship.common.model.client;

import distributed.battleship.common.model.node.Node;
import distributed.battleship.common.model.room.Room;
import distributed.battleship.common.model.server.PrimaryServer;

import java.util.UUID;

/**
 * Pure domain model for a connected player.
 * Holds only player-specific state: display name, server reference and current room.
 */
public class Client extends Node {

    /** Display name chosen by the player. */
    private String name;

    /** Logical reference to the primary server this client talks to. */
    // transient to avoid Gson circular reference
    private transient PrimaryServer server;

    /** Current room where this client is playing. Null when out of game. */
    private transient Room room;

    /** Port where the peer opponent connects to this client. */
    private int peerConnectionPort;

    /** Port where the server connects back to this client. */
    private int serverConnectionPort;

    public Client(String ip, String name) {
        super(ip);
        this.name = name;
        this.serverConnectionPort = 0;
        this.peerConnectionPort = 0;
    }

    public Client(UUID id, String ip, String name) {
        super(id, ip);
        this.name = name;
        this.serverConnectionPort = 0;
        this.peerConnectionPort = 0;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public PrimaryServer getServer() {
        return server;
    }

    public void setServer(PrimaryServer server) {
        this.server = server;
    }

    public Room getRoom() {
        return room;
    }

    public void setRoom(Room room) {
        this.room = room;
    }

    public int getServerConnectionPort() {
        return serverConnectionPort;
    }

    public void setServerConnectionPort(int serverConnectionPort) {
        this.serverConnectionPort = serverConnectionPort;
    }

    public int getPeerConnectionPort() {
        return peerConnectionPort;
    }

    public void setPeerConnectionPort(int peerConnectionPort) {
        this.peerConnectionPort = peerConnectionPort;
    }

    /**
     * Returns the opponent client reference from the current room, when available.
     */
    public Client getOpponentFromRoom() {
        if (room == null) {
            return null;
        }
        UUID currentNodeId = getNodeId();
        if (room.getPlayerOne() != null && !currentNodeId.equals(room.getPlayerOne().getNodeId())) {
            return room.getPlayerOne();
        }
        if (room.getPlayerTwo() != null && !currentNodeId.equals(room.getPlayerTwo().getNodeId())) {
            return room.getPlayerTwo();
        }
        return null;
    }
}
