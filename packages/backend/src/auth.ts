import crypto from 'crypto';

export interface JwtPayload {
  sub: string;
  iat?: number;
  exp?: number;
  [key: string]: unknown;
}

const TOKEN_TTL_SECONDS = 24 * 60 * 60;

export function createToken(payload: JwtPayload, secret: string): string {
  const now = Math.floor(Date.now() / 1000);
  const claims: JwtPayload = {
    ...payload,
    iat: payload.iat ?? now,
    exp: payload.exp ?? now + TOKEN_TTL_SECONDS,
  };
  const header = Buffer.from(JSON.stringify({ alg: 'HS256', typ: 'JWT' })).toString('base64url');
  const body = Buffer.from(JSON.stringify(claims)).toString('base64url');
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
  const actualBuffer = Buffer.from(signature);
  const expectedBuffer = Buffer.from(expected);

  if (actualBuffer.length !== expectedBuffer.length || !crypto.timingSafeEqual(actualBuffer, expectedBuffer)) {
    return null;
  }

  try {
    const payload = JSON.parse(Buffer.from(body, 'base64url').toString('utf8')) as JwtPayload;
    if (!payload || typeof payload.sub !== 'string' || !payload.sub) {
      return null;
    }
    if (typeof payload.exp === 'number' && payload.exp <= Math.floor(Date.now() / 1000)) {
      return null;
    }
    return payload;
  } catch {
    return null;
  }
}
