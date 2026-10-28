# Actual execution results

Recorded on 28 October 2026. No estimated or invented solve times appear here.
These are individual deterministic runs, not a statistical performance study.

Environment: Windows 11 amd64; OpenJDK/Temurin 21.0.12.1; 8 processors visible to
the JVM; Intel64 Family 6 Model 140 Stepping 2; `-Xmx512m` (536,870,912-byte heap
limit). The algorithm is single-threaded. Java 17 language/API compatibility was
also checked with `javac --release 17 -Xlint:all *.java` on this installed JDK;
execution on an actual Java 17 runtime was not separately tested.

## Reproduce

Run in the project directory:

```powershell
javac --release 17 -Xlint:all *.java
java -Xmx512m Main --build-pdb
java -Xmx512m SolverTests
java -Xmx512m SolverTests --benchmark
```

The benchmark uses **30,000 ms and 5,000,000 visits per input**. Those node caps
are lower than Main's defaults. This is intentionally a bounded demo; a limit
here does not say that the next few seconds or a larger budget would fail.
The 10/12/14/20-turn inputs are deterministic prefixes of the same sequence,
not independent uniformly random cubes. Scramble word length is not assumed to
be the state's optimal distance. Optimal length is reported only after success.

## Full database generation and reload

`PDB_BUILD.txt` is the original complete generation log. Every coordinate was
reached. Initialization, including move tables, generation and saves, took
**11.185 seconds** on that run. A subsequent benchmark initialization loaded
the compatible caches and rebuilt the small move tables in **0.317 seconds**.

| Database | Entries reached | Measured max abstract distance | Exact cache bytes |
|---|---:|---:|---:|
| corners.pdb | 88,179,840 | 11 | 88,179,887 |
| edgesA.pdb | 3,041,280 | 9 | 3,041,335 |
| edgesB.pdb | 3,041,280 | 9 | 3,041,335 |
| Total | 94,262,400 | | 94,262,557 |

Headers contribute 157 bytes total. Distance payload is 94,262,400 bytes
(89.896 MiB); distance plus move-table primitive payload is 105,797,772 bytes
(100.897 MiB). No process-RSS or peak-memory measurement was performed.
The build code guards signed-byte capacity; no nibble packing is used.

## Optimal IDA* results

Source: `BENCHMARK_RESULTS.txt`, from the final benchmark run after readability
cleanup. Each successful solution was replayed to the full solved state.

| Input turns | Optimal length | Initial h | Iterations | Visits | Pruned | Search ms | Result |
|---:|---:|---:|---:|---:|---:|---:|---|
| 3 | 3 | 3 | 1 | 25 | 24 | 1 | verified optimal |
| 6 | 6 | 6 | 1 | 46 | 51 | 0 | verified optimal |
| 10 | 10 | 10 | 1 | 297 | 368 | 1 | verified optimal |
| 12 | 12 | 8 | 5 | 683,355 | 869,708 | 225 | verified optimal |
| 14 | unknown | 9 | 5 | 5,000,000 | 6,362,051 | 1,146 | node limit |
| 20 | unknown | 10 | 4 | 5,000,000 | 6,352,708 | 1,096 | node limit |

`0 ms` means less than one millisecond after truncation in that run, not zero
computation. Very small times vary with JVM warmup and system activity. These
times **exclude PDB initialization**. A realistic first-run total includes that
preprocessing plus JVM startup, and later runs include cache loading/startup.

Exact inputs and returned paths:

```text
3 turns:
Scramble: R U F
Solution: F' U' R'

6 turns:
Scramble: R U F2 L D B'
Solution: B D' L' F2 U' R'

10 turns:
Scramble: R U2 F' L D B2 R' U F2 D'
Solution: D F2 U' R B2 D' L' F U2 R'

12 turns:
Scramble: R U2 F' L D B2 R' U F2 D' L2 B
Solution: B' L2 D F2 U' R B2 D' L' F U2 R'

14 turns:
Scramble: R U2 F' L D B2 R' U F2 D' L2 B U' R2
Search limit reached; no solution returned.

20 turns:
Scramble: R U2 F' L D B2 R' U F2 D' L2 B U' R2 F D2 L' B' U2 R
Search limit reached; no solution returned.
```

The 12-turn case is useful in an interview: its initial lower bound was only 8.
IDA* searched thresholds 8,9,10,11,12 before finding the minimum. It demonstrates
actual iterative deepening beyond a perfect initial estimate.

## A larger-budget hard run

`HARDER_RUN.txt` records this additional command:

```powershell
java -Xmx512m Main "R U2 F' L D B2 R' U F2 D' L2 B U' R2" --max-nodes 100000000 --timeout-ms 30000
```

It reached the **100,000,000-node cap** after **19,946 ms**, pruned 127,302,631
branches/candidates and entered six iterations, with initial h=9. It returned
only `Search limit reached`. Even this 14-turn input was difficult for the chosen
patterns and move ordering. We did not determine its optimal length. An inverse
scramble is a valid upper bound, but the program deliberately does not return it
as an “optimal” fallback. No arbitrary-random-cube throughput claim is supported.

## Educational algorithm comparison

Separate CLI runs on `R U F`, recorded in `CLI_EXAMPLES.txt`:

| Algorithm | Optimal length | Visits | Pruned | Search ms |
|---|---:|---:|---:|---:|
| BFS | 3 | 198 | 915 | 4 |
| Bidirectional BFS | 3 | 20 | 81 | 1 |
| IDDFS | 3 | 2,857 | 3,614 | 2 |
| IDA* | 3 | 25 | 24 | 0 |

All four returned `F' U' R'` and passed replay. IDA* additionally paid 0.358 s of
database initialization in that CLI process. The log also contains the requested
five-move example, help output and rejection of invalid input `R3`.
Do not compare these few millisecond timings as a controlled benchmark.

## Metric definitions

- `nodesVisited`: accepted DFS/search entries, including heuristic/depth cutoffs;
  for BFS/bidirectional BFS, states removed from a frontier for expansion.
  Counts accumulate across iterations. They are not unique-state counts.
- `nodesPruned`: rejected redundant moves, bound/depth cutoffs, or duplicate BFS
  successors. It can exceed visits because forbidden successors are never visited.
- `solutionLength`: returned FTM length; absent/unknown on a limit.
- `elapsedMilliseconds`: search-only time, truncated from a monotonic clock,
  captured before final replay verification. Excludes initialization.
- `initialHeuristic`: PDB lower bound for IDA*; zero for the three basic algorithms.
- `iterations`: IDA* thresholds; IDDFS depth limits; bidirectional layers expanded;
  ordinary BFS reports one traversal. This is not a uniform cross-algorithm count.

## Tests and simplicity review

`TEST_RESULTS.txt`: **217,136 checks passed**, including **444 BFS/IDA* optimal
length matches**, in **0.910 seconds** on the recorded final test run. That time
includes cache initialization, the added 12-turn regression case and a timeout
test. It does not include first-time generation.

The tests exhaustively roundtrip 40,320 corner permutation ranks, 2,187 corner
orientation ranks and 95,040 ordered edge-position ranks, and sample 20,000
combined coordinates of each kind. They compare 9,000 coordinate transitions
to moved full states, and 100 seeded 30-move sequences plus all 18 individual
moves to an independent geometric sticker oracle. All 324 two-move words and
120 seeded words of lengths 0–4 are compared against BFS. Small bidirectional
and IDDFS results are also compared to BFS; solved, limit and invalid-input
behaviors are tested. Replay runs unconditionally, without `-ea`.

The final simplicity pass replaced compact opposite-face bit logic with explicit
face-pair arithmetic, and a decoder's side-effecting condition with an explicit
“take unused label” loop. The core IDA* remains one short file; no transposition
table, two-phase solver, search framework or external dependency was added.
