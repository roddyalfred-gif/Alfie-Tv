import { Theme } from './types';

export const DARK_THEME: Theme = {
  id: 'dark',
  name: 'Dark',
  isDark: true,
  colors: {
    primary: '#00a8ff',
    secondary: '#7c3aed',
    background: '#0a0e27',
    surface: '#1a1f3a',
    text: '#ffffff',
    textSecondary: '#b0b0b0',
    border: '#2d3748',
    accent: '#00d4ff',
    success: '#10b981',
    warning: '#f59e0b',
    error: '#ef4444',
  },
  spacing: { xs: 4, sm: 8, md: 16, lg: 24, xl: 32 },
  typography: {
    fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
    sizes: { xs: 12, sm: 14, md: 16, lg: 18, xl: 24, '2xl': 32 },
  },
  borderRadius: { sm: 4, md: 8, lg: 12, full: 9999 },
};

export const LIGHT_THEME: Theme = {
  id: 'light',
  name: 'Light',
  isDark: false,
  colors: {
    primary: '#0066cc',
    secondary: '#6d28d9',
    background: '#ffffff',
    surface: '#f3f4f6',
    text: '#111827',
    textSecondary: '#6b7280',
    border: '#e5e7eb',
    accent: '#0084ff',
    success: '#059669',
    warning: '#d97706',
    error: '#dc2626',
  },
  spacing: { xs: 4, sm: 8, md: 16, lg: 24, xl: 32 },
  typography: {
    fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
    sizes: { xs: 12, sm: 14, md: 16, lg: 18, xl: 24, '2xl': 32 },
  },
  borderRadius: { sm: 4, md: 8, lg: 12, full: 9999 },
};
