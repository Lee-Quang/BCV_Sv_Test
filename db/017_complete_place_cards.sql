-- Complete the reviewed 311-place dataset after 013_northern_cards_collections.sql.
-- Only missing cards are created; existing cards, memberships and ownership stay intact.
BEGIN;
SET LOCAL client_encoding = 'UTF8';
SET LOCAL search_path = public;

CREATE TEMP TABLE remaining_card_regions (
    region_id bigint PRIMARY KEY,
    collection_name text NOT NULL
) ON COMMIT DROP;

-- Region IDs refer to the supplied dataset, matching its original province names.
INSERT INTO remaining_card_regions VALUES
    (19, 'Hành trình Bắc Trung Bộ'), (34, 'Hành trình Bắc Trung Bộ'),
    (39, 'Hành trình Bắc Trung Bộ'), (43, 'Hành trình Bắc Trung Bộ'),
    (46, 'Hành trình Bắc Trung Bộ'), (50, 'Hành trình Bắc Trung Bộ'),
    (5, 'Duyên hải Nam Trung Bộ'), (6, 'Duyên hải Nam Trung Bộ'),
    (25, 'Duyên hải Nam Trung Bộ'), (36, 'Duyên hải Nam Trung Bộ'),
    (38, 'Duyên hải Nam Trung Bộ'), (40, 'Duyên hải Nam Trung Bộ'),
    (41, 'Duyên hải Nam Trung Bộ'), (59, 'Duyên hải Nam Trung Bộ'),
    (15, 'Khám phá Tây Nguyên'), (27, 'Khám phá Tây Nguyên'),
    (31, 'Khám phá Tây Nguyên'), (60, 'Khám phá Tây Nguyên'),
    (61, 'Khám phá Tây Nguyên'),
    (2, 'Dấu chân Đông Nam Bộ'), (3, 'Dấu chân Đông Nam Bộ'),
    (4, 'Dấu chân Đông Nam Bộ'), (47, 'Dấu chân Đông Nam Bộ'),
    (54, 'Dấu chân Đông Nam Bộ'), (62, 'Dấu chân Đông Nam Bộ'),
    (1, 'Sắc màu miền Tây'), (7, 'Sắc màu miền Tây'),
    (11, 'Sắc màu miền Tây'), (13, 'Sắc màu miền Tây'),
    (14, 'Sắc màu miền Tây'), (24, 'Sắc màu miền Tây'),
    (26, 'Sắc màu miền Tây'), (29, 'Sắc màu miền Tây'),
    (44, 'Sắc màu miền Tây'), (51, 'Sắc màu miền Tây'),
    (52, 'Sắc màu miền Tây'), (55, 'Sắc màu miền Tây'),
    (63, 'Sắc màu miền Tây');

DO $check$
BEGIN
    IF (SELECT count(*) FROM public.place) <> 311 THEN
        RAISE EXCEPTION 'Expected the reviewed 311-place dataset; no changes applied';
    END IF;
    IF EXISTS (
        SELECT 1 FROM public.place p
        WHERE NOT EXISTS (SELECT 1 FROM public.card c WHERE c.place_id = p.place_id)
          AND NOT EXISTS (SELECT 1 FROM remaining_card_regions r WHERE r.region_id = p.region_id)
    ) THEN
        RAISE EXCEPTION 'Missing cards outside the mapped regions; run migration 013 first';
    END IF;
    IF EXISTS (
        SELECT 1 FROM public.collection c
        WHERE c.name IN (SELECT collection_name FROM remaining_card_regions)
        GROUP BY c.name HAVING count(*) > 1 OR bool_or(c.status <> 'ACTIVE')
    ) THEN
        RAISE EXCEPTION 'Target collection names must be unique and ACTIVE';
    END IF;
END
$check$;

CREATE TEMP TABLE added_place_cards ON COMMIT DROP AS
WITH added AS (
    INSERT INTO public.card (place_id, name, image_key, description, status)
    SELECT p.place_id, p.name,
           (SELECT pi.image_key FROM public.place_image pi
            WHERE pi.place_id = p.place_id AND NULLIF(btrim(pi.image_key), '') IS NOT NULL
            ORDER BY pi.place_image_id LIMIT 1),
           COALESCE(NULLIF(btrim(p.introduction), ''), 'Thẻ sưu tầm địa danh ' || p.name),
           CASE WHEN p.status = 'ACTIVE' AND r.status = 'ACTIVE' THEN 'ACTIVE' ELSE 'DRAFT' END
    FROM public.place p
    JOIN public.region r ON r.region_id = p.region_id
    WHERE NOT EXISTS (SELECT 1 FROM public.card c WHERE c.place_id = p.place_id)
    ORDER BY p.place_id
    ON CONFLICT (place_id) DO NOTHING
    RETURNING card_id, place_id
)
SELECT * FROM added;

INSERT INTO public.collection (name, description, status)
SELECT DISTINCT m.collection_name,
       'Bộ sưu tập địa danh ' || m.collection_name || ' theo khu vực trong dữ liệu Bước Chân Việt.',
       'ACTIVE'
FROM added_place_cards a
JOIN public.place p ON p.place_id = a.place_id
JOIN remaining_card_regions m ON m.region_id = p.region_id
WHERE NOT EXISTS (SELECT 1 FROM public.collection c WHERE c.name = m.collection_name);

INSERT INTO public.collection_card (collection_id, card_id)
SELECT c.collection_id, a.card_id
FROM added_place_cards a
JOIN public.place p ON p.place_id = a.place_id
JOIN remaining_card_regions m ON m.region_id = p.region_id
JOIN public.collection c ON c.name = m.collection_name
ON CONFLICT (collection_id, card_id) DO NOTHING;

-- Users who already discovered these places receive the newly available card too.
INSERT INTO public.user_card (user_id, card_id, received_at)
SELECT d.user_id, a.card_id, d.discovered_at
FROM added_place_cards a
JOIN public.card c ON c.card_id = a.card_id AND c.status = 'ACTIVE'
JOIN public.user_discovered_place d ON d.place_id = a.place_id
ON CONFLICT (user_id, card_id) DO NOTHING;

DO $check$
BEGIN
    IF (SELECT count(*) FROM public.card) <> 311
       OR EXISTS (SELECT 1 FROM public.place p
                  WHERE NOT EXISTS (SELECT 1 FROM public.card c WHERE c.place_id = p.place_id))
       OR EXISTS (SELECT 1 FROM added_place_cards a
                  LEFT JOIN public.collection_card cc ON cc.card_id = a.card_id
                  GROUP BY a.card_id HAVING count(cc.collection_id) <> 1)
       OR EXISTS (SELECT 1 FROM added_place_cards a
                  JOIN public.card c ON c.card_id = a.card_id AND c.status = 'ACTIVE'
                  JOIN public.user_discovered_place d ON d.place_id = a.place_id
                  WHERE NOT EXISTS (SELECT 1 FROM public.user_card uc
                                    WHERE uc.user_id = d.user_id AND uc.card_id = a.card_id)) THEN
        RAISE EXCEPTION 'Card completion validation failed; transaction rolled back';
    END IF;
END
$check$;

SELECT count(*) AS cards_created FROM added_place_cards;
COMMIT;
