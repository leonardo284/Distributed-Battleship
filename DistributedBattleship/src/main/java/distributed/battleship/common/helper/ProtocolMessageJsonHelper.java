package distributed.battleship.common.helper;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import distributed.battleship.common.model.message.MessageConstants;

/**
 * Utility methods to serialize and deserialize protocol tuples as JSON.
 */
public final class ProtocolMessageJsonHelper {

    private static final Gson GSON = new Gson();

    private ProtocolMessageJsonHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Serializes a protocol tuple to JSON with shape { type, payload }.
     *
     * @param message Protocol message tuple
     * @return Serialized JSON string
     */
    public static String serialize(MessageConstants.MessageTuple message) {
        JsonObject root = new JsonObject();
        root.addProperty("$type", message.getType().name());
        root.add("payload", GSON.toJsonTree(message));
        return GSON.toJson(root);
    }

    /**
     * Deserializes JSON containing { type, payload } into the proper tuple class.
     *
     * @param jsonMessage Raw JSON message
     * @return Parsed message tuple
     */
    public static MessageConstants.MessageTuple deserialize(String jsonMessage) {
        JsonObject root = JsonParser.parseString(jsonMessage).getAsJsonObject();
        String typeValue = root.get("$type").getAsString();
        MessageConstants.MessageType type = MessageConstants.MessageType.valueOf(typeValue);
        JsonElement payload = root.get("payload");
        if (payload == null || payload.isJsonNull()) {
            payload = root;
        }

        return GSON.fromJson(payload, type.getRecordClass());
    }
}
