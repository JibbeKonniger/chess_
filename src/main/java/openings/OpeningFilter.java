package openings;

import java.io.*;
import java.nio.file.*;

/**
 * Reads a.tsv - e.tsv from resources/openings/, filters out openings with
 * 8 or fewer full moves OR a Stockfish evaluation worse than ±200cp at the
 * final position (depth 15), and writes passing entries to filtered_openings.tsv.
 *
 * Columns expected: eco \t name \t pgn
 */
public class OpeningFilter {

    private static final int    MIN_FULL_MOVES   = 6;
    private static final int    MAX_CP           = 100;
    private static final int    STOCKFISH_DEPTH  = 10;
    private static final String STOCKFISH_PATH   = "C:\\stockfish\\stockfish-windows-x86-64-avx2.exe";

    private static final String[] INPUT_FILES = { "a.tsv", "b.tsv", "c.tsv", "d.tsv", "e.tsv" };
    private static final String   OUTPUT_FILE = "filtered_openings.tsv";
    private static final String   HEADER      = "eco\tname\tpgn";

    // ------------------------------------------------------------------ //
    //  Stockfish process (single shared instance for speed)               //
    // ------------------------------------------------------------------ //
    private static Process        sfProcess;
    private static BufferedWriter sfWriter;
    private static BufferedReader sfReader;

    public static void main(String[] args) throws IOException {
        Path dir = Paths.get("src", "main", "resources", "openings");

        startStockfish();
        try {
            filter(dir, dir.resolve(OUTPUT_FILE));
        } finally {
            stopStockfish();
        }
    }

    public static void filter(Path inputDir, Path outputPath) throws IOException {
        int totalRead      = 0;
        int totalWritten   = 0;
        int filteredShort  = 0;
        int filteredEval   = 0;

        // Count total entries across all files for the progress bar
        int total = 0;
        for (String filename : INPUT_FILES) {
            Path file = inputDir.resolve(filename);
            if (!Files.exists(file)) continue;
            try (BufferedReader r = Files.newBufferedReader(file)) {
                r.readLine(); // skip header
                while (r.readLine() != null) total++;
            }
        }
        startProgress(total);

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
            writer.write(HEADER);
            writer.newLine();

            for (String filename : INPUT_FILES) {
                Path file = inputDir.resolve(filename);

                int fileRead    = 0;
                int fileWritten = 0;

                try (BufferedReader reader = Files.newBufferedReader(file)) {
                    reader.readLine(); // skip header

                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.isBlank()) continue;
                        fileRead++;

                        String[] cols = line.split("\t");
                        if (cols.length < 3) continue;

                        String pgn       = cols[2].trim();
                        int    fullMoves = countFullMoves(pgn);

                        String label = cols[1].trim();
                        tickProgress(label);

                        // --- Filter 1: too short ---
                        if (fullMoves < MIN_FULL_MOVES) {
                            filteredShort++;
                            continue;
                        }

                        // --- Filter 2: Stockfish evaluation ---
                        String[] uciMoves = pgnToUci(pgn);
                        if (uciMoves == null) {
                            System.err.println("[OpeningFilter] Failed to parse PGN: " + pgn);
                            continue;
                        }

                        int cp = evaluateFinalPosition(uciMoves);
                        if (Math.abs(cp) > MAX_CP) {
                            filteredEval++;
                            continue;
                        }

                        writer.write(line);
                        writer.newLine();
                        fileWritten++;
                    }
                }

                System.out.printf("[OpeningFilter] %s: %d read, %d kept%n",
                        filename, fileRead, fileWritten);
                totalRead    += fileRead;
                totalWritten += fileWritten;
            }
        }
        finishProgress();

        System.out.printf("%n[OpeningFilter] Done.%n");
        System.out.printf("  Total read:           %d%n", totalRead);
        System.out.printf("  Filtered (too short): %d%n", filteredShort);
        System.out.printf("  Filtered (eval >±%dcp): %d%n", MAX_CP, filteredEval);
        System.out.printf("  Written:              %d%n", totalWritten);
        System.out.printf("  Output: %s%n", outputPath.toAbsolutePath());
    }

    private static void startStockfish() throws IOException {
        ProcessBuilder pb = new ProcessBuilder(STOCKFISH_PATH);
        pb.redirectErrorStream(true);
        sfProcess = pb.start();
        sfWriter  = new BufferedWriter(new OutputStreamWriter(sfProcess.getOutputStream()));
        sfReader  = new BufferedReader(new InputStreamReader(sfProcess.getInputStream()));

        sendSF("uci");
        waitForSF("uciok");
        sendSF("isready");
        waitForSF("readyok");
        System.out.println("[OpeningFilter] Stockfish ready.");
    }

    private static void stopStockfish() {
        try {
            sendSF("quit");
            sfProcess.waitFor();
        } catch (Exception e) {
            sfProcess.destroyForcibly();
        }
    }

    /**
     * Evaluates the position after playing all the given UCI moves from the
     * starting position. Returns the evaluation in centipawns from White's
     * perspective. Returns 0 on failure.
     */
    private static int evaluateFinalPosition(String[] uciMoves) {
        try {
            String movesStr = String.join(" ", uciMoves);
            sendSF("ucinewgame");
            sendSF("isready");
            waitForSF("readyok");
            sendSF("position startpos moves " + movesStr);
            sendSF("go depth " + STOCKFISH_DEPTH);

            int    cp           = 0;
            String line;

            while ((line = sfReader.readLine()) != null) {
                // Extract the most recent "score cp" from info lines
                if (line.startsWith("info") && line.contains("score cp")) {
                    cp = parseCp(line);
                }
                if (line.startsWith("bestmove")) {
                    break;
                }
            }

            return cp;
        } catch (IOException e) {
            System.err.println("[OpeningFilter] Stockfish error: " + e.getMessage());
            return 0;
        }
    }

    private static int parseCp(String infoLine) {
        // e.g. "info depth 15 ... score cp -34 ..."
        String[] tokens = infoLine.split(" ");
        for (int i = 0; i < tokens.length - 1; i++) {
            if (tokens[i].equals("cp")) {
                try { return Integer.parseInt(tokens[i + 1]); }
                catch (NumberFormatException ignored) {}
            }
        }
        return 0;
    }

    private static void sendSF(String cmd) throws IOException {
        sfWriter.write(cmd + "\n");
        sfWriter.flush();
    }

    private static void waitForSF(String expected) throws IOException {
        String line;
        while ((line = sfReader.readLine()) != null) {
            if (line.contains(expected)) return;
        }
    }

    /**
     * Converts a PGN move string like "1. e4 e5 2. Nf3 Nc6" into UCI moves
     * by asking Stockfish to parse each move in sequence.
     * Returns null if any move fails to parse.
     */
    private static String[] pgnToUci(String pgn) {
        // Strip move numbers: "1. e4 e5 2. Nf3" → ["e4", "e5", "Nf3"]
        String[] tokens = pgn.split(" ");
        java.util.List<String> sanMoves = new java.util.ArrayList<>();
        for (String t : tokens) {
            if (!t.matches("\\d+\\..*") && !t.isBlank()) sanMoves.add(t.trim());
        }

        // Use Stockfish to convert SAN → UCI one move at a time
        java.util.List<String> uciMoves = new java.util.ArrayList<>();
        try {
            for (int i = 0; i < sanMoves.size(); i++) {
                String movesSoFar = String.join(" ", uciMoves);
                String pos = uciMoves.isEmpty()
                        ? "position startpos"
                        : "position startpos moves " + movesSoFar;

                sendSF(pos);
                sendSF("go depth 1 searchmoves " + sanMoves.get(i));

                // Read until bestmove
                String line;
                String uciMove = null;
                while ((line = sfReader.readLine()) != null) {
                    if (line.startsWith("bestmove")) {
                        String[] parts = line.split(" ");
                        if (parts.length >= 2 && !parts[1].equals("(none)")) {
                            uciMove = parts[1];
                        }
                        break;
                    }
                }

                if (uciMove == null) return null;
                uciMoves.add(uciMove);
            }
        } catch (IOException e) {
            return null;
        }

        return uciMoves.toArray(new String[0]);
    }

    private static int countFullMoves(String pgn) {
        int count = 0;
        for (String token : pgn.split(" ")) {
            if (token.matches("\\d+\\.")) count++;
        }
        return count;
    }


    // ------------------------------------------------------------------ //
//  Progress bar                                                        //
// ------------------------------------------------------------------ //
    private static int     progressTotal   = 0;
    private static int     progressCurrent = 0;
    private static String  progressLabel   = "";

    private static void startProgress(int total) {
        progressTotal   = total;
        progressCurrent = 0;
    }

    private static void tickProgress(String label) {
        progressCurrent++;
        progressLabel = label;

        int    barWidth = 40;
        double pct      = (double) progressCurrent / progressTotal;
        int    filled   = (int)(pct * barWidth);

        String bar = "█".repeat(filled) + "░".repeat(barWidth - filled);
        // \r moves cursor back to start of line — overwrites previous output
        System.out.printf("\r[%s] %d/%d (%.1f%%) — %s",
                bar, progressCurrent, progressTotal, pct * 100, progressLabel);
        System.out.flush();
    }

    private static void finishProgress() {
        System.out.println(); // newline after the bar is done
    }
}