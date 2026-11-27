# Q5 – Derive a Generic Ranking Component from a Principle (K4)

## From the principle to a design (developed before implementation)

The principle asks for a *consistent* comparison: antisymmetric (if A ranks above B, then B ranks below A), transitive (A > B and B > C ⇒ A > C), and "equal items compare as equal". It also says the component will be reused for players, colleges and quiz teams. Four decisions follow from that:

1. **Comparator objects, not `Comparable<Team>`.** `Comparable` fixes *one* natural order inside the class. The same `Team` may need other orders (alphabetical for a fixture list, by goals for a "best attack" award), and `Player` and `College` would each need their own `compareTo` with the same logic copied. A separate `Comparator` keeps the *ranking rule* outside the data class, so it can be swapped, reused and tested on its own. `Leaderboard<T>` takes the rule as a constructor argument.
2. **Multi-field comparison from library combinators.** `comparingInt(points).reversed()`, then `thenComparing(comparingInt(goalDiff).reversed())`, then `thenComparing(name)`. Each key is compared with `Integer.compare` or `String.compareTo`, both of which are consistent total orders. Hand-written subtraction is never used.
3. **Generics.** `Leaderboard<T>` works for any type. My reusable variant `RankBoard<T extends Rankable>` uses a **bounded type**, so it can build the standard order itself for *anything* that exposes `primary()`, `secondary()` and `label()` (Team, Player, College).
4. **Wildcards.**
   * `Comparator<? super T>` in the given class lets a `Comparator<Object>` or `Comparator<Rankable>` rank `Team`s (a *consumer* of T).
   * `addAll(Collection<? extends T>)` lets a `List<FootballTeam>` be added to a `RankBoard<TeamR>` (a *producer* of T).
   * `best(List<? extends T>)` with `<T extends Comparable<? super T>>` shows both bounds together.

## Complete the Code – `LeaderboardDemo.java`

```java
// TODO 1
List<T> copy = new ArrayList<>(items);
copy.sort(order);
return copy;

// TODO 2
Comparator<Team> byRank = Comparator.comparingInt((Team t) -> t.points).reversed()
        .thenComparing(Comparator.comparingInt((Team t) -> t.goalDiff).reversed())
        .thenComparing(t -> t.name);
```

* **Expected output:** `[Eagles(9,6), Bisons(9,4), Panthers(9,4), Cobras(6,1)]`
* **Actual output:** `[Eagles(9,6), Bisons(9,4), Panthers(9,4), Cobras(6,1)]` ✔

`ranking()` copies first, so `items` keeps its insertion order and repeated calls are safe. `List.sort` is a stable merge sort (TimSort) with O(n log n) comparisons.

**Why Bisons is ranked above Panthers:** both have 9 points (tie at level 1) and goal difference 4 (tie at level 2). The third key decides: `"Bisons".compareTo("Panthers") < 0` because 'B' (66) < 'P' (80), so Bisons comes first alphabetically. Eagles is above both because its goal difference 6 > 4. Cobras is last on points (6 < 9).

## Testing the design

**Data set 1 – ties at every level** (10 teams: three on (12,5), two on (12,3), two on (9,3), …):

* `CORRECT` gives `[Bears, Lions, Tigers (12,5) | Hawks, Wolves (12,3) | Rams, Sharks (9,3) | Owls | Bulls, Foxes]`.
* The exhaustive contract checker (all pairs and triples): *0 antisymmetry violations, 0 transitivity violations, 0 distinct teams comparing equal*.
* Sorting a shuffled copy gives the identical list (`true`).

**Faulty comparator A – ignores the name tie-breaker:**

* Input order gives `[Lions, Tigers, Bears, Wolves, Hawks …]`; shuffled input gives `[Tigers, Lions, Bears, Hawks, Wolves …, Foxes, Bulls]`. **The ranking changes with the input order** (`same ranking both times? false`).
* The checker reports **6 pairs of distinct teams comparing equal**. That breaks "equal items must compare as equal" from the *other* side: unequal items compare as equal, so the published table depends on whichever team was entered first.

**Data set 2 – designed to break the subtraction comparator.** Teams with extreme bonus/penalty points: `Alpha = Integer.MAX_VALUE`, `Beta = −10`, `Gamma = 5`, `Delta = Integer.MIN_VALUE + 5`.

* `CORRECT`: `[Alpha, Gamma, Beta, Delta]`.
* `SUBTRACTION` (`b.points − a.points`): `[Delta, Gamma, Beta, Alpha]`, which is **completely inverted at the ends**.
* `compare(Alpha, Beta) = −10 − 2147483647` overflows to `+2147483639`, so Alpha appears *below* Beta.
* Checker: **2 antisymmetry and 9 transitivity violations**. With larger inputs, TimSort can even throw `IllegalArgumentException: Comparison method violates its general contract!`.

**Reuse:** the same `RankBoard` ranks players by goals then assists then name: `[Imran[9g,0a], Arjun[7g,4a], Ravi[7g,2a]]`.

## Correctness argument (reasoning, not output)

Let `c` = chain(k₁ desc, k₂ desc, name asc).

1. Each component comparator is a **total order**. `Integer.compare(x, y)` returns the sign of x − y computed *without overflow* (it uses `<` and `==`), so it is antisymmetric and transitive for all ints. `reversed()` flips the sign, which keeps both properties. `String.compareTo` is lexicographic order, which is a total order.
2. `thenComparing` builds the **lexicographic product**: c(a, b) is the first non-zero result among k₁, k₂, name. A lexicographic product of total orders is a total order:
   * *Antisymmetry:* the first non-zero key for (a, b) is the same key for (b, a) with the opposite sign.
   * *Transitivity:* if a < b and b < x, let i be the first key where a and b differ and j the first key where b and x differ. At position min(i, j), a ≤ b ≤ x with at least one strict inequality, and all earlier keys are equal. So a < x.
3. **Equality:** c(a, b) = 0 only if points, goal difference *and* name are all equal. Team names are unique in a league, so c returns 0 only for the same team, which is consistent with "equal items compare as equal".
4. Subtraction fails step 1: `b − a` wraps around when |b − a| > 2³¹ − 1, so its sign no longer reflects the order. Leaving out the name fails step 3.

## Alternative Concept and Complexity Analysis

**(a) `Team implements Comparable<Team>`** (`ComparableTeam.compareTo` in `RankingDesign`):

```java
public int compareTo(ComparableTeam o) {
    int c = Integer.compare(o.points, points);
    if (c != 0) return c;
    c = Integer.compare(o.goalDiff, goalDiff);
    return c != 0 ? c : name.compareTo(o.name);
}
// Collections.sort(list);  // natural order
```

**(b) Three stable sorts, least significant key first:** sort by name, then by goal difference (desc), then by points (desc). Because each sort is *stable*, ties at a later sort keep the order established by the earlier sorts, which produces the same result as the chain. The program confirms `identical to chain: true` at all sizes.

**Measured** (random teams, mean of 5 runs after warm-up):

| n | Comparator chain | Comparable | 3 stable sorts |
|---|---|---|---|
| 10,000 | 3.4 ms | 2.9 ms | 7.2 ms |
| 100,000 | 27.5 ms | 21.5 ms | 43.2 ms |
| 1,000,000 | 467.0 ms | 321.1 ms | 659.6 ms |

| Approach | Sorts performed | Time | Extra space | Reuse for players / colleges |
|---|---|---|---|---|
| Comparator chain | 1 | O(n log n) comparisons; each comparison up to 3 key extractions through lambdas | O(n) TimSort buffer + O(1) comparator objects | **Best**: one generic comparator (`RankBoard.standardOrder()`) for every `Rankable`, and new orders without touching the classes |
| `Comparable` | 1 | O(n log n); the fastest here (≈ 30 % less), because the keys are read directly without lambda indirection | O(n) buffer | Poor: every class re-implements `compareTo`, and only one order per class |
| 3 stable sorts | **3** | 3 × O(n log n); measured about 1.4–2.1× the chain | O(n) buffer per sort (reused) | Medium: easy to read, but the order of sorts is fragile, and it only works with a *stable* sort |

**Conclusion:** `Comparable` is about 30 % faster, but the comparator chain is the right engineering choice for a component that must rank teams, players, colleges and quiz teams, and its cost is still O(n log n) with one sort. The three-sort approach is never better: it does 3× the sorting work.
