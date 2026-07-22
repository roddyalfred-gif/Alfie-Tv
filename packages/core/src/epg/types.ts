export interface EPGProgram {
  id: string;
  channelId: string;
  title: string;
  description: string;
  startTime: number;
  endTime: number;
  duration: number;
  genre: string;
  rating?: string;
  image?: string;
}

export interface EPGSchedule {
  channelId: string;
  programs: EPGProgram[];
}

export interface EPGGuide {
  [channelId: string]: EPGProgram[];
}
