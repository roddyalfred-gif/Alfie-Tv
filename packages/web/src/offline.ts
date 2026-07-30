export function getOfflineStatusMessage(isOnline: boolean | null = null): string {
  if (typeof isOnline === 'boolean') {
    return isOnline ? 'Online' : 'Offline';
  }

  if (typeof navigator === 'undefined') {
    return 'Online';
  }

  return navigator.onLine ? 'Online' : 'Offline';
}
