// Run a compiled TeavmMain wasm dir on hymn-satb.grey bytes and save the
// exported MusicXML (globalThis.__mxlB64, set by TeavmMain after PAGE).
// Usage: node run-fullout-mxl.mjs <wasm-dir> <out.musicxml>
//   e.g. node run-fullout-mxl.mjs full-out48 wasm-hymn.musicxml
// Runtime is the generic one from ../teavm/out-teavm/runtime.mjs.
// Do NOT modify run-fullout.mjs; this is the Phase D (§22) variant.
import { readFile } from "node:fs/promises";
import { saveMxl, readMxlList } from "./save-mxl.mjs"; // §37: one file per score

const dir = process.argv[2] ?? "full-out48";
const out = process.argv[3] ?? "wasm-hymn.musicxml";
const grey = await readFile(new URL("../teavm/hymn-satb.gray", import.meta.url));
const meta = JSON.parse(await readFile(new URL("../teavm/hymn-satb.json", import.meta.url), "utf8"));
globalThis.__greyB64 = grey.toString("base64");
globalThis.__greyW = meta.width;
globalThis.__greyH = meta.height;
console.log(`grey bytes=${grey.length} w=${meta.width} h=${meta.height}`);

const t0 = Date.now();
const { load } = await import("../teavm/out-teavm/runtime.mjs");
const teavm = await load(new URL(`./${dir}/classes.wasm`, import.meta.url).pathname);
console.log(`wasm loaded in ${Date.now() - t0} ms`);
const t1 = Date.now();
teavm.exports.main([]);
console.log(`main() returned in ${Date.now() - t1} ms`);

const list = readMxlList();
if (!list) {
    console.log("NO MXL: globalThis.__mxlB64 was never set");
    process.exit(2);
}
const files = await saveMxl(out, list);
for (const f of files) {
    console.log(`MXL saved ${f.path} bytes=${f.bytes}`);
}
