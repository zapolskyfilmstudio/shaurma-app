INSERT INTO categories (id, name, sort_order, is_active, is_grill, default_cooking_minutes)
VALUES (5, 'Картошка & снеки', 25, true, false, 10)
ON CONFLICT (id) DO UPDATE SET
  name = EXCLUDED.name,
  sort_order = EXCLUDED.sort_order,
  is_active = EXCLUDED.is_active,
  is_grill = EXCLUDED.is_grill,
  default_cooking_minutes = EXCLUDED.default_cooking_minutes;

INSERT INTO menu_items (id, category_id, name, description, price, weight, cooking_time, image_url, sort_order, is_active, created_at, updated_at)
VALUES
  (311, 5, 'Наггетсы', null, 300, 0, 0, null, 10, true, 0, 0),
  (312, 5, 'Картофель фри', null, 149, 0, 0, null, 20, true, 0, 0),
  (313, 5, 'Картофель по-деревенски', null, 199, 0, 0, null, 30, true, 0, 0)
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
