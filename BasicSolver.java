import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Small educational searches. Unit-cost edges make all three optimal on success. */
public final class BasicSolver {
    private static final int FOUND = -1, LIMIT = -2;
    private record Parent(CubeState previous, Move move) {}
    private BasicSolver() {}

    public static SearchResult bfs(CubeState start, SearchResult.Limits limits) {
        SearchResult.Tracker stats = new SearchResult.Tracker(limits);
        ArrayDeque<CubeState> queue = new ArrayDeque<>();
        Map<CubeState, Parent> parents = new HashMap<>();
        parents.put(start, new Parent(null, null)); queue.add(start);
        if (start.isSolved()) return stats.finish(start, List.of(), 0, 1);
        while (!queue.isEmpty()) {
            if (!stats.visit()) return stats.finish(start, null, 0, 1);
            CubeState state = queue.remove();
            // BFS uses all 18 moves: visited-state merging has no last-face context.
            for (Move move : Move.ALL) {
                CubeState next = state.apply(move);
                if (parents.containsKey(next)) { stats.nodesPruned++; continue; }
                if (stats.tooManyStates(parents.size())) return stats.finish(start, null, 0, 1);
                parents.put(next, new Parent(state, move));
                if (next.isSolved()) return stats.finish(start, path(parents, next), 0, 1);
                queue.add(next);
            }
        }
        throw new IllegalStateException("Reachable cube graph unexpectedly exhausted.");
    }

    public static SearchResult bidirectionalBfs(CubeState start, SearchResult.Limits limits) {
        SearchResult.Tracker stats = new SearchResult.Tracker(limits);
        if (start.isSolved()) return stats.finish(start, List.of(), 0, 0);
        if (limits.maxStates() > 0 && limits.maxStates() < 2) return stats.finish(start, null, 0, 0);
        Map<CubeState, Parent> fromStart = new HashMap<>(), fromGoal = new HashMap<>();
        fromStart.put(start, new Parent(null, null));
        CubeState goal = CubeState.solved(); fromGoal.put(goal, new Parent(null, null));
        List<CubeState> startLayer = List.of(start), goalLayer = List.of(goal);
        int layers = 0;
        while (!startLayer.isEmpty() && !goalLayer.isEmpty()) {
            boolean expandStart = startLayer.size() <= goalLayer.size();
            Map<CubeState, Parent> own = expandStart ? fromStart : fromGoal;
            Map<CubeState, Parent> other = expandStart ? fromGoal : fromStart;
            List<CubeState> layer = expandStart ? startLayer : goalLayer;
            List<CubeState> nextLayer = new ArrayList<>();
            CubeState meeting = null;
            // Expand a COMPLETE layer. All new nodes on this side have equal depth.
            for (CubeState state : layer) {
                if (!stats.visit()) return stats.finish(start, null, 0, layers);
                for (Move move : Move.ALL) {
                    CubeState next = state.apply(move);
                    if (own.containsKey(next)) { stats.nodesPruned++; continue; }
                    if (stats.tooManyStates(fromStart.size() + fromGoal.size()))
                        return stats.finish(start, null, 0, layers);
                    own.put(next, new Parent(state, move)); nextLayer.add(next);
                    if (other.containsKey(next) && meeting == null) meeting = next;
                }
            }
            layers++;
            if (meeting != null) {
                List<Move> solution = new ArrayList<>(path(fromStart, meeting));
                solution.addAll(Move.inverse(path(fromGoal, meeting)));
                return stats.finish(start, solution, 0, layers);
            }
            if (expandStart) startLayer = nextLayer; else goalLayer = nextLayer;
        }
        throw new IllegalStateException("Reachable cube graph unexpectedly exhausted.");
    }

    private static List<Move> path(Map<CubeState, Parent> parents, CubeState state) {
        List<Move> result = new ArrayList<>();
        Parent p;
        while ((p = parents.get(state)).previous != null) {
            result.add(p.move); state = p.previous;
        }
        Collections.reverse(result);
        return result;
    }

    public static SearchResult iddfs(CubeState start, SearchResult.Limits limits) {
        SearchResult.Tracker stats = new SearchResult.Tracker(limits);
        List<Move> path = new ArrayList<>();
        for (int depthLimit = 0; ; depthLimit++) {
            int result = depthLimited(start, depthLimit, -1, path, stats);
            if (result == FOUND) return stats.finish(start, path, 0, depthLimit + 1);
            if (result == LIMIT) return stats.finish(start, null, 0, depthLimit + 1);
        }
    }

    private static int depthLimited(CubeState state, int remaining, int previousFace,
            List<Move> path, SearchResult.Tracker stats) {
        if (!stats.visit()) return LIMIT;
        if (state.isSolved()) return FOUND;
        if (remaining == 0) { stats.nodesPruned++; return 0; }
        for (Move move : Move.ALL) {
            if (!Move.allowedAfter(previousFace, move)) { stats.nodesPruned++; continue; }
            path.add(move);
            int result = depthLimited(state.apply(move), remaining - 1, move.face, path, stats);
            if (result == FOUND || result == LIMIT) return result;
            path.remove(path.size() - 1);
        }
        return 0;
    }
}
