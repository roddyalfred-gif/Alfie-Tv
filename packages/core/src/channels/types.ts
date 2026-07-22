export interface Channel {
  id: string;
  name: string;
  number: number;
  logo: string;
  streamUrl: string;
  category: string;
  isFavorite: boolean;
  quality: string;
  epgId?: string;
}

export interface ChannelGroup {
  id: string;
  name: string;
  channels: Channel[];
}

export interface ChannelFilter {
  category?: string;
  search?: string;
  favoriteOnly?: boolean;
}
