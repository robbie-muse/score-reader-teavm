package repro;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal repro: ArrayList.retainAll with a subList view of the same list.
 * Mirror of HiLoPeakFinder.findPeaks quorum branch:
 *   peaks.retainAll(peaks.subList(0, i));
 * JDK: works — ArrayList.retainAll uses batchRemove (raw array access, no
 * iterator), so no ConcurrentModificationException, and the list is
 * truncated to its first i elements.
 * TeaVM 0.16.0: TAbstractCollection.retainAll iterates with an iterator
 * while subList.contains() uses a second iterator over the same list; the
 * outer iterator's remove() bumps modCount, so the inner subList iterator
 * throws ConcurrentModificationException.
 */
public class RetainRepro
{
    public static void main (String[] args)
    {
        List<String> peaks = new ArrayList<>();

        for (int i = 0; i < 11; i++) {
            peaks.add("p" + i);
        }

        System.out.println("before size=" + peaks.size());

        try {
            peaks.retainAll(peaks.subList(0, 5));
            System.out.println("after size=" + peaks.size() + " peaks=" + peaks);
            System.out.println("RETAIN-REPRO OK (no exception)");
        } catch (Throwable t) {
            System.out.println("CAUGHT " + t.getClass().getName() + ": " + t.getMessage());
        }

        // removeAll, same batchRemove helper, opposite complement:
        // keep elements NOT in the argument.
        List<String> xs = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            xs.add("x" + i);
        }
        xs.removeAll(java.util.Arrays.asList("x1", "x3"));
        System.out.println("removeAll -> size=" + xs.size() + " xs=" + xs);
        // removeAll with a self-subList view (same CME class as retainAll).
        try {
            xs.removeAll(xs.subList(0, 2));
            System.out.println("removeAll self-subList -> size=" + xs.size() + " xs=" + xs);
        } catch (Throwable t) {
            System.out.println("CAUGHT " + t.getClass().getName() + ": " + t.getMessage());
        }
    }
}
