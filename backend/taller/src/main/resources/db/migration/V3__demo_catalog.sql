INSERT INTO products (id, name, width_cm, length_cm, material, price, campaign_category, description)
VALUES
    (1, 'Tapiz Aurora', 60, 90, 'Algodón', 45000, 'INTERIOR_DECORATION', 'Tapiz artesanal de algodón.'),
    (2, 'Colgante Brisa', 30, 50, 'Cáñamo', 25000, 'FESTIVALS', 'Pieza colgante de fibras naturales.'),
    (3, 'Camino de Mesa', 35, 180, 'Algodón', 38000, 'WEDDINGS', 'Camino de mesa tejido a mano.'),
    (4, 'Portamacetero', 25, 110, 'Yute', 18000, 'TRAVEL', 'Portamacetero para espacios interiores.');

INSERT INTO atelier_collections (id, name, description) VALUES
    (1, 'Fibras naturales', 'Colección inicial de demostración.');
INSERT INTO collection_products (collection_id, product_id) VALUES (1, 1), (1, 2);
