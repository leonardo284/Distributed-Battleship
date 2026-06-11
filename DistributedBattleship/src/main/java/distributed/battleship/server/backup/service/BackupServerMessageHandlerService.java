package distributed.battleship.server.backup.service;

import distributed.battleship.common.model.message.MessageConstants;
import distributed.battleship.common.service.MessageHandlerService;
import distributed.battleship.server.backup.controller.BackupServerController;

/**
 * Handles messages received by a backup server from the primary server.
 *
 * <p>Message-specific logic is intentionally left empty for now.
 */
public class BackupServerMessageHandlerService implements MessageHandlerService {

    private final BackupServerController backupServerController;

    public BackupServerMessageHandlerService(BackupServerController backupServerController) {
        this.backupServerController = backupServerController;
    }

    @Override
    public void handleMessage(MessageConstants.MessageTuple msg) {
        switch (msg.getType()) {
            case SS_RESPONSE_HELLO -> handleResponseHello((MessageConstants.SSResponseHello) msg);
            case SS_SEND_STATE_TO_BACKUP -> handleSendState((MessageConstants.SSSendStateToBackup) msg);
            default -> backupServerController.log("Received message on backup handler: " + msg.getType());
        }
    }

    private void handleResponseHello(MessageConstants.SSResponseHello responseHello) {
        backupServerController.setBackupOrder(responseHello.order());
    }

    private void handleSendState(MessageConstants.SSSendStateToBackup stateMsg) {
        backupServerController.applyStateSnapshot(stateMsg);
        backupServerController.sendAckToPrimary();
    }
}
