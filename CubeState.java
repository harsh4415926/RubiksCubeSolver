import java.util.Arrays;
import java.util.List;

/** Immutable cubie state. Arrays map POSITION -> PIECE, with twist/flip at that position. */
public final class CubeState {
    // Corners: URF UFL ULB UBR DFR DLF DBL DRB.
    // Edges:   UR UF UL UB DR DF DL DB FR FL BL BR.
    // Orientation convention: standard cubie convention, documented in README.
    private final int[] cornerPermutation, cornerOrientation;
    private final int[] edgePermutation, edgeOrientation;
    private final int hash;

    // Each quarter-turn table says which OLD position supplies each NEW position.
    // Face order: U D L R F B. Viewed straight at a face, these are clockwise.
    private static final int[][] CORNER_SOURCE = {
        {3,0,1,2,4,5,6,7}, {0,1,2,3,5,6,7,4},
        {0,2,6,3,4,1,5,7}, {4,1,2,0,7,5,6,3},
        {1,5,2,3,0,4,6,7}, {0,1,3,7,4,5,2,6}
    };
    private static final int[][] CORNER_TWIST = {
        {0,0,0,0,0,0,0,0}, {0,0,0,0,0,0,0,0},
        {0,1,2,0,0,2,1,0}, {2,0,0,1,1,0,0,2},
        {1,2,0,0,2,1,0,0}, {0,0,1,2,0,0,2,1}
    };
    private static final int[][] EDGE_SOURCE = {
        {3,0,1,2,4,5,6,7,8,9,10,11}, {0,1,2,3,5,6,7,4,8,9,10,11},
        {0,1,10,3,4,5,9,7,8,2,6,11}, {8,1,2,3,11,5,6,7,4,9,10,0},
        {0,9,2,3,4,8,6,7,1,5,10,11}, {0,1,2,11,4,5,6,10,8,9,3,7}
    };
    private static final int[][] EDGE_FLIP = {
        {0,0,0,0,0,0,0,0,0,0,0,0}, {0,0,0,0,0,0,0,0,0,0,0,0},
        {0,0,0,0,0,0,0,0,0,0,0,0}, {0,0,0,0,0,0,0,0,0,0,0,0},
        {0,1,0,0,0,1,0,0,1,1,0,0}, {0,0,0,1,0,0,0,1,0,0,1,1}
    };

    // Composed once for all 18 moves; application then uses one transformation.
    private static final int[][] CP = new int[18][8], CO = new int[18][8];
    private static final int[][] EP = new int[18][12], EO = new int[18][12];
    private static final int[][] EDGE_DESTINATION = new int[18][12];
    static {
        for (Move move : Move.ALL) {
            int m = move.ordinal(), f = move.face;
            for (int i = 0; i < 8; i++) CP[m][i] = i;
            for (int i = 0; i < 12; i++) EP[m][i] = i;
            for (int turn = 0; turn < move.quarterTurns; turn++) {
                int[] cp = CP[m].clone(), co = CO[m].clone();
                int[] ep = EP[m].clone(), eo = EO[m].clone();
                for (int i = 0; i < 8; i++) {
                    int source = CORNER_SOURCE[f][i];
                    CP[m][i] = cp[source];
                    CO[m][i] = (co[source] + CORNER_TWIST[f][i]) % 3;
                }
                for (int i = 0; i < 12; i++) {
                    int source = EDGE_SOURCE[f][i];
                    EP[m][i] = ep[source];
                    EO[m][i] = eo[source] ^ EDGE_FLIP[f][i];
                }
            }
            for (int destination = 0; destination < 12; destination++)
                EDGE_DESTINATION[m][EP[m][destination]] = destination;
        }
    }

    private CubeState(int[] cp, int[] co, int[] ep, int[] eo) {
        cornerPermutation = cp; cornerOrientation = co;
        edgePermutation = ep; edgeOrientation = eo;
        hash = 31 * (31 * (31 * Arrays.hashCode(cp) + Arrays.hashCode(co))
                + Arrays.hashCode(ep)) + Arrays.hashCode(eo);
    }

    public static CubeState solved() {
        int[] cp = new int[8], ep = new int[12];
        for (int i = 0; i < 8; i++) cp[i] = i;
        for (int i = 0; i < 12; i++) ep[i] = i;
        return new CubeState(cp, new int[8], ep, new int[12]);
    }

    /** Public entry point for arbitrary cubie input: checks physical reachability. */
    public static CubeState of(int[] cp, int[] co, int[] ep, int[] eo) {
        if (cp.length != 8 || co.length != 8 || ep.length != 12 || eo.length != 12)
            throw new IllegalArgumentException("Expected 8 corners and 12 edges.");
        validatePermutation(cp); validatePermutation(ep);
        int twists = 0, flips = 0;
        for (int x : co) {
            if (x < 0 || x > 2) throw new IllegalArgumentException("Invalid corner orientation.");
            twists += x;
        }
        for (int x : eo) {
            if (x < 0 || x > 1) throw new IllegalArgumentException("Invalid edge orientation.");
            flips += x;
        }
        if (twists % 3 != 0 || flips % 2 != 0 || parity(cp) != parity(ep))
            throw new IllegalArgumentException("Cubie configuration is not physically reachable.");
        return new CubeState(cp.clone(), co.clone(), ep.clone(), eo.clone());
    }

    private static void validatePermutation(int[] p) {
        boolean[] seen = new boolean[p.length];
        for (int x : p) {
            if (x < 0 || x >= p.length || seen[x])
                throw new IllegalArgumentException("Invalid cubie permutation.");
            seen[x] = true;
        }
    }

    private static int parity(int[] p) {
        int inversions = 0;
        for (int i = 0; i < p.length; i++)
            for (int j = i + 1; j < p.length; j++) if (p[i] > p[j]) inversions++;
        return inversions % 2;
    }

    public CubeState apply(Move move) {
        int m = move.ordinal();
        int[] cp = new int[8], co = new int[8], ep = new int[12], eo = new int[12];
        for (int i = 0; i < 8; i++) {
            cp[i] = cornerPermutation[CP[m][i]];
            co[i] = (cornerOrientation[CP[m][i]] + CO[m][i]) % 3;
        }
        for (int i = 0; i < 12; i++) {
            ep[i] = edgePermutation[EP[m][i]];
            eo[i] = edgeOrientation[EP[m][i]] ^ EO[m][i];
        }
        return new CubeState(cp, co, ep, eo);
    }

    public CubeState apply(List<Move> moves) {
        CubeState state = this;
        for (Move move : moves) state = state.apply(move);
        return state;
    }

    public boolean isSolved() {
        for (int i = 0; i < 8; i++)
            if (cornerPermutation[i] != i || cornerOrientation[i] != 0) return false;
        for (int i = 0; i < 12; i++)
            if (edgePermutation[i] != i || edgeOrientation[i] != 0) return false;
        return true;
    }

    // Scalar readers keep the internal arrays private and immutable.
    public int cornerAt(int position) { return cornerPermutation[position]; }
    public int cornerTwist(int position) { return cornerOrientation[position]; }
    public int edgeAt(int position) { return edgePermutation[position]; }
    public int edgeFlip(int position) { return edgeOrientation[position]; }
    static int cornerSource(int m, int p) { return CP[m][p]; }
    static int cornerTwistDelta(int m, int p) { return CO[m][p]; }
    static int edgeDestination(int m, int p) { return EDGE_DESTINATION[m][p]; }
    static int edgeFlipDelta(int m, int newPosition) { return EO[m][newPosition]; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CubeState c)) return false;
        return Arrays.equals(cornerPermutation, c.cornerPermutation)
                && Arrays.equals(cornerOrientation, c.cornerOrientation)
                && Arrays.equals(edgePermutation, c.edgePermutation)
                && Arrays.equals(edgeOrientation, c.edgeOrientation);
    }
    @Override public int hashCode() { return hash; }
}
