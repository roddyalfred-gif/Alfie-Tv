import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  resolve: {
    alias: {
      '@alfie-tv/core': fileURLToPath(new URL('./packages/core/src/index.ts', import.meta.url)),
      '@alfie-tv/ui': fileURLToPath(new URL('./packages/ui/src/index.ts', import.meta.url)),
    },
  },
  test: {
    environment: 'jsdom',
    include: ['packages/**/src/**/*.{test,spec}.{ts,tsx,js,jsx}'],
    exclude: ['**/dist/**', '**/node_modules/**', '**/coverage/**'],
    setupFiles: ['./packages/web/src/__tests__/setup.ts'],
  },
});
