ALTER TABLE orders
    ADD COLUMN payment_status TEXT NOT NULL DEFAULT 'PAID'
        CHECK (payment_status IN ('WAITING', 'PAID', 'FAILED', 'CANCELLED')),
    ADD COLUMN tbank_payment_id BIGINT,
    ADD COLUMN paid_at BIGINT;

UPDATE orders SET payment_status = 'PAID';

ALTER TABLE orders ALTER COLUMN payment_status SET DEFAULT 'WAITING';

CREATE INDEX idx_orders_payment_status_updated ON orders (payment_status, updated_at, id);
