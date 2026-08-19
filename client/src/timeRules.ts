import type { DayScheduleDto } from "./types";

const MOSCOW = "Europe/Moscow";
const WEEKDAY_TO_ISO: Record<string, number> = {
  Mon: 1,
  Tue: 2,
  Wed: 3,
  Thu: 4,
  Fri: 5,
  Sat: 6,
  Sun: 7,
};

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

export function parseTimeToMinutes(value: string): number {
  const [h, m] = value.split(":").map(Number);
  return h * 60 + m;
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

export function moscowIsoDayOfWeek(year: number, month: number, day: number): number {
  const label = new Intl.DateTimeFormat("en-US", { timeZone: MOSCOW, weekday: "short" }).format(
    new Date(moscowToMs(year, month, day, 12, 0)),
  );
  return WEEKDAY_TO_ISO[label] ?? 1;
}

export function scheduleForDate(
  date: { year: number; month: number; day: number },
  weeklySchedule: DayScheduleDto[],
): DayScheduleDto {
  const dayOfWeek = moscowIsoDayOfWeek(date.year, date.month, date.day);
  const schedule = weeklySchedule.find((entry) => entry.day_of_week === dayOfWeek);
  if (!schedule) {
    throw new Error("Расписание для выбранного дня не найдено");
  }
  return schedule;
}

export function addMoscowDays(year: number, month: number, day: number, delta: number) {
  const next = toMoscowParts(moscowToMs(year, month, day, 12, 0) + delta * 86_400_000);
  return { year: next.year, month: next.month, day: next.day };
}

export function isSameMoscowDate(
  left: { year: number; month: number; day: number },
  right: { year: number; month: number; day: number },
): boolean {
  return left.year === right.year && left.month === right.month && left.day === right.day;
}

export function pickupBoundsForDate(
  date: { year: number; month: number; day: number },
  serverNowMs: number,
  prepMinutes: number,
  weeklySchedule: DayScheduleDto[],
): { minMs: number; maxMs: number; canAcceptOrderToday: boolean } {
  const schedule = scheduleForDate(date, weeklySchedule);
  const openMinutes = parseTimeToMinutes(schedule.open_time) + prepMinutes;
  const maxMinutes = parseTimeToMinutes(schedule.last_order_time) + prepMinutes;
  const today = toMoscowParts(serverNowMs);
  const isToday = isSameMoscowDate(date, today);
  const nowMinutes = today.hour * 60 + today.minute;

  let minMinutes = openMinutes;
  if (isToday) {
    minMinutes = Math.max(openMinutes, nowMinutes + prepMinutes);
  }

  const minMs = moscowToMs(date.year, date.month, date.day, Math.floor(minMinutes / 60), minMinutes % 60);
  const maxMs = moscowToMs(date.year, date.month, date.day, Math.floor(maxMinutes / 60), maxMinutes % 60);
  const canAcceptOrderToday = !isToday || nowMinutes <= parseTimeToMinutes(schedule.last_order_time);

  return { minMs, maxMs, canAcceptOrderToday };
}

export function findEarliestValidSlot(serverNowMs: number, prepMinutes: number, weeklySchedule: DayScheduleDto[]): number {
  const today = toMoscowParts(serverNowMs);
  for (let offset = 0; offset <= 3; offset += 1) {
    const date = addMoscowDays(today.year, today.month, today.day, offset);
    const bounds = pickupBoundsForDate(date, serverNowMs, prepMinutes, weeklySchedule);
    if (offset === 0 && !bounds.canAcceptOrderToday) {
      continue;
    }
    if (bounds.minMs <= bounds.maxMs) {
      return bounds.minMs;
    }
  }
  return moscowToMs(today.year, today.month, today.day, 23, 59);
}

export function allowedDates(
  serverNowMs: number,
  prepMinutes: number,
  weeklySchedule: DayScheduleDto[],
): { year: number; month: number; day: number }[] {
  const today = toMoscowParts(serverNowMs);
  const dates: { year: number; month: number; day: number }[] = [];
  for (let offset = 0; offset <= 3; offset += 1) {
    const date = addMoscowDays(today.year, today.month, today.day, offset);
    const bounds = pickupBoundsForDate(date, serverNowMs, prepMinutes, weeklySchedule);
    if (offset === 0 && !bounds.canAcceptOrderToday) {
      continue;
    }
    if (bounds.minMs <= bounds.maxMs) {
      dates.push(date);
    }
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
  return Array.from({ length: maxHour - minHour + 1 }, (_, index) => minHour + index);
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
  return Array.from({ length: maxMinute - minMinute + 1 }, (_, index) => minMinute + index);
}

export function canSubmitOrderForSelectedDate(
  date: { year: number; month: number; day: number },
  serverNowMs: number,
  weeklySchedule: DayScheduleDto[],
): boolean {
  const today = toMoscowParts(serverNowMs);
  if (!isSameMoscowDate(date, today)) {
    return true;
  }
  const schedule = scheduleForDate(date, weeklySchedule);
  const nowMinutes = today.hour * 60 + today.minute;
  return nowMinutes <= parseTimeToMinutes(schedule.last_order_time);
}

export function clampRequestedTime(
  requestedMs: number,
  serverNowMs: number,
  prepMinutes: number,
  weeklySchedule: DayScheduleDto[],
): number {
  const selected = toMoscowParts(requestedMs);
  const bounds = pickupBoundsForDate(selected, serverNowMs, prepMinutes, weeklySchedule);
  if (requestedMs < bounds.minMs) {
    return bounds.minMs;
  }
  if (requestedMs > bounds.maxMs) {
    return bounds.maxMs;
  }
  return requestedMs;
}

/** @deprecated kept for callers migrating off single-day settings */
export function isCafeOpen(nowMs: number, workStart: string, cutoff: string): boolean {
  const now = toMoscowParts(nowMs);
  const current = now.hour * 60 + now.minute;
  return current >= parseTimeToMinutes(workStart) && current <= parseTimeToMinutes(cutoff);
}
