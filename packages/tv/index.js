const React = require('react');
const { createElement } = React;
const { createTvViewModel } = require('./src/tvApp.ts');
const { getTvRecommendations } = require('./src/recommendations.ts');

function App() {
  const viewModel = createTvViewModel();
  const recommendations = getTvRecommendations();

  return createElement(
    'div',
    { style: { padding: 24, color: '#f8fafc', background: 'linear-gradient(135deg, #020617 0%, #111827 100%)', minHeight: '100vh' } },
    createElement('h1', { style: { fontSize: 28, marginBottom: 8 } }, viewModel.title),
    createElement('p', { style: { color: '#cbd5e1' } }, 'A richer smart-TV shell with playback status, device awareness, and recommendations.'),
    createElement(
      'div',
      { style: { display: 'flex', gap: 8, marginTop: 16, flexWrap: 'wrap' } },
      ...viewModel.quickActions.map((action) =>
        createElement(
          'button',
          { key: action, style: { padding: '8px 12px', borderRadius: 999, border: '1px solid #334155', background: '#0f172a', color: '#f8fafc', cursor: 'pointer' } },
          action
        )
      )
    ),
    createElement(
      'div',
      { style: { marginTop: 18, padding: 14, background: 'rgba(15, 23, 42, 0.85)', borderRadius: 12, maxWidth: 480 } },
      createElement('div', { style: { fontWeight: 700, marginBottom: 4 } }, 'Now playing'),
      createElement('div', null, `Channel: ${viewModel.playback.channelId}`),
      createElement('div', { style: { color: '#94a3b8' } }, `Position: ${viewModel.playback.positionSeconds}s`),
      createElement('div', { style: { color: '#94a3b8' } }, `Remote control: ${viewModel.deviceProfile.supportsRemoteControl ? 'enabled' : 'disabled'}`)
    ),
    createElement(
      'div',
      { style: { marginTop: 16 } },
      createElement('h2', { style: { fontSize: 18 } }, 'Recommended for you'),
      ...recommendations.map((item) =>
        createElement(
          'div',
          { key: item.title, style: { marginTop: 8, padding: 10, background: '#111827', borderRadius: 8 } },
          createElement('div', { style: { fontWeight: 700 } }, item.title),
          createElement('div', { style: { color: '#94a3b8', marginTop: 4 } }, item.reason)
        )
      )
    )
  );
}

module.exports = { App };
