export function registerServiceWorker() {
  if (typeof navigator === 'undefined' || !('serviceWorker' in navigator)) {
    return;
  }

  navigator.serviceWorker.register('/sw.js').catch(() => undefined);
}
