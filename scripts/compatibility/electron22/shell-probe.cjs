// Starts the adapted real shell against a disposable loopback test server.
const {app,BrowserWindow}=require('electron');
const fs=require('fs'),path=require('path'),http=require('http');
const [rootArg,outArg]=process.argv.slice(2);
if(!rootArg||!outArg)throw new Error('Usage: electron shell-probe.cjs STAGED_POS NEW_RESULTS_DIR');
const root=path.resolve(rootArg),out=path.resolve(outArg);
if(fs.existsSync(out))throw new Error('Use a new evidence directory');
fs.mkdirSync(out,{recursive:true});
const profile=path.join(out,'isolated-profile');fs.mkdirSync(profile);
app.setPath('userData',profile);
app.disableHardwareAcceleration();
const report={versions:process.versions,platform:process.platform,arch:process.arch,checks:[],errors:[]};
let finished=false;
function finish(error){if(finished)return;finished=true;if(error)report.errors.push(String(error));report.ok=!report.errors.length;fs.writeFileSync(path.join(out,'shell-result.json'),JSON.stringify(report,null,2));console.log(JSON.stringify(report));app.exit(report.ok?0:1);}
function check(condition,label){if(!condition)throw new Error(label);report.checks.push(label);}
setTimeout(()=>finish('Timed out'),45000);
const server=http.createServer((req,res)=>{
 let body='';req.on('data',c=>body+=c);req.on('end',()=>{
  if(req.url==='/api/failure'){res.writeHead(503,{'Content-Type':'application/json'});res.end(JSON.stringify({errorCode:'PROBE_TEMPORARY_FAILURE'}));return;}
  res.writeHead(200,{'Content-Type':'application/json'});res.end(JSON.stringify({method:req.method,url:req.url,auth:req.headers.authorization,body}));
 });
});
server.listen(0,'127.0.0.1',()=>{
 const address=server.address();
 fs.writeFileSync(path.join(profile,'config.json'),JSON.stringify({serverUrl:`http://127.0.0.1:${address.port}`,printer:{name:null,widthDots:576,cut:'none',kickDrawer:false,feedLines:3,threshold:160}}));
 require(path.join(root,'dist-electron/electron/main.js'));
 app.whenReady().then(async()=>{
  let main;
  for(let i=0;i<100;i++){
   main=BrowserWindow.getAllWindows().find(w=>w.webContents.getURL().endsWith('/index.html'));
   if(main&&!main.webContents.isLoading())break;
   await new Promise(r=>setTimeout(r,100));
  }
  if(!main)throw new Error('Main window missing');
  main.webContents.on('console-message',(_e,level,message)=>{if(level===3)report.errors.push(message);});
  const bridge=await main.webContents.executeJavaScript('({electron:window.posHost?.isElectron,platform:window.posHost?.platform,secure:isSecureContext,inputs:document.querySelectorAll("input").length})');
  check(bridge.electron&&bridge.secure&&bridge.inputs>0,'real CommonJS main/preload and login screen');
  const echo=await main.webContents.executeJavaScript(`fetch('/api/echo?test=1',{method:'POST',headers:{'Content-Type':'application/json','Authorization':'Bearer PROBE-NOT-A-CREDENTIAL'},body:JSON.stringify({label:'طلب اختبار',total:100})}).then(r=>r.json())`);
  check(echo.method==='POST'&&echo.url==='/api/echo?test=1'&&echo.auth==='Bearer PROBE-NOT-A-CREDENTIAL'&&JSON.parse(echo.body).label==='طلب اختبار','API POST body, query and authorization forwarding');
  const status=await main.webContents.executeJavaScript("fetch('/api/failure').then(async r=>({status:r.status,body:await r.json()}))");
  check(status.status===503&&status.body.errorCode==='PROBE_TEMPORARY_FAILURE','HTTP rejection remains HTTP rejection');
  await new Promise(r=>server.close(r));
  const networkFailure=await main.webContents.executeJavaScript("fetch('/api/echo').then(()=>false,()=>true)");
  check(networkFailure,'connection failure rejects fetch, not synthetic HTTP 500');
  const {renderReceiptBitmap,disposeRenderer}=require(path.join(root,'dist-electron/electron/printing/renderReceipt.js'));
  const {buildReceiptJob}=require(path.join(root,'dist-electron/electron/printing/escpos.js'));
  const payload={kind:'customer',lang:'ar',ticket:'PROBE',mode:'تيك أواي',clock:'15:00',cashierName:'تجربة',lines:[{qty:1,name:'وجبة تجريبية',total:'100'}],subtotal:'100',vat:'14',total:'114',branding:{brand:'مطعم تجريبي',branchName:'فرع تجريبي'},labels:{subtotal:'قبل الضريبة',vat:'الضريبة',total:'الإجمالي',customerCopy:'نسخة العميل',kitchenCopy:'نسخة المطبخ'}};
  const bitmap=await renderReceiptBitmap(payload,{widthDots:576});
  const job=buildReceiptJob(bitmap,{cut:'none',feed:3,kickDrawer:false});
  check(job.length>100&&job[0]===0x1b&&job[1]===0x40,'actual bitmap to ESC/POS job generation');
  report.printJobBytes=job.length;fs.writeFileSync(path.join(out,'synthetic-receipt.escpos'),job);disposeRenderer();
  // Expected network failures above may log Chromium resource errors. Capture separately.
  report.expectedNetworkConsole=report.errors.filter(e=>e.includes('ERR_FAILED')||e.includes('503'));
  report.errors=report.errors.filter(e=>!report.expectedNetworkConsole.includes(e));
  finish();
 }).catch(finish);
});
