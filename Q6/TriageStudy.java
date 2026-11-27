import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.PriorityQueue;
import java.util.Random;

/** Q6 - correctness check, growth investigation, starvation + aging rule, and alternative designs. */
public class TriageStudy {
    record P(String name, int severity, long arrival) {
        @Override public String toString() { return name + "(S" + severity + ")"; }
    }
    static final Comparator<P> RULE = Comparator.comparingInt(P::severity).thenComparingLong(P::arrival);

    public static void main(String[] args) {
        // ---------- 1. Hand-checkable instance (8 patients) ----------
        String[] names = {"Anil", "Bala", "Chitra", "Deepa", "Ezhil", "Farah", "Gopi", "Hema"};
        int[] sev =      {   4,      2,       5,       2,       1,       4,      3,      1};
        PriorityQueue<P> pq = new PriorityQueue<>(RULE);
        for (int i = 0; i < names.length; i++) pq.add(new P(names[i], sev[i], i));
        System.out.println("Arrivals (in order): Anil S4, Bala S2, Chitra S5, Deepa S2, Ezhil S1, Farah S4, Gopi S3, Hema S1");
        System.out.println("Hand-worked order  : Ezhil(S1) Hema(S1) Bala(S2) Deepa(S2) Gopi(S3) Anil(S4) Farah(S4) Chitra(S5)");
        System.out.println("PriorityQueue.toString() (heap array): " + pq);
        List<P> sortedView = new ArrayList<>(pq); sortedView.sort(RULE);
        System.out.println("Waiting list display (sorted copy)   : " + sortedView);
        StringBuilder served = new StringBuilder();
        while (!pq.isEmpty()) served.append(pq.poll()).append(' ');
        System.out.println("Program treatment order (poll)       : " + served.toString().trim());
        System.out.println("Matches hand-worked order? " + served.toString().trim().equals("Ezhil(S1) Hema(S1) Bala(S2) Deepa(S2) Gopi(S3) Anil(S4) Farah(S4) Chitra(S5)"));

        // ---------- 2. Growth investigation ----------
        System.out.println();
        System.out.println("Growth: ns per admit / per serve (queue pre-filled to n, 100,000 admit+serve pairs, mean of 5)");
        System.out.printf("  %-10s %14s %14s %10s%n", "n", "admit (add)", "serve (poll)", "log2(n)");
        Random r = new Random(1);
        for (int n : new int[]{1_000, 10_000, 100_000, 1_000_000}) {
            double ta = 0, tp = 0;
            for (int rep = 0; rep < 6; rep++) {
                PriorityQueue<P> q = new PriorityQueue<>(RULE);
                long c = 0;
                for (int i = 0; i < n; i++) q.add(new P("x", 1 + r.nextInt(5), c++));
                P[] incoming = new P[100_000];
                for (int i = 0; i < incoming.length; i++) incoming[i] = new P("y", 1 + r.nextInt(5), c++);
                long t0 = System.nanoTime();
                for (P p : incoming) q.add(p);
                long t1 = System.nanoTime();
                for (int i = 0; i < incoming.length; i++) q.poll();
                long t2 = System.nanoTime();
                if (rep > 0) { ta += t1 - t0; tp += t2 - t1; }
            }
            System.out.printf("  %-10d %14.1f %14.1f %10.1f%n", n, ta / 5 / 100_000, tp / 5 / 100_000, Math.log(n) / Math.log(2));
        }

        // ---------- 3. Starvation and a fair (aging) rule ----------
        System.out.println();
        System.out.println("Starvation simulation: 1 doctor, 1 patient seen every 10 min; severe patients (S1/S2) arrive every 9 min;");
        System.out.println("one S5 patient (Zara) arrives at t=0. Simulated 12 hours.");
        for (boolean aging : new boolean[]{false, true}) {
            List<P> waiting = new ArrayList<>();
            waiting.add(new P("Zara", 5, 0));
            long zaraSeenAt = -1; int k = 0;
            for (long t = 0; t <= 12 * 60; t++) {
                if (t % 9 == 0) waiting.add(new P("Sev" + (k++), 1 + (k % 2), t));
                if (t % 10 == 0 && !waiting.isEmpty()) {
                    final long now = t;
                    Comparator<P> cmp = aging
                            ? Comparator.comparingInt((P p) -> effective(p, now)).thenComparingLong(P::arrival)
                            : RULE;
                    PriorityQueue<P> q = new PriorityQueue<>(cmp);   // rebuilt because priorities change with time
                    q.addAll(waiting);
                    P next = q.poll();
                    waiting.remove(next);
                    if (next.name().equals("Zara")) { zaraSeenAt = t; break; }
                }
            }
            System.out.println("  " + (aging ? "With aging (1 level per 30 min waited)" : "Strict severity rule          ") + ": Zara seen at "
                    + (zaraSeenAt < 0 ? "NEVER (still waiting after 12 h, queue length " + waiting.size() + ")" : "t = " + zaraSeenAt + " min"));
        }

        // ---------- 4. Alternatives: sorted LinkedList and 5 FIFO queues ----------
        System.out.println();
        System.out.println("Alternative designs: ns per admit / serve with n waiting (20,000 admit+serve pairs, mean of 3)");
        System.out.printf("  %-10s %-22s %12s %12s%n", "n", "design", "admit", "serve");
        for (int n : new int[]{1_000, 10_000, 100_000}) {
            for (String d : new String[]{"PriorityQueue", "sorted LinkedList", "5 FIFO queues"}) {
                double ta = 0, ts = 0;
                for (int rep = 0; rep < 4; rep++) {
                    Random rr = new Random(rep);
                    long c = 0;
                    PriorityQueue<P> q = new PriorityQueue<>(RULE);
                    LinkedList<P> ll = new LinkedList<>();
                    @SuppressWarnings("unchecked") ArrayDeque<P>[] lv = new ArrayDeque[6];
                    for (int i = 1; i <= 5; i++) lv[i] = new ArrayDeque<>();
                    P[] pre = new P[n]; for (int i = 0; i < n; i++) pre[i] = new P("x", 1 + rr.nextInt(5), c++);
                    if (d.equals("sorted LinkedList")) { List<P> tmp = new ArrayList<>(List.of(pre)); tmp.sort(RULE); ll.addAll(tmp); }
                    for (P p : pre) { if (d.equals("PriorityQueue")) q.add(p); else if (d.equals("5 FIFO queues")) lv[p.severity()].addLast(p); }
                    int m = 20_000; P[] in = new P[m]; for (int i = 0; i < m; i++) in[i] = new P("y", 1 + rr.nextInt(5), c++);
                    long t0 = System.nanoTime();
                    for (P p : in) {
                        switch (d) {
                            case "PriorityQueue" -> q.add(p);
                            case "5 FIFO queues" -> lv[p.severity()].addLast(p);
                            default -> {                                     // walk to the correct position
                                ListIterator<P> it = ll.listIterator();
                                while (it.hasNext()) { if (RULE.compare(it.next(), p) > 0) { it.previous(); break; } }
                                it.add(p);
                            }
                        }
                    }
                    long t1 = System.nanoTime();
                    for (int i = 0; i < m; i++) {
                        switch (d) {
                            case "PriorityQueue" -> q.poll();
                            case "5 FIFO queues" -> { for (int s = 1; s <= 5; s++) if (!lv[s].isEmpty()) { lv[s].pollFirst(); break; } }
                            default -> ll.pollFirst();
                        }
                    }
                    long t2 = System.nanoTime();
                    if (rep > 0) { ta += t1 - t0; ts += t2 - t1; }
                }
                System.out.printf("  %-10d %-22s %12.1f %12.1f%n", n, d, ta / 3 / 20_000, ts / 3 / 20_000);
            }
        }
    }

    /** Effective level: one level more urgent for every 30 minutes waited, never better than 1. */
    static int effective(P p, long now) { return (int) Math.max(1, p.severity() - (now - p.arrival()) / 30); }
}
