import { mkdirSync, readFileSync, writeFileSync, existsSync } from 'fs';
import path from 'path';

export interface DatabaseChannel {
  id: string;
  name: string;
  category: string;
  streamUrl: string;
  isFavorite?: boolean;
  number?: number;
  logo?: string;
  quality?: string;
}

export class JsonDatabase {
  constructor(private readonly filePath: string) {}

  private ensureFile(): void {
    mkdirSync(path.dirname(this.filePath), { recursive: true });
    if (!existsSync(this.filePath)) {
      writeFileSync(this.filePath, JSON.stringify({ channels: [] }, null, 2));
    }
  }

  listChannels(): DatabaseChannel[] {
    this.ensureFile();
    const raw = readFileSync(this.filePath, 'utf8');
    const parsed = JSON.parse(raw) as { channels?: DatabaseChannel[] };
    return parsed.channels ?? [];
  }

  saveChannels(channels: DatabaseChannel[]): void {
    this.ensureFile();
    writeFileSync(this.filePath, JSON.stringify({ channels }, null, 2));
  }

  addChannel(channel: DatabaseChannel): DatabaseChannel {
    const channels = this.listChannels();
    channels.push(channel);
    this.saveChannels(channels);
    return channel;
  }
}
