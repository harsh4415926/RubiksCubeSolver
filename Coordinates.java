/** Dense integer coordinates. The code favors explicit arithmetic over bit tricks. */
public final class Coordinates {
    public static final int CORNER_PERMUTATIONS = 40320; // 8!
    public static final int CORNER_ORIENTATIONS = 2187;  // 3^7
    public static final int CORNER_STATES = CORNER_PERMUTATIONS * CORNER_ORIENTATIONS;
    public static final int EDGE_PIECES = 5;
    public static final int EDGE_ORIENTATIONS = 32;      // 2^5
    public static final int EDGE_POSITIONS = 12 * 11 * 10 * 9 * 8;
    public static final int EDGE_STATES = EDGE_POSITIONS * EDGE_ORIENTATIONS;
    public record Corners(int[] permutation, int[] orientation) {}
    public record Edges(int[] positions, int[] orientation) {}
    private Coordinates() {}

    /** Lehmer rank: at each position, count smaller pieces to its right. */
    public static int rankPermutation(int[] permutation) {
        int rank = 0;
        for (int i = 0; i < permutation.length; i++) {
            int smaller = 0;
            for (int j = i + 1; j < permutation.length; j++)
                if (permutation[j] < permutation[i]) smaller++;
            rank = rank * (permutation.length - i) + smaller;
        }
        return rank;
    }

    public static int[] unrankPermutation(int rank, int size) {
        int[] digits = new int[size], permutation = new int[size];
        for (int i = size - 1; i >= 0; i--) {
            digits[i] = rank % (size - i);
            rank /= size - i;
        }
        boolean[] used = new boolean[size];
        for (int i = 0; i < size; i++) {
            permutation[i] = takeUnused(used, digits[i]);
        }
        return permutation;
    }

    /** Pick digit-th unused label in increasing order, then mark it used. */
    private static int takeUnused(boolean[] used, int digit) {
        for (int value = 0; value < used.length; value++) {
            if (used[value]) continue;
            if (digit == 0) { used[value] = true; return value; }
            digit--;
        }
        throw new IllegalArgumentException("Coordinate rank is out of range.");
    }

    public static int cornerOrientation(int[] orientation) {
        int rank = 0;
        for (int i = 0; i < 7; i++) rank = rank * 3 + orientation[i];
        return rank;
    }

    public static int[] decodeCornerOrientation(int rank) {
        int[] orientation = new int[8];
        int sum = 0;
        for (int i = 6; i >= 0; i--) {
            orientation[i] = rank % 3; rank /= 3; sum += orientation[i];
        }
        orientation[7] = (3 - sum % 3) % 3;
        return orientation;
    }

    public static int encodeCorners(int[] permutation, int[] orientation) {
        return rankPermutation(permutation) * CORNER_ORIENTATIONS
                + cornerOrientation(orientation);
    }

    public static int encodeCorners(CubeState state) {
        int permutation = 0, orientation = 0;
        for (int i = 0; i < 8; i++) {
            int smaller = 0;
            for (int j = i + 1; j < 8; j++) if (state.cornerAt(j) < state.cornerAt(i)) smaller++;
            permutation = permutation * (8 - i) + smaller;
            if (i < 7) orientation = orientation * 3 + state.cornerTwist(i);
        }
        return permutation * CORNER_ORIENTATIONS + orientation;
    }

    public static Corners decodeCorners(int coordinate) {
        return new Corners(unrankPermutation(coordinate / CORNER_ORIENTATIONS, 8),
                decodeCornerOrientation(coordinate % CORNER_ORIENTATIONS));
    }

    /** Ordered positions of five FIXED piece identities: 12P5 possibilities.
     * This encodes both subset location and the selected pieces' permutation. */
    public static int rankEdgePositions(int[] positions) {
        int rank = 0;
        for (int i = 0; i < EDGE_PIECES; i++) {
            int smallerUsed = 0;
            for (int j = 0; j < i; j++) if (positions[j] < positions[i]) smallerUsed++;
            rank = rank * (12 - i) + positions[i] - smallerUsed;
        }
        return rank;
    }

    public static int[] decodeEdgePositions(int rank) {
        int[] digits = new int[EDGE_PIECES], positions = new int[EDGE_PIECES];
        for (int i = EDGE_PIECES - 1; i >= 0; i--) {
            digits[i] = rank % (12 - i); rank /= 12 - i;
        }
        boolean[] used = new boolean[12];
        for (int i = 0; i < EDGE_PIECES; i++) {
            positions[i] = takeUnused(used, digits[i]);
        }
        return positions;
    }

    public static int encodeEdges(int[] positions, int[] orientation) {
        int flips = 0;
        for (int i = 0; i < EDGE_PIECES; i++) flips += orientation[i] * (1 << i);
        return rankEdgePositions(positions) * EDGE_ORIENTATIONS + flips;
    }

    public static int encodeEdges(CubeState state, int[] selectedPieces) {
        int[] positions = new int[EDGE_PIECES], orientation = new int[EDGE_PIECES];
        for (int p = 0; p < 12; p++) {
            for (int i = 0; i < EDGE_PIECES; i++) if (state.edgeAt(p) == selectedPieces[i]) {
                positions[i] = p; orientation[i] = state.edgeFlip(p);
            }
        }
        return encodeEdges(positions, orientation);
    }

    public static Edges decodeEdges(int coordinate) {
        int[] orientation = new int[EDGE_PIECES];
        int flips = coordinate % EDGE_ORIENTATIONS;
        for (int i = 0; i < EDGE_PIECES; i++) orientation[i] = (flips >>> i) & 1;
        return new Edges(decodeEdgePositions(coordinate / EDGE_ORIENTATIONS), orientation);
    }
}
