export interface InitResponse {
  client_number: number;
  name: string | null;
  phone: string | null;
  is_blocked: boolean;
  server_time: number;
}

export interface PublicConfigResponse {
  server_time: number;
  work_start_time: string;
  cutoff_time: string;
  is_open: boolean;
  payment_enabled: boolean;
}

export interface MenuResponse {
  server_time: number;
  categories: CategoryDto[];
}

export interface CategoryDto {
  id: number;
  name: string;
  sort_order: number;
  is_active: boolean;
  is_grill: boolean;
  default_cooking_minutes: number;
  items: MenuItemDto[];
}

export interface MenuItemDto {
  id: number;
  category_id: number;
  name: string;
  description: string | null;
  price: number;
  weight: number;
  cooking_time: number;
  image_url: string | null;
  sort_order: number;
  is_active: boolean;
  additions: AdditionDto[];
  removals: RemovalDto[];
}

export interface AdditionDto {
  id: number;
  menu_item_id: number;
  name: string;
  price: number;
  weight: number;
  is_active: boolean;
}

export interface RemovalDto {
  id: number;
  menu_item_id: number;
  name: string;
  is_active: boolean;
}

export interface CreateOrderRequest {
  requested_time: number;
  general_comment?: string | null;
  items: CreateOrderItemRequest[];
}

export interface CreateOrderItemRequest {
  menu_item_id: number;
  additions_ids: number[];
  removals_ids: number[];
}

export interface CreateOrderResponse {
  public_id: string;
  status: string;
  updated_at: number;
  payment_status: string;
  payment_url?: string | null;
}

export interface PaymentStatusResponse {
  public_id: string;
  payment_status: string;
  status: string;
}

export interface OrdersResponse {
  orders: OrderDto[];
}

export interface OrderDto {
  id: number;
  public_id: string;
  status: string;
  payment_status?: string;
  created_at: number;
  updated_at: number;
  requested_time: number;
  cooking_start_time: number;
  total_price: number;
  general_comment: string | null;
  items: OrderItemDto[];
}

export interface OrderItemDto {
  id: number;
  menu_item_id: number;
  name_snapshot: string;
  price_snapshot: number;
  weight_snapshot: number;
  additions_snapshot: { id: number; name: string; price: number; weight: number }[];
  removals_snapshot: { id: number; name: string }[];
}

export interface CartItem {
  id: string;
  menuItemId: number;
  name: string;
  price: number;
  weight: number;
  cookingTime: number;
  additionNames: string;
  removalNames: string;
  additionsIds: number[];
  removalsIds: number[];
  totalPrice: number;
}

export interface ClientProfile {
  deviceId: string;
  clientNumber: number | null;
  name: string | null;
  phone: string | null;
  isBlocked: boolean;
  serverTimeOffsetMs: number;
}
