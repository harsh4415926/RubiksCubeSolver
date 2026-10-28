import java.util.ArrayList;
import java.util.List;

/** All 18 unit-cost moves in the face-turn metric (FTM). */
public enum Move {
    U("U", 0, 1), U_PRIME("U'", 0, 3), U2("U2", 0, 2),
    D("D", 1, 1), D_PRIME("D'", 1, 3), D2("D2", 1, 2),
    L("L", 2, 1), L_PRIME("L'", 2, 3), L2("L2", 2, 2),
    R("R", 3, 1), R_PRIME("R'", 3, 3), R2("R2", 3, 2),
    F("F", 4, 1), F_PRIME("F'", 4, 3), F2("F2", 4, 2),
    B("B", 5, 1), B_PRIME("B'", 5, 3), B2("B2", 5, 2);

    public final String notation;
    public final int face;
    public final int quarterTurns;
    public static final Move[] ALL = values(); // Treat as read-only inside the project.

    Move(String notation, int face, int quarterTurns) {
        this.notation = notation;
        this.face = face;
        this.quarterTurns = quarterTurns;
    }

    public Move inverse() {
        return ALL[face * 3 + (quarterTurns == 1 ? 1 : quarterTurns == 3 ? 0 : 2)];
    }

    public static List<Move> inverse(List<Move> moves) {
        List<Move> result = new ArrayList<>(moves.size());
        for (int i = moves.size() - 1; i >= 0; i--) result.add(moves.get(i).inverse());
        return List.copyOf(result);
    }

    /** Safe for DFS: keep one representative of redundant move sequences. */
    public static boolean allowedAfter(int previousFace, Move next) {
        if (previousFace < 0) return true;
        if (previousFace == next.face) return false;
        // Opposite faces commute. Keep U before D, L before R, F before B.
        // Faces 0/1, 2/3, 4/5 are the three opposite pairs.
        boolean opposite = previousFace / 2 == next.face / 2;
        return !opposite || previousFace < next.face;
    }

    @Override public String toString() { return notation; }
}
