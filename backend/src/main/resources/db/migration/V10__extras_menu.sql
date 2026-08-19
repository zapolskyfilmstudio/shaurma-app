INSERT INTO categories (id, name, sort_order, is_active, is_grill, default_cooking_minutes)
VALUES (6, 'Дополнительно', 35, true, false, 5)
ON CONFLICT (id) DO UPDATE SET
  name = EXCLUDED.name,
  sort_order = EXCLUDED.sort_order,
  is_active = EXCLUDED.is_active,
  is_grill = EXCLUDED.is_grill,
  default_cooking_minutes = EXCLUDED.default_cooking_minutes;

INSERT INTO menu_items (id, category_id, name, description, price, weight, cooking_time, image_url, sort_order, is_active, created_at, updated_at)
VALUES
  (411, 6, 'Соус белый', null, 49, 0, 0, null, 10, true, 0, 0),
  (412, 6, 'Соус красный', null, 49, 0, 0, null, 20, true, 0, 0),
  (413, 6, 'Лаваш', null, 59, 0, 0, null, 30, true, 0, 0),
  (414, 6, 'Лук', null, 29, 0, 0, null, 40, true, 0, 0)
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

SELECT setval(pg_get_serial_sequence('categories', 'id'), COALESCE((SELECT max(id) FROM categories), 1), true);
SELECT setval(pg_get_serial_sequence('menu_items', 'id'), COALESCE((SELECT max(id) FROM menu_items), 1), true);
