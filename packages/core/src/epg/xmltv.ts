export interface XMLTVProgram {
  id: string;
  channelId: string;
  title: string;
  description: string;
  startTime: number;
  endTime: number;
  duration: number;
  genre: string;
}

export function parseXMLTVGuide(xml: string): Record<string, XMLTVProgram[]> {
  const guide: Record<string, XMLTVProgram[]> = {};
  const programmeRegex = /<programme[^>]*channel="([^"]+)"[^>]*start="([^"]+)"[^>]*stop="([^"]+)"[^>]*>([\s\S]*?)<\/programme>/g;

  let match: RegExpExecArray | null;
  while ((match = programmeRegex.exec(xml)) !== null) {
    const [, channelId, startRaw, stopRaw, body] = match;
    const titleMatch = body.match(/<title[^>]*>([\s\S]*?)<\/title>/i);
    const descMatch = body.match(/<desc[^>]*>([\s\S]*?)<\/desc>/i);

    const startTime = Date.parse(startRaw.replace(/(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})/, '$1-$2-$3T$4:$5:$6Z'));
    const endTime = Date.parse(stopRaw.replace(/(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})/, '$1-$2-$3T$4:$5:$6Z'));

    guide[channelId] = guide[channelId] || [];
    guide[channelId].push({
      id: `${channelId}-${guide[channelId].length + 1}`,
      channelId,
      title: titleMatch?.[1].replace(/<[^>]+>/g, '').trim() || 'Untitled Program',
      description: descMatch?.[1].replace(/<[^>]+>/g, '').trim() || '',
      startTime,
      endTime,
      duration: Math.max(endTime - startTime, 0),
      genre: 'General',
    });
  }

  return guide;
}
