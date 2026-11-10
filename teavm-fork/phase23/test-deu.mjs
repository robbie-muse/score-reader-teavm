import { readFile } from 'node:fs/promises';
import { readText, stopOcr } from './ocr-run.mjs';
const grey = await readFile('./lieder-lindenbaum.gray');
const t0 = Date.now();
const text = await readText(grey, 2480, 3507, 3, 'deu');
console.log(`deu ocr ms=${Date.now()-t0} rows=${text.split('\n').length}`);
console.log(text.split('\n').filter(r=>r.startsWith('L ')).slice(0,8).join('\n'));
console.log(text.split('\n').filter(r=>r.startsWith('W ')).slice(0,10).join('\n'));
await stopOcr();
