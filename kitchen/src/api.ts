import type {
  AdditionDto,
  AdditionUpsert,
  CategoryDto,
  CategoryUpsert,
  ClientDto,
  KitchenOrderDto,
  MenuItemDto,
  MenuItemUpsert,
  OrderDto,
  OrderStatus,
  RemovalDto,
  RemovalUpsert,
  SettingsResponse,
  StatisticsResponse,
} from "./types";

const API_URL = (import.meta.env.VITE_API_URL || "http://localhost:8080").replace(/\/$/, "");
const BEARER_TOKEN = import.meta.env.VITE_BEARER_TOKEN || "";

type QueryValue = string | number | boolean | null | undefined;

function queryString(params: Record<string, QueryValue>): string {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") {
      search.set(key, String(value));
    }
  });
  const text = search.toString();
  return text ? `?${text}` : "";
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  headers.set("Accept", "application/json");
  if (options.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  if (BEARER_TOKEN) {
    headers.set("Authorization", `Bearer ${BEARER_TOKEN}`);
  }

  const response = await fetch(`${API_URL}${path}`, { ...options, headers });
  if (!response.ok) {
    let message = `Ошибка API ${response.status}`;
    try {
      const data = (await response.json()) as { message?: string; error?: string };
      message = data.message || data.error || message;
    } catch {
      const text = await response.text().catch(() => "");
      if (text) message = text;
    }
    throw new Error(message);
  }
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

const jsonBody = (body: unknown): RequestInit => ({
  body: JSON.stringify(body),
});

export const api = {
  getOrders: (since_updated_at: number, since_id: number) =>
    request<{ orders: KitchenOrderDto[] }>(`/api/orders${queryString({ since_updated_at, since_id })}`),

  updateOrderStatus: (public_id: string, status: OrderStatus) =>
    request<OrderDto>(`/api/order/${encodeURIComponent(public_id)}/status`, {
      method: "POST",
      ...jsonBody({ status }),
    }),

  getStatistics: (from?: string, to?: string) =>
    request<StatisticsResponse>(`/api/statistics${queryString({ from, to })}`),

  getCategories: () => request<CategoryDto[]>("/api/admin/categories"),
  createCategory: (body: CategoryUpsert) =>
    request<CategoryDto>("/api/admin/categories", { method: "POST", ...jsonBody(body) }),
  updateCategory: (id: number, body: CategoryUpsert) =>
    request<CategoryDto>(`/api/admin/categories/${id}`, { method: "PUT", ...jsonBody(body) }),
  deleteCategory: (id: number) =>
    request<{ ok: boolean }>(`/api/admin/categories/${id}`, { method: "DELETE" }),

  getMenuItems: () => request<MenuItemDto[]>("/api/admin/menu_items"),
  createMenuItem: (body: MenuItemUpsert) =>
    request<MenuItemDto>("/api/admin/menu_items", { method: "POST", ...jsonBody(body) }),
  updateMenuItem: (id: number, body: MenuItemUpsert) =>
    request<MenuItemDto>(`/api/admin/menu_items/${id}`, { method: "PUT", ...jsonBody(body) }),
  deleteMenuItem: (id: number) =>
    request<{ ok: boolean }>(`/api/admin/menu_items/${id}`, { method: "DELETE" }),

  getAdditions: () => request<AdditionDto[]>("/api/admin/additions"),
  createAddition: (body: AdditionUpsert) =>
    request<AdditionDto>("/api/admin/additions", { method: "POST", ...jsonBody(body) }),
  updateAddition: (id: number, body: AdditionUpsert) =>
    request<AdditionDto>(`/api/admin/additions/${id}`, { method: "PUT", ...jsonBody(body) }),
  deleteAddition: (id: number) =>
    request<{ ok: boolean }>(`/api/admin/additions/${id}`, { method: "DELETE" }),

  getRemovals: () => request<RemovalDto[]>("/api/admin/removals"),
  createRemoval: (body: RemovalUpsert) =>
    request<RemovalDto>("/api/admin/removals", { method: "POST", ...jsonBody(body) }),
  updateRemoval: (id: number, body: RemovalUpsert) =>
    request<RemovalDto>(`/api/admin/removals/${id}`, { method: "PUT", ...jsonBody(body) }),
  deleteRemoval: (id: number) =>
    request<{ ok: boolean }>(`/api/admin/removals/${id}`, { method: "DELETE" }),

  getClients: (search: string) =>
    request<{ clients: ClientDto[] }>(`/api/clients${queryString({ search })}`),
  updateClientStatus: (device_id: string, is_blocked: boolean) =>
    request<ClientDto>(`/api/clients/${encodeURIComponent(device_id)}/status`, {
      method: "PUT",
      ...jsonBody({ is_blocked }),
    }),
  getClientOrders: (client_number: number) =>
    request<{ orders: OrderDto[] }>(`/api/clients/${client_number}/orders`),

  getSettings: () => request<SettingsResponse>("/api/admin/settings"),
  updateSettings: (body: Record<string, unknown>) =>
    request<SettingsResponse>("/api/admin/settings", { method: "PUT", ...jsonBody(body) }),
};
