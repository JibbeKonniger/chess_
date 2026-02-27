package bots;

import com.github.bhlangonijr.chesslib.*;
import com.github.bhlangonijr.chesslib.move.Move;
import openings.OpeningBook;
import utils.TranspositionTable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MinimaxBot implements ChessBot {

    // CONFIG
    private static final long TIME_LIMIT_MS = 2000;
    private static final int  MAX_DEPTH     = 64;
    private static final int  INF           = 1_000_000;
    private static final int  MATE_SCORE    = 900_000;

    // Penalty applied to any move that repeats a position seen in the game
    // Large enough to avoid repetition, small enough to still accept it if
    // it's the only way to avoid losing
    private static final int  REPETITION_PENALTY = 300;

    private static final int[] PIECE_VALUE = new int[13];
    static {
        PIECE_VALUE[Piece.WHITE_PAWN.ordinal()]   = 100;
        PIECE_VALUE[Piece.WHITE_KNIGHT.ordinal()] = 320;
        PIECE_VALUE[Piece.WHITE_BISHOP.ordinal()] = 330;
        PIECE_VALUE[Piece.WHITE_ROOK.ordinal()]   = 500;
        PIECE_VALUE[Piece.WHITE_QUEEN.ordinal()]  = 900;
        PIECE_VALUE[Piece.WHITE_KING.ordinal()]   = 20000;
        PIECE_VALUE[Piece.BLACK_PAWN.ordinal()]   = 100;
        PIECE_VALUE[Piece.BLACK_KNIGHT.ordinal()] = 320;
        PIECE_VALUE[Piece.BLACK_BISHOP.ordinal()] = 330;
        PIECE_VALUE[Piece.BLACK_ROOK.ordinal()]   = 500;
        PIECE_VALUE[Piece.BLACK_QUEEN.ordinal()]  = 900;
        PIECE_VALUE[Piece.BLACK_KING.ordinal()]   = 20000;
    }

    private static final int[] PST_PAWN = {
            0,  0,  0,  0,  0,  0,  0,  0,
            50, 50, 50, 50, 50, 50, 50, 50,
            10, 10, 20, 30, 30, 20, 10, 10,
            5,  5, 10, 25, 25, 10,  5,  5,
            0,  0,  0, 20, 20,  0,  0,  0,
            5, -5,-10,  0,  0,-10, -5,  5,
            5, 10, 10,-20,-20, 10, 10,  5,
            0,  0,  0,  0,  0,  0,  0,  0
    };
    private static final int[] PST_KNIGHT = {
            -50,-40,-30,-30,-30,-30,-40,-50,
            -40,-20,  0,  0,  0,  0,-20,-40,
            -30,  0, 10, 15, 15, 10,  0,-30,
            -30,  5, 15, 20, 20, 15,  5,-30,
            -30,  0, 15, 20, 20, 15,  0,-30,
            -30,  5, 10, 15, 15, 10,  5,-30,
            -40,-20,  0,  5,  5,  0,-20,-40,
            -50,-40,-30,-30,-30,-30,-40,-50
    };
    private static final int[] PST_BISHOP = {
            -20,-10,-10,-10,-10,-10,-10,-20,
            -10,  0,  0,  0,  0,  0,  0,-10,
            -10,  0,  5, 10, 10,  5,  0,-10,
            -10,  5,  5, 10, 10,  5,  5,-10,
            -10,  0, 10, 10, 10, 10,  0,-10,
            -10, 10, 10, 10, 10, 10, 10,-10,
            -10,  5,  0,  0,  0,  0,  5,-10,
            -20,-10,-10,-10,-10,-10,-10,-20
    };
    private static final int[] PST_ROOK = {
            0,  0,  0,  0,  0,  0,  0,  0,
            5, 10, 10, 10, 10, 10, 10,  5,
            -5,  0,  0,  0,  0,  0,  0, -5,
            -5,  0,  0,  0,  0,  0,  0, -5,
            -5,  0,  0,  0,  0,  0,  0, -5,
            -5,  0,  0,  0,  0,  0,  0, -5,
            -5,  0,  0,  0,  0,  0,  0, -5,
            0,  0,  0,  5,  5,  0,  0,  0
    };
    private static final int[] PST_QUEEN = {
            -20,-10,-10, -5, -5,-10,-10,-20,
            -10,  0,  0,  0,  0,  0,  0,-10,
            -10,  0,  5,  5,  5,  5,  0,-10,
            -5,  0,  5,  5,  5,  5,  0, -5,
            0,  0,  5,  5,  5,  5,  0, -5,
            -10,  5,  5,  5,  5,  5,  0,-10,
            -10,  0,  5,  0,  0,  0,  0,-10,
            -20,-10,-10, -5, -5,-10,-10,-20
    };
    private static final int[] PST_KING_MG = {
            -30,-40,-40,-50,-50,-40,-40,-30,
            -30,-40,-40,-50,-50,-40,-40,-30,
            -30,-40,-40,-50,-50,-40,-40,-30,
            -30,-40,-40,-50,-50,-40,-40,-30,
            -20,-30,-30,-40,-40,-30,-30,-20,
            -10,-20,-20,-20,-20,-20,-20,-10,
            20, 20,  0, -20, -20,  0, 20, 20,
            20, 30, 10,   0,   0, 10, 30, 20
    };

    // FIELDS
    private final TranspositionTable tt          = new TranspositionTable();
    private final OpeningBook        openingBook = new OpeningBook();

    // Positions seen during the actual game — persists across moves
    private final Set<Long> gameHistory = new HashSet<>();

    private long    deadline;
    private boolean timeUp;
    private Move    bestMoveAtRoot;

    @Override
    public Move getBestMove(Board board) {
        // Record current position into game history before searching
        gameHistory.add(board.getZobristKey());

        Move bookMove = openingBook.nextMove(board);
        if (bookMove != null) {
            // Record the position after the book move too
            board.doMove(bookMove);
            gameHistory.add(board.getZobristKey());
            board.undoMove();
            return bookMove;
        }

        deadline = System.currentTimeMillis() + TIME_LIMIT_MS;
        timeUp   = false;

        Move bestMove  = null;
        int  bestScore = 0;
        int  depth;

        for (depth = 1; depth <= MAX_DEPTH; depth++) {
            bestMoveAtRoot = null;
            int score = alphaBeta(board, depth, -INF, INF, 0);

            if (!timeUp) {
                if (bestMoveAtRoot != null) bestMove = bestMoveAtRoot;
                bestScore = score;
            }

            if (timeUp) break;
            if (Math.abs(bestScore) >= MATE_SCORE - MAX_DEPTH) break;
        }

        System.out.printf("Last Completed Depth: %d | Score for Moving Side: %d%n", depth, bestScore);

        if (bestMove == null) {
            List<Move> moves = board.legalMoves();
            if (!moves.isEmpty()) bestMove = moves.get(0);
        }

        // Record the position we're moving into
        if (bestMove != null) {
            board.doMove(bestMove);
            gameHistory.add(board.getZobristKey());
            board.undoMove();
        }

        return bestMove;
    }

    private int alphaBeta(Board board, int depth, int alpha, int beta, int ply) {
        if (timeUp) return 0;
        if (System.currentTimeMillis() >= deadline) { timeUp = true; return 0; }

        long key = board.getZobristKey();

        // Penalise positions already seen in the actual game.
        // Only at ply > 0 — at ply 0 we want the search to still find
        // the best non-repeating move rather than just returning early.
        if (ply > 0 && gameHistory.contains(key)) {
            return -REPETITION_PENALTY;
        }

        // TT lookup
        TranspositionTable.Entry entry = tt.get(key);
        Move ttMove = null;
        if (entry != null) {
            ttMove = entry.bestMove;
            if (entry.depth >= depth) {
                switch (entry.flag) {
                    case EXACT:       if (ply > 0) return entry.score; break;
                    case LOWER_BOUND: alpha = Math.max(alpha, entry.score); break;
                    case UPPER_BOUND: beta  = Math.min(beta,  entry.score); break;
                }
                if (alpha >= beta) return entry.score;
            }
        }

        List<Move> moves = board.legalMoves();
        if (moves.isEmpty()) return board.isKingAttacked() ? -MATE_SCORE + ply : 0;
        if (board.isDraw())  return 0;
        if (depth <= 0)      return quiesce(board, alpha, beta);

        moves = orderMoves(board, moves, ttMove);

        int  originalAlpha = alpha;
        Move bestMove      = null;
        int  best          = -INF;

        for (Move move : moves) {
            board.doMove(move);
            int score = -alphaBeta(board, depth - 1, -beta, -alpha, ply + 1);
            board.undoMove();

            if (timeUp) return 0;

            if (score > best) {
                best     = score;
                bestMove = move;
                if (ply == 0) bestMoveAtRoot = move;
            }

            alpha = Math.max(alpha, score);
            if (alpha >= beta) break;
        }

        TranspositionTable.Flag flag;
        if      (best <= originalAlpha) flag = TranspositionTable.Flag.UPPER_BOUND;
        else if (best >= beta)          flag = TranspositionTable.Flag.LOWER_BOUND;
        else                            flag = TranspositionTable.Flag.EXACT;
        tt.put(key, depth, best, flag, bestMove);

        return best;
    }

    private int quiesce(Board board, int alpha, int beta) {
        if (timeUp) return 0;

        int standPat = evaluate(board);
        if (standPat >= beta) return beta;
        alpha = Math.max(alpha, standPat);

        for (Move move : board.legalMoves()) {
            if (!isCapture(board, move)) continue;

            board.doMove(move);
            int score = -quiesce(board, -beta, -alpha);
            board.undoMove();

            if (score >= beta) return beta;
            alpha = Math.max(alpha, score);
        }
        return alpha;
    }

    private List<Move> orderMoves(Board board, List<Move> moves, Move ttMove) {
        List<Move> ordered  = new ArrayList<>(moves.size());
        List<Move> captures = new ArrayList<>();
        List<Move> quiets   = new ArrayList<>();

        for (Move m : moves) {
            if (m.equals(ttMove))        ordered.add(m);
            else if (isCapture(board, m)) captures.add(m);
            else                          quiets.add(m);
        }

        captures.sort((a, b) -> mvvLva(board, b) - mvvLva(board, a));
        ordered.addAll(captures);
        ordered.addAll(quiets);
        return ordered;
    }

    private int mvvLva(Board board, Move move) {
        Piece victim   = board.getPiece(move.getTo());
        Piece attacker = board.getPiece(move.getFrom());
        int   vv = victim   == Piece.NONE ? 0 : PIECE_VALUE[victim.ordinal()];
        int   av = attacker == Piece.NONE ? 0 : PIECE_VALUE[attacker.ordinal()];
        return vv * 10 - av;
    }

    private int evaluate(Board board) {
        int score      = 0;
        int pieceCount = 0;

        for (Square sq : Square.values()) {
            if (sq == Square.NONE) continue;
            Piece piece = board.getPiece(sq);
            if (piece == Piece.NONE) continue;

            pieceCount++;
            int value = PIECE_VALUE[piece.ordinal()] + pstValue(piece, sq);
            if (piece.getPieceSide() == Side.WHITE) score += value;
            else                                     score -= value;

            if (piece.getPieceType() == PieceType.PAWN) {
                int rank = sq.getRank().ordinal();
                if (piece.getPieceSide() == Side.WHITE) score += rank * 20;
                else                                     score += (7 - rank) * 20;
            }
        }

        if (pieceCount <= 8) {
            Square ks   = board.getKingSquare(board.getSideToMove());
            int    file = ks.getFile().ordinal();
            int    rank = ks.getRank().ordinal();
            score += ((3 - Math.abs(3 - file)) + (3 - Math.abs(3 - rank))) * 50;
        }

        return board.getSideToMove() == Side.WHITE ? score : -score;
    }

    private int pstValue(Piece piece, Square sq) {
        int     file  = sq.getFile().ordinal();
        int     rank  = sq.getRank().ordinal();
        boolean white = piece.getPieceSide() == Side.WHITE;
        int     idx   = white ? (rank * 8 + file) : ((7 - rank) * 8 + file);

        switch (piece.getPieceType()) {
            case PAWN:   return PST_PAWN[idx];
            case KNIGHT: return PST_KNIGHT[idx];
            case BISHOP: return PST_BISHOP[idx];
            case ROOK:   return PST_ROOK[idx];
            case QUEEN:  return PST_QUEEN[idx];
            case KING:   return PST_KING_MG[idx];
            default:     return 0;
        }
    }

    private boolean isCapture(Board board, Move move) {
        if (board.getPiece(move.getTo()) != Piece.NONE) return true;
        if (board.getEnPassant() == move.getTo()) {
            Piece moving = board.getPiece(move.getFrom());
            return moving == Piece.WHITE_PAWN || moving == Piece.BLACK_PAWN;
        }
        return false;
    }
}