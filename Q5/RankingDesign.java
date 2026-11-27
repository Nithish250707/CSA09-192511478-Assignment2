import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/** Q5 - reusable ranking design, tie-heavy data, adversarial data that breaks faulty comparators,
 *  an exhaustive contract checker, and the Comparable / triple-stable-sort alternatives. */
public class RankingDesign {

    /** A rankable entity exposes its sort keys; Team, Player, College all implement it. */
    interface Rankable { int primary(); int secondary(); String label(); }

    record TeamR(String label, int primary, int secondary) implements Rankable {
        @Override public String toString() { return label + "(" + primary + "," + secondary + ")"; }
    }
    record Player(String label, int primary, int secondary) implements Rankable {   // goals, assists
        @Override public String toString() { return label + "[" + primary + "g," + secondary + "a]"; }
    }

    /** Comparable alternative: the ranking is baked into the class. */
    record ComparableTeam(String name, int points, int goalDiff) implements Comparable<ComparableTeam> {
        @Override public int compareTo(ComparableTeam o) {
            int c = Integer.compare(o.points, points);
            if (c != 0) return c;
            c = Integer.compare(o.goalDiff, goalDiff);
            return c != 0 ? c : name.compareTo(o.name);
        }
    }

    /** Generic, bounded leaderboard: works for ANY Rankable; wildcard on input lists. */
    static final class RankBoard<T extends Rankable> {
        private final List<T> items = new ArrayList<>();
        void addAll(Collection<? extends T> more) { items.addAll(more); }   // accepts List<SubTypeOfT>
        List<T> ranking() { List<T> c = new ArrayList<>(items); c.sort(RankBoard.<T>standardOrder()); return c; }
        static <T extends Rankable> Comparator<T> standardOrder() {
            return Comparator.<T>comparingInt(Rankable::primary).reversed()
                    .thenComparing(Comparator.<T>comparingInt(Rankable::secondary).reversed())
                    .thenComparing(Rankable::label);
        }
    }

    static <T extends Comparable<? super T>> T best(List<? extends T> list) {          // bounded + wildcard
        T b = list.get(0);
        for (T x : list) if (x.compareTo(b) < 0) b = x;                               // smallest = ranked first
        return b;
    }

    // ---------------- comparators under test ----------------
    static final Comparator<TeamR> CORRECT = RankBoard.standardOrder();
    static final Comparator<TeamR> SUBTRACTION = (a, b) -> {                // faulty: overflow
        int c = b.primary() - a.primary();
        if (c != 0) return c;
        c = b.secondary() - a.secondary();
        return c != 0 ? c : a.label().compareTo(b.label());
    };
    static final Comparator<TeamR> NO_NAME_TIEBREAK =                        // faulty: ignores name
            Comparator.comparingInt(TeamR::primary).reversed().thenComparing(Comparator.comparingInt(TeamR::secondary).reversed());

    /** Exhaustively checks sgn(c(a,b)) = -sgn(c(b,a)), transitivity, and that compare==0 only for identical teams. */
    static String checkContract(Comparator<TeamR> c, List<TeamR> data) {
        int asym = 0, intrans = 0, falseTies = 0;
        for (TeamR a : data) for (TeamR b : data) {
            if (Integer.signum(c.compare(a, b)) != -Integer.signum(c.compare(b, a))) asym++;
            if (a != b && c.compare(a, b) == 0) falseTies++;
            for (TeamR x : data) if (c.compare(a, b) < 0 && c.compare(b, x) < 0 && c.compare(a, x) >= 0) intrans++;
        }
        return "antisymmetry violations=" + asym + ", transitivity violations=" + intrans + ", distinct teams comparing equal=" + falseTies / 2;
    }

    static List<TeamR> tieData() {
        return List.of(new TeamR("Lions", 12, 5), new TeamR("Tigers", 12, 5), new TeamR("Bears", 12, 5),
                new TeamR("Wolves", 12, 3), new TeamR("Hawks", 12, 3), new TeamR("Sharks", 9, 3),
                new TeamR("Rams", 9, 3), new TeamR("Owls", 9, -2), new TeamR("Bulls", 6, 0), new TeamR("Foxes", 6, 0));
    }

    public static void main(String[] args) {
        System.out.println("Data set 1 - ties at every level:");
        List<TeamR> ties = new ArrayList<>(tieData());
        List<TeamR> r1 = new ArrayList<>(ties); r1.sort(CORRECT);
        System.out.println("  CORRECT      : " + r1);
        System.out.println("  contract     : " + checkContract(CORRECT, ties));
        // ignoring the name tie-break: result depends on input order
        List<TeamR> shuffled = new ArrayList<>(ties); Collections.shuffle(shuffled, new Random(4));
        List<TeamR> a = new ArrayList<>(ties); a.sort(NO_NAME_TIEBREAK);
        List<TeamR> b = new ArrayList<>(shuffled); b.sort(NO_NAME_TIEBREAK);
        System.out.println("  NO_NAME (input order)   : " + a);
        System.out.println("  NO_NAME (shuffled input): " + b);
        System.out.println("  same ranking both times? " + a.equals(b) + "   | " + checkContract(NO_NAME_TIEBREAK, ties));
        List<TeamR> c1 = new ArrayList<>(shuffled); c1.sort(CORRECT);
        System.out.println("  CORRECT on shuffled input equals CORRECT on original? " + c1.equals(r1));

        System.out.println();
        System.out.println("Data set 2 - designed to break the subtraction comparator (extreme bonus/penalty points):");
        List<TeamR> adv = new ArrayList<>(List.of(new TeamR("Alpha", Integer.MAX_VALUE, 0), new TeamR("Beta", -10, 0),
                new TeamR("Gamma", 5, 0), new TeamR("Delta", Integer.MIN_VALUE + 5, 0)));
        List<TeamR> good = new ArrayList<>(adv); good.sort(CORRECT);
        List<TeamR> bad = new ArrayList<>(adv);
        String outcome;
        try { bad.sort(SUBTRACTION); outcome = bad.toString(); }
        catch (IllegalArgumentException e) { outcome = "IllegalArgumentException: " + e.getMessage(); }
        System.out.println("  CORRECT     : " + good);
        System.out.println("  SUBTRACTION : " + outcome);
        System.out.println("  SUBTRACTION compare(Alpha, Beta) = " + SUBTRACTION.compare(adv.get(0), adv.get(1))
                + "  (should be negative: Alpha has more points)");
        System.out.println("  contract    : " + checkContract(SUBTRACTION, adv));

        System.out.println();
        System.out.println("Reuse of the same generic design:");
        RankBoard<Player> players = new RankBoard<>();
        players.addAll(List.of(new Player("Ravi", 7, 2), new Player("Arjun", 7, 4), new Player("Imran", 9, 0)));
        System.out.println("  players : " + players.ranking());
        List<ComparableTeam> ct = List.of(new ComparableTeam("Panthers", 9, 4), new ComparableTeam("Eagles", 9, 6), new ComparableTeam("Bisons", 9, 4));
        System.out.println("  best ComparableTeam via <T extends Comparable<? super T>> best(List<? extends T>): " + best(ct));

        // ---------------- cost: comparator chain vs Comparable vs 3 stable sorts ----------------
        System.out.println();
        System.out.println("Sorting cost (ms, mean of 5 after warm-up):");
        System.out.printf("  %-9s %14s %12s %18s%n", "n", "comparator", "Comparable", "3 stable sorts");
        Random r = new Random(11);
        for (int n : new int[]{10_000, 100_000, 1_000_000}) {
            TeamR[] base = new TeamR[n]; ComparableTeam[] cbase = new ComparableTeam[n];
            for (int i = 0; i < n; i++) { int p = r.nextInt(30), g = r.nextInt(41) - 20; String nm = "T" + r.nextInt(n);
                base[i] = new TeamR(nm, p, g); cbase[i] = new ComparableTeam(nm, p, g); }
            double t1 = 0, t2 = 0, t3 = 0; boolean same = true;
            for (int rep = 0; rep < 6; rep++) {
                TeamR[] x = base.clone(); ComparableTeam[] y = cbase.clone(); TeamR[] z = base.clone();
                long s0 = System.nanoTime(); Arrays.sort(x, CORRECT);
                long s1 = System.nanoTime(); Arrays.sort(y);
                long s2 = System.nanoTime();
                Arrays.sort(z, Comparator.comparing(TeamR::label));                       // least significant key first
                Arrays.sort(z, Comparator.comparingInt(TeamR::secondary).reversed());
                Arrays.sort(z, Comparator.comparingInt(TeamR::primary).reversed());       // most significant last
                long s3 = System.nanoTime();
                same &= Arrays.equals(x, z);
                if (rep > 0) { t1 += s1 - s0; t2 += s2 - s1; t3 += s3 - s2; }
            }
            System.out.printf("  %-9d %14.1f %12.1f %18.1f   (3-sort result identical to chain: %s)%n", n, t1 / 5e6, t2 / 5e6, t3 / 5e6, same);
        }
    }
}
