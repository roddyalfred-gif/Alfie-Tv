import { mkdirSync, readFileSync, writeFileSync, existsSync } from 'fs';
import path from 'path';

export interface StoredProfile {
  id: string;
  username: string;
  email: string;
  theme: string;
  language: string;
  createdAt: number;
  updatedAt: number;
}

export class ProfileStore {
  constructor(private readonly filePath: string) {}

  private ensureFile(): void {
    mkdirSync(path.dirname(this.filePath), { recursive: true });
    if (!existsSync(this.filePath)) {
      writeFileSync(this.filePath, JSON.stringify({ profiles: [] }, null, 2));
    }
  }

  getProfile(userId: string): StoredProfile | undefined {
    this.ensureFile();
    const raw = readFileSync(this.filePath, 'utf8');
    const parsed = JSON.parse(raw) as { profiles?: StoredProfile[] };
    return parsed.profiles?.find((profile) => profile.id === userId);
  }

  saveProfile(profile: StoredProfile): StoredProfile {
    this.ensureFile();
    const raw = readFileSync(this.filePath, 'utf8');
    const parsed = JSON.parse(raw) as { profiles?: StoredProfile[] };
    const profiles = parsed.profiles ?? [];
    const existingIndex = profiles.findIndex((item) => item.id === profile.id);

    if (existingIndex >= 0) {
      profiles[existingIndex] = profile;
    } else {
      profiles.push(profile);
    }

    writeFileSync(this.filePath, JSON.stringify({ profiles }, null, 2));
    return profile;
  }
}
