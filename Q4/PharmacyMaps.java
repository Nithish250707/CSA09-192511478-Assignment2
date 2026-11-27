import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/** Q4 - Pharmacy stock lookup with HashMap, TreeMap, LinkedHashMap (+ Hashtable, + sorted array). */
public class PharmacyMaps {
    record Medicine(String code, String name, int stock) { }

    static List<Medicine> generate(int n, long seed) {
        Random r = new Random(seed);
        List<Medicine> list = new ArrayList<>();
        for (int i = 0; i < n; i++) list.add(new Medicine(String.format("MED%06d", i), "Drug" + i, r.nextInt(500)));
        Collections.shuffle(list, r);              // medicines are added in random code order
        return list;
    }

    /** Code-order report; for a HashMap/LinkedHashMap the keys must be copied and sorted first. */
    static List<String> codeOrder(Map<String, Medicine> m) {
        if (m instanceof TreeMap) return new ArrayList<>(m.keySet());
        List<String> keys = new ArrayList<>(m.keySet());
        Collections.sort(keys);
        return keys;
    }

    /** Insertion-order report; only LinkedHashMap remembers it - others need the separate added list. */
    static List<String> addedOrder(Map<String, Medicine> m, List<Medicine> addedLog) {
        if (m instanceof LinkedHashMap) return new ArrayList<>(m.keySet());
        List<String> keys = new ArrayList<>(addedLog.size());
        for (Medicine x : addedLog) keys.add(x.code());     // extra structure kept alongside the map
        return keys;
    }

    public static void main(String[] args) {
        int[] sizes = {1_000, 10_000, 100_000, 1_000_000};
        int lookups = 100_000;
        System.out.println("Times in ms (mean of 5 runs after 1 warm-up, after a global warm-up pass). Lookups = 100,000 random get(code).");
        System.out.printf("%-9s %-14s %9s %12s %13s %13s %10s %10s%n", "n", "map", "put all", "100k gets", "code report", "added report", "code ok", "added ok");
        for (int pass = 0; pass < 2; pass++)                       // pass 0 = global JIT warm-up, not printed
        for (int n : (pass == 0 ? new int[]{10_000, 100_000} : sizes)) {
            boolean show = pass == 1;
            List<Medicine> meds = generate(n, n);
            List<String> sortedCodes = new ArrayList<>(); for (Medicine x : meds) sortedCodes.add(x.code()); Collections.sort(sortedCodes);
            List<String> addedCodes = new ArrayList<>(); for (Medicine x : meds) addedCodes.add(x.code());
            Random r = new Random(9);
            String[] keys = new String[lookups];
            for (int i = 0; i < lookups; i++) keys[i] = String.format("MED%06d", r.nextInt(n));
            for (String kind : new String[]{"HashMap", "TreeMap", "LinkedHashMap", "Hashtable"}) {
                double tp = 0, tg = 0, tc = 0, ta = 0; boolean codeOk = false, addedOk = false;
                for (int rep = 0; rep < 6; rep++) {
                    Map<String, Medicine> m = switch (kind) {
                        case "HashMap" -> new HashMap<>(); case "TreeMap" -> new TreeMap<>();
                        case "LinkedHashMap" -> new LinkedHashMap<>(); default -> new Hashtable<>(); };
                    long t0 = System.nanoTime();
                    for (Medicine x : meds) m.put(x.code(), x);
                    long t1 = System.nanoTime();
                    long sink = 0; for (String k : keys) sink += m.get(k).stock();
                    long t2 = System.nanoTime();
                    List<String> c = codeOrder(m);
                    long t3 = System.nanoTime();
                    List<String> a = addedOrder(m, meds);
                    long t4 = System.nanoTime();
                    codeOk = c.equals(sortedCodes); addedOk = a.equals(addedCodes);
                    if (sink < 0) System.out.println();
                    if (rep > 0) { tp += t1 - t0; tg += t2 - t1; tc += t3 - t2; ta += t4 - t3; }
                    if (rep == 5) { tp /= 5.0 / 3; tg /= 5.0 / 3; tc /= 5.0 / 3; ta /= 5.0 / 3; }
                }
                if (show) System.out.printf("%-9d %-14s %9.2f %12.2f %13.2f %13.2f %10s %10s%n", n, kind, tp / 3e6, tg / 3e6, tc / 3e6, ta / 3e6, codeOk, addedOk);
            }
            // Alternative: sorted array + Arrays.binarySearch
            double tb = 0, tsort = 0;
            for (int rep = 0; rep < 4; rep++) {
                long t0 = System.nanoTime();
                String[] codes = new String[n]; Medicine[] byIdx = new Medicine[n];
                Medicine[] arr = meds.toArray(new Medicine[0]);
                Arrays.sort(arr, (x, y) -> x.code().compareTo(y.code()));
                for (int i = 0; i < n; i++) { codes[i] = arr[i].code(); byIdx[i] = arr[i]; }
                long t1 = System.nanoTime();
                long sink = 0; for (String k : keys) sink += byIdx[Arrays.binarySearch(codes, k)].stock();
                long t2 = System.nanoTime();
                if (sink < 0) System.out.println();
                if (rep > 0) { tsort += t1 - t0; tb += t2 - t1; }
            }
            if (show) System.out.printf("%-9d %-14s %9.2f %12.2f %13s %13s%n", n, "sorted array", tsort / 3e6, tb / 3e6, "0 (already)", "needs log");
        }
        // Inserting ONE new medicine into the sorted array (shift) vs map put
        int n = 100_000;
        String[] codes = new String[n + 1];
        for (int i = 0; i < n; i++) codes[i] = String.format("MED%06d", i * 2);
        long t0 = System.nanoTime();
        for (int rep = 0; rep < 1000; rep++) {
            String newCode = String.format("MED%06d", 1);
            int pos = -(Arrays.binarySearch(codes, 0, n, newCode) + 1);
            System.arraycopy(codes, pos, codes, pos + 1, n - pos);   // shift right
            codes[pos] = newCode;
            System.arraycopy(codes, pos + 1, codes, pos, n - pos);   // undo, to repeat
        }
        long t1 = System.nanoTime();
        System.out.printf("%nSorted array: insert one new medicine near the front at n=100,000: %.1f us (binary search + shift)%n", (t1 - t0) / 1000.0 / 1000 / 2);
    }
}
