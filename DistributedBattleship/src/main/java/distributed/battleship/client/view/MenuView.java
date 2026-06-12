package distributed.battleship.client.view;

import distributed.battleship.common.view.IViewLoggable;
import distributed.battleship.common.model.server.BackupServer;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

/**
 * Main menu view shown at client startup.
 *
 * <p>Allows the player to enter their name and optionally a room ID to join an
 * existing session.
 *
 * <p>When {@code debugMode} is {@code true} a log area is displayed at the bottom of the
 * window; it is hidden otherwise.
 */
public class MenuView extends JFrame implements IViewLoggable {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    /** Whether debug logging is enabled for this view. */
    private final boolean debugMode;

    /** Text field for the player name (Join Game tab). */
    private JTextField joinGameNameField;

    /** Button used to submit the join-room request. */
    private JButton joinButton;

    // ── Debug log area ─────────────────────────────────────────────────────────

    /** Log area shown at the bottom; only visible when {@code debugMode} is true. */
    private JTextArea logArea;

    /** Label showing backup server count; only visible when {@code debugMode} is true. */
    private JLabel backupCountLabel;

    /** Callback invoked when the player requests to join an existing game. */
    private Runnable joinGameAction;

    /** Callback invoked when the window is closed. */
    private Runnable closeAction;

    // ──────────────────────────────────────────────────────────────────────────
    // Constructor
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Creates the menu view.
     *
     * @param debugMode {@code true} to show the debug log area at the bottom
     */
    public MenuView(boolean debugMode) {
        this.debugMode = debugMode;
        initUI();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // UI initialisation
    // ──────────────────────────────────────────────────────────────────────────

    /** Builds and lays out all UI components. */
    private void initUI() {
        setTitle("Battleship \u2013 Main Menu");
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setSize(700, debugMode ? 460 : 320);
        setLocationByPlatform(true);
        setResizable(false);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                if (closeAction != null) {
                    closeAction.run();
                    return;
                }
                dispose();
                System.exit(0);
            }
        });

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        root.add(buildJoinGamePanel(), BorderLayout.CENTER);

        // ── Debug log area (only when debug mode is on) ───────────────────────
        if (debugMode) {
            root.add(buildLogPanel(), BorderLayout.SOUTH);
        }

        setContentPane(root);
    }

    /**
     * Builds the join-room panel.
     * Allows the player to enter a display name and an existing game ID.
     *
     * @return The configured panel
     */
    private JPanel buildJoinGamePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        GridBagConstraints gbc = defaultGbc();

        // ── Name field ────────────────────────────────────────────────────────
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("Your name:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        joinGameNameField = new JTextField(20);
        panel.add(joinGameNameField, gbc);

        // ── "Join Game" button ─────────────────────────────────────────────────
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.CENTER;
        joinButton = new JButton("Join Game");
        joinButton.setEnabled(false);
        joinButton.addActionListener(e -> onJoinGame());
        panel.add(joinButton, gbc);

        joinGameNameField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                updateJoinButtonState();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                updateJoinButtonState();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                updateJoinButtonState();
            }
        });

        // ── Backup counter (debug mode only) ──────────────────────────────────
        if (debugMode) {
            gbc.gridx = 0;
            gbc.gridy = 2;
            gbc.gridwidth = 2;
            gbc.anchor = GridBagConstraints.WEST;
            backupCountLabel = new JLabel("Backups connected: 0");
            backupCountLabel.setFont(backupCountLabel.getFont().deriveFont(Font.ITALIC, 11f));
            backupCountLabel.setForeground(Color.DARK_GRAY);
            panel.add(backupCountLabel, gbc);
        }

        return panel;
    }

    /**
     * Builds the debug log panel shown at the bottom of the window.
     *
     * @return A scroll pane wrapping the log text area
     */
    private JScrollPane buildLogPanel() {
        logArea = new JTextArea(4, 40);
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));

        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Debug log"));
        scrollPane.setPreferredSize(new Dimension(0, 90));
        return scrollPane;
    }

    /** Returns a default {@link GridBagConstraints} configuration for this view. */
    private GridBagConstraints defaultGbc() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        return gbc;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Action handlers
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Handles the "Join Game" button click.
     * Validates input and triggers the join logic.
     */
    private void onJoinGame() {
        String name = joinGameNameField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(
                    this,
                    "Please enter your name.",
                    "Validation Error",
                    JOptionPane.WARNING_MESSAGE);
            addLog("CLIENT", "Join game attempted with empty name");
            return;
        }
        addLog("CLIENT", "Joining game as player: " + name);
        if (joinGameAction != null) {
            joinGameAction.run();
        }
    }

    private void updateJoinButtonState() {
        if (joinButton == null || joinGameNameField == null) {
            return;
        }
        joinButton.setEnabled(!joinGameNameField.getText().trim().isEmpty());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // IViewLoggable
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Appends a timestamped debug log entry to the log area.
     * Has no effect when debug mode is disabled.
     *
     * @param log The message to log
     */
    @Override
    public void addLog(String tag, String log) {
        if (!debugMode || logArea == null) {
            return;
        }
        String entry = "[" + TIME_FORMATTER.format(LocalDateTime.now()) + "] [" + tag + "] " + log
                + System.lineSeparator();
        SwingUtilities.invokeLater(() -> {
            logArea.append(entry);
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    @Override
    public String getViewLog() {
        if (!debugMode || logArea == null) {
            return "";
        }
        return logArea.getText();
    }

    public void setViewLog(String logText) {
        if (!debugMode || logArea == null) {
            return;
        }
        String safeText = logText == null ? "" : logText;
        SwingUtilities.invokeLater(() -> {
            logArea.setText(safeText);
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Accessors (useful for wiring a controller)
    // ──────────────────────────────────────────────────────────────────────────

    /** @return The player name entered in the Join Game tab. */
    public String getJoinGamePlayerName() {
        return joinGameNameField.getText().trim();
    }

    /**
     * Registers the callback executed after the Join Game form validation succeeds.
     *
     * @param joinGameAction The action managed by the client controller
     */
    public void setJoinGameAction(Runnable joinGameAction) {
        this.joinGameAction = joinGameAction;
    }

    /**
     * Registers the callback executed when the user closes the menu window.
     *
     * @param closeAction The shutdown action managed by the client controller
     */
    public void setCloseAction(Runnable closeAction) {
        this.closeAction = closeAction;
    }

    public void showBackupServers(List<BackupServer> backupServers) {
        if (!debugMode || backupCountLabel == null) {
            return;
        }
        int count = backupServers == null ? 0 : backupServers.size();
        SwingUtilities.invokeLater(() -> backupCountLabel.setText("Backups connected: " + count));
    }
}

