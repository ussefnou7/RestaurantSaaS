// Usage: electron ui-probe.cjs BUILT_RENDERER NEW_EVIDENCE_DIRECTORY
// A local-only renderer smoke test, not a replacement production shell.
const { app, BrowserWindow, protocol, session } = require('electron');
const fs = require('fs');
const path = require('path');
const [rootArg, outputArg] = process.argv.slice(2);
if (!rootArg || !outputArg) throw new Error('Pass renderer build and new evidence directory');
const root = path.resolve(rootArg), out = path.resolve(outputArg);
if (fs.existsSync(out)) throw new Error('Evidence directory must be new');
fs.mkdirSync(out, { recursive: true });
app.setPath('userData', path.join(out, 'isolated-profile'));
app.disableHardwareAcceleration();
protocol.registerSchemesAsPrivileged([{ scheme:'probe', privileges:{standard:true,secure:true,supportFetchAPI:true} }]);
let finished = false;
const result = { versions:process.versions, platform:process.platform, arch:process.arch, errors:[], scope:'Renderer login and synthetic Arabic receipt; no backend or real printer' };
function finish(error) {
 if(finished)return; finished=true;
 if(error)result.errors.push(String(error));
 result.ok=result.errors.length===0;
 fs.writeFileSync(path.join(out,'ui-result.json'),JSON.stringify(result,null,2));
 console.log(JSON.stringify(result));app.exit(result.ok?0:1);
}
setTimeout(()=>finish('Timeout'),45000);
app.whenReady().then(async()=>{
 session.defaultSession.webRequest.onBeforeRequest({urls:['http://*/*','https://*/*']},(_details,callback)=>callback({cancel:true}));
 protocol.registerFileProtocol('probe',(request,callback)=>{
  const url=new URL(request.url),file=path.resolve(root,'.'+decodeURIComponent(url.pathname));
  callback(url.hostname==='local'&&file.startsWith(root+path.sep)?{path:file}:{error:-10});
 });
 const win=new BrowserWindow({show:false,width:1440,height:900,webPreferences:{offscreen:true,contextIsolation:true,nodeIntegration:false,sandbox:true}});
 win.webContents.on('console-message',(_event,level,message)=>{if(level===3)result.errors.push(message);});
 await win.loadURL('probe://local/index.html');
 await win.webContents.executeJavaScript('new Promise(r=>setTimeout(r,1000))');
 result.login=await win.webContents.executeJavaScript('({text:document.body.innerText,inputs:document.querySelectorAll("input").length,secureContext:isSecureContext})');
 if(!result.login.text.trim() || !result.login.inputs)throw new Error('Login page did not render usable inputs');
 fs.writeFileSync(path.join(out,'login.png'),(await win.webContents.capturePage()).toPNG());
 await win.loadURL('probe://local/receipt.html');
 win.setContentSize(576,1600);
 const payload={kind:'customer',lang:'ar',ticket:'TEST-001',mode:'تيك أواي',clock:'2026-10-02 15:00',cashierName:'كاشير تجريبي',lines:[{qty:2,name:'وجبة دجاج',total:'100'}],subtotal:'100',vat:'14',total:'114',branding:{brand:'مطعم تجريبي',branchName:'الفرع الرئيسي',footer:'اختبار توافق — ليس إيصال بيع'},labels:{subtotal:'الإجمالي الفرعي',vat:'الضريبة',total:'الإجمالي',customerCopy:'نسخة العميل',kitchenCopy:'نسخة المطبخ'}};
 result.receiptHeight=await win.webContents.executeJavaScript('window.__renderReceipt('+JSON.stringify(payload)+')');
 if(!Number.isFinite(result.receiptHeight)||result.receiptHeight<=0)throw new Error('Invalid receipt height');
 win.setContentSize(576,Math.ceil(result.receiptHeight));
 await win.webContents.executeJavaScript('document.fonts.ready.then(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))))');
 result.receiptText=await win.webContents.executeJavaScript('document.body.innerText');
 if(!result.receiptText.includes('وجبة دجاج')||!result.receiptText.includes('114'))throw new Error('Receipt content missing');
 fs.writeFileSync(path.join(out,'receipt.png'),(await win.webContents.capturePage()).toPNG());
 finish();
}).catch(finish);
