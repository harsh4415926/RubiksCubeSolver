# CubeTrace — Optimal 3×3 Rubik's Cube Solver

Java 17+, standard library only. Main algorithm: **full-cube IDA* with admissible
pattern databases, under the face-turn metric (FTM)**. Successful results are
globally shortest. A resource limit returns no solution and no optimality claim.

## Database size calculation (chosen before implementation)

| Pattern | State count | Bytes per entry | Distance array / payload |
|---|---:|---:|---:|
| All 8 corners | 8! × 3^7 = 88,179,840 | 1 | 84.095 MiB |
| Edges A: UR UF UL UB DR | 12P5 × 2^5 = 3,041,280 | 1 | 2.900 MiB |
| Edges B: DF DL DB FR FL | 12P5 × 2^5 = 3,041,280 | 1 | 2.900 MiB |
| **Total** | **94,262,400** | | **89.896 MiB** |

MiB = 1,048,576 bytes. Caches have the same payload size plus a tiny header per
file (magic, format, identity, entry count, goal coordinate, maximum distance).
Move tables use 11,535,372 bytes (11.001 MiB): 40,320 × 18 × 4 for corner
permutation; 2,187 × 18 × 2 for corner orientation; 95,040 × 18 × (4+1) for
edge positions and flip masks. Both edge patterns share the last two tables.
Persistent primitive-array payload: **105,797,772 bytes = 100.897 MiB**.
Array/object headers, temporary arrays, search allocations and JVM overhead are
additional. `-Xmx512m` provides comfortable heap space; it is not an RSS promise.

For comparison, two six-edge patterns each have 12P6 × 2^6 = 42,577,920 states.
Together with the corner pattern that is 173,335,680 bytes (165.306 MiB) at one
byte per entry, before larger move tables. Classic four-bit packing halves the
distance payload but adds complexity. This project chooses five-edge patterns
and simple bytes. Smaller patterns weaken speed, **not admissibility or optimality**.

## Run it

Open a terminal in this directory. JDK 17 or later is required (tested on JDK 21).
There is no Maven, Gradle, external dependency, network download or GUI.

```powershell
javac -Xlint:all *.java
java -Xmx512m Main "R U R' U' F2"
java -Xmx512m SolverTests
java -Xmx512m SolverTests --benchmark
```

`javac *.java` also works. After compilation the requested `java Main "R U F"`
works with the JVM's default heap; the explicit 512 MiB setting is recommended.
PowerShell double quotes preserve the `'` characters inside the scramble.

```powershell
java Main "R U F" --algorithm bfs
java Main "R U F" --algorithm bibfs
java Main "R U F" --algorithm iddfs
java -Xmx512m Main "R U F" --algorithm ida
java -Xmx512m Main --build-pdb
java -Xmx512m Main "R U F" --timeout-ms 30000 --max-nodes 5000000
java -Xmx512m Main "R U F" --cache .cache
java Main --help
```

Defaults: IDA*, 60 seconds of **search**, 10,000,000 node visits, and 100,000 stored
states for BFS/bidirectional BFS. Bidirectional BFS counts both maps together.
`--max-states` does not apply to IDDFS/IDA*. Zero disables the corresponding limit.
For example, `--timeout-ms 0 --max-nodes 0` removes both IDA* search limits.
Preprocessing/cache loading is separately timed and is outside the search timeout.
Timeouts are checked at node visits, not by interrupting a running move operation.
Memory limits are state counts, not exact byte budgets. Do not disable BFS limits
casually. Successful search output always includes replay verification.

Input is a scramble from the solved cube using U D L R F B, optionally followed by
`'` (counterclockwise) or `2` (half-turn). Turns are viewed straight at that face.
All 18 cost **one**. Empty input `""` is the solved state. No sticker scanner,
whole-cube rotations, slice moves, wide moves or physical-cube input UI is included.
For arbitrary reachable cubie input, use `CubeState.of(...)` in Java; it validates
piece uniqueness, twist sum, flip sum and matching permutation parity.

## Project structure

```text
CubeTrace/
  Main.java                 CLI and human-readable reports
  Move.java                 18 FTM moves, inverses, safe DFS pruning
  CubeState.java            immutable cubie model and move transformations
  CubeParser.java           notation parsing and formatting
  BasicSolver.java          BFS, bidirectional BFS, IDDFS
  Coordinates.java          dense permutation/orientation/edge-subset indexes
  PatternDatabase.java      byte distances, reverse BFS, simple binary cache
  PatternDatabases.java     three patterns and factorized coordinate move tables
  OptimalSolver.java        full-cube IDA* (the core interview file)
  SearchResult.java         result, limits, counters and replay verification
  SolverTests.java          standalone deterministic tests and six-case benchmark
  README.md
  INTERVIEW_PREP.md
  RESUME.md
  MEASUREMENTS.md
  TEST_RESULTS.txt          actual final test run
  BENCHMARK_RESULTS.txt     actual six-case run
  PDB_BUILD.txt             actual first generation
  HARDER_RUN.txt            additional 14-turn input run with a larger node budget
  CLI_EXAMPLES.txt          all four algorithms, five-turn demo, help/input errors
  .gitignore
  .cache/                  generated locally; safe to delete and rebuild
    corners.pdb
    edgesA.pdb
    edgesB.pdb
  *.class                  generated by javac
```

Ten production Java classes and one test class. Read `INTERVIEW_PREP.md` for a
three-hour study plan, forty questions, and the ten code sections to know.

## The graph and the algorithm progression

A complete legal cubie arrangement is a **vertex**. Applying one face turn is an
**edge** of cost one. Every move has an inverse, so this is an undirected,
unit-cost graph. The problem is a shortest path from the input vertex to solved.
The state includes both permutation and orientation; equal permutations alone
do not identify equal states. Centers establish a fixed face/color frame.

### 1. BFS

`BasicSolver.bfs` uses an `ArrayDeque` and a `HashMap<CubeState, Parent>`.
The map both marks visited states and stores parent pointers. BFS discovers
depth 0, then 1, then 2, and so on. The first discovered goal therefore has minimum
FTM distance. Reconstruct the solution by following parents and reversing moves.

The state-space view gives O(V+E) graph traversal time. Written in terms of
branching factor b and solution depth d, tree growth is O(b^d) time and space.
The memory for the frontier **and all visited states/parents** is its weakness.
It uses all 18 moves; it does not merge states while imposing a last-face rule.

### 2. Bidirectional BFS

Start one BFS at the input and another at solved. Expand the smaller frontier,
but always expand its **entire current layer**. Both visited maps remain complete
balls of their respective radii. At the first meeting, reconstruct input → meeting
and append the inverse of solved → meeting (reverse the order AND invert moves).

Why is choosing the smaller layer safe? Before the meeting the balls of radii a
and b are disjoint. If a solution of length at most a+b existed, those balls would
already overlap along that path. A new meeting while growing one ball by one
therefore yields distance a+b+1, the first possible distance. New meetings cannot
connect to an older inner layer of the opposite ball without implying an earlier
overlap. Our code finishes the layer before returning; a resource limit during
that layer conservatively returns only a limit status.

Intuitively, two searches to about d/2 cost O(b^(d/2)) time/space in a balanced
tree, although graph shape and frontier imbalance affect the actual work. It
still stores many states and is intended for short scrambles.

### 3. IDDFS

Run depth-limited DFS with limits 0, 1, 2, ... . Unit-cost edges imply that the
first successful depth is optimal. DFS alone can find a long solution before a
shorter one; IDDFS fixes that by limiting depth. Its active path uses O(d) space
for constant-size cube states, with no visited map. Time is O(b^d) for b>1, but
shallow layers are expanded repeatedly. It demonstrates the transition from
memory-heavy BFS to memory-light optimal search.

### 4. IDA*

```text
g = moves already made
h = lower bound on moves remaining
f = g + h

bound = h(start)
repeat:
    depth-first search, pruning nodes where f > bound
    if goal found: return path
    bound = smallest f that exceeded the old bound
```

`OptimalSolver.search` contains precisely those steps. IDDFS limits `g`; IDA*
limits `g+h`. The estimate avoids searching many nodes that cannot finish within
the current threshold. A* uses the same f-cost idea with a global priority queue
and usually a large visited/best-cost map; IDA* trades repeated work for small
active-path memory. IDA* is still exponential in the worst case. Its memory is
O(d) **for the DFS**, plus the substantial fixed pattern database/move-table memory.

## Admissible heuristics and pattern databases

Admissible means `0 <= h(s) <= trueDistance(s, solved)`. A heuristic may be weak
and return zero for unsolved states; it must never claim too many moves remain.

A pattern keeps only some cubies and forgets the others. A full move still has a
well-defined effect on the selected cubies. Reverse BFS from the abstract solved
configuration stores the **exact abstract shortest distance**, one byte per index.
Because every move has an inverse in our 18-move set, expanding outward from the
goal is exactly the required reverse search. BFS is exact because all abstract
edges also cost one, including moves that leave the abstract pattern unchanged.

Any full solution projects to an abstract walk ending at the abstract goal. That
walk has the same number of moves (some can be abstract self-loops). Its length
cannot be smaller than the abstract shortest distance. Hence each database value
is a lower bound on the full solution length.

```java
h = Math.max(cornerDistance, Math.max(edgeADistance, edgeBDistance));
```

**Why max, not sum?** A single face turn can improve several patterns together.
Adding their distances could count that move several times. Even disjoint sets
of cubies do not automatically give additive costs. Each individual lower bound
is at most the true distance, so their maximum is also at most that distance.
We implement no cost partitioning and never add these distances.

## Why My Solver Is Optimal

Let C* be the globally minimum FTM solution length.

1. Every legal search edge costs one. `h(goal)=0`, and h is nonnegative/admissible.
2. Every prefix of an optimal path has `g + h <= C*`, because that path's remaining
   suffix is a valid completion and h cannot exceed its length.
3. The initial threshold `h(start)` is at most C*. If a threshold B<C* fails,
   follow an optimal path until its first cutoff. That cutoff has f<=C*, so the
   smallest exceeded f returned by the iteration is also <=C*. Thresholds thus
   cannot jump over C* before finding a solution.
4. At threshold C*, at least one optimal representative path survives both the
   heuristic cutoff and the safe word pruning below. Finite, complete DFS at
   that threshold reaches a goal. A goal accepted at threshold B has g<=B, since
   h(goal)=0 and the cutoff happens **before** accepting the goal.
5. There is no goal with length <C*. Therefore the first accepted goal has g=C*.

Assumptions: correct cubie moves and coordinate transitions; exact compatible PDB
data generated by this code; all 18 unit-cost moves; the full goal test; complete
DFS within each threshold except for proved safe pruning; and a completed search.
A timeout or node limit breaks completion, so it returns **Search limit reached**,
never a non-optimal candidate or “No solution exists.” Replay establishes that a
path solves the cube; the argument above establishes minimality. Shallow BFS
cross-checks support the implementation but are not a proof for every state.

## Cubie representation

`cornerPermutation[i]` identifies the corner piece at position i;
`cornerOrientation[i]` gives its twist (0,1,2). Edges work the same way with flip
0/1. Solved permutations are identity and orientations are zero. All four arrays
are private; public factories copy input and public readers return scalars.
`apply` allocates a new state, so backtracking removes only the last path move.
An immutable state is also a safe `HashMap` key.

Position and piece numbering use:

```text
corners: URF UFL ULB UBR DFR DLF DBL DRB
edges:   UR  UF  UL  UB  DR  DF  DL  DB  FR  FL  BL  BR
```

These strings also define ordered sticker slots. For example, corner piece URF
has colors in order U,R,F. Twist t means color slot j occupies position slot
`(j+t)%3`. Edge flip uses `(j+flip)%2` with the edge orders above. This makes the
orientation convention precise without needing 3D geometry in the solver.

`CubeState` has six readable quarter-turn source/delta tables. At class startup
it composes those tables into all 18 transformations, so R2 is applied once as a
single search edge. A source table tells which old position fills each new one;
twists add modulo three, flips XOR. `SolverTests` independently checks the
convention by rotating 54 stickers geometrically.

## Coordinate compression

The PDB must use integer array indexes instead of object keys.

**All corners.** There are 8! permutations. There are 3^7 independent twists:
the total twist is 0 mod 3, so the eighth twist is fixed by the first seven.
Encode those seven as a base-three integer. Encode the permutation with Lehmer
ranking. Combine them as `permutationRank * 2187 + orientationRank`. Different
legal corner configurations yield different integers in [0, 88,179,840).
Every such corner configuration has a completion with suitable edges.

**Lehmer code.** For each position count smaller piece labels to its right.
These counts are digits in decreasing radices 8,7,...,1. For example, permutation
`[2,0,1]` has digits `[2,0,0]` and rank 4 out of 3!=6 arrangements. Decoding
extracts the digits and picks the corresponding unused labels in sorted order.

**Five edges.** Fix an ordered list of five piece identities. Record the position
of each selected piece in that identity order. There are 12 choices for the first,
11 for the second, ..., 8 for the fifth: 12P5=95,040. A partial Lehmer ranking
uses `position - count(previously used positions smaller than this one)` as each
digit. It captures both which five positions are occupied AND which piece is
where, so separate combination ranking is unnecessary. Five flip bits have 2^5
possibilities; unselected edges can satisfy the global flip/parity constraints.
The index is `positionRank * 32 + flipBits`, giving 3,041,280 entries. Decoding
recovers exactly the selected pieces' positions and flips, not the ignored cubies.

## PDB generation, move tables and cache

Distances start at -1 (unvisited), except the abstract goal at zero. For depth
0,1,2,... scan all entries with that exact distance and discover their neighbors
at depth+1. New entries are not expanded until the next depth. This is ordinary
layer BFS implemented by scans instead of a potentially enormous integer queue.
It spends extra sequential scans to save queue memory. No full `CubeState` objects
are created per abstract node, and no boxed `Integer` maps are used. The generator
checks that all abstract coordinates were reached and rejects byte overflow.

To make the neighbor calculation cheap, precompute `nextCoordinate[coordinate][move]`
in flattened arrays. The corner coordinate factors into independent permutation
and twist tables. An edge position table also records which selected pieces flip.
Their flip bits XOR that mask. Edge A and B have the same transition tables but
different abstract goals. Search carries all three coordinates alongside the full
cube, updating them by table lookup. This is an optimization only: tests compare
it to freshly encoding the moved full cube on 9,000 transitions.

Caches load automatically if compatible; missing, truncated, header-mismatched or
out-of-range data are rebuilt. Files contain exact generated distances with a
small metadata header, not Java serialization. Cache contents are trusted local
generated data: range/header checks do not detect every possible corruption.
Do not hand-edit them or substitute unverified files; delete `.cache` to regenerate.
There is intentionally no checksum, atomic replacement or cache-management framework.
If disk I/O fails, the program reports the I/O error rather than claiming a solve.

## Safe move pruning

Only IDDFS and IDA* use these path-based rules:

1. **No consecutive turns of the same face.** Two such turns combine into one of
   the other 18 moves or cancel. A shortest word therefore never needs that pair.
   This also removes immediate move/inverse pairs and R2/R2.
2. **Order adjacent opposite faces.** U/D, L/R and F/B operate on disjoint layers
   and commute. Keep U before D, L before R, F before B. A forbidden adjacent pair
   can be swapped without changing the cube or length. Repeating these swaps
   produces an allowed representative of every shortest word. If swapping ever
   exposed a mergeable same-face pair, the original word was not shortest.

There is no transposition table, symmetry reduction, two-phase restriction,
last-axis restriction or unproved pruning. Pruning states by path context while
also merging states requires care; the basic BFS implementations simply explore
all moves and use their visited maps.

## Validation and measured limits

`SolverTests` covers identities/inverses, equality/hash, immutable input copies,
parser errors, invalid cubie inputs, coordinate roundtrips, cache load/rebuild,
PDB goal values, sampled consistency and coordinate transitions, all four solvers,
solution replay, search limits, and comparison to BFS on **444** shallow cases.
Those include all 18×18 two-move words plus 120 seeded random words of length 0–4.
Bidirectional BFS and IDDFS are also compared against BFS on shallow samples.

The full corner database reached 88,179,840 states with maximum abstract distance
11; both five-edge databases reached 3,041,280 states with maximum 9. These are
**measured abstraction diameters**, not claims about the full cube's diameter.
See `MEASUREMENTS.md` and raw logs for timings, returned moves, nodes and limits.

The primary bottleneck on difficult inputs is exponential full search. Immutable
child states also allocate arrays; simpler code was preferred to an in-place
apply/undo implementation. The largest fixed memory consumer is the corner PDB.
BFS's main bottleneck is its growing state/parent map. Random-looking 20-turn
inputs need not have distance 20, and this project does not promise fast optimal
solves for arbitrary random cubes. The measured 20-turn input hit its node cap.

Potential improvements: larger edge patterns, more well-chosen admissible patterns,
four-bit distance packing, careful in-place search state, or a proven context-aware
transposition scheme. Each trades simplicity, memory or preprocessing for speed.
None is required to understand or justify the current solver's optimality.

For the original research inspiration, see Richard Korf's 1997 paper,
[Finding Optimal Solutions to Rubik's Cube Using Pattern Databases](https://cdn.aaai.org/AAAI/1997/AAAI97-109.pdf).
This is a small educational Java implementation of that search idea, with smaller
edge patterns; it does not claim to reproduce the paper's performance.
