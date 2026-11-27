import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Q3 - designed experiments on the equals()/hashCode() contract. Each test prints its prediction first. */
public class ContractExperiments {

    /** The junior developer's class: equals() overridden, hashCode() NOT overridden. */
    static class BrokenStudent {
        String regNo; String name;
        BrokenStudent(String regNo, String name) { this.regNo = regNo; this.name = name; }
        @Override public boolean equals(Object o) {
            if (!(o instanceof BrokenStudent)) return false;
            return regNo.equals(((BrokenStudent) o).regNo);
        }
        @Override public String toString() { return regNo; }
    }

    /** Fixed but MUTABLE: regNo can still be changed after insertion. */
    static class MutableStudent {
        String regNo; final String name;
        MutableStudent(String regNo, String name) { this.regNo = regNo; this.name = name; }
        @Override public boolean equals(Object o) { return o instanceof MutableStudent m && regNo.equals(m.regNo); }
        @Override public int hashCode() { return regNo.hashCode(); }
        @Override public String toString() { return regNo; }
    }

    /** Correct: immutable key fields, consistent equals/hashCode. */
    static final class FixedStudent {
        private final String regNo, name;
        FixedStudent(String regNo, String name) { this.regNo = regNo; this.name = name; }
        String regNo() { return regNo; } String name() { return name; }
        @Override public boolean equals(Object o) { return this == o || (o instanceof FixedStudent f && regNo.equals(f.regNo)); }
        @Override public int hashCode() { return regNo.hashCode(); }
        @Override public String toString() { return regNo + " " + name; }
    }

    static void report(String id, String desc, int predicted, int actual, String why) {
        System.out.printf("%-3s %-62s predicted=%d actual=%d %s%n     reason: %s%n", id, desc, predicted, actual,
                predicted == actual ? "(prediction correct)" : "(PREDICTION WRONG)", why);
    }

    public static void main(String[] args) {
        // Test 1 - appears to work: the gate re-uses the SAME object for a repeated card tap
        Set<BrokenStudent> t1 = new HashSet<>();
        BrokenStudent arun = new BrokenStudent("192311001", "Arun");
        t1.add(arun); t1.add(new BrokenStudent("192311002", "Divya")); t1.add(arun);
        report("T1", "Broken class, repeated scan re-uses the same object", 2, t1.size(),
                "same reference -> same identity hashCode -> same bucket, and == short-circuits equals");

        // Test 2 - designed to expose duplicates: each scan creates a NEW object (real scanner behaviour)
        Set<BrokenStudent> t2 = new HashSet<>();
        String[] scans = {"192311001", "192311002", "192311001", "192311003", "192311001", "192311002"};
        for (String s : scans) t2.add(new BrokenStudent(s, "n"));
        report("T2", "Broken class, 6 scans of 3 students, new object per scan", 6, t2.size(),
                "Object.hashCode() differs per object -> different buckets -> equals() never called");
        System.out.println("     equals says scan#1 equals scan#3: " + new BrokenStudent("192311001", "a").equals(new BrokenStudent("192311001", "b"))
                + ", but hashCodes " + new BrokenStudent("192311001", "a").hashCode() + " vs " + new BrokenStudent("192311001", "b").hashCode());
        System.out.println("     contains(new BrokenStudent(\"192311001\")) = " + t2.contains(new BrokenStudent("192311001", "x")));

        // Same scans with the corrected class
        Set<FixedStudent> t2f = new HashSet<>();
        for (String s : scans) t2f.add(new FixedStudent(s, "n"));
        report("T2'", "Fixed class, same 6 scans", 3, t2f.size(), "equal regNo -> equal hashCode -> same bucket -> equals() finds the duplicate");

        // Test 3a - own test: change regNo after insertion
        Set<MutableStudent> t3 = new HashSet<>();
        MutableStudent m = new MutableStudent("192311004", "Karthik");
        t3.add(m);
        m.regNo = "192311099";                       // correction typed by an admin after the scan
        boolean found = t3.contains(m);
        t3.add(new MutableStudent("192311099", "Karthik"));
        report("T3a", "Mutable key changed after add, then same student re-added", 1, t3.size(),
                "object sits in the bucket of the OLD hash; lookup goes to the NEW bucket, so contains=" + found + " and a duplicate is added");
        boolean removed = t3.remove(m);
        System.out.println("     remove(m) = " + removed + " -> the original entry is now unreachable (memory leak in the set)");

        // Test 3b - own test: TreeSet whose comparator is inconsistent with equals (compares names)
        Set<FixedStudent> t3b = new TreeSet<>(Comparator.comparing(FixedStudent::name));
        t3b.add(new FixedStudent("192311005", "Priya"));
        t3b.add(new FixedStudent("192311006", "Priya"));   // a DIFFERENT student with the same name
        t3b.add(new FixedStudent("192311007", "Rahul"));
        report("T3b", "TreeSet ordered by name; two different students named Priya", 3, t3b.size(),
                "TreeSet uses compare()==0, not equals(); the second Priya is silently dropped");

        // Verification of the correction: 10,000 random scans of 1,000 students
        Set<FixedStudent> v = new HashSet<>();
        Map<String, Integer> truth = new HashMap<>();
        java.util.Random r = new java.util.Random(3);
        for (int i = 0; i < 10_000; i++) {
            String reg = "1923" + (11000 + r.nextInt(1000));
            v.add(new FixedStudent(reg, "x")); truth.merge(reg, 1, Integer::sum);
        }
        System.out.println();
        System.out.println("Verification: 10,000 random scans -> HashSet size " + v.size() + ", distinct regNos " + truth.size()
                + (v.size() == truth.size() ? "  OK" : "  MISMATCH"));

        // Alternatives: HashSet vs TreeSet(Comparator) vs HashMap<String,Student> - time per scan
        System.out.println();
        System.out.println("Time per scan (ns), n unique students, 10 scans each, mean of 3 runs");
        System.out.printf("%-9s %10s %14s %18s%n", "n", "HashSet", "TreeSet(cmp)", "HashMap<regNo,..>");
        for (int n : new int[]{1_000, 10_000, 100_000}) {
            FixedStudent[] scansArr = new FixedStudent[n * 10];
            for (int i = 0; i < scansArr.length; i++) { String reg = "S" + (r.nextInt(n) + 1_000_000); scansArr[i] = new FixedStudent(reg, "x"); }
            double hs = 0, ts = 0, hm = 0;
            for (int rep = 0; rep < 4; rep++) {
                long c0 = System.nanoTime();
                Set<FixedStudent> a = new HashSet<>(); for (FixedStudent s : scansArr) a.add(s);
                long c1 = System.nanoTime();
                Set<FixedStudent> b = new TreeSet<>(Comparator.comparing(FixedStudent::regNo)); for (FixedStudent s : scansArr) b.add(s);
                long c2 = System.nanoTime();
                Map<String, FixedStudent> c = new HashMap<>(); for (FixedStudent s : scansArr) c.putIfAbsent(s.regNo(), s);
                long c3 = System.nanoTime();
                if (a.size() != b.size() || b.size() != c.size()) throw new AssertionError();
                if (rep > 0) { hs += c1 - c0; ts += c2 - c1; hm += c3 - c2; }
            }
            int k = scansArr.length;
            System.out.printf("%-9d %10.1f %14.1f %18.1f%n", n, hs / 3 / k, ts / 3 / k, hm / 3 / k);
        }
    }
}
