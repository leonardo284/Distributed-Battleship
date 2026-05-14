package distributed.battleship.common.service;

import org.example.common.model.message.MessageConstants;

/**
 * Common contract for protocol message handler services.
 */
public interface MessageHandlerService {

    /**
     * Handles one deserialized protocol message.
     *
     * @param msg protocol message to handle
     */
    void handleMessage(MessageConstants.MessageTuple msg);
}
