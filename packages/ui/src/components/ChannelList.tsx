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
        >
          {channel.logo && (
            <img
              src={channel.logo}
              alt={channel.name}
              className="w-10 h-10 rounded object-cover"
            />
          )}

          <div className="flex-1 min-w-0">
            <h3 className="font-semibold truncate">{channel.name}</h3>
            <p className="text-xs opacity-75">{channel.category}</p>
          </div>

          <button
            onClick={(e) => {
              e.stopPropagation();
              onFavoriteToggle(channel.id);
            }}
            className={`text-xl ${
              channel.isFavorite ? 'text-yellow-400' : 'text-gray-400'
            } hover:text-yellow-400`}
          >
            ★
          </button>
        </div>
      ))}
    </div>
  );
};
