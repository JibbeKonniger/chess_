import bots.ChessBot;
import bots.MinimaxBot;
import com.github.bhlangonijr.chesslib.*;
import com.github.bhlangonijr.chesslib.move.Move;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class ChessGUI extends JFrame {

    private static final int SQ         = 100;
    private static final int RESTART_MS = 10000;

    private static final Color CLR_LIGHT     = new Color(240, 217, 181);
    private static final Color CLR_DARK      = new Color(181, 136, 99);
    private static final Color CLR_HIGHLIGHT = new Color(205, 209, 110, 180);

    private Board    board     = new Board();
    private ChessBot whiteBot  = new MinimaxBot();
    private ChessBot blackBot  = new MinimaxBot();
    private Move     lastMove  = null;
    private boolean  paused    = false;
    private String   resultMsg = null;

    private final Map<String, Image> images    = new HashMap<>();
    private final BoardPanel         boardPanel = new BoardPanel();
    private Timer moveTimer;
    private Timer restartTimer;

    private JButton pauseBtn;
    private JLabel  statusLabel;

    public ChessGUI() {
        loadImages();
        setTitle("Chess");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(false);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 6));
        controls.setBackground(new Color(30, 30, 30));

        pauseBtn = new JButton("Pause");
        styleButton(pauseBtn);
        pauseBtn.addActionListener(e -> togglePause());

        JButton newGameBtn = new JButton("New Game");
        styleButton(newGameBtn);
        newGameBtn.addActionListener(e -> newGame());

        statusLabel = new JLabel("White to move");
        statusLabel.setForeground(new Color(200, 200, 200));
        statusLabel.setFont(new Font("SansSerif", Font.BOLD, 13));

        controls.add(pauseBtn);
        controls.add(newGameBtn);
        controls.add(Box.createHorizontalStrut(16));
        controls.add(statusLabel);

        add(boardPanel, BorderLayout.CENTER);
        add(controls,   BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
        setVisible(true);
        startMoveTimer();
    }

    private void startMoveTimer() {
        if (moveTimer != null) moveTimer.stop();
        moveTimer = new Timer(300, e -> tick());
        moveTimer.start();
    }

    private void tick() {
        if (paused) return;

        if (board.isMated() || board.isDraw()) {
            moveTimer.stop();
            resultMsg = board.isMated()
                    ? (board.getSideToMove() == Side.WHITE ? "Black" : "White") + " wins!"
                    : "Draw!";
            statusLabel.setText(resultMsg + "  — restarting in 3s");
            boardPanel.repaint();
            restartTimer = new Timer(RESTART_MS, ev -> newGame());
            restartTimer.setRepeats(false);
            restartTimer.start();
            return;
        }

        moveTimer.stop();
        ChessBot bot = board.getSideToMove() == Side.WHITE ? whiteBot : blackBot;

        new SwingWorker<Move, Void>() {
            @Override protected Move doInBackground() { return bot.getBestMove(board); }
            @Override protected void done() {
                try {
                    Move move = get();
                    if (move != null && !board.isMated() && !board.isDraw()) {
                        board.doMove(move);
                        lastMove = move;
                        updateStatus();
                        boardPanel.repaint();
                    }
                } catch (Exception ex) { ex.printStackTrace(); }
                if (!paused && resultMsg == null) startMoveTimer();
            }
        }.execute();
    }

    private void togglePause() {
        paused = !paused;
        pauseBtn.setText(paused ? "Play" : "Pause");
        if (!paused && resultMsg == null) startMoveTimer();
    }

    private void newGame() {
        if (restartTimer != null) restartTimer.stop();
        if (moveTimer    != null) moveTimer.stop();
        board     = new Board();
        whiteBot  = new MinimaxBot();
        blackBot  = new MinimaxBot();
        lastMove  = null;
        resultMsg = null;
        paused    = false;
        pauseBtn.setText("Pause");
        updateStatus();
        boardPanel.repaint();
        startMoveTimer();
    }

    private void updateStatus() {
        if (resultMsg != null) return;
        statusLabel.setText((board.getSideToMove() == Side.WHITE ? "White" : "Black") + " to move");
    }

    private class BoardPanel extends JPanel {
        BoardPanel() { setPreferredSize(new Dimension(SQ * 8, SQ * 8)); }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_SPEED);

            for (int r = 0; r < 8; r++) {
                for (int f = 0; f < 8; f++) {
                    int x = f * SQ, y = r * SQ;

                    g2.setColor((r + f) % 2 == 0 ? CLR_LIGHT : CLR_DARK);
                    g2.fillRect(x, y, SQ, SQ);

                    if (lastMove != null) {
                        Square sq = Square.squareAt((7 - r) * 8 + f);
                        if (sq == lastMove.getFrom() || sq == lastMove.getTo()) {
                            g2.setColor(CLR_HIGHLIGHT);
                            g2.fillRect(x, y, SQ, SQ);
                        }
                    }

                    Square square = Square.squareAt((7 - r) * 8 + f);
                    Piece  piece  = board.getPiece(square);
                    if (piece != Piece.NONE) {
                        Image img = images.get(pieceKey(piece));
                        if (img != null) {
                            int sw = img.getWidth(null) * 4;
                            int sh = img.getHeight(null) * 4;
                            int ox = x + (SQ - sw) / 2;
                            int oy = y + (SQ - sh) / 2;
                            g2.drawImage(img, ox, oy, sw, sh, null);
                        }
                    }
                }
            }

            if (resultMsg != null) {
                g2.setColor(new Color(0, 0, 0, 130));
                g2.fillRect(0, SQ * 3, SQ * 8, SQ * 2);
                g2.setFont(new Font("SansSerif", Font.BOLD, 36));
                FontMetrics fm = g2.getFontMetrics();
                g2.setColor(new Color(255, 220, 50));
                g2.drawString(resultMsg, (SQ * 8 - fm.stringWidth(resultMsg)) / 2, SQ * 4 + 12);
            }
        }
    }

    private void loadImages() {
        String[] names = {
                "white/pawn","white/rook","white/knight","white/bishop","white/queen","white/king",
                "black/pawn","black/rook","black/knight","black/bishop","black/queen","black/king"
        };
        for (String n : names) {
            var url = Thread.currentThread().getContextClassLoader()
                    .getResource("Pieces/color/" + n + ".png");
            if (url == null) throw new RuntimeException("Missing: Pieces/color/" + n + ".png");
            images.put(n, new ImageIcon(url).getImage());
        }
    }

    private String pieceKey(Piece piece) {
        String color = piece.getPieceSide() == Side.WHITE ? "white/" : "black/";
        String type  = switch (piece.getPieceType()) {
            case PAWN   -> "pawn";
            case ROOK   -> "rook";
            case KNIGHT -> "knight";
            case BISHOP -> "bishop";
            case QUEEN  -> "queen";
            case KING   -> "king";
            default     -> throw new IllegalStateException("Unknown: " + piece.getPieceType());
        };
        return color + type;
    }

    private void styleButton(JButton btn) {
        btn.setFont(new Font("SansSerif", Font.BOLD, 13));
        btn.setForeground(Color.WHITE);
        btn.setBackground(new Color(60, 60, 60));
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(90, 90, 90)),
                BorderFactory.createEmptyBorder(5, 14, 5, 14)));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ChessGUI::new);
    }
}