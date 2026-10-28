import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Accepts standard face turns separated by whitespace; no rotations or slice moves. */
public final class CubeParser {
    private CubeParser() {}

    public static List<Move> parseMoves(String text) {
        if (text == null) throw new IllegalArgumentException("Scramble is null.");
        if (text.isBlank()) return List.of();
        List<Move> moves = new ArrayList<>();
        for (String token : text.trim().split("\\s+")) {
            Move found = null;
            for (Move move : Move.ALL) if (move.notation.equals(token)) found = move;
            if (found == null) throw new IllegalArgumentException("Invalid move: " + token
                    + ". Use U D L R F B, optionally followed by ' or 2.");
            moves.add(found);
        }
        return List.copyOf(moves);
    }

    public static CubeState fromScramble(String scramble) {
        return CubeState.solved().apply(parseMoves(scramble));
    }

    public static String format(List<Move> moves) {
        return moves.stream().map(Move::toString).collect(Collectors.joining(" "));
    }
}
