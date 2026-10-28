import java.util.List;

/** A success contains a verified optimal path. A limit contains no solution claim. */
public record SearchResult(List<Move> solution, boolean limitReached, long nodesVisited,
        long nodesPruned, long elapsedMilliseconds, int initialHeuristic, int iterations) {
    public SearchResult {
        if ((solution == null) != limitReached)
            throw new IllegalArgumentException("Supply a solution or a limit status, exclusively.");
        if (solution != null) solution = List.copyOf(solution);
    }
    public boolean solved() { return solution != null; }
    public int solutionLength() { return solved() ? solution.size() : -1; }

    /** Zero means unlimited. maxStates is used only by BFS / bidirectional BFS. */
    public record Limits(long timeoutMilliseconds, long maxNodes, int maxStates) {
        public Limits {
            if (timeoutMilliseconds < 0 || maxNodes < 0 || maxStates < 0)
                throw new IllegalArgumentException("Limits cannot be negative.");
        }
        public static Limits defaults() { return new Limits(60_000, 10_000_000, 100_000); }
    }

    /** Just the counters and resource checks shared by the four algorithms. */
    static final class Tracker {
        final Limits limits;
        final long started = System.nanoTime();
        long nodesVisited, nodesPruned;
        Tracker(Limits limits) { this.limits = limits; }
        boolean visit() {
            if ((limits.maxNodes > 0 && nodesVisited >= limits.maxNodes)
                    || (limits.timeoutMilliseconds > 0
                    && (System.nanoTime() - started) / 1_000_000 >= limits.timeoutMilliseconds)) return false;
            nodesVisited++;
            return true;
        }
        boolean tooManyStates(int size) { return limits.maxStates > 0 && size >= limits.maxStates; }
        SearchResult finish(CubeState original, List<Move> solution, int initial, int iterations) {
            long elapsed = (System.nanoTime() - started) / 1_000_000;
            // Always verify with the full cubie model; never rely on a Java assert flag.
            if (solution != null && !original.apply(solution).isSolved())
                throw new IllegalStateException("BUG: returned solution failed replay verification.");
            return new SearchResult(solution, solution == null, nodesVisited, nodesPruned,
                    elapsed, initial, iterations);
        }
    }
}
