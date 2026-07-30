export type AppScreen = 'home' | 'channels' | 'player' | 'settings';

export interface NavigationState {
  currentScreen: AppScreen;
  canGoBack: boolean;
}

export function createNavigationState(initialScreen: AppScreen = 'home'): NavigationState {
  return {
    currentScreen: initialScreen,
    canGoBack: false,
  };
}

export function navigateTo(state: NavigationState, screen: AppScreen): NavigationState {
  return {
    ...state,
    currentScreen: screen,
    canGoBack: state.currentScreen !== screen,
  };
}
