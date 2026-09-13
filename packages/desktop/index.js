const path = require('path');
const http = require('http');
const https = require('https');
let electron;
try { electron = require('electron'); } catch { electron = null; }
const LAYOUT_PATCH = require('./layoutPatch');
const RUNTIME_PATCH = require('./runtimePatch');

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

const GUIDE_PATCH = String.raw`
(() => {
  let guideRequest = 0;
  const parseGuideTime = (value) => {
    if (value == null || value === '') return NaN;
    const n = Number(value);
    if (Number.isFinite(n)) return n < 100000000000 ? n * 1000 : n;
    const d = new Date(String(value).replace(' ', 'T'));
    return d.getTime();
  };
  const extractListings = (response) => {
    if (Array.isArray(response)) return response;
    if (Array.isArray(response?.epg_listings)) return response.epg_listings;
    if (Array.isArray(response?.epg)) return response.epg;
    if (Array.isArray(response?.data)) return response.data;
    return [];
  };
  const normalizeListings = (items) => {
    const listings = items
      .map((p) => ({ ...p, _start: parseGuideTime(p.start ?? p.start_timestamp), _end: parseGuideTime(p.end ?? p.stop_timestamp) }))
      .filter((p) => Number.isFinite(p._start) && Number.isFinite(p._end) && p._end > p._start)
      .sort((a, b) => a._start - b._start);
    const now = Date.now();
    const currentIndex = listings.findIndex((p) => p._start <= now && now < p._end);
    if (currentIndex > 0) {
      const current = listings.splice(currentIndex, 1)[0];
      listings.unshift(current);
    }
    return listings.map((p, index) => ({ ...p, _isNow: index === 0 && p._start <= now && now < p._end }));
  };
  window.playLive = async (id) => {
    const channel = state.channels.find((x) => String(x.stream_id) === String(id));
    if (!channel) return;
    const requestId = ++guideRequest;
    state.page = 'live';
    state.selected = { id: channel.stream_id, name: channel.name, epgChannelId: channel.epg_channel_id || channel.channel_id || '', url: streamUrl(channel, 'live'), type: 'Live' };
    state.epg = [];
    render();
    try {
      const response = await api('get_short_epg', { stream_id: String(id), limit: '12' });
      if (requestId !== guideRequest) return;
      const listings = normalizeListings(extractListings(response));
      const epgId = channel.epg_channel_id || channel.channel_id || '';
      state.epg = listings.filter((p) => !p.channel_id || !epgId || String(p.channel_id) === String(epgId));
      render();
    } catch (error) {
      if (requestId !== guideRequest) return;
      state.epg = [];
      render();
    }
  };
  window.__alfieGuidePatch = true;
})();
`;

const PLAYER_PATCH = String.raw`
(() => {
  let hls = null;
  let retryTimer = null;
  const destroy = () => { if (hls) { try { hls.destroy(); } catch {} hls = null; } if (retryTimer) { clearTimeout(retryTimer); retryTimer = null; } };
  const attach = (video) => {
    if (!video || video.dataset.alfieHlsAttached === '1') return;
    video.dataset.alfieHlsAttached = '1';
    const url = video.currentSrc || video.src;
    if (!url) return;
    destroy();
    const isHls = /\.m3u8(?:$|[?#])/i.test(url);
    if (!isHls) return;
    try {
      const Hls = require('hls.js');
      if (Hls && Hls.isSupported()) {
        hls = new Hls({ enableWorker: true, lowLatencyMode: true, backBufferLength: 90, maxBufferLength: 30, manifestLoadingTimeOut: 20000, fragLoadingTimeOut: 20000 });
        hls.loadSource(url);
        hls.attachMedia(video);
        hls.on(Hls.Events.ERROR, (_event, data) => {
          if (!data?.fatal) return;
          if (data.type === Hls.ErrorTypes.NETWORK_ERROR) {
            try { hls.startLoad(); } catch {}
          } else if (data.type === Hls.ErrorTypes.MEDIA_ERROR) {
            try { hls.recoverMediaError(); } catch {}
          } else {
            destroy();
            retryTimer = setTimeout(() => attach(video), 1500);
          }
        });
        return;
      }
    } catch {}
    video.load();
    video.play().catch(() => {});
  };
  const observe = () => {
    const video = document.querySelector('#video');
    if (video) attach(video);
  };
  new MutationObserver(observe).observe(document.documentElement, { childList: true, subtree: true });
  document.addEventListener('loadedmetadata', observe, true);
  document.addEventListener('error', observe, true);
  window.addEventListener('beforeunload', destroy);
  observe();
  window.__alfiePlayerPatch = true;
})();
`;

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
  const win = new electron.BrowserWindow({ width: 1440, height: 900, minWidth: 1050, minHeight: 700, backgroundColor: '#050816', webPreferences: { nodeIntegration: true, contextIsolation: false } });
  win.loadFile(path.join(__dirname, 'index.html'));
  win.webContents.on('did-finish-load', () => {
    win.webContents.executeJavaScript(RUNTIME_PATCH).catch(() => {});
    win.webContents.executeJavaScript(GUIDE_PATCH).catch(() => {});
    win.webContents.executeJavaScript(PLAYER_PATCH).catch(() => {});
    win.webContents.executeJavaScript(LAYOUT_PATCH).catch(() => {});
  });
}

if (electron && electron.app) {
  electron.app.whenReady().then(createWindow);
  electron.app.on('window-all-closed', () => { if (process.platform !== 'darwin') electron.app.quit(); });
  electron.app.on('activate', () => { if (electron.BrowserWindow.getAllWindows().length === 0) createWindow(); });
}

module.exports = { createWindow, providerLogin, providerAction };