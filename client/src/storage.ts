import type { CartItem, ClientProfile } from "./types";

const DEVICE_KEY = "shaurma_device_id";
const PROFILE_KEY = "shaurma_profile";
const CART_KEY = "shaurma_cart";
export const PENDING_ORDER_KEY = "pending_order_public_id";

export interface CartDraft {
  items: CartItem[];
  requestedTime: number | null;
  comment: string;
}

export function getDeviceId(): string {
  let id = localStorage.getItem(DEVICE_KEY);
  if (!id) {
    id = crypto.randomUUID();
    localStorage.setItem(DEVICE_KEY, id);
  }
  return id;
}

export function loadProfile(): ClientProfile | null {
  const raw = localStorage.getItem(PROFILE_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as ClientProfile;
  } catch {
    return null;
  }
}

export function saveProfile(profile: ClientProfile): void {
  localStorage.setItem(PROFILE_KEY, JSON.stringify(profile));
}

function normalizeCartDraft(raw: unknown): CartDraft {
  if (Array.isArray(raw)) {
    return { items: raw as CartItem[], requestedTime: null, comment: "" };
  }
  if (raw && typeof raw === "object") {
    const value = raw as Partial<CartDraft>;
    return {
      items: Array.isArray(value.items) ? value.items : [],
      requestedTime: typeof value.requestedTime === "number" ? value.requestedTime : null,
      comment: typeof value.comment === "string" ? value.comment : "",
    };
  }
  return { items: [], requestedTime: null, comment: "" };
}

export function loadCartDraft(): CartDraft {
  const raw = localStorage.getItem(CART_KEY);
  if (!raw) return { items: [], requestedTime: null, comment: "" };
  try {
    return normalizeCartDraft(JSON.parse(raw));
  } catch {
    return { items: [], requestedTime: null, comment: "" };
  }
}

export function saveCartDraft(draft: CartDraft): void {
  localStorage.setItem(CART_KEY, JSON.stringify(draft));
}

export function loadCart(): CartItem[] {
  return loadCartDraft().items;
}

export function saveCart(items: CartItem[]): void {
  const draft = loadCartDraft();
  saveCartDraft({ ...draft, items });
}

export function clearCartDraft(): void {
  localStorage.removeItem(CART_KEY);
}

export function clearCart(): void {
  clearCartDraft();
}

export function getPendingOrderId(): string | null {
  return localStorage.getItem(PENDING_ORDER_KEY);
}

export function setPendingOrderId(publicId: string): void {
  localStorage.setItem(PENDING_ORDER_KEY, publicId);
}

export function clearPendingOrderId(): void {
  localStorage.removeItem(PENDING_ORDER_KEY);
}
