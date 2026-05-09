package distributed.battleship.common.model.message;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

/**
 * Shared protocol message tuples exchanged between the application nodes.
 */
public final class MessageConstants {

    private MessageConstants() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** Protocol message types exchanged across the distributed system. */
    public enum MessageType {
        CS_REQUEST_ENTER_ROOM_ID(CSRequestEnterRoomId.class),
        CS_REQUEST_JOIN_ROOM(CSRequestJoinRoom.class),
        CS_REQUEST_HEARTBEAT(CSRequestHeartbeat.class),
        CS_RESPONSE_CREATE_ROOM(CSResponseCreateRoom.class),
        CS_RESPONSE_JOIN_ROOM(CSResponseJoinRoom.class),
        CS_RESPONSE_HEARTBEAT(CSResponseHeartbeat.class),
        CS_ROOM_OPENED(CSRoomOpened.class),
        CS_ROOM_CLOSED(CSRoomClosed.class),
        CS_ROOM_INTERRUPTED(CSRoomInterrupted.class),
        CS_ROOM_TIMEOUT(CSRoomTimeout.class),
        CS_ROOM_TIMEOUT_ACK(CSRoomTimeoutAck.class),
        CS_CLIENT_EXIT(CSClientExit.class),
        CS_CLIENT_RECONNECTED(CSClientReconnected.class),
        CS_BACKUP_JOINED(CSBackupJoined.class),
        CS_BACKUP_EXIT(CSBackupExit.class),

        SS_HELLO_FROM_BACKUP(SSHelloFromBackup.class),
        SS_RESPONSE_HELLO(SSResponseHello.class),
        SS_SEND_STATE_TO_BACKUP(SSSendStateToBackup.class),
        SS_ACK(SSAck.class),
        SS_BACKUP_EXIT(SSBackupExit.class),

        PP_CONNECT(PPConnect.class),
        PP_READY(PPReady.class),
        PP_START(PPStart.class),
        PP_SHOT(PPShot.class),
        PP_HITTED(PPHitted.class),
        PP_MISSED(PPMissed.class),
        PP_WIN(PPWin.class),
        PP_EXIT(PPExit.class);

        private final Class<? extends MessageTuple> recordClass;

        MessageType(Class<? extends MessageTuple> recordClass) {
            this.recordClass = recordClass;
        }

        public Class<? extends MessageTuple> getRecordClass() {
            return recordClass;
        }
    }

    /** Base contract for typed protocol tuples. */
    public interface MessageTuple extends Serializable {
        MessageType getType();
        UUID senderNodeId();
    }

    // ---------------------------------------------------------------------
    // Client-server messages
    // ---------------------------------------------------------------------

    public record CSRequestEnterRoomId(UUID senderNodeId, String playerName, String roomId) implements MessageTuple {
        @Override public MessageType getType() { return MessageType.CS_REQUEST_ENTER_ROOM_ID; }
    }

    public record CSRequestJoinRoom(UUID senderNodeId, String playerName, String peerIp, int peerConnectionPort, int serverConnectionPort) implements MessageTuple {
        @Override public MessageType getType() { return MessageType.CS_REQUEST_JOIN_ROOM; }
    }

    public record CSRequestHeartbeat(UUID senderNodeId) implements MessageTuple {
        @Override public MessageType getType() { return MessageType.CS_REQUEST_HEARTBEAT; }
    }

    public record CSResponseCreateRoom(UUID senderNodeId, String roomId, List<BackupServer> connectedBackups) implements MessageTuple {
        @Override public MessageType getType() { return MessageType.CS_RESPONSE_CREATE_ROOM; }
    }

    public record CSResponseJoinRoom(
            UUID senderNodeId,
            String roomId,
            String opponentName,
            String opponentIp,
            int opponentPort,
            UUID opponentNodeId,
            UUID startingPlayerId,
            List<BackupServer> connectedBackups
    ) implements MessageTuple {
        @Override public MessageType getType() { return MessageType.CS_RESPONSE_JOIN_ROOM; }
    }

    public record CSResponseHeartbeat(UUID senderNodeId) implements MessageTuple {
        @Override public MessageType getType() { return MessageType.CS_RESPONSE_HEARTBEAT; }
    }

    public record CSRoomOpened(UUID senderNodeId, String roomId, String playerName) implements MessageTuple {
        @Override public MessageType getType() { return MessageType.CS_ROOM_OPENED; }
    }

    public record CSRoomClosed(UUID senderNodeId, String roomId, String playerName) implements MessageTuple {
        @Override public MessageType getType() { return MessageType.CS_ROOM_CLOSED; }
    }

    public record CSRoomInterrupted(UUID senderNodeId, String roomId, String playerName) implements MessageTuple {
        @Override public MessageType getType() { return MessageType.CS_ROOM_INTERRUPTED; }
    }

    public record CSRoomTimeout(UUID senderNodeId, String roomId, String playerName) implements MessageTuple {
        @Override public MessageType getType() { return MessageType.CS_ROOM_TIMEOUT; }
    }

    public record CSRoomTimeoutAck(UUID senderNodeId, String roomId, String playerName) implements MessageTuple {
        @Override public MessageType getType() { return MessageType.CS_ROOM_TIMEOUT_ACK; }
    }

    public record CSClientExit(UUID senderNodeId, String roomId, String playerName) implements MessageTuple {
        @Override public MessageType getType() { return MessageType.CS_CLIENT_EXIT; }
    }

    public record CSClientReconnected(UUID senderNodeId, String roomId, String playerName) implements MessageTuple {
        @Override public MessageType getType() { return MessageType.CS_CLIENT_RECONNECTED; }
    }

    public record CSBackupJoined(UUID senderNodeId, String backupIp, int backupPort) implements MessageTuple {
        @Override public MessageType getType() { return MessageType.CS_BACKUP_JOINED; }
    }

    public record CSBackupExit(UUID senderNodeId, String backupIp, int backupPort) implements MessageTuple {
        @Override public MessageType getType() { return MessageType.CS_BACKUP_EXIT; }
    }
}
