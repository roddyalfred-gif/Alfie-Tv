module.exports = String.raw`
(() => {
  const clean = (value) => String(value || '').trim().replace(/\/+$/, '');
  const encoded = (value) => encodeURIComponent(String(value || ''));
  const providerStreamUrl = (item, type = 'live') => {
    if (!item) return '';
    if (item.direct_source) return String(item.direct_source).trim();
    if (item.stream_url) return String(item.stream_url).trim();
    if (item.url) return String(item.url).trim();
    const cfg = typeof config !== 'undefined' ? config : window.config;
    if (!cfg || !cfg.server) return '';
    const base = clean(cfg.server);
    const user = encoded(cfg.username);
    const pass = encoded(cfg.password);
    const id = encoded(item.stream_id ?? item.id);
    if (!id) return '';
    if (type === 'live') {
      const ext = String(item.container_extension || item.extension || '').replace(/^\./, '').trim();
      return base + '/live/' + user + '/' + pass + '/' + id + (ext ? '.' + ext : '.ts');
    }
    const folder = type === 'movie' ? 'movie' : 'series';
    const ext = String(item.container_extension || 'mp4').replace(/^\./, '') || 'mp4';
    return base + '/' + folder + '/' + user + '/' + pass + '/' + id + '.' + ext;
  };
  window.streamUrl = providerStreamUrl;
  const installVideoGuard = () => {
    const video = document.querySelector('#video');
    if (!video || video.dataset.alfieGuard === '1') return;
    video.dataset.alfieGuard = '1';
    video.addEventListener('error', () => {
      const box = video.closest('.player-box') || video.parentElement;
      if (!box || box.querySelector('.alfie-playback-error')) return;
      const message = document.createElement('div');
      message.className = 'alfie-playback-error';
      message.textContent = 'Playback error — check the provider stream or try another title/channel.';
      Object.assign(message.style, { color:'#fda4af', padding:'10px 2px', fontSize:'12px' });
      box.appendChild(message);
    });
    video.addEventListener('playing', () => {
      const message = video.closest('.player-box')?.querySelector('.alfie-playback-error');
      if (message) message.remove();
    });
  };
  const restoreProvider = () => {
    try {
      const raw = localStorage.getItem('alfie-provider');
      if (!raw || document.querySelector('#login')?.classList.contains('hidden')) return;
      const saved = JSON.parse(raw);
      if (!saved?.server || !saved?.username) return;
      const server = document.querySelector('#server');
      const username = document.querySelector('#username');
      const password = document.querySelector('#password');
      if (!server || !username || !password) return;
      server.value = saved.server;
      username.value = saved.username;
      password.value = saved.password || '';
      if (saved.password) document.querySelector('#loginForm')?.requestSubmit();
    } catch {}
  };
  new MutationObserver(() => { installVideoGuard(); restoreProvider(); }).observe(document.documentElement, { childList: true, subtree: true });
  installVideoGuard();
  setTimeout(restoreProvider, 0);
  window.__alfieRuntimePatch = true;
})();
`;