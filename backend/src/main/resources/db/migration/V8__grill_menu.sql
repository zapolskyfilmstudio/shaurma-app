UPDATE categories
SET name = 'Гриль на углях'
WHERE id = 2;

UPDATE menu_items
SET is_active = false, updated_at = EXTRACT(EPOCH FROM NOW())::BIGINT * 1000
WHERE id = 201;

INSERT INTO menu_items (id, category_id, name, description, price, weight, cooking_time, image_url, sort_order, is_active, created_at, updated_at)
VALUES
  (211, 2, 'Шашлык из баранины', null, 350, 0, 0, null, 10, true, 0, 0),
  (212, 2, 'Шашлык из говядины (салат)', null, 350, 0, 0, null, 20, true, 0, 0),
  (213, 2, 'Кура-гриль', null, 1200, 0, 0, null, 30, true, 0, 0),
  (214, 2, 'Стейк', null, 1200, 0, 0, null, 40, true, 0, 0),
  (215, 2, 'Крылышки (4 шт)', null, 250, 0, 0, null, 50, true, 0, 0),
  (216, 2, 'Люля-кебаб (фри, салат)', null, 450, 0, 0, null, 60, true, 0, 0),
  (217, 2, 'Люля-кебаб', null, 300, 0, 0, null, 70, true, 0, 0)
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
