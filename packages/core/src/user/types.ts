export interface UserProfile {
  id: string;
  username: string;
  email: string;
  avatar?: string;
  theme: 'light' | 'dark';
  language: string;
  createdAt: number;
  updatedAt: number;
}

export interface UserPreferences {
  userId: string;
  defaultQuality: string;
  autoPlay: boolean;
  rememberPosition: boolean;
  lastPosition?: {
    channelId: string;
    timestamp: number;
  };
  favoriteChannels: string[];
}
