# Q1 – Building Text Under Load: String, StringBuilder or StringBuffer? (K4)

## Prediction (made before running)

* **`String` with `+` in a loop.** A `String` is immutable, so `s = s + field + "|"` cannot change `s`. Each iteration allocates a **new** String and copies every character built so far. For a ticket of n fields of average length L, the copies are L + 2L + … + nL = O(n²·L) characters, and n intermediate Strings become garbage at once. Prediction: negligible for a short ticket, noticeably slower for 50 fields, and **dramatically** slower (quadratic) for thousands of fields.
* **`StringBuilder`** keeps one resizable `byte[]`. `append` copies only the new characters, and the array doubles when full (amortised O(1) per character), so total work is O(n·L) and there is 1 final String per ticket. Prediction: linear, and the fastest.
* **`StringBuffer`** is the same algorithm, but every method is `synchronized`. With one thread the lock is uncontended and the JIT can elide or bias it. Prediction: within about 10–30 % of `StringBuilder`.
* `String.join`/`StringJoiner` use a builder internally, so they should be close to `StringBuilder`. `String.format` parses the format string on every call, so it should be several times slower.

## Workloads designed

| Workload | Shape | Why it was chosen |
|---|---|---|
| W1 | 1 ticket × 8 fields | The everyday single booking, where "does it matter?" is tested at its most favourable for `+` |
| W2 | 10,000 tickets × 50 fields | A full day of e-tickets: many medium strings (the PDF's contrasting load) |
| W3 *(own)* | 20 tickets × 5,000 fields | A group/charter manifest (whole-train booking). One very long ticket exposes the O(n²) growth inside a single ticket |

## Measured results (`TicketBuilderBenchmark.java`, real numbers in `output.txt`)

Time is the mean of 5 timed runs after 1 warm-up (W1: 2,000 runs). *Alloc* is the increase in used heap measured with `Runtime` before and after the timed loop (after `System.gc()`). It is approximate: heap grows in TLAB-sized chunks, and 0.04 MB is the measurement floor.

| Approach | W1 time (ms) | W2 time (ms) | W2 alloc (MB) | W3 time (ms) | W3 alloc (MB) | Intermediate Strings, W2 / W3 (estimate) |
|---|---|---|---|---|---|---|
| `String +` in loop | 0.0031 | 7.63 | 43.4 | **110.40** | **124.6** | 500,000 / 100,000 |
| `StringBuilder` | 0.0021 | 2.12 | 17.5 | **0.34** | 3.9 | 10,000 / 20 |
| `StringBuffer` | 0.0021 | 2.15 | 18.0 | 0.35 | 3.9 | 10,000 / 20 |
| `String.join` | 0.0027 | 2.69 | 13.0 | 0.55 | 2.4 | 10,000 / 20 |
| `StringJoiner` | 0.0028 | 2.40 | 11.0 | 0.47 | 2.3 | 10,000 / 20 |
| `String.format` | 0.0058 | 21.67 | 25.3 | 3.95 | 11.6 | 500,000 / 100,000 |

The intermediate-String count is an estimate. `s = s + x + "|"` is compiled (JDK 9+) as one `invokedynamic` concatenation, so it produces 1 new String per field, each a full copy of the text so far. Builders produce 1 String per ticket. `format` produces 1 String per field.

## Prediction vs observation

| Prediction | Observed | Verdict |
|---|---|---|
| `+` negligible for a short ticket | W1: 3.1 µs vs 2.1 µs, i.e. 1 µs per ticket | **Confirmed.** At 8 fields, `+` is fine |
| `+` clearly slower at 50 fields | W2: 3.6× slower and 2.5× the allocation | **Confirmed** |
| `+` quadratic for long text | W3: **328× slower** (110 ms vs 0.34 ms) and **32× the allocation**. 100× more fields per ticket than W2 made `+` ~14× slower *per ticket*, while `StringBuilder` time per field stayed flat | **Confirmed**: the cost depends on the square of the length |
| `StringBuffer` 10–30 % slower | 0–4 % slower single-threaded | **Partly wrong.** Uncontended locks are almost free on JDK 21, so the predicted penalty did not appear |
| `format` several times slower | 10× slower than the builder in W2 and 12× in W3 | **Confirmed** |

## Design decision – multithreaded booking server

Shared-log experiment (real output): 4 threads each append 50,000 lines to one shared object.

| Trial | Expected length | `StringBuilder` | `StringBuffer` |
|---|---|---|---|
| 1 | 4,800,000 | 4,749,624 – **CORRUPTED** (lines lost) | 4,800,000 – OK |
| 2 | 4,800,000 | 4,756,392 – **CORRUPTED** | 4,800,000 – OK |
| 3 | 4,800,000 | 4,753,392 – **CORRUPTED** | 4,800,000 – OK |

* **Per-ticket text → `StringBuilder`.** Each ticket is built by one request thread in a local variable, so the object never escapes its thread and needs no lock. It was the fastest in every workload.
* **Shared booking log → not a bare `StringBuilder`.** Concurrent `append`s race on the internal `count` and array, and about 1 % of the characters were lost in every trial. `StringBuffer` was correct in all 3 trials because each `append` holds the object's lock. It is my recommendation where a shared in-memory text buffer is truly needed. Note that `StringBuffer` makes each *call* atomic, not a *sequence* of calls: `log.append(pnr).append(status)` from two threads can interleave between the two calls. Build each full line in a local `StringBuilder` and append it to the shared log in **one** call.
* At server scale, a better design is a `ConcurrentLinkedQueue<String>` or a logging framework writing to a file. Threads then do not contend on one growing buffer.

## Alternative Concept and Complexity Analysis

**Solutions with the alternatives:**

```java
String ticket = String.join("|", fields) + "|";                 // join
StringJoiner j = new StringJoiner("|", "", "|"); for (String f : fields) j.add(f);  // joiner
for (String f : fields) sb.append(String.format("%s|", f));     // format
```

**Derivation.** Let a ticket have n fields of average length L, so the final length is N = nL.

* Repeated `+`: iteration i copies the existing (i−1)L characters plus L new ones into a fresh String. Total copied = L(1 + 2 + … + n) = L·n(n+1)/2 = **O(n²L)**. It also creates n intermediate Strings of sizes L, 2L, …, nL, i.e. O(n²L) bytes of garbage, even though only O(nL) are live at any time.
* `StringBuilder`: each character is copied once on `append`, plus array regrowth. Capacity doubles, so regrowth copies at most N + N/2 + N/4 + … < 2N characters. Total **O(nL) = O(N)**. Garbage: O(log N) discarded arrays + the 1 final String.
* `String.join` / `StringJoiner`: also **O(N)**. Since JDK 17 `String.join` pre-computes the exact size and copies once, so it does no regrowth. `StringJoiner` is similar. Both keep the n input Strings alive in an array: O(n) references.
* `String.format`: O(N) characters in total, but each call parses the pattern and creates a `Formatter` and a String, so the constant factor is much larger. It also creates n intermediate Strings, like `+` but without the quadratic copying.

| Approach | Time to build n fields | Extra space (intermediate objects) | Measured W3 |
|---|---|---|---|
| `+` in loop | O(n²L) | O(n²L) garbage, n Strings | 110.4 ms, 124.6 MB |
| `StringBuilder` / `StringBuffer` | O(nL) | O(nL) + O(log N) regrown arrays | 0.34 ms, 3.9 MB |
| `String.join` | O(nL) | O(nL) – sized exactly | 0.55 ms, 2.4 MB |
| `StringJoiner` | O(nL) | O(nL) + array of n parts | 0.47 ms, 2.3 MB |
| `String.format` per field | O(nL), large constant | n Strings + n Formatters | 3.95 ms, 11.6 MB |

**Recommendation:**

* Use `StringBuilder` (or `String.join` when the fields are already in an array) for each ticket.
* Use `StringBuffer` only where one text buffer is genuinely shared between threads, and append whole lines.
* Never use `+` inside a loop.
* Reserve `String.format` for one-off formatting of a single field, such as the fare `"%.2f"`.
