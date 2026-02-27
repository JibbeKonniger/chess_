package utils;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.move.Move;

public class MoveUtils {
    public static boolean isCapture(Board board, Move move) {
        // Standard capture: enemy piece on destination square
        Piece capturedPiece = board.getPiece(move.getTo());
        if (capturedPiece != null && capturedPiece != Piece.NONE) {
            return true;
        }

        // En passant capture: moving pawn to the en passant target square
        if (board.getEnPassant() != null && board.getEnPassant() == move.getTo()) {
            Piece movingPiece = board.getPiece(move.getFrom());
            if (movingPiece == Piece.WHITE_PAWN || movingPiece == Piece.BLACK_PAWN) {
                return true;
            }
        }

        return false;
    }
}
