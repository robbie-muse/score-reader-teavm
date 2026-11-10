# Draft issue — TeaVM `TMath.rint` rounds halves away from zero, and `round` is wrong for negative halves; `rint` also destroys NaN/Infinity

Status: DRAFT ONLY — do not file. Jonathan 2026-11-09: file the TeaVM upstream
PRs after our own work is done; focus on ours first, upstream later.

## Summary

`Math.rint` on the JDK rounds half to even (JLS / `Math.rint` javadoc: "If two
double values that are mathematical integers are equally close, the result is
the integer value that is even"). TeaVM's `TMath.rint` delegates to `round()`,
i.e. round half away from zero — so `rint(8.5)` is `9.0` under TeaVM, `8.0` on
the JDK. `rint` additionally destroys NaN and infinities: they pass through
`round`'s `(long)` cast, becoming `0.0` / `+-9.223372036854776E18`
(`Long.MAX_VALUE` as a double).

`Math.round` shares the defect for negative halves: the JDK defines
`round(double)` as `(long) Math.floor(a + 0.5)` and `round(float)` as
`(int) Math.floor(a + 0.5f)`, while TeaVM computes
`(long) (a + signum(a) * 0.5)` — so `round(-8.5)` is `-9` under TeaVM, `-8`
on the JDK (and likewise `round(-8.5f)`).

Found in the wild: Audiveris 5.11 under TeaVM 0.16.0 WasmGC (Hallelujah p01,
interline 17). `SlursBuilder`'s tangent length is
`scale.toPixels(0.5 fraction) = (int) Math.rint(0.5 * 17) = rint(8.5)` — 8 on
the JDK, 9 under TeaVM — which flips a staff-line tangency gate (incidence
15.945 deg vs 14.036 deg against a 15 deg threshold) and kills a tie slur that
the desktop keeps; the staff-line ordinate `rint(1656.5)` flips the same way
(1656.0 vs 1657.0). Both inputs are exact `.5` values, so this is not
floating-point noise — it is the rounding rule.

## Minimal repro

`phase47/rint-repro/src/repro/RintMain.java` (prints `Math.rint` / `Math.round`
results with the JDK value beside each; built with TeaVM 0.16.0 WasmGC, see
`jars.txt`, runner `run-repro2.mjs`):

```java
Math.rint(8.5);      // JDK 8.0,  TeaVM (pre-fix) 9.0
Math.rint(1656.5);   // JDK 1656.0, TeaVM (pre-fix) 1657.0
Math.rint(0.5);      // JDK 0.0,  TeaVM (pre-fix) 1.0
Math.rint(2.5);      // JDK 2.0,  TeaVM (pre-fix) 3.0
Math.rint(-8.5);     // JDK -8.0, TeaVM (pre-fix) -9.0
Math.rint(-0.5);     // JDK -0.0, TeaVM (pre-fix) -1.0
Math.rint(Double.NaN);                 // JDK NaN, TeaVM (pre-fix) 0.0
Math.rint(Double.POSITIVE_INFINITY);   // JDK Infinity, TeaVM (pre-fix) 9.223372036854776E18
Math.round(-8.5);    // JDK -8, TeaVM (pre-fix) -9
Math.round(-8.5f);   // JDK -8, TeaVM (pre-fix) -9
```

Pre-fix classlib (`out/`): 9 of 20 `rint` cases and 4 of 10 `round` cases
mismatch the JDK (all non-half values already agree). Fixed classlib
(`out-fixed/`): all 30 repro cases match the JDK exactly, including the
printed `-0.0` for `rint(-0.5)`.

JDK reference: `phase47/rint-repro/jdk-reference.log` (JDK 25, all 30 OK).

## Root cause

`TMath` (`classlib/src/main/java/org/teavm/classlib/java/lang/TMath.java`):

```java
public static double rint(double a) {
    return round(a);
}
public static int round(float a) {
    return (int) (a + signum(a) * 0.5f);
}
public static long round(double a) {
    return (long) (a + signum(a) * 0.5);
}
```

## Proposed fix

`teavm-classlib-rint.patch` (2 hunks, same file):

```java
public static double rint(double a) {
    // JDK: the closest integer; ties go to the even integer (round half
    // to even). TeaVM previously delegated to round(), i.e. half away
    // from zero, and additionally destroyed NaN/Infinity (they pass
    // through a (long) cast, becoming 0 / +-Long.MAX_VALUE).
    if (Double.isNaN(a) || Double.isInfinite(a) || a == 0.0) {
        return a;
    }
    double f = floor(a);
    double diff = a - f;
    if (diff < 0.5) {
        return f;
    }
    if (diff > 0.5) {
        return f + 1.0;
    }
    // Tie: return the even neighbour. (Preserve the sign of zero:
    // rint(-0.5) is -0.0 on the JDK.)
    double r = (f % 2.0 == 0.0) ? f : f + 1.0;
    return (r == 0.0) ? (a < 0.0 ? -0.0 : 0.0) : r;
}

public static int round(float a) {
    // JDK: (int) floor(a + 0.5f).
    return (int) floor(a + 0.5f);
}

public static long round(double a) {
    // JDK: (long) floor(a + 0.5).
    return (long) floor(a + 0.5);
}
```

`floor` is the existing native import (`teavmMath.floor`); `%` on doubles is
native `f64.rem`, exact for integer-valued operands. Verified here by `javac` +
in-place `jar uf` (Gradle does not run in this VM); per `src/AGENTS.md` the
Gradle suite (`./gradlew :tests:test` with the `teavm.tests.wasm-gc`/`js`/`c`
and `teavm.tests.optimized` properties) must run on a machine where Gradle
works before submitting upstream.

## Test gap

TeaVM's own `MathTest` (`tests/src/test/java/org/teavm/classlib/java/lang/MathTest.java`)
has no `rint` coverage at all, and `roundWorks` tests only non-half values
(1.3, 1.8, -1.3, -1.8) — which is why this survived. A regression test along
the repro's lines (half values both signs, NaN, ±Infinity, ±0.0) would cover
this.

## Wild verification (Audiveris, full-out88)

With the fixed classlib jar, Hallelujah p01 no-OCR under TeaVM: the tie slur's
purge-gate inputs now match the desktop exactly (lineY 1656.0, tangent dx 7,
incidence 15.945 deg) and the slur survives; deep diff vs desktop 93 -> 89.
Lindenbaum deep diff vs fixed desktop 10 -> 2 (filename artefacts only);
hymn deep diff vs desktop 12 -> 2 (filename artefacts only). No regression on
either control page.
