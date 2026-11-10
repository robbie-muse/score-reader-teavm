import { readFile } from 'node:fs/promises';
import { readText, stopOcr } from './ocr-run.mjs';
const grey = await readFile('/home/hatch/workspace/score-reader-spikes/teavm-wasm/teavm/hymn-satb.gray');
const t0 = Date.now();
const text = await readText(grey, 2480, 3507, 3, 'eng');
console.log(`ocr ms=${Date.now()-t0} rows=${text.split('\n').length}`);
console.log('L rows:', text.split('\n').filter(r=>r.startsWith('L ')).length, 'W rows:', text.split('\n').filter(r=>r.startsWith('W ')).length, 'C rows:', text.split('\n').filter(r=>r.startsWith('C ')).length);
console.log(text.split('\n').slice(0,12).join('\n'));
await stopOcr();
