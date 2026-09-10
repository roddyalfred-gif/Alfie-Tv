import express from 'express';
import cors from 'cors';
import dotenv from 'dotenv';
import { randomUUID } from 'node:crypto';
import { createToken, verifyToken } from './auth';
import { FileStore } from './store';
import { InMemoryCache } from './cache';
import { JsonDatabase } from './database';
import { ProfileStore } from './profile-store';

dotenv.config();

const app = express();
const PORT = Number(process.env.PORT) || 3000;
const startedAt = Date.now();
const isProduction = process.env.NODE_ENV === 'production';
const jwtSecret = process.env.JWT_SECRET?.trim() || (isProduction ? '' : 'dev-secret');

if (isProduction && !jwtSecret) {
  throw new Error('JWT_SECRET is required in production');
}

const store = new FileStore(process.env.DATA_FILE || './data/channels.json');
const database = new JsonDatabase(process.env.DATABASE_FILE || './data/database.json');
const profileStore = new ProfileStore(process.env.PROFILE_FILE || './data/profiles.json');
const cache = new InMemoryCache<{ channels: Array<{ id: string; name: string; category: string; streamUrl: string; isFavorite?: boolean }> }>(60000);

function createUserId(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`;
}

export function buildSeedChannels() {
  return [
    { id: 'ch-1', name: 'Alfie News', number: 1, logo: '', streamUrl: 'https://example.com/stream1.m3u8', category: 'News', isFavorite: false, quality: '1080p' },
    { id: 'ch-2', name: 'Alfie Sports', number: 2, logo: '', streamUrl: 'https://example.com/stream2.m3u8', category: 'Sports', isFavorite: false, quality: '4K' },
    { id: 'ch-3', name: 'Alfie Movies', number: 3, logo: '', streamUrl: 'https://example.com/stream3.m3u8', category: 'Movies', isFavorite: false, quality: '1080p' },
  ];
}

export function buildSeedPrograms(channelId: string) {
  const now = Date.now();

  return [
    { id: 'prog-1', channelId, title: 'Live Headlines', description: 'Current stories from around the globe.', startTime: now - 3600000, endTime: now, duration: 3600000, genre: 'News' },
    { id: 'prog-2', channelId, title: 'Next Up', description: 'A preview of what is coming next on the channel.', startTime: now, endTime: now + 3600000, duration: 3600000, genre: 'News' },
  ];
}

// Middleware
app.disable('x-powered-by');
app.use(express.json({ limit: process.env.JSON_BODY_LIMIT || '1mb' }));
app.use(express.urlencoded({ extended: true, limit: process.env.JSON_BODY_LIMIT || '1mb' }));

const configuredOrigins = (process.env.CORS_ORIGINS || '*')
  .split(',')
  .map((origin) => origin.trim())
  .filter(Boolean);

app.use(cors({
  origin: configuredOrigins.includes('*') ? true : configuredOrigins,
  credentials: !configuredOrigins.includes('*'),
}));

app.use((req, res, next) => {
  const requestId = req.header('x-request-id')?.trim() || randomUUID();
  res.setHeader('x-request-id', requestId);
  next();
});

// Health check: intentionally unauthenticated for load balancers and uptime monitors.
app.get('/api/health', (_req, res) => {
  res.status(200).json({
    status: 'ok',
    service: 'alfie-tv-backend',
    uptimeSeconds: Math.floor((Date.now() - startedAt) / 1000),
    timestamp: new Date().toISOString(),
  });
});

// Readiness check for deployments. Storage initialization is performed at startup,
// so a successful response means the API process is ready to accept traffic.
app.get('/api/ready', (_req, res) => {
  res.status(200).json({
    status: 'ready',
    service: 'alfie-tv-backend',
    timestamp: new Date().toISOString(),
  });
});

// Versioned health aliases make deployment probes easier to standardize.
app.get('/api/v1/health', (_req, res) => {
  res.redirect(307, '/api/health');
});

// Channels endpoint
app.get('/api/channels', (_req, res) => {
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
  const username = typeof req.body?.username === 'string' ? req.body.username.trim() : '';
  const password = typeof req.body?.password === 'string' ? req.body.password : '';
  if (!username || !password) {
    res.status(400).json({ error: 'Username and password are required' });
    return;
  }

  const userId = createUserId('user');
  const token = createToken({ sub: userId, username }, jwtSecret);
  const now = Date.now();

  res.json({
    token,
    user: {
      id: userId,
      username,
      email: `${username}@alfie-tv.local`,
      theme: 'dark',
      language: 'en',
      createdAt: now,
      updatedAt: now,
    },
  });
});

app.get('/api/auth/me', (req, res) => {
  const authHeader = req.headers.authorization;
  const token = authHeader?.startsWith('Bearer ') ? authHeader.slice(7).trim() : undefined;

  if (!token) {
    res.status(401).json({ error: 'Unauthorized' });
    return;
  }

  const payload = verifyToken(token, jwtSecret);
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

// Consistent JSON response for unknown API routes.
app.use('/api', (_req, res) => {
  res.status(404).json({ error: 'Not Found' });
});

// Error handling
app.use((err: unknown, _req: express.Request, res: express.Response, _next: express.NextFunction) => {
  console.error(err);
  res.status(500).json({
    error: 'Internal Server Error',
    ...(isProduction ? {} : { message: err instanceof Error ? err.message : 'Unknown error' }),
  });
});

const server = app.listen(PORT, () => {
  console.log(`🚀 Alfie TV API listening on port ${PORT}`);
  console.log(`📡 Health check: http://localhost:${PORT}/api/health`);
});

function shutdown(signal: string) {
  console.log(`Received ${signal}; shutting down Alfie TV API`);
  server.close((error) => {
    if (error) {
      console.error('Failed to close server cleanly:', error);
      process.exitCode = 1;
      return;
    }
    process.exit(0);
  });
}

process.once('SIGINT', () => shutdown('SIGINT'));
process.once('SIGTERM', () => shutdown('SIGTERM'));

server.on('error', (error: Error & { code?: string }) => {
  console.error('Failed to start server:', error.message);
  process.exitCode = 1;
});
