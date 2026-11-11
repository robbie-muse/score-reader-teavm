# score-reader-teavm

Audiveris 5.11 running on TeaVM 0.16.0 (WasmGC) — a self-hosted,
CheerpJ-free way to run OMR in the browser.

This is the work-in-progress stack behind
[jdlehman/score-reader](https://github.com/jdlehman/score-reader), a web
app that scans sheet music (especially choral scores) and returns
MusicXML. The shipped app runs Audiveris on CheerpJ 5.0, which loads its
runtime from Leaning Technologies' CDN under a community licence — the
vendor can narrow the terms or shut the CDN and break the app. This
stack replaces CheerpJ with TeaVM (Apache-2.0), so the site serves only
its own static files and makes no request to `leaningtech.com` at
runtime.

**Status: WIP, not integrated into score-reader yet.** It runs end to
end in Node (LOAD → PAGE → `.mxl`, lyrics included via a tesseract.js
bridge) and matches desktop Audiveris on the control pages. It has
**not** run in a real browser yet (no Chrome run — that is the next
milestone), and it is not wired into the app's `reader.js`. See
[Gaps](#gaps) and `teavm-fork/FORK_FIX.md` §47 for what is still open.
CheerpJ 5 remains the only working option for the shipped app.

## What is verified (full-out113, the reference build)

The reference build is **full-out113**: 0 problems, 3,055 classes,
28,730 methods, `classes.wasm` 14,252,588 bytes, DIAG prints stripped.
(The built `.wasm` is *not* in this repo — build outputs are excluded;
rebuild from source below.)

60-page validation against desktop Audiveris 5.11, no-OCR
(`open-music/FULLOUT92_RESULTS.md` — the full-out92 run; full-out110
changes nothing on the validated pages except the Mozart fix (§44.1)
and the flat-key fix (§45: Widmung 291 pitched = desktop, Wiegenlied
155 = desktop; controls byte-identical to full-out94); full-out113
changes nothing on any verified page — the §46 fix round and §47
DIAG55 are output-identical to full-out110 (hymnal `.mxl`
byte-identical, controls byte-identical)):

- **Multi-page (37 pp: BWV 227, Hallelujah, Gute Nacht): 37/37
  complete, zero crashes, 31/37 identical note counts.** Whole-piece
  pitched notes 11,259 / 11,257 desktop (100.0%); exact-pitch hits
  6,081 / 6,082 (99.98%).
- **One-page set (23 pp): 21/23 produce a `.mxl`.** 9 of 16
  ground-truth pages identical in note counts; chorales at ceiling
  (BWV 371/397/248-33/269: 211/211, 209/209, 210/210, 118/118 exact).
- **Control pages: zero real diffs vs desktop** — hymn (44,105 B),
  Hallelujah p01 (105,434 B), Lindenbaum (99,305 B) all deep-diff **2,
  both filename artefacts** (`<source>/hymn-satb.png</source>` is a
  hardcoded string in the Wasm writer, §22).
- **German lyrics end to end** (deu OCR, §34): 14 lyrics, syllable
  sequence identical to desktop.
- **Timing (Node v24.22.1):** multi-page mean `main_ms` 92–94 s/page;
  one-page mean 113 s/page; peak child RSS ~920 MB–1.09 GB. Wasm is
  ~1.5–2× desktop wall on renders, ~2–8× on big scans (Beethoven p229:
  863 s vs desktop 111 s).

## Layout

| Path | What it is |
|---|---|
| `teavm/build.sh` | Builds shims.jar, patched Audiveris classes, entry classes, policy classes |
| `teavm/Spike.java` | TeaVM headless build entry (`Spike wasm-gc …`) |
| `teavm/patched/make_patches.py` | Copies Audiveris 5.11 sources and applies the TeaVM patches (ROUNDs 1–49, incl. `strip_diag.py`) |
| `teavm/shims/src/` | JDK shims for classes TeaVM's classlib lacks or gets wrong (AWT, `Area`, `Rectangle`, `Font`, …) + `gen_*.py` generators |
| `teavm/entry/src/teavm/` | `TeavmMain` (headless entry), `MxlWriter` (hand-rolled MusicXML writer, no JAXB), pre-rendered templates/classifier/metrics + `StubClassifier` |
| `teavm/policy/src/` | TeaVM `ReflectionPolicy` (ConstantSet, support-class constructors, …) |
| `teavm/tools/RewriteJars.java` | ASM rewrites applied to shims.jar / dependency jars |
| `teavm/lib/` | TeaVM 0.16.0 jars used at build time |
| `teavm/out-teavm/runtime.mjs` | TeaVM WasmGC runtime loader used by the Node runners |
| `teavm/hymn-satb.gray` + `.json` | Control-page test input (hymn, 2480×3507) |
| `teavm-fork/FORK_FIX.md` | The master log: §§1–47, every defect, diagnostic, fix and verdict |
| `teavm-fork/*.patch` | Fork patches: classlib (TPoint/TDimension, TColor, hypot, retainAll, TFormatter, rint) + core (#21244, #21244b) + Audiveris-side (FloodFiller, ClefBuilder, TemplateFactory) |
| `teavm-fork/lib-fork/` | The two current patched jars: `teavm-classlib-0.16.0.jar` (all five classlib patches) and `teavm-core-0.16.0-patched2.jar` (#21244/#15448). Pre-patch rollback jars are *not* included. |
| `teavm-fork/run-fullout-*.mjs`, `save-mxl.mjs` | Node runners (no-OCR control, OCR, multi-score save) |
| `teavm-fork/phase23/` | Desktop harness sources (`src/`), OCR bridge runner (`ocr-run.mjs`, tesseract.js 6.0.1), control inputs (Hallelujah, Lindenbaum `.gray`/`.json`) |
| `teavm-fork/phaseb/`, `phasec/`, `phase-font/` | Generators for the pre-rendered templates / classifier weights / symbol metrics |
| `teavm-fork/phase27/`, `phase38/`, `phase41/`, `phase47/` | Minimal TeaVM-level repros + issue drafts for the genuine TeaVM defects |
| `teavm/fn272/repro/` | Minimal repro for the #272 `Point`/`Point2D` defect |
| `TEAVM_WASM.md` | Synthesis: alternatives survey, verdicts, what was ruled out |
| `UPSTREAM.md` | Deferred upstream TeaVM filings (drafts + patches, none filed yet) |

## Prerequisites

- **JDK 25** (this work used `jdk-25.0.5+8`; `java` is not on `PATH`
  in a fresh shell here — the build script hardcodes the JDK path, see
  [Portability](#portability)).
- **Node v24** (v24.22.1 used; the WasmGC module loads in Node — no
  browser run yet).
- **Python 3** (patch generation).
- **Audiveris 5.11 source + its built jars.** `make_patches.py` reads
  from an Audiveris checkout (`REPO = …/repo/.work/audiveris/app/src/main/java`)
  and `program-jars.txt` lists the Audiveris build outputs
  (`audiveris.jar` + ~60 dependency jars). Build Audiveris 5.11 first
  (its Gradle build) and point those two at your checkout.
- **TeaVM 0.16.0 jars** are included in `teavm/lib/`; the patched
  classlib/core jars are in `teavm-fork/lib-fork/`.

## Build

```sh
cd teavm && ./build.sh            # regenerates patched/src, shims.jar, patched/classes
java -Xmx3g -cp "../teavm-fork/lib-fork/teavm-core-0.16.0-patched2.jar:lib/*:classes" \
  Spike wasm-gc ../teavm-fork/full-outN teavm.TeavmMain \
  @../teavm-fork/teavm-main-jars-corepatch2.txt @program-jars.txt
```

Notes:

- The patched core jar must be **first** on the compiler classpath
  (§10.8) — otherwise the stock core wins and #21244 comes back.
- `teavm-main-jars-corepatch2.txt` and `program-jars.txt` contain
  **absolute paths** to the original VM (see [Portability](#portability));
  regenerate them for your checkout. They list, in order: `shims.jar`,
  `policy-classes`, the patched classlib + core jars, the TeaVM jars,
  `patched/classes`, the ASM-rewritten dependency jars, and the
  Audiveris program jars.
- `build.sh` also regenerates the AWT/Swing stub sources
  (`shims/gen_*.py`) and runs the ASM rewrites (`tools/RewriteJars.java`).
- Determinism: rebuilding the same inputs gives a byte-identical
  `.wasm` (§10.8).

## Run

```sh
cd teavm-fork
# No-OCR control run (grey input + {width,height} meta JSON):
node run-fullout-noocr.mjs full-out113 ../teavm/hymn-satb.gray ../teavm/hymn-satb.json out.musicxml
# Expected: exit 0, 44,105 B, 128 notes, 2 parts, 16 measures.
```

- The runners import `../teavm/out-teavm/runtime.mjs` and load
  `<wasm-dir>/classes.wasm`.
- **OCR (lyrics):** two-phase bridge (§23) — phase 1 exports the clean
  image, `phase23/ocr-run.mjs` runs tesseract.js (legacy engine,
  70 dpi), phase 2 re-runs with the text prefed. `cd
  teavm-fork/phase23 && npm install` (tesseract.js 6.0.1); language
  packs are downloaded by tesseract.js on first use (~6–15 MB each;
  eng/deu/fra/ita/spa/lat wired). Then
  `node run-fullout-ocr.mjs …` (see `run-fullout-ocr.mjs` header).
- **Multi-score books:** `save-mxl.mjs` emits one file per score
  (`__mxlB64List`, §37); `__mxlB64` carries the first score for old
  runners.

## Reproducing the 60-page validation

The test music is **not** in this repo. It is the open-source choral
test set collected in `open-music/` on the spike machine
(`choral-test-set.zip`, 313.2 MB, 978 files — ask the maintainer for
the current zip): one-page slices (Bach chorales, BWV 227 divisi,
CC0 Lieder, Fauré organ, orchestral, writer-path pages, hymnal) + 4
full multi-page pieces (BWV 227, Hallelujah, Gute Nacht, Cantique de
Jean Racine). Every page's licence is recorded
exactly as stated at its source in `MANIFEST.md` (CC0, Public Domain,
CC BY-NC-SA 4.0, CC BY-NC, CPDL); the four Bach chorales carry a
"© 2004 Margaret Greentree, used by permission" encoding credit and
are testing-only, not for commercial redistribution.

The validation report is `open-music/FULLOUT92_RESULTS.md` (full-out92
run; the full-out94 fix round is `teavm-fork/FORK_FIX.md` §44, the
full-out110 flat-key fix is §45). The 22-page additions run
(writer paths, Cantique, hymnal, orchestral) is
`open-music/FULLOUT110_ADDITIONS_RESULTS.md`; its fix round is §46
and the DIAG55 hymnal split is §47. Runners
and scorers live in `open-music/` (`run92_part1.py`,
`multi-page/run_wasm92.py`, `score92.py`) — they are not in this repo
yet; the `.gray` + `.json` control inputs that *are* here
(hymn, Hallelujah p01, Lindenbaum) reproduce the three control pages,
which are the pages every § in `FORK_FIX.md` is verified against.

## Gaps

- **No real-browser Chrome run yet** — everything is Node v24.22.1.
  The browser has a fixed ~1 MB V8 stack and tighter memory limits;
  the Node `--stack-size` knob does not exist there.
- **Flat-key signatures — FIXED (§45).** The §19 `BravuraMetrics`
  table lacked codepoint 0xe010 (`LINE_CODE`), so the shim `TextLayout`
  fell back to stub bounds and `populatePitchOffsets` computed
  `lineHeight` 200.0 instead of 6.40625, collapsing `pitchHeight`
  25.0 → 0.80 and inflating every pitch offset ~31×; flat alters
  graded 0 and no key was ever created. Fix is shim data only
  (0xe010 metrics dumped on desktop Java,
  `teavm-fork/phase55/LineCodeDump.java`; table 157 → 158
  codepoints; no Audiveris code changed). All 8 flat-key pages
  recovered: Widmung 291 pitched = desktop (was 169), Wiegenlied
  155 = desktop with pickup m0 restored and the phantom G♭4 gone,
  BWV 397 and Après un rêve exact, Erlkönig/Wolf/Sanctus/Agnus keys
  exact with pre-existing pitched gaps unchanged. Controls
  byte-identical to full-out94.
- **BWV 227 p14 +2** (§44.4): a §40 × §42 interaction (rint fix flips
  HEADS template matching, §42 supports let the flipped heads
  survive); no contained fix — reverting either re-breaks §40 or §42.
  Next diagnostic DIAG53 named.
- **Mozart Kyrie** (§44.1, fixed): export trap was a null-fifths
  KEY_CANCEL key; guard in `MxlWriter.processKey`. Residuals: 5
  missing measure-1 header keys (DIAG50 named), P4 +1 note, and the
  `joinByLeftLines` system merge (a score-reader-patch question, not
  TeaVM — Wasm faithfully reproduces the patched desktop behaviour).
- **Beethoven typeset p165 fails on both sides** (interline-7 SCALE
  rejection) — an Audiveris/page issue, not TeaVM.
- **StackRhythm validity wobbles ±1–3 measures** (§25.5, unchanged).
- **Beethoven p229 Wasm wall 863 s** (desktop 111 s) — worst observed
  latency; profiled in §46.5: the STEMS..SYMBOLS span is 83% of the
  Wasm wall (656.8 s, ×52 vs desktop) while HEADS is ×1.9 — the TeaVM
  penalty concentrates in graph work, not pixel work, and scales
  roughly quadratically in single-system graph size (1 system,
  36 staves, SIG 4,261; 18.2 s/staff vs Cantique 2.0). Needs a
  per-substep timing split before any optimisation, and before calling
  the build shippable for orchestral scans.
- **Writer paths — VERIFIED at unit level (§46.3).** `WriterPathTest`
  drives a mutated hymn Score through `MxlWriter.build`: `<grace>` ×1,
  `<harmony>` ×1 (C major), `<unpitched>` ×40, `<measure-repeat>`
  start + stop, `<octave-shift>` down + stop (same number 1, size 8) —
  all 7 checks pass. The gap was pipeline-side (Audiveris never
  produces those model elements on the test pages), not a writer bug.
- **Hymnal −28 — SPLIT in §47 (DIAG55).** 28 = 22 template-driven
  (stock-template harness SYMBOLS-END 119 vs pre-rendered 97, same
  gray, same harness; request 57 → catalog 64, 12% oversize) + 6
  gray-conversion (batch 125 pitched vs stock-harness 119 heads;
  batch gray is max-RGB, harness/Wasm gray is PIL-L luma — all
  2,338,180 px differ, max 22 levels) + **0 Wasm-side** (Wasm ==
  pre-rendered desktop harness at every measured step, identical
  checksums and COLUMN dumps to full float precision). The harness
  TEXTS crash is solved too: it was the ROUND 20 DIAG24 dead block
  (hard-coded ROI x 1854..1929 on a 1196-wide page) at
  `SheetScanner.getCleanImage:143`, removed — stock code restored
  exactly.
- **Lindenbaum batch-vs-harness 2-measure gap** — reframed by §47:
  most plausibly the same gray-conversion cause as the hymnal split
  (batch gray is max-RGB, harness/Wasm gray is PIL-L luma). A cheap
  check, still open, not yet run.
- **Snow p5 template-catalog point size 140** still out of window at
  full 300 dpi (catalog {64,72,80,92,108} ±8); at the app's 4096 cap
  it scales to ~112, inside the window.

## Tradeoffs

- **Pre-rendered templates / classifier / metrics instead of real
  Bravura rendering.** TeaVM has no font engine, so whole `Template`
  objects, neural-classifier weights and symbol metrics are
  pre-rendered offline on desktop Java and substituted in
  (`teavm/entry/src/teavm/PreRendered*.java`, generators in
  `phaseb/`/`phasec/`/`phase-font/`). Catalog {64,72,80,92,108} ±8.
  A page calibrated outside the window gets HEADS 0.
- **Stub fonts.** `TextLayout.draw` is a no-op in the shims; anything
  Audiveris paints with the music font (head erasure, §30/§32) is
  re-implemented in the patched code.
- **Fork classlib patches for genuine TeaVM defects** — #272
  (TPoint/TDimension), #21244/#15448 (WasmTypeInference), retainAll,
  TFormatter, rint/round, plus TColor. All drafted, **none filed**
  (upstream after our own work is done — see `UPSTREAM.md`).
- **Hand-rolled MusicXML writer.** JAXB does not build under TeaVM
  (§21), so `MxlWriter.java` emits MusicXML 4.0.3 text with a plain
  `StringBuilder`, mirroring `PartwiseBuilder`. Desktop-verified:
  406 notes, 0 deep semantic diffs on four pages (§22).
- **Single-threaded inline executor.** `OmrExecutors` pools are
  no-arg factories; `cpuCount` is 1. No true parallelism under Wasm.
- **DIAG prints stripped in the clean build** (ROUND 49,
  `strip_diag.py`); full-out113 is the reference. Diagnostic builds
  (full-out111/112, DIAG54+55 kept) are not in this repo.

## Licensing

- **AGPL-3.0** (`LICENSE`): the Audiveris-derived parts — the patches
  in `teavm/patched/make_patches.py` (against Audiveris 5.11, AGPL-3.0),
  the spike entry/writer/runner code, and this repo as a whole, matching
  [jdlehman/score-reader](https://github.com/jdlehman/score-reader).
  Keep the transcoding component open source; publish its source as
  AGPL requires.
- **Apache-2.0**: the TeaVM fork patches (`teavm-fork/*.patch` against
  TeaVM 0.16.0) — TeaVM itself is Apache-2.0; distributing a fork
  requires including the licence, marking changed files, and retaining
  attribution/NOTICE. The fork does not change Audiveris's AGPL-3.0
  status.
- Pre-rendered template/classifier data is derived from the Bravura
  music font (SIL Open Font License).
- tesseract.js language data (`tessdata_fast`) is Apache-2.0 — verify
  before shipping anything trained on it.

## Portability

This stack was built on one VM, and the paths show it. Before
rebuilding elsewhere:

- `teavm/build.sh` hardcodes the JDK path
  (`…/repo/.work/jdk/jdk-25.0.5+8/bin`) and the Audiveris lib paths
  (`…/repo/.work/lib/*.jar`); edit them for your checkout.
- `teavm-main-jars-corepatch2.txt` and `program-jars.txt` contain
  absolute paths; regenerate them with your paths.
- **Gradle does not work in this VM** (proxy CONNECT tunnel failure),
  so the classlib/core patches were applied with `javac` + `jar uf`
  and verified by `javap` + repro runs — the Gradle suite must run on
  a machine where Gradle works before submitting anything upstream.
- `java` is not on `PATH` in a fresh shell here; the JDK lives at
  `repo/.work/jdk/jdk-25.0.5+8/bin`.
