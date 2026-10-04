// Produce an experimental shell in a new directory, preserving all POS originals.
import { cpSync, copyFileSync, mkdirSync, readFileSync, writeFileSync, existsSync, symlinkSync } from 'node:fs';
import { resolve, join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';
const [posArg, outArg] = process.argv.slice(2);
if (!posArg || !outArg) throw new Error('Usage: node stage-shell.mjs POS_ROOT NEW_OUTPUT_DIR');
const pos=resolve(posArg), out=resolve(outArg), here=dirname(fileURLToPath(import.meta.url));
if(existsSync(out))throw new Error('Output must not exist');
mkdirSync(out,{recursive:true});
for(const name of ['electron','shared','src','scripts','public']) {
 if(existsSync(join(pos,name)))cpSync(join(pos,name),join(out,name),{recursive:true});
}
for(const name of ['package.json','package-lock.json','index.html','receipt.html','vite.config.ts','tsconfig.json','tsconfig.app.json','tsconfig.node.json','tsconfig.electron.json']) {
 if(existsSync(join(pos,name)))copyFileSync(join(pos,name),join(out,name));
}
symlinkSync(join(pos,'node_modules'),join(out,'node_modules'),'dir');
let main=readFileSync(join(out,'electron/main.ts'),'utf8').replaceAll('import.meta.dirname','__dirname').replace("'preload.mjs'","'preload.js'");
writeFileSync(join(out,'electron/main.ts'),main);
copyFileSync(join(here,'legacy-protocol.ts'),join(out,'electron/protocol.ts'));
const ts=createRequire(join(pos,'package.json'))('typescript');
const parsed=ts.parseConfigFileTextToJson('tsconfig.electron.json',readFileSync(join(out,'tsconfig.electron.json'),'utf8'));
if(parsed.error)throw new Error('Cannot parse TypeScript configuration');
const config=parsed.config;
Object.assign(config.compilerOptions,{module:'commonjs',moduleResolution:'node',ignoreDeprecations:'6.0',target:'es2022',verbatimModuleSyntax:false,tsBuildInfoFile:'./.build/legacy.tsbuildinfo'});
writeFileSync(join(out,'tsconfig.electron.json'),JSON.stringify(config,null,2));
let vite=readFileSync(join(out,'vite.config.ts'),'utf8').replace('plugins: [react()],',`plugins: [react()], cacheDir: ${JSON.stringify(join(out,'.vite-cache'))},`).replace('build: {',"build: { target: 'chrome108',");
writeFileSync(join(out,'vite.config.ts'),vite);
mkdirSync(join(out,'dist-electron'),{recursive:true});
writeFileSync(join(out,'dist-electron/package.json'),JSON.stringify({type:'commonjs'}));
// Run tsc then copy settings.html into dist-electron/electron before launching.
console.log(out);
