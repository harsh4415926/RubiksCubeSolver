import java.util.ArrayList;
import java.util.List;

/** Full-cube IDA*: no two-phase restriction, no transposition pruning. */
public final class OptimalSolver {
    private static final int FOUND = -1, LIMIT = -2;
    private final PatternDatabases pdb;
    private SearchResult.Tracker stats;
    private List<Move> path;

    public OptimalSolver(PatternDatabases pdb) { this.pdb = pdb; }

    // A solver instance is intentionally single-threaded. Reuse sequentially.
    public SearchResult solve(CubeState start, SearchResult.Limits limits) {
        stats = new SearchResult.Tracker(limits);
        path = new ArrayList<>();
        int corner = pdb.cornerCoordinate(start);
        int edgeA = pdb.edgeACoordinate(start), edgeB = pdb.edgeBCoordinate(start);
        int initialHeuristic = pdb.heuristic(corner, edgeA, edgeB);
        int bound = initialHeuristic;

        for (int iteration = 1; ; iteration++) {
            int result = search(start, 0, bound, -1, corner, edgeA, edgeB);
            if (result == FOUND) return stats.finish(start, path, initialHeuristic, iteration);
            if (result == LIMIT) return stats.finish(start, null, initialHeuristic, iteration);
            if (result == Integer.MAX_VALUE) throw new IllegalStateException("No next IDA* bound.");
            bound = result; // Smallest f-cost that exceeded the previous threshold.
        }
    }

    private int search(CubeState state, int g, int bound, int previousFace,
            int corner, int edgeA, int edgeB) {
        if (!stats.visit()) return LIMIT;
        int h = pdb.heuristic(corner, edgeA, edgeB);
        int f = g + h;
        if (f > bound) { stats.nodesPruned++; return f; }
        // h=0 alone is NOT a goal: two edges are absent from the patterns.
        if (state.isSolved()) return FOUND;

        int nextBound = Integer.MAX_VALUE;
        for (Move move : Move.ALL) {
            if (!Move.allowedAfter(previousFace, move)) { stats.nodesPruned++; continue; }
            path.add(move);
            int m = move.ordinal();
            int result = search(state.apply(move), g + 1, bound, move.face,
                    pdb.nextCorner(corner, m), pdb.nextEdge(edgeA, m), pdb.nextEdge(edgeB, m));
            if (result == FOUND || result == LIMIT) return result;
            path.remove(path.size() - 1); // Backtrack; CubeState itself is immutable.
            nextBound = Math.min(nextBound, result);
        }
        return nextBound;
    }
}
