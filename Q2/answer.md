# Q2 – When Is ArrayList an Appropriate Engineering Choice? (K4)

## Experiment design

`PlaylistBenchmark.java` builds a playlist of `Song` records at three sizes (1,000, 10,000, 100,000) and times each operation separately, so one cost does not hide another.

| Operation | How it is exercised |
|---|---|
| add at end | k × `add(song)` |
| insert at front / remove at front | k × `add(0, song)` / `remove(0)` |
| insert / remove in the middle | k × `add(size/2, song)` / `remove(size/2)` |
| get k-th | k × `get(random index)` – "jump to the k-th song" |
| iterate | 20 full passes with for-each (the iterator) |

* k = min(1,000, n/2) operations per measurement.
* Each measurement is repeated 5 times on a freshly filled list; the first repetition is discarded as JIT warm-up and the mean of the other 4 is reported.
* The 1,000-element rows run first, so they still contain some warm-up noise (for example `get` at 120 ns).

## Results (ns per operation, `output.txt`)

| Operation | n | ArrayList | LinkedList | LinkedList ÷ ArrayList |
|---|---|---|---|---|
| add at end | 1k / 10k / 100k | 36.8 / 26.3 / 27.5 | 32.5 / 25.1 / 28.4 | ≈ 1 (same) |
| insert at front | 1k / 10k / 100k | 113.6 / 559.8 / **6,958.9** | 75.6 / 34.1 / **28.5** | ArrayList 244× slower at 100k |
| remove at front | 1k / 10k / 100k | 71.8 / 482.4 / **6,886.2** | 44.8 / 21.0 / **15.1** | ArrayList 456× slower at 100k |
| insert in middle | 1k / 10k / 100k | 81.2 / 297.0 / **3,547.4** | 1,096.1 / 8,860.0 / **84,773.5** | LinkedList 24× slower |
| remove from middle | 1k / 10k / 100k | 53.4 / 244.4 / 3,447.8 | 579.7 / 7,642.7 / 81,372.9 | LinkedList 24× slower |
| get k-th (random) | 1k / 10k / 100k | 120.0 / 47.8 / **48.9** | 466.3 / 4,229.1 / **43,420.4** | LinkedList **888×** slower |
| iterate (per element) | 1k / 10k / 100k | 19.2 / 3.4 / 2.1 | 8.4 / 2.8 / 2.6 | ≈ 1 |

What the numbers show:

* **ArrayList front operations** grow ×10 with n (560 → 6,959 ns), which is O(n): every insertion or removal at index 0 shifts all elements with `System.arraycopy`.
* **LinkedList middle operations** also grow linearly, and *faster* than ArrayList's, even though linking a node is O(1). The O(n/2) *walk to the middle* chases pointers across the heap (cache misses). ArrayList's shift is one bulk memory copy that the CPU streams very fast.
* **`get(k)`** is flat at about 48 ns for ArrayList, which is O(1) index arithmetic; the 48 ns mostly comes from `Random`. LinkedList walks from the nearer end, so its cost is O(n): 466 → 4,229 → 43,420 ns.
* **Iteration** is similar for both, because the nodes were allocated consecutively in this test. In a real long-lived app, nodes scattered by GC would make LinkedList iteration slower. A node is also 24 B (header + item + next + prev) against a 4 B array slot.

## Operating boundary – when is ArrayList reasonable?

**ArrayList is a reasonable choice** when the playlist workload is dominated by:

* appending songs,
* jumping to the k-th song,
* playing straight through,
* occasionally inserting or removing in the middle of lists of up to a few thousand songs.

That is exactly the normal "playlist" pattern. Even at 10,000 songs, a middle insertion costs 0.3 µs, which no user can notice, and the list uses about 6× less memory per song than LinkedList.

**I would stop recommending it** when operations at the *front* become frequent on large lists. At 100,000 songs each `add(0)` or `remove(0)` costs about 7 µs. A queue that does a million of them per session costs seconds, as the Up Next experiment below shows (4.6 s). The rule of thumb from my data: once front insertions/removals are a regular operation and n exceeds about 10,000, ArrayList is the wrong structure. LinkedList is rarely the answer either, because of its O(n) `get(k)` and slow middle access.

## Changed condition – the "Up Next" queue

Workload: start with 10,000 songs, then 1,000,000 random operations (40 % `addLast`, 20 % `addFirst`, 40 % `pollFirst`). Mean of 3 timed runs.

| Structure | Time |
|---|---|
| ArrayList (`add(0)`, `remove(0)`) | **4,616 ms** |
| LinkedList (`addFirst`, `addLast`, `pollFirst`) | 21.1 ms |
| **ArrayDeque** | **10.3 ms** |

**Re-evaluation:** the original recommendation (ArrayList) is **wrong for Up Next**: it is 450× slower than ArrayDeque. ArrayDeque is a circular array: the head and tail indices move, and no elements are shifted, so both ends are amortised O(1). It is also 2× faster than LinkedList because it allocates no node per song and has better cache locality. The app should therefore use **two structures**: `ArrayList<Song>` for the editable playlist (random access, position edits) and `ArrayDeque<Song>` for the Up Next queue (both ends, no indexing needed).

## Alternative Concept and Complexity Analysis

| Operation | ArrayList | LinkedList | ArrayDeque | Plain array + manual shift |
|---|---|---|---|---|
| add at end | O(1) amortised | O(1) | O(1) amortised | O(1) if spare capacity, else O(n) copy to a bigger array |
| insert at front | O(n) shift | O(1) | O(1) amortised | O(n) shift |
| insert in middle | O(n) shift (bulk copy) | O(n) walk + O(1) link | not supported (no index) | O(n) shift |
| get k-th | **O(1)** | O(n) walk (≤ n/2) | not supported (only ends) | **O(1)** |
| iterate | O(n) | O(n) | O(n) | O(n) |
| space per element | 1 reference (4 B) + up to 50 % spare capacity | node of ~24 B (header, item, next, prev) | 1 reference, power-of-two capacity | 1 reference, fixed capacity |

**Plain array with manual shifting:**

```
ALGORITHM InsertAtFront(arr, size, song)
1. IF size = length(arr) THEN arr ← copy of arr with double capacity END IF
2. FOR j ← size DOWNTO 1 DO  arr[j] ← arr[j-1]  END FOR
3. arr[0] ← song
4. size ← size + 1
```

Its Big-O is identical to ArrayList: O(n) front/middle insertion and O(1) `get`. The measured cost was 4.3 µs → 9.9 µs → **105.8 µs** per front insertion. That is **15× slower than ArrayList** at 100,000 songs (6.96 µs), because the element-by-element Java loop cannot match `System.arraycopy`'s bulk copy. The programmer also has to manage capacity, size and bounds by hand. ArrayList is exactly this design done correctly, so there is no reason to write it manually.
