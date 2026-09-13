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
  style.textContent = [
    '.alfie-view-switcher{display:none;align-items:center;gap:4px;margin-left:auto}',
    '.alfie-view-switcher .view-label{font-size:11px;color:#64748b;margin-right:3px}',
    '.alfie-view-btn{border:1px solid #334155;background:#0f172a;color:#94a3b8;border-radius:7px;padding:7px 9px;line-height:1}',
    '.alfie-view-btn.active{background:#312e81;border-color:#6366f1;color:#fff}',
    '#page.alfie-layout-list .grid{grid-template-columns:1fr;gap:8px}',
    '#page.alfie-layout-list .grid .card{min-height:76px;display:flex;align-items:center}',
    '#page.alfie-layout-list .grid .poster{width:100px;min-width:100px;height:76px}',
    '#page.alfie-layout-list .grid .card-body{flex:1}',
    '#page.alfie-layout-list .grid .card-actions{margin-top:5px}',
    '#page.alfie-layout-tile .grid{grid-template-columns:repeat(auto-fill,minmax(105px,1fr));gap:10px}',
    '#page.alfie-layout-tile .grid .card{min-height:112px;text-align:center}',
    '#page.alfie-layout-tile .grid .poster{height:78px}',
    '#page.alfie-layout-tile .grid .card-body{padding:7px}',
    '#page.alfie-layout-tile .grid .card-title{font-size:12px}',
    '#page.alfie-layout-tile .grid .small{display:none}',
    '#page.alfie-layout-tile .grid .card-actions{justify-content:center;margin-top:5px}',
    '#page.alfie-layout-tile .grid .card-actions .icon{font-size:10px;padding:4px 6px}',
    '#page.alfie-layout-grid .grid{grid-template-columns:repeat(auto-fill,minmax(180px,1fr))}',
    '#page.alfie-layout-list .channel-list{display:flex;flex-direction:column}',
    '#page.alfie-layout-grid .channel-list{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));align-content:start;gap:4px}',
    '#page.alfie-layout-grid .channel-list .toolbar{grid-column:1/-1}',
    '#page.alfie-layout-tile .channel-list{display:grid;grid-template-columns:repeat(auto-fill,minmax(105px,1fr));align-content:start;gap:6px}',
    '#page.alfie-layout-tile .channel-list .toolbar{grid-column:1/-1}',
    '#page.alfie-layout-tile .channel{display:flex;flex-direction:column;justify-content:center;text-align:center;padding:9px 5px;min-height:105px;gap:5px}',
    '#page.alfie-layout-tile .channel img{width:48px;height:48px}',
    '#page.alfie-layout-tile .channel-main{width:100%}',
    '#page.alfie-layout-tile .channel-main .small{display:none}',
    '@media(max-width:900px){.alfie-view-switcher{display:flex}.alfie-view-switcher .view-label{display:none}#page.alfie-layout-grid .channel-list{grid-template-columns:1fr}}'
  ].join('');
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
module.exports = LAYOUT_PATCH;
