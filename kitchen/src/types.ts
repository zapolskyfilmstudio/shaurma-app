export type OrderStatus = "NEW" | "CONFIRMED" | "COOKING" | "READY" | "COMPLETED";

export type ClientDto = {
  device_id: string;
  client_number: number;
  name?: string | null;
  phone?: string | null;
  is_blocked: boolean;
  created_at: number;
  updated_at?: number;
};

export type AdditionSnapshot = {
  id: number;
  name: string;
  price: number;
  weight: number;
};

export type RemovalSnapshot = {
  id: number;
  name: string;
};

export type OrderItemDto = {
  id: number;
  menu_item_id: number;
  name_snapshot: string;
  price_snapshot: number;
  weight_snapshot: number;
  additions_snapshot: AdditionSnapshot[];
  removals_snapshot: RemovalSnapshot[];
};

export type OrderDto = {
  id: number;
  public_id: string;
  status: OrderStatus;
  created_at: number;
  updated_at: number;
  requested_time: number;
  cooking_start_time: number;
  total_price: number;
  general_comment?: string | null;
  items: OrderItemDto[];
};

export type KitchenOrderDto = OrderDto & {
  client: ClientDto;
};

export type CategoryDto = {
  id: number;
  name: string;
  sort_order: number;
  is_active: boolean;
  is_grill: boolean;
  items: MenuItemDto[];
};

export type MenuItemDto = {
  id: number;
  category_id: number;
  name: string;
  description?: string | null;
  price: number;
  weight: number;
  cooking_time: number;
  image_url?: string | null;
  sort_order: number;
  is_active: boolean;
  additions: AdditionDto[];
  removals: RemovalDto[];
};

export type AdditionDto = {
  id: number;
  menu_item_id: number;
  name: string;
  price: number;
  weight: number;
  is_active: boolean;
};

export type RemovalDto = {
  id: number;
  menu_item_id: number;
  name: string;
  is_active: boolean;
};

export type CategoryUpsert = {
  name: string;
  sort_order: number;
  is_active: boolean;
  is_grill: boolean;
};

export type MenuItemUpsert = {
  category_id: number;
  name: string;
  description?: string | null;
  price: number;
  weight: number;
  cooking_time: number;
  image_url?: string | null;
  sort_order: number;
  is_active: boolean;
};

export type AdditionUpsert = {
  menu_item_id: number;
  name: string;
  price: number;
  weight: number;
  is_active: boolean;
};

export type RemovalUpsert = {
  menu_item_id: number;
  name: string;
  is_active: boolean;
};

export type StatsBucket = {
  total_sum: number;
  order_count: number;
};

export type StatisticsResponse = {
  today: StatsBucket;
  period: StatsBucket;
};

export type SettingDto = {
  key: string;
  value: string;
  updated_at: number;
};

export type SettingsResponse = {
  settings: SettingDto[];
};
