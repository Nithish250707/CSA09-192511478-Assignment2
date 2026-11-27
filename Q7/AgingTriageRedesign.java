import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;

/**
 * Q7 - Diagnose / redesign / validate the FAIR (aging) triage queue from Q6.
 * Original : waiting list + PriorityQueue rebuilt with a time-dependent comparator on every serve.
 * Redesign : five FIFO buckets (one per base severity); only the 5 bucket heads are compared.
 */
public class AgingTriageRedesign {
    record P(int id, int severity, long arrival) { }
    static final long AGING_MIN = 30;
    static int effective(P p, long now) { return (int) Math.max(1, p.severity() - (now - p.arrival()) / AGING_MIN); }
    static Comparator<P> at(long now) {
        return Comparator.comparingInt((P p) -> effective(p, now)).thenComparingLong(P::arrival).thenComparingInt(P::id);
    }

    interface Triage { void admit(P p); P serve(long now); List<P> display(long now); long objectsCreated(); }

    /** ORIGINAL (from Q6): rebuild a PriorityQueue at every serve because priorities change with time. */
    static final class RebuildTriage implements Triage {
        private final List<P> waiting = new ArrayList<>();
        private long objects = 0;
        public void admit(P p) { waiting.add(p); }
        public P serve(long now) {
            if (waiting.isEmpty()) return null;
            PriorityQueue<P> q = new PriorityQueue<>(Math.max(1, waiting.size()), at(now));
            q.addAll(waiting);                          // O(n log n) every time
            objects += 2;                               // new heap + its backing array (+ comparator chain)
            P next = q.poll();
            waiting.remove(next);                       // O(n) search + shift
            return next;
        }
        public List<P> display(long now) { List<P> c = new ArrayList<>(waiting); c.sort(at(now)); objects++; return c; }
        public long objectsCreated() { return objects; }
    }

    /** REDESIGN: bucket per base severity. Inside a bucket arrival order == aging order, so the head dominates. */
    static final class BucketTriage implements Triage {
        @SuppressWarnings("unchecked")
        private final ArrayDeque<P>[] bucket = new ArrayDeque[6];
        BucketTriage() { for (int s = 1; s <= 5; s++) bucket[s] = new ArrayDeque<>(); }
        public void admit(P p) { bucket[p.severity()].addLast(p); }            // O(1); arrivals are increasing
        public P serve(long now) {
            Comparator<P> cmp = at(now);
            int best = -1;
            for (int s = 1; s <= 5; s++)                                         // at most 5 comparisons
                if (!bucket[s].isEmpty() && (best < 0 || cmp.compare(bucket[s].peekFirst(), bucket[best].peekFirst()) < 0)) best = s;
            return best < 0 ? null : bucket[best].pollFirst();                   // O(1)
        }
        public List<P> display(long now) {                                       // 5-way merge of already-sorted buckets, O(5n)
            Comparator<P> cmp = at(now);
            List<P> out = new ArrayList<>();
            @SuppressWarnings("unchecked") java.util.Iterator<P>[] it = new java.util.Iterator[6];
            P[] head = new P[6];
            for (int s = 1; s <= 5; s++) { it[s] = bucket[s].iterator(); head[s] = it[s].hasNext() ? it[s].next() : null; }
            while (true) {
                int b = -1;
                for (int s = 1; s <= 5; s++) if (head[s] != null && (b < 0 || cmp.compare(head[s], head[b]) < 0)) b = s;
                if (b < 0) return out;
                out.add(head[b]); head[b] = it[b].hasNext() ? it[b].next() : null;
            }
        }
        public long objectsCreated() { return 0; }
    }

    /** Same workload for both designs: n already waiting (arrived over the last 3 h), then `serves` serve+admit cycles. */
    static long[] run(Triage t, int n, int serves, long seed, List<Integer> servedIds) {
        Random r = new Random(seed);
        long now = 0;
        int id = 0;
        // n patients who arrived in the 180 minutes before t=0, in arrival order
        for (int i = 0; i < n; i++) t.admit(new P(id++, 1 + r.nextInt(5), -180 + (180L * i) / Math.max(1, n)));
        com.sun.management.ThreadMXBean mx = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        long a0 = mx.getCurrentThreadAllocatedBytes();
        long t0 = System.nanoTime();
        for (int k = 0; k < serves; k++) {
            now++;                                                    // one minute passes
            t.admit(new P(id++, 1 + r.nextInt(5), now));              // one new arrival per minute
            P p = t.serve(now);
            if (servedIds != null) servedIds.add(p.id());
        }
        long t1 = System.nanoTime();
        long a1 = mx.getCurrentThreadAllocatedBytes();
        return new long[]{t1 - t0, t.objectsCreated(), a1 - a0};
    }

    public static void main(String[] args) {
        // ---- 1. correctness: identical served sequence and display on the same workload ----
        for (int n : new int[]{50, 2_000}) {
            List<Integer> a = new ArrayList<>(), b = new ArrayList<>();
            run(new RebuildTriage(), n, 600, 42, a);
            run(new BucketTriage(), n, 600, 42, b);
            System.out.println("Correctness n=" + n + ": 600 serves, identical treatment order? " + a.equals(b));
        }
        RebuildTriage ro = new RebuildTriage(); BucketTriage bo = new BucketTriage();
        Random r = new Random(5);
        for (int i = 0; i < 1_000; i++) { P p = new P(i, 1 + r.nextInt(5), -300 + i / 4); ro.admit(p); bo.admit(p); }
        System.out.println("Display (waiting list in current treatment order) identical? " + ro.display(0).equals(bo.display(0)));

        // ---- 2. starvation check for the redesign (same scenario as Q6) ----
        BucketTriage er = new BucketTriage();
        er.admit(new P(-1, 5, 0));
        int k = 0; long zara = -1;
        for (long t = 0; t <= 720 && zara < 0; t++) {
            if (t % 9 == 0) er.admit(new P(k++, 1 + (k % 2), t));
            if (t % 10 == 0) { P p = er.serve(t); if (p != null && p.id() == -1) zara = t; }
        }
        System.out.println("Q6 starvation scenario with redesign: S5 patient seen at t = " + zara + " min (Q6 aging result: 110)");

        // ---- 3. performance: same conditions for both designs ----
        System.out.println();
        System.out.println("Time per serve (incl. one admit), mean of 3 timed runs after warm-up");
        System.out.printf("%-10s %-7s %14s %14s %9s %18s %18s%n", "waiting n", "serves", "original (us)", "redesign (us)", "speed-up", "orig bytes/serve", "redesign bytes/serve");
        for (int n : new int[]{100, 1_000, 10_000, 100_000}) {
            int serves = (int) Math.min(2_000, 40_000_000L / ((long) n * 17));
            serves = Math.max(serves, 50);
            double to = 0, tb = 0; long ao = 0, ab = 0;
            for (int rep = 0; rep < 4; rep++) {
                long[] o = run(new RebuildTriage(), n, serves, rep, null);
                long[] b = run(new BucketTriage(), n, serves, rep, null);
                if (rep > 0) { to += o[0]; tb += b[0]; ao += o[2]; ab += b[2]; }
            }
            double po = to / 3 / serves / 1000, pb = tb / 3 / serves / 1000;
            System.out.printf("%-10d %-7d %14.2f %14.3f %8.0fx %18d %18d%n", n, serves, po, pb, po / pb, ao / 3 / serves, ab / 3 / serves);
        }
    }
}
