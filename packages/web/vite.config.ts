import { defineConfig, type PluginOption } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';

export default defineConfig({
  plugins: [react() as unknown as PluginOption],
  resolve: {
    alias: {
      '@alfie-tv/core': path.resolve(__dirname, '../core/src'),
      '@alfie-tv/ui': path.resolve(__dirname, '../ui/src'),
      '@': path.resolve(__dirname, './src'),
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
