# TeaVM WasmGC function #272 — diagnosis (RESOLVED: root cause found)

Date: 2026-11-08. TeaVM 0.16.0, Node v24.22.1, Java 25 (jdk-25.0.5+8),
Audiveris 5.11, headless entry point `teavm.TeavmMain`.

Original error (SIMPLE optimisation, `out-teavm/classes.wasm`, 10,190,425 bytes,
2,863 classes, 27,661 methods):

```
CompileError: WebAssembly.compileStreaming(): Compiling function #272 failed:
call_ref[1] expected type (ref null 48), found block of type (ref null 467) @+997174
```

## TL;DR

**Not a vtable/codegen bug in the sense first suspected. The module is
invalid because TeaVM's class library breaks the real Java geometry
hierarchy: `java.awt.Point` does NOT extend `java.awt.geom.Point2D` in
TeaVM.**

- In real Java, `Point extends Point2D`. Audiveris (correctly) passes a
  `Point` wherever a `Point2D` is expected.
- TeaVM replaces `java.awt.Point` with its classlib `TPoint`
  (`org.teavm.classlib.java.awt.TPoint`), which extends `java.lang.Object`
  directly and has only `int x, y` + an `(int, int)` constructor — no
  `getX`/`getY`, no `Point2D` supertype. Same for `TDimension`
  (extends `Object`, not `Dimension2D`).
- TeaVM therefore emits two **sibling** WasmGC struct types under `Object`:
  type 48 = `Point2D` (fields: vtable + eqref only) and type 467 = `Point`
  (fields: vtable + eqref + two `i32`). A `call_ref` whose declared
  parameter type is `(ref null 48)` receives a `(ref null 467)` value, which
  is not a subtype — so Node rejects the whole module at validation time,
  before any user code runs.
- The earlier hypothesis in `TEAVM_SPIKE.md` §5 ("vtable/call-site type
  confusion in TeaVM's WasmGC codegen for a virtual/interface call") is
  superseded by this. The codegen is faithfully emitting the types implied
  by TeaVM's own class model; the class model itself is what diverges
  from Java. (Whether TeaVM *should* also have caught the mismatch during
  compilation instead of emitting an invalid module is a separate,
  secondary question for the issue.)

## 1. What function #272 is

Identified with TeaVM's own debug info — not the old
`DebugInformationReader` (which crashes on `.teadbg`; see §6), but the
WasmGC debug section parsers (`DebugStringParser`, `DebugFileParser`,
`DebugClassParser`, `DebugMethodParser`, `DebugLinesParser` fed through
`ExternalDebugFile.read`). Tool: `fn272/DumpDebug.java`.

- Function #272 = **defined function #213** (59 function imports),
  type `func (ref null 917, ref null 467) -> i32`.
- Line info: **`org.audiveris.omr.sig.inter.AbstractInter.contains(Point)`**,
  body at file offsets [997029..998201), 1,172 bytes.
- Params: local 0 = `this` (type 917 = `AbstractInter`), local 1 = `point`
  (type 467 = `java.awt.Point`).

The failing call site, from the disassembly (`fn272/disasm.py`,
`fn272/fn272.txt`), is the `area.contains(point)` branch
(`AbstractInter.java:355`):

```
997982  local.get 0
997984  struct.get 917 16        ; area field (type 903 = java.awt.geom.Area)
997989  local.tee 7
997991  local.get 1             ; point (type 467 = java.awt.Point)
997993  local.get 7             ; area
997995  struct.get 5 0          ; area vtable
997999  ref.cast ht_idx(3194)
998003  struct.get 3194 16      ; vtable slot 16 = Area.contains funcref
998008  i32.const 9             ; suspension-point state
998010  local.set 10
998012  br 1
998014  end
998015  ref.null ht_idx(903)
998018  ref.null ht_idx(467)
998021  local.get 9
998023  call 62
998025  ref.cast_null ht_idx(3190)
998029  end
998030  local.tee 11
998032  ref.cast_null ht_idx(3190)
998036  call_ref 3190           ; type 3190 = func (ref null 903, ref null 48) -> i32
```

Type 3190 is `Area.contains(Point2D)`: params `(Area, Point2D)`. The
second argument on the stack is the `Point` (467). The suspension
continuation block at the reported offset 997174 has block type 8604 =
`func [] -> (ref null 903, ref null 467, ref null 3190)` — exactly the
three values the `call_ref` consumes (area, point, funcref) — which is
why Node reports the block as the source of the mismatched value, and
why `[1]` in `call_ref[1]` is the parameter index (the 2nd parameter,
0-based index 1). The other two calls in the same method are fine:
`bounds.contains(point)` is `Rectangle.contains(Point)` (type 1920,
params `(449, 467)` — Point matches) and `glyph.contains(point)` takes
a `Point` (type 941, params `(5, 467)`).

Type identities (binary type indices, verified with `fn272/typemap.py`
against the module itself — note Binaryen's `wasm-dis` renumbers types,
so its `$N` labels do NOT match binary indices):

| binary type | shape | Java class |
|---|---|---|
| 5 | struct [vtable, eqref], root | `java.lang.Object` |
| 48 | struct [vtable, eqref], sub of 5 | `java.awt.geom.Point2D` (shim, abstract, no fields) |
| 467 | struct [vtable, eqref, i32, i32], sub of 5 | `java.awt.Point` (TeaVM `TPoint`) |
| 449 | struct [vtable, eqref, i32×4], sub of 53 | `java.awt.Rectangle` (shim, extends `Rectangle2D`) |
| 903 | struct [vtable, eqref, ref 607], sub of 5 | `java.awt.geom.Area` (shim) |
| 917 | struct, sub of 806 | `AbstractInter` (field 5 = glyph, 13 = bounds `Rectangle`, 16 = area) |
| 808 | struct, sub of 807 | `Glyph` |
| 3190 | func `(903, 48) -> i32` | `Area.contains(Point2D)` |
| 1920 | func `(449, 467) -> i32` | `Rectangle.contains(Point)` |
| 941 | func `(5, 467) -> i32` | `contains(Point)` (Glyph/Inter) |
| 450 | func `(5) -> 449` | `getBounds()` |

`TPoint` verified by `javap` on `lib/teavm-classlib-0.16.0.jar`
(`org/teavm/classlib/java/awt/TPoint.class`): `public class TPoint {
public int x; public int y; public TPoint(int, int); }` — superclass
`java.lang.Object`, no `getX`/`getY`/`distanceSq`/`translate`. `TDimension`
is the same shape (width/height, extends `Object`).

## 2. Minimal repro (reproduces the exact error without Audiveris)

In `fn272/repro/` (`Repro.java`, `ReproFixed.java`, build log
`build-repro.log`, outputs `out/` and `out-fixed/`). Built with the same
driver as TeavmMain (`Spike.java`, TeaVMTool, WasmGC, SIMPLE), classpath
`repro/classes + shims.jar + teavm-classlib/core/interop/jso*/metaprogramming*/platform-0.16.0.jar`.
The shim `java.awt.geom.Area`/`Point2D` come from `shims.jar`; `Point`
comes from TeaVM's classlib.

```java
package repro;
import java.awt.Point;
import java.awt.geom.Area;
import java.awt.geom.Point2D;

public class Repro {
    static boolean check(Area area, Point p) { return area.contains(p); }
    static boolean checkViaPoint2D(Area area, Point p) {
        Point2D p2d = p; return area.contains(p2d);
    }
    public static void main(String[] args) {
        Area area = new Area();
        Point p = new Point(3, 4);
        System.out.println("direct: " + check(area, p));
        System.out.println("via Point2D local: " + checkViaPoint2D(area, p));
    }
}
```

Result (Node v24.22.1, 99 classes, 1,123 methods, 18,249-byte module):

```
WebAssembly.compile(): Compiling function #197 failed:
call_ref[1] expected type (ref null 22), found local.get of type (ref null 90) @+12772
```

Function #197 (via the same `DumpDebug` tool on the repro `.teadbg`) is
`repro.Repro.check`. Types 22/90 in the repro module are the same shapes
as 48/467 (Point2D / Point, both sub of Object). The "found" value is a
`local.get` here rather than a `block` only because the small repro has
no suspension point; the root cause is identical.

Control (`ReproFixed.java`, same program but
`area.contains(p.x, p.y)` — the `(double, double)` overload):

```
FIXED VARIANT: LOADED OK
```

So the mechanism is proven in isolation: **any** `Point` passed where a
`Point2D` is expected makes TeaVM 0.16.0 WasmGC emit an invalid module.
`checkViaPoint2D` (assigning a `Point` to a `Point2D` local) is the same
defect in assignment form; Node reports the first failing function
(`check`), so the assignment form was not separately load-tested — but
§4 shows it occurring for real in `Staff.getConcreteLedgerNearby`.

## 3. Is it fixed in 0.17.0? — No (classlib checked)

- 0.17.0 is not on Maven Central. The pre-release line is
  `0.17.0-dev-1` (Oct 7, 2026) / `0.17.0-dev-2` (Oct 12, 2026), published
  on TeaVM's own Maven repo (`https://teavm.org/maven/repository/`).
- Downloaded `teavm-classlib-0.17.0-dev-2.jar` from that repo and inspected
  `TPoint.class` / `TDimension.class`: **unchanged** — `TPoint` still
  extends `Object`, only `x`/`y` + `(int, int)` ctor; `TDimension`
  unchanged too. Since the emitted struct supertype comes from the
  classfile's superclass, the bug persists in 0.17.0-dev-2.
- Caveat: only the classlib artifact was checked, not a full dev-2
  compile of the repro. A compiler-side fix that rewrote the hierarchy
  is implausible (the hierarchy comes from the classfiles), but the
  full dev-2 repro run was not done.

## 4. Workaround tested: per-call-site rewrites — works, but whack-a-mole

Because a user-supplied `java.awt.Point` does **not** override the
classlib (see §5), the only workaround available to us is to stop
passing `Point` where `Point2D` is expected, call site by call site:
use the `(double, double)` overload where one exists
(`area.contains(p.x, p.y)`), or wrap in `new Point2D.Double(x, y)`.

Script: `fn272/apply_272_workaround.py` (idempotent; applies the fixes
to `patched/src/` only — the repo is never touched, `make_patches.py`
is not modified). It also copies five Audiveris sources that live only
in `audiveris.jar` into `patched/src/` so the fixed versions shadow the
jar (`patched/classes` precedes it in `teavm-main-jars.txt`).

Each iteration: apply fixes → recompile `patched/classes` (javac, same
command as `build.sh` step 2) → `Spike wasm-gc out-teavm-fixedN
teavm.TeavmMain @teavm-main-jars.txt @program-jars.txt` (~120 s) →
`WebAssembly.compile` in Node. Node stops at the **first** failing
function, so each iteration reveals exactly one more instance:

| iteration | fixed in that round | Node result |
|---|---|---|
| 0 (original `out-teavm/`) | — | `#272 AbstractInter.contains` — `area.contains(point)` |
| 1 (`out-teavm-fixed/`) | 7× `area.contains(Point)` → `(x, y)` (AbstractInter, LedgerInter, AbstractHorizontalInter, SIGraph, CurvesBuilder, SlurLinker, WedgesBuilder) | `#1463 ShapeSymbol.buildImage` — `paint(g, p, new Point(0, 0), TOP_LEFT)` where `paint(..., Point2D location, ...)` |
| 2 (`out-teavm-fixed2/`) | `paint(..., new Point2D.Double(0, 0), ...)`; `ObjectEditor` `area.contains(newPt)` → `(x, y)` | `#2056 Staff.addLedger` — `pitchPositionOf(center)` where `center` is a `Point` and `pitchPositionOf(Point2D)` is virtual (overridden by OneLineStaff, Tablature) |
| 3 (`out-teavm-fixed3/`) | `Staff.addLedger`: wrap in `new Point2D.Double(center.x, center.y)` | `#2115 Staff.getConcreteLedgerNearby` — `final Point2D center = ledger.getCenter();` (`getCenter()` returns `Point`) — a `local.tee` shape, not a call argument |

10 sites fixed in total; the module **still does not load** after
iteration 3. The defect class is not limited to call arguments:
assignments (`Point2D x = <Point>`), and by the same logic returns and
field stores, are all affected. Other untested-but-suspicious reachable
sites of the same class (found by grep, not yet hit by Node):
`Line2D.relativeCCW` / `ptLineDist` / `ptSegDist` with `Point` args
(`Circle.java:256`, `CubicUtil.java:69`, `BeamsBuilder.java:1314`,
`SlurHeadLink.java:111`, `HeadLinker.java:1803`, `StemBuilder.java:1004`,
`AbstractBeamInter.java:701`), `Point2D.setLocation(Point)` /
`distanceSq` / `distance` call sites, and any `Dimension` passed where
`Dimension2D` is expected (same `TDimension` defect). Sites already
checked and **fine** (argument already declared `Point2D`/`Rectangle`):
`StaffManager.getStavesOf`, `SystemManager` (both), `BeamsBuilder:1490`,
`LedgersBuilder:219`, `BeamLinker`/`HeadLinker` `getReferencePoint()`,
`Skew.deskewed`/`skewed`, `SIGraph.containingInters` bounds check,
`Rectangle.contains(Point)` call sites, `Area.contains(double, double)`
call sites.

**Conclusion on the workaround:** it is viable in principle (each fixed
site stays fixed; the module gets one failure further each time) but
impractical as the path forward — the count is unbounded by inspection
and each iteration costs a ~2-minute recompile plus a load test. The
systematic fix is upstream: make `TPoint extend Point2D` (and
`TDimension extend Dimension2D`), implementing the abstract
`getX`/`getY`/`setLocation` methods. Until then, WasmGC cannot compile
this program.

State left behind: the workaround is applied in the spike's
`patched/src/` only (10 fixes + 5 copied-in files:
`LedgerInter`, `AbstractHorizontalInter`, `SIGraph`, `ObjectEditor`,
`Staff`; `ShapeSymbol` was already managed there). `patched/classes`
currently reflects iteration 3. `out-teavm/` (original failing build)
is untouched; fixed builds are in `out-teavm-fixed/`,
`out-teavm-fixed2/`, `out-teavm-fixed3/`. Re-running `build.sh`
regenerates `patched/src` from `make_patches.py` and **wipes the
workaround** (the fixes are not in `make_patches.py`); re-run
`fn272/apply_272_workaround.py` afterwards to re-apply.

## 5. A user Point shim does NOT override the classlib (tested)

Hypothesis: supply our own `java.awt.Point extends Point2D` on the
program classpath and TeaVM would use it instead of `TPoint`.
Test: `fn272/repro/src2/java/awt/Point.java` (extends the shim
`Point2D`, `x`/`y`, ctors, `getX`/`getY`/`setLocation`), compiled to
`fn272/repro/point-shim.jar`, placed **first** on the TeaVM classpath
for the repro. Result: **identical failure** —
`Compiling function #197 failed: call_ref[1] expected type (ref null 22),
found local.get of type (ref null 90) @+12772` — same function, same
offset, same types. TeaVM used its classlib `TPoint` regardless of
classpath order. This confirms `TEAVM_SPIKE.md` §3.2's finding for
`Color`/`Point`/`Dimension`: for classes the classlib defines, the
classlib version wins. There is no one-file shim workaround.

## 6. How #272 was identified (method notes — for the next person)

- The earlier worker's claim that `DebugInformationReader` crashes on
  `.teadbg` was a red herring in one respect: that reader is for the
  *old* debug format, not the `.teadbg` container. The `.teadbg` file
  is an `ExternalDebugFile` container (`tdbg` magic, version 1) holding
  sections `teavm_str`, `teavm_file`, `teavm_pkg`, `teavm_cls`,
  `teavm_mtd`, `teavm_line` — notably **no** `teavm_cll` (class layout)
  and no `teavm_var`, so type-index → class-name mapping is not in the
  debug info. `fn272/DumpDebug.java` feeds those sections to TeaVM's
  own WasmGC debug parsers and looks up a file offset in the line-info
  sequences; the sequence whose range contains the offset names the
  method. The class names for the wasm types were then established from
  struct shapes + the vtable/field layout + the source, cross-checked
  against `javap` of the classlib/shim classfiles.
- `fn272/disasm.py` is a minimal single-function Wasm disassembler
  (locals + GC opcodes) that prints file offsets; `fn272/typemap.py`
  dumps selected entries of the binary type section. Both were needed
  because Binaryen's `wasm-dis` **renumbers types** (it inserts
  synthetic tuple types for the coroutine transform), so its `$N` type
  labels do not match the binary's type indices. Its instruction
  stream is correct; its type numbers are not.
- V8's message format, as observed: `call_ref[<param index>] expected
  type <expected>, found <producer> of type <actual> @+<offset>`, where
  `<producer>` is the instruction that produced the value (`block`,
  `local.get`, `local.tee`) and the offset is that instruction's file
  offset. The ADVANCED-mode error recorded in `TEAVM_SPIKE.md` §5
  (`function #273`, `call[1] expected type (ref null 49), found block of
  type (ref null 462)`) is the same root cause: in the ADVANCED module,
  types 49/462 have exactly the Point2D/Point shapes (verified with
  `typemap.py`); only the numbering differs.
- `FULL` optimisation crashing TeaVM itself (`spike-teavm-full.log`) is
  a separate, unrelated TeaVM bug and was not re-investigated here.

## 7. What this does NOT block / next steps if WasmGC stays blocked

- The JavaScript backend is **not** affected by this defect (no WasmGC
  validation), but it is not a proven fallback either: the JS build ran
  ~47 minutes with zero output and never reached HEADS (>90× slower
  than native 30.8 s LOAD→HEADS, or stuck). Say that plainly; do not
  present JS as the fallback.
- CheerpJ remains the working path for the app (it runs real JDK
  classes, where `Point extends Point2D`). The TeaVM work stays a
  self-hosting investigation, not a migration, until the upstream
  hierarchy is fixed or the per-site workaround is carried to
  completion (unknown number of further iterations).
- If the upstream fix lands: re-run the repro (`fn272/repro/`, see its
  README) against the new TeaVM version first (seconds, not minutes),
  then recompile TeavmMain from the *unmodified* patched tree and load
  it in Node. If the per-site workaround is pursued instead: fold
  `fn272/apply_272_workaround.py`'s fixes into `make_patches.py`
  properly, then iterate Node failures until the module loads — and
  expect further instances beyond the four named here, including
  assignment/return/field-store shapes, not just call arguments.
- The issue draft is in `ISSUE_272_DRAFT.md` (ready to file from
  Jonathan's machine; do NOT file it from the `robbie-muse` account
  without explicit approval).
