import { describe, expect, it } from 'vitest';
import { act } from 'react';
import ReactDOM from 'react-dom/client';
import App from '../App';

describe('App', () => {
  it('renders the initial shell for the web experience', async () => {
    const container = document.createElement('div');
    document.body.appendChild(container);

    const root = ReactDOM.createRoot(container);
    await act(async () => {
      root.render(<App />);
    });

    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 3200));
    });

    expect(container.textContent).toContain('Channels');
    expect(container.textContent).toContain('VPN integration');
  });
});
