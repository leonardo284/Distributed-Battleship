package distributed.battleship.server.view;

import distributed.battleship.common.view.IViewLoggable;
import distributed.battleship.common.model.client.Client;
import distributed.battleship.common.model.room.Room;
import distributed.battleship.common.model.server.BackupServer;
import distributed.battleship.common.model.server.ServerType;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

/**
 * Simple Swing GUI implementation of the server view.
 */
public class SimpleServerView implements ServerView {
    private boolean running;
    private JFrame frame;
    private JLabel serverTypeLabel;
    private JLabel backupOrderLabel;
    private JTextArea logArea;
    private JButton showBackupsButton;
    private JButton showRoomsButton;
    private JButton showUsersButton;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
    private Runnable closeAction;
    private Runnable showRoomsAction;
    private Runnable showBackupsAction;
    private Runnable showUsersAction;
    private java.util.List<BackupServer> currentBackupServers = new java.util.ArrayList<>();

    public SimpleServerView() {
        this.running = false;
    }

    @Override
    public void showMessage(String message) {
        appendLog("INFO", message);
    }

    @Override
    public void showError(String error) {
        appendLog("ERROR", error);
    }

    @Override
    public void start() {
        running = true;
        SwingUtilities.invokeLater(() -> {
            frame = new JFrame("Battaglia Navale - Server");
            frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            frame.setSize(700, 450);
            frame.setLocationByPlatform(true);
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent event) {
                    if (closeAction != null) {
                        Runnable action = closeAction;
                        closeAction = null;
                        frame.dispose();
                        action.run();
                        return;
                    }
                    frame.dispose();
                    System.exit(0);
                }
            });

            JPanel root = new JPanel(new BorderLayout(10, 10));
            root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            serverTypeLabel = new JLabel("Server type: -", SwingConstants.CENTER);
            serverTypeLabel.setFont(serverTypeLabel.getFont().deriveFont(Font.BOLD, 16f));

            backupOrderLabel = new JLabel("");
            backupOrderLabel.setFont(backupOrderLabel.getFont().deriveFont(Font.BOLD, 22f));
            backupOrderLabel.setVisible(false);

            showUsersButton = new JButton("Show Users (0)");
            showUsersButton.setFocusPainted(false);
            showUsersButton.addActionListener(e -> {
                if (showUsersAction != null) {
                    showUsersAction.run();
                }
            });

            showRoomsButton = new JButton("Show Rooms (0)");
            showRoomsButton.setFocusPainted(false);
            showRoomsButton.addActionListener(e -> {
                if (showRoomsAction != null) {
                    showRoomsAction.run();
                }
            });

            showBackupsButton = new JButton("Show Backups (0)");
            showBackupsButton.setFocusPainted(false);
            showBackupsButton.setVisible(false);
            showBackupsButton.addActionListener(e -> {
                if (showBackupsAction != null) {
                    showBackupsAction.run();
                }
            });

            JButton clearButton = new JButton("Clear Log");
            clearButton.setFocusPainted(false);
            clearButton.addActionListener(e -> {
                if (logArea != null) {
                    logArea.setText("");
                }
            });

            JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
            buttonsPanel.add(showBackupsButton);
            buttonsPanel.add(showUsersButton);
            buttonsPanel.add(showRoomsButton);
            buttonsPanel.add(clearButton);

            JPanel topPanel = new JPanel(new BorderLayout(4, 4));
            topPanel.add(backupOrderLabel, BorderLayout.WEST);
            topPanel.add(serverTypeLabel, BorderLayout.NORTH);
            topPanel.add(buttonsPanel, BorderLayout.CENTER);
            topPanel.add(new JSeparator(), BorderLayout.SOUTH);

            logArea = new JTextArea();
            logArea.setEditable(false);
            logArea.setLineWrap(true);
            logArea.setWrapStyleWord(true);

            JScrollPane scrollPane = new JScrollPane(logArea);
            scrollPane.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

            root.add(topPanel, BorderLayout.NORTH);
            root.add(scrollPane, BorderLayout.CENTER);

            frame.setContentPane(root);
            frame.setVisible(true);

            appendLog("INFO", "Server GUI started");
        });
    }

    @Override
    public void stop() {
        running = false;
        appendLog("INFO", "Server stopped");
        SwingUtilities.invokeLater(() -> {
            if (frame != null) {
                frame.dispose();
            }
        });
    }

    @Override
    public void showConnectedClients(int count) {
        SwingUtilities.invokeLater(() -> {
            if (showUsersButton != null) {
                showUsersButton.setText("Show Users (" + count + ")");
            }
        });
    }

    @Override
    public void showRoomsCount(int count) {
        SwingUtilities.invokeLater(() -> {
            if (showRoomsButton != null) {
                showRoomsButton.setText("Show Rooms (" + count + ")");
            }
        });
    }

    @Override
    public void showServerType(ServerType serverType) {
        SwingUtilities.invokeLater(() -> {
            if (serverTypeLabel != null) {
                serverTypeLabel.setText("Server type: " + (serverType == null ? "-" : serverType.name()));
            }
        });
    }

    @Override
    public void showConnectedBackupServers(int count) {
        SwingUtilities.invokeLater(() -> {
            if (showBackupsButton != null) {
                showBackupsButton.setText("Show Backups (" + count + ")");
            }
        });
    }

    @Override
    public void showBackupServersList(List<BackupServer> backupServers) {
        currentBackupServers = backupServers != null ? new java.util.ArrayList<>(backupServers) : new java.util.ArrayList<>();
    }

    @Override
    public void setShowBackupsAction(Runnable action) {
        this.showBackupsAction = action;
        SwingUtilities.invokeLater(() -> {
            if (showBackupsButton != null) {
                showBackupsButton.setVisible(true);
            }
        });
    }

    @Override
    public void setShowUsersAction(Runnable action) {
        this.showUsersAction = action;
    }

    @Override
    public void showUsersTable(List<Client> users) {
        String[] columnNames = {"Name", "IP", "Node ID"};
        List<Client> list = users != null ? users : java.util.List.of();
        Object[][] data = new Object[list.size()][3];
        for (int i = 0; i < list.size(); i++) {
            Client c = list.get(i);
            data[i][0] = c.getName();
            data[i][1] = c.getIp();
            data[i][2] = c.getNodeId().toString();
        }
        JTable table = new JTable(data, columnNames);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        JScrollPane tableScrollPane = new JScrollPane(table);
        tableScrollPane.setPreferredSize(new Dimension(500, 200));
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                frame,
                tableScrollPane,
                "Connected Users",
                JOptionPane.INFORMATION_MESSAGE));
    }

    @Override
    public void showBackupsTable(List<BackupServer> backups) {
        String[] columnNames = {"Order", "IP", "Port", "Node ID"};
        List<BackupServer> list = backups != null ? backups : java.util.List.of();
        Object[][] data = new Object[list.size()][4];
        for (int i = 0; i < list.size(); i++) {
            BackupServer b = list.get(i);
            data[i][0] = b.getOrder();
            data[i][1] = b.getIp();
            data[i][2] = b.getConnectionPort();
            data[i][3] = b.getNodeId().toString();
        }
        JTable table = new JTable(data, columnNames);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        JScrollPane tableScrollPane = new JScrollPane(table);
        tableScrollPane.setPreferredSize(new Dimension(600, 200));
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                frame,
                tableScrollPane,
                "Connected Backup Servers",
                JOptionPane.INFORMATION_MESSAGE));
    }

    @Override
    public void setBackupOrder(int order) {
        SwingUtilities.invokeLater(() -> {
            if (backupOrderLabel != null) {
                backupOrderLabel.setText("#" + order);
                backupOrderLabel.setVisible(true);
            }
        });
    }

    @Override
    public void hideBackupOrder() {
        SwingUtilities.invokeLater(() -> {
            if (backupOrderLabel != null) {
                backupOrderLabel.setVisible(false);
                backupOrderLabel.setText("");
            }
        });
    }

    /**
     * Appends a debug log entry. Required by {@link IViewLoggable}.
     *
     * @param log The debug message to display
     */
    @Override
    public void addLog(String tag, String log) {
        appendLog(tag, log);
    }

    @Override
    public String getViewLog() {
        return logArea != null ? logArea.getText() : "";
    }

    @Override
    public void setShowRoomsAction(Runnable showRoomsAction) {
        this.showRoomsAction = showRoomsAction;
    }

    @Override
    public void setCloseAction(Runnable closeAction) {
        this.closeAction = closeAction;
    }

    @Override
    public void showRoomsTable(List<Room> rooms) {
        String[] columnNames = {
                "Room ID", "Player One", "Player One IP", "Player One Port",
                "Player Two", "Player Two IP", "Player Two Port", "Two Players"
        };

        Object[][] data = new Object[rooms.size()][columnNames.length];
        for (int i = 0; i < rooms.size(); i++) {
            Room room = rooms.get(i);
            data[i][0] = room.getRoomId();
            data[i][1] = room.getPlayerOne() != null ? room.getPlayerOne().getName() : "-";
            data[i][2] = room.getPlayerOne() != null ? room.getPlayerOne().getIp() : "-";
            data[i][3] = room.getPlayerOne() != null ? room.getPlayerOne().getPeerConnectionPort() : -1;
            data[i][4] = room.getPlayerTwo() != null ? room.getPlayerTwo().getName() : "-";
            data[i][5] = room.getPlayerTwo() != null ? room.getPlayerTwo().getIp() : "-";
            data[i][6] = room.getPlayerTwo() != null ? room.getPlayerTwo().getPeerConnectionPort() : -1;
            data[i][7] = room.hasTwoPlayers();
        }

        JTable table = new JTable(data, columnNames);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        JScrollPane tableScrollPane = new JScrollPane(table);
        tableScrollPane.setPreferredSize(new Dimension(1000, 320));

        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                frame,
                tableScrollPane,
                "Current Rooms",
                JOptionPane.INFORMATION_MESSAGE));
    }

    private void appendLog(String level, String message) {
        String line = "[" + formatter.format(LocalDateTime.now()) + "] [" + level + "] " + message + System.lineSeparator();
        SwingUtilities.invokeLater(() -> {
            if (logArea != null) {
                logArea.append(line);
                logArea.setCaretPosition(logArea.getDocument().getLength());
            } else {
                System.out.print(line);
            }
        });
    }
}
