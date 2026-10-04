// Electron 22-compatible shell, local-only. Not the production application shell.
const { app, BrowserWindow, protocol, ipcMain, net } = require('electron');
const fs = require('fs');
const path = require('path');
const phase = process.argv.includes('--verify') ? 'verify' : 'write';
app.setPath('userData', path.join(__dirname, 'isolated-profile'));
app.disableHardwareAcceleration();
protocol.registerSchemesAsPrivileged([{ scheme: 'probe', privileges: { standard: true, secure: true, supportFetchAPI: true } }]);
let done = false;
const report = { phase, platform: process.platform, arch: process.arch, versions: process.versions,
  runtimeAPIs: { protocolHandle: typeof protocol.handle, netFetch: typeof net.fetch },
  scope: 'Actual POS SQLite/outbox under a minimal legacy shell; not a Windows or printer test' };
function finish(result) {
  if (done) return;
  done = true;
  Object.assign(report, result);
  fs.writeFileSync(path.join(__dirname, phase + '-result.json'), JSON.stringify(report, null, 2));
  console.log(JSON.stringify(report));
  app.exit(result.ok ? 0 : 1);
}
const timer = setTimeout(() => finish({ ok: false, error: 'Probe timed out after 45 seconds' }), 45000);
ipcMain.once('probe-result', (_event, data) => { clearTimeout(timer); finish(data); });
app.whenReady().then(() => {
  protocol.registerFileProtocol('probe', (request, callback) => {
    const url = new URL(request.url);
    const file = path.resolve(__dirname, '.' + decodeURIComponent(url.pathname));
    if (url.hostname !== 'local' || !file.startsWith(__dirname + path.sep)) return callback({ error: -10 });
    callback({ path: file });
  });
  const win = new BrowserWindow({ show: false, webPreferences: { preload: path.join(__dirname, 'preload.cjs'), contextIsolation: true, nodeIntegration: false, sandbox: true } });
  win.webContents.on('render-process-gone', (_e, details) => finish({ ok: false, error: 'Renderer exited', details }));
  win.loadURL('probe://local/index.html?phase=' + phase).catch(e => finish({ ok: false, error: String(e) }));
}).catch(e => finish({ ok: false, error: String(e) }));
