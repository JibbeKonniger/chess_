package openings;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.PieceType;
import com.github.bhlangonijr.chesslib.move.Move;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

/**
 * Position-based Opening Book.
 *
 * Loads openings from resources/openings/filtered_openings.tsv
 * and builds a mapping:
 *
 *     FEN position -> List of possible next book moves
 *
 * At runtime, nextMove(board) returns a random book move
 * matching the current position, or null if no move exists.
 *
 * TSV format:
 * eco \t name \t pgn
 *
 * PGN example:
 * "1. e4 e5 2. Nf3 Nc6 3. Bb5 ..."
 */
public class OpeningBook {

    private static final String TSV_PATH = "/openings/filtered_openings.tsv";

    /** Map: normalized FEN -> list of possible next moves */
    private final Map<String, List<Move>> book = new HashMap<>();
    private final Random random = new Random();

    public OpeningBook() {
        List<Opening> openings = load();
        if (openings.isEmpty()) {
            System.err.println("[OpeningBook] No openings loaded — book disabled.");
            return;
        }

        for (Opening opening : openings) {
            Board scratch = new Board();

            for (String san : opening.sanMoves) {

                String key = normalizeFen(scratch.getFen());

                Move move = findMoveBySan(scratch, san);
                if (move == null) {
                    break; // skip broken line
                }

                book.computeIfAbsent(key, k -> new ArrayList<>())
                        .add(move);

                scratch.doMove(move);
            }
        }

        System.out.printf("[OpeningBook] Loaded %d positions into book.%n", book.size());
    }

    // ------------------------------------------------------------------ //
    // Public API                                                         //
    // ------------------------------------------------------------------ //

    /**
     * Returns a random book move for the current position,
     * or null if no book move exists.
     */
    public Move nextMove(Board board) {
        String key = normalizeFen(board.getFen());
        List<Move> moves = book.get(key);

        if (moves == null || moves.isEmpty()) {
            return null;
        }

        return moves.get(random.nextInt(moves.size()));
    }

    // ------------------------------------------------------------------ //
    // FEN normalization (ignore move counters)                           //
    // ------------------------------------------------------------------ //

    /**
     * Keeps only:
     *   - piece placement
     *   - side to move
     *   - castling rights
     *   - en passant square
     *
     * Removes halfmove and fullmove counters.
     */
    private static String normalizeFen(String fen) {
        String[] parts = fen.split(" ");
        if (parts.length < 4) return fen;
        return parts[0] + " " + parts[1] + " " + parts[2] + " " + parts[3];
    }

    // ------------------------------------------------------------------ //
    // TSV Loading                                                        //
    // ------------------------------------------------------------------ //

    private List<Opening> load() {
        List<Opening> result = new ArrayList<>();

        InputStream stream = getClass().getResourceAsStream(TSV_PATH);
        if (stream == null) {
            System.err.println("[OpeningBook] File not found: " + TSV_PATH);
            return result;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            reader.readLine(); // skip header

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;

                String[] cols = line.split("\t");
                if (cols.length < 3) continue;

                String name = cols[1].trim();
                String pgn = cols[2].trim();

                String[] sanMoves = parsePgn(pgn);
                if (sanMoves.length > 0) {
                    result.add(new Opening(name, sanMoves));
                }
            }
        } catch (Exception e) {
            System.err.println("[OpeningBook] Error reading TSV: " + e.getMessage());
        }

        System.out.printf("[OpeningBook] Loaded %d openings from %s%n",
                result.size(), TSV_PATH);

        return result;
    }

    // ------------------------------------------------------------------ //
    // PGN Parsing                                                        //
    // ------------------------------------------------------------------ //

    /**
     * Converts:
     * "1. e4 e5 2. Nf3 Nc6"
     *
     * Into:
     * ["e4", "e5", "Nf3", "Nc6"]
     */
    private static String[] parsePgn(String pgn) {
        List<String> sanMoves = new ArrayList<>();

        for (String token : pgn.split(" ")) {
            token = token.trim();
            if (token.isEmpty()) continue;
            if (token.matches("\\d+\\.+")) continue;
            sanMoves.add(token);
        }

        return sanMoves.toArray(new String[0]);
    }

    // ------------------------------------------------------------------ //
    // SAN -> Move Resolution                                             //
    // ------------------------------------------------------------------ //

    private static Move findMoveBySan(Board board, String san) {

        for (Move move : board.legalMoves()) {
            if (moveToSan(board, move).equals(san)) {
                return move;
            }
        }
        return null;
    }

    private static String moveToSan(Board board, Move move) {
        Piece piece = board.getPiece(move.getFrom());
        if (piece == Piece.NONE) return "";

        PieceType type = piece.getPieceType();

        String toFile = String.valueOf((char) ('a' + move.getTo().getFile().ordinal()));
        String toRank = String.valueOf(move.getTo().getRank().ordinal() + 1);
        String to = toFile + toRank;

        // Castling
        if (type == PieceType.KING) {
            int fileDiff = move.getTo().getFile().ordinal()
                    - move.getFrom().getFile().ordinal();
            if (fileDiff == 2) return "O-O";
            if (fileDiff == -2) return "O-O-O";
        }

        boolean isCapture =
                board.getPiece(move.getTo()) != Piece.NONE
                        || board.getEnPassant() == move.getTo();

        // Pawn
        if (type == PieceType.PAWN) {
            String result = isCapture
                    ? move.getFrom().getFile().toString().toLowerCase() + "x" + to
                    : to;

            if (move.getPromotion() != null &&
                    move.getPromotion() != Piece.NONE) {
                result += "=" +
                        move.getPromotion().getPieceType().toString().charAt(0);
            }

            return result;
        }

        String pieceLetter =
                type == PieceType.KNIGHT ? "N"
                        : type.toString().substring(0, 1);

        String disambig = disambiguate(board, move, piece);

        return pieceLetter + disambig + (isCapture ? "x" : "") + to;
    }

    private static String disambiguate(Board board, Move move, Piece piece) {

        List<Move> ambiguous = new ArrayList<>();

        for (Move m : board.legalMoves()) {
            if (!m.equals(move)
                    && board.getPiece(m.getFrom()) == piece
                    && m.getTo() == move.getTo()) {
                ambiguous.add(m);
            }
        }

        if (ambiguous.isEmpty()) return "";

        String fromFile =
                move.getFrom().getFile().toString().toLowerCase();
        String fromRank =
                String.valueOf(move.getFrom().getRank().ordinal() + 1);

        boolean fileUnique = ambiguous.stream()
                .allMatch(m ->
                        m.getFrom().getFile() != move.getFrom().getFile());

        if (fileUnique) return fromFile;

        boolean rankUnique = ambiguous.stream()
                .allMatch(m ->
                        m.getFrom().getRank() != move.getFrom().getRank());

        if (rankUnique) return fromRank;

        return fromFile + fromRank;
    }

    // ------------------------------------------------------------------ //
    // Data Class                                                         //
    // ------------------------------------------------------------------ //

    private static class Opening {
        final String name;
        final String[] sanMoves;

        Opening(String name, String[] sanMoves) {
            this.name = name;
            this.sanMoves = sanMoves;
        }
    }
}