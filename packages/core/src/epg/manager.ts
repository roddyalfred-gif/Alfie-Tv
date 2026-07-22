import { EPGGuide, EPGProgram, EPGSchedule } from './types';

export class EPGManager {
  private guide: EPGGuide = {};

  addSchedule(schedule: EPGSchedule): void {
    this.guide[schedule.channelId] = schedule.programs;
  }

  getSchedule(channelId: string): EPGProgram[] {
    return this.guide[channelId] || [];
  }

  getCurrentProgram(channelId: string): EPGProgram | undefined {
    const now = Date.now();
    const schedule = this.getSchedule(channelId);
    return schedule.find((program) => program.startTime <= now && now < program.endTime);
  }

  getNextProgram(channelId: string): EPGProgram | undefined {
    const now = Date.now();
    const schedule = this.getSchedule(channelId);
    return schedule.find((program) => program.startTime > now);
  }

  getProgramsInRange(channelId: string, startTime: number, endTime: number): EPGProgram[] {
    const schedule = this.getSchedule(channelId);
    return schedule.filter(
      (program) => program.startTime >= startTime && program.endTime <= endTime
    );
  }

  searchPrograms(query: string): EPGProgram[] {
    const lowerQuery = query.toLowerCase();
    const results: EPGProgram[] = [];

    Object.values(this.guide).forEach((programs) => {
      programs.forEach((program) => {
        if (
          program.title.toLowerCase().includes(lowerQuery) ||
          program.description.toLowerCase().includes(lowerQuery)
        ) {
          results.push(program);
        }
      });
    });

    return results;
  }
}

export * from './types';
