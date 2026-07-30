export interface PlaybackState {
  channelId: string | null;
  isPlaying: boolean;
  positionSeconds: number;
}

export function createPlaybackState(channelId: string | null = null): PlaybackState {
  return {
    channelId,
    isPlaying: false,
    positionSeconds: 0,
  };
}

export function updatePlaybackState(
  state: PlaybackState,
  updates: Partial<PlaybackState>
): PlaybackState {
  const nextPosition = typeof updates.positionSeconds === 'number' && Number.isFinite(updates.positionSeconds)
    ? Math.max(0, Math.floor(updates.positionSeconds))
    : state.positionSeconds;

  return {
    ...state,
    ...updates,
    isPlaying: Boolean(updates.isPlaying ?? state.isPlaying),
    positionSeconds: nextPosition,
  };
}
