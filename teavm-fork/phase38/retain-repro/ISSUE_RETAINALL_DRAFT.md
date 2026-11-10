# DRAFT — TeaVM issue: `ArrayList.retainAll` / `removeAll` throw `ConcurrentModificationException` for a self-`subList` argument

**Status: DRAFT ONLY — do not file yet.** Jonathan's decision (2026-11-09): upstream
TeaVM filings wait until our own work is done. Prepared 2026-11-10 as part of
score-reader TeaVM spike §33.

## Summary

TeaVM 0.16.0's classlib `ArrayList` does not override `retainAll`/`removeAll`,
so it inherits the iterator-based `TAbstractCollection.retainAll`. Calling
`list.retainAll(list.subList(0, i))` — legal JDK code, used in nine places in
Audiveris — throws `ConcurrentModificationException` under TeaVM, while the
JDK completes it and truncates the list to its first `i` elements.

## Minimal repro

`phase38/retain-repro/src/repro/RetainRepro.java` (stock TeaVM 0.16.0 classlib,
no fork patches, no Audiveris code):

```java
List<String> peaks = new ArrayList<>();
for (int i = 0; i < 11; i++) {
    peaks.add("p" + i);
}
peaks.retainAll(peaks.subList(0, 5));   // JDK: size 5, [p0..p4]
                                        // TeaVM: ConcurrentModificationException
```

Build (from `teavm/`, JDK 25):

```bash
J=~/workspace/score-reader-spikes/repo/.work/jdk/jdk-25.0.5+8/bin
$J/javac -d phase38/retain-repro/classes phase38/retain-repro/src/repro/*.java
$J/java -Xmx3g -cp "lib/*:classes" Spike wasm-gc phase38/retain-repro/out \
  repro.RetainRepro phase38/retain-repro/classes \
  lib/teavm-classlib-0.16.0.jar lib/teavm-core-0.16.0.jar \
  lib/teavm-interop-0.16.0.jar lib/teavm-jso-0.16.0.jar \
  lib/teavm-jso-apis-0.16.0.jar lib/teavm-jso-impl-0.16.0.jar \
  lib/teavm-metaprogramming-api-0.16.0.jar \
  lib/teavm-metaprogramming-impl-0.16.0.jar lib/teavm-platform-0.16.0.jar
node phase38/retain-repro/run-repro.mjs out
```

Observed (Node v24.22.1):

```
before size=11
CAUGHT java.util.ConcurrentModificationException: null
```

Same program on the JDK:

```
before size=11
after size=5 peaks=[p0, p1, p2, p3, p4]
RETAIN-REPRO OK (no exception)
```

The same defect affects `removeAll` with a self-`subList` argument (same
inherited implementation); the repro also covers `removeAll(Arrays.asList(...))`
and `removeAll(self-subList)`.

## Root cause

`TAbstractCollection.retainAll` (`classlib/src/main/java/org/teavm/classlib/
java/util/TAbstractCollection.java:123`):

```java
for (TIterator<E> iter = iterator(); iter.hasNext();) {
    E e = iter.next();
    if (!c.contains(e)) {
        iter.remove();
        changed = true;
    }
}
```

With `c` a `subList` view of the same list, `c.contains(e)` iterates the
subList, whose iterator checks `modCount`. The outer iterator's `remove()`
bumps `modCount`, so the next `contains()` call throws. The JDK's
`ArrayList` avoids this entirely: it overrides `retainAll`/`removeAll` with
`batchRemove`, which scans the backing array by index and never creates an
iterator — and it does not touch `modCount` until the end, so no iterator
ever observes a concurrent modification. TeaVM's `TArrayList` has no such
override.

Note the JDK's own `AbstractCollection.retainAll` is iterator-based too — the
contract difference is specifically that `ArrayList` overrides it. (For a
`LinkedList`, the JDK throws here as well, so this is an `ArrayList`
fidelity gap, not a general `Collection` one.)

## Proposed fix

Port OpenJDK `ArrayList.batchRemove` into `TArrayList`, with `retainAll` →
`batchRemove(c, true)` and `removeAll` → `batchRemove(c, false)` — exactly
the JDK's call shape. Verified in our fork (`teavm-classlib-retainall.patch`,
67 lines, `TArrayList.java` only): the repro then prints the JDK-identical
output under TeaVM, and the Audiveris pipeline that hit this defect
(`HiLoPeakFinder.findPeaks` quorum branch, plus eight sibling call sites in
`ScaleBuilder`, `ClefBuilder`, `KeyBuilder` ×5, `KeyExtractor`) completes.

## Impact in the wild

Audiveris (AGPL) calls `list.retainAll(list.subList(0, i))` in nine places;
any TeaVM user running Audiveris — or any code with this idiom — hits a
`ConcurrentModificationException` that does not exist on the JDK. The failure
is data-dependent (the branch only executes when the truncation point is
reached with at least two elements after it), so it surfaces as an
intermittent-looking crash on some inputs and not others.
