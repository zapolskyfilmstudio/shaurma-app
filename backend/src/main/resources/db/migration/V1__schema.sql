CREATE TABLE devices (
    device_id UUID PRIMARY KEY,
    platform TEXT NOT NULL CHECK (platform IN ('android', 'ios')),
    client_number INTEGER NOT NULL UNIQUE,
    name VARCHAR(30),
    phone VARCHAR(12),
    is_blocked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE INDEX idx_devices_client_number ON devices (client_number);
CREATE INDEX idx_devices_phone ON devices (phone);
CREATE INDEX idx_devices_name_lower ON devices (lower(name));
CREATE INDEX idx_devices_updated_at_id ON devices (updated_at, device_id);

CREATE TABLE categories (
    id BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_grill BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_categories_active_sort ON categories (is_active, sort_order, id);

CREATE TABLE menu_items (
    id BIGSERIAL PRIMARY KEY,
    category_id BIGINT NOT NULL REFERENCES categories (id),
    name TEXT NOT NULL,
    description TEXT,
    price INTEGER NOT NULL CHECK (price >= 0),
    weight INTEGER NOT NULL CHECK (weight >= 0),
    cooking_time INTEGER NOT NULL CHECK (cooking_time >= 0),
    image_url TEXT,
    sort_order INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE INDEX idx_menu_items_category_active_sort ON menu_items (category_id, is_active, sort_order, id);

CREATE TABLE additions (
    id BIGSERIAL PRIMARY KEY,
    menu_item_id BIGINT NOT NULL REFERENCES menu_items (id),
    name TEXT NOT NULL,
    price INTEGER NOT NULL CHECK (price >= 0),
    weight INTEGER NOT NULL CHECK (weight >= 0),
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_additions_item_active ON additions (menu_item_id, is_active, id);

CREATE TABLE removals (
    id BIGSERIAL PRIMARY KEY,
    menu_item_id BIGINT NOT NULL REFERENCES menu_items (id),
    name TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_removals_item_active ON removals (menu_item_id, is_active, id);

CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    public_id TEXT NOT NULL UNIQUE,
    device_id UUID NOT NULL REFERENCES devices (device_id),
    status TEXT NOT NULL CHECK (status IN ('NEW', 'CONFIRMED', 'COOKING', 'READY', 'COMPLETED')),
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    requested_time BIGINT NOT NULL,
    cooking_start_time BIGINT NOT NULL,
    total_price INTEGER NOT NULL CHECK (total_price >= 0),
    general_comment TEXT
);

CREATE INDEX idx_orders_device_updated_id ON orders (device_id, updated_at, id);
CREATE INDEX idx_orders_updated_id ON orders (updated_at, id);
CREATE INDEX idx_orders_requested_time ON orders (requested_time);
CREATE INDEX idx_orders_status ON orders (status);

CREATE TABLE order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    menu_item_id BIGINT NOT NULL,
    name_snapshot TEXT NOT NULL,
    price_snapshot INTEGER NOT NULL CHECK (price_snapshot >= 0),
    weight_snapshot INTEGER NOT NULL CHECK (weight_snapshot >= 0),
    additions_snapshot JSONB NOT NULL DEFAULT '[]'::jsonb,
    removals_snapshot JSONB NOT NULL DEFAULT '[]'::jsonb
);

CREATE INDEX idx_order_items_order_id ON order_items (order_id, id);

CREATE TABLE order_status_history (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    status TEXT NOT NULL CHECK (status IN ('NEW', 'CONFIRMED', 'COOKING', 'READY', 'COMPLETED')),
    changed_at BIGINT NOT NULL,
    changed_by TEXT NOT NULL
);

CREATE INDEX idx_order_status_history_order_id ON order_status_history (order_id, id);

CREATE TABLE settings (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE TABLE daily_counter (
    date_key TEXT PRIMARY KEY,
    date_part TEXT NOT NULL,
    counter INTEGER NOT NULL DEFAULT 0
);
