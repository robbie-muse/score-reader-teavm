# Deferred upstream TeaVM filings

These are genuine TeaVM defects found while getting Audiveris 5.11 to
run on TeaVM 0.16.0 WasmGC. **None has been filed.** Per the project
owner's decision (2026-11-09): upstream PRs/issues go up only after our
own work is done — "You can put these up to teavm PRs after we do all
of this. Let's focus on our own first."

Each row links the draft (where one exists), the patch in this repo,
and the minimal repro. Filing order is not decided; #272 and #21244
are the two that block a stock TeaVM build outright.

| # | Defect | Draft | Patch in this repo | Repro |
|---|---|---|---|---|
| #272 | `TPoint` extends `Object`, not `Point2D` (same for `TDimension` vs `Dimension2D`) — `AbstractInter.contains(Point)` fails WasmGC validation (`call_ref` type error, function #272) | `teavm/ISSUE_272_DRAFT.md` (+ `teavm/FN272_DIAGNOSIS.md`) | `teavm-fork/teavm-classlib-fix.patch` | `teavm/fn272/repro/` |
| #21244 (+ #15448, same class — do NOT file separately) | `WasmTypeInference.forceNullable` not honoured at every push site — coroutine-split blocks declare non-nullable result types for nullable fallthrough values (`LineCluster.getPointsAt`) | — (no draft yet) | `teavm-fork/teavm-core-21244.patch` + `teavm-core-21244b.patch` | — (no minimal repro; two faithful repros both load fine) |
| §11 | `Profiles.getName()` returns null under TeaVM — metadata-generation gap (JDK contract violated) | — (no draft yet) | Contained spike workaround in `ConstantSet.java` (via `teavm/patched/make_patches.py`) | — |
| §19 TColor | `Color.<init>(IZ)V` and `Color.MAGENTA` missing from `TColor` | — (no draft yet) | `teavm-fork/teavm-classlib-color.patch` | — |
| §27 hypot | `TMath.hypot` is `sqrt(x*x + y*y)` — returns NaN for `hypot(NaN, +Inf)`; JDK specifies +Inf when either argument is infinite | `teavm-fork/phase27/ISSUE_HYPOT_DRAFT.md` | `teavm-fork/teavm-classlib-hypot.patch` | `teavm-fork/phase27/hypot-repro/` |
| §33 retainAll | `TArrayList` inherits iterator-based `TAbstractCollection.retainAll`; JDK's `ArrayList` overrides it with index-based `batchRemove` — `peaks.retainAll(peaks.subList(0, i))` throws `ConcurrentModificationException` under TeaVM | `teavm-fork/phase38/retain-repro/ISSUE_RETAINALL_DRAFT.md` | `teavm-fork/teavm-classlib-retainall.patch` | `teavm-fork/phase38/retain-repro/` |
| §35 TFormatter | `TFormatter.FormatWriter.formatFloat` / `formatDecimalInt` / `formatRadixInt` throw `IllegalFormatConversionException` on a null argument; JDK formats null as `"null"` | `teavm-fork/phase41/format-repro/ISSUE_FORMATTER_DRAFT.md` | `teavm-fork/teavm-classlib-formatter.patch` | `teavm-fork/phase41/format-repro/` |
| §40 rint/round | `TMath.rint` is `return round(a)` — round half *away* from zero, where the JDK rounds half to *even*; `round` is also wrong for negative halves; `rint` destroys NaN/±Inf through the `(long)` cast | `teavm-fork/phase47/rint-repro/ISSUE_RINT_DRAFT.md` | `teavm-fork/teavm-classlib-rint.patch` | `teavm-fork/phase47/rint-repro/` |

## What is NOT filed (deliberately)

- #3027 / #18066 / #19489 / #32683 — spike-shim defects, not TeaVM bugs.
- The §12/§13 shim defects, the §14 `TemplateSymbol` deref (Audiveris
  code), trap 4 (§24 — shim `Rectangle.contains` bug), the §28 out64
  print crash, the §30 head-erasure gap (shim `TextLayout.draw` no-op),
  §42 HeadHeadRelation (ReflectionPolicy gap — TeaVM's designed opt-in
  reflection, fixed in our policy, not a TeaVM code change).
- The §44.1 Mozart export trap and the §34 Lindenbaum null timeOffset:
  Audiveris data + Wasm catchability, fixed in our writer.

## Before submitting

TeaVM's own `src/AGENTS.md` prescribes Gradle + checkstyle. Gradle
does not work on the VM this stack was built on, so the classlib
patches were verified by `javac` + `jar uf` only — the Gradle suite
must run on a machine where Gradle works before submitting upstream.
