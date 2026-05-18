import type { OrderStatus } from "./types";

export const MSK_TIME_ZONE = "Europe/Moscow";
export const STATUS_CHAIN: OrderStatus[] = ["NEW", "CONFIRMED", "COOKING", "READY", "COMPLETED"];

export const STATUS_LABEL: Record<OrderStatus, string> = {
  NEW: "Новый",
  CONFIRMED: "Подтверждён",
  COOKING: "Готовится",
  READY: "Готов",
  COMPLETED: "Завершён",
};

export const NEXT_STATUS_LABEL: Record<OrderStatus, string> = {
  NEW: "Подтвердить",
  CONFIRMED: "Начать готовить",
  COOKING: "Готов",
  READY: "Завершить",
  COMPLETED: "Завершён",
};

export function nextStatus(status: OrderStatus): OrderStatus | null {
  const index = STATUS_CHAIN.indexOf(status);
  return index >= 0 && index < STATUS_CHAIN.length - 1 ? STATUS_CHAIN[index + 1] : null;
}

export function formatMoney(value: number): string {
  return `${value.toLocaleString("ru-RU")} ₽`;
}

export function formatDateTime(ms: number): string {
  return new Intl.DateTimeFormat("ru-RU", {
    timeZone: MSK_TIME_ZONE,
    day: "2-digit",
    month: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(ms));
}

export function formatTime(ms: number): string {
  return new Intl.DateTimeFormat("ru-RU", {
    timeZone: MSK_TIME_ZONE,
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(ms));
}

export function moscowDateKey(ms: number): string {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: MSK_TIME_ZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(new Date(ms));
  const get = (type: string) => parts.find((part) => part.type === type)?.value ?? "";
  return `${get("year")}-${get("month")}-${get("day")}`;
}

export function moscowTodayKey(offsetDays = 0): string {
  const now = new Date();
  const moscowNow = new Date(now.toLocaleString("en-US", { timeZone: MSK_TIME_ZONE }));
  moscowNow.setDate(moscowNow.getDate() + offsetDays);
  const year = moscowNow.getFullYear();
  const month = String(moscowNow.getMonth() + 1).padStart(2, "0");
  const day = String(moscowNow.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

export function dayLabel(offset: number): string {
  if (offset === 0) return "Сегодня";
  if (offset === 1) return "Завтра";
  return `+${offset} дня`;
}

export function toInputDate(key: string): string {
  return key;
}

export function parseNumber(value: FormDataEntryValue | null, fallback = 0): number {
  const parsed = Number(value ?? fallback);
  return Number.isFinite(parsed) ? parsed : fallback;
}

export function nullableText(value: FormDataEntryValue | null): string | null {
  const text = String(value ?? "").trim();
  return text ? text : null;
}
