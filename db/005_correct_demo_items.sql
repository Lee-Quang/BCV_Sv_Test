-- Correct the already imported demo ITEM data to match the thesis:
-- Giày tăng tốc and Vé máy bay are the two supported item types.
-- 2x over 5,000 actual steps and one use are illustrative demo settings.
-- The related USER_ITEM and QUEST rows are retargeted before removing item 900003.
-- No tables or columns are created.

BEGIN;
SET LOCAL client_encoding = 'UTF8';
SET LOCAL search_path = public;

DO $check$
BEGIN
    IF (SELECT count(*) FROM pg_catalog.pg_tables
        WHERE schemaname = 'public' AND tablename <> 'spatial_ref_sys') <> 25 THEN
        RAISE EXCEPTION 'Expected exactly 25 application tables in public';
    END IF;
    IF (SELECT count(*) FROM public.item
        WHERE item_id IN (900001, 900002, 900003)) <> 3
       OR (SELECT count(*) FROM public.item) <> 3
       OR (SELECT count(*) FROM public.user_item
           WHERE user_item_id = 900002 AND user_id = 900001 AND item_id = 900003) <> 1
       OR (SELECT count(*) FROM public.quest
           WHERE quest_id = 900003 AND reward_item_id = 900003) <> 1 THEN
        RAISE EXCEPTION 'Demo item data differs from expected snapshot; no changes applied';
    END IF;
END
$check$;

UPDATE public.item
SET name = 'Giày tăng tốc',
    item_type = 'SPEED_SHOES',
    description = 'Nhân đôi số bước tính vào tiến độ hành trình trong tối đa 5.000 bước thực tế sau khi kích hoạt.',
    effect_value = 2.00,
    effective_range = 5000,
    usage_limit = 1,
    status = 'DRAFT'
WHERE item_id = 900001;

UPDATE public.item
SET name = 'Vé máy bay',
    item_type = 'FLIGHT_TICKET',
    description = 'Chuyển vị trí ảo đến địa danh thuộc vùng hợp lệ; chỉ trừ vé khi cập nhật vị trí thành công.',
    effect_value = NULL,
    effective_range = NULL,
    usage_limit = 1,
    status = 'DRAFT'
WHERE item_id = 900002;

UPDATE public.user_item
SET item_id = 900002
WHERE user_item_id = 900002 AND user_id = 900001 AND item_id = 900003;

UPDATE public.quest
SET reward_item_id = 900002
WHERE quest_id = 900003 AND reward_item_id = 900003;

DELETE FROM public.item WHERE item_id = 900003;

DO $check$
BEGIN
    IF (SELECT count(*) FROM public.item) <> 2
       OR (SELECT count(*) FROM public.item
           WHERE (item_id = 900001 AND name = 'Giày tăng tốc'
                  AND item_type = 'SPEED_SHOES' AND effect_value = 2.00
                  AND effective_range = 5000 AND usage_limit = 1)
              OR (item_id = 900002 AND name = 'Vé máy bay'
                  AND item_type = 'FLIGHT_TICKET' AND effect_value IS NULL
                  AND effective_range IS NULL AND usage_limit = 1)) <> 2
       OR (SELECT count(*) FROM public.user_item WHERE item_id = 900003) <> 0
       OR (SELECT count(*) FROM public.quest WHERE reward_item_id = 900003) <> 0
       OR (SELECT count(*) FROM public.user_item) <> 3
       OR (SELECT count(*) FROM public.quest) <> 3 THEN
        RAISE EXCEPTION 'Demo item correction failed; transaction rolled back';
    END IF;
END
$check$;

COMMIT;
