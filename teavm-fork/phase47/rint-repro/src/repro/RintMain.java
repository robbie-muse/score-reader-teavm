package repro;

// Minimal repro: TeaVM TMath.rint delegates to round(), i.e. round half away
// from zero, where the JDK rounds half to even (JLS 15 / Math.rint javadoc:
// "If two double values that are mathematical integers are equally close,
// the result is the integer value that is even").
// Found in the wild (Audiveris Hallelujah p01, interline 17):
//   SlursBuilder tangentLength = scale.toPixels(0.5 fraction)
//     = rint(0.5 * 17) = rint(8.5): JDK 8, TeaVM 9
//     -> purgeStaffLines tangent dx 7 vs 8, incidence 15.945 deg vs 14.036 deg
//     (15 deg gate), the P2 m9 tie slur is killed under Wasm only.
//   Staff-line Y = rint(1656.5): JDK 1656.0, TeaVM 1657.0 (same 1 px flip).
// round(double)/round(float) share the defect for NEGATIVE halves:
// JDK round(x) = floor(x + 0.5); TeaVM = (long)(x + signum(x)*0.5),
// so round(-8.5) is -9 under TeaVM, -8 on the JDK.
public class RintMain {
    static void t(String label, double got, double want) {
        boolean ok = (got == want) || (Double.isNaN(got) && Double.isNaN(want));
        System.out.println(label + " = " + got + " (JDK " + want + ") " + (ok ? "OK" : "MISMATCH"));
    }

    static void tl(String label, long got, long want) {
        System.out.println(label + " = " + got + " (JDK " + want + ") " + (got == want ? "OK" : "MISMATCH"));
    }

    static void ti(String label, int got, int want) {
        System.out.println(label + " = " + got + " (JDK " + want + ") " + (got == want ? "OK" : "MISMATCH"));
    }

    public static void main(String[] args) {
        // rint: half to even (the two in-the-wild values first)
        t("rint(8.5)", Math.rint(8.5), 8.0);
        t("rint(1656.5)", Math.rint(1656.5), 1656.0);
        t("rint(9.5)", Math.rint(9.5), 10.0);
        t("rint(0.5)", Math.rint(0.5), 0.0);
        t("rint(1.5)", Math.rint(1.5), 2.0);
        t("rint(2.5)", Math.rint(2.5), 2.0);
        t("rint(3.5)", Math.rint(3.5), 4.0);
        t("rint(-8.5)", Math.rint(-8.5), -8.0);
        t("rint(-9.5)", Math.rint(-9.5), -10.0);
        t("rint(-0.5)", Math.rint(-0.5), -0.0);
        t("rint(-1.5)", Math.rint(-1.5), -2.0);
        // rint: non-halves (should already agree)
        t("rint(2.3)", Math.rint(2.3), 2.0);
        t("rint(2.7)", Math.rint(2.7), 3.0);
        t("rint(-2.3)", Math.rint(-2.3), -2.0);
        t("rint(-2.7)", Math.rint(-2.7), -3.0);
        t("rint(5.0)", Math.rint(5.0), 5.0);
        // rint: specials
        t("rint(NaN)", Math.rint(Double.NaN), Double.NaN);
        t("rint(+Inf)", Math.rint(Double.POSITIVE_INFINITY), Double.POSITIVE_INFINITY);
        t("rint(-Inf)", Math.rint(Double.NEGATIVE_INFINITY), Double.NEGATIVE_INFINITY);
        t("rint(0.0)", Math.rint(0.0), 0.0);
        // round(double): floor(x + 0.5)
        tl("round(8.5)", Math.round(8.5), 9L);
        tl("round(9.5)", Math.round(9.5), 10L);
        tl("round(-8.5)", Math.round(-8.5), -8L);
        tl("round(-9.5)", Math.round(-9.5), -9L);
        tl("round(2.3)", Math.round(2.3), 2L);
        tl("round(-2.3)", Math.round(-2.3), -2L);
        // round(float): floor(x + 0.5f)
        ti("round(8.5f)", Math.round(8.5f), 9);
        ti("round(-8.5f)", Math.round(-8.5f), -8);
        ti("round(-9.5f)", Math.round(-9.5f), -9);
        ti("round(2.3f)", Math.round(2.3f), 2);
    }
}
