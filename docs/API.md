# API Documentation

## Base URL

```
http://localhost:3000/api
```

## Authentication

Include JWT token in Authorization header:

```
Authorization: Bearer <token>
```

## Endpoints

### Health Check

```http
GET /health
```

**Response:**
```json
{
  "status": "ok",
  "message": "Alfie TV API is running",
  "timestamp": "2024-01-01T12:00:00Z"
}
```

### Get Channels

```http
GET /channels
```

**Response:**
```json
{
  "channels": [
    {
      "id": "ch-1",
      "name": "Channel 1",
      "number": 1,
      "logo": "https://example.com/logo.png",
      "streamUrl": "https://example.com/stream.m3u8",
      "category": "General",
      "isFavorite": false,
      "quality": "1080p"
    }
  ]
}
```

### Get EPG for Channel

```http
GET /epg/:channelId
```

**Response:**
```json
{
  "programs": [
    {
      "id": "prog-1",
      "channelId": "ch-1",
      "title": "Program Title",
      "description": "Program description",
      "startTime": 1704110400000,
      "endTime": 1704114000000,
      "duration": 3600000,
      "genre": "General"
    }
  ]
}
```

### Create User

```http
POST /users
Content-Type: application/json

{
  "username": "john_doe",
  "email": "john@example.com"
}
```

**Response:**
```json
{
  "id": "user-1704110400000",
  "username": "john_doe",
  "email": "john@example.com",
  "theme": "dark",
  "language": "en",
  "createdAt": 1704110400000,
  "updatedAt": 1704110400000
}
```

## Error Responses

```json
{
  "error": "Error message",
  "message": "Detailed error message"
}
```

## Rate Limiting

- 100 requests per minute per IP
- 1000 requests per hour per IP

## Status Codes

- `200` - OK
- `201` - Created
- `400` - Bad Request
- `401` - Unauthorized
- `403` - Forbidden
- `404` - Not Found
- `500` - Internal Server Error
