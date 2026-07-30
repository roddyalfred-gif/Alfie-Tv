import { Channel, ChannelFilter, ChannelGroup } from './types';

export class ChannelManager {
  private channels: Map<string, Channel> = new Map();
  private groups: Map<string, ChannelGroup> = new Map();
  private favorites: Set<string> = new Set();
  private cachedChannelList: Channel[] = [];

  private syncCachedChannelList(): void {
    this.cachedChannelList = Array.from(this.channels.values()).map((channel) => ({
      ...channel,
      isFavorite: this.favorites.has(channel.id),
    }));
  }

  addChannel(channel: Channel): void {
    this.channels.set(channel.id, { ...channel, isFavorite: this.favorites.has(channel.id) });
    this.syncCachedChannelList();
  }

  getChannel(id: string): Channel | undefined {
    return this.channels.get(id);
  }

  getAllChannels(): Channel[] {
    return this.cachedChannelList;
  }

  searchChannels(query: string): Channel[] {
    const lowerQuery = query.toLowerCase();
    return this.cachedChannelList.filter(
      (channel) =>
        channel.name.toLowerCase().includes(lowerQuery) ||
        channel.category.toLowerCase().includes(lowerQuery)
    );
  }

  filterChannels(filter: ChannelFilter): Channel[] {
    let filtered = this.cachedChannelList;

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
    const channel = this.channels.get(channelId);

    if (this.favorites.has(channelId)) {
      this.favorites.delete(channelId);
      if (channel) {
        this.channels.set(channelId, { ...channel, isFavorite: false });
      }
      this.syncCachedChannelList();
      return false;
    }

    this.favorites.add(channelId);
    if (channel) {
      this.channels.set(channelId, { ...channel, isFavorite: true });
    }
    this.syncCachedChannelList();
    return true;
  }

  getFavorites(): Channel[] {
    return this.cachedChannelList.filter((c) => this.favorites.has(c.id));
  }

  createGroup(id: string, name: string, channels: Channel[]): void {
    this.groups.set(id, { id, name, channels });
  }

  importFromM3U(content: string): Channel[] {
    const lines = content.split(/\r?\n/);
    const importedChannels: Channel[] = [];
    const seenUrls = new Set<string>();
    let pendingName = 'Imported Channel';

    lines.forEach((line) => {
      const trimmed = line.trim();

      if (!trimmed) {
        return;
      }

      if (trimmed.startsWith('#EXTINF')) {
        const match = trimmed.match(/,(.+)$/);
        if (match?.[1]) {
          pendingName = match[1].trim();
        }
        return;
      }

      if (trimmed.startsWith('#')) {
        return;
      }

      if (seenUrls.has(trimmed)) {
        return;
      }

      seenUrls.add(trimmed);
      const channel: Channel = {
        id: `imported-${importedChannels.length + 1}`,
        name: pendingName || `Imported Channel ${importedChannels.length + 1}`,
        number: importedChannels.length + 1,
        logo: '',
        streamUrl: trimmed,
        category: 'Imported',
        isFavorite: false,
        quality: '1080p',
      };

      importedChannels.push(channel);
      this.addChannel(channel);
    });

    return importedChannels;
  }

  getGroup(id: string): ChannelGroup | undefined {
    return this.groups.get(id);
  }

  getAllGroups(): ChannelGroup[] {
    return Array.from(this.groups.values());
  }
}

export * from './types';
