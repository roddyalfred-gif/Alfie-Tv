import { defineConfig, type PluginOption } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react() as unknown as PluginOption],
  resolve: {
    alias: {
      '@alfie-tv/core': new URL('../core/src', import.meta.url).pathname,
      '@alfie-tv/ui': new URL('../ui/src', import.meta.url).pathname,
      '@': new URL('./src', import.meta.url).pathname,
    },
  },
  server: {
    port: 5173,
    strictPort: false,
  },
  build: {
    outDir: 'dist',
    sourcemap: true,
  },
});
