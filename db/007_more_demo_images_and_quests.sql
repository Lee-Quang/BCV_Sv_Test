-- Expand the existing BCV demo data. No schema objects are created.
-- Wikimedia Commons thumbnail URLs are direct JPEGs; attribution is in README.md.

BEGIN;
SET LOCAL client_encoding = 'UTF8';
SET LOCAL search_path = public;

DO $check$
BEGIN
    IF (SELECT count(*) FROM pg_catalog.pg_tables
        WHERE schemaname = 'public' AND tablename <> 'spatial_ref_sys') <> 25
       OR (SELECT count(*) FROM public.place_image) <> 3
       OR (SELECT count(*) FROM public.quest) <> 3
       OR (SELECT count(*) FROM public.quest_progress) <> 4
       OR (SELECT count(*) FROM public.item WHERE item_id IN (900001, 900002)) <> 2
       OR (SELECT count(*) FROM public.place WHERE place_id IN
           (6, 21, 31, 51, 71, 76, 86, 126, 138, 206, 211, 282)) <> 12
       OR (SELECT count(*) FROM public.quest WHERE quest_id = 900003
           AND quest_type = 'MILESTONE' AND cycle_type = 'ONCE'
           AND condition_type = 'COMPLETE_JOURNEY') <> 1
       OR (SELECT count(*) FROM public.quest_progress WHERE quest_progress_id = 900002
           AND quest_id = 900003 AND cycle_key = 'ONCE'
           AND completed_at::date = DATE '2026-10-20') <> 1 THEN
        RAISE EXCEPTION 'Unexpected BCV demo snapshot; no changes applied';
    END IF;
END
$check$;

INSERT INTO public.place_image (place_image_id, place_id, image_key) VALUES
    (900004, 76, 'https://thumb.wikimedia.org/wikipedia/commons/thumb/0/06/Hoi_An_Ancient_Town.jpg/960px-Hoi_An_Ancient_Town.jpg'),
    (900005, 86, 'https://thumb.wikimedia.org/wikipedia/commons/thumb/2/2d/Halong_Bay_in_Vietnam.jpg/960px-Halong_Bay_in_Vietnam.jpg'),
    (900006, 206, 'https://thumb.wikimedia.org/wikipedia/commons/thumb/1/13/Ban_Gioc.jpg/960px-Ban_Gioc.jpg'),
    (900007, 282, 'https://thumb.wikimedia.org/wikipedia/commons/thumb/8/83/Ho_Hoan_Kiem.jpg/960px-Ho_Hoan_Kiem.jpg'),
    (900008, 31, 'https://thumb.wikimedia.org/wikipedia/commons/thumb/5/59/Fansipan_summit_new.jpg/960px-Fansipan_summit_new.jpg'),
    (900009, 138, 'https://thumb.wikimedia.org/wikipedia/commons/thumb/8/88/Saigon_Notre-Dame_Basilica.jpg/960px-Saigon_Notre-Dame_Basilica.jpg'),
    (900010, 51, 'https://thumb.wikimedia.org/wikipedia/commons/thumb/1/11/Trang_An_Landscape_Complex%2C_Ninh_Binh_Province%2C_Vietnam%2C_20240202_1524_5364.jpg/960px-Trang_An_Landscape_Complex%2C_Ninh_Binh_Province%2C_Vietnam%2C_20240202_1524_5364.jpg'),
    (900011, 6, 'https://thumb.wikimedia.org/wikipedia/commons/thumb/b/bf/Phu_Quoc_Beach.jpg/960px-Phu_Quoc_Beach.jpg'),
    (900012, 21, 'https://thumb.wikimedia.org/wikipedia/commons/thumb/3/3a/Xuan_Huong_Lake_in_Da_Lat_%2828219543381%29.jpg/960px-Xuan_Huong_Lake_in_Da_Lat_%2828219543381%29.jpg'),
    (900013, 211, 'https://thumb.wikimedia.org/wikipedia/commons/thumb/a/a5/The_Golden_Bridge%2C_Ba_Na_Hills%2C_Vietnam.jpg/960px-The_Golden_Bridge%2C_Ba_Na_Hills%2C_Vietnam.jpg'),
    (900014, 71, 'https://thumb.wikimedia.org/wikipedia/commons/thumb/b/bd/Phong_Nha_cave_entrance.jpg/960px-Phong_Nha_cave_entrance.jpg'),
    (900015, 126, 'https://thumb.wikimedia.org/wikipedia/commons/thumb/d/d7/Imperial_City%2C_Hue%2C_Vietnam_%2849575145062%29.jpg/960px-Imperial_City%2C_Hue%2C_Vietnam_%2849575145062%29.jpg');

-- The thesis defines repeatable daily and weekly quests. Keep the completed
-- sample's date in its weekly cycle rather than its former one-time cycle.
UPDATE public.quest
SET quest_type = 'WEEKLY', cycle_type = 'WEEKLY'
WHERE quest_id = 900003;

UPDATE public.quest_progress
SET cycle_key = '2026-W43'
WHERE quest_progress_id = 900002;

INSERT INTO public.quest
    (quest_id, reward_item_id, name, quest_type, condition_type,
     target_value, cycle_type, reward_quantity, status) VALUES
    (900004, 900001, 'Đi 2.000 bước mỗi ngày', 'DAILY', 'STEP_COUNT',
     2000, 'DAILY', 1, 'DRAFT'),
    (900005, 900001, 'Đi 8.000 bước mỗi ngày', 'DAILY', 'STEP_COUNT',
     8000, 'DAILY', 1, 'DRAFT'),
    (900006, 900002, 'Đi 12.000 bước mỗi ngày', 'DAILY', 'STEP_COUNT',
     12000, 'DAILY', 1, 'DRAFT'),
    (900007, 900001, 'Đi 30.000 bước trong tuần', 'WEEKLY', 'STEP_COUNT',
     30000, 'WEEKLY', 2, 'DRAFT'),
    (900008, 900002, 'Đi 50.000 bước trong tuần', 'WEEKLY', 'STEP_COUNT',
     50000, 'WEEKLY', 1, 'DRAFT'),
    (900009, 900001, 'Khám phá 1 địa danh mỗi ngày', 'DAILY', 'DISCOVER_PLACE',
     1, 'DAILY', 1, 'DRAFT'),
    (900010, 900002, 'Khám phá 3 địa danh trong tuần', 'WEEKLY', 'DISCOVER_PLACE',
     3, 'WEEKLY', 1, 'DRAFT'),
    (900011, 900002, 'Hoàn thành 2 hành trình trong tuần', 'WEEKLY', 'COMPLETE_JOURNEY',
     2, 'WEEKLY', 1, 'DRAFT');

DO $check$
BEGIN
    IF (SELECT count(*) FROM public.place_image) <> 15
       OR (SELECT count(DISTINCT place_id) FROM public.place_image) <> 15
       OR (SELECT count(*) FROM public.quest) <> 11
       OR (SELECT count(*) FROM public.quest WHERE quest_type = cycle_type
           AND quest_type IN ('DAILY', 'WEEKLY')
           AND reward_item_id IN (900001, 900002)
           AND reward_quantity > 0 AND status = 'DRAFT') <> 11
       OR (SELECT count(*) FROM public.quest_progress WHERE quest_progress_id = 900002
           AND cycle_key = '2026-W43' AND completion_status = TRUE) <> 1
       OR (SELECT count(*) FROM public.place_image AS pi
           LEFT JOIN public.place AS p ON p.place_id = pi.place_id
           WHERE p.place_id IS NULL) <> 0
       OR (SELECT count(*) FROM public.quest AS q
           LEFT JOIN public.item AS i ON i.item_id = q.reward_item_id
           WHERE i.item_id IS NULL) <> 0 THEN
        RAISE EXCEPTION 'Expanded demo data failed validation; transaction rolled back';
    END IF;
END
$check$;

COMMIT;
