# TeaVM spike for Audiveris-in-WASM — results

Date: 2026-11-08. Worker: subagent (parallel with the CheerpJ worker).
Repo: `jdlehman/score-reader`, HEAD 6602144, Audiveris 5.11, read-only clone at
`~/workspace/score-reader-spikes/repo/`. Work folder (only folder written):
`~/workspace/score-reader-spikes/teavm-wasm/teavm/`.

## TL;DR

- **TeaVM 0.16.0** (released Oct 3, 2026) is the latest stable on Maven Central;
  0.14.1 is superseded. `teavm-cli` is discontinued after 0.14.1; `teavm-tooling`
  0.16.0 is the programmatic driver. Java 25 is supported; minimum JDK to run
  TeaVM is 17. WasmGC has coroutine (thread) support since 0.13.
- **Bench reproduced in spirit, not in absolutes.** Desktop Java 707 ms vs
  TeaVM/WASM 1,072 ms (1.5x) from 2026-11-03 is not reproducible as absolute
  times on this loaded host (desktop best 1,860 ms, WASM best 1,863 ms over
  9/6 runs; medians ~3,000/~2,700 ms). Best-of-best ratio ≈ **1.00x**;
  the 1.5x figure is consistent only with the upper end of the WASM range
  vs the lower end of desktop. Check value 1565550 identical on both.
- **Audiveris 5.11 `Main` under TeaVM 0.16.0: 635 problems** (vs 361 reported
  2026-11-03 with 0.14.1). Same practical outcome (no wasm written), more
  problems — mostly because 0.16.0 reports more, not because Audiveris got worse.
- **Headless/batch TeavmMain: 0 problems after 7 rounds of shimming.**
  TeaVM 0.16.0 WasmGC compiles it: **2,863 classes, 27,661 methods,
  10,190,425-byte `classes.wasm`** — but **Node 24 refuses to load it**:
  `WebAssembly.compileStreaming(): Compiling function #272 failed:
  call_ref[1] expected type (ref null 48), found block of type
  (ref null 467) @+997174`. This is a TeaVM WasmGC codegen bug (see §5).
- **Same entry point under TeaVM 0.16.0 JavaScript backend: 0 problems,
  compiles, main() executes — but never completes.** The JS run ran
  ~47 min wall at 85–99% CPU and was terminated with zero output; it
  never reached HEADS (native LOAD→HEADS is 30.8 s, so JS is >90×
  slower here or stuck — see §6). It is NOT a proven fallback.
- **Verdict: investigate further — WasmGC is NOT a drop-in win yet.**
  The class-library gap is closable (done here), but the WasmGC backend
  itself miscompiles this program. Effort to first page: see §7.
- **Why TeaVM matters here (clarified 2026-11-08): vendor
  independence first, speed second.** Jonathan's main reason for
  wanting off CheerpJ is NOT speed — it is that CheerpJ Community
  requires loading the runtime from Leaning Technologies' CDN
  (`cjrtnc.leaningtech.com`), self-hosting is not on the free tier
  (£5,000/yr + Small Business), and Leaning could narrow the tier,
  change terms, or shut the CDN path and break the app on their
  timetable. TeaVM's value is primarily as a **self-hosted exit**:
  Apache-2.0, static Wasm/JS output the site hosts itself, no vendor
  server in the loop, no licence server. The ~1–3× speed is
  secondary. See §7 for the full reframing.

## 1. TeaVM version

- Latest stable: **0.16.0** (Oct 3, 2026). Dev: 0.17.0-dev-2 (Oct 12, 2026,
  not on Maven Central — only the 0.16.0 line is published there).
- Downloaded via a custom Python resolver (`resolve.py`, curl + POM parsing,
  because Maven's HTTP client cannot use the egress proxy): **29 artifacts**
  in `teavm/lib/`, all 0.16.0 except ASM 9.10.1, hppc 0.10.0, rhino 1.9.1,
  jzlib 1.1.3, joda-time 2.12.2.
- Release notes (0.16.0): minimum JDK to run TeaVM bumped to 17; **Java 25
  supported**; WasmGC coroutine support (threads) since 0.13; WasmGC now
  intrinsifies `Math.min`/`Math.max`; C backend closer to parity.

## 2. Reproduction

### 2.1 Bench (Bench.java, chamfer + template matching on plain arrays)

| | desktop Java 25 | TeaVM 0.16.0 WasmGC / Node 24 |
|---|---|---|
| runs (ms) | 1860, 2079, 2175, 2998, 3016, 3108, 3177, 3299, 3370 | 1863, 2318, 2340, 3105, 3391, 5806 |
| best | 1860 | 1863 |
| median | ~3016 | ~2722 |
| check | 1565550 | 1565550 (identical) |

Best-of-best ratio ≈ 1.00x; median ratio ≈ 0.9x. The 2026-11-03 numbers
(707 vs 1072 ms) are not reproducible as absolutes here — this host is
loaded/shared (parallel worker, load avg up to 8.6) and absolute times run
2.6–4.3x slower. Honest conclusion: on this host both land in the same
band; TeaVM is not slower than desktop here. Bench compiled cleanly:
82 classes, 899 methods, 0 problems, ~11 s compile.

### 2.2 Spike on Audiveris 5.11 Main (TeaVM 0.16.0, wasm-gc)

Command: `java -Xmx3g -cp "lib/*:classes" Spike wasm-gc out-audiveris
org.audiveris.omr.Main @teavm-jars.txt @program-jars.txt` (57 jars from
`repo/.work/lib/`). Took 2m08s. **635 ERROR problems, 0 warnings** (vs 361
on 2026-11-03 with 0.14.1). No `classes.wasm` was written (`out-audiveris/`
empty) despite the log line `generated: [out-audiveris/classes.wasm]`.
0.16.0 does not crash with an exception; it completes and reports
"Output file built with errors" while writing nothing.

Grouping (saved in `problems-grouped.txt`; raw log is `problems.txt`,
638 lines): 504 missing-class refs (105 distinct classes), 104
missing-method refs (42 distinct methods), 27 missing-field refs
(14 distinct fields). Distinct locations: 325.

Missing-class refs by package: java.awt 183, java.util 72, javax.swing 63,
javax.xml 50, java.lang 46, javax.imageio 40, java.nio 19, com.jcraft 14
(jzlib — not in `.work/lib`, only arrived as a TeaVM dep), org.xml 7,
java.beans 4, java.security 4, java.net 1, java.io 1.

Most-missing classes: `javax.xml.namespace.QName` 41,
`java.awt.font.TextLayout` 33, `java.awt.image.BufferedImage` 29,
`java.awt.Rectangle` 28, `java.lang.UnsatisfiedLinkError` 22,
`java.util.concurrent.locks.Lock` 20, `java.awt.geom.Point2D$Double` 19,
`ReentrantLock` 18, `javax.imageio.ImageReader` 16,
`javax.swing.SwingUtilities` 15, `java.awt.Font` 14, `ImageInputStream` 14,
`ConcurrentSkipListMap` 12, `FileChannel` 11, `java.lang.Module` 11.

**Problem-count caveat (verified empirically):** TeaVM undercounts.
A test with 3 missing `Color` fields in one method reported only 1 problem
(the first). It appears to dedupe per (method, missing class) for fields.
635 is a lower bound, especially for fields.

### 2.3 Native desktop baseline (hymn-satb.png, 2480×3507)

`java -Xmx4g org.audiveris.omr.Main -batch -constant
TesseractOCR.useOCR=false ... -transcribe -export` (no Tesseract on the VM,
so lyrics 0/64). **68.4 s wall, exit 0, .mxl exported** (second timed run;
first ~71 s). Much slower than SPIKES.md's 19–31 s/page — same loaded-host
effect as the bench. Per-step timings (2026-11-08 run): LOAD ~1.4 s,
BINARY 0.9, SCALE 0.08, GRID 3.1, HEADERS 3.7, STEM_SEEDS 1.2, BEAMS 9.7,
LEDGERS 0.9, HEADS 10.2, STEMS 4.5, REDUCTION 0.8, TEXTS 0.08 (OCR off),
CURVES 3.9, SYMBOLS 5.4, LINKS 0.3, RHYTHMS 0.5. **LOAD→HEADS ≈ 30.8 s.**
Ground truth verified from the native .mxl: 128 `<note>` elements, 2 parts,
16 measures per part. Native outputs in `native-out/hymn-satb/` and
`native-out/hymn-satb2/`.

## 3. What was tried — the headless TeavmMain path

Entry point `teavm.TeavmMain` (in `entry/src/teavm/`): fetches the grey
image bytes from JS (`globalThis.__greyB64/__greyW/__greyH`, base64),
builds a `TYPE_BYTE_GRAY` BufferedImage, `new Book(Paths.get(...))`,
`book.addStub(new SheetStub(book, 1))`, `stub.reachStep(OmrStep.HEADS,
false)`, prints REACH HEADS ms, LATEST STEP, INTERLINE, SYSTEMS, STAVES,
HEADS, CHECK (sum of head bounds). OCR skipped (TEXTS is post-HEADS);
export skipped (JAXB); templates use stub fonts in Phase A (see §7).

### 3.1 Patches to Audiveris sources (patched/make_patches.py, rounds 1–6)

Patched sources are copied from `repo/.work/audiveris/` to `patched/src/`
(the repo is never touched) and compiled with the entry into
`patched/classes/` (240 classes). Highlights:

- `WellKnowns`: RES_URI null, folders null, `getGdkMaxScale()` → 1 (kills JNA).
- `LogUtil`, `StepMonitoring`: all methods no-ops (console logging instead
  of Logback/Swing).
- `LoadStep.doit`: image from `teavm.TeavmImageHolder` (cuts ImageIO/PDFBox).
- `SheetStub.loadGrayImage`: same; `doOneStep`: storeSheet block deleted.
- `OmrExecutors`: pools → inline `Executors` shim (single-threaded).
- `Sheet`/`Book`/`SheetStub`: all `OMR.gui` blocks deleted or neutered;
  `StubsController` gutted to a no-op same-API class.
- `ShapeClassifier.getInstance` → `teavm.StubClassifier` (returns empty
  evaluations; the neural model cannot load in WASM).
- `TextsStep`: doProlog returns an empty Context; doSystem no-op (no OCR).
- `Book`/`Sheet`/`BasicClassifier` JAXB entry points neutered (return null /
  no-op); `Constant.decodeColor` reimplemented by hand (TColor has no decode).
- `Color.white`/`Color.red`/`new Dimension(Dimension)` call sites fixed
  (TColor has only BLACK/WHITE; TDimension has no copy ctor).
- Repo's own patched sources (FastMath/FlatMorphology etc.) copied as-is
  for the 13 files the repo patch touches.

### 3.2 Shims (shims/src/, shims.jar, ~290 KB)

~200 stub classes in 4 generators (`shims/gen_awt.py`, `gen_awt2.py`,
`shims/gen_swing2.py`, `gen_swing3.py`) plus direct files. Built with a
multi-pass javac (`--patch-module java.base` / `java.desktop` /
`java.datatransfer` / `java.xml` — a single pass fails with 83 errors;
`java.awt.datatransfer` lives in its own module, not java.desktop).

Covered: SLF4J (console loggers); `java.util.concurrent` (inline executor,
no-op ReentrantLock, TreeMap-backed ConcurrentSkipListMap/Set,
ArrayDeque-backed LinkedBlockingQueue, SynchronousQueue); `java.awt.geom`
(full Point2D/Rectangle2D/Line2D/AffineTransform/Path2D/Area/Ellipse2D/
QuadCurve2D/CubicCurve2D/RoundRectangle2D/GeneralPath); `java.awt.image`
(real pixel storage: BufferedImage TYPE_BYTE_GRAY/INT with DataBufferByte/Int,
a real Graphics2D that draws into pixels); AWT widgets (Component/Container/
Window/Frame/Dialog/Canvas/Panel/Scrollbar/Menu*/TextArea/TextField/Checkbox/
Choice/FileDialog/GridBagLayout/Constraints/FontMetrics/SystemColor);
Swing (JComponent/AbstractButton/JButton/JLabel/JTabbedPane/JPanel/JTextField/
JList/JTable/JFileChooser/JOptionPane/UIManager/JDialog/TitledBorder/
AbstractAction/TransferHandler/HTMLEditorKit/StyleSheet + text/event/undo
subpackages); `javax.imageio` (+ metadata/IIOMetadata, ImageWriteParam);
`java.nio.channels.FileChannel/FileLock`; `java.security.MessageDigest`;
`java.net.URLClassLoader`; `java.io.Object{Input,Output}Stream`;
`java.applet.Applet`; `org.w3c.dom.*`; `java.lang.UnsatisfiedLinkError`,
`Process`, `PhantomReference`.

Key findings about the classlib (0.16.0, from inspecting
`teavm-classlib-0.16.0.jar`):
- **For any class TeaVM's classlib defines (Color, Point, Dimension), the
  classlib version wins regardless of classpath order** — a user-supplied
  `java.awt.Color` shim is ignored. TColor has only BLACK/WHITE, ctors,
  getRed/Green/Blue/Alpha. No lowercase `black`/`white`, no LIGHT_GRAY/
  MAGENTA/etc., no getRGB, no decode. Missing Color constants/methods must
  be fixed at call sites or by bytecode rewriting, NOT by shimming Color.
  (Same for TPoint: only x/y fields + (int,int) ctor — no getX/getY/
  distanceSq/translate; TDimension: width/height + (int,int) ctor.)
- Classlib lacks: ExecutorService/Executors/Future/ThreadPoolExecutor/
  ReentrantLock/Lock/ConcurrentSkipListMap/Set/LinkedBlockingQueue/
  CountDownLatch/Semaphore; File.toPath/canExecute/setExecutable;
  FileOutputStream/RandomAccessFile.getChannel; System.exit/getenv/load/
  loadLibrary/mapLibraryName; ClassLoader.getResource/getResources/loadClass;
  Class.getModule/getPackageName/getProtectionDomain/getResource;
  Runtime.exec/maxMemory; Module, UnsatisfiedLinkError, Process,
  PhantomReference; MethodHandles.lookup; Files.createSymbolicLink;
  Collections.emptySortedSet; Properties.store; Logger.setUseParentHandlers;
  java.text.AttributedCharacterIterator/CharacterIterator.

### 3.3 ASM jar rewrites (tools/RewriteJars.java, lib-patched/)

- **ij** (`ij-1.54p.jar`): Color field refs → TColor equivalents (incl.
  uppercase variants); `requestFocus()V` → `Component.requestFocus()Z` + POP
  (15 sites); 30 methods neutered whose bodies call classlib-missing members
  (Color.getRGB, ClassLoader.*, System.*, Runtime.*, Thread.*) in ij's
  GUI/macro/plugin machinery — none in the grey-image path. Frames are
  recomputed (COMPUTE_FRAMES) after the rewrite.
- **jgrapht**: `SupplierUtil.createSupplier` skips `Constructor.canAccess`.
- **bsaf** (`bsaf-1.9.2.jar`): ResourceMap string converters + loadImageIcon
  → return null (they call Color.decode, Dimension.setSize, Point.setLocation,
  ClassLoader.getResource).
- **jaxb-api** (`jaxb-api-2.3.1.jar`): all 5 `JAXBContext.newInstance`
  overloads → return null (many Audiveris classes call it in store/load
  methods TeaVM analyzes but the demo never calls).
- **jgoodies-forms**: Color field refs → TColor (FormDebugPanel used Color.red).
- **shims.jar (phantom)**: PhantomReference `super(referent)` → `super()`
  (TeaVM's TReference has only a no-arg ctor).

### 3.4 Problem-count trajectory (TeavmMain)

619 → 289 → 50 → **407** → 26 → 11 → **0**.

The round-4 rise is real and instructive: adding Frame/JPanel shims
unblocked analysis of whole subtrees TeaVM had been silently skipping
(a class with a missing superclass is not analyzed at all), so the "50"
was artificially low. Problem counts are not comparable across runs when
shims unblock new subtrees; the meaningful metric is whether the reading
path is clean. Final round (7): **0 problems** — `Output file successfully
built`, 2,863 classes, 27,661 methods.

## 4. Measured timings

Full numbers in `timings.json`. Summary:

| measurement | value |
|---|---|
| Bench desktop Java 25 (best / median of 9) | 1,860 / ~3,016 ms |
| Bench TeaVM 0.16.0 WasmGC / Node 24 (best / median of 6) | 1,863 / ~2,722 ms |
| Native Audiveris hymn-satb, wall (2 runs) | ~71 s, 68.4 s |
| Native Audiveris hymn-satb, LOAD→HEADS | ~30.8 s |
| TeaVM WasmGC compile of TeavmMain | ~120 s wall, 0 problems |
| TeaVM WasmGC classes.wasm | 10,190,425 bytes |
| TeaVM JS compile of TeavmMain | ~120 s wall, 0 problems |
| TeaVM JS classes.js | 8,765,986 bytes |

## 5. Blocker: WasmGC validation failure (function #272) — DIAGNOSED, see FN272_DIAGNOSIS.md

> **Update 2026-11-08:** the root cause is now known and is **not** a
> vtable/codegen confusion as hypothesised below. TeaVM's classlib
> `TPoint` (`java.awt.Point`) extends `Object`, not `Point2D`, so a
> `Point` passed where a `Point2D` is expected emits an invalid module.
> Full diagnosis, minimal repro, workaround iterations, and a
> ready-to-file TeaVM issue draft: `FN272_DIAGNOSIS.md`,
> `ISSUE_272_DRAFT.md`, `fn272/repro/`. The notes below are kept as the
> contemporaneous record; the diagnosis doc supersedes the hypothesis
> in the third bullet.

`node run-teavm.mjs` fails at load time, before any user code runs:

```
CompileError: WebAssembly.compileStreaming(): Compiling function #272 failed:
call_ref[1] expected type (ref null 48), found block of type (ref null 467) @+997174
```

Diagnosis performed (all in this session, evidence in the folder):

- The wasm is byte-identical across rebuilds (same size, same error, same
  offset) — deterministic TeaVM output, not a flaky build.
- Recomputing StackMapTable frames in the ASM-rewritten jars (ij, jgrapht)
  did NOT change the output — the stale-frames hypothesis is dead; TeaVM
  ignores frames for this.
- Parsed the wasm type section (31,811 types, 59 function imports, 33,065
  defined functions): function #272 is defined function #213, type
  `func(refnull:917, refnull:467) -> i32`. Type 48 and type 467 are both
  structs subtyping base struct 5 (fields `refnull:41, eqref`); 48 adds no
  fields, 467 adds two `i32` fields. The call_ref inside #272 expects a
  callee of type (ref null 48) but the block produces (ref null 467) — two
  sibling subtypes, so a vtable/call-site type confusion in TeaVM's
  WasmGC codegen for some virtual/interface call in this program.
- TeaVM's own debug reader (`DebugInformationReader`) crashes reading the
  emitted `classes.wasm.teadbg` (`ArrayIndexOutOfBoundsException` in
  `readRle`), and `setDebugInformationGenerated(true)` +
  `setWasmDebugInfoLevel(FULL)` adds no name section to the wasm — so the
  Java method behind #272 could not be named from the artifacts. (The
  `.teadbg` file is saved at `out-teavm/classes.wasm.teadbg` for a later
  attempt with a fixed reader or a newer TeaVM.)
- Maven Central's latest is 0.16.0; the 0.17.0-dev-2 build is not published
  there, so a "try the next dev version" step needs the snapshot repo.
- **Optimization level FULL is worse, not better:** `SpikeOpt` with
  `-Dteavm.opt=FULL` crashes TeaVM itself during optimization —
  `java.lang.RuntimeException: java.lang.AssertionError: Variable used
  before definition: @28 at $4` at `PhiUpdater.use` /
  `ScalarReplacement.performOnce`, while optimizing
  `ij.process.ImageProcessor.getStringBounds(String)` (log
  `spike-teavm-full.log`; nothing generated). ADVANCED was also tried:
  it compiles (2,863 classes, 27,661 methods, `classes.wasm` 7,426,043
  bytes, `out-teavm-adv/`, log `spike-teavm-adv.log`) but Node rejects it
  with the same class of error at a different function: `Compiling
  function #273 failed: call[1] expected type (ref null 49), found block
  of type (ref null 462) @+701262`. So the bug is in TeaVM's WasmGC
  codegen for this program at every optimization level, not in one
  optimization pass.
- The same entry point compiles cleanly under the **JavaScript backend**
  (0 problems), which isolates this to WasmGC codegen, not to the program
  or the shims.

What was NOT tried (no time left in this session): a minimal repro of
function #272 (bisect the entry point / classpath to isolate the Java
method), TeaVM 0.17.0-SNAPSHOT from the snapshot repo, a different
optimization level, or filing a TeaVM issue with the wasm attached.

## 6. JavaScript backend run

**Compiled: 0 problems.** "Output file successfully built". Classes
compiled: 2795. Methods compiled: 27246. `out-js/classes.js`
(8,765,986 bytes) + `classes.js.teavmdbg`. The JS output is a UMD module
(main exposed as `B.main`); it does not auto-run main on load.

`run-js2.mjs` sets the grey-image globals, imports the module and calls
`main([])`. **main() is genuinely executing** — the Node process has run
at ~85–99% CPU for 20+ minutes of CPU time (session proc_ff00e93af8e2,
started 18:16 UTC) with no crash and no output yet. (Output is piped
through `head -60`, so nothing is visible until the process exits.)

**It did not complete.** The process ran ~47 minutes of wall time
(~47 min CPU, 85–99% the whole way) and was terminated (SIGTERM) with
zero output — it never printed `REACH HEADS`, never returned from
`main()`, and never crashed. (Output was piped through `head -60`, so
nothing was visible while it ran; the termination flushed nothing.)
Native LOAD→HEADS is 30.8 s, so the JS backend is at least ~90x slower
than native on this page if it was still in the HEADS phase — or it was
stuck in a pathological path. Leading hypothesis (unverified): Phase A
uses stub fonts, so `BufferedImageGraphics` text/drawGlyphVector ops are
no-ops and the note-head templates are built from blank glyph images
(empty keyPoints). Template matching with empty keyPoints may match
everywhere, producing a huge spurious candidate set that makes head
linking / chord building effectively never finish. This is a hypothesis,
not a measured fact — it is testable by running the JS build on a smaller
page, or by adding progress prints per step in TeavmMain.

Nothing about a TeaVM Audiveris step timing is claimed from the JS run:
no Audiveris step completed under TeaVM in this session, in either
backend.

## 7. Verdict and effort to first page

**Verdict: investigate further.** The class-library gap that blocked the
2026-11-03 spike is now closed for the headless path (0 problems, wasm
emitted), and the bench shows TeaVM is not slower than desktop Java on
this host — but the WasmGC backend miscompiles this program, so there is
no WasmGC page read yet. Do not present WasmGC as working.

### Why TeaVM — vendor independence first, speed second (clarified 2026-11-08)

Jonathan's main reason for wanting off CheerpJ is **not speed**. It is
**vendor/CDN dependence**: CheerpJ Community licence requires loading
the runtime from Leaning Technologies' CDN
(`cjrtnc.leaningtech.com`); self-hosting is not included on the free
tier (£5,000/yr, on top of Small Business at £100/developer/month);
there is no licence server, but Leaning controls the CDN the licence
requires — so Leaning could narrow the Community tier, change terms,
or shut the CDN path, and the app would break on their timetable, not
his. He wants **fully self-hosted**: static files he controls, no
vendor server in the loop, no licence server.

That reframes what TeaVM is *for* in this project:

- **TeaVM's value is primarily as a self-hosted exit.** Apache-2.0,
  ahead-of-time compilation to static Wasm/JS the site hosts itself,
  no CDN, no vendor, no licence check, no per-seat or per-developer
  price. Once built, the output keeps working whether or not the
  TeaVM project, Leaning, or anyone else changes terms.
- **The ~1–3× speed is secondary.** A faster page is welcome, but it
  is not the reason to spend 2–3 weeks porting. If TeaVM were only
  as fast as CheerpJ, it would still be worth doing on independence
  grounds; if it were faster but still vendor-hosted, it would not
  solve the actual problem.
- **The same scoring applies to every alternative:** judge first on
  self-hosting / vendor independence, then on speed. That is why
  GraalVM Web Image (open source, self-hosted output) stays a
  watch-list item despite being slower to reach, while CheerpJ 6.0's
  WasmGC — even if it works and is faster — does **not** solve the
  problem at all: it is still Leaning's runtime from Leaning's CDN
  under Leaning's licence. Likewise the server-side option scores
  well on independence *from Leaning* (the server is yours) but
  introduces a different dependence (your own server to run, and the
  "nothing uploaded" privacy cost) — see ALTERNATIVES.md §5.
- **PRs 1, 3 and 5 are interim CheerpJ improvements only.** The
  preflight warn-only (PR 1) and the gated TrOMR merge (PR 3) make
  the CheerpJ app better, faster, or more accurate — and PR 5 is in
  the same category — but none of them removes the CheerpJ
  dependency. They are worth doing on their own merits, but they
  must not be mistaken for progress toward the self-hosting goal.
  The TeaVM port is the only item in the current plan that removes
  the vendor from the loop.
- **What "done" looks like:** the site serves its own static files
  (Audiveris-via-TeaVM Wasm + JS, tesseract.js, PDF.js, the
  `.traineddata` packs) from its own hosting, with no request to
  `leaningtech.com` at runtime. Speed, download size (~10 MB Wasm
  in this spike vs ~60 MB CheerpJ first load) and memory are
  secondary measures of that same build, not the goal itself.
  If the WasmGC codegen bug (§5) cannot be worked around, the TeaVM
  **JavaScript** backend is **not** a proven fallback either: it
  compiled (0 problems) and main() executed, but it never completed
  a page in ~47 min (§6) — >90× slower than native, or stuck in a
  pathological path (unverified hypothesis: stub-font blank
  templates). It would need the same diagnosis as WasmGC before it
  could be called a fallback. Until one backend completes a page,
  staying on CheerpJ is the only working option.

Effort estimates (rough, for a follow-up):

- **Identify function #272 and file/minimize a TeaVM issue (~0.5–1 day):**
  bisect the entry point (stub out step groups / jars) until the validation
  failure disappears; or get a working debug-name mapping. This is the
  critical path — everything else depends on whether TeaVM fixes it or a
  workaround (e.g. restructuring the offending call) exists.
- **If WasmGC is fixed/worked around (~1–2 days after that):** run TeavmMain
  in Node, compare LOAD→HEADS timing and heads count vs native (30.8 s,
  128 notes). Expect the stub classifier to find **0 heads** (Phase A):
  template building uses stub fonts (no MusicFont glyphs), so HEADS will
  run but match nothing. That is an honest partial result — the pipeline
  runs, the classifier is the gap.
- **Phase B — pre-rendered note-head templates (~2–4 days):** dump templates
  on desktop (TemplateFactory catalog for the needed family/pointSize;
  pointSize depends on scale/interline ~21–22 for hymn-satb), serialize
  keyPoints + anchors, patch TemplateFactory to construct Templates from
  data. Then re-run and compare heads/notes vs the native 128-note ground
  truth. (The Template ctor takes plain data, so this is feasible without
  fonts.)
- **After that:** export path (JAXB/MusicXML — needs a real JAXB-in-WASM
  story or a JS-side MusicXML writer), OCR (tesseract.js already exists in
  the CheerpJ app; same approach applies), and the 60 MB download / 1 GB
  memory comparison vs CheerpJ. CheerpJ comparison was not feasible in
  this session (no browser automation available to the worker).

## 8. Files created (all under teavm/)

- `TEAVM_SPIKE.md` (this file), `timings.json`, `problems.txt`
  (635-problem Audiveris Main log), `problems-grouped.txt`,
  `problems-teavm*.txt` / `spike-teavm*.log` (rounds 1–9),
  `problems-teavm5.txt` (26), `problems-teavm6.txt` (11),
  `problems-teavm7` (0 — see `spike-teavm7.log` / `spike-teavm8.log`).
- `resolve.py` (Maven Central resolver), `lib/` (29 TeaVM 0.16.0 jars),
  `lib17/` (empty — 0.17.0-dev-2 is not on Central).
- `Spike.java`, `Bench.java`, `run-wasm.mjs`, `run-bench.mjs`,
  `run-test.mjs`, `run-teavm.mjs`, `run-js.mjs`, `run-js2.mjs`,
  `teavm-main-jars.txt`, `teavm-jars.txt`, `program-jars.txt`.
- `shims/` (`gen_awt.py`, `gen_awt2.py`, `gen_swing2.py`, `gen_swing3.py`,
  `src/`, `shims.jar`), `shims.jar` ~287 KB.
- `patched/` (`make_patches.py` rounds 1–6, `src/`, `classes/` 240 classes),
  `entry/src/teavm/` (TeavmMain, TeavmImageHolder, StubClassifier),
  `test/src/` (TestMain, TestColor).
- `tools/RewriteJars.java`, `tools/classes/`, `lib-patched/`
  (ij, jgrapht, bsaf, jaxb-api, jgoodies-forms TeaVM builds).
- `hymn-satb.gray` + `hymn-satb.json` (test image as raw grey bytes),
  `native-out/hymn-satb/` + `native-out/hymn-satb2/` (native .mxl/.omr/logs).
- `out-teavm/` (`classes.wasm` 10,190,425 bytes, `runtime.mjs`,
  `classes.wasm.teadbg`), `out-js/` (`classes.js`, `classes.js.teavmdbg`),
  `out-bench/`, `out-test/`, `build.sh`.
- `build.sh` rebuilds everything from scratch (patches → shims → ASM
  rewrites → patched/entry compile). `java` is not on PATH; use
  `~/workspace/score-reader-spikes/repo/.work/jdk/jdk-25.0.5+8/bin/java`.

## 9. What was NOT claimed

- No Audiveris step has run under TeaVM WasmGC (the module fails Node
  validation before main runs). No WasmGC timing exists.
- The JS-backend run never completed: ~47 min wall, terminated with zero
  output (see §6). No TeaVM step timing exists from either backend.
- The bench 1.5x figure from 2026-11-03 is not reproduced as an absolute;
  only the same-band conclusion is claimed.
- The stub classifier means Phase A would find 0 heads even if WasmGC
  loaded; heads/notes parity requires Phase B templates.

## 10. Non-English OCR — added 2026-11-08 (Jonathan's question)

**Question:** the repo's BrowserOCR is English-only. Would TeaVM get
non-English OCR back with Audiveris alone — i.e. restore Audiveris's
native TesseractOCR under TeaVM?

**Verdict: NO. TeaVM does not restore native Tesseract, and there is
no mechanism by which it could. The non-English path that works
in-browser is tesseract.js language packs — the same engine, a
different loader — and it needs small, known changes in `app/ocr.js`,
`app/reader.js`, `build.sh` and the `BrowserOCR` patch.**

### Why native Tesseract cannot work under TeaVM

Audiveris's native OCR path is `TesseractOCR`
(`app/src/main/java/org/audiveris/omr/text/tesseract/TesseractOCR.java`
in the Audiveris source tree at `repo/.work/audiveris/`):

- Its class doc says it "relies on **tesseract-ocr** C++ program,
  accessed through a **JavaCPP**-based bridge", importing
  `org.bytedeco.tesseract.TessBaseAPI` and
  `org.bytedeco.tesseract.global.tesseract`.
- The build pulls `org.bytedeco:tesseract:$tessVersion-$jcppVersion`
  plus a per-OS native jar
  (`tesseract-5.5.2-1.5.13-linux-x86_64.jar` is in
  `repo/.work/lib/`). JavaCPP loads a compiled native `libtesseract`
  via JNI at runtime.
- TeaVM compiles Java **bytecode only** and "restricts usage of …
  JNI" (teavm.org docs, checked 2026-11-08: "There are Java APIs that
  are impossible to implement without generating inefficient
  JavaScript. Some of these APIs are: reflection, resources, class
  loaders, and **JNI**. TeaVM restricts usage of these APIs."). Its
  JavaScript interop (`@JSBody`) lets Java call *JavaScript*, not
  native C++ — there is no JNI, no `System.loadLibrary`, no native
  `.so` in a WasmGC module. The one loophole in the TeaVM loader
  (`emscriptenModules`, linking Emscripten-compiled C libraries) is
  for linking a *separately compiled* Emscripten module, not for
  running JavaCPP's JNI bridge — and that Emscripten module would
  itself be Tesseract compiled to Wasm, i.e. exactly what
  tesseract.js already is. So "restore native Tesseract under TeaVM"
  collapses into "use tesseract.js", which the CheerpJ app already
  does. No TeaVM feature changes this; do not claim otherwise.

The same argument applies to CheerpJ: the repo patch already records
it ("Tesseract's native code can't run in CheerpJ", `app/ocr.js`
header; `TesseractOCR.useOCR=false` is set in `app/reader.js`).
TeaVM is not worse than CheerpJ here — it is the same constraint,
for the same reason (no native code in the browser).

### What Audiveris itself offers on language (beyond the engine)

Language is **not** an engine property in Audiveris; it is a
per-book parameter, and the plumbing already exists:

- `org.audiveris.omr.text.Language` defines the spec format
  `LAN[+LAN]*` (e.g. `"DEU+ENG+ITA"`), a default spec constant of
  `"eng"` (`Language.java:170-172`), and `ocrDefaultLanguages`.
- `Book` / `Params` carry an `ocr-languages` parameter
  (`Book.java:302`, `Params.java:91-92`), so a score can in principle
  declare its lyric language(s) and Audiveris passes that spec down
  to whichever OCR implementation is active.
- `TesseractOCR.getSupportedLanguages()` asks the native engine what
  `.traineddata` files it has; `BrowserOCR.getSupportedLanguages()`
  returns the hard-coded set `{"eng"}` and `supports(langSpec)`
  rejects anything outside it (`patches/0001-score-reader.patch`,
  `BrowserOCR.java`).

So the Audiveris side needs **no** new language machinery — it
already asks the OCR implementation for a language spec and checks
support. What is missing is entirely on the browser side, and — this
is the finding — the spec is already threaded through Java and then
**dropped at the JS bridge**:

- `BrowserOCR.recognize(...)` calls its native
  `read(pixels, width, height, langSpec, mode)` — the spec *is*
  passed to the native method.
- `app/reader.js:200-202`, the CheerpJ native implementation
  `Java_org_audiveris_omr_text_BrowserOCR_read(lib, grey, width,
  height, languages, pageSegMode)`, receives `languages` and then
  calls `readText(grey, width, height, pageSegMode)` — **without
  passing `languages`**.
- `app/ocr.js:22` hard-codes `Tesseract.createWorker('eng', LEGACY,
  {… langPath: new URL('tessdata', BASE).href })`, and the worker is
  a singleton (`worker ??= …`), so even the first language is fixed
  for the page's lifetime.
- `build.sh:66` copies only
  `node_modules/@tesseract.js-data/eng/4.0.0/eng.traineddata.gz`
  into `dist/tessdata/`; `package.json` lists only
  `"@tesseract.js-data/eng": "1.0.0"`. There is no `tessdata/`
  directory in `app/` — it is a build output.

### The path that DOES work in-browser: tesseract.js language packs

tesseract.js is the same Tesseract engine compiled to Wasm; a
language is just a `.traineddata` file fetched on demand:

- One file per language, from the `tesseract-ocr/tessdata` repo or
  the `@tesseract.js-data/<lang>` npm packages
  (`https://cdn.jsdelivr.net/npm/@tesseract.js-data/<LANG>@1.0.0/4.0.0/<LANG>.traineddata.gz`).
  Standard 4.0.0 models, gzipped: eng 10.42 MB, spa 7.98 MB,
  deu 6.77 MB, fra 5.99 MB, ara 1.60 MB (dev.to/EasyInvoiceOCR
  measurements, Nov 2026 — same files tesseract.js fetches by
  default). `tessdata_fast` is 1–3 MB/language (lower accuracy),
  `tessdata_best` 30–50 MB/language. Latin (`lat`) is in the same
  tessdata repo; size is in the same few-MB range (not measured here
  — check before budgeting).
- `createWorker` takes the language as its first argument and
  accepts combined specs with `+` (`'eng+deu'`, `'deu+eng+fra'`),
  matching Audiveris's `LAN[+LAN]*` format (case differs: Tesseract
  wants lowercase `deu`, Audiveris's dictionary uses `DEU` — normalise
  case at the bridge). The legacy engine (`LEGACY = 0`) is the one
  Audiveris uses, and the same engine must be used for the other
  languages — the legacy `traineddata` files are the ones in the
  standard tessdata repo.
- A model is fetched once, on demand, when recognition runs in that
  language, then browser-cached; it is not page weight. Vendoring
  (copying the files into `dist/tessdata/` at build time, as
  `build.sh` already does for `eng`) avoids the CDN-drift/404
  failure mode other projects have hit (a picker listing a language
  whose file was never vendored).

### What would have to change (small, known work)

1. **`app/ocr.js`:** `startOcr(lang)` — take a language spec,
   normalise to lowercase `+`-joined codes, create (or recreate)
   the worker for that spec. The worker is currently a singleton,
   so a language change needs a worker re-create (or one worker
   per language, cached). `readText` takes the spec and passes it
   through.
2. **`app/reader.js`:** pass the `languages` argument the native
   already receives into `readText`. One line; this is the actual
   bug-shaped gap today.
3. **`BrowserOCR` patch:** extend `LANGUAGES` from `{"eng"}` to the
   set the page ships (e.g. `{"eng","deu","fra","ita","spa","lat"}`),
   so `supports(langSpec)` stops rejecting non-English specs and
   Audiveris does not fall back to / warn about an unsupported
   language.
4. **`build.sh` / `package.json`:** add the
   `@tesseract.js-data/<lang>` packages and copy each
   `<lang>.traineddata.gz` into `dist/tessdata/`. ~6–10 MB per
   language gzipped (standard models).
5. **UI / selection:** the score's language has to come from
   somewhere — a per-score picker in the page (simplest), or
   `Book`'s `ocr-languages` param if the app ever sets it. Note
   the default in Audiveris is `"eng"`, so English stays the
   default; nothing changes for existing scores.
6. **What this does NOT fix:** syllable/hyphenation handling is
   language-dependent in the *text* (German/French hyphenation and
   elision, Latin word spacing), but Audiveris's lyric
   syllable-to-note alignment works on the words Tesseract returns
   — there is no separate Audiveris hyphenation table to port.
   Accuracy per language is whatever Tesseract's model gives for
   that language on printed lyrics; measure it on a real German /
   French / Latin page before claiming parity with English
   (the English BrowserOCR gap, 64–77% of syllables, is the
   baseline to beat). Also, `lat` has no native speakers and its
   model is trained on Latin text — fine for chant/hymn lyrics,
   but expect the same "clean print reads well, dense lyrics
   struggle" pattern as English.

### Verdict

- **TeaVM restoring native Tesseract: NO-GO — no mechanism.**
  TeaVM cannot run JNI/native libtesseract; the only in-browser
  Tesseract is the Wasm one (tesseract.js), which is engine-identical
  and already in the app.
- **Non-English OCR in the browser: GO via tesseract.js packs.**
  Small, bounded work (bridge the dropped `languages` argument,
  parameterise the worker, vendor the `.traineddata` files, widen
  `BrowserOCR.LANGUAGES`). Effort: well under a day for the plumbing;
  the real work is measuring accuracy per language on real pages.
  **Open question for Jonathan (do not build before asking): which
  languages?** German, French, Italian, Spanish and Latin cover the
  choral repertoire; the set determines the vendored size
  (~6–10 MB each) and the test pages needed.
- This section does not change the TeaVM verdict in §7: TeaVM is
  neither better nor worse than CheerpJ on OCR — both use
  tesseract.js, and both need the same bridge fix above.
