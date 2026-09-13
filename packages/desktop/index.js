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
    state.selected = {
      id: channel.stream_id,
      name: channel.name,
      epgChannelId: channel.epg_channel_id || channel.channel_id || '',
      url: streamUrl(channel, 'live'),
      type: 'Live'
    };
    state.epg = [];
    render();
    try {
      const response = await api('get_short_epg', { stream_id: String(channel.stream_id), limit: '12' });
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

const LAYOUT_PATCH = String.raw`
(() => {
  const KEY = 'alfie-layout-preferences-v1';
  const defaults = { live: 'list', movies: 'grid', series: 'grid', favorites: 'grid', guide: 'grid' };
  let prefs = { ...defaults };
  try { prefs = { ...defaults, ...JSON.parse(localStorage.getItem(KEY) || '{}') }; } catch {}

  const save = () => localStorage.setItem(KEY, JSON.stringify(prefs));
  const pageKey = () => ({ live:'live', movies:'movies', series:'series', favorites:'favorites', guide:'guide' }[state.page] || null);

  const style = document.createElement('style');
  style.id = 'alfie-layout-styles';
  style.textContent = `
    .alfie-view-switcher{display:none;align-items:center;gap:4px;margin-left:auto}
    .alfie-view-switcher .view-label{font-size:11px;color:#64748b;margin-right:3px}
    .alfie-view-btn{border:1px solid #334155;background:#0f172a;color:#94a3b8;border-radius:7px;padding:7px 9px;line-height:1}
    .alfie-view-btn.active{background:#312e81;border-color:#6366f1;color:#fff}
    #page.alfie-layout-list .grid{grid-template-columns:1fr;gap:8px}
    #page.alfie-layout-list .grid .card{min-height:76px;display:flex;align-items:center}
    #page.alfie-layout-list .grid .poster{width:100px;min-width:100px;height:76px}
    #page.alfie-layout-list .grid .card-body{flex:1}
    #page.alfie-layout-list .grid .card-actions{margin-top:5px}
    #page.alfie-layout-tile .grid{grid-template-columns:repeat(auto-fill,minmax(105px,1fr));gap:10px}
    #page.alfie-layout-tile .grid .card{min-height:112px;text-align:center}
    #page.alfie-layout-tile .grid .poster{height:78px}
    #page.alfie-layout-tile .grid .card-body{padding:7px}
    #page.alfie-layout-tile .grid .card-title{font-size:12px}
    #page.alfie-layout-tile .grid .small{display:none}
    #page.alfie-layout-tile .grid .card-actions{justify-content:center;margin-top:5px}
    #page.alfie-layout-tile .grid .card-actions .icon{font-size:10px;padding:4px 6px}
    #page.alfie-layout-grid .grid{grid-template-columns:repeat(auto-fill,minmax(180px,1fr))}
    #page.alfie-layout-list .channel-list{display:flex;flex-direction:column}
    #page.alfie-layout-grid .channel-list{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));align-content:start;gap:4px}
    #page.alfie-layout-grid .channel-list .toolbar{grid-column:1/-1}
    #page.alfie-layout-tile .channel-list{display:grid;grid-template-columns:repeat(auto-fill,minmax(105px,1fr));align-content:start;gap:6px}
    #page.alfie-layout-tile .channel-list .toolbar{grid-column:1/-1}
    #page.alfie-layout-tile .channel{display:flex;flex-direction:column;justify-content:center;text-align:center;padding:9px 5px;min-height:105px;gap:5px}
    #page.alfie-layout-tile .channel img{width:48px;height:48px}
    #page.alfie-layout-tile .channel-main{width:100%}
    #page.alfie-layout-tile .channel-main .small{display:none}
    #page.alfie-layout-tile .channel .icon{padding:3px 7px}
    @media(max-width:900px){.alfie-view-switcher{display:flex}.alfie-view-switcher .view-label{display:none}#page.alfie-layout-grid .channel-list{grid-template-columns:1fr}}
  `;
  document.head.appendChild(style);

  const ensureSwitcher = () => {
    let box = document.getElementById('alfieViewSwitcher');
    if (!box) {
      box = document.createElement('div');
      box.id = 'alfieViewSwitcher';
      box.className = 'alfie-view-switcher';
      box.innerHTML = '<span class="view-label">View</span><button class="alfie-view-btn" data-view="grid" title="Grid view">▦</button><button class="alfie-view-btn" data-view="list" title="List view">☰</button><button class="alfie-view-btn" data-view="tile" title="Tile icons view">⊞</button>';
      const topbar = document.querySelector('.topbar');
      if (topbar) topbar.insertBefore(box, document.getElementById('globalSearch'));
      box.addEventListener('click', (event) => {
        const button = event.target.closest('[data-view]');
        const key = pageKey();
        if (!button || !key) return;
        prefs[key] = button.dataset.view;
        save();
        apply();
      });
    }
    return box;
  };

  const apply = () => {
    const key = pageKey();
    const page = document.getElementById('page');
    const box = ensureSwitcher();
    if (!key || !page || !box) {
      if (box) box.style.display = 'none';
      if (page) page.classList.remove('alfie-layout-grid','alfie-layout-list','alfie-layout-tile');
      return;
    }
    const view = prefs[key] || defaults[key] || 'grid';
    page.classList.remove('alfie-layout-grid','alfie-layout-list','alfie-layout-tile');
    page.classList.add('alfie-layout-' + view);
    box.style.display = 'flex';
    box.querySelectorAll('[data-view]').forEach((button) => button.classList.toggle('active', button.dataset.view === view));
  };

  const observer = new MutationObserver(() => apply());
  const start = () => {
    const page = document.getElementById('page');
    if (page) observer.observe(page, { childList:true, subtree:true });
    apply();
  };
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start, { once:true }); else start();
  window.__alfieLayoutPatch = true;
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
  const win = new electron.BrowserWindow({
    width: 1440, height: 900, minWidth: 1050, minHeight: 700,
    backgroundColor: '#050816',
    webPreferences: { nodeIntegration: true, contextIsolation: false },
  });
  win.loadFile(path.join(__dirname, 'index.html'));
  win.webContents.on('did-finish-load', () => {
    win.webContents.executeJavaScript(GUIDE_PATCH).catch(() => {});
    win.webContents.executeJavaScript(LAYOUT_PATCH).catch(() => {});
  });
}

if (electron && electron.app) {
  electron.app.whenReady().then(createWindow);
  electron.app.on('window-all-closed', () => { if (process.platform !== 'darwin') electron.app.quit(); });
  electron.app.on('activate', () => { if (electron.BrowserWindow.getAllWindows().length === 0) createWindow(); });
}

module.exports = { createWindow, providerLogin, providerAction };