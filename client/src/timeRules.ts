const MOSCOW = "Europe/Moscow";

export function toMoscowParts(ms: number) {
  const parts = new Intl.DateTimeFormat("ru-RU", {
    timeZone: MOSCOW,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).formatToParts(new Date(ms));
  const get = (type: string) => Number(parts.find((p) => p.type === type)?.value ?? "0");
  return {
    year: get("year"),
    month: get("month"),
    day: get("day"),
    hour: get("hour"),
    minute: get("minute"),
  };
}

export function moscowToMs(year: number, month: number, day: number, hour: number, minute: number): number {
  const utc = Date.UTC(year, month - 1, day, hour, minute, 0, 0);
  const probe = new Date(utc);
  const fmt = new Intl.DateTimeFormat("en-US", {
    timeZone: MOSCOW,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).formatToParts(probe);
  const get = (type: string) => Number(fmt.find((p) => p.type === type)?.value ?? "0");
  const shownHour = get("hour") % 24;
  const diffMinutes = (hour - shownHour) * 60 + (minute - get("minute"));
  return utc - diffMinutes * 60_000;
}

export function minimumRequestedTime(serverNowMs: number, maxCookingMinutes: number): number {
  const now = toMoscowParts(serverNowMs);
  const rawMinutes = now.hour * 60 + now.minute + maxCookingMinutes;
  const rounded = Math.ceil(rawMinutes / 10) * 10;
  const hour = Math.floor(rounded / 60) % 24;
  const minute = rounded % 60;
  return moscowToMs(now.year, now.month, now.day, hour, minute);
}

export function maximumRequestedTime(serverNowMs: number): number {
  const now = toMoscowParts(serverNowMs);
  const maxDay = new Date(Date.UTC(now.year, now.month - 1, now.day + 3));
  return moscowToMs(maxDay.getUTCFullYear(), maxDay.getUTCMonth() + 1, maxDay.getUTCDate(), 23, 50);
}

export function parseTimeToMinutes(value: string): number {
  const [h, m] = value.split(":").map(Number);
  return h * 60 + m;
}

export function isCafeOpen(nowMs: number, workStart: string, cutoff: string): boolean {
  const now = toMoscowParts(nowMs);
  const current = now.hour * 60 + now.minute;
  return current >= parseTimeToMinutes(workStart) && current <= parseTimeToMinutes(cutoff);
}

export function formatMoney(value: number): string {
  return `${value} ₽`;
}

export function formatDateTime(ms: number): string {
  const p = toMoscowParts(ms);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${pad(p.day)}.${pad(p.month)} ${pad(p.hour)}:${pad(p.minute)}`;
}

export function monthNameRu(month: number): string {
  return ["Янв", "Фев", "Мар", "Апр", "Май", "Июн", "Июл", "Авг", "Сен", "Окт", "Ноя", "Дек"][month - 1] ?? "";
}

export function normalizeMenuName(value: string): string {
  return value.trim().replace(/\s+/g, " ").toUpperCase();
}

export function allowedDates(minMs: number, maxMs: number): { year: number; month: number; day: number }[] {
  const dates: { year: number; month: number; day: number }[] = [];
  const min = toMoscowParts(minMs);
  const max = toMoscowParts(maxMs);
  const cursor = new Date(Date.UTC(min.year, min.month - 1, min.day));
  const end = new Date(Date.UTC(max.year, max.month - 1, max.day));
  while (cursor <= end) {
    dates.push({
      year: cursor.getUTCFullYear(),
      month: cursor.getUTCMonth() + 1,
      day: cursor.getUTCDate(),
    });
    cursor.setUTCDate(cursor.getUTCDate() + 1);
  }
  return dates;
}

export function allowedHourRange(
  date: { year: number; month: number; day: number },
  minMs: number,
  maxMs: number,
): number[] {
  const min = toMoscowParts(minMs);
  const max = toMoscowParts(maxMs);
  const minHour = date.year === min.year && date.month === min.month && date.day === min.day ? min.hour : 0;
  const maxHour = date.year === max.year && date.month === max.month && date.day === max.day ? max.hour : 23;
  return Array.from({ length: maxHour - minHour + 1 }, (_, i) => minHour + i);
}

export function allowedMinuteRange(
  date: { year: number; month: number; day: number },
  hour: number,
  minMs: number,
  maxMs: number,
): number[] {
  const min = toMoscowParts(minMs);
  const max = toMoscowParts(maxMs);
  const minMinute =
    date.year === min.year && date.month === min.month && date.day === min.day && hour === min.hour ? min.minute : 0;
  const maxMinute =
    date.year === max.year && date.month === max.month && date.day === max.day && hour === max.hour ? max.minute : 59;
  return Array.from({ length: maxMinute - minMinute + 1 }, (_, i) => minMinute + i);
}
