package bots;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.move.Move;

public interface ChessBot {
    Move getBestMove(Board board);
}
