import { useState } from 'react';
import { SplashScreen, VideoPlayer, ChannelList } from '@alfie-tv/ui';
import { ChannelManager, StreamQuality, StreamFormat, UserManager } from '@alfie-tv/core';
import './App.css';

function App() {
  const [showSplash, setShowSplash] = useState(true);
  const [channelManager] = useState(() => new ChannelManager());
  const [userManager] = useState(() => new UserManager());
  const [selectedChannel, setSelectedChannel] = useState<string | null>(null);

  const channels = channelManager.getAllChannels();

  const handleSplashComplete = () => {
    setShowSplash(false);
  };

  const handleChannelSelect = (channelId: string) => {
    setSelectedChannel(channelId);
  };

  const handleFavoriteToggle = (channelId: string) => {
    channelManager.toggleFavorite(channelId);
  };

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
            <ChannelList
              channels={channels}
              onChannelSelect={(channel) => handleChannelSelect(channel.id)}
              onFavoriteToggle={handleFavoriteToggle}
              selectedChannelId={selectedChannel || undefined}
            />
          </div>

          <div className="flex-1 flex flex-col">
            {selectedChannel ? (
              <div className="flex-1 bg-black">
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
