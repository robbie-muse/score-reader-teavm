# Draft issue — TeaVM `TFormatter` throws `IllegalFormatConversionException` for a null argument to a numeric conversion

Status: DRAFT ONLY — do not file. Jonathan 2026-11-09: file the TeaVM upstream
PRs after our own work is done; focus on ours first, upstream later.

## Summary

`java.util.Formatter` on the JDK formats a null argument as the string
`"null"` for every conversion (precision truncates it, width pads it;
`%b` gives `"false"`). TeaVM's `TFormatter` throws
`IllegalFormatConversionException: Can't format argument of null using f
conversion` instead, for the numeric conversions `%f`, `%d`, `%x`, `%o`
(and `%X`, `%O`, `%D` via the same methods). The general conversions
`%s`, `%c`, `%b`, `%h` already handle null correctly.

Found in the wild: Audiveris `LedgersPostAnalysis.filter()` formats six
boxed `Double`s with `%.2f`; when a page has no below-staff ledgers the two
below-staff ratios stay `null` and the whole run dies in the LEDGERS step,
although the JDK formats them as `"nu"` and completes.

## Minimal repro

`phase41/format-repro/src/repro/FormatMain.java` (prints `String.format`
results; built with TeaVM 0.16.0 WasmGC, see `jars.txt`, runner
`run-repro2.mjs`):

```java
String.format("%.2f", (Double) null);  // JDK: "nu"
String.format("%f", (Double) null);    // JDK: "null"
String.format("%d", (Integer) null);   // JDK: "null"
String.format("%x", (Integer) null);   // JDK: "null"
String.format("%o", (Integer) null);   // JDK: "null"
String.format("%5.2f", (Double) null); // JDK: "   nu"
```

Pre-fix classlib (`out/`): all five numeric cases throw
`IllegalFormatConversionException`; `%s`/`%c`/`%b`/`%h` with null and all
non-null cases are already correct.
Fixed classlib (`out-fixed/`): all thirteen repro cases match the JDK
exactly, including the six-argument `LedgersPostAnalysis` format string.

JDK reference (JDK 25, measured): null formats as `"null"` for `%f`,
`%e`, `%g`, `%a`, `%d`, `%x`, `%o`, `%s`, `%c`, `%h`; `"false"` for `%b`;
precision truncates (`%.2f` -> `"nu"`, `%5.2f` -> `"   nu"`).

## Root cause

`TFormatter.FormatWriter` (`classlib/src/main/java/org/teavm/classlib/java/util/TFormatter.java`):
`formatFloat` throws for any argument that is not `Double`/`Float`/
`BigDecimal`, including null; `formatDecimalInt` and `formatRadixInt`
throw for any argument that is not an integral wrapper, including null.
`formatChar` already has the null branch (`formatGivenString(upperCase,
"null")`), and `formatBoolean`/`formatHex`/`formatString` handle null —
only the three numeric formatters are missing it.

## Proposed fix

In `formatFloat`, `formatDecimalInt`, and `formatRadixInt`, right after
reading the argument, return the JDK's `"null"` rendering through the
existing width/precision handling:

```java
if (arg == null) {
    formatGivenString(upperCase, "null");
    return;
}
```

(Placed after the flag-verification calls, so illegal flag/precision
combinations still throw exactly as before.) Patch:
`teavm-classlib-formatter.patch` (3 hunks, one per method).

## Known limitation (not part of this fix)

`TFormatter` does not dispatch `%e`/`%E`/`%g`/`%G`/`%a`/`%A`/`%F` at
all — they hit `default: throw new UnknownFormatConversionException`.
The JDK supports them; that is a separate coverage gap, not the null
defect fixed here.

## Notes for the fix author

- TeaVM's own `FormatterTest` (`tests/src/test/java/org/teavm/classlib/java/util/FormatterTest.java`)
  does not cover null arguments to numeric conversions (2 mentions of
  `null` in the file, neither a numeric null case) — a regression test
  along the lines of the repro above would cover this.
- Verified here with `javac` + in-place `jar uf` (Gradle does not run in
  this environment); the Gradle suite (`./gradlew :tests:test` with the
  `teavm.tests.wasm-gc`/`js`/`c` and `teavm.tests.optimized` properties,
  per `src/AGENTS.md`) should run on a machine where Gradle works before
  submitting.
