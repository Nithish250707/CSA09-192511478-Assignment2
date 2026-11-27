import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * Q1 - Railway e-ticket text: String (+ in a loop) vs StringBuilder vs StringBuffer,
 * plus String.join / StringJoiner / String.format, and a multithreaded shared-log test.
 */
public class TicketBuilderBenchmark {

    static String[] fields(int n, int ticket) {
        String[] f = new String[n];
        for (int i = 0; i < n; i++) f[i] = "F" + i + "=" + (ticket * 31 + i);
        f[0] = "PNR=" + (4_000_000_000L + ticket);
        return f;
    }

    static String withPlus(String[] f) {
        String s = "";
        for (String x : f) s = s + x + "|";          // new String every iteration
        return s;
    }
    static String withBuilder(String[] f) {
        StringBuilder sb = new StringBuilder();
        for (String x : f) sb.append(x).append('|');
        return sb.toString();
    }
    static String withBuffer(String[] f) {
        StringBuffer sb = new StringBuffer();
        for (String x : f) sb.append(x).append('|');
        return sb.toString();
    }
    static String withJoin(String[] f) { return String.join("|", f) + "|"; }
    static String withJoiner(String[] f) {
        StringJoiner j = new StringJoiner("|", "", "|");
        for (String x : f) j.add(x);
        return j.toString();
    }
    static String withFormat(String[] f) {
        StringBuilder sb = new StringBuilder();
        for (String x : f) sb.append(String.format("%s|", x));
        return sb.toString();
    }

    interface Approach { String build(String[] f); }

    static long usedMemory() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }

    /** Builds `tickets` tickets of `nFields` each, returns average ms over `runs` (after 1 warm-up). */
    static double[] measure(Approach a, int tickets, int nFields, int runs) {
        String[][] data = new String[tickets][];
        for (int t = 0; t < tickets; t++) data[t] = fields(nFields, t);
        long chars = 0;
        double total = 0; long memDelta = 0;
        for (int r = 0; r <= runs; r++) {
            System.gc();
            long m0 = usedMemory();
            long t0 = System.nanoTime();
            for (int t = 0; t < tickets; t++) chars += a.build(data[t]).length();
            long t1 = System.nanoTime();
            long m1 = usedMemory();
            if (r > 0) { total += (t1 - t0); memDelta += Math.max(0, m1 - m0); }
        }
        return new double[]{ total / runs / 1e6, memDelta / runs / 1024.0 / 1024.0, chars };
    }

    public static void main(String[] args) throws Exception {
        String[] names = {"String +", "StringBuilder", "StringBuffer", "String.join", "StringJoiner", "String.format"};
        Approach[] as = {TicketBuilderBenchmark::withPlus, TicketBuilderBenchmark::withBuilder,
                TicketBuilderBenchmark::withBuffer, TicketBuilderBenchmark::withJoin,
                TicketBuilderBenchmark::withJoiner, TicketBuilderBenchmark::withFormat};
        // sanity: all approaches build identical text
        String[] probe = fields(5, 1);
        for (Approach a : as) if (!a.build(probe).equals(withBuilder(probe))) throw new AssertionError();
        System.out.println("Sample ticket (5 fields): " + withBuilder(probe));

        int[][] workloads = { {1, 8}, {10_000, 50}, {20, 5_000} };
        String[] wname = {"W1 one short ticket (1 x 8 fields)", "W2 bulk day (10,000 tickets x 50 fields)",
                "W3 group-booking manifest (20 tickets x 5,000 fields)  [own workload]"};
        int runs = 5;
        for (int w = 0; w < workloads.length; w++) {
            int tickets = workloads[w][0], nf = workloads[w][1];
            System.out.println();
            System.out.println(wname[w] + ", avg of " + runs + " runs");
            System.out.printf("  %-14s %12s %14s %22s%n", "approach", "time (ms)", "alloc (MB)", "intermediate Strings*");
            for (int i = 0; i < as.length; i++) {
                if (i == 0 && w == 2) { /* still run; it is the point of W3 */ }
                double[] m = measure(as[i], tickets, nf, w == 0 ? 2000 : runs);
                long inter = (i == 0) ? (long) tickets * nf : (i == 5 ? (long) tickets * nf : tickets);
                System.out.printf("  %-14s %12.4f %14.2f %22d%n", names[i], m[0], m[1], inter);
            }
        }
        System.out.println("* estimate: 's = s + x + \"|\"' is one indy concatenation (JDK 9+), so 1 new String per field, each a full copy of s;");
        System.out.println("  builders create 1 final String per ticket (+ internal array regrowths); format creates 1 per field.");

        // Multithreaded shared booking log
        System.out.println();
        int threads = 4, perThread = 50_000;
        String line = "PNR4000000001 CONFIRMED\n";
        for (int trial = 1; trial <= 3; trial++) {
            StringBuilder unsafe = new StringBuilder();
            StringBuffer safe = new StringBuffer();
            List<Thread> ts = new ArrayList<>();
            String[] err = new String[1];
            for (int t = 0; t < threads; t++) ts.add(new Thread(() -> {
                for (int i = 0; i < perThread; i++) {
                    try { unsafe.append(line); } catch (RuntimeException e) { err[0] = e.getClass().getSimpleName(); }
                    safe.append(line);
                }
            }));
            for (Thread t : ts) t.start();
            for (Thread t : ts) t.join();
            long expected = (long) threads * perThread * line.length();
            System.out.printf("Shared log trial %d: expected %d chars | StringBuilder %d (%s)%s | StringBuffer %d (%s)%n",
                    trial, expected, unsafe.length(), unsafe.length() == expected ? "OK" : "CORRUPTED",
                    err[0] == null ? "" : " exception " + err[0], safe.length(), safe.length() == expected ? "OK" : "CORRUPTED");
        }
    }
}
