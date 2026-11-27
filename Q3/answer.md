# Q3 – Engineer the hashCode–equals Contract (K4)

> Note: the PDF shows `if (this = = o)`. That is a spacing artifact of the PDF text and does not compile, so it was copied as `this == o`. Nothing else in the given code was changed.

## Complete the Code – before and after TODO 1

`before/Attendance.java` is the given code with TODO 1 left empty. `Attendance.java` is the same file with only this line added:

```java
@Override public int hashCode() { return regNo.hashCode(); }
```

| Run | Output |
|---|---|
| Before TODO 1 | `Unique students: 3` |
| After TODO 1 | `Unique students: 2` (**matches the expected output**) |

**Why they differ.** Before the fix, `Student` inherits `Object.hashCode()`, which is based on object identity, so the two `new Student("192311001", "Arun")` objects get different hash codes. `HashSet.add` uses the hash code to choose a bucket. The second Arun goes into a *different* bucket, where there is nothing to compare against, so `equals()` is **never called** and the duplicate is stored. After the fix, equal `regNo` gives an equal hash code and the same bucket; `equals()` then reports the duplicate and `add` returns `false`.

## Three designed tests – predictions recorded before execution

Each prediction was written into `ContractExperiments.java` *before* the first run, and the program prints prediction vs actual.

| Test | Design | Predicted size | Reason for the prediction | Actual |
|---|---|---|---|---|
| **T1** – appears to work | Broken class. The gate software re-uses the **same object** for a repeated tap (`add(arun)` twice) | 2 | Same reference → same identity hash → same bucket, and `==` matches | **2** ✔ |
| **T2** – exposes duplicates | Broken class. 6 scans of 3 students, each scan creates a **new** object (what a real card reader does) | 6 | Every object has its own identity hash, so all 6 land in different buckets | **6** ✔ |
| T2′ – same scans, fixed class | `FixedStudent` with `hashCode = regNo.hashCode()` | 3 | Equal keys → same bucket → `equals` detects them | **3** ✔ |
| **T3a** – own test: mutate key after insertion | `MutableStudent` (correct `hashCode`, but `regNo` not final). Add it, then change `regNo` to 192311099, then add a new object with 192311099 | 1 | I expected the set to "notice" the same student | **2** ✘ |
| **T3b** – own test: `TreeSet` with comparator inconsistent with `equals` | `TreeSet` ordered by **name**; two different students both named Priya, plus Rahul | 3 | Three different register numbers | **2** ✘ |

T1 shows why the bug survives casual testing: when the same object is re-added, everything looks correct. T2 is what happens in production, where every scan builds a new object. The output also shows the contradiction directly: `equals` is `true` but the hash codes are `804564176` vs `1421795058`, and `contains(new BrokenStudent("192311001"))` is `false`.

## Inquiry – comparing prediction and result

**Why duplicates appear (T2).** The contract says: *if `a.equals(b)` then `a.hashCode() == b.hashCode()`*. The broken class violates it. `HashSet` trusts the hash code to decide *where to look*, so a broken hash code means `equals` is never even consulted.

**How `HashSet` locates an element** (it is a `HashMap` with dummy values):

1. Compute `h = key.hashCode()`, then spread it: `h ^ (h >>> 16)`.
2. Bucket index = `h & (table.length − 1)`.
3. Walk that bucket's chain (or tree, if more than 8 entries collide). For each entry, first compare the stored hash. Only if the hashes are equal is `key == e.key || key.equals(e.key)` called.
4. If a match is found, `add` returns `false`; otherwise a new node is appended.

So `hashCode()` selects the bucket and `equals()` confirms identity *within* the bucket. Both must agree.

**Changing a field used in `hashCode()` after insertion (T3a – my prediction was wrong).**

* The object stays in the bucket computed from its **old** hash. Hash sets never re-hash existing entries.
* `contains(m)` now hashes the **new** regNo and looks in a different bucket, so it returns `false`.
* Adding the "same" student again therefore succeeds, and the size becomes 2.
* `remove(m)` returned `true`, but it removed the *second* object (which lives in the new bucket and is `equal` to m). The original entry is still in the old bucket, and no lookup can ever reach it. It is effectively a memory leak and a phantom attendee in any iteration.

I predicted 1 because I had reasoned only about `equals`. The result showed that the *location* is fixed at insertion time.

**TreeSet inconsistent with equals (T3b – my prediction was wrong).** `TreeSet` never calls `equals` or `hashCode`. It treats `compare(a, b) == 0` as "same element". Comparing by name makes two different students called Priya the same element, so the second is **silently dropped**: an absent record for a student who was present. The comparator for a de-duplication set must compare the identity key (`regNo`).

## The corrected Student class and how it is verified

```java
final class Student {
    private final String regNo;          // final: the key cannot change after insertion
    private final String name;
    Student(String regNo, String name) { this.regNo = Objects.requireNonNull(regNo); this.name = name; }
    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Student)) return false;
        return regNo.equals(((Student) o).regNo);
    }
    @Override public int hashCode() { return regNo.hashCode(); }   // same field as equals
    @Override public String toString() { return regNo + " " + name; }
}
```

* `hashCode` uses exactly the fields `equals` uses (only `regNo`; `name` is deliberately excluded, so a spelling difference at another gate does not create a new student).
* The key fields are `private final`, which prevents the T3a failure.
* The class is `final`, so a subclass cannot break symmetry.

**Verification** (real output): 10,000 random scans of 1,000 students → `HashSet size 1000, distinct regNos 1000 OK`. The distinct count was computed independently with a `HashMap<String, Integer>` tally. T2′ also gives 3 for 3 students.

## Alternative Concept and Complexity Analysis – `TreeSet` with comparator, or `HashMap<String, Student>`

```java
Set<Student> present = new TreeSet<>(Comparator.comparing(Student::regNo));   // no hashCode needed
Map<String, Student> present = new HashMap<>();                                // key = regNo
present.putIfAbsent(scan.regNo(), scan);                                       // keeps the first scan
```

**Measured** (10 scans per student, mean of 3 runs after warm-up):

| n unique students | HashSet (ns/scan) | TreeSet with comparator (ns/scan) | HashMap<regNo, Student> (ns/scan) |
|---|---|---|---|
| 1,000 | 92.1 | 105.0 | 61.4 |
| 10,000 | 18.7 | 118.0 | 18.1 |
| 100,000 | 17.2 | **198.8** | 17.8 |

(The 1,000 row includes JIT warm-up.)

| Structure | Time per scan | Space for n unique students | Notes |
|---|---|---|---|
| `HashSet<Student>` | O(1) average (O(log n) worst case per bucket since Java 8) – flat 17–19 ns | O(n): about 32–48 B per node + table | needs a correct `hashCode`/`equals` |
| `TreeSet<Student>` (comparator on regNo) | O(log n) – grows 105 → 118 → 199 ns as the tree deepens and String comparisons multiply | O(n): about 40 B per red-black node | no `hashCode` needed; gives sorted attendance for free; the comparator **must** compare the key |
| `HashMap<String, Student>` | O(1) average – same as HashSet (HashSet *is* a HashMap) | O(n): node + key reference + value reference | the key is the immutable `String` regNo, so the mutable-key problem cannot occur; also keeps the first or last scan time per student |

**Recommendation:** `HashMap<String, Student>` keyed by `regNo` is the most robust choice for de-duplication. It is O(1) like `HashSet`, and its key is the immutable String, so it does not depend on every developer writing `hashCode` correctly. A `TreeSet` is only worth its O(log n) cost if the attendance list must be printed in regNo order.
