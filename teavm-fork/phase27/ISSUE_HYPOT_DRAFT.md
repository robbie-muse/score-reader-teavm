# Draft issue — TeaVM `Math.hypot` violates the JDK spec for infinite/NaN arguments

Status: DRAFT ONLY — do not file. Jonathan 2026-11-09: file the TeaVM upstream
PRs after our own work is done; focus on ours first, upstream later.

## Summary

`org.teavm.classlib.java.lang.TMath.hypot(double, double)` is implemented as
`sqrt(x * x + y * y)`. That returns `NaN` for `hypot(NaN, +Infinity)` and
`hypot(+Infinity, NaN)`, where the JDK (`java.lang.Math.hypot`) specifies
positive infinity: "If either argument is infinite, then the result is
positive infinity. If either argument is NaN and neither argument is
infinite, then the result is NaN."

## Minimal repro

`phase27/hypot-repro/src/repro/HypotMain.java` (prints `Math.hypot` results):

```java
System.out.println(Math.hypot(Double.NaN, Double.POSITIVE_INFINITY)); // JDK: Infinity
System.out.println(Math.hypot(Double.POSITIVE_INFINITY, Double.NaN)); // JDK: Infinity
System.out.println(Math.hypot(Double.NaN, 1.0));                      // JDK: NaN
System.out.println(Math.hypot(Double.POSITIVE_INFINITY, 1.0));        // JDK: Infinity
System.out.println(Math.hypot(3.0, 4.0));                             // JDK: 5.0
System.out.println(Math.hypot(0.0, 0.0));                             // JDK: 0.0
```

Built with TeaVM 0.16.0 WasmGC (see `phase27/hypot-repro/jars-fixed.txt` /
`jars-prefixed.txt`, runner `run-repro2.mjs`):

- Pre-fix classlib (`out-prefixed`): `hypot(NaN,Inf)=NaN`, `hypot(Inf,NaN)=NaN`
  — wrong; the other four cases are already correct.
- Fixed classlib (`out-fixed`): all six cases match the JDK exactly.

## Root cause

`TMath.hypot` (`classlib/src/main/java/org/teavm/classlib/java/lang/TMath.java:601`):

```java
public static double hypot(double x, double y) {
    return sqrt(x * x + y * y);
}
```

With `x = NaN, y = +Inf`: `x*x = NaN`, `y*y = +Inf`, `NaN + Inf = NaN`,
`sqrt(NaN) = NaN`. (`TStrictMath.hypot` delegates to `TMath.hypot`, so it
has the same defect.)

## Proposed fix

`teavm-classlib-hypot.patch` (29 lines, against the 0.16.0 tag): honour the
two JDK special cases before the existing formula, leaving finite inputs
bit-identical to the old formula (no scaled algorithm, so no 1-ulp change
for finite values):

```java
public static double hypot(double x, double y) {
    if (Double.isInfinite(x) || Double.isInfinite(y)) {
        return Double.POSITIVE_INFINITY;
    }
    if (Double.isNaN(x) || Double.isNaN(y)) {
        return Double.NaN;
    }
    return sqrt(x * x + y * y);
}
```

## Real-world impact (how it was found)

Audiveris 5.11 under TeaVM WasmGC crashed in its CURVES step: for a
perfectly collinear slur triple, the circle fit's centre is `(NaN, +Infinity)`
(the two bisectors are parallel, denominator 0), so the radius is
`hypot(NaN, +Infinity)`. On the JDK that is `+Infinity`, and
`CircleModel.createValid` rejects the model (radius infinite) and falls back
to a line model. Under TeaVM the radius was `NaN`, the model was accepted
with NaN angles, its Bézier curve came back null, and `SlurInfo.getCurve`
dereferenced it — an uncatchable Wasm trap, where the JDK throws a catchable
`NullPointerException`. Full write-up: `FORK_FIX.md` §27.

## Notes for the submitter

- Upstream repo conventions (`src/AGENTS.md`) want Gradle (`./gradlew :tests:test`
  with `teavm.tests.wasm-gc` / `teavm.tests.js` / `teavm.tests.c` and
  `teavm.tests.optimized` set) plus checkstyle. Gradle does not work in the
  VM where this was developed (proxy CONNECT failure), so the fix was verified
  here with javac + an in-place jar entry replacement instead; run the Gradle
  test suite on a machine where Gradle works before submitting.
- `TMath` is compiled against the classlib + interop/core jars
  (`javac -cp <classlib.jar>:<teavm>/lib/*`); the class file was verified with
  `javap -c` to contain the new `isInfinite`/`isNaN` branches before the jar
  entry was replaced.
