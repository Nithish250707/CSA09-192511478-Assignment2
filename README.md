# CSA09 – Assignment 2: Strings, Collections and Generics (CO2)

| | |
|---|---|
| **Name** | Nithish G |
| **Register Number** | 192511478 |
| **Programme** | B.E. Computer Science and Engineering, SIMATS Engineering, Chennai |
| **Course** | Programming in Java – CSA0921 (printed as CSA09 in the assignment) |
| **Assignment** | 2 – CO2: String/StringBuilder, Collection Framework, hashCode–equals, Generics, Comparable/Comparator |
| **Java version** | `java version "21.0.12.1" 2026-08-18 LTS` (Java HotSpot 64-Bit Server VM, build 21.0.12.1+1-LTS-4) |
| **Report** | [REPORT.md](REPORT.md) – all written answers in question order |

## Repository layout

Each `Qn/` folder has:

* the Java source,
* `output.txt` – the real console output, which is the **raw experimental data**,
* `answer.md` – predictions, analysis, tables, complexity and design decisions,
* `run.sh` – the exact commands used.

`bash run_all.sh` re-runs everything (≈ 2 minutes, dominated by the benchmarks). Because these are console programs, `output.txt` serves as the output evidence in place of screenshots.

## Compile and run each program

Run from the repository root; only a JDK is needed.

| Q | Compile | Run |
|---|---|---|
| Q1 | `cd Q1 && javac -d out *.java` | `java -Xmx2g -cp out TicketBuilderBenchmark` |
| Q2 | `cd Q2 && javac -d out *.java` | `java -Xmx2g -cp out PlaylistBenchmark` |
| Q3 | `cd Q3 && javac -d out/before before/Attendance.java && javac -d out/after Attendance.java && javac -d out ContractExperiments.java` | `java -cp out/before Attendance` (before TODO) · `java -cp out/after Attendance` (after TODO) · `java -cp out ContractExperiments` |
| Q4 | `cd Q4 && javac -d out *.java` | `java -Xmx3g -cp out PharmacyMaps` |
| Q5 | `cd Q5 && javac -d out *.java` | `java -cp out LeaderboardDemo` · `java -Xmx2g -cp out RankingDesign` |
| Q6 | `cd Q6 && javac -d out *.java` | `java -cp out Triage` · `java -Xmx3g -cp out TriageStudy` |
| Q7 | `cd Q7 && javac -d out *.java` | `java -Xmx3g -cp out AgingTriageRedesign` |

## Result of each question

| Q | Result |
|---|---|
| Q1 – String vs StringBuilder vs StringBuffer | `+` in a loop was 1.5× slower for a single short ticket, 3.6× for 10,000 × 50 fields, and **328× slower (110 ms vs 0.34 ms)** for my own 5,000-field manifest workload, confirming the O(n²) prediction. StringBuffer was ≈ StringBuilder single-threaded. A shared StringBuilder lost ~1 % of characters with 4 threads (3/3 trials); StringBuffer did not. Recommendation: StringBuilder per ticket, StringBuffer (whole lines) for a shared buffer. |
| Q2 – ArrayList vs LinkedList | At 100k: ArrayList `get(k)` 49 ns vs LinkedList 43 µs; ArrayList front insert 7 µs vs 28 ns; the middle insert is *faster* on ArrayList (3.5 µs vs 85 µs). ArrayList is right for a playlist. For "Up Next": ArrayList 4.6 s vs ArrayDeque 10 ms, so switch to **ArrayDeque**. |
| Q3 – hashCode–equals | **Before TODO: `Unique students: 3`; after: `Unique students: 2` (expected).** 3 designed tests with predictions: the bug is hidden when the same object is reused (2), exposed with a new object per scan (6 instead of 3), and a mutated key leaves an unreachable entry; a TreeSet by name drops a different student. Fix verified on 10,000 scans. |
| Q4 – Pharmacy maps | HashMap/LinkedHashMap lookups ~16–56 ns from 1k to 1M; TreeMap 4–10× slower (O(log n)). All report orders verified correct. Criterion ≤ 1 µs per lookup. Decision: **LinkedHashMap** (O(1) lookup + free "order added" report, one evening sort). |
| Q5 – Generic ranking | **Completed `LeaderboardDemo` matches the expected output.** Generic bounded `RankBoard<T extends Rankable>`. The contract checker finds 0 violations for the chain, while the subtraction comparator inverts the ranking (2 antisymmetry / 9 transitivity violations) and the missing name tie-break makes the order depend on input. Comparable ~30 % faster, 3 stable sorts 1.4–2× slower. |
| Q6 – Triage | **Completed `Triage` matches the expected output.** 8-patient hand check matches. `poll` grows ~log n (135 → 203 ns), `add` stays O(1) on average. Starvation demonstrated (S5 never seen in 12 h); an aging rule gets them seen at 110 min. 5 FIFO queues are O(1). Suitable for 5,000 patients/day (~1 ms CPU/day). |
| Q7 – Redesign | Diagnosed the aging queue's O(n log n) rebuild per serve (842 µs, 400 KB at 100k). Redesign: 5 arrival-ordered buckets, comparing only their heads. Identical treatment order; **0.117 µs per serve, 7,220× faster**, constant 152 B per serve. |

## Assumptions and notes

* Timing numbers come from one machine (Apple Silicon Mac, JDK 21) and are means over repeated runs after warm-up. The first (smallest-size) rows of some tables still contain JIT warm-up noise, which is noted where it matters. Absolute values will differ on other hardware; the trends are the evidence.
* In Q3, the PDF's `if (this = = o)` is a text-extraction spacing artifact and was copied as `this == o`.
* "Complete the Code" programs (`Q3/Attendance.java`, `Q5/LeaderboardDemo.java`, `Q6/Triage.java`) are copied unchanged except for the TODO bodies. `Q3/before/` keeps the untouched version for the "before" run.
* For Q7, the solution redesigned is the aging (fair) triage queue introduced in Q6, as allowed ("select ONE solution").
