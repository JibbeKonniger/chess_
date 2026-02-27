package bots;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;

public class Evaluation {
    private static final int[] PIECE_VALUES = new int[13];
    static {
        PIECE_VALUES[Piece.WHITE_PAWN.ordinal()]   = 100;
        PIECE_VALUES[Piece.WHITE_KNIGHT.ordinal()] = 320;
        PIECE_VALUES[Piece.WHITE_BISHOP.ordinal()] = 330;
        PIECE_VALUES[Piece.WHITE_ROOK.ordinal()]   = 500;
        PIECE_VALUES[Piece.WHITE_QUEEN.ordinal()]  = 900;
        PIECE_VALUES[Piece.WHITE_KING.ordinal()]   = 20000;
        PIECE_VALUES[Piece.BLACK_PAWN.ordinal()]   = 100;
        PIECE_VALUES[Piece.BLACK_KNIGHT.ordinal()] = 320;
        PIECE_VALUES[Piece.BLACK_BISHOP.ordinal()] = 330;
        PIECE_VALUES[Piece.BLACK_ROOK.ordinal()]   = 500;
        PIECE_VALUES[Piece.BLACK_QUEEN.ordinal()]  = 900;
        PIECE_VALUES[Piece.BLACK_KING.ordinal()]   = 20000;
    }

    static int[] PESTO_PAWN_MG = {
            0,   0,   0,   0,   0,   0,   0,   0,
            98, 134,  61,  95,  68, 126,  34, -11,
            -6,   7,  26,  31,  65,  56,  25, -20,
            -14,  13,   6,  21,  23,  12,  17, -23,
            -27,  -2,  -5,  12,  17,   6,  10, -25,
            -26,  -4,  -4, -10,   3,   3,  33, -12,
            -35,  -1, -20, -23, -15,  24,  38, -22,
            0,   0,   0,   0,   0,   0,   0,   0
    };
    static int[] PESTO_PAWN_EG = {
            0,   0,   0,   0,   0,   0,   0,   0,
            178, 173, 158, 134, 147, 132, 165, 187,
            94, 100,  85,  67,  56,  53,  82,  84,
            32,  24,  13,   5,  -2,   4,  17,  17,
            13,   9,  -3,  -7,  -7,  -8,   3,  -1,
            4,   7,  -6,   1,   0,  -5,  -1,  -8,
            13,   8,   8,  10,  13,   0,   2,  -7,
            0,   0,   0,   0,   0,   0,   0,   0
    };
    static int[] PESTO_KNIGHT_MG = {
            -167, -89, -34, -49,  61, -97, -15, -107,
            -73, -41,  72,  36,  23,  62,   7,  -17,
            -47,  60,  37,  65,  84, 129,  73,   44,
            -9,  17,  19,  53,  37,  69,  18,   22,
            -13,   4,  16,  13,  28,  19,  21,   -8,
            -23,  -9,  12,  10,  19,  17,  25,  -16,
            -29, -53, -12,  -3,  -1,  18, -14,  -19,
            -105, -21, -58, -33, -17, -28, -19,  -23
    };

    static int[] PESTO_KNIGHT_EG = {
            -58, -38, -13, -28, -31, -27, -63, -99,
            -25,  -8, -25,  -2,  -9, -25, -24, -52,
            -24, -20,  10,   9,  -1,  -9, -19, -41,
            -17,   3,  22,  22,  22,  11,   8, -18,
            -18,  -6,  16,  25,  16,  17,   4, -18,
            -23,  -3,  -1,  15,  10,  -3, -20, -22,
            -42, -20, -10,  -5,  -2, -20, -23, -44,
            -29, -51, -23, -15, -22, -18, -50, -64
    };
    static int[] PESTO_BISHOP_MG = {
            -29,   4, -82, -37, -25, -42,   7,  -8,
            -26,  16, -18, -13,  30,  59,  18, -47,
            -16,  37,  43,  40,  35,  50,  37,  -2,
            -4,   5,  19,  50,  37,  37,   7,  -2,
            -6,  13,  13,  26,  34,  12,  10,   4,
            0,  15,  15,  15,  14,  27,  18,  10,
            4,  15,  16,   0,   7,  21,  33,   1,
            -33,  -3, -14, -21, -13, -12, -39, -21
    };

    static int[] PESTO_BISHOP_EG = {
            -14, -21, -11,  -8, -7,  -9, -17, -24,
            -8,  -4,   7, -12, -3, -13,  -4, -14,
            2,  -8,   0,  -1, -2,   6,   0,   4,
            -3,   9,  12,   9,  14,  10,   3,   2,
            -6,   3,  13,  19,   7,  10,  -3,  -9,
            -12,  -3,   8,  10,  13,   3,  -7, -15,
            -14, -18,  -7,  -1,   4,  -9, -15, -27,
            -23,  -9, -23,  -5,  -9, -16,  -5, -17
    };
    static int[] PESTO_ROOK_MG = {
            32,  42,  32,  51,  63,   9,  31,  43,
            27,  32,  58,  62,  80,  67,  26,  44,
            -5,  19,  26,  36,  17,  45,  61,  16,
            -24, -11,   7,  26,  24,  35,  -8, -20,
            -36, -26, -12,  -1,   9,  -7,   6, -23,
            -45, -25, -16, -17,   3,   0,  -5, -33,
            -44, -16, -20,  -9,  -1,  11,  -6, -71,
            -19, -13,   1,  17,  16,   7, -37, -26
    };

    static int[] PESTO_ROOK_EG = {
            13,  10,  18,  15,  12,  12,   8,   5,
            -8,  22,  24,  27,  26,  33,  26,   3,
            -18,  -4,  21,  24,  27,  23,   9, -11,
            -19,  -3,  11,  21,  23,  16,   7,  -9,
            -27, -11,   4,  13,  14,   4,  -5, -17,
            -53, -34, -21, -11, -28, -14, -24, -43,
            0,    0,   0,   0,   0,   0,    0,   0,
            0,    0,   0,   0,   0,   0,    0,   0
    };
    static int[] PESTO_QUEEN_MG = {
            -74, -23, -26, -24, -19, -35, -22, -24,
            -14, -18,   0, -19, -15, -12, -11, -17,
            -2,  -6,   9,   6,   6,   1,   0,   6,
            -7,   2,  -5, -12, -12, -14,   3, -13,
            -11,  -4,   2,  -11,   1,   1,  -20, -10,
            -12,  -8,   0,   -9,  -5,  -9,  -12, -13,
            -29, -15, -13, -10, -11, -15, -14, -20,
            -21, -25, -14, -12, -14, -15, -27, -23
    };

    static int[] PESTO_QUEEN_EG = {
            -58, -42, -23, -41, -31, -42, -20, -43,
            -28, -21, -26, -19, -16, -18, -19, -25,
            -32, -19, -23, -15, -11, -11, -18, -32,
            -29, -12, -12, -10,  -8, -12, -16, -14,
            -26, -10,  -8, -10,  -6, -11, -13, -23,
            -25, -17, -11, -13,  -7, -12, -13, -23,
            -32, -18, -28, -16, -14, -15, -20, -28,
            -24, -23, -12, -15, -11, -16, -22, -22
    };
    static int[] PESTO_KING_MG = {
            -65,  23,  16, -15, -56, -34,   2,  13,
            29, -14,  -3, -12, -13,  -9,  -12, -39,
            -9,   -1, -10,  -6,  -7, -16,  -14, -10,
            -17, -20,  -12, -27, -30, -12, -18, -14,
            -19, -24,  -20, -14, -14, -27, -22, -23,
            -16, -20,  -13, -16, -13, -21, -24, -20,
            24,  -24,  -52, -11,  -5,  -28, -36, -26,
            -74,  -35, -18, -18, -11,  15,   4, -17
    };

    static int[] PESTO_KING_EG = {
            -50, -40, -30, -20, -20, -30, -40, -50,
            -30, -20, -10,   0,   0, -10, -20, -30,
            -30, -10,  20,  30,  30,  20, -10, -30,
            -30, -10,  30,  40,  40,  30, -10, -30,
            -30, -10,  30,  40,  40,  30, -10, -30,
            -30, -10,  20,  30,  30,  20, -10, -30,
            -30, -20, -10,   0,   0, -10, -20, -30,
            -50, -30, -30, -30, -30, -30, -30, -50
    };

    public static int evaluation(Board board) {
        int score = 0;
        for (Square sq : Square.values()) {
            if (sq == Square.NONE) continue;

            /*----------- GET PIECE VALUE -------------*/
            int pieceValue = 0;
            Piece piece = board.getPiece(sq);
            if (piece == Piece.NONE) continue;
            pieceValue += pstValue(board, piece, sq);
            pieceValue += PIECE_VALUES[piece.getPieceType().ordinal()];
            /*-----------------------------------------*/

            if (piece.getPieceSide() == Side.WHITE) score += pieceValue;
            else score -= pieceValue;
        }
        return score;
    }

    private static int pstValue(Board board, Piece piece, Square sq) {
        // PST index: white reads rank bottom-up, black reads rank top-down
        int file  = sq.getFile().ordinal();
        int rank  = sq.getRank().ordinal();
        boolean white = piece.getPieceSide() == Side.WHITE;
        int idx = white ? (rank * 8 + file) : ((7 - rank) * 8 + file);
        double phase = calculatePhase(board);
        double phaseX = 1-phase;

        switch (piece.getPieceType()) {
            case PAWN:   return (int) (phase * PESTO_PAWN_MG[idx] + phaseX * PESTO_PAWN_EG[idx]);
            case KNIGHT: return (int) (phase * PESTO_KNIGHT_MG[idx] + phaseX * PESTO_KNIGHT_EG[idx]);
            case BISHOP: return (int) (phase * PESTO_BISHOP_MG[idx] + phaseX * PESTO_BISHOP_EG[idx]);
            case ROOK:   return (int) (phase * PESTO_ROOK_MG[idx] + phaseX * PESTO_ROOK_EG[idx]);
            case QUEEN:  return (int) (phase * PESTO_QUEEN_MG[idx] + phaseX * PESTO_QUEEN_EG[idx]);
            case KING:   return (int) (phase * PESTO_KING_MG[idx] + phaseX * PESTO_KING_EG[idx]);
            default:     return 0;
        }
    }

    /**
     * The more pieces on the board, the higher the phase.
     * @param board
     * @return
     */
    private static double calculatePhase(Board board) {
        int startingTotal = 32;
        int currentTotal = Long.bitCount(board.getBitboard());
        int currentTotalSquare = currentTotal * currentTotal;

        return Math.max(Math.min((1 + -0.03 * currentTotalSquare) / startingTotal, 1), 0);
    }
}
