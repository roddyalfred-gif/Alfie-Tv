import { mkdirSync, readFileSync, writeFileSync, existsSync } from 'fs';
import path from 'path';

export interface StoredChannel {
  id: string;
  name: string;
  category: string;
  streamUrl: string;
  isFavorite?: boolean;
}

export class FileStore {
  constructor(private readonly filePath: string) {}

  private normalizeChannel(channel: StoredChannel): StoredChannel | null {
    if (!channel?.id || !channel?.name || !channel?.streamUrl) {
      return null;
    }

    return {
      ...channel,
      category: channel.category || 'General',
      isFavorite: Boolean(channel.isFavorite),
    };
  }

  setChannels(channels: StoredChannel[]): void {
    const normalizedChannels = channels
      .map((channel) => this.normalizeChannel(channel))
      .filter((channel): channel is StoredChannel => channel !== null);

    mkdirSync(path.dirname(this.filePath), { recursive: true });
    writeFileSync(this.filePath, JSON.stringify({ channels: normalizedChannels }, null, 2));
  }

  getChannels(): StoredChannel[] {
    if (!existsSync(this.filePath)) {
      return [];
    }

    try {
      const raw = readFileSync(this.filePath, 'utf8');
      const parsed = JSON.parse(raw) as { channels?: StoredChannel[] };
      const channels = Array.isArray(parsed.channels) ? parsed.channels : [];

      return channels
        .map((channel) => this.normalizeChannel(channel))
        .filter((channel): channel is StoredChannel => channel !== null);
    } catch {
      return [];
    }
  }
}
