import test from 'node:test';
import assert from 'node:assert/strict';
import { EPGManager } from '../manager.ts';

test('EPGManager exposes current and next programs', () => {
  const manager = new EPGManager();
  const now = Date.now();
  manager.addSchedule({
    channelId: 'ch-1',
    programs: [
      { id: 'p1', channelId: 'ch-1', title: 'Past', description: '', startTime: now - 3600000, endTime: now - 600000, duration: 1800000, genre: 'News' },
      { id: 'p2', channelId: 'ch-1', title: 'Current', description: '', startTime: now - 600000, endTime: now + 1800000, duration: 2400000, genre: 'News' },
      { id: 'p3', channelId: 'ch-1', title: 'Next', description: '', startTime: now + 1800000, endTime: now + 3600000, duration: 1800000, genre: 'News' },
    ],
  });

  assert.equal(manager.getCurrentProgram('ch-1')?.title, 'Current');
  assert.equal(manager.getNextProgram('ch-1')?.title, 'Next');
});
