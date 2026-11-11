package repro;

import java.util.ArrayList;
import java.util.List;

/**
 * Control: the proposed fix — subList(i, size).clear() instead of
 * retainAll(subList(0, i)). Semantically identical (Range uses identity
 * equality, so retainAll keeps exactly the first i elements), and it
 * removes a range via the subList itself rather than iterating the list
 * while testing membership in a view of it.
 */
public class RetainFixed
{
    public static void main (String[] args)
    {
        List<String> peaks = new ArrayList<>();

        for (int i = 0; i < 11; i++) {
            peaks.add("p" + i);
        }

        System.out.println("before size=" + peaks.size());
        peaks.subList(5, peaks.size()).clear();
        System.out.println("after size=" + peaks.size() + " peaks=" + peaks);
        System.out.println("RETAIN-FIXED OK");
    }
}
