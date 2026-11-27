import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

/** Q2 - playlist operations on ArrayList vs LinkedList (and a plain array), plus an "Up Next" deque test. */
public class PlaylistBenchmark {
    record Song(int id, String title) { }

    static List<Song> filled(List<Song> l, int n) { for (int i = 0; i < n; i++) l.add(new Song(i, "S" + i)); return l; }

    interface Op { void run(List<Song> l, int ops, Random r); }

    static double time(String impl, int n, int ops, Op op) {
        double best = Double.MAX_VALUE;
        for (int rep = 0; rep < 5; rep++) {                       // 5 repetitions, report the mean of the last 4
            List<Song> l = filled(impl.equals("ArrayList") ? new ArrayList<>() : new LinkedList<>(), n);
            Random r = new Random(1);
            long t0 = System.nanoTime();
            op.run(l, ops, r);
            long t1 = System.nanoTime();
            if (rep == 0) continue;
            best = (best == Double.MAX_VALUE ? 0 : best) + (t1 - t0) / (double) ops;
        }
        return best / 4;
    }

    public static void main(String[] args) {
        int[] sizes = {1_000, 10_000, 100_000};
        String[] opNames = {"add at end", "insert at front", "remove at front", "insert in middle", "remove from middle", "get k-th (random)", "iterate (per element)"};
        Op[] ops = {
            (l, k, r) -> { for (int i = 0; i < k; i++) l.add(new Song(i, "x")); },
            (l, k, r) -> { for (int i = 0; i < k; i++) l.add(0, new Song(i, "x")); },
            (l, k, r) -> { for (int i = 0; i < k; i++) l.remove(0); },
            (l, k, r) -> { for (int i = 0; i < k; i++) l.add(l.size() / 2, new Song(i, "x")); },
            (l, k, r) -> { for (int i = 0; i < k; i++) l.remove(l.size() / 2); },
            (l, k, r) -> { long s = 0; for (int i = 0; i < k; i++) s += l.get(r.nextInt(l.size())).id(); if (s == -1) System.out.print(""); },
            (l, k, r) -> { long s = 0; for (int p = 0; p < 20; p++) for (Song x : l) s += x.id(); if (s == -1) System.out.print(""); },
        };
        System.out.println("Time per operation in nanoseconds (mean of 4 timed repetitions after 1 warm-up)");
        System.out.printf("%-22s %-8s %14s %14s %10s%n", "operation", "n", "ArrayList", "LinkedList", "LL/AL");
        for (int o = 0; o < ops.length; o++) {
            for (int n : sizes) {
                int k = (o == 6) ? 20 * n : Math.min(1_000, n / 2);  // iterate: 20 full passes
                double a = time("ArrayList", n, k, ops[o]);
                double b = time("LinkedList", n, k, ops[o]);
                System.out.printf("%-22s %-8d %14.1f %14.1f %10.2f%n", opNames[o], n, a, b, b / a);
            }
        }

        // Plain array with manual shifting: insert at front
        System.out.println();
        System.out.println("Plain Song[] with manual shifting (insert at front, ns/op)");
        for (int n : sizes) {
            Song[] arr = new Song[n + 1_000];
            int size = n;
            for (int i = 0; i < n; i++) arr[i] = new Song(i, "S");
            long t0 = System.nanoTime();
            for (int i = 0; i < 1_000; i++) {
                for (int j = size; j > 0; j--) arr[j] = arr[j - 1];     // shift right by one
                arr[0] = new Song(-i, "x");
                size++;
            }
            long t1 = System.nanoTime();
            System.out.printf("  n=%-8d %10.1f%n", n, (t1 - t0) / 1000.0);
        }

        // "Up Next" queue: add at both ends, remove from front
        System.out.println();
        System.out.println("Up Next workload: 1,000,000 ops (40% addLast, 20% addFirst, 40% pollFirst), start size 10,000");
        for (String impl : new String[]{"ArrayList", "LinkedList", "ArrayDeque"}) {
            double total = 0;
            for (int rep = 0; rep < 4; rep++) {
                Random r = new Random(5);
                long t0 = System.nanoTime();
                if (impl.equals("ArrayDeque")) {
                    ArrayDeque<Song> d = new ArrayDeque<>();
                    for (int i = 0; i < 10_000; i++) d.addLast(new Song(i, "s"));
                    for (int i = 0; i < 1_000_000; i++) {
                        int p = r.nextInt(10);
                        if (p < 4) d.addLast(new Song(i, "s")); else if (p < 6) d.addFirst(new Song(i, "s")); else d.pollFirst();
                    }
                } else {
                    List<Song> l = impl.equals("ArrayList") ? new ArrayList<>() : new LinkedList<>();
                    for (int i = 0; i < 10_000; i++) l.add(new Song(i, "s"));
                    for (int i = 0; i < 1_000_000; i++) {
                        int p = r.nextInt(10);
                        if (p < 4) l.add(new Song(i, "s")); else if (p < 6) l.add(0, new Song(i, "s")); else if (!l.isEmpty()) l.remove(0);
                    }
                }
                long t1 = System.nanoTime();
                if (rep > 0) total += (t1 - t0) / 1e6;
            }
            System.out.printf("  %-11s %10.1f ms%n", impl, total / 3);
        }
    }
}
