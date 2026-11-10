<!--
DRAFT — ready to file at https://github.com/konsoletyper/teavm/issues
(new issue). Do NOT file from the robbie-muse account without explicit
approval; Jonathan / his agent files it from his machine.

Everything below the line is the issue body as it should be filed.
The minimal repro lives in fn272/repro/ (Repro.java, ReproFixed.java);
attach that folder or link to it when filing.
-->

---

## Title

WasmGC backend emits invalid module when a `java.awt.Point` is passed where a `java.awt.geom.Point2D` is expected (classlib `TPoint` does not extend `Point2D`)

## Summary

TeaVM 0.16.0 (WasmGC backend) compiles a program that passes a
`java.awt.Point` to a method expecting a `java.awt.geom.Point2D` into a
WebAssembly module that Node rejects at validation time:

```
CompileError: WebAssembly.compile(): Compiling function #197 failed:
call_ref[1] expected type (ref null 22), found local.get of type (ref null 90) @+12772
```

In real Java this code is legal: `Point extends Point2D`. In TeaVM's
class library, `TPoint` (`org.teavm.classlib.java.awt.TPoint`) extends
`java.lang.Object` directly — it is not a subtype of `Point2D`. TeaVM
therefore emits the two classes as **sibling** WasmGC struct types
under `Object`, and the resulting `call_ref` passes a `Point` value
where a `Point2D` is declared. The module is invalid; nothing runs.

This is not specific to one call site. In a large real program
(Audiveris 5.11, compiled headless) the same defect surfaced in four
different functions in sequence — Node stops at the first failure, so
fixing one call site just exposes the next. The defect also occurs for
assignments (`Point2D x = <Point>`), not only call arguments.

## Minimal repro

```java
import java.awt.Point;
import java.awt.geom.Area;
import java.awt.geom.Point2D;

public class Repro {
    static boolean check(Area area, Point p) {
        return area.contains(p);          // Point passed where Point2D is expected
    }
    public static void main(String[] args) {
        System.out.println(check(new Area(), new Point(3, 4)));
    }
}
```

Notes on the repro:

- `Area` / `Point2D` here are minimal hand-written stand-ins (the
  real JDK classes are not in TeaVM's classlib); `Point` comes from
  TeaVM's classlib (`TPoint`). The point is the *hierarchy*: any
  method whose parameter type is `Point2D`, called with a `Point`,
  triggers it.
- Compiled with `TeaVMTool`, target `WEBASSEMBLY_GC`, optimisation
  `SIMPLE`, debug info on. 99 classes, 1,123 methods, 18,249-byte
  `classes.wasm`.
- Node v24.22.1: `WebAssembly.compile()` fails as above. Function #197
  is `Repro.check`; in the repro module type 22 is `Point2D` (struct
  with no fields beyond vtable) and type 90 is `Point` (struct with two
  `i32` fields), both direct subtypes of `Object`.
- Control: replacing the call with the `(double, double)` overload —
  `area.contains(p.x, p.y)` — compiles to a module Node accepts.

A second form, also reproduced in the real program:

```java
Point2D p2d = p;              // assigning a Point to a Point2D local
return area.contains(p2d);
```

In the real program this appears as `final Point2D center =
ledger.getCenter();` (`getCenter()` returns `Point`) and fails as
`local.tee[0] expected type (ref null 48), found local.tee of type
(ref null 467)`.

## Expected vs actual

- Expected: the compiled module validates and runs. `Point` is a
  `Point2D` in Java, so passing it where a `Point2D` is expected must
  be representable in the emitted WasmGC types.
- Actual: TeaVM emits an invalid module. Node (and any spec-conformant
  engine) rejects it at load time, before any code executes.

## Root cause (as diagnosed)

`TPoint` and `TDimension` in `teavm-classlib` do not mirror the JDK
hierarchy:

```java
// teavm-classlib, org/teavm/classlib/java/awt/TPoint.java
public class TPoint {
    public int x;
    public int y;
    public TPoint(int, int) { ... }
}
```

`javap` on `teavm-classlib-0.16.0.jar` confirms: superclass is
`java.lang.Object`; there are no `getX`/`getY`/`distanceSq`/`translate`
methods. The JDK declares `public class Point extends Point2D`.
Because the emitted WasmGC struct's supertype comes from the
classfile's superclass, `Point`'s struct subtypes `Object`, not
`Point2D` — while the shim/real `Point2D` is a separate struct that
also subtypes `Object`. Every use of a `Point` as a `Point2D` (call
argument, assignment, return, field store) is then ill-typed in the
emitted module. `TDimension` has the identical defect with respect to
`Dimension2D`.

Related observations from the real program (Audiveris 5.11, 2,863
classes, 27,661 methods, headless entry point):

- Original failure: `AbstractInter.contains(Point)` calling
  `area.contains(point)` — function #272, `call_ref[1] expected type
  (ref null 48), found block of type (ref null 467) @+997174`. The
  "block" is TeaVM's suspension-continuation block for that call site;
  its type is a multi-value block yielding exactly the call's operands
  `(Area, Point, funcref)`.
- After fixing that site, Node reported in turn:
  `ShapeSymbol.buildImage` (`paint(..., new Point(0, 0), ...)` where
  `paint(..., Point2D location, ...)`), `Staff.addLedger`
  (`pitchPositionOf(Point)` where `pitchPositionOf(Point2D)` is
  virtual), `Staff.getConcreteLedgerNearby` (`Point2D center =
  ledger.getCenter()`). Ten call sites were rewritten
  (`area.contains(p.x, p.y)`, `new Point2D.Double(x, y)`) and the
  module still does not load — the count of affected sites is not
  bounded by inspection.
- The same class of error occurs at `ADVANCED` optimisation (different
  function/type numbering, same Point2D/Point struct shapes).
- A user-supplied `java.awt.Point extends Point2D` on the program
  classpath does **not** override the classlib: with such a shim
  placed first on the classpath, the repro fails identically (same
  function, same offset, same types). There is no one-file workaround
  on the user side.

## Suggested fix

Make `TPoint extend Point2D` (and `TDimension extend Dimension2D`),
implementing the abstract `getX`/`getY`/`setLocation` methods (and the
corresponding `Dimension2D` abstract methods). That restores the JDK
hierarchy in the emitted WasmGC types, so a `Point` value is a subtype
of `Point2D` wherever it is used.

A secondary, smaller ask: TeaVM could reject this at compile time
instead of emitting an invalid module — if an argument/local/return
value's type is not a subtype of the declared type in TeaVM's own
class model, that is a compilation error, not something to discover in
the browser.

## Versions

- TeaVM 0.16.0 (also checked: `teavm-classlib-0.17.0-dev-2` from
  `https://teavm.org/maven/repository/` — `TPoint`/`TDimension` are
  unchanged there, so the defect persists in the dev line as of
  Oct 12, 2026; only the classlib artifact was checked, not a full
  dev-2 compile).
- Node v24.22.1, Java 25 (temurin jdk-25.0.5+8), Audiveris 5.11.
- Repro compiled with `org.teavm.tooling.TeaVMTool`,
  `TeaVMTargetType.WEBASSEMBLY_GC`, `TeaVMOptimizationLevel.SIMPLE`.

## Why it matters

Any Java program that uses `java.awt.Point` with the `java.awt.geom`
API (`Area`/`Shape`/`Path2D`/`Rectangle2D.contains`,
`AffineTransform.transform`, `Line2D` distance methods,
`paint(..., Point2D, ...)`, `pitchPositionOf(Point2D)`, …) — i.e.
ordinary, legal Java 2D code — cannot currently be compiled to
working WasmGC by TeaVM. The failure is at load time with no
workaround short of rewriting every affected use in the application,
and there is no compile-time diagnostic pointing at the cause.
