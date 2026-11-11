// Run a TeaVM repro wasm in Node. Usage (from teavm-fork/):
//   node phase38/retain-repro/run-repro.mjs out
//   node phase38/retain-repro/run-repro.mjs out-fixed
import { load } from "../../../teavm/out-teavm/runtime.mjs";
const dir = process.argv[2];
const teavm = await load(new URL(`./${dir}/classes.wasm`, import.meta.url).pathname);
console.log("wasm loaded");
teavm.exports.main([]);
console.log("main() returned");
