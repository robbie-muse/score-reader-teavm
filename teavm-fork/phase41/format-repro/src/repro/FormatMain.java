package repro;
// Minimal repro: TeaVM TFormatter throws IllegalFormatConversionException
// for a null argument to a numeric conversion, where the JDK formats "null".
// JDK (java.util.Formatter): a null argument is formatted as "null" for every
// conversion (precision truncates, width pads; %b gives "false").
// Failing case in the wild: Audiveris LedgersPostAnalysis.filter() formats
// six boxed Doubles with %.2f; min/maxDeltaBelowRatio stay null when the page
// has no below-staff ledgers (Hallelujah p06: cardBelow=0).
public class FormatMain {
    static void t(String fmt, Object arg) {
        try {
            System.out.println("[" + fmt + "] -> [" + String.format(fmt, arg) + "]");
        } catch (Throwable e) {
            System.out.println("[" + fmt + "] THREW " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        Double nd = null;
        Integer ni = null;
        t("%.2f", nd);
        t("%f", nd);
        t("%d", ni);
        t("%x", ni);
        t("%o", ni);
        t("%5.2f", nd);
        t("%s", null);
        t("%c", null);
        t("%b", null);
        t("%h", null);
        t("%.2f", 1.23456);
        t("%d", 42);
        Double minAbove = 0.73065234022056;
        Double maxAbove = 1.0518496968453708;
        Double minBelow = null;
        Double maxBelow = null;
        Double minHeight = 0.037465810394649655;
        Double maxHeight = 0.10325098914567923;
        try {
            String s = String.format("Filter minDeltaAboveRatio:%.2f maxDeltaAboveRatio:%.2f"
                    + " minDeltaBelowRatio:%.2f maxDeltaBelowRatio:%.2f"
                    + " minHeightRatio:%.2f maxHeightRatio:%.2f",
                    minAbove, maxAbove, minBelow, maxBelow, minHeight, maxHeight);
            System.out.println("FILTER-FMT OK: [" + s + "]");
        } catch (Throwable e) {
            System.out.println("FILTER-FMT THREW " + e.getClass().getName() + ": " + e.getMessage());
        }
    }
}
