// Run a compiled TeavmMain wasm dir on hymn-satb.grey bytes.
// Usage: node run-fullout.mjs <wasm-dir>   (e.g. node run-fullout.mjs full-out6)
// Runtime is the generic one from ../teavm/out-teavm/runtime.mjs.
import { readFile } from "node:fs/promises";

const dir = process.argv[2] ?? "full-out6";
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
