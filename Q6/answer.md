# Q6 – Priority, Order and Fairness in a Queue (K5)

## Design (developed without supplied code)

* **Why a plain `Queue` (LinkedList/ArrayDeque) is not enough.** A FIFO queue serves strictly in arrival order. A severity-1 (critical) patient who arrives after ten severity-5 patients would wait for all ten, which breaks the rule "always see the most severe patient next". Finding the most severe patient in a FIFO queue means scanning all n patients on every call.
* **How a `PriorityQueue` with a Comparator enforces the rule.** The queue is a binary min-heap ordered by the comparator. With `comparingInt(severity)`, the smallest severity number (most critical) is always at the root, so `poll()` returns it in O(log n).
* **How arrival order is kept among equal severities.** A heap is *not* stable: two S3 patients can come out in either order. The comparator therefore needs a second key, a strictly increasing **arrival counter** (`thenComparingLong(arrival)`). No two patients share an arrival number, so the order (severity, arrival) is a total order and "first come, first served within a severity" is guaranteed by the comparator itself, not by luck.
* **Removing the next patient and displaying the waiting list.**
  * `poll()` removes and returns the root (`null` when nobody is waiting).
  * To *display* the list, copy the queue and sort the copy with the same comparator, O(n log n). Never print the queue directly (see below), and never `poll()` everyone just to show them.

## Complete the Code – `Triage.java`

```java
private final PriorityQueue<Patient> queue = new PriorityQueue<>(
        Comparator.comparingInt((Patient p) -> p.severity).thenComparingLong(p -> p.arrival));
```

* **Expected:** `Meena(S1) Kumar(S1) Lata(S2) Ravi(S3) John(S3) Asha(S5)`
* **Actual:** `Meena(S1) Kumar(S1) Lata(S2) Ravi(S3) John(S3) Asha(S5)` ✔

**How the counter keeps equal severities in arrival order:** `admit` stamps each patient with `counter++` (Ravi = 0, Meena = 1, John = 2, Asha = 3, Kumar = 4, Lata = 5). Meena and Kumar are both S1; the tie-break compares 1 < 4, so Meena comes first. Ravi (0) and John (2) are both S3, so Ravi comes first. A counter is used instead of a clock time because two patients can be admitted within the same millisecond, while the counter is always unique.

## Correctness check – 8 patients, worked out by hand

Arrivals (counter 0–7): Anil S4, Bala S2, Chitra S5, Deepa S2, Ezhil S1, Farah S4, Gopi S3, Hema S1.

Hand derivation:

* S1: Ezhil (4), Hema (7)
* S2: Bala (1), Deepa (3)
* S3: Gopi (6)
* S4: Anil (0), Farah (5)
* S5: Chitra (2)

Expected treatment order: **Ezhil, Hema, Bala, Deepa, Gopi, Anil, Farah, Chitra**.

| What was printed | Output |
|---|---|
| `PriorityQueue.toString()` | `[Ezhil(S1), Hema(S1), Gopi(S3), Bala(S2), Deepa(S2), Chitra(S5), Farah(S4), Anil(S4)]` |
| Sorted copy (display) | `[Ezhil(S1), Hema(S1), Bala(S2), Deepa(S2), Gopi(S3), Anil(S4), Farah(S4), Chitra(S5)]` |
| Order of `poll()` | `Ezhil(S1) Hema(S1) Bala(S2) Deepa(S2) Gopi(S3) Anil(S4) Farah(S4) Chitra(S5)` – **matches the hand order: `true`** |

**Why printing the PriorityQueue does not show treatment order.** `toString()` (and the iterator) walk the internal **heap array** from index 0 upwards. A heap only guarantees that each parent ≤ its children (`a[i] ≤ a[2i+1], a[2i+2]`); siblings and different subtrees are unordered. Gopi (S3) appears before Bala (S2) because Gopi is the root's right child while Bala sits deeper on the left. Only repeated `poll()` (or a sorted copy) gives the true order.

## Growth investigation

**Prediction (before experiments):**

* `add` = sift-up = O(log n) in the worst case.
* `poll` = move the last element to the root and sift it down = O(log n), because it always travels about the full height of the heap.
* Going from 1,000 to 1,000,000 patients (log₂ 10 → 20) should therefore roughly double both costs.

**Measured** (queue pre-filled to n, then 100,000 admits and 100,000 serves, mean of 5 runs):

| n waiting | admit (ns) | serve (ns) | log₂ n |
|---|---|---|---|
| 1,000 | 20.9 | 135.0 | 10.0 |
| 10,000 | 15.8 | 140.7 | 13.3 |
| 100,000 | 14.8 | 136.4 | 16.6 |
| 1,000,000 | 14.5 | 202.8 | 19.9 |

* **Serve** grew slowly, from 135 to 203 ns, consistent with O(log n). The jump at 1M is larger than log n alone predicts, because the heap no longer fits in the CPU cache.
* **Admit** stayed *flat* (about 15 ns), which disproves my "O(log n)" prediction *for the average case*. A new patient with a random severity and the *largest* arrival number usually belongs near the bottom of the heap, so sift-up stops after about 1–2 levels (the expected cost of a random heap insertion is O(1)). The O(log n) bound is only reached when a critical patient arrives and must climb to the root.

## Inquiry – starvation and a fair rule

**Why a severity-5 patient can wait forever.** The comparator compares severity *first*. While at least one patient with severity < 5 is waiting, `poll()` never returns the S5 patient, however long they have waited. If severe cases arrive faster than the doctor clears them, there is *always* someone more urgent, so the S5 patient is starved.

**Simulation** (one doctor, one patient seen every 10 min; S1/S2 patients arrive every 9 min; Zara (S5) arrives at t = 0; 12 simulated hours):

| Rule | Zara is seen |
|---|---|
| Strict severity | **NEVER**: still waiting after 12 h, with 9 patients ahead |
| Aging: effective level = max(1, severity − ⌊wait / 30 min⌋) | **t = 110 min** |

**Fair rule designed:** every 30 minutes of waiting raises a patient one level, but never above level 1. Ties are still broken by arrival. At 110 min Zara's effective level is 5 − 3 = 2. She now ties with the S2 arrivals and wins on her earlier arrival.

Properties:

* A genuine S1 is never overtaken by a lower-severity patient who has waited less than 30 × (severity − 1) minutes.
* Every patient is guaranteed to reach level 1 within 2 hours, which bounds the waiting time.
* The interval (30 min) is a clinical policy parameter.

Implementation consequence: with aging, the priority **changes with time**, while a `PriorityQueue` assumes keys do not change after insertion. My Q6 implementation therefore rebuilds the queue with a comparator for the current time before each serve. This is correct but costs O(n log n) per serve. Q7 diagnoses and redesigns exactly this.

## Deployment decision – a city hospital with 5,000 patients a day

* 5,000 patients per day ≈ 3.5 arrivals per minute, or about 1 every 17 s.
* Even if *all* 5,000 were waiting at once, a serve costs about 0.14–0.2 µs and an admit about 0.02 µs (measured at n = 1,000–1,000,000). That is 5,000 × 0.2 µs ≈ **1 ms of CPU per day**.
* Memory: 5,000 × ~40 B ≈ 200 KB.

**Yes, the data structure can serve the hospital with a margin of roughly a million times.** The real bottleneck is the number of doctors, not the queue. The conditions:

1. Add the aging rule. Without it, the starvation shown above is a clinical-safety failure, not a performance one.
2. With several triage desks or doctors on separate threads, use a `PriorityBlockingQueue` (or synchronise `admit`/`next`). `PriorityQueue` is not thread-safe.
3. Persist the queue, so that a restart does not lose waiting patients.

## Alternative Concept and Complexity Analysis

**(a) Sorted `LinkedList`:** admit walks to the first patient who should come *after* the newcomer and inserts there; serve is `pollFirst()`.

**(b) Array of five FIFO queues** (`ArrayDeque[6]`, index = severity): admit is `lv[severity].addLast(p)`; serve takes the first non-empty queue from 1 to 5. Arrival order within a severity comes free from FIFO, with **no counter needed**.

**Measured** (n patients waiting, then 20,000 admits and 20,000 serves, mean of 3):

| n | Design | admit (ns) | serve (ns) |
|---|---|---|---|
| 1,000 | PriorityQueue | 22.1 | 155.2 |
| 1,000 | sorted LinkedList | 31,881 | 4.9 |
| 1,000 | 5 FIFO queues | 34.0 | 30.3 |
| 10,000 | PriorityQueue | 23.3 | 167.0 |
| 10,000 | sorted LinkedList | 46,399 | 3.5 |
| 10,000 | 5 FIFO queues | 4.3 | 2.3 |
| 100,000 | PriorityQueue | 21.0 | 180.2 |
| 100,000 | sorted LinkedList | **198,735** | 2.2 |
| 100,000 | 5 FIFO queues | **7.4** | **1.7** |

(The 1,000 row of the FIFO design includes JIT warm-up.)

| Design | Admit | Serve | Space | Notes |
|---|---|---|---|---|
| PriorityQueue (heap) | O(log n) worst, O(1) average | O(log n) | O(n) array | works for any number of priority levels |
| Sorted LinkedList | **O(n)** walk (0.2 ms per admit at 100k) | O(1) | O(n) nodes (~24 B per node + patient) | the list is always in display order, but admission is too slow at scale |
| 5 FIFO queues | **O(1)** | **O(k) = O(1)** for k = 5 levels | O(n) + 5 deques | the fastest, and naturally stable; works only because severity has a small fixed range (1–5) |

The bucketed design wins because the key has only 5 possible values. A heap is needed only when priorities form a large or continuous range (e.g. a numeric risk score).
