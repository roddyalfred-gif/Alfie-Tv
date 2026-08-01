import { useCallback, useEffect, useMemo, useState } from 'react';
import { SplashScreen, VideoPlayer, ChannelList } from '@alfie-tv/ui';
import {
  ChannelManager,
  StreamQuality,
  StreamFormat,
  UserManager,
  parseM3UPlaylist,
  generateRecommendations,
  PersistedHistoryStore,
  PersistedFavoritesStore,
  createDefaultPreferences,
  updatePreference,
  resolveVpnStatus,
} from '@alfie-tv/core';
import './App.css';
import { loginUser } from './auth';
import { getOfflineStatusMessage } from './offline';
import { readStoredPreferences, writeStoredPreferences } from './preferencesStorage';

const demoPlaylist = `#EXTM3U\n#EXTINF:-1 tvg-id="ch1" group-title="News",Demo Channel\nhttps://example.com/stream.m3u8`;

function App() {
  const [offlineStatus, setOfflineStatus] = useState(() => getOfflineStatusMessage());
  const [showSplash, setShowSplash] = useState(true);
  const [channelManager] = useState(() => new ChannelManager());
  const [userManager] = useState(() => new UserManager());
  const [historyStore] = useState(() => new PersistedHistoryStore('alfie-tv-history'));
  const [favoritesStore] = useState(() => new PersistedFavoritesStore('alfie-tv-favorites'));
  const [selectedChannel, setSelectedChannel] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const [favoritesOnly, setFavoritesOnly] = useState(false);
  const [_authToken, setAuthToken] = useState<string | null>(null);
  const [authUser, setAuthUser] = useState<{ username: string } | null>(null);
  const [username, setUsername] = useState('demo');
  const [password, setPassword] = useState('password');
  const [authError, setAuthError] = useState('');
  const [channelDetails, setChannelDetails] = useState<{ name: string; category: string; quality: string } | null>(null);
  const [resumeChannelId, setResumeChannelId] = useState<string | null>(null);
  const [preferences, setPreferences] = useState(() => readStoredPreferences(createDefaultPreferences()));

  const channels = useMemo(() => {
    const existing = channelManager.getAllChannels();
    if (existing.length === 0) {
      const parsed = parseM3UPlaylist(demoPlaylist);
      parsed.forEach((channel) => channelManager.addChannel(channel));
      return channelManager.getAllChannels();
    }

    return existing;
  }, [channelManager]);

  const filteredChannels = useMemo(() => {
    const normalizedQuery = query.trim();

    if (favoritesOnly) {
      return channelManager.filterChannels({
        favoriteOnly: true,
        search: normalizedQuery || undefined,
      });
    }

    if (!normalizedQuery) {
      return channels;
    }

    return channelManager.searchChannels(normalizedQuery);
  }, [channelManager, channels, favoritesOnly, query]);

  const recommendations = useMemo(() => {
    return generateRecommendations(channels, ['news'], ['news'], 3);
  }, [channels]);

  const favoriteChannels = useMemo(() => {
    return channels.filter((channel) => favoritesStore.has(channel.id));
  }, [channels, favoritesStore]);

  const isSelectedChannelFavorite = useMemo(() => {
    if (!selectedChannel) {
      return false;
    }

    return favoritesStore.has(selectedChannel);
  }, [favoritesStore, selectedChannel]);

  const vpnStatus = useMemo(() => resolveVpnStatus(preferences, navigator.onLine), [preferences]);

  const watchHistory = useMemo(() => {
    const entries = historyStore.load();
    if (entries.length === 0) {
      const demoEntries = [
        { channelId: 'demo-channel', watchedAt: Date.now(), positionSeconds: 120 },
        { channelId: 'demo-channel', watchedAt: Date.now() - 60000, positionSeconds: 420 },
      ];
      demoEntries.forEach((entry) => historyStore.add(entry));
    }

    const channelLabelMap: Record<string, string> = {
      'demo-channel': 'Demo Channel',
      'news': 'News Channel',
      'sports': 'Sports Channel',
    };

    return historyStore.getRecent(5).map((entry) => {
      const label = channelLabelMap[entry.channelId] || entry.channelId.replace(/-/g, ' ');
      const timestamp = new Date(entry.watchedAt).toLocaleTimeString([], {
        hour: '2-digit',
        minute: '2-digit',
      });

      return {
        channelId: entry.channelId,
        label: `${label} • ${timestamp}`,
      };
    });
  }, [historyStore]);

  const handleSplashComplete = useCallback(() => {
    setShowSplash(false);
  }, []);

  const activateChannel = useCallback(
    (channelId: string, positionSeconds = 0) => {
      const channel = channelManager.getChannel(channelId);
      const normalizedPosition = Math.max(positionSeconds, 0);

      setSelectedChannel(channelId);
      setResumeChannelId(channelId);
      setChannelDetails(
        channel
          ? { name: channel.name, category: channel.category, quality: channel.quality || '1080p' }
          : null
      );

      const latestEntry = historyStore.load()[0];
      if (latestEntry?.channelId !== channelId) {
        historyStore.add({ channelId, watchedAt: Date.now(), positionSeconds: normalizedPosition });
      }
    },
    [channelManager, historyStore]
  );

  const handleChannelSelect = useCallback(
    (channelId: string) => {
      activateChannel(channelId, 0);
      userManager.addWatchHistory('demo-user', channelId);
    },
    [activateChannel, userManager]
  );

  const handleFavoriteToggle = useCallback((channelId: string) => {
    favoritesStore.toggle(channelId);
    channelManager.toggleFavorite(channelId);
    userManager.updatePreferences('demo-user', { favoriteChannels: [...favoritesStore.getAll()] });
  }, [channelManager, favoritesStore, userManager]);

  const handleResumeRecentPick = useCallback(
    (channelId: string | null = resumeChannelId) => {
      if (!channelId) {
        return;
      }

      activateChannel(channelId, 120);
    },
    [activateChannel, resumeChannelId]
  );

  const _handleResumeSelection = useCallback(() => {
    if (!selectedChannel) {
      return;
    }

    activateChannel(selectedChannel, 120);
  }, []);

  useEffect(() => {
    const storedToken = localStorage.getItem('alfie-tv-auth-token');
    if (storedToken) {
      setAuthToken(storedToken);
      setAuthUser({ username: 'demo' });
    }

    const updateConnectionStatus = () => {
      setOfflineStatus(getOfflineStatusMessage(navigator.onLine));
    };

    updateConnectionStatus();
    window.addEventListener('online', updateConnectionStatus);
    window.addEventListener('offline', updateConnectionStatus);

    return () => {
      window.removeEventListener('online', updateConnectionStatus);
      window.removeEventListener('offline', updateConnectionStatus);
    };
  }, []);

  const handleLogin = useCallback(async (event: React.FormEvent) => {
    event.preventDefault();
    try {
      const result = await loginUser(username, password);
      setAuthToken(result.token);
      setAuthUser({ username: result.user.username });
      setAuthError('');
      localStorage.setItem('alfie-tv-auth-token', result.token);
    } catch {
      setAuthError('Unable to sign in');
    }
  }, [password, setAuthError, setAuthToken, setAuthUser, username]);

  const handleLogout = useCallback(() => {
    setAuthToken(null);
    setAuthUser(null);
    localStorage.removeItem('alfie-tv-auth-token');
  }, []);

  const handlePreferenceChange = useCallback(<T extends keyof typeof preferences>(key: T, value: (typeof preferences)[T]) => {
    setPreferences((current) => {
      const next = updatePreference(current, key, value);
      writeStoredPreferences(next);
      return next;
    });
  }, []);

  return (
    <div className="w-full h-screen bg-gray-900 text-white">
      {showSplash && (
        <SplashScreen
          config={{
            text: 'Alfie TV',
            duration: 3000,
            animation: 'fade',
          }}
          onComplete={handleSplashComplete}
        />
      )}

      {!showSplash && (
        <div className="flex h-full">
          <div className="w-64 bg-gray-800 p-4 overflow-y-auto border-r border-gray-700">
            <h2 className="text-xl font-bold mb-4">Channels</h2>
            <input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Search channels"
              className="w-full mb-3 rounded border border-gray-600 bg-gray-900 px-3 py-2 text-sm"
            />
            <button
              type="button"
              onClick={() => setFavoritesOnly((value) => !value)}
              className={`mb-4 w-full rounded border px-3 py-2 text-sm transition ${
                favoritesOnly
                  ? 'border-yellow-500 bg-yellow-600/20 text-yellow-200'
                  : 'border-gray-600 bg-gray-900 text-gray-300 hover:bg-gray-700'
              }`}
            >
              {favoritesOnly ? 'Showing favorites only' : 'Show favorites only'}
            </button>
            <ChannelList
              channels={filteredChannels}
              onChannelSelect={(channel) => handleChannelSelect(channel.id)}
              onFavoriteToggle={handleFavoriteToggle}
              selectedChannelId={selectedChannel || undefined}
            />
          </div>

          <div className="flex-1 flex flex-col">
            <div className="border-b border-gray-700 bg-gray-900 px-4 py-3">
              <div className="mb-3 flex items-center justify-between gap-3">
                <div>
                  <h3 className="text-sm font-semibold uppercase tracking-wide text-gray-400">Recommended</h3>
                  <p className="mt-1 text-xs text-slate-400">Offline status: {offlineStatus}</p>
                </div>
                {authUser ? (
                  <div className="flex items-center gap-2">
                    <span className="rounded-full bg-green-600/20 px-3 py-1 text-sm text-green-200">
                      {authUser.username}
                    </span>
                    <button
                      type="button"
                      onClick={handleLogout}
                      className="rounded border border-gray-700 px-2 py-1 text-sm text-gray-300"
                    >
                      Logout
                    </button>
                  </div>
                ) : (
                  <form onSubmit={handleLogin} className="flex items-center gap-2">
                    <input
                      value={username}
                      onChange={(event) => setUsername(event.target.value)}
                      placeholder="Username"
                      className="rounded border border-gray-700 bg-gray-800 px-2 py-1 text-sm"
                    />
                    <input
                      value={password}
                      onChange={(event) => setPassword(event.target.value)}
                      placeholder="Password"
                      type="password"
                      className="rounded border border-gray-700 bg-gray-800 px-2 py-1 text-sm"
                    />
                    <button type="submit" className="rounded bg-blue-600 px-3 py-1 text-sm">
                      Sign in
                    </button>
                  </form>
                )}
              </div>
              {authError ? <p className="mb-2 text-sm text-red-400">{authError}</p> : null}
              <div className="mt-2 flex flex-wrap gap-2">
                {recommendations.map((item) => (
                  <span key={item.id} className="rounded-full bg-blue-600/20 px-3 py-1 text-sm text-blue-200">
                    {item.category}
                  </span>
                ))}
              </div>
              <div className="mt-3 border-t border-gray-800 pt-3">
                <h4 className="text-xs uppercase tracking-wide text-gray-500">VPN integration</h4>
                <div className="mt-2 flex flex-wrap items-center gap-2">
                  <label className="flex items-center gap-2 rounded border border-gray-700 bg-gray-800 px-3 py-2 text-sm text-gray-200">
                    <input
                      type="checkbox"
                      checked={preferences.vpnEnabled}
                      onChange={(event) => handlePreferenceChange('vpnEnabled', event.target.checked)}
                    />
                    Enable VPN
                  </label>
                  <select
                    value={preferences.vpnMode}
                    onChange={(event) => handlePreferenceChange('vpnMode', event.target.value as typeof preferences.vpnMode)}
                    className="rounded border border-gray-700 bg-gray-800 px-2 py-2 text-sm"
                  >
                    <option value="off">Off</option>
                    <option value="auto">Auto</option>
                    <option value="on">On</option>
                  </select>
                  <input
                    value={preferences.vpnProvider}
                    onChange={(event) => handlePreferenceChange('vpnProvider', event.target.value)}
                    placeholder="Provider"
                    className="rounded border border-gray-700 bg-gray-800 px-2 py-2 text-sm"
                  />
                </div>
                <p className="mt-2 text-xs text-slate-400">
                  VPN status: {vpnStatus.message}
                </p>
              </div>
              <div className="mt-3 border-t border-gray-800 pt-3">
                <h4 className="text-xs uppercase tracking-wide text-gray-500">Saved favorites</h4>
                <div className="mt-2 flex flex-wrap gap-2">
                  {favoriteChannels.length > 0 ? (
                    favoriteChannels.map((channel) => (
                      <span key={channel.id} className="rounded-full bg-yellow-600/20 px-3 py-1 text-sm text-yellow-200">
                        {channel.name}
                      </span>
                    ))
                  ) : (
                    <span className="text-sm text-gray-500">No favorites yet</span>
                  )}
                </div>
              </div>
              <div className="mt-3 border-t border-gray-800 pt-3">
                <h4 className="text-xs uppercase tracking-wide text-gray-500">Recent picks</h4>
                <div className="mt-2 flex flex-col gap-2">
                  {watchHistory.map((entry, index) => (
                    <button
                      key={`${entry.channelId}-${index}`}
                      type="button"
                      onClick={() => handleResumeRecentPick(entry.channelId)}
                      className="rounded-full bg-gray-800 px-3 py-1 text-left text-sm text-gray-300 hover:bg-gray-700"
                    >
                      {entry.label}
                    </button>
                  ))}
                </div>
              </div>
            </div>
            {selectedChannel ? (
              <div className="flex-1 bg-black flex flex-col">
                <div className="border-b border-gray-800 bg-gray-900/90 px-4 py-3">
                  <div className="flex items-center justify-between">
                    <div>
                      <h3 className="text-lg font-semibold">{channelDetails?.name || selectedChannel}</h3>
                      <p className="text-sm text-gray-400">
                        {channelDetails?.category || 'Live channel'} • {channelDetails?.quality || '1080p'}
                      </p>
                    </div>
                    <span className="rounded-full bg-blue-600/20 px-3 py-1 text-sm text-blue-200">Now playing</span>
                  </div>
                  <div className="mt-3 flex flex-wrap gap-2">
                    <button
                      type="button"
                      onClick={() => selectedChannel && handleFavoriteToggle(selectedChannel)}
                      className={`rounded border px-3 py-1 text-sm ${
                        isSelectedChannelFavorite
                          ? 'border-yellow-500 bg-yellow-600/20 text-yellow-200'
                          : 'border-gray-700 text-gray-200'
                      }`}
                    >
                      {isSelectedChannelFavorite ? 'Favorited' : 'Add to favorites'}
                    </button>
                    <button
                      type="button"
                      onClick={() => handleResumeRecentPick(selectedChannel)}
                      className="rounded border border-gray-700 px-3 py-1 text-sm text-gray-200"
                    >
                      Resume
                    </button>
                    <button className="rounded border border-gray-700 px-3 py-1 text-sm text-gray-200">Watch later</button>
                  </div>
                </div>
                <div className="flex-1">
                  <VideoPlayer
                    streamConfig={{
                      url: 'https://example.com/stream.m3u8',
                      format: StreamFormat.HLS,
                      quality: StreamQuality.HD,
                      autoQuality: true,
                      bufferSize: 5,
                      timeout: 30000,
                      retryAttempts: 3,
                    }}
                  />
                </div>
              </div>
            ) : (
              <div className="flex-1 flex items-center justify-center">
                <p className="text-gray-400 text-xl">Select a channel to watch</p>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

export default App;
