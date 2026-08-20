import type {
  CreateOrderRequest,
  InitResponse,
  MenuResponse,
  OrdersResponse,
  PaymentStatusResponse,
  PendingOrderResponse,
  PublicConfigResponse,
  CreateOrderResponse,
  OrderDto,
} from "./types";
import { getDeviceId } from "./storage";

const API_URL = "";

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  headers.set("Accept", "application/json");
  if (options.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  const deviceId = getDeviceId();
  if (deviceId && path !== "/api/init" && path !== "/api/menu" && path !== "/api/config") {
    headers.set("X-Device-Id", deviceId);
  }
  const response = await fetch(`${API_URL}${path}`, { ...options, headers });
  if (!response.ok) {
    let message = `Ошибка ${response.status}`;
    try {
      const data = (await response.json()) as { message?: string; error?: string };
      message = data.message || data.error || message;
    } catch {
      /* ignore */
    }
    throw new Error(message);
  }
  return (await response.json()) as T;
}

export const api = {
  init: (deviceId: string) =>
    request<InitResponse>("/api/init", {
      method: "POST",
      body: JSON.stringify({ device_id: deviceId, platform: "web" }),
    }),

  config: () => request<PublicConfigResponse>("/api/config"),

  menu: () => request<MenuResponse>("/api/menu"),

  updateProfile: (body: { name?: string | null; phone?: string | null }) =>
    request<InitResponse>("/api/profile", { method: "POST", body: JSON.stringify(body) }),

  createOrder: (body: CreateOrderRequest) =>
    request<CreateOrderResponse>("/api/order", { method: "POST", body: JSON.stringify(body) }),

  getPendingOrder: () => request<PendingOrderResponse>("/api/order/pending"),

  getPaymentStatus: (publicId: string) =>
    request<PaymentStatusResponse>(`/api/order/${encodeURIComponent(publicId)}/payment`),

  retryPayment: (publicId: string) =>
    request<CreateOrderResponse>(`/api/order/${encodeURIComponent(publicId)}/pay`, { method: "POST" }),

  cancelOrder: (publicId: string) =>
    request<OrderDto>(`/api/order/${encodeURIComponent(publicId)}/cancel`, { method: "POST" }),

  myOrders: (sinceUpdatedAt = 0, sinceId = 0) =>
    request<OrdersResponse>(`/api/orders/my?since_updated_at=${sinceUpdatedAt}&since_id=${sinceId}`),
};
