package bots;

import com.github.bhlangonijr.chesslib.*;
import com.github.bhlangonijr.chesslib.move.Move;
import openings.OpeningBook;
import utils.TranspositionTable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MinimaxBot implements ChessBot {

    // ------------------------------------------------------------------ //
    //  Config                                                              //
    // ------------------------------------------------------------------ //
    private static final long TIME_LIMIT_MS = 2000;
    private static final int  MAX_DEPTH     = 64;
    private static final int  INF           = 1_000_000;
    private static final int  MATE_SCORE    = 900_000;

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

    // ------------------------------------------------------------------ //
    //  Piece-square tables                                                 //
    // ------------------------------------------------------------------ //
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
            20, 20,  0,  0,  0,  0, 20, 20,
            20, 30, 10,  0,  0, 10, 30, 20
    };

    // ------------------------------------------------------------------ //
    //  Search state                                                        //
    // ------------------------------------------------------------------ //
    private final TranspositionTable tt          = new TranspositionTable();
    private final OpeningBook        openingBook = new OpeningBook();

    // Killer moves: 2 slots per ply, up to 64 plies deep
    private final Move[][] killers = new Move[64][2];

    // History heuristic: [piece_ordinal][to_square_ordinal]
    // Incremented by depth² every time a quiet move causes a beta cutoff
    private final int[][] history = new int[13][64];

    private long    deadline;
    private boolean timeUp;
    private Move    bestMoveAtRoot;

    // ------------------------------------------------------------------ //
    //  Entry point                                                         //
    // ------------------------------------------------------------------ //
    @Override
    public Move getBestMove(Board board) {

        // Book moves
        Move bookMove = openingBook.nextMove(board);
        if (bookMove != null) return bookMove;

        // Reset per-search heuristic tables
        for (Move[] k : killers) { k[0] = null; k[1] = null; }
        for (int[] row : history) Arrays.fill(row, 0);

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

        System.out.printf("[Engine] Depth: %d | Score: %d%n", depth, bestScore);

        if (bestMove == null) {
            List<Move> moves = board.legalMoves();
            if (!moves.isEmpty()) bestMove = moves.get(0);
        }

        return bestMove;
    }

    // ------------------------------------------------------------------ //
    //  Alpha-beta (negamax)                                               //
    // ------------------------------------------------------------------ //
    private int alphaBeta(Board board, int depth, int alpha, int beta, int ply) {
        if (timeUp) return 0;
        if (System.currentTimeMillis() >= deadline) { timeUp = true; return 0; }

        long key = board.getZobristKey();

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

        // Terminal / leaf
        List<Move> moves = board.legalMoves();
        if (moves.isEmpty()) return board.isKingAttacked() ? -MATE_SCORE + ply : 0;
        if (board.isDraw())  return 0;
        if (depth <= 0)      return quiesce(board, alpha, beta);

        moves = orderMoves(board, moves, ttMove, ply);

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

            if (score > alpha) alpha = score;

            if (alpha >= beta) {
                // Beta cutoff — update killer and history for quiet moves
                if (!isCapture(board, move)) {
                    // Killers
                    if (ply < killers.length && !move.equals(killers[ply][0])) {
                        killers[ply][1] = killers[ply][0];
                        killers[ply][0] = move;
                    }
                    // History: weight by depth² so deep cutoffs matter more
                    Piece p = board.getPiece(move.getFrom());
                    if (p != Piece.NONE) {
                        history[p.ordinal()][move.getTo().ordinal()] += depth * depth;
                    }
                }
                break;
            }
        }

        // TT store
        TranspositionTable.Flag flag;
        if      (best <= originalAlpha) flag = TranspositionTable.Flag.UPPER_BOUND;
        else if (best >= beta)          flag = TranspositionTable.Flag.LOWER_BOUND;
        else                            flag = TranspositionTable.Flag.EXACT;
        tt.put(key, depth, best, flag, bestMove);

        return best;
    }

    // ------------------------------------------------------------------ //
    //  Quiescence search                                                   //
    // ------------------------------------------------------------------ //
    private int quiesce(Board board, int alpha, int beta) {
        if (timeUp) return 0;

        int standPat = evaluate(board);
        if (standPat >= beta) return beta;
        alpha = Math.max(alpha, standPat);

        // Only search winning/equal captures in quiescence (SEE >= 0)
        for (Move move : board.legalMoves()) {
            if (!isCapture(board, move)) continue;
            if (see(board, move) < 0)    continue; // skip losing captures

            board.doMove(move);
            int score = -quiesce(board, -beta, -alpha);
            board.undoMove();

            if (score >= beta) return beta;
            alpha = Math.max(alpha, score);
        }
        return alpha;
    }

    // ------------------------------------------------------------------ //
    //  Move ordering                                                       //
    //                                                                      //
    //  Priority:                                                           //
    //    1. TT move          (10_000_000)                                  //
    //    2. Queen promotions  (9_000_000)                                  //
    //    3. Winning captures  (8_000_000 + MVV-LVA)  SEE >= 0             //
    //    4. Killer move 1     (6_000_000)                                  //
    //    5. Killer move 2     (5_000_000)                                  //
    //    6. Quiet moves       (history score, 0+)                          //
    //    7. Minor promotions  (4_000_000)  below quiets so they're visible //
    //    8. Losing captures  (-1_000_000 + MVV-LVA)  SEE < 0              //
    // ------------------------------------------------------------------ //
    private List<Move> orderMoves(Board board, List<Move> moves, Move ttMove, int ply) {
        int[] scores = new int[moves.size()];

        for (int i = 0; i < moves.size(); i++) {
            Move m = moves.get(i);

            if (m.equals(ttMove)) {
                scores[i] = 10_000_000;
                continue;
            }

            // Promotions
            if (m.getPromotion() != null && m.getPromotion() != Piece.NONE) {
                scores[i] = m.getPromotion().getPieceType() == PieceType.QUEEN
                        ? 9_000_000
                        : 4_000_000;
                continue;
            }

            // Captures — scored by SEE, tiebroken by MVV-LVA
            if (isCapture(board, m)) {
                int seeScore = see(board, m);
                int mvv      = mvvLva(board, m);
                scores[i] = seeScore >= 0
                        ? 8_000_000 + mvv   // winning or equal
                        : -1_000_000 + mvv; // losing — try last
                continue;
            }

            // Killers
            if (ply < killers.length) {
                if (m.equals(killers[ply][0])) { scores[i] = 6_000_000; continue; }
                if (m.equals(killers[ply][1])) { scores[i] = 5_000_000; continue; }
            }

            // History heuristic for all other quiets
            Piece p = board.getPiece(m.getFrom());
            if (p != Piece.NONE) {
                scores[i] = history[p.ordinal()][m.getTo().ordinal()];
            }
        }

        // Sort indices by score descending
        Integer[] idx = new Integer[moves.size()];
        for (int i = 0; i < idx.length; i++) idx[i] = i;
        Arrays.sort(idx, (a, b) -> scores[b] - scores[a]);

        List<Move> ordered = new ArrayList<>(moves.size());
        for (int i : idx) ordered.add(moves.get(i));
        return ordered;
    }

    // ------------------------------------------------------------------ //
    //  Static Exchange Evaluation (SEE)                                    //
    //  Simulates the full capture recapture sequence on a square.         //
    //  Returns net material gain from the moving side's perspective:      //
    //    positive = winning capture, 0 = even, negative = losing          //
    // ------------------------------------------------------------------ //
    private int see(Board board, Move move) {
        Piece captured = board.getPiece(move.getTo());
        int gain = (captured != Piece.NONE) ? PIECE_VALUE[captured.ordinal()] : 0;
        Piece attacker = board.getPiece(move.getFrom());
        if (attacker == Piece.NONE) return 0;

        board.doMove(move);
        // Opponent now recaptures — subtract what they can win
        int response = seeRecapture(board, move.getTo(), PIECE_VALUE[attacker.ordinal()]);
        board.undoMove();

        return gain - response;
    }

    /**
     * Returns the best net gain the side to move can achieve by recapturing on {@code sq},
     * given the last piece placed there was worth {@code lastValue}.
     */
    private int seeRecapture(Board board, Square sq, int lastValue) {
        // Find the cheapest attacker available for the side to move
        Move   cheapest    = null;
        int    cheapestVal = Integer.MAX_VALUE;

        for (Move m : board.legalMoves()) {
            if (m.getTo() != sq) continue;
            Piece p = board.getPiece(m.getFrom());
            if (p == Piece.NONE) continue;
            int v = PIECE_VALUE[p.ordinal()];
            if (v < cheapestVal) { cheapestVal = v; cheapest = m; }
        }

        if (cheapest == null) return 0; // no recapture available

        // Recapture gains lastValue but risks cheapestVal
        board.doMove(cheapest);
        int response = seeRecapture(board, sq, cheapestVal);
        board.undoMove();

        // The side to move will only recapture if it's profitable
        return Math.max(0, lastValue - response);
    }

    // ------------------------------------------------------------------ //
    //  MVV-LVA tiebreaker for captures of equal SEE                       //
    // ------------------------------------------------------------------ //
    private int mvvLva(Board board, Move move) {
        Piece victim   = board.getPiece(move.getTo());
        Piece attacker = board.getPiece(move.getFrom());
        int   vv = victim   == Piece.NONE ? 0 : PIECE_VALUE[victim.ordinal()];
        int   av = attacker == Piece.NONE ? 0 : PIECE_VALUE[attacker.ordinal()];
        return vv * 10 - av;
    }

    // ------------------------------------------------------------------ //
    //  Evaluation                                                          //
    // ------------------------------------------------------------------ //
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

            // Pawn advancement bonus
            if (piece.getPieceType() == PieceType.PAWN) {
                int rank = sq.getRank().ordinal();
                if (piece.getPieceSide() == Side.WHITE) score += rank * 20;
                else                                     score -= (7 - rank) * 20;
            }
        }

        // Endgame: king centralisation
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

    // ------------------------------------------------------------------ //
    //  Helpers                                                             //
    // ------------------------------------------------------------------ //
    private boolean isCapture(Board board, Move move) {
        if (board.getPiece(move.getTo()) != Piece.NONE) return true;
        if (board.getEnPassant() == move.getTo()) {
            Piece moving = board.getPiece(move.getFrom());
            return moving == Piece.WHITE_PAWN || moving == Piece.BLACK_PAWN;
        }
        return false;
    }
}