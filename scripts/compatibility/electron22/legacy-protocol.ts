// Experimental Electron 22 equivalent of electron/protocol.ts. Not installed in POS.
import { net, protocol } from 'electron';
import type { ProtocolRequest, ProtocolResponse } from 'electron';
import { createReadStream, statSync } from 'node:fs';
import { PassThrough, Readable } from 'node:stream';
import { extname, join, resolve, sep } from 'node:path';
import { loadConfig } from './config.js';
export const APP_SCHEME = 'app';
export const APP_HOST = 'pos';
export const APP_ORIGIN = `${APP_SCHEME}://${APP_HOST}`;
export function registerAppScheme(): void {
  protocol.registerSchemesAsPrivileged([{ scheme: APP_SCHEME, privileges: {
    standard: true, secure: true, supportFetchAPI: true, stream: true,
  } }]);
}
const mime: Record<string, string> = {
  '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.mjs': 'text/javascript',
  '.css': 'text/css', '.json': 'application/json', '.wasm': 'application/wasm',
  '.png': 'image/png', '.svg': 'image/svg+xml', '.jpg': 'image/jpeg',
  '.woff': 'font/woff', '.woff2': 'font/woff2', '.ico': 'image/x-icon',
};
export function registerAppProtocol(rendererRoot: string): void {
  const root = resolve(rendererRoot);
  protocol.registerStreamProtocol(APP_SCHEME, (request, callback) => {
    try {
      const url = new URL(request.url);
      if (url.hostname !== APP_HOST) return callback({ error: -10 });
      if (url.pathname === '/api' || url.pathname.startsWith('/api/')) {
        return proxy(request, url, callback);
      }
      const pathname = decodeURIComponent(url.pathname);
      let file = resolve(root, '.' + pathname);
      if (file !== root && !file.startsWith(root + sep)) return callback({ error: -10 });
      if (pathname === '/' || (!exists(file) && !extname(pathname))) file = join(root, 'index.html');
      if (!exists(file)) return callback({ statusCode: 404, data: Readable.from([]) });
      callback({ statusCode: 200, headers: { 'Content-Type': mime[extname(file)] || 'application/octet-stream' }, data: createReadStream(file) });
    } catch { callback({ error: -2 }); }
  });
}
function exists(file: string): boolean {
  try { return statSync(file).isFile(); } catch { return false; }
}
function proxy(request: ProtocolRequest, url: URL, callback: (response: ProtocolResponse) => void): void {
  const { serverUrl } = loadConfig();
  if (!serverUrl) return callback({ error: -2 });
  // POS API writes are JSON byte uploads. Do not silently omit unsupported uploads.
  if (request.uploadData?.some(part => !part.bytes)) return callback({ error: -2 });
  const upstream = net.request({ url: `${serverUrl}${url.pathname}${url.search}`, method: request.method, redirect: 'follow' });
  for (const [name, value] of Object.entries(request.headers)) {
    if (!['origin', 'referer', 'host', 'content-length'].includes(name.toLowerCase())) upstream.setHeader(name, value);
  }
  let answered = false;
  const timer = setTimeout(() => { upstream.abort(); reply({ error: -7 }); }, 30000);
  function reply(response: ProtocolResponse): void {
    if (answered) return;
    answered = true;
    clearTimeout(timer);
    callback(response);
  }
  upstream.on('error', () => reply({ error: -2 }));
  upstream.on('response', response => {
    const data = new PassThrough();
    response.on('data', chunk => data.write(chunk));
    response.on('end', () => data.end());
    response.on('error', error => data.destroy(error));
    reply({ statusCode: response.statusCode, headers: response.headers, data });
  });
  for (const part of request.uploadData || []) upstream.write(part.bytes);
  upstream.end();
}
