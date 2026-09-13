module.exports = String.raw`
(() => {
  const clean = (value) => String(value || '').trim().replace(/\/+$/, '');
  const encoded = (value) => encodeURIComponent(String(value || ''));
  const providerStreamUrl = (item, type = 'live') => {
    if (!item) return '';
    if (item.direct_source) return String(item.direct_source);
    if (item.stream_url) return String(item.stream_url);
    if (item.url) return String(item.url);
    const cfg = typeof config !== 'undefined' ? config : window.config;
    if (!cfg || !cfg.server) return '';
    const base = clean(cfg.server);
    const user = encoded(cfg.username);
    const pass = encoded(cfg.password);
    const id = encoded(item.stream_id ?? item.id);
    if (!id) return '';
    if (type === 'live') return `${base}/live/${user}/${pass}/${id}`;
    const folder = type === 'movie' ? 'movie' : 'series';
    const ext = String(item.container_extension || 'mp4').replace(/^\./, '') || 'mp4';
    return `${base}/${folder}/${user}/${pass}/${id}.${ext}`;
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
      message.textContent = 'Playback error — check the provider stream or try another channel.';
      Object.assign(message.style, { color:'#fda4af', padding:'10px 2px', fontSize:'12px' });
      box.appendChild(message);
    });
    video.addEventListener('playing', () => {
      const message = video.closest('.player-box')?.querySelector('.alfie-playback-error');
      if (message) message.remove();
    });
  };
  new MutationObserver(installVideoGuard).observe(document.documentElement, { childList: true, subtree: true });
  installVideoGuard();
  window.__alfieRuntimePatch = true;
})();
`;