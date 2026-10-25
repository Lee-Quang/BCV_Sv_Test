-- Replace the three demo image placeholders with direct image URLs.
-- The same URL is stored for the matching PLACE_IMAGE and CARD rows.
-- These are external demo URLs, not Cloudflare R2 object keys.
--
-- 2  Po Nagar Cham Towers: 4pf3154f7, CC BY-SA 3.0
--    https://commons.wikimedia.org/wiki/File:Po_Nagar_Cham_Towers.jpeg
-- 4  Vinpearl Land Nha Trang Ferris wheel: 유자차, CC BY-SA 4.0
--    https://commons.wikimedia.org/wiki/File:Vinpearl_Land_Nha_Trang_Ferris_wheel.jpg
-- 11 Kontum wooden catholic church: Rdavout, CC BY-SA 3.0
--    https://commons.wikimedia.org/wiki/File:Kontum_wooden_catholic_church.jpg
-- Keep these credits with the images if they are shown in an app.

BEGIN;
SET LOCAL client_encoding = 'UTF8';
SET LOCAL search_path = public;

DO $check$
BEGIN
    IF (SELECT count(*) FROM pg_catalog.pg_tables
        WHERE schemaname = 'public' AND tablename <> 'spatial_ref_sys') <> 25 THEN
        RAISE EXCEPTION 'Expected exactly 25 application tables in public';
    END IF;
    IF (SELECT count(*) FROM public.place_image) <> 3
       OR (SELECT count(*) FROM public.card) <> 3
       OR (SELECT count(*) FROM public.place_image
           WHERE (place_image_id, place_id, image_key) IN (
               (900001, 2, 'demo/not-uploaded/place-0002.jpg'),
               (900002, 4, 'demo/not-uploaded/place-0004.jpg'),
               (900003, 11, 'demo/not-uploaded/place-0011.jpg'))) <> 3
       OR (SELECT count(*) FROM public.card
           WHERE card_id IN (900001, 900002, 900003)
             AND image_key IS NULL) <> 3 THEN
        RAISE EXCEPTION 'Demo image data differs from expected snapshot; no changes applied';
    END IF;
END
$check$;

UPDATE public.place_image AS pi
SET image_key = v.image_url
FROM (VALUES
    (900001, 2, 'https://upload.wikimedia.org/wikipedia/commons/b/b5/Po_Nagar_Cham_Towers.jpeg'),
    (900002, 4, 'https://upload.wikimedia.org/wikipedia/commons/a/ad/Vinpearl_Land_Nha_Trang_Ferris_wheel.jpg'),
    (900003, 11, 'https://upload.wikimedia.org/wikipedia/commons/a/ac/Kontum_wooden_catholic_church.jpg')
) AS v(place_image_id, place_id, image_url)
WHERE pi.place_image_id = v.place_image_id AND pi.place_id = v.place_id;

UPDATE public.card AS c
SET image_key = v.image_url
FROM (VALUES
    (900001, 2, 'https://upload.wikimedia.org/wikipedia/commons/b/b5/Po_Nagar_Cham_Towers.jpeg'),
    (900002, 4, 'https://upload.wikimedia.org/wikipedia/commons/a/ad/Vinpearl_Land_Nha_Trang_Ferris_wheel.jpg'),
    (900003, 11, 'https://upload.wikimedia.org/wikipedia/commons/a/ac/Kontum_wooden_catholic_church.jpg')
) AS v(card_id, place_id, image_url)
WHERE c.card_id = v.card_id AND c.place_id = v.place_id;

DO $check$
BEGIN
    IF (SELECT count(*) FROM public.place_image AS pi
        JOIN public.card AS c ON c.place_id = pi.place_id
        WHERE pi.image_key = c.image_key
          AND pi.image_key LIKE 'https://upload.wikimedia.org/%') <> 3
       OR (SELECT count(*) FROM public.place_image) <> 3
       OR (SELECT count(*) FROM public.card) <> 3 THEN
        RAISE EXCEPTION 'Demo image URL update failed; transaction rolled back';
    END IF;
END
$check$;

COMMIT;
