import { UserPreferences, UserProfile } from './types';

export class UserManager {
  private profiles: Map<string, UserProfile> = new Map();
  private preferences: Map<string, UserPreferences> = new Map();

  createProfile(profile: UserProfile): void {
    this.profiles.set(profile.id, profile);
    this.preferences.set(profile.id, {
      userId: profile.id,
      defaultQuality: '1080p',
      autoPlay: true,
      rememberPosition: true,
      favoriteChannels: [],
    });
  }

  getProfile(userId: string): UserProfile | undefined {
    return this.profiles.get(userId);
  }

  updateProfile(userId: string, updates: Partial<UserProfile>): void {
    const profile = this.profiles.get(userId);
    if (profile) {
      this.profiles.set(userId, { ...profile, ...updates, updatedAt: Date.now() });
    }
  }

  getPreferences(userId: string): UserPreferences | undefined {
    return this.preferences.get(userId);
  }

  updatePreferences(userId: string, updates: Partial<UserPreferences>): void {
    const prefs = this.preferences.get(userId);
    if (prefs) {
      this.preferences.set(userId, { ...prefs, ...updates });
    }
  }
}

export * from './types';
