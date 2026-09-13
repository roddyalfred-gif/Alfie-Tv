const path = require('path');
const http = require('http');
const https = require('https');
let electron;
try { electron = require('electron'); } catch { electron = null; }

function requestJson(url) {
  return new Promise((resolve, reject) => {
    const client = url.startsWith('https:') ? https : http;
    const req = client.get(url, { timeout: 20000, headers: { 'User-Agent': 'Alfie-TV-Desktop' } }, (res) => {
      let body = '';
      res.setEncoding('utf8');
      res.on('data', (chunk) => { body += chunk; });
      res.on('end', () => {
        if (res.statusCode < 200 || res.statusCode >= 300) return reject(new Error(`Provider HTTP ${res.statusCode}`));
        try { resolve(JSON.parse(body)); } catch { reject(new Error('Provider returned invalid JSON')); }
      });
    });
    req.on('timeout', () => req.destroy(new Error('Provider request timed out')));
    req.on('error', reject);
  });
}

function checkedBase(server) {
  const base = String(server || '').trim().replace(/\/+$/, '');
  const parsed = new URL(base);
  if (!['http:', 'https:'].includes(parsed.protocol)) throw new Error('Provider URL must use HTTP or HTTPS');
  return base;
}

function apiUrl(config, action, params = {}) {
  const base = checkedBase(config.server);
  const q = new URLSearchParams({ username: config.username, password: config.password, action, ...params });
  return `${base}/player_api.php?${q.toString()}`;
}

async function providerAction(config, action, params = {}) { return requestJson(apiUrl(config, action, params)); }

async function providerLogin(config) {
  const data = await requestJson(`${checkedBase(config.server)}/player_api.php?${new URLSearchParams({ username: config.username, password: config.password })}`);
  const info = data && data.user_info;
  if (!info || Number(info.auth) !== 1) throw new Error('Provider authentication failed');
  const status = String(info.status || '').toLowerCase();
  if (status && !['active', 'enabled'].includes(status)) throw new Error('Provider account is not active');
  return { ok: true, user: info };
}

function createWindow() {
  if (!electron || !electron.BrowserWindow) return;
  const { ipcMain } = electron;
  if (!ipcMain.listenerCount('alfie:provider')) {
    ipcMain.handle('alfie:provider', async (_event, payload) => {
      if (!payload || !payload.config) throw new Error('Provider configuration is required');
      if (payload.action === 'login') return providerLogin(payload.config);
      return providerAction(payload.config, payload.action, payload.params || {});
    });
  }
  const win = new electron.BrowserWindow({
    width: 1440, height: 900, minWidth: 1050, minHeight: 700,
    backgroundColor: '#050816',
    webPreferences: { nodeIntegration: true, contextIsolation: false },
  });
  win.loadFile(path.join(__dirname, 'index.html'));
}

if (electron && electron.app) {
  electron.app.whenReady().then(createWindow);
  electron.app.on('window-all-closed', () => { if (process.platform !== 'darwin') electron.app.quit(); });
  electron.app.on('activate', () => { if (electron.BrowserWindow.getAllWindows().length === 0) createWindow(); });
}

module.exports = { createWindow, providerLogin, providerAction };
