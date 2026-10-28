import java.io.IOException;
import java.nio.file.Path;

/** Three exact abstractions, combined with MAX (never their unpartitioned sum). */
public final class PatternDatabases {
    // Identity order is fixed: positions/orientations are recorded in this order.
    private static final int[] EDGES_A = {0, 1, 2, 3, 4};
    private static final int[] EDGES_B = {5, 6, 7, 8, 9};
    // Edges 10 and 11 are ignored by the heuristic, but NOT by the full goal test.
    private final PatternDatabase corners, edgesA, edgesB;

    // Small FACTORIZED move tables, not 88 million x 18 entries.
    private final int[] cornerPermutationMoves = new int[Coordinates.CORNER_PERMUTATIONS * 18];
    private final short[] cornerOrientationMoves = new short[Coordinates.CORNER_ORIENTATIONS * 18];
    private final int[] edgePositionMoves = new int[Coordinates.EDGE_POSITIONS * 18];
    private final byte[] edgeFlipMasks = new byte[Coordinates.EDGE_POSITIONS * 18];

    public PatternDatabases(Path cache) throws IOException {
        long started = System.nanoTime();
        buildMoveTables();
        // Change the identity/format if move conventions or coordinates change.
        corners = PatternDatabase.loadOrGenerate(cache.resolve("corners.pdb"),
                "cubietrace-ftm-v1-corners", Coordinates.CORNER_STATES, 0, this::nextCorner);
        edgesA = PatternDatabase.loadOrGenerate(cache.resolve("edgesA.pdb"),
                "cubietrace-ftm-v1-edges-0-1-2-3-4", Coordinates.EDGE_STATES,
                Coordinates.encodeEdges(CubeState.solved(), EDGES_A), this::nextEdge);
        edgesB = PatternDatabase.loadOrGenerate(cache.resolve("edgesB.pdb"),
                "cubietrace-ftm-v1-edges-5-6-7-8-9", Coordinates.EDGE_STATES,
                Coordinates.encodeEdges(CubeState.solved(), EDGES_B), this::nextEdge);
        System.out.printf("PDB initialization: %.3f s%n", (System.nanoTime() - started) / 1_000_000_000.0);
    }

    private void buildMoveTables() {
        for (int rank = 0; rank < Coordinates.CORNER_PERMUTATIONS; rank++) {
            int[] old = Coordinates.unrankPermutation(rank, 8), moved = new int[8];
            for (int m = 0; m < 18; m++) {
                for (int p = 0; p < 8; p++) moved[p] = old[CubeState.cornerSource(m, p)];
                cornerPermutationMoves[rank * 18 + m] = Coordinates.rankPermutation(moved);
            }
        }
        for (int rank = 0; rank < Coordinates.CORNER_ORIENTATIONS; rank++) {
            int[] old = Coordinates.decodeCornerOrientation(rank), moved = new int[8];
            for (int m = 0; m < 18; m++) {
                for (int p = 0; p < 8; p++) moved[p] = (old[CubeState.cornerSource(m, p)]
                        + CubeState.cornerTwistDelta(m, p)) % 3;
                cornerOrientationMoves[rank * 18 + m] = (short) Coordinates.cornerOrientation(moved);
            }
        }
        for (int rank = 0; rank < Coordinates.EDGE_POSITIONS; rank++) {
            int[] old = Coordinates.decodeEdgePositions(rank), moved = new int[Coordinates.EDGE_PIECES];
            for (int m = 0; m < 18; m++) {
                int flipMask = 0;
                for (int i = 0; i < Coordinates.EDGE_PIECES; i++) {
                    moved[i] = CubeState.edgeDestination(m, old[i]);
                    flipMask += CubeState.edgeFlipDelta(m, moved[i]) * (1 << i);
                }
                edgePositionMoves[rank * 18 + m] = Coordinates.rankEdgePositions(moved);
                edgeFlipMasks[rank * 18 + m] = (byte) flipMask;
            }
        }
    }

    public int nextCorner(int coordinate, int move) {
        int permutation = coordinate / Coordinates.CORNER_ORIENTATIONS;
        int orientation = coordinate % Coordinates.CORNER_ORIENTATIONS;
        return cornerPermutationMoves[permutation * 18 + move] * Coordinates.CORNER_ORIENTATIONS
                + cornerOrientationMoves[orientation * 18 + move];
    }

    public int nextEdge(int coordinate, int move) {
        int positions = coordinate / Coordinates.EDGE_ORIENTATIONS;
        int flips = coordinate % Coordinates.EDGE_ORIENTATIONS;
        int tableIndex = positions * 18 + move;
        return edgePositionMoves[tableIndex] * Coordinates.EDGE_ORIENTATIONS
                + (flips ^ edgeFlipMasks[tableIndex]);
    }

    public int cornerCoordinate(CubeState state) { return Coordinates.encodeCorners(state); }
    public int edgeACoordinate(CubeState state) { return Coordinates.encodeEdges(state, EDGES_A); }
    public int edgeBCoordinate(CubeState state) { return Coordinates.encodeEdges(state, EDGES_B); }

    public int heuristic(CubeState state) {
        return heuristic(cornerCoordinate(state), edgeACoordinate(state), edgeBCoordinate(state));
    }

    public int heuristic(int corner, int edgeA, int edgeB) {
        return Math.max(corners.distance(corner), Math.max(edgesA.distance(edgeA), edgesB.distance(edgeB)));
    }

    public int cornerDistance(int coordinate) { return corners.distance(coordinate); }
    public int edgeADistance(int coordinate) { return edgesA.distance(coordinate); }
    public int edgeBDistance(int coordinate) { return edgesB.distance(coordinate); }
}
