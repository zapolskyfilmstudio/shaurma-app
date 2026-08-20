ALTER TABLE orders
    ADD COLUMN delivery_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN delivery_phone TEXT,
    ADD COLUMN delivery_address TEXT;
