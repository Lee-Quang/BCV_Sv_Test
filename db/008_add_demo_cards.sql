-- Give every pictured place a collectible card and a collection membership.
-- No new tables, users, or images are created.

BEGIN;
SET LOCAL client_encoding = 'UTF8';
SET LOCAL search_path = public;

DO $check$
BEGIN
    IF (SELECT count(*) FROM pg_catalog.pg_tables
        WHERE schemaname = 'public' AND tablename <> 'spatial_ref_sys') <> 25
       OR (SELECT count(*) FROM public.place_image) <> 15
       OR (SELECT count(DISTINCT place_id) FROM public.place_image) <> 15
       OR (SELECT count(*) FROM public.card) <> 3
       OR (SELECT count(*) FROM public.collection) <> 2
       OR (SELECT count(*) FROM public.collection_card) <> 3
       OR (SELECT count(*) FROM public.place_image AS pi
           LEFT JOIN public.card AS c ON c.place_id = pi.place_id
           WHERE pi.place_image_id BETWEEN 900004 AND 900015
             AND c.card_id IS NOT NULL) <> 0
       OR (SELECT count(*) FROM public.place_image
           WHERE place_image_id BETWEEN 900004 AND 900015) <> 12 THEN
        RAISE EXCEPTION 'Unexpected BCV card/image snapshot; no changes applied';
    END IF;
END
$check$;

INSERT INTO public.card
    (card_id, place_id, name, image_key, description, status)
SELECT pi.place_image_id, p.place_id, p.name, pi.image_key,
       COALESCE(NULLIF(btrim(p.introduction), ''),
                'Thẻ sưu tầm cho địa danh ' || p.name),
       'DRAFT'
FROM public.place_image AS pi
JOIN public.place AS p ON p.place_id = pi.place_id
WHERE pi.place_image_id BETWEEN 900004 AND 900015
ORDER BY pi.place_image_id;

INSERT INTO public.collection_card
    (collection_card_id, collection_id, card_id) VALUES
    (900004, 900001, 900004), -- Hội An
    (900005, 900002, 900005), -- Hạ Long
    (900006, 900002, 900006), -- Bản Giốc
    (900007, 900001, 900007), -- Hồ Gươm
    (900008, 900002, 900008), -- Fansipan
    (900009, 900001, 900009), -- Nhà thờ Đức Bà
    (900010, 900002, 900010), -- Tràng An
    (900011, 900002, 900011), -- Phú Quốc
    (900012, 900002, 900012), -- Hồ Xuân Hương
    (900013, 900002, 900013), -- Bà Nà Hills
    (900014, 900002, 900014), -- Phong Nha
    (900015, 900001, 900015); -- Đại Nội Huế

DO $check$
BEGIN
    IF (SELECT count(*) FROM public.card) <> 15
       OR (SELECT count(*) FROM public.collection_card) <> 15
       OR (SELECT count(*) FROM public.card AS c
           JOIN public.place_image AS pi
             ON pi.place_id = c.place_id AND pi.image_key = c.image_key
           JOIN public.place AS p ON p.place_id = c.place_id
           WHERE c.name = p.name AND c.status = 'DRAFT') <> 15
       OR (SELECT count(DISTINCT card_id) FROM public.collection_card) <> 15
       OR (SELECT count(*) FROM public.card AS c
           LEFT JOIN public.collection_card AS cc ON cc.card_id = c.card_id
           WHERE cc.card_id IS NULL) <> 0 THEN
        RAISE EXCEPTION 'Expanded card data failed validation; transaction rolled back';
    END IF;
END
$check$;

COMMIT;
