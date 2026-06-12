package distributed.battleship.common.helper;

import distributed.battleship.common.model.message.MessageConstants;
import distributed.battleship.common.model.room.grid.Position;
import distributed.battleship.common.model.server.BackupServer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ProtocolMessageJsonHelper}: verifies that every message type
 * can be serialized to JSON and deserialized back to the correct record type
 * with all fields preserved (round-trip).
 */
class ProtocolMessageJsonHelperTest {

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private static MessageConstants.MessageTuple roundTrip(MessageConstants.MessageTuple msg) {
        String json = ProtocolMessageJsonHelper.serialize(msg);
        return ProtocolMessageJsonHelper.deserialize(json);
    }

    // -----------------------------------------------------------------------
    // Client-Server messages
    // -----------------------------------------------------------------------

    @Test
    void csRequestEnterRoomId_roundTrip() {
        UUID id = UUID.randomUUID();
        var original = new MessageConstants.CSRequestEnterRoomId(id, "Alice", "room-1");
        var result = (MessageConstants.CSRequestEnterRoomId) roundTrip(original);

        assertEquals(MessageConstants.MessageType.CS_REQUEST_ENTER_ROOM_ID, result.getType());
        assertEquals(id, result.senderNodeId());
        assertEquals("Alice", result.playerName());
        assertEquals("room-1", result.roomId());
    }

    @Test
    void csRequestJoinRoom_roundTrip() {
        UUID id = UUID.randomUUID();
        var original = new MessageConstants.CSRequestJoinRoom(id, "Bob", "10.0.0.1", 8080, 9090);
        var result = (MessageConstants.CSRequestJoinRoom) roundTrip(original);

        assertEquals(MessageConstants.MessageType.CS_REQUEST_JOIN_ROOM, result.getType());
        assertEquals(id, result.senderNodeId());
        assertEquals("Bob", result.playerName());
        assertEquals("10.0.0.1", result.peerIp());
        assertEquals(8080, result.peerConnectionPort());
        assertEquals(9090, result.serverConnectionPort());
    }

    @Test
    void csRequestHeartbeat_roundTrip() {
        UUID id = UUID.randomUUID();
        var original = new MessageConstants.CSRequestHeartbeat(id);
        var result = (MessageConstants.CSRequestHeartbeat) roundTrip(original);

        assertEquals(MessageConstants.MessageType.CS_REQUEST_HEARTBEAT, result.getType());
        assertEquals(id, result.senderNodeId());
    }

    @Test
    void csResponseCreateRoom_roundTrip() {
        UUID id = UUID.randomUUID();
        BackupServer backup = new BackupServer(UUID.randomUUID(), "5.5.5.5", 7000);
        var original = new MessageConstants.CSResponseCreateRoom(id, "room-99", List.of(backup));
        var result = (MessageConstants.CSResponseCreateRoom) roundTrip(original);

        assertEquals(MessageConstants.MessageType.CS_RESPONSE_CREATE_ROOM, result.getType());
        assertEquals(id, result.senderNodeId());
        assertEquals("room-99", result.roomId());
        assertEquals(1, result.connectedBackups().size());
        assertEquals("5.5.5.5", result.connectedBackups().get(0).getIp());
    }

    @Test
    void csResponseJoinRoom_roundTrip() {
        UUID sender = UUID.randomUUID();
        UUID opponentId = UUID.randomUUID();
        UUID startingId = UUID.randomUUID();
        var original = new MessageConstants.CSResponseJoinRoom(
                sender, "room-7", "Charlie", "3.3.3.3", 5001, opponentId, startingId, List.of());
        var result = (MessageConstants.CSResponseJoinRoom) roundTrip(original);

        assertEquals(MessageConstants.MessageType.CS_RESPONSE_JOIN_ROOM, result.getType());
        assertEquals(sender, result.senderNodeId());
        assertEquals("room-7", result.roomId());
        assertEquals("Charlie", result.opponentName());
        assertEquals("3.3.3.3", result.opponentIp());
        assertEquals(5001, result.opponentPort());
        assertEquals(opponentId, result.opponentNodeId());
        assertEquals(startingId, result.startingPlayerId());
    }

    @Test
    void csResponseHeartbeat_roundTrip() {
        UUID id = UUID.randomUUID();
        var result = (MessageConstants.CSResponseHeartbeat)
                roundTrip(new MessageConstants.CSResponseHeartbeat(id));
        assertEquals(id, result.senderNodeId());
    }

    @Test
    void csRoomOpened_roundTrip() {
        UUID id = UUID.randomUUID();
        var result = (MessageConstants.CSRoomOpened)
                roundTrip(new MessageConstants.CSRoomOpened(id, "r1", "Dave"));
        assertEquals("r1", result.roomId());
        assertEquals("Dave", result.playerName());
    }

    @Test
    void csRoomClosed_roundTrip() {
        UUID id = UUID.randomUUID();
        var result = (MessageConstants.CSRoomClosed)
                roundTrip(new MessageConstants.CSRoomClosed(id, "r1", "Eve"));
        assertEquals(MessageConstants.MessageType.CS_ROOM_CLOSED, result.getType());
    }

    @Test
    void csRoomInterrupted_roundTrip() {
        UUID id = UUID.randomUUID();
        var result = (MessageConstants.CSRoomInterrupted)
                roundTrip(new MessageConstants.CSRoomInterrupted(id, "r2", "Frank"));
        assertEquals("r2", result.roomId());
    }

    @Test
    void csRoomTimeout_roundTrip() {
        UUID id = UUID.randomUUID();
        var result = (MessageConstants.CSRoomTimeout)
                roundTrip(new MessageConstants.CSRoomTimeout(id, "r3", "Grace"));
        assertEquals(MessageConstants.MessageType.CS_ROOM_TIMEOUT, result.getType());
    }

    @Test
    void csRoomTimeoutAck_roundTrip() {
        UUID id = UUID.randomUUID();
        var result = (MessageConstants.CSRoomTimeoutAck)
                roundTrip(new MessageConstants.CSRoomTimeoutAck(id, "r3", "Grace"));
        assertEquals(MessageConstants.MessageType.CS_ROOM_TIMEOUT_ACK, result.getType());
    }

    @Test
    void csClientExit_roundTrip() {
        UUID id = UUID.randomUUID();
        var result = (MessageConstants.CSClientExit)
                roundTrip(new MessageConstants.CSClientExit(id, "r4", "Heidi"));
        assertEquals("Heidi", result.playerName());
    }

    @Test
    void csClientReconnected_roundTrip() {
        UUID id = UUID.randomUUID();
        var result = (MessageConstants.CSClientReconnected)
                roundTrip(new MessageConstants.CSClientReconnected(id, "r4", "Ivan"));
        assertEquals(MessageConstants.MessageType.CS_CLIENT_RECONNECTED, result.getType());
    }

    @Test
    void csBackupJoined_roundTrip() {
        UUID id = UUID.randomUUID();
        var result = (MessageConstants.CSBackupJoined)
                roundTrip(new MessageConstants.CSBackupJoined(id, "6.6.6.6", 4000));
        assertEquals("6.6.6.6", result.backupIp());
        assertEquals(4000, result.backupPort());
    }

    @Test
    void csBackupExit_roundTrip() {
        UUID id = UUID.randomUUID();
        var result = (MessageConstants.CSBackupExit)
                roundTrip(new MessageConstants.CSBackupExit(id, "6.6.6.6", 4001));
        assertEquals(MessageConstants.MessageType.CS_BACKUP_EXIT, result.getType());
    }

    // -----------------------------------------------------------------------
    // Server-Server messages
    // -----------------------------------------------------------------------

    @Test
    void ssHelloFromBackup_roundTrip() {
        UUID id = UUID.randomUUID();
        var result = (MessageConstants.SSHelloFromBackup)
                roundTrip(new MessageConstants.SSHelloFromBackup(id, "7.7.7.7", 3000));
        assertEquals("7.7.7.7", result.backupIp());
        assertEquals(3000, result.backupPort());
    }

    @Test
    void ssResponseHello_roundTrip() {
        UUID id = UUID.randomUUID();
        var result = (MessageConstants.SSResponseHello)
                roundTrip(new MessageConstants.SSResponseHello(id, 2));
        assertEquals(2, result.order());
    }

    @Test
    void ssAck_roundTrip() {
        UUID id = UUID.randomUUID();
        var result = (MessageConstants.SSAck) roundTrip(new MessageConstants.SSAck(id));
        assertEquals(id, result.senderNodeId());
    }

    @Test
    void ssBackupExit_roundTrip() {
        UUID id = UUID.randomUUID();
        var result = (MessageConstants.SSBackupExit)
                roundTrip(new MessageConstants.SSBackupExit(id, "8.8.8.8", 5500));
        assertEquals("8.8.8.8", result.backupIp());
        assertEquals(5500, result.backupPort());
    }

    // -----------------------------------------------------------------------
    // Peer-to-peer messages
    // -----------------------------------------------------------------------

    @Test
    void ppConnect_roundTrip() {
        UUID sender = UUID.randomUUID();
        UUID starting = UUID.randomUUID();
        var original = new MessageConstants.PPConnect(sender, "Judy", "9.9.9.9", 6000, starting);
        var result = (MessageConstants.PPConnect) roundTrip(original);

        assertEquals(MessageConstants.MessageType.PP_CONNECT, result.getType());
        assertEquals(sender, result.senderNodeId());
        assertEquals("Judy", result.playerName());
        assertEquals("9.9.9.9", result.ip());
        assertEquals(6000, result.port());
        assertEquals(starting, result.startingPlayerId());
    }

    @Test
    void ppReady_roundTrip() {
        UUID id = UUID.randomUUID();
        var result = (MessageConstants.PPReady)
                roundTrip(new MessageConstants.PPReady(id, "room-5", "Karl"));
        assertEquals("room-5", result.roomId());
        assertEquals("Karl", result.playerName());
    }

    @Test
    void ppStart_roundTrip() {
        UUID id = UUID.randomUUID();
        List<Position> ships = List.of(new Position(0, 0), new Position(0, 1));
        var original = new MessageConstants.PPStart(id, "room-5", "Karl", ships);
        var result = (MessageConstants.PPStart) roundTrip(original);

        assertEquals(MessageConstants.MessageType.PP_START, result.getType());
        assertEquals(2, result.shipPositions().size());
        assertEquals(0, result.shipPositions().get(0).getX());
        assertEquals(1, result.shipPositions().get(1).getY());
    }

    @Test
    void ppShot_roundTrip() {
        UUID id = UUID.randomUUID();
        var result = (MessageConstants.PPShot) roundTrip(new MessageConstants.PPShot(id, 3, 7));
        assertEquals(3, result.x());
        assertEquals(7, result.y());
    }

    @Test
    void ppHitted_roundTrip() {
        UUID id = UUID.randomUUID();
        var result = (MessageConstants.PPHitted) roundTrip(new MessageConstants.PPHitted(id, 2, 5));
        assertEquals(MessageConstants.MessageType.PP_HITTED, result.getType());
        assertEquals(2, result.x());
        assertEquals(5, result.y());
    }

    @Test
    void ppMissed_roundTrip() {
        UUID id = UUID.randomUUID();
        var result = (MessageConstants.PPMissed) roundTrip(new MessageConstants.PPMissed(id, 1, 1));
        assertEquals(MessageConstants.MessageType.PP_MISSED, result.getType());
    }

    @Test
    void ppWin_roundTrip() {
        UUID id = UUID.randomUUID();
        var result = (MessageConstants.PPWin) roundTrip(new MessageConstants.PPWin(id));
        assertEquals(MessageConstants.MessageType.PP_WIN, result.getType());
        assertEquals(id, result.senderNodeId());
    }

    @Test
    void ppExit_roundTrip() {
        UUID id = UUID.randomUUID();
        var result = (MessageConstants.PPExit)
                roundTrip(new MessageConstants.PPExit(id, "room-6", "Luca"));
        assertEquals("room-6", result.roomId());
        assertEquals("Luca", result.playerName());
    }

    // -----------------------------------------------------------------------
    // serialize produces valid JSON structure
    // -----------------------------------------------------------------------

    @Test
    void serialize_producesJsonWithTypeAndPayloadFields() {
        UUID id = UUID.randomUUID();
        String json = ProtocolMessageJsonHelper.serialize(new MessageConstants.SSAck(id));
        assertTrue(json.contains("\"$type\""));
        assertTrue(json.contains("SS_ACK"));
        assertTrue(json.contains("\"payload\""));
    }

    @Test
    void deserialize_unknownType_throwsException() {
        String malformed = "{\"$type\":\"UNKNOWN_TYPE\",\"payload\":{}}";
        assertThrows(IllegalArgumentException.class,
                () -> ProtocolMessageJsonHelper.deserialize(malformed));
    }
}
