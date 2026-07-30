import crypto from 'crypto';

export interface JwtPayload {
  sub: string;
  [key: string]: unknown;
}

export function createToken(payload: JwtPayload, secret: string): string {
  const header = Buffer.from(JSON.stringify({ alg: 'HS256', typ: 'JWT' })).toString('base64url');
  const body = Buffer.from(JSON.stringify(payload)).toString('base64url');
  const signature = crypto.createHmac('sha256', secret).update(`${header}.${body}`).digest('base64url');
  return `${header}.${body}.${signature}`;
}

export function verifyToken(token: string, secret: string): JwtPayload | null {
  const parts = token.split('.');
  if (parts.length !== 3) {
    return null;
  }

  const [header, body, signature] = parts;
  const expected = crypto.createHmac('sha256', secret).update(`${header}.${body}`).digest('base64url');

  if (crypto.timingSafeEqual(Buffer.from(signature), Buffer.from(expected))) {
    try {
      return JSON.parse(Buffer.from(body, 'base64url').toString('utf8')) as JwtPayload;
    } catch {
      return null;
    }
  }

  return null;
}
