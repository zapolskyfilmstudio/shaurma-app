INSERT INTO categories (id, name, sort_order, is_active, is_grill)
VALUES
  (1, 'Шаурма', 10, true, false),
  (2, 'Гриль', 20, true, true),
  (3, 'Напитки', 30, true, false),
  (4, 'Соусы', 40, true, false)
ON CONFLICT (id) DO NOTHING;

INSERT INTO menu_items (id, category_id, name, description, price, weight, cooking_time, image_url, sort_order, is_active, created_at, updated_at)
VALUES
  (101, 1, 'Шаурма классическая', 'Лаваш — 150 г; Курица — 120 г; Соус — 30 г; Помидоры — 40 г; Огурцы — 40 г', 350, 450, 10, null, 10, true, 0, 0),
  (102, 1, 'Шаурма с сыром', 'Лаваш, курица, сыр, овощи, фирменный соус', 400, 480, 10, null, 20, true, 0, 0),
  (103, 1, 'Шаурма острая', 'Лаваш, курица, острый соус, овощи', 380, 450, 10, null, 30, true, 0, 0),
  (104, 1, 'Шаурма с фри', 'Лаваш, курица, картофель фри, овощи, соус', 430, 520, 12, null, 40, true, 0, 0),
  (201, 2, 'Курица гриль', 'Курица гриль, специи, соус', 600, 900, 60, null, 10, true, 0, 0),
  (301, 3, 'Морс', 'Домашний морс', 120, 500, 2, null, 10, true, 0, 0),
  (302, 3, 'Вода', 'Вода без газа', 80, 500, 1, null, 20, true, 0, 0),
  (401, 4, 'Соус чесночный', 'Порция соуса', 50, 50, 1, null, 10, true, 0, 0)
ON CONFLICT (id) DO NOTHING;

INSERT INTO additions (id, menu_item_id, name, price, weight, is_active)
VALUES
  (2001, 101, 'Картошка фри', 100, 150, true),
  (2002, 101, 'Халупень', 50, 50, true),
  (2003, 101, 'Доп. соус', 40, 30, true),
  (2004, 101, 'Сырный соус', 60, 40, true),
  (2011, 102, 'Картошка фри', 100, 150, true),
  (2012, 102, 'Доп. соус', 40, 30, true)
ON CONFLICT (id) DO NOTHING;

INSERT INTO removals (id, menu_item_id, name, is_active)
VALUES
  (3001, 101, 'Помидоры', true),
  (3002, 101, 'Огурцы', true),
  (3003, 101, 'Капуста', true),
  (3004, 101, 'Лук', true),
  (3005, 101, 'Соус чесночный', true),
  (3011, 102, 'Помидоры', true),
  (3012, 102, 'Лук', true)
ON CONFLICT (id) DO NOTHING;

INSERT INTO settings (key, value, updated_at)
VALUES
  ('work_start_time', '00:00', 0),
  ('cutoff_regular', '23:00', 0),
  ('cutoff_grill', '23:00', 0)
ON CONFLICT (key) DO NOTHING;

SELECT setval(pg_get_serial_sequence('categories', 'id'), COALESCE((SELECT max(id) FROM categories), 1), true);
SELECT setval(pg_get_serial_sequence('menu_items', 'id'), COALESCE((SELECT max(id) FROM menu_items), 1), true);
SELECT setval(pg_get_serial_sequence('additions', 'id'), COALESCE((SELECT max(id) FROM additions), 1), true);
SELECT setval(pg_get_serial_sequence('removals', 'id'), COALESCE((SELECT max(id) FROM removals), 1), true);
