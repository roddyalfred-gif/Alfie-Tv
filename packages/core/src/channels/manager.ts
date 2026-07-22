import { Channel, ChannelFilter, ChannelGroup } from './types';

export class ChannelManager {
  private channels: Map<string, Channel> = new Map();
  private groups: Map<string, ChannelGroup> = new Map();
  private favorites: Set<string> = new Set();

  addChannel(channel: Channel): void {
    this.channels.set(channel.id, channel);
  }

  getChannel(id: string): Channel | undefined {
    return this.channels.get(id);
  }

  getAllChannels(): Channel[] {
    return Array.from(this.channels.values());
  }

  searchChannels(query: string): Channel[] {
    const lowerQuery = query.toLowerCase();
    return Array.from(this.channels.values()).filter(
      (channel) =>
        channel.name.toLowerCase().includes(lowerQuery) ||
        channel.category.toLowerCase().includes(lowerQuery)
    );
  }

  filterChannels(filter: ChannelFilter): Channel[] {
    let filtered = Array.from(this.channels.values());

    if (filter.category) {
      filtered = filtered.filter((c) => c.category === filter.category);
    }

    if (filter.search) {
      filtered = filtered.filter((c) =>
        c.name.toLowerCase().includes(filter.search!.toLowerCase())
      );
    }

    if (filter.favoriteOnly) {
      filtered = filtered.filter((c) => this.favorites.has(c.id));
    }

    return filtered;
  }

  toggleFavorite(channelId: string): boolean {
    if (this.favorites.has(channelId)) {
      this.favorites.delete(channelId);
      return false;
    } else {
      this.favorites.add(channelId);
      return true;
    }
  }

  getFavorites(): Channel[] {
    return Array.from(this.channels.values()).filter((c) => this.favorites.has(c.id));
  }

  createGroup(id: string, name: string, channels: Channel[]): void {
    this.groups.set(id, { id, name, channels });
  }

  getGroup(id: string): ChannelGroup | undefined {
    return this.groups.get(id);
  }

  getAllGroups(): ChannelGroup[] {
    return Array.from(this.groups.values());
  }
}

export * from './types';
