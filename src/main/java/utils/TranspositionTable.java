package utils;

import com.github.bhlangonijr.chesslib.move.Move;

public class TranspositionTable {

    public enum Flag { EXACT, LOWER_BOUND, UPPER_BOUND }

    public static class Entry {
        public final long zobristKey;
        public final int depth;
        public final int score;
        public final Flag flag;
        public final Move bestMove;

        public Entry(long zobristKey, int depth, int score, Flag flag, Move bestMove) {
            this.zobristKey = zobristKey;
            this.depth      = depth;
            this.score      = score;
            this.flag       = flag;
            this.bestMove   = bestMove;
        }
    }

    private final Entry[] table;
    private final int capacity;

    // Default: ~64MB (we do 256mb) table (each entry ~50 bytes, so ~1.2M * 4 entries)
    public TranspositionTable() {
        this(1 << 22); // 4 * 1,048,576 entries
    }

    public TranspositionTable(int capacity) {
        this.capacity = capacity;
        this.table    = new Entry[capacity];
    }

    private int index(long zobristKey) {
        // Mask to positive int, then mod
        return (int)((zobristKey & 0x7FFFFFFFL) % capacity);
    }

    /** Store a position. Uses always-replace strategy. */
    public void put(long zobristKey, int depth, int score, Flag flag, Move bestMove) {
        int i = index(zobristKey);
        table[i] = new Entry(zobristKey, depth, score, flag, bestMove);
    }

    /**
     * Look up a position. Returns null if not found.
     * Always validate the returned entry's zobristKey against your board's
     * key to guard against index collisions.
     */
    public Entry get(long zobristKey) {
        Entry e = table[index(zobristKey)];
        if (e == null || e.zobristKey != zobristKey) return null;
        return e;
    }

    public void clear() {
        java.util.Arrays.fill(table, null);
    }

    public void printFullness() {
        long filled = 0;
        for (Entry e : table) {
            if (e != null) filled++;
        }

        double pct = (double) filled / capacity * 100;
        int barLength = 40;
        int filledBars = (int) (pct / 100 * barLength);

        String bar = "#".repeat(filledBars) + "-".repeat(barLength - filledBars);
        System.out.printf("[%s] %.1f%% (%d / %d)%n", bar, pct, filled, capacity);
    }
}