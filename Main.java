import java.nio.file.Path;
import java.util.List;

/** No libraries or build system required. Java 17 or later. */
public final class Main {
    private Main() {}

    public static void main(String[] args) throws Exception {
        try {
            run(args);
        } catch (IllegalArgumentException e) {
            System.err.println("Input error: " + e.getMessage());
            System.err.println("Run java Main --help for usage.");
            System.exit(2);
        }
    }

    private static void run(String[] args) throws Exception {
        String scramble = "R U R' U' F2", algorithm = "ida";
        Path cache = Path.of(".cache");
        long timeout = 60_000, maxNodes = 10_000_000;
        int maxStates = 100_000;
        boolean buildOnly = false, haveScramble = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--help", "-h" -> { help(); return; }
                case "--algorithm" -> algorithm = value(args, ++i);
                case "--timeout-ms" -> timeout = Long.parseLong(value(args, ++i));
                case "--max-nodes" -> maxNodes = Long.parseLong(value(args, ++i));
                case "--max-states" -> maxStates = Integer.parseInt(value(args, ++i));
                case "--cache" -> cache = Path.of(value(args, ++i));
                case "--build-pdb" -> buildOnly = true;
                default -> {
                    if (args[i].startsWith("--") || haveScramble)
                        throw new IllegalArgumentException("Unknown/extra argument: " + args[i]);
                    scramble = args[i]; haveScramble = true;
                }
            }
        }
        if (!List.of("bfs", "bibfs", "iddfs", "ida").contains(algorithm))
            throw new IllegalArgumentException("Algorithm must be bfs, bibfs, iddfs or ida.");
        SearchResult.Limits limits = new SearchResult.Limits(timeout, maxNodes, maxStates);
        List<Move> scrambleMoves = CubeParser.parseMoves(scramble);
        CubeState start = CubeState.solved().apply(scrambleMoves);
        PatternDatabases pdb = null;
        if (algorithm.equals("ida") || buildOnly) pdb = new PatternDatabases(cache);
        if (buildOnly) { System.out.println("Pattern databases ready."); return; }
        SearchResult result = switch (algorithm) {
            case "bfs" -> BasicSolver.bfs(start, limits);
            case "bibfs" -> BasicSolver.bidirectionalBfs(start, limits);
            case "iddfs" -> BasicSolver.iddfs(start, limits);
            default -> new OptimalSolver(pdb).solve(start, limits);
        };
        printReport(CubeParser.format(scrambleMoves), algorithm, result);
    }

    private static String value(String[] args, int index) {
        if (index >= args.length) throw new IllegalArgumentException("Missing option value.");
        return args[index];
    }

    public static void printReport(String scramble, String algorithm, SearchResult result) {
        System.out.println("\nScramble: " + (scramble.isEmpty() ? "(solved)" : scramble));
        System.out.println("Metric: Face-Turn Metric (R, R' and R2 each cost 1)");
        System.out.println("Algorithm: " + switch (algorithm) {
            case "bfs" -> "BFS";
            case "bibfs" -> "Bidirectional BFS";
            case "iddfs" -> "IDDFS";
            default -> "Optimal IDA* + Pattern Databases";
        });
        if (result.solved()) {
            System.out.println("Solution: " + (result.solution().isEmpty() ? "(empty)"
                    : CubeParser.format(result.solution())));
            System.out.println("Optimal solution length: " + result.solutionLength());
            System.out.println("Optimal: yes");
            System.out.println("Verified: yes");
        } else {
            System.out.println("Search limit reached");
            System.out.println("Optimal: no solution returned");
        }
        System.out.println("Nodes visited: " + result.nodesVisited());
        System.out.println("Nodes pruned: " + result.nodesPruned());
        System.out.println("Search time: " + result.elapsedMilliseconds() + " ms");
        System.out.println("Initial heuristic: " + result.initialHeuristic());
        System.out.println("Iterations/layers: " + result.iterations());
    }

    private static void help() {
        System.out.println("""
                CubeTrace -- globally shortest 3x3 solutions under FTM
                javac *.java
                java -Xmx512m Main "R U R' U' F2"
                java Main "R U F" --algorithm bibfs
                Options:
                  --algorithm ida|bfs|bibfs|iddfs  (default: ida)
                  --timeout-ms N                  (default: 60000, search only)
                  --max-nodes N                   (default: 10000000)
                  --max-states N                  (default: 100000, BFS combined map limit)
                  --cache PATH                    (default: .cache)
                  --build-pdb                     generate/load databases and exit
                Zero disables a limit. BFS memory can grow very quickly without limits.
                First IDA* run builds about 90 MiB of cached distances.
                A limit returns no solution and makes no impossibility claim.
                """);
    }
}
