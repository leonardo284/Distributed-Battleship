package distributed.battleship.client.view;

import distributed.battleship.client.controller.ClientController;
import distributed.battleship.common.model.client.PlayingRoom;
import distributed.battleship.common.view.IViewLoggable;
import distributed.battleship.common.model.room.grid.Grid;
import distributed.battleship.common.model.room.grid.Position;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

/**
 * Game view that displays the state of an active {@link PlayingRoom}.
 *
 * <p>The layout is split into two halves:
 * <ul>
 *   <li>Left  – the local player's own grid (own ships and received shots).</li>
 *   <li>Right – the opponent's grid (shots fired by the local player).</li>
 * </ul>
 * Each half is headed by the corresponding player's name.
 *
 * <p>When {@code debugMode} is {@code true} a scrollable log area is shown at
 * the bottom of the window; it is hidden otherwise.
 */
public class GameView extends JFrame implements IViewLoggable {

    // region Fields
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** Seconds given to the player to place all ships during placement phase. */
    public static final int SHOT_SELECTION_TIME_SECONDS = 30;

    /** Controller used as single source of truth for game state. */
    private final ClientController clientController;

    /** Whether debug logging is enabled for this view. */
    private final boolean debugMode;

    // ── Player name labels ─────────────────────────────────────────────────────

    /** Label showing player one's name. */
    private JLabel playerOneNameLabel;

    /** Label showing player two's name. */
    private JLabel playerTwoNameLabel;

    // ── Grid panels ────────────────────────────────────────────────────────────

    /** Panel that renders the first player's grid. */
    private GridPanel currentPlayerGridPanel;

    /** Panel that renders the second player's grid. */
    private GridPanel opponentPlayerGridPanel;

    /** Loading panel shown while waiting for the opponent. */
    private JPanel loadingPanel;

    /** Caption shown next to the loading icon. */
    private JLabel loadingCaptionLabel;

    /** Indeterminate progress bar used as loading icon. */
    private JProgressBar loadingProgressBar;

    /** Button shown on terminal game states (win/lose) to leave the match view. */
    private JButton loadingBackToMenuButton;

    /** When true, all in-game interactions are blocked. */
    private boolean gameInteractionLocked;

    // ── Ship placement ─────────────────────────────────────────────────────────

    /** Whether ship placement mode is currently active. */
    private boolean placementMode = false;

    /** Index into {@link ShipType#values()} of the ship currently being placed. */
    private int currentShipIndex = 0;

    /** {@code true} = horizontal, {@code false} = vertical placement. */
    private boolean placementHorizontal = true;

    /** Swing timer used for the placement countdown. */
    private Timer placementTimer;

    /** Remaining seconds shown in the placement panel. */
    private int placementSecondsLeft;

    /** Callback invoked when placement ends (all ships placed or timer expired). */
    private Runnable onPlacementDone;

    /** Callback invoked when user asks to place a ship. */
    private ShipPlacementHandler shipPlacementHandler;

    /** Callback invoked when placement timer expires. */
    private PlacementTimeoutHandler placementTimeoutHandler;

    /** Callback invoked when user selects an opponent cell to shoot. */
    private ShotHandler shotHandler;

    /** Right-side panel shown during ship placement. */
    private JPanel placementPanel;

    /** Label showing the countdown. */
    private JLabel timerLabel;

    /** Panel shown during local shot selection with countdown information. */
    private JPanel shotSelectionPanel;

    /** Label showing the shot-selection countdown. */
    private JLabel shotTimerLabel;

    /** Swing timer used for the shot-selection countdown. */
    private Timer shotSelectionTimer;

    /** Remaining seconds to choose a target cell. */
    private int shotSelectionSecondsLeft;

    /** Label showing the ship currently being placed. */
    private JLabel currentShipLabel;

    /** Labels showing placed/remaining status for each ship. */
    private JLabel[] shipStatusLabels;

    /** Toggle buttons for horizontal/vertical orientation. */
    private JToggleButton btnHorizontal;
    private JToggleButton btnVertical;

    // ── Debug log area ─────────────────────────────────────────────────────────

    /** Log area shown at the bottom; only visible when {@code debugMode} is true. */
    private JTextArea logArea;

    /** Callback invoked when the window is closed. */
    private Runnable closeAction;

    /** Callback invoked when the player abandons the current match and returns to menu. */
    private Runnable backToMenuAction;

    /** Button to abandon an active match and return to menu. */
    private JButton abandonMatchButton;

    /** Label showing how many backup servers are known by the client. */
    // removed – backup info no longer shown in GameView UI

    //endregion

    // ──────────────────────────────────────────────────────────────────────────
    // Constructors
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Creates the game view using the client controller as state source.
     *
     * @param clientController The controller providing the current room state
     * @param debugMode {@code true} to show the debug log area at the bottom
     */
    public GameView(ClientController clientController, boolean debugMode) {
        this.clientController = clientController;
        this.debugMode = debugMode;
        initUI();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // UI initialisation
    // ──────────────────────────────────────────────────────────────────────────

    /** Initializes the UI with a glass pane for overlay loading indicator. */
    private void initUI() {
        setTitle("Battleship – Game");
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setSize(1350, debugMode ? 820 : 720);
        setLocationByPlatform(true);
        setResizable(true);
        setExtendedState(getExtendedState() | JFrame.MAXIMIZED_BOTH);
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

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        root.add(buildTopStatusPanel(), BorderLayout.NORTH);
        root.add(buildCenterPanel(), BorderLayout.CENTER);

        if (debugMode) {
            root.add(buildLogPanel(), BorderLayout.SOUTH);
        }

        setContentPane(root);

        // Set up glass pane for loading overlay
        buildLoadingPanel();
        setGlassPane(loadingPanel);

        refreshView();
    }

    private JPanel buildCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.add(buildGridsPanel(), BorderLayout.CENTER);

        JPanel sidePanel = new JPanel();
        sidePanel.setLayout(new BoxLayout(sidePanel, BoxLayout.Y_AXIS));

        placementPanel = buildPlacementPanel();
        placementPanel.setVisible(false);
        placementPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        abandonMatchButton = new JButton("Leave match");
        abandonMatchButton.setAlignmentX(Component.RIGHT_ALIGNMENT);
        abandonMatchButton.setMaximumSize(new Dimension(220, 32));
        abandonMatchButton.addActionListener(e -> {
            if (backToMenuAction != null) {
                backToMenuAction.run();
            }
        });

        sidePanel.add(placementPanel);
        sidePanel.add(Box.createVerticalStrut(12));
        sidePanel.add(abandonMatchButton);
        sidePanel.add(Box.createVerticalGlue());

        panel.add(sidePanel, BorderLayout.EAST);
        return panel;
    }

    /** Builds the right-side panel shown during the ship placement phase. */
    private JPanel buildPlacementPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder("Ship placement"));
        panel.setPreferredSize(new Dimension(260, 0));

        timerLabel = new JLabel("Time: " + ClientController.PLACEMENT_TIME_SECONDS + "s");
        timerLabel.setFont(timerLabel.getFont().deriveFont(Font.BOLD, 18f));
        timerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        timerLabel.setForeground(new Color(180, 0, 0));

        currentShipLabel = new JLabel(" ");
        currentShipLabel.setFont(currentShipLabel.getFont().deriveFont(Font.BOLD, 13f));
        currentShipLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel orientLabel = new JLabel("Orientation:");
        orientLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        ButtonGroup orientGroup = new ButtonGroup();
        btnHorizontal = new JToggleButton("Horizontal", true);
        btnVertical   = new JToggleButton("Vertical",   false);
        btnHorizontal.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnVertical.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnHorizontal.setMaximumSize(new Dimension(200, 30));
        btnVertical.setMaximumSize(new Dimension(200, 30));
        orientGroup.add(btnHorizontal);
        orientGroup.add(btnVertical);

        btnHorizontal.addActionListener(e -> {
            placementHorizontal = true;
            currentPlayerGridPanel.updateOrientation(true);
        });
        btnVertical.addActionListener(e -> {
            placementHorizontal = false;
            currentPlayerGridPanel.updateOrientation(false);
        });

        JLabel shipsTitle = new JLabel("Ships to place:");
        shipsTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        shipsTitle.setFont(shipsTitle.getFont().deriveFont(Font.BOLD, 12f));

        ShipType[] ships = ShipType.values();
        shipStatusLabels = new JLabel[ships.length];
        for (int i = 0; i < ships.length; i++) {
            shipStatusLabels[i] = new JLabel("  \u25cb " + ships[i].displayName + " (" + ships[i].size + ")");
            shipStatusLabels[i].setAlignmentX(Component.LEFT_ALIGNMENT);
        }

        panel.add(Box.createVerticalStrut(10));
        panel.add(timerLabel);
        panel.add(Box.createVerticalStrut(14));
        panel.add(currentShipLabel);
        panel.add(Box.createVerticalStrut(14));
        panel.add(orientLabel);
        panel.add(Box.createVerticalStrut(4));
        panel.add(btnHorizontal);
        panel.add(Box.createVerticalStrut(4));
        panel.add(btnVertical);
        panel.add(Box.createVerticalStrut(16));
        panel.add(shipsTitle);
        panel.add(Box.createVerticalStrut(6));
        for (JLabel lbl : shipStatusLabels) {
            panel.add(lbl);
            panel.add(Box.createVerticalStrut(2));
        }
        panel.add(Box.createVerticalGlue());
        return panel;
    }

    private JPanel buildShotSelectionPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setPreferredSize(new Dimension(420, 70));

        shotTimerLabel = new JLabel("Shot timer: " + SHOT_SELECTION_TIME_SECONDS + "s");
        shotTimerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        shotTimerLabel.setFont(shotTimerLabel.getFont().deriveFont(Font.BOLD, 28f));
        shotTimerLabel.setForeground(new Color(0, 90, 180));

        panel.add(Box.createVerticalStrut(8));
        panel.add(shotTimerLabel);
        panel.add(Box.createVerticalGlue());
        return panel;
    }

    private JPanel buildLoadingPanel() {
        // Overlay panel with semi-transparent dark background
        loadingPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                // Draw semi-transparent overlay
                Graphics2D g2d = (Graphics2D) g;
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));
                g2d.setColor(new Color(0, 0, 0));
                g2d.fillRect(0, 0, getWidth(), getHeight());
                super.paintComponent(g);
            }
        };

        // Create centered content panel
        JPanel centerContent = new JPanel();
        centerContent.setLayout(new BoxLayout(centerContent, BoxLayout.Y_AXIS));
        centerContent.setOpaque(false);

        loadingProgressBar = new JProgressBar();
        loadingProgressBar.setIndeterminate(true);
        loadingProgressBar.setPreferredSize(new Dimension(150, 20));
        loadingProgressBar.setMaximumSize(new Dimension(150, 20));
        loadingProgressBar.setAlignmentX(Component.CENTER_ALIGNMENT);

        loadingCaptionLabel = new JLabel("Waiting for an opponent...");
        loadingCaptionLabel.setFont(loadingCaptionLabel.getFont().deriveFont(Font.BOLD, 14f));
        loadingCaptionLabel.setForeground(Color.WHITE);
        loadingCaptionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        loadingBackToMenuButton = new JButton("Back to menu");
        loadingBackToMenuButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        loadingBackToMenuButton.setVisible(false);
        loadingBackToMenuButton.addActionListener(e -> {
            if (backToMenuAction != null) {
                backToMenuAction.run();
            }
        });

        centerContent.add(Box.createVerticalGlue());
        centerContent.add(loadingProgressBar);
        centerContent.add(Box.createVerticalStrut(15));
        centerContent.add(loadingCaptionLabel);
        centerContent.add(Box.createVerticalStrut(16));
        centerContent.add(loadingBackToMenuButton);
        centerContent.add(Box.createVerticalGlue());

        // Center horizontally by wrapping in a panel
        JPanel horizontalCenterPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        horizontalCenterPanel.setOpaque(false);
        horizontalCenterPanel.add(centerContent);

        loadingPanel.add(horizontalCenterPanel, BorderLayout.CENTER);
        loadingPanel.setOpaque(false);
        loadingPanel.setVisible(false);
        return loadingPanel;
    }

    /**
     * Builds the top status row with player names on the sides and shot timer in the middle.
     *
     * @return The configured panel
     */
    private JPanel buildTopStatusPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 3, 10, 0));

        playerOneNameLabel = new JLabel("", SwingConstants.CENTER);
        playerOneNameLabel.setFont(playerOneNameLabel.getFont().deriveFont(Font.BOLD, 24f));

        playerTwoNameLabel = new JLabel("", SwingConstants.CENTER);
        playerTwoNameLabel.setFont(playerTwoNameLabel.getFont().deriveFont(Font.BOLD, 24f));

        shotSelectionPanel = buildShotSelectionPanel();
        shotSelectionPanel.setVisible(false);

        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.add(shotSelectionPanel);

        panel.add(playerOneNameLabel);
        panel.add(centerPanel);
        panel.add(playerTwoNameLabel);
        return panel;
    }

    /**
     * Builds the central panel holding the two game grids side by side.
     *
     * @return The configured panel
     */
    private JPanel buildGridsPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 2, 20, 0));

        currentPlayerGridPanel = new GridPanel();
        opponentPlayerGridPanel = new GridPanel();

        panel.add(wrapGrid(currentPlayerGridPanel, "Your Board"));
        panel.add(wrapGrid(opponentPlayerGridPanel, "Opponent's Board"));
        return panel;
    }

    /**
     * Wraps a {@link GridPanel} in a titled border panel.
     *
     * @param gridPanel The grid panel to wrap
     * @param title     The border title
     * @return The wrapper panel
     */
    private JPanel wrapGrid(GridPanel gridPanel, String title) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBorder(BorderFactory.createTitledBorder(title));
        wrapper.add(gridPanel, BorderLayout.CENTER);
        return wrapper;
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

    /**
     * Refreshes all displayed data from the current {@link PlayingRoom} state.
     * Should be called whenever the room state changes.
     */
    public void refreshView() {
        PlayingRoom room = clientController != null ? clientController.getCurrentRoom() : null;
        if (room == null) {
            return;
        }

        // Update player name labels.
        String p1Name = room.getPlayerOne() != null ? room.getPlayerOne().getName() : "Player 1";
        String p2Name = room.getPlayerTwo() != null ? room.getPlayerTwo().getName() : "Player 2";
        playerOneNameLabel.setText(p1Name != null ? p1Name : "Player 1");
        playerTwoNameLabel.setText(p2Name != null ? p2Name : "Player 2");

        // Update grids.
        currentPlayerGridPanel.setGrid(room.getCurrentGrid(), true);
        opponentPlayerGridPanel.setGrid(room.getOpponentGrid(), false);

        addLog("CLIENT", "View refreshed – " + p1Name + " vs " + p2Name);
    }

    /**
     * Registers the callback executed when the user closes the game window.
     *
     * @param closeAction The shutdown action managed by the client controller
     */
    public void setCloseAction(Runnable closeAction) { this.closeAction = closeAction; }

    public void setBackToMenuAction(Runnable backToMenuAction) { this.backToMenuAction = backToMenuAction; }

    /**
     * Registers controller callbacks for placement operations.
     *
     * @param shipPlacementHandler handles manual ship placement requests
     * @param placementTimeoutHandler handles random placement when time expires
     */
    public void setPlacementHandlers(
            ShipPlacementHandler shipPlacementHandler,
            PlacementTimeoutHandler placementTimeoutHandler) {
        this.shipPlacementHandler = shipPlacementHandler;
        this.placementTimeoutHandler = placementTimeoutHandler;
    }

    public void setShotHandler(ShotHandler shotHandler) { this.shotHandler = shotHandler; }

    public void enableShotSelectionMode() {
        if (gameInteractionLocked) {
            return;
        }
        opponentPlayerGridPanel.activateShotSelection(this::handleShotClick);
        startShotSelectionTimer();
    }

    public void disableShotSelectionMode() {
        opponentPlayerGridPanel.deactivateShotSelection();
        stopShotSelectionTimer();
    }

    private void handleShotClick(Position target) {
        if (gameInteractionLocked) {
            return;
        }
        if (shotHandler == null) {
            return;
        }
        if (clientController.isAlreadyTargetedCell(target)) {
            addLog("CLIENT", "Cell (" + target.getX() + "," + target.getY() + ") already targeted: choose another cell.");
            return;
        }
        stopShotSelectionTimer();
        boolean hit = shotHandler.fireShot(target);
        if (!hit) {
            disableShotSelectionMode();
        } else {
            startShotSelectionTimer();
        }
        refreshView();
    }

    private void startShotSelectionTimer() {
        shotSelectionSecondsLeft = SHOT_SELECTION_TIME_SECONDS;
        updateShotTimerLabel();
        if (shotSelectionPanel != null) {
            shotSelectionPanel.setVisible(true);
        }
        if (shotSelectionTimer != null) {
            shotSelectionTimer.stop();
        }
        shotSelectionTimer = new Timer(1000, e -> {
            shotSelectionSecondsLeft--;
            updateShotTimerLabel();
            if (shotSelectionSecondsLeft <= 0) {
                stopShotSelectionTimer();
                Position randomTarget = clientController.chooseRandomAvailableShotTarget();
                if (randomTarget != null) {
                    addLog("CLIENT", "Shot timer expired - randomly selecting cell (" + randomTarget.getX() + "," + randomTarget.getY() + ").");
                    handleShotClick(randomTarget);
                } else {
                    addLog("CLIENT", "Shot timer expired, but no available cells can be targeted.");
                    disableShotSelectionMode();
                }
            }
        });
        shotSelectionTimer.start();
    }

    private void stopShotSelectionTimer() {
        if (shotSelectionTimer != null) {
            shotSelectionTimer.stop();
            shotSelectionTimer = null;
        }
        if (shotSelectionPanel != null) {
            shotSelectionPanel.setVisible(false);
        }
    }

    private void updateShotTimerLabel() {
        if (shotTimerLabel != null) {
            shotTimerLabel.setText("Shot timer: " + shotSelectionSecondsLeft + "s");
            shotTimerLabel.setForeground(shotSelectionSecondsLeft <= 3
                    ? new Color(200, 0, 0)
                    : new Color(0, 90, 180));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Ship placement phase
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Starts the interactive ship placement phase.
     * Shows the placement panel, starts the placement time countdown
     * and enables click interaction on the player's own grid.
     *
     * @param onDone callback invoked when placement ends (all ships placed or timer expired)
     */
    public void startPlacementMode(Runnable onDone) {
        this.onPlacementDone = onDone;
        this.placementMode   = true;
        this.currentShipIndex = 0;
        this.placementHorizontal = true;
        this.placementSecondsLeft = ClientController.PLACEMENT_TIME_SECONDS;

        btnHorizontal.setSelected(true);
        btnVertical.setSelected(false);
        updatePlacementUI();

        placementPanel.setVisible(true);

        currentPlayerGridPanel.activatePlacement(
                ShipType.values()[currentShipIndex].size,
                placementHorizontal,
                this::handlePlacementClick
        );

        placementTimer = new Timer(1000, e -> {
            placementSecondsLeft--;
            updateTimerLabel();
            if (placementSecondsLeft <= 0) {
                addLog("CLIENT", "Time expired - remaining ships are placed randomly.");
                int[] fleetLengths = Grid.getDefaultFleetShipLengths();
                int autoPlaced = placementTimeoutHandler != null
                        ? placementTimeoutHandler.handleTimeout(currentShipIndex, fleetLengths)
                        : 0;
                currentShipIndex += autoPlaced;
                if (currentShipIndex < ShipType.values().length) {
                    addLog("CLIENT", "Warning: not all remaining ships could be placed automatically.");
                }
                refreshView();
                endPlacement();
            }
        });
        placementTimer.start();
        addLog("CLIENT", "Ship placement started - " + ClientController.PLACEMENT_TIME_SECONDS + " seconds.");
    }

    /** Handles a cell click during placement mode. */
    private void handlePlacementClick(Position cell) {
        if (!placementMode) return;
        ShipType ship = ShipType.values()[currentShipIndex];
        boolean placed = shipPlacementHandler != null && shipPlacementHandler.placeShip(cell, ship.size, placementHorizontal);
        if (!placed) {
            addLog("CLIENT", "Invalid position for " + ship.displayName + " - try again.");
            return;
        }
        addLog("CLIENT", ship.displayName + " placed.");

        // Mark ship as placed in the status labels
        shipStatusLabels[currentShipIndex].setText("  \u2713 " + ship.displayName + " (" + ship.size + ")");
        shipStatusLabels[currentShipIndex].setForeground(new Color(0, 140, 0));

        refreshView();

        currentShipIndex++;
        if (currentShipIndex >= ShipType.values().length) {
            addLog("CLIENT", "All ships placed!");
            endPlacement();
        } else {
            updatePlacementUI();
            ShipType next = ShipType.values()[currentShipIndex];
            currentPlayerGridPanel.activatePlacement(next.size, placementHorizontal, this::handlePlacementClick);
        }
    }

    /** Updates the placement panel labels for the current ship. */
    private void updatePlacementUI() {
        updateTimerLabel();
        ShipType[] ships = ShipType.values();
        if (currentShipIndex < ships.length) {
            ShipType ship = ships[currentShipIndex];
            currentShipLabel.setText("Place: " + ship.displayName + " (" + ship.size + " cells)");
        } else {
            currentShipLabel.setText("All ships placed!");
        }
    }

    /** Refreshes only the timer label text. */
    private void updateTimerLabel() {
        if (timerLabel != null) {
            timerLabel.setText("Time: " + placementSecondsLeft + "s");
            timerLabel.setForeground(placementSecondsLeft <= 10
                    ? new Color(200, 0, 0) : new Color(180, 0, 0));
        }
    }

    /** Ends the placement phase, stops the timer, hides the panel and fires the callback. */
    private void endPlacement() {
        placementMode = false;
        if (placementTimer != null) {
            placementTimer.stop();
            placementTimer = null;
        }
        currentPlayerGridPanel.deactivatePlacement();
        SwingUtilities.invokeLater(() -> {
            placementPanel.setVisible(false);
            refreshView();
        });
        if (onPlacementDone != null) {
            onPlacementDone.run();
        }
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

    /**
     * Prepends raw log text at the beginning of the game log area.
     *
     * @param logText text to prepend
     */
    public void prependLog(String logText) {
        if (!debugMode || logArea == null || logText == null || logText.isEmpty()) {
            return;
        }
        String current = logArea.getText();
        logArea.setText(logText + current);
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    public void showBackupServers(List<String> backupServers) {
        // Backup server info is no longer displayed in the game UI.
    }

    /**
     * Shows the loading indicator with the provided caption.
     *
     * @param caption loading text, defaults to "Waiting for an opponent..." when blank
     */
    public void showLoadingIndicator(String caption) {
        String safeCaption = (caption == null || caption.isBlank())
                ? "Waiting for an opponent..."
                : caption;
        if (loadingProgressBar != null) {
            loadingProgressBar.setVisible(true);
        }
        if (loadingBackToMenuButton != null) {
            loadingBackToMenuButton.setText("Leave match");
            loadingBackToMenuButton.setVisible(true);
        }
        if (loadingCaptionLabel != null) {
            loadingCaptionLabel.setText(safeCaption);
        }
        if (loadingPanel != null) {
            loadingPanel.setVisible(true);
        }
        // Disable grid panels to block input
        currentPlayerGridPanel.setEnabled(false);
        opponentPlayerGridPanel.setEnabled(false);
    }

    public void showMatchEndOverlay(String caption) {
        showMatchEndOverlay(caption, true);
    }

    private void showMatchEndOverlay(String caption, boolean showBackToMenuButton) {
        gameInteractionLocked = true;
        disableShotSelectionMode();

        if (placementTimer != null) {
            placementTimer.stop();
            placementTimer = null;
        }
        placementMode = false;
        currentPlayerGridPanel.deactivatePlacement();

        if (placementPanel != null) {
            placementPanel.setVisible(false);
        }
        if (shotSelectionPanel != null) {
            shotSelectionPanel.setVisible(false);
        }

        String safeCaption = (caption == null || caption.isBlank())
                ? "Match ended."
                : caption;
        if (loadingProgressBar != null) {
            loadingProgressBar.setVisible(false);
        }
        if (loadingBackToMenuButton != null) {
            loadingBackToMenuButton.setText("Back to menu");
            loadingBackToMenuButton.setVisible(showBackToMenuButton);
        }
        if (loadingCaptionLabel != null) {
            loadingCaptionLabel.setText(safeCaption);
        }
        if (loadingPanel != null) {
            loadingPanel.setVisible(true);
        }

        currentPlayerGridPanel.setEnabled(false);
        opponentPlayerGridPanel.setEnabled(false);
    }

    public void showMatchEndPopup(String caption) {
        Runnable uiTask = () -> {
            String safeCaption = (caption == null || caption.isBlank())
                    ? "Match ended."
                    : caption;

            showMatchEndOverlay(safeCaption, false);

            Object[] options = {"Back to menu"};
            int choice = JOptionPane.showOptionDialog(
                    this,
                    safeCaption,
                    "Match ended",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.INFORMATION_MESSAGE,
                    null,
                    options,
                    options[0]);

            if (choice == 0) {
                if (backToMenuAction != null) {
                    backToMenuAction.run();
                    return;
                }
                if (closeAction != null) {
                    closeAction.run();
                    return;
                }
            }

            if (loadingBackToMenuButton != null) {
                loadingBackToMenuButton.setText("Back to menu");
                loadingBackToMenuButton.setVisible(true);
            }
        };

        if (SwingUtilities.isEventDispatchThread()) {
            uiTask.run();
        } else {
            SwingUtilities.invokeLater(uiTask);
        }
    }

    /**
     * Hides the loading indicator.
     */
    public void hideLoadingIndicator() {
        if (gameInteractionLocked) {
            return;
        }
        if (loadingPanel != null) {
            loadingPanel.setVisible(false);
        }
        // Re-enable grid panels
        currentPlayerGridPanel.setEnabled(true);
        opponentPlayerGridPanel.setEnabled(true);
    }
}
