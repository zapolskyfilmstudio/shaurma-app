INSERT INTO removals (menu_item_id, name, is_active)
SELECT mi.id, ingredient.name, true
FROM menu_items mi
CROSS JOIN (
    VALUES ('капуста'), ('морковь'), ('помидор'), ('огурец')
) AS ingredient(name)
WHERE mi.category_id = 1
  AND mi.is_active = true
  AND NOT EXISTS (
    SELECT 1
    FROM removals existing
    WHERE existing.menu_item_id = mi.id
      AND lower(existing.name) = ingredient.name
      AND existing.is_active = true
  );
