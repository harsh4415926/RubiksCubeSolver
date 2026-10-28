import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.IntBinaryOperator;

/** Exact abstract distances, built by layer-by-layer reverse BFS. */
public final class PatternDatabase {
    private static final int MAGIC = 0x43545044; // CTPD
    private static final int FORMAT = 1;
    private final byte[] distances;
    private final int maxDistance;

    private PatternDatabase(byte[] distances, int maxDistance) {
        this.distances = distances;
        this.maxDistance = maxDistance;
    }

    public int distance(int coordinate) { return distances[coordinate]; }
    public int size() { return distances.length; }
    public int maxDistance() { return maxDistance; }

    public static PatternDatabase loadOrGenerate(Path file, String identity, int size,
            int goal, IntBinaryOperator next) throws IOException {
        if (Files.exists(file)) {
            try {
                PatternDatabase loaded = load(file, identity, size, goal);
                System.out.printf("Loaded %s: %,d entries, max distance %d%n",
                        file.getFileName(), size, loaded.maxDistance);
                return loaded;
            } catch (IOException e) {
                System.out.println("Rebuilding incompatible/incomplete cache " + file.getFileName()
                        + ": " + (e.getMessage() == null ? "Incomplete header/payload" : e.getMessage()));
            }
        }
        PatternDatabase generated = generate(identity, size, goal, next);
        Files.createDirectories(file.toAbsolutePath().getParent());
        generated.save(file, identity, goal);
        return generated;
    }

    /**
     * Instead of a huge queue, scan the byte array for the current BFS layer.
     * Entries discovered at depth+1 are NOT expanded until the next scan.
     * Memory is one byte per state; the tradeoff is repeated linear scans.
     * All moves have inverses, so BFS outward from goal equals reverse BFS.
     */
    static PatternDatabase generate(String name, int size, int goal, IntBinaryOperator next) {
        byte[] distances = new byte[size];
        Arrays.fill(distances, (byte) -1);
        distances[goal] = 0;
        int reached = 1, depth = 0;
        long started = System.nanoTime();
        System.out.printf("Generating %s: %,d bytes%n", name, size);
        while (reached < size) {
            if (depth >= 126) throw new IllegalStateException("Distance does not fit in a byte.");
            int before = reached;
            for (int coordinate = 0; coordinate < size; coordinate++) {
                if (distances[coordinate] != depth) continue;
                for (int move = 0; move < 18; move++) {
                    int neighbor = next.applyAsInt(coordinate, move);
                    if (distances[neighbor] == -1) {
                        distances[neighbor] = (byte) (depth + 1);
                        reached++;
                    }
                }
            }
            depth++;
            System.out.printf("  depth %d: %,d / %,d states (%.1f s)%n", depth, reached, size,
                    (System.nanoTime() - started) / 1_000_000_000.0);
            if (before == reached) throw new IllegalStateException("Abstract graph is disconnected.");
        }
        return new PatternDatabase(distances, depth);
    }

    // Header: magic, version, identity (UTF), size, goal, max distance; then bytes.
    private static PatternDatabase load(Path file, String identity, int size, int goal)
            throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            if (in.readInt() != MAGIC || in.readInt() != FORMAT || !in.readUTF().equals(identity)
                    || in.readInt() != size || in.readInt() != goal)
                throw new IOException("Header mismatch");
            int max = in.readInt();
            if (max < 0 || max > 126) throw new IOException("Invalid maximum distance");
            byte[] data = new byte[size];
            in.readFully(data);
            if (in.read() != -1 || data[goal] != 0) throw new IOException("Invalid payload");
            int actualMax = 0;
            for (byte d : data) {
                if (d < 0 || d > max) throw new IOException("Invalid distance");
                actualMax = Math.max(actualMax, d);
            }
            if (actualMax != max) throw new IOException("Maximum distance mismatch");
            return new PatternDatabase(data, max);
        }
    }

    private void save(Path file, String identity, int goal) throws IOException {
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file)))) {
            out.writeInt(MAGIC); out.writeInt(FORMAT); out.writeUTF(identity);
            out.writeInt(distances.length); out.writeInt(goal); out.writeInt(maxDistance);
            out.write(distances);
        }
    }
}
