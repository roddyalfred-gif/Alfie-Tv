import express from 'express';
import cors from 'cors';
import dotenv from 'dotenv';

dotenv.config();

const app = express();
const PORT = process.env.PORT || 3000;

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
  const mockChannels = [
    {
      id: 'ch-1',
      name: 'Channel 1',
      number: 1,
      logo: 'https://via.placeholder.com/150?text=CH1',
      streamUrl: 'https://example.com/stream1.m3u8',
      category: 'General',
      isFavorite: false,
      quality: '1080p',
    },
    {
      id: 'ch-2',
      name: 'Channel 2',
      number: 2,
      logo: 'https://via.placeholder.com/150?text=CH2',
      streamUrl: 'https://example.com/stream2.m3u8',
      category: 'Sports',
      isFavorite: false,
      quality: '4K',
    },
    {
      id: 'ch-3',
      name: 'Channel 3',
      number: 3,
      logo: 'https://via.placeholder.com/150?text=CH3',
      streamUrl: 'https://example.com/stream3.m3u8',
      category: 'News',
      isFavorite: false,
      quality: '1080p',
    },
  ];
  res.json({ channels: mockChannels });
});

// EPG endpoint
app.get('/api/epg/:channelId', (req, res) => {
  const { channelId } = req.params;
  const now = Date.now();
  const mockPrograms = [
    {
      id: 'prog-1',
      channelId,
      title: 'Example Program 1',
      description: 'This is an example program',
      startTime: now - 3600000,
      endTime: now,
      duration: 3600000,
      genre: 'General',
    },
    {
      id: 'prog-2',
      channelId,
      title: 'Example Program 2',
      description: 'This is another example program',
      startTime: now,
      endTime: now + 3600000,
      duration: 3600000,
      genre: 'General',
    },
  ];
  res.json({ programs: mockPrograms });
});

// User profile endpoint
app.post('/api/users', (req, res) => {
  const { username, email } = req.body;
  res.json({
    id: 'user-' + Date.now(),
    username,
    email,
    theme: 'dark',
    language: 'en',
    createdAt: Date.now(),
    updatedAt: Date.now(),
  });
});

// Error handling
app.use((err: any, req: express.Request, res: express.Response, next: express.NextFunction) => {
  console.error(err);
  res.status(500).json({ error: 'Internal Server Error', message: err.message });
});

app.listen(PORT, () => {
  console.log(`🚀 Alfie TV API listening on port ${PORT}`);
  console.log(`📡 Health check: http://localhost:${PORT}/api/health`);
});
