import express from 'express';
import cors from 'cors';
import dotenv from 'dotenv';
import { createToken, verifyToken } from './auth';
import { FileStore } from './store';
import { InMemoryCache } from './cache';
import { JsonDatabase } from './database';
import { ProfileStore } from './profile-store';

dotenv.config();

const app = express();
const PORT = Number(process.env.PORT) || 3000;
const store = new FileStore(process.env.DATA_FILE || './data/channels.json');
const database = new JsonDatabase(process.env.DATABASE_FILE || './data/database.json');
const profileStore = new ProfileStore(process.env.PROFILE_FILE || './data/profiles.json');
const cache = new InMemoryCache<{ channels: Array<{ id: string; name: string; category: string; streamUrl: string; isFavorite?: boolean }> }>(60000);

function createUserId(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`;
}

export function buildSeedChannels() {
  return [
    {
      id: 'ch-1',
      name: 'Alfie News',
      number: 1,
      logo: '',
      streamUrl: 'https://example.com/stream1.m3u8',
      category: 'News',
      isFavorite: false,
      quality: '1080p',
    },
    {
      id: 'ch-2',
      name: 'Alfie Sports',
      number: 2,
      logo: '',
      streamUrl: 'https://example.com/stream2.m3u8',
      category: 'Sports',
      isFavorite: false,
      quality: '4K',
    },
    {
      id: 'ch-3',
      name: 'Alfie Movies',
      number: 3,
      logo: '',
      streamUrl: 'https://example.com/stream3.m3u8',
      category: 'Movies',
      isFavorite: false,
      quality: '1080p',
    },
  ];
}

export function buildSeedPrograms(channelId: string) {
  const now = Date.now();

  return [
    {
      id: 'prog-1',
      channelId,
      title: 'Live Headlines',
      description: 'Current stories from around the globe.',
      startTime: now - 3600000,
      endTime: now,
      duration: 3600000,
      genre: 'News',
    },
    {
      id: 'prog-2',
      channelId,
      title: 'Next Up',
      description: 'A preview of what is coming next on the channel.',
      startTime: now,
      endTime: now + 3600000,
      duration: 3600000,
      genre: 'News',
    },
  ];
}

// Middleware
app.use(express.json());
app.use(express.urlencoded({ extended: true }));
app.use(cors());

// Health check
app.get('/api/health', (req, res) => {
  res.json({ status: 'ok', message: 'Alfie TV API is running', timestamp: new Date().toISOString() });
});

// Channels endpoint
app.get('/api/channels', (req, res) => {
  const cached = cache.get('channels');
  if (cached) {
    res.json(cached);
    return;
  }

  const dbChannels = database.listChannels();
  const channels = dbChannels.length > 0
    ? dbChannels.map((channel) => ({ ...channel, number: 0, logo: '', quality: '1080p' }))
    : buildSeedChannels();

  const payload = { channels };
  cache.set('channels', payload);
  res.json(payload);
});

app.post('/api/channels', (req, res) => {
  const channels = Array.isArray(req.body?.channels) ? req.body.channels : [];
  store.setChannels(channels);
  database.saveChannels(channels);
  cache.set('channels', { channels });
  res.json({ ok: true, channels });
});

// EPG endpoint
app.get('/api/epg/:channelId', (req, res) => {
  const { channelId } = req.params;
  res.json({ programs: buildSeedPrograms(channelId) });
});

// Auth endpoint
app.post('/api/auth/login', (req, res) => {
  const { username, password } = req.body;
  if (!username || !password) {
    res.status(400).json({ error: 'Username and password are required' });
    return;
  }

  const secret = process.env.JWT_SECRET || 'dev-secret';
  const userId = createUserId('user');
  const token = createToken({ sub: userId, username }, secret);

  res.json({
    token,
    user: {
      id: userId,
      username,
      email: `${username}@alfie-tv.local`,
      theme: 'dark',
      language: 'en',
      createdAt: Date.now(),
      updatedAt: Date.now(),
    },
  });
});

app.get('/api/auth/me', (req, res) => {
  const authHeader = req.headers.authorization;
  const token = authHeader?.startsWith('Bearer ') ? authHeader.slice(7) : undefined;
  const secret = process.env.JWT_SECRET || 'dev-secret';

  if (!token) {
    res.status(401).json({ error: 'Unauthorized' });
    return;
  }

  const payload = verifyToken(token, secret);
  if (!payload) {
    res.status(401).json({ error: 'Unauthorized' });
    return;
  }

  res.json({ user: { id: payload.sub, username: payload.username } });
});

// User profile endpoint
app.post('/api/users', (req, res) => {
  const { username, email } = req.body;
  const profile = profileStore.saveProfile({
    id: createUserId('user'),
    username,
    email,
    theme: 'dark',
    language: 'en',
    createdAt: Date.now(),
    updatedAt: Date.now(),
  });
  res.json(profile);
});

app.get('/api/users/:userId', (req, res) => {
  const profile = profileStore.getProfile(req.params.userId);
  if (!profile) {
    res.status(404).json({ error: 'Profile not found' });
    return;
  }

  res.json(profile);
});

// Error handling
app.use((err: unknown, req: express.Request, res: express.Response, _next: express.NextFunction) => {
  console.error(err);
  const message = err instanceof Error ? err.message : 'Unknown error';
  res.status(500).json({ error: 'Internal Server Error', message });
});

const server = app.listen(PORT, () => {
  console.log(`🚀 Alfie TV API listening on port ${PORT}`);
  console.log(`📡 Health check: http://localhost:${PORT}/api/health`);
});

server.on('error', (error: Error & { code?: string }) => {
  console.error('Failed to start server:', error.message);
  process.exitCode = 1;
});
