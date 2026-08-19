ALTER TABLE categories
    ADD COLUMN IF NOT EXISTS default_cooking_minutes INTEGER NOT NULL DEFAULT 15 CHECK (default_cooking_minutes >= 0);

UPDATE categories SET default_cooking_minutes = 15 WHERE id = 1;
UPDATE categories SET default_cooking_minutes = 30 WHERE id = 2;
UPDATE categories SET default_cooking_minutes = 5 WHERE id IN (3, 4);

UPDATE menu_items SET cooking_time = 15, updated_at = EXTRACT(EPOCH FROM NOW())::BIGINT * 1000
WHERE category_id = 1;

UPDATE menu_items SET cooking_time = 60, updated_at = EXTRACT(EPOCH FROM NOW())::BIGINT * 1000
WHERE category_id = 2 AND name ILIKE '%куриц%';

UPDATE menu_items SET cooking_time = 30, updated_at = EXTRACT(EPOCH FROM NOW())::BIGINT * 1000
WHERE category_id = 2 AND name NOT ILIKE '%куриц%';

UPDATE settings SET value = '22:00', updated_at = EXTRACT(EPOCH FROM NOW())::BIGINT * 1000
WHERE key = 'cutoff_regular';

UPDATE settings SET value = '22:00', updated_at = EXTRACT(EPOCH FROM NOW())::BIGINT * 1000
WHERE key = 'cutoff_grill';
