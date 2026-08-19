CREATE TABLE weekly_schedule (
    day_of_week SMALLINT PRIMARY KEY CHECK (day_of_week BETWEEN 1 AND 7),
    open_time TIME NOT NULL,
    last_order_time TIME NOT NULL,
    updated_at BIGINT NOT NULL DEFAULT 0
);

INSERT INTO weekly_schedule (day_of_week, open_time, last_order_time, updated_at)
VALUES
    (1, '12:00', '21:30', 0),
    (2, '12:00', '21:30', 0),
    (3, '12:00', '21:30', 0),
    (4, '12:00', '21:30', 0),
    (5, '15:30', '21:30', 0),
    (6, '12:00', '21:30', 0),
    (7, '12:00', '21:30', 0);

UPDATE settings SET value = '12:00', updated_at = EXTRACT(EPOCH FROM NOW())::BIGINT * 1000
WHERE key = 'work_start_time';

UPDATE settings SET value = '21:30', updated_at = EXTRACT(EPOCH FROM NOW())::BIGINT * 1000
WHERE key IN ('cutoff_regular', 'cutoff_grill');

UPDATE categories SET default_cooking_minutes = 10 WHERE id = 1;
UPDATE categories SET default_cooking_minutes = 40 WHERE id = 2;
UPDATE categories SET default_cooking_minutes = 0 WHERE id = 3;
UPDATE categories SET default_cooking_minutes = 10 WHERE id = 5;
UPDATE categories SET default_cooking_minutes = 0 WHERE id = 6;

UPDATE menu_items
SET cooking_time = 10, updated_at = EXTRACT(EPOCH FROM NOW())::BIGINT * 1000
WHERE category_id = 1 AND is_active = true;

UPDATE menu_items
SET cooking_time = 60, updated_at = EXTRACT(EPOCH FROM NOW())::BIGINT * 1000
WHERE id IN (213, 214);

UPDATE menu_items
SET cooking_time = 30, updated_at = EXTRACT(EPOCH FROM NOW())::BIGINT * 1000
WHERE id IN (216, 217);

UPDATE menu_items
SET cooking_time = 40, updated_at = EXTRACT(EPOCH FROM NOW())::BIGINT * 1000
WHERE id IN (211, 212, 215);

UPDATE menu_items
SET cooking_time = 0, updated_at = EXTRACT(EPOCH FROM NOW())::BIGINT * 1000
WHERE category_id = 3 AND is_active = true;

UPDATE menu_items
SET cooking_time = 10, updated_at = EXTRACT(EPOCH FROM NOW())::BIGINT * 1000
WHERE category_id = 5 AND is_active = true;

UPDATE menu_items
SET cooking_time = 0, updated_at = EXTRACT(EPOCH FROM NOW())::BIGINT * 1000
WHERE category_id = 6 AND is_active = true;

UPDATE menu_items
SET cooking_time = 40, updated_at = EXTRACT(EPOCH FROM NOW())::BIGINT * 1000
WHERE category_id = 2 AND is_active = true AND id NOT IN (211, 212, 213, 214, 215, 216, 217);
