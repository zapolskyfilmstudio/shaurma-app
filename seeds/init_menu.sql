INSERT INTO categories (id, name, sort_order, is_active, is_grill)
VALUES
  (1, 'Шаурма', 10, true, false),
  (2, 'Гриль на углях', 20, true, true),
  (3, 'Напитки', 30, true, false),
  (4, 'Соусы', 40, true, false)
ON CONFLICT (id) DO NOTHING;

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
  (126, 1, 'Шаурма на тар большая с картофелем', null, 549, 0, 0, null, 160, true, 0, 0),
  (211, 2, 'Шашлык из баранины', null, 350, 0, 0, null, 10, true, 0, 0),
  (212, 2, 'Шашлык из говядины (салат)', null, 350, 0, 0, null, 20, true, 0, 0),
  (213, 2, 'Кура-гриль', null, 1200, 0, 0, null, 30, true, 0, 0),
  (214, 2, 'Стейк', null, 1200, 0, 0, null, 40, true, 0, 0),
  (215, 2, 'Крылышки (4 шт)', null, 250, 0, 0, null, 50, true, 0, 0),
  (216, 2, 'Люля-кебаб (фри, салат)', null, 450, 0, 0, null, 60, true, 0, 0),
  (217, 2, 'Люля-кебаб', null, 300, 0, 0, null, 70, true, 0, 0),
  (301, 3, 'Морс', 'Домашний морс', 120, 500, 2, null, 10, true, 0, 0),
  (302, 3, 'Вода', 'Вода без газа', 80, 500, 1, null, 20, true, 0, 0),
  (401, 4, 'Соус чесночный', 'Порция соуса', 50, 50, 1, null, 10, true, 0, 0)
ON CONFLICT (id) DO NOTHING;

INSERT INTO settings (key, value, updated_at)
VALUES
  ('work_start_time', '00:00', 0),
  ('cutoff_regular', '23:00', 0),
  ('cutoff_grill', '23:00', 0)
ON CONFLICT (key) DO NOTHING;

SELECT setval(pg_get_serial_sequence('categories', 'id'), COALESCE((SELECT max(id) FROM categories), 1), true);
SELECT setval(pg_get_serial_sequence('menu_items', 'id'), COALESCE((SELECT max(id) FROM menu_items), 1), true);
