package distributed.battleship.common.model.server;

import distributed.battleship.common.model.node.Node;
import distributed.battleship.common.model.room.Room;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Abstract domain model for a server node.
 * Holds only the IP address and the list of active game rooms.
 * Concrete subclasses ({@link PrimaryServer}, {@link BackupServer}) define
 * their own port semantics.
 */
public abstract class Server extends Node {

    private final List<Room> rooms = new ArrayList<>();

    protected Server(String ip) {
        super(ip);
    }

    protected Server(UUID nodeId, String ip) {
        super(nodeId, ip);
    }

    /**
     * Returns an unmodifiable view of the current room list.
     */
    public List<Room> getRooms() {
        return Collections.unmodifiableList(rooms);
    }

    /**
     * Adds a room to the server's room registry.
     */
    public synchronized void addRoom(Room room) {
        rooms.add(room);
    }

    public synchronized boolean removeRoomById(String roomId) {
        if (roomId == null || roomId.isBlank()) {
            return false;
        }
        return rooms.removeIf(room -> roomId.equals(room.getRoomId()));
    }

    /** Removes all rooms from this server. */
    public synchronized void clearRooms() {
        rooms.clear();
    }

    /**
     * Returns the role of this server at runtime.
     * {@link PrimaryServer} → {@link ServerType#PRIMARY},
     * {@link BackupServer} → {@link ServerType#BACKUP}.
     */
    public ServerType getServerType() {
        if (this instanceof PrimaryServer) {
            return ServerType.PRIMARY;
        }
        return ServerType.BACKUP;
    }
}
