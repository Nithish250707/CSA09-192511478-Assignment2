# Q4 – Choosing a Map for a Pharmacy Inventory (K4)

## Design reasoning before implementation

| Map | put | get | iteration | Natural iteration order |
|---|---|---|---|---|
| `HashMap` | O(1) average | O(1) average | O(n + capacity) | none (hash-bucket order) |
| `TreeMap` | O(log n) | O(log n) | O(n) | **sorted by key**, so the code-order report comes for free |
| `LinkedHashMap` | O(1) average (+ doubly-linked list update) | O(1) average | O(n) | **insertion order**, so the "order added" report comes for free |

Extra work when the "wrong" map is used for a report:

* **Code-order report from a `HashMap`/`LinkedHashMap`:** copy the n keys into a list and sort them, O(n log n) time and O(n) extra space, every evening.
* **"Order added" report from a `HashMap`/`TreeMap`:** the map has no record of the order at all. A separate `List<Medicine>` (an "added log") must be kept next to the map and updated on every insert, so it costs space all day, not just at report time.
* `TreeMap` makes **every lookup** O(log n) to save a once-a-day sort.

## Implementation (`PharmacyMaps.java`)

* Medicines `MED000000…` are generated and **added in random code order** (shuffled), so insertion order ≠ code order.
* For each map: put all, then 100,000 random `get(code)`, then both reports.
* Both reports are checked against independently computed expected lists (`code ok`, `added ok`).
* Sizes: 1,000, 10,000, 100,000, 1,000,000.
* Each figure is the mean of 5 timed runs, after 1 warm-up run and a global JIT warm-up pass (an earlier run without the global warm-up gave noisy, order-dependent numbers, which were discarded).

## Measured results (ms, `output.txt`)

| n | Map | put all | **100k gets** | code-order report | added-order report | Orders correct |
|---|---|---|---|---|---|---|
| 1,000 | HashMap | 0.02 | 1.56 | 0.03 (sort) | 0.00 (log list) | ✔ ✔ |
| 1,000 | TreeMap | 0.10 | 12.10 | 0.02 (natural) | 0.00 (log list) | ✔ ✔ |
| 1,000 | LinkedHashMap | 0.04 | 2.56 | 0.05 (sort) | 0.00 (natural) | ✔ ✔ |
| 10,000 | HashMap | 0.26 | 2.15 | 0.43 | 0.02 | ✔ ✔ |
| 10,000 | TreeMap | 1.50 | 15.12 | 0.14 | 0.02 | ✔ ✔ |
| 10,000 | LinkedHashMap | 0.34 | 1.85 | 0.93 | 0.02 | ✔ ✔ |
| 100,000 | HashMap | 5.71 | 3.90 | 5.10 | 0.72 | ✔ ✔ |
| 100,000 | TreeMap | 21.69 | 21.34 | 1.44 | 0.60 | ✔ ✔ |
| 100,000 | LinkedHashMap | 5.37 | 1.66 | 14.94 | 0.14 | ✔ ✔ |
| 1,000,000 | HashMap | 71.85 | 5.58 | 92.97 | 10.90 | ✔ ✔ |
| 1,000,000 | TreeMap | 523.49 | 58.40 | 66.78 | 10.51 | ✔ ✔ |
| 1,000,000 | LinkedHashMap | 114.28 | 5.03 | 276.72 | 34.27 | ✔ ✔ |
| 1,000,000 | Hashtable | 91.06 | 5.73 | 56.47 | 10.48 | ✔ ✔ |

Observations:

* **Lookups:** HashMap and LinkedHashMap stay at 16–56 ns per `get` from 1k to 1M medicines, which is effectively constant (the slow rise is cache misses). TreeMap grows with log n: 121 → 151 → 213 → 584 ns per `get`, about **10× slower** than hashing at every size.
* **Code-order report:** TreeMap is cheapest (it just walks the tree). Sorting the keys of a LinkedHashMap was the most expensive (277 ms at 1M). Its keys come out in fully random order, while HashMap's bucket order is already partly sorted for these keys, which TimSort exploits.
* **Added-order report:** LinkedHashMap is natural. The other maps need the separately maintained log list, which costs O(n) extra memory.
* All three maps produced **correct** orders in every run, given the extra work (sort or log list) where needed.

## Scalability inquiry

**Acceptability criterion (defined):** one stock lookup must finish in **≤ 1 µs** of CPU time. 100,000 lookups (several days of pharmacy traffic) must then take ≤ 100 ms, so lookups can never be what the pharmacist waits for. The evening reports must finish in **≤ 1 s**.

* At a realistic hospital formulary of 10,000 medicines, every map meets this. Even TreeMap needs 0.15 µs per lookup.
* At 1,000,000 codes (a central warehouse), HashMap and LinkedHashMap take 0.05 µs and TreeMap 0.58 µs. All still pass, but TreeMap uses more than half the budget.

**Decision: deploy `LinkedHashMap<String, Medicine>`.**

* Lookup is O(1) and measured as fast as HashMap (5.0 ms per 100k at 1M).
* It produces the "order added" report directly, with no second list to keep in step.
* The code-order report needs one sort per evening: 0.93 ms at 10k medicines, far below the 1 s budget. Even at 1M it is 0.28 s.

Where the remaining cost comes from:

* in a lookup: computing `String.hashCode()` of the code (cached after the first call), one `equals()` in the bucket, and a cache miss to fetch the entry;
* in the evening: the O(n log n) sort of keys for the code report;
* in memory: about 40 B per `LinkedHashMap` entry (hash node + before/after links).

If code-order range queries ("all medicines from MED0100 to MED0199") were needed during the day, a `TreeMap` *index* would be added next to it.

**Why not `Hashtable`?**

* It is a legacy class (JDK 1.0) and does not follow the modern Map design: it extends `Dictionary`, has `Enumeration`-based methods, and allows no `null` keys or values.
* **Every** method is `synchronized`. In my single-threaded test this cost nothing measurable, because uncontended locks are cheap. Under many pharmacist threads, though, the single lock serialises all access.
* Locking each call also does not make compound actions such as "check stock then decrement" atomic.
* New code uses `HashMap` (single thread) or `ConcurrentHashMap` (fine-grained, lock-free reads, and atomic `compute`/`merge` for stock updates).

## Alternative Concept and Complexity Analysis – sorted array + `Arrays.binarySearch`

```
ALGORITHM BuildIndex(meds)
1. SORT meds by code                               // O(n log n), once
2. codes[i] ← meds[i].code for i in 0..n-1

ALGORITHM Lookup(code)
1. i ← BINARYSEARCH(codes, code)                   // O(log n)
2. IF i < 0 THEN RETURN "not stocked" END IF
3. RETURN meds[i]

ALGORITHM AddMedicine(m)
1. i ← -(BINARYSEARCH(codes, m.code) + 1)          // insertion point
2. shift codes[i..n-1] and meds[i..n-1] right by one   // O(n)
3. codes[i] ← m.code; meds[i] ← m
```

| Structure | Lookup | Insert one medicine | Code-order report | Added-order report | Space |
|---|---|---|---|---|---|
| Sorted array + binary search | O(log n) – measured 45 ms per 100k at 1M (0.45 µs per lookup) | **O(n)** – measured 6.9 µs at n = 100k (binary search + `arraycopy` shift) | **O(n)**, already sorted | needs a separate log, O(n) | 2 arrays of n references (~8 B per medicine): the most compact |
| HashMap | O(1) – 0.06 µs | O(1) | O(n log n) sort | log list, O(n) | ~32–48 B per entry |
| TreeMap | O(log n) – 0.58 µs | O(log n) | O(n), already sorted | log list, O(n) | ~40 B per entry |

The sorted array is the most memory-efficient option and gives the code report for free. Its lookup is O(log n), similar to TreeMap (0.45 vs 0.58 µs at 1M), and every new medicine costs an O(n) shift. It fits a formulary that is loaded once and rarely changed. The pharmacy adds medicines during the day, so a map is the better fit.
