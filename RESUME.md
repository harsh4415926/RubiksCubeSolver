# CubeTrace — Optimal Rubik's Cube Solver

Java | DSA | BFS | IDA* | Pattern Databases

- Modeled the 3×3 Rubik's Cube as a state-space graph and implemented BFS,
  bidirectional BFS, IDDFS and full-cube IDA* to compare optimal search strategies.
- Built admissible pattern database heuristics using reverse BFS and compact
  permutation/orientation coordinates, storing three databases in 89.9 MiB of
  distance payload and combining their lower bounds with max.
- Implemented globally optimal completed solutions under the face-turn metric,
  with safe redundant-move pruning and solution replay verification; cross-validated
  IDA* solution lengths against BFS on 444 deterministic shallow cases.

## Be precise when discussing the bullets

The optimality claim concerns **successful completed searches** under FTM. It does
not promise termination within the configured resource limits. The approximately
90 MiB figure excludes move tables, object headers and JVM overhead; primitive
distances plus move tables use about 100.9 MiB. Do not claim constant-time solving,
fast optimal solving of every random cube, or an improvement over research solvers.

Measured examples and failures are in `MEASUREMENTS.md`. The algorithmic optimality
proof and its assumptions are in `README.md` and `INTERVIEW_PREP.md`. Shallow BFS
cross-checks and replay tests support correctness; they do not exhaust all cubes.
