export interface AuthResponse {
  token: string;
  user: {
    id: string;
    username: string;
    email: string;
    theme: string;
    language: string;
  };
}

export async function loginUser(username: string, password: string): Promise<AuthResponse> {
  const response = await fetch('http://localhost:3000/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });

  if (!response.ok) {
    throw new Error('Unable to sign in');
  }

  return response.json();
}
