import React from 'react';
import { Channel } from '@alfie-tv/core';

interface ChannelListProps {
  channels: Channel[];
  onChannelSelect: (channel: Channel) => void;
  onFavoriteToggle: (channelId: string) => void;
  selectedChannelId?: string;
}

export const ChannelList: React.FC<ChannelListProps> = ({
  channels,
  onChannelSelect,
  onFavoriteToggle,
  selectedChannelId,
}) => {
  return (
    <div className="flex flex-col gap-2 h-full overflow-y-auto">
      {channels.map((channel) => (
        <div
          key={channel.id}
          className={`
            flex items-center gap-3 p-3 rounded cursor-pointer transition
            ${
              selectedChannelId === channel.id
                ? 'bg-blue-600 text-white'
                : 'bg-gray-800 hover:bg-gray-700'
            }
          `}
          onClick={() => onChannelSelect(channel)}
          onKeyDown={(event) => {
            if (event.key === 'Enter' || event.key === ' ') {
              event.preventDefault();
              onChannelSelect(channel);
            }
          }}
          role="button"
          tabIndex={0}
        >
          {channel.logo && (
            <img
              src={channel.logo}
              alt={channel.name}
              className="w-10 h-10 rounded object-cover"
            />
          )}

          <div className="flex-1 min-w-0">
            <div className="flex items-center justify-between gap-2">
              <h3 className="font-semibold truncate">{channel.name}</h3>
              <div className="flex items-center gap-2">
                {selectedChannelId === channel.id ? (
                  <span className="rounded-full bg-blue-500/20 px-2 py-0.5 text-[10px] uppercase tracking-wide text-blue-200">
                    Live
                  </span>
                ) : null}
                <span className="rounded-full bg-gray-700/80 px-2 py-0.5 text-[10px] uppercase tracking-wide text-gray-300">
                  {channel.quality || 'HD'}
                </span>
              </div>
            </div>
            <p className="mt-1 text-xs opacity-75">{channel.category}</p>
          </div>

          <button
            type="button"
            aria-label={channel.isFavorite ? `Remove ${channel.name} from favorites` : `Add ${channel.name} to favorites`}
            onClick={(e) => {
              e.stopPropagation();
              onFavoriteToggle(channel.id);
            }}
            className={`text-xl ${
              channel.isFavorite ? 'text-yellow-400' : 'text-gray-400'
            } hover:text-yellow-400`}
          >
            {channel.isFavorite ? '★' : '☆'}
          </button>
        </div>
      ))}
    </div>
  );
};
