package distributed.battleship.client.view;

import distributed.battleship.common.model.room.grid.Cell;
import distributed.battleship.common.model.room.grid.Grid;
import distributed.battleship.common.model.room.grid.Position;

import javax.swing.*;
import java.awt.*;
import java.awt.AlphaComposite;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.function.Consumer;

/**
 * Custom panel that renders a {@link Grid} as a coloured cell table.
 *
 * <p>Colour legend:
 * <ul>
 *   <li>Light blue  – empty cell</li>
 *   <li>Dark grey   – ship cell</li>
 *   <li>Red         – hit</li>
 *   <li>Grey        – unknown opponent cell</li>
 * </ul>
 */
public class GridPanel extends JPanel {

    /** Grid data to render; may be {@code null} before the game starts. */
    private Grid grid;

    /** Whether ship cells should be rendered explicitly. */
    private boolean revealShips = true;

    /** Border colour used to draw cell outlines. */
    private static final Color BORDER_COLOR = new Color(80, 80, 80);

    private static final Color COLOR_EMPTY = new Color(173, 216, 230);  // light blue
    private static final Color COLOR_SHIP = new Color(60,  60,  60);   // dark grey
    private static final Color COLOR_HIT = new Color(40, 90, 200);    // blue (shot on empty)
    private static final Color COLOR_SUNK = new Color(220, 50, 50);   // red (ship hit)
    private static final Color COLOR_UNKNOWN = new Color(140, 140, 140);
    private static final Color COLOR_PREVIEW_VALID = new Color(0,  200,  0,  160); // translucent green
    private static final Color COLOR_PREVIEW_INVALID = new Color(220, 50, 50, 160);  // translucent red

    // ── Placement mode state ──────────────────────────────────────────────
    private boolean inPlacementMode = false;
    private boolean inShotSelectionMode = false;
    private int previewLength = 1;
    private boolean previewHorizontal = true;
    private Position hoverCell = null;
    private Consumer<Position> clickHandler = null;
    private Consumer<Position> shotClickHandler = null;

    public GridPanel() {
        setBackground(Color.WHITE);

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                if (!inPlacementMode) return;
                hoverCell = cellFromMouse(e);
                repaint();
            }
        });

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Position cell = cellFromMouse(e);
                if (cell == null) {
                    return;
                }

                if (inPlacementMode && clickHandler != null) {
                    clickHandler.accept(cell);
                    return;
                }

                if (inShotSelectionMode && shotClickHandler != null) {
                    shotClickHandler.accept(cell);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (!inPlacementMode) return;
                hoverCell = null;
                repaint();
            }
        });
    }

    public void activateShotSelection(Consumer<Position> onCellClick) {
        this.inShotSelectionMode = true;
        this.shotClickHandler = onCellClick;
        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        repaint();
    }

    public void deactivateShotSelection() {
        this.inShotSelectionMode = false;
        this.shotClickHandler = null;
        if (!inPlacementMode) {
            setCursor(Cursor.getDefaultCursor());
        }
        repaint();
    }

    /** Activates placement mode for the next ship. */
    public void activatePlacement(int shipLength, boolean horizontal, Consumer<Position> onCellClick) {
        this.inPlacementMode   = true;
        this.previewLength     = shipLength;
        this.previewHorizontal = horizontal;
        this.clickHandler      = onCellClick;
        this.hoverCell         = null;
        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        repaint();
    }

    /** Deactivates placement mode. */
    public void deactivatePlacement() {
        this.inPlacementMode = false;
        this.hoverCell       = null;
        this.clickHandler    = null;
        setCursor(Cursor.getDefaultCursor());
        repaint();
    }

    /** Updates orientation while placement mode is active and repaints. */
    public void updateOrientation(boolean horizontal) {
        this.previewHorizontal = horizontal;
        repaint();
    }

    /**
     * Updates the grid data and triggers a repaint.
     *
     * @param grid The grid to display
     */
    public void setGrid(Grid grid) {
        setGrid(grid, true);
    }

    public void setGrid(Grid grid, boolean revealShips) {
        this.grid = grid;
        this.revealShips = revealShips;
        repaint();
    }

    /** Converts a mouse event to the corresponding grid cell, or {@code null} if outside. */
    private Position cellFromMouse(MouseEvent e) {
        if (grid == null) return null;
        int cols = grid.getWidth();
        int rows = grid.getHeight();
        int cw = Math.max(4, getWidth()  / (cols + 1));
        int ch = Math.max(4, getHeight() / (rows + 1));
        int col = (e.getX() / cw) - 1;
        int row = (e.getY() / ch) - 1;
        if (col < 0 || row < 0 || col >= cols || row >= rows) return null;

        return new Position(row, col);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (grid == null) {
            return;
        }

        int cols = grid.getWidth();
        int rows = grid.getHeight();

        // +1 to account for the header row and header column.
        int cellWidth  = Math.max(4, getWidth()  / (cols + 1));
        int cellHeight = Math.max(4, getHeight() / (rows + 1));

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Font headerFont = g2.getFont().deriveFont(Font.BOLD, Math.max(8f, cellHeight * 0.45f));
        g2.setFont(headerFont);
        FontMetrics fm = g2.getFontMetrics();

        Color headerBackground = new Color(200, 210, 230);

        // ── Header row (row 0): empty corner + letters A-J ───────────────
        for (int col = 0; col <= cols; col++) {
            int x = col * cellWidth;
            g2.setColor(headerBackground);
            g2.fillRect(x, 0, cellWidth, cellHeight);
            g2.setColor(BORDER_COLOR);
            g2.drawRect(x, 0, cellWidth, cellHeight);

            if (col > 0) {
                String letter = String.valueOf((char) ('A' + col - 1));
                int tx = x + (cellWidth  - fm.stringWidth(letter)) / 2;
                int ty = (cellHeight + fm.getAscent() - fm.getDescent()) / 2;
                g2.setColor(Color.DARK_GRAY);
                g2.drawString(letter, tx, ty);
            }
        }

        // ── Data rows ────────────────────────────────────────────────────
        for (int row = 0; row < rows; row++) {
            int hy = (row + 1) * cellHeight;

            // Header column: row numbers 1-10
            g2.setColor(headerBackground);
            g2.fillRect(0, hy, cellWidth, cellHeight);
            g2.setColor(BORDER_COLOR);
            g2.drawRect(0, hy, cellWidth, cellHeight);

            String number = String.valueOf(row + 1);
            int tx = (cellWidth  - fm.stringWidth(number)) / 2;
            int ty = hy + (cellHeight + fm.getAscent() - fm.getDescent()) / 2;
            g2.setColor(Color.DARK_GRAY);
            g2.drawString(number, tx, ty);

            // Data cells
            for (int col = 0; col < cols; col++) {
                Cell.CellState state = grid.getCell(new Position(row, col));

                Color fill;
                if (!revealShips) {
                    switch (state) {
                        case HIT -> fill = COLOR_EMPTY;
                        case SUNK -> fill = COLOR_SUNK;
                        default -> fill = COLOR_UNKNOWN;
                    }
                } else {
                    switch (state) {
                        case SHIP -> fill = COLOR_SHIP;
                        case HIT  -> fill = COLOR_HIT;
                        case SUNK -> fill = COLOR_SUNK;
                        case UNKNOW -> fill = COLOR_UNKNOWN;
                        default   -> fill = COLOR_EMPTY;
                    }
                }

                int x = (col + 1) * cellWidth;
                int y = (row + 1) * cellHeight;

                g2.setColor(fill);
                g2.fillRect(x, y, cellWidth, cellHeight);
                g2.setColor(BORDER_COLOR);
                g2.drawRect(x, y, cellWidth, cellHeight);
            }
        }

        // ── Placement preview overlay ─────────────────────────────────────
        if (inPlacementMode && hoverCell != null) {
            boolean valid = grid.canPlaceShip(hoverCell, previewLength, previewHorizontal);
            Color previewColor = valid ? COLOR_PREVIEW_VALID : COLOR_PREVIEW_INVALID;
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.6f));
            for (int i = 0; i < previewLength; i++) {
                int previewRow = previewHorizontal ? hoverCell.getX() : hoverCell.getX() + i;
                int previewCol = previewHorizontal ? hoverCell.getY() + i : hoverCell.getY();
                int pc = previewCol;
                int pr = previewRow;
                if (pc >= 0 && pc < cols && pr >= 0 && pr < rows) {
                    int px = (pc + 1) * cellWidth;
                    int py = (pr + 1) * cellHeight;
                    g2.setColor(previewColor);
                    g2.fillRect(px, py, cellWidth, cellHeight);
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
                    g2.setColor(BORDER_COLOR);
                    g2.drawRect(px, py, cellWidth, cellHeight);
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.6f));
                }
            }
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
        }
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(300, 300);
    }
}
