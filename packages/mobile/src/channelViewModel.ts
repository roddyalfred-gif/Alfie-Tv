export interface ChannelSummaryInput {
  id: string;
  name: string;
  category: string;
  quality?: string;
  isFavorite?: boolean;
}

export interface ChannelSummary {
  title: string;
  subtitle: string;
  status: 'Favorite' | 'Live';
}

export function buildChannelSummary(channel: ChannelSummaryInput): ChannelSummary {
  const quality = channel.quality || 'HD';
  const status = channel.isFavorite ? 'Favorite' : 'Live';

  return {
    title: channel.name,
    subtitle: `${channel.category} • ${quality}`,
    status,
  };
}
