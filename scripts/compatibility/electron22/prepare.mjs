// Prepare a disposable probe from the POS's actual storage source, never a mock DB.
import { createRequire } from 'node:module';
import { readFileSync, writeFileSync, copyFileSync, mkdirSync, existsSync } from 'node:fs';
import { resolve, join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createHash } from 'node:crypto';
const [posArg, outputArg] = process.argv.slice(2);
if (!posArg || !outputArg) throw new Error('Usage: node prepare.mjs POS_ROOT EMPTY_OUTPUT_DIR');
const pos = resolve(posArg), out = resolve(outputArg);
if (existsSync(out)) throw new Error('Output must not exist; use a new disposable directory');
mkdirSync(out, { recursive: true });
const require = createRequire(join(pos, 'package.json'));
const ts = require('typescript');
const sourceHashes = {};
for (const name of ['sqlite', 'schema', 'idempotency', 'orderOutboxRepo']) {
  const sourcePath = `src/pos/db/${name}.ts`;
  const source = readFileSync(join(pos, sourcePath), 'utf8');
  sourceHashes[sourcePath] = createHash('sha256').update(source).digest('hex');
  let js = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 } }).outputText;
  js = js.replaceAll("'@sqlite.org/sqlite-wasm'", "'./sqlite-library.mjs'")
    .replaceAll("'./schema'", "'./schema.mjs'").replaceAll("'./idempotency'", "'./idempotency.mjs'");
  writeFileSync(join(out, name + '.mjs'), js);
}
for (const [input, output] of [['index.mjs', 'sqlite-library.mjs'], ['sqlite3.wasm', 'sqlite3.wasm']]) {
  const src = join(pos, 'node_modules/@sqlite.org/sqlite-wasm/dist', input);
  sourceHashes['sqlite-wasm/' + input] = createHash('sha256').update(readFileSync(src)).digest('hex');
  copyFileSync(src, join(out, output));
}
const here = dirname(fileURLToPath(import.meta.url));
for (const name of ['main.cjs', 'worker.mjs', 'index.html']) copyFileSync(join(here, name), join(out, name));
writeFileSync(join(out, 'preload.cjs'), "const {contextBridge,ipcRenderer}=require('electron');contextBridge.exposeInMainWorld('probe',{finish: data=>ipcRenderer.send('probe-result',data)});");
writeFileSync(join(out, 'package.json'), JSON.stringify({ name: 'pos-legacy-storage-probe', version: '0.0.0', main: 'main.cjs' }));
writeFileSync(join(out, 'source-sha256.json'), JSON.stringify(sourceHashes, null, 2) + '\n');
console.log(out);
