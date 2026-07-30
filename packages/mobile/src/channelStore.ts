import { Channel } from '@alfie-tv/core';

export interface MobileChannel extends Channel {
  featured?: boolean;
}

const demoChannels: MobileChannel[] = [
  {
    id: 'news',
    name: 'News HD',
    number: 1,
    logo: '',
    streamUrl: 'https://example.com/news.m3u8',
    category: 'News',
    isFavorite: true,
    quality: '1080p',
    featured: true,
  },
  {
    id: 'sports',
    name: 'Sports Live',
    number: 2,
    logo: '',
    streamUrl: 'https://example.com/sports.m3u8',
    category: 'Sports',
    isFavorite: false,
    quality: '720p',
  },
];

export function getMobileChannels(): MobileChannel[] {
  return demoChannels;
}
