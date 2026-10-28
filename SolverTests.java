import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/** Deterministic tests, no JUnit dependency. Run: java -Xmx512m SolverTests */
public final class SolverTests {
    private static int checks;
    private static final SearchResult.Limits SHALLOW = new SearchResult.Limits(30_000, 2_000_000, 100_000);
    private SolverTests() {}

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("--benchmark")) { benchmark(); return; }
        long started = System.nanoTime();
        cubeTests();
        coordinateTests();
        cacheTests();
        PatternDatabases pdb = new PatternDatabases(Path.of(".cache"));
        patternTests(pdb);
        solverTests(pdb);
        System.out.printf("PASS: %,d checks in %.3f s%n", checks,
                (System.nanoTime() - started) / 1_000_000_000.0);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void expectInvalid(Runnable task) {
        boolean rejected = false;
        try { task.run(); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "Invalid input must be rejected");
    }

    private static void cubeTests() {
        CubeState solved = CubeState.solved();
        check(solved.isSolved(), "Solved cube");
        for (Move m : Move.ALL) {
            check(solved.apply(m).apply(m.inverse()).isSolved(), "Inverse: " + m);
            check(m.inverse().inverse() == m, "Inverse involution");
            check(!solved.apply(m).isSolved(), "A face turn changes solved cube");
        }
        for (String face : List.of("U", "D", "L", "R", "F", "B")) {
            check(CubeParser.fromScramble(String.join(" ", face, face, face, face)).isSolved(), "Four turns");
            check(CubeParser.fromScramble(face + "2 " + face + "2").isSolved(), "Two half-turns");
        }
        check(CubeParser.fromScramble("R U R' U' ".repeat(6)).isSolved(), "Known six repetitions identity");
        for (String pair : List.of("U D", "L R", "F B")) {
            List<Move> moves = CubeParser.parseMoves(pair);
            check(solved.apply(moves).equals(solved.apply(List.of(moves.get(1), moves.get(0)))), "Opposite commute");
        }
        CubeState a = CubeParser.fromScramble("R U F2"), b = CubeParser.fromScramble("R U F2");
        check(a.equals(b) && a.hashCode() == b.hashCode(), "Equality and hashCode");
        check(!a.equals(solved) && !a.equals(null) && !a.equals("cube"), "Inequality");
        CubeState before = a; a.apply(Move.B); check(a.equals(before), "Immutable application");
        Random random = new Random(20261028);
        for (int trial = 0; trial < 100; trial++) {
            List<Move> sequence = randomMoves(random, 30);
            CubeState state = solved.apply(sequence);
            check(state.apply(Move.inverse(sequence)).isSolved(), "Sequence inverse");
            // Geometry-based sticker oracle does not use CubeState's move tables.
            StickerOracle oracle = new StickerOracle();
            for (Move move : sequence) oracle.apply(move);
            check(oracle.matches(state), "Independent sticker/cubie agreement");
            check(copyThroughValidatedFactory(state).equals(state), "Moves preserve reachability invariants");
        }
        for (Move move : Move.ALL) {
            StickerOracle oracle = new StickerOracle(); oracle.apply(move);
            check(oracle.matches(solved.apply(move)), "Sticker oracle: " + move);
        }
        int[] cp = {0,1,2,3,4,5,6,7}, ep = {0,1,2,3,4,5,6,7,8,9,10,11};
        int[] twist = new int[8]; twist[0] = 1;
        expectInvalid(() -> CubeState.of(cp, twist, ep, new int[12]));
        int[] flip = new int[12]; flip[0] = 1;
        expectInvalid(() -> CubeState.of(cp, new int[8], ep, flip));
        int[] swapped = cp.clone(); swapped[0] = 1; swapped[1] = 0;
        expectInvalid(() -> CubeState.of(swapped, new int[8], ep, new int[12]));
        int[] duplicate = cp.clone(); duplicate[0] = 1;
        expectInvalid(() -> CubeState.of(duplicate, new int[8], ep, new int[12]));
        int[] input = cp.clone();
        CubeState copied = CubeState.of(input, new int[8], ep, new int[12]); input[0] = 7;
        check(copied.isSolved(), "Factory defensively copies arrays");
        expectInvalid(() -> CubeParser.parseMoves("R3"));
        expectInvalid(() -> CubeParser.parseMoves("r U"));
        check(CubeParser.parseMoves("  \t ").isEmpty(), "Empty scramble");
        check(CubeParser.format(CubeParser.parseMoves("U U' U2 D D' D2 L L' L2 R R' R2 F F' F2 B B' B2"))
                .equals("U U' U2 D D' D2 L L' L2 R R' R2 F F' F2 B B' B2"), "All notations");
        System.out.println("Cube/move/parser tests passed, including independent sticker oracle.");
    }

    private static CubeState copyThroughValidatedFactory(CubeState s) {
        int[] cp = new int[8], co = new int[8], ep = new int[12], eo = new int[12];
        for (int i = 0; i < 8; i++) { cp[i] = s.cornerAt(i); co[i] = s.cornerTwist(i); }
        for (int i = 0; i < 12; i++) { ep[i] = s.edgeAt(i); eo[i] = s.edgeFlip(i); }
        return CubeState.of(cp, co, ep, eo);
    }

    private static void coordinateTests() {
        for (int rank = 0; rank < Coordinates.CORNER_PERMUTATIONS; rank++)
            check(Coordinates.rankPermutation(Coordinates.unrankPermutation(rank, 8)) == rank, "Lehmer roundtrip");
        for (int rank = 0; rank < Coordinates.CORNER_ORIENTATIONS; rank++) {
            int[] co = Coordinates.decodeCornerOrientation(rank);
            check(Coordinates.cornerOrientation(co) == rank && Arrays.stream(co).sum() % 3 == 0, "Twist roundtrip");
        }
        for (int rank = 0; rank < Coordinates.EDGE_POSITIONS; rank++)
            check(Coordinates.rankEdgePositions(Coordinates.decodeEdgePositions(rank)) == rank, "Ordered positions roundtrip");
        Random random = new Random(481516);
        for (int trial = 0; trial < 20_000; trial++) {
            int corner = random.nextInt(Coordinates.CORNER_STATES);
            Coordinates.Corners c = Coordinates.decodeCorners(corner);
            check(Coordinates.encodeCorners(c.permutation(), c.orientation()) == corner, "Full corner roundtrip");
            int edge = random.nextInt(Coordinates.EDGE_STATES);
            Coordinates.Edges e = Coordinates.decodeEdges(edge);
            check(Coordinates.encodeEdges(e.positions(), e.orientation()) == edge, "Full edge roundtrip");
        }
        System.out.println("Coordinate roundtrip tests passed.");
    }

    private static void cacheTests() throws IOException {
        // Tiny 18-move cyclic graph isolates cache generation/load/truncation recovery.
        Path directory = Files.createTempDirectory(Path.of("."), "cache-test-");
        Path file = directory.resolve("tiny.pdb");
        try {
            var next = (java.util.function.IntBinaryOperator) (c, m) -> (c + (m % 2 == 0 ? 1 : 6)) % 7;
            PatternDatabase first = PatternDatabase.loadOrGenerate(file, "test-cycle", 7, 0, next);
            PatternDatabase loaded = PatternDatabase.loadOrGenerate(file, "test-cycle", 7, 0, next);
            for (int i = 0; i < 7; i++)
                check(first.distance(i) == Math.min(i, 7 - i) && loaded.distance(i) == first.distance(i), "Cache roundtrip");
            Files.write(file, new byte[] {1,2,3});
            PatternDatabase repaired = PatternDatabase.loadOrGenerate(file, "test-cycle", 7, 0, next);
            check(repaired.distance(3) == 3, "Truncated cache rebuild");
            PatternDatabase changed = PatternDatabase.loadOrGenerate(file, "different-identity", 7, 0, next);
            check(changed.distance(4) == 3, "Incompatible cache rebuild");
        } finally { Files.deleteIfExists(file); Files.deleteIfExists(directory); }
    }

    private static void patternTests(PatternDatabases pdb) {
        CubeState state = CubeState.solved();
        check(pdb.heuristic(state) == 0, "Solved heuristic zero");
        check(pdb.cornerDistance(0) == 0, "Corner goal zero");
        check(pdb.edgeADistance(pdb.edgeACoordinate(state)) == 0, "Edge A goal zero");
        check(pdb.edgeBDistance(pdb.edgeBCoordinate(state)) == 0, "Edge B goal zero");
        Random random = new Random(90210);
        for (int trial = 0; trial < 500; trial++) {
            state = state.apply(Move.ALL[random.nextInt(18)]);
            int corner = pdb.cornerCoordinate(state), edgeA = pdb.edgeACoordinate(state), edgeB = pdb.edgeBCoordinate(state);
            for (Move move : Move.ALL) {
                CubeState child = state.apply(move); int m = move.ordinal();
                int c = pdb.nextCorner(corner, m), a = pdb.nextEdge(edgeA, m), b = pdb.nextEdge(edgeB, m);
                check(c == pdb.cornerCoordinate(child), "Corner transition table");
                check(a == pdb.edgeACoordinate(child), "Edge A transition table");
                check(b == pdb.edgeBCoordinate(child), "Edge B transition table");
                check(Math.abs(pdb.heuristic(corner, edgeA, edgeB) - pdb.heuristic(c, a, b)) <= 1, "Heuristic consistency sample");
            }
        }
        // Ignored edges can both be flipped in a physically valid, unsolved state.
        int[] cp = {0,1,2,3,4,5,6,7}, ep = {0,1,2,3,4,5,6,7,8,9,10,11};
        int[] eo = new int[12]; eo[10] = eo[11] = 1;
        CubeState hidden = CubeState.of(cp, new int[8], ep, eo);
        check(pdb.heuristic(hidden) == 0 && !hidden.isSolved(), "Zero heuristic is not a full goal test");
        SearchResult limited = new OptimalSolver(pdb).solve(hidden, new SearchResult.Limits(0, 1, 0));
        check(limited.limitReached() && limited.solution() == null, "Must not accept an abstract-only goal");
        System.out.println("PDB goal/transition/consistency tests passed.");
    }

    private static void verify(CubeState start, SearchResult result, String algorithm) {
        check(result.solved(), algorithm + " should finish this example");
        check(start.apply(result.solution()).isSolved(), algorithm + " solution replay");
    }

    private static void solverTests(PatternDatabases pdb) {
        OptimalSolver ida = new OptimalSolver(pdb);
        CubeState solved = CubeState.solved();
        for (SearchResult r : List.of(BasicSolver.bfs(solved, SHALLOW), BasicSolver.bidirectionalBfs(solved, SHALLOW),
                BasicSolver.iddfs(solved, SHALLOW), ida.solve(solved, SHALLOW))) {
            verify(solved, r, "Solved case"); check(r.solutionLength() == 0, "Zero optimal length");
        }
        // Exhaustive depth <= 2 move words, including cancellation and opposite faces.
        int compared = 0;
        for (Move first : Move.ALL) for (Move second : Move.ALL) {
            CubeState state = solved.apply(first).apply(second);
            SearchResult bfs = BasicSolver.bfs(state, SHALLOW), optimal = ida.solve(state, SHALLOW);
            verify(state, bfs, "BFS"); verify(state, optimal, "IDA*");
            check(bfs.solutionLength() == optimal.solutionLength(), "BFS/IDA* exhaustive length match");
            check(pdb.heuristic(state) <= bfs.solutionLength(), "Admissibility vs BFS");
            compared++;
        }
        Random random = new Random(123456789);
        for (int trial = 0; trial < 120; trial++) {
            CubeState state = solved.apply(randomMoves(random, trial % 5));
            SearchResult bfs = BasicSolver.bfs(state, SHALLOW), optimal = ida.solve(state, SHALLOW);
            verify(state, bfs, "BFS"); verify(state, optimal, "IDA*");
            check(bfs.solutionLength() == optimal.solutionLength(), "BFS/IDA* random length match");
            check(pdb.heuristic(state) <= bfs.solutionLength(), "Heuristic bounded by BFS distance");
            SearchResult bi = BasicSolver.bidirectionalBfs(state, SHALLOW);
            verify(state, bi, "Bidirectional BFS");
            check(bi.solutionLength() == bfs.solutionLength(), "Bidirectional BFS optimal length");
            if (trial < 40) {
                SearchResult iddfs = BasicSolver.iddfs(state, SHALLOW);
                verify(state, iddfs, "IDDFS");
                check(iddfs.solutionLength() == bfs.solutionLength(), "IDDFS optimal length");
            }
            compared++;
        }
        for (String scramble : List.of("R U F", "R U F2 L D B'", "R U2 F' L D B2 R' U F2 D'",
                "R U2 F' L D B2 R' U F2 D' L2 B")) {
            CubeState state = CubeParser.fromScramble(scramble);
            SearchResult r = ida.solve(state, new SearchResult.Limits(60_000, 30_000_000, 0));
            verify(state, r, "Realistic deterministic scramble");
            if (scramble.endsWith("L2 B")) check(r.iterations() > 1, "Exercise threshold increases");
            System.out.printf("Replay passed: %s -> %d moves, %,d nodes, %d ms%n",
                    scramble, r.solutionLength(), r.nodesVisited(), r.elapsedMilliseconds());
        }
        CubeState state = CubeParser.fromScramble("R U F2 L D B'");
        SearchResult.Limits oneNode = new SearchResult.Limits(0, 1, 0);
        for (SearchResult r : List.of(BasicSolver.bfs(state, oneNode), BasicSolver.bidirectionalBfs(state, oneNode),
                BasicSolver.iddfs(state, oneNode), ida.solve(state, oneNode)))
            check(r.limitReached() && r.solution() == null && r.nodesVisited() == 1, "Node limit is not no-solution");
        SearchResult.Limits twoStates = new SearchResult.Limits(0, 0, 2);
        check(BasicSolver.bfs(state, twoStates).limitReached(), "BFS state cap");
        check(BasicSolver.bidirectionalBfs(state, twoStates).limitReached(), "Bidirectional combined state cap");
        check(BasicSolver.bidirectionalBfs(state, new SearchResult.Limits(0, 0, 1)).limitReached(), "Single-state cap");
        CubeState hard = CubeParser.fromScramble("R U2 F' L D B2 R' U F2 D' L2 B U' R2 F D2 L' B' U2 R");
        SearchResult timed = ida.solve(hard, new SearchResult.Limits(1, 0, 0));
        check(timed.limitReached() && timed.solution() == null, "Timeout is not no-solution");
        System.out.println("BFS/IDA* equal lengths on " + compared + " deterministic shallow cases.");
    }

    private static List<Move> randomMoves(Random random, int length) {
        List<Move> moves = new ArrayList<>();
        for (int i = 0; i < length; i++) moves.add(Move.ALL[random.nextInt(18)]);
        return moves;
    }

    private static void benchmark() throws IOException {
        System.out.printf("Runtime: %s %s; OS: %s %s; available processors: %d; max heap: %,d bytes%n",
                System.getProperty("java.vm.name"), System.getProperty("java.version"),
                System.getProperty("os.name"), System.getProperty("os.arch"),
                Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().maxMemory());
        PatternDatabases pdb = new PatternDatabases(Path.of(".cache"));
        OptimalSolver solver = new OptimalSolver(pdb);
        String[] scrambles = {
            "R U F",
            "R U F2 L D B'",
            "R U2 F' L D B2 R' U F2 D'",
            "R U2 F' L D B2 R' U F2 D' L2 B",
            "R U2 F' L D B2 R' U F2 D' L2 B U' R2",
            "R U2 F' L D B2 R' U F2 D' L2 B U' R2 F D2 L' B' U2 R"
        };
        for (String scramble : scrambles) {
            SearchResult r = solver.solve(CubeParser.fromScramble(scramble),
                    new SearchResult.Limits(30_000, 5_000_000, 0));
            Main.printReport(scramble, "ida", r);
        }
    }

    /** Test-only geometric oracle. Coordinates: right +x, up +y, front +z.
     * It rotates individual stickers with elementary 90-degree geometry. */
    private static final class StickerOracle {
        private static final String[] CORNERS = {"URF","UFL","ULB","UBR","DFR","DLF","DBL","DRB"};
        private static final String[] EDGES = {"UR","UF","UL","UB","DR","DF","DL","DB","FR","FL","BL","BR"};
        private static final String FACES = "UDLRFB";
        private static final int[][] NORMAL = {{0,1,0},{0,-1,0},{-1,0,0},{1,0,0},{0,0,1},{0,0,-1}};
        private final int[][] position = new int[54][3], normal = new int[54][3];
        private final char[] color = new char[54];
        StickerOracle() {
            int index = 0;
            for (int f = 0; f < 6; f++) {
                int axis = NORMAL[f][0] != 0 ? 0 : NORMAL[f][1] != 0 ? 1 : 2;
                for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) {
                    position[index][axis] = NORMAL[f][axis];
                    position[index][(axis + 1) % 3] = a; position[index][(axis + 2) % 3] = b;
                    normal[index] = NORMAL[f].clone(); color[index] = FACES.charAt(f); index++;
                }
            }
        }
        void apply(Move move) {
            int[] outward = NORMAL[move.face];
            int axis = outward[0] != 0 ? 0 : outward[1] != 0 ? 1 : 2;
            int sign = outward[axis];
            for (int t = 0; t < move.quarterTurns; t++) for (int i = 0; i < 54; i++)
                if (position[i][axis] == sign) {
                    rotate(position[i], axis, -sign); rotate(normal[i], axis, -sign);
                }
        }
        private static void rotate(int[] vector, int axis, int direction) {
            int a = (axis + 1) % 3, b = (axis + 2) % 3, oldA = vector[a];
            vector[a] = -direction * vector[b]; vector[b] = direction * oldA;
        }
        private char sticker(String location, char face) {
            int[] target = new int[3];
            for (char c : location.toCharArray()) {
                int[] n = NORMAL[FACES.indexOf(c)];
                for (int a = 0; a < 3; a++) target[a] += n[a];
            }
            for (int i = 0; i < 54; i++)
                if (Arrays.equals(position[i], target) && Arrays.equals(normal[i], NORMAL[FACES.indexOf(face)]))
                    return color[i];
            throw new AssertionError("Missing sticker");
        }
        boolean matches(CubeState state) {
            for (int p = 0; p < 8; p++) for (int slot = 0; slot < 3; slot++)
                if (sticker(CORNERS[p], CORNERS[p].charAt((slot + state.cornerTwist(p)) % 3))
                        != CORNERS[state.cornerAt(p)].charAt(slot)) return false;
            for (int p = 0; p < 12; p++) for (int slot = 0; slot < 2; slot++)
                if (sticker(EDGES[p], EDGES[p].charAt((slot + state.edgeFlip(p)) % 2))
                        != EDGES[state.edgeAt(p)].charAt(slot)) return false;
            return true;
        }
    }
}
