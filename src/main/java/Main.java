import com.github.bhlangonijr.chesslib.*;
import utils.TranspositionTable;
import utils.Utils;

public class Main {

    public static void main(String[] args) {
        Board board = new Board();
        board.loadFromFen("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1");
        for (int depth = 0; depth < 20; depth++) {
            TranspositionTable tt = new TranspositionTable();
            long startTime = System.currentTimeMillis();
            long perft = Utils.perft(board, depth);
            long timeTaken = System.currentTimeMillis() - startTime;
            System.out.printf("Depth: %d | Perft: %d | Time: %d ms\n", depth, perft, timeTaken);
            tt.printFullness();
        }
    }

}
