-- One collectible card per northern place, grouped into five regional collections.
-- Run after 003_import_places.sql. 012_northern_place_images.sql is optional but
-- supplies photos to 31 cards. Re-running this migration does not duplicate rows.
BEGIN;
SET LOCAL client_encoding = 'UTF8';
SET LOCAL search_path = public;

DO $check$
BEGIN
    IF (SELECT count(*) FROM public.place
        WHERE region_id IN (8,9,10,12,16,17,18,20,21,22,23,28,30,32,33,35,37,42,45,48,49,53,56,57,58)) <> 125 THEN
        RAISE EXCEPTION 'Expected 125 northern places; no changes applied';
    END IF;
    IF EXISTS (SELECT 1 FROM public.collection
               WHERE name IN ('Di sản Hà Nội và Kinh Bắc', 'Đồng bằng Bắc Bộ',
                              'Biển đảo Bắc Bộ', 'Núi rừng Đông Bắc', 'Hành trình Tây Bắc')
               GROUP BY name HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'Duplicate northern collection names; no changes applied';
    END IF;
END
$check$;

INSERT INTO public.card (place_id, name, image_key, description, status)
SELECT p.place_id, p.name,
       (SELECT pi.image_key FROM public.place_image pi
        WHERE pi.place_id = p.place_id ORDER BY pi.place_image_id LIMIT 1),
       COALESCE(NULLIF(btrim(p.introduction), ''), 'Thẻ sưu tầm địa danh ' || p.name),
       'DRAFT'
FROM public.place p
WHERE p.region_id IN (8,9,10,12,16,17,18,20,21,22,23,28,30,32,33,35,37,42,45,48,49,53,56,57,58)
ON CONFLICT (place_id) DO NOTHING;

-- Cards created before image import gain a photo when one is available.
UPDATE public.card c
SET image_key = (SELECT pi.image_key FROM public.place_image pi
                 WHERE pi.place_id = c.place_id ORDER BY pi.place_image_id LIMIT 1)
WHERE c.image_key IS NULL
  AND EXISTS (SELECT 1 FROM public.place p WHERE p.place_id = c.place_id
              AND p.region_id IN (8,9,10,12,16,17,18,20,21,22,23,28,30,32,33,35,37,42,45,48,49,53,56,57,58))
  AND EXISTS (SELECT 1 FROM public.place_image pi WHERE pi.place_id = c.place_id);

WITH groups(name, description) AS (VALUES
    ('Di sản Hà Nội và Kinh Bắc',
     'Các địa danh ở Hà Nội, Bắc Ninh, Bắc Giang và Vĩnh Phúc.'),
    ('Đồng bằng Bắc Bộ',
     'Các địa danh ở Hà Nam, Hưng Yên, Hải Dương, Nam Định, Ninh Bình và Thái Bình.'),
    ('Biển đảo Bắc Bộ',
     'Các địa danh ở Hải Phòng và Quảng Ninh.'),
    ('Núi rừng Đông Bắc',
     'Các địa danh ở Bắc Kạn, Cao Bằng, Hà Giang, Lạng Sơn, Thái Nguyên và Tuyên Quang.'),
    ('Hành trình Tây Bắc',
     'Các địa danh ở Hòa Bình, Lai Châu, Lào Cai, Phú Thọ, Sơn La, Yên Bái và Điện Biên.')
)
INSERT INTO public.collection (name, description, status)
SELECT g.name, g.description, 'DRAFT'
FROM groups g
WHERE NOT EXISTS (SELECT 1 FROM public.collection c WHERE c.name = g.name);

WITH region_groups(region_id, collection_name) AS (VALUES
    (8, 'Di sản Hà Nội và Kinh Bắc'), (10, 'Di sản Hà Nội và Kinh Bắc'),
    (18, 'Di sản Hà Nội và Kinh Bắc'), (56, 'Di sản Hà Nội và Kinh Bắc'),
    (17, 'Đồng bằng Bắc Bộ'), (21, 'Đồng bằng Bắc Bộ'),
    (22, 'Đồng bằng Bắc Bộ'), (33, 'Đồng bằng Bắc Bộ'),
    (35, 'Đồng bằng Bắc Bộ'), (48, 'Đồng bằng Bắc Bộ'),
    (23, 'Biển đảo Bắc Bộ'), (42, 'Biển đảo Bắc Bộ'),
    (9, 'Núi rừng Đông Bắc'), (12, 'Núi rừng Đông Bắc'),
    (16, 'Núi rừng Đông Bắc'), (32, 'Núi rừng Đông Bắc'),
    (49, 'Núi rừng Đông Bắc'), (53, 'Núi rừng Đông Bắc'),
    (20, 'Hành trình Tây Bắc'), (28, 'Hành trình Tây Bắc'),
    (30, 'Hành trình Tây Bắc'), (37, 'Hành trình Tây Bắc'),
    (45, 'Hành trình Tây Bắc'), (57, 'Hành trình Tây Bắc'),
    (58, 'Hành trình Tây Bắc')
)
INSERT INTO public.collection_card (collection_id, card_id)
SELECT col.collection_id, c.card_id
FROM region_groups rg
JOIN public.place p ON p.region_id = rg.region_id
JOIN public.card c ON c.place_id = p.place_id
JOIN public.collection col ON col.name = rg.collection_name
WHERE NOT EXISTS (SELECT 1 FROM public.collection_card cc
                  WHERE cc.collection_id = col.collection_id AND cc.card_id = c.card_id);

DO $check$
DECLARE
    northern_names text[] := ARRAY['Di sản Hà Nội và Kinh Bắc', 'Đồng bằng Bắc Bộ',
                                    'Biển đảo Bắc Bộ', 'Núi rừng Đông Bắc', 'Hành trình Tây Bắc'];
BEGIN
    IF (SELECT count(*) FROM public.card c JOIN public.place p ON p.place_id = c.place_id
        WHERE p.region_id IN (8,9,10,12,16,17,18,20,21,22,23,28,30,32,33,35,37,42,45,48,49,53,56,57,58)) <> 125
       OR (SELECT count(*) FROM public.collection WHERE name = ANY(northern_names)) <> 5
       OR (SELECT count(*) FROM public.collection_card cc
           JOIN public.collection col ON col.collection_id = cc.collection_id
           WHERE col.name = ANY(northern_names)) <> 125
       OR EXISTS (
           SELECT 1 FROM public.place p
           JOIN public.card c ON c.place_id = p.place_id
           LEFT JOIN public.collection_card cc ON cc.card_id = c.card_id
           LEFT JOIN public.collection col ON col.collection_id = cc.collection_id
                                          AND col.name = ANY(northern_names)
           WHERE p.region_id IN (8,9,10,12,16,17,18,20,21,22,23,28,30,32,33,35,37,42,45,48,49,53,56,57,58)
           GROUP BY p.place_id HAVING count(col.collection_id) <> 1
       ) THEN
        RAISE EXCEPTION 'Northern card/collection validation failed; transaction rolled back';
    END IF;
END
$check$;

COMMIT;
