UPDATE menu_items
SET is_active = false, updated_at = EXTRACT(EPOCH FROM NOW())::BIGINT * 1000
WHERE id IN (101, 102, 103, 104);

UPDATE additions
SET is_active = false
WHERE menu_item_id IN (101, 102, 103, 104);

UPDATE removals
SET is_active = false
WHERE menu_item_id IN (101, 102, 103, 104);

INSERT INTO menu_items (id, category_id, name, description, price, weight, cooking_time, image_url, sort_order, is_active, created_at, updated_at)
VALUES
  (111, 1, 'Шаурма мини', null, 259, 0, 0, null, 10, true, 0, 0),
  (112, 1, 'Шаурма мини с фри', null, 349, 0, 0, null, 20, true, 0, 0),
  (113, 1, 'Шаурма мини острая', null, 289, 0, 0, null, 30, true, 0, 0),
  (114, 1, 'Шаурма мини с сыром', null, 309, 0, 0, null, 40, true, 0, 0),
  (115, 1, 'Шаурма стандарт', null, 349, 0, 0, null, 50, true, 0, 0),
  (116, 1, 'Шаурма с сыром', null, 399, 0, 0, null, 60, true, 0, 0),
  (117, 1, 'Сирийская острая', null, 369, 0, 0, null, 70, true, 0, 0),
  (118, 1, 'Шаурма с фри', null, 469, 0, 0, null, 80, true, 0, 0),
  (119, 1, 'Шаурма большая', null, 429, 0, 0, null, 90, true, 0, 0),
  (120, 1, 'Шаурма большая с сыром', null, 509, 0, 0, null, 100, true, 0, 0),
  (121, 1, 'Сирийская большая острая', null, 449, 0, 0, null, 110, true, 0, 0),
  (122, 1, 'Шаурма с фри большая', null, 529, 0, 0, null, 120, true, 0, 0),
  (123, 1, 'Шаурма на тар с фри', null, 459, 0, 0, null, 130, true, 0, 0),
  (124, 1, 'Шаурма на тар с картофелем', null, 479, 0, 0, null, 140, true, 0, 0),
  (125, 1, 'Шаурма на тар большая с фри', null, 519, 0, 0, null, 150, true, 0, 0),
  (126, 1, 'Шаурма на тар большая с картофелем', null, 549, 0, 0, null, 160, true, 0, 0)
ON CONFLICT (id) DO UPDATE SET
  category_id = EXCLUDED.category_id,
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  price = EXCLUDED.price,
  weight = EXCLUDED.weight,
  cooking_time = EXCLUDED.cooking_time,
  sort_order = EXCLUDED.sort_order,
  is_active = EXCLUDED.is_active,
  updated_at = EXTRACT(EPOCH FROM NOW())::BIGINT * 1000;

SELECT setval(pg_get_serial_sequence('menu_items', 'id'), COALESCE((SELECT max(id) FROM menu_items), 1), true);
