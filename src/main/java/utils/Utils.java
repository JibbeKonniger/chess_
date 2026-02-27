package utils;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.move.Move;

public class Utils {
    public static long perftTT(Board board, int depth, TranspositionTable tt) {

        long key = board.getZobristKey();
        TranspositionTable.Entry entry = tt.get(key);
        if (entry != null && entry.depth == depth) {
            return entry.score;
        }

        if (depth == 0) {
            return 1;
        }

        long sum = 0;
        for (Move move : board.legalMoves()) {
            board.doMove(move);
            sum += perftTT(board, depth - 1, tt);
            board.undoMove();
        }

        tt.put(key, depth, (int) sum, TranspositionTable.Flag.EXACT, null);
        return sum;
    }

    public static long perft(Board board, int depth) {
        if (depth == 0) {
            return 1;
        }

        long sum = 0;
        for (Move move : board.legalMoves()) {
            board.doMove(move);
            sum += perft(board, depth - 1);
            board.undoMove();
        }

        return sum;
    }
}
