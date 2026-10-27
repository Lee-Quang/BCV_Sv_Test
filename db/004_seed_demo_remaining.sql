-- BCV sample data for the 17 application tables still empty after 003_import_places.sql.
-- Uses only the existing 25-table schema. All IDs in this file are reserved demo IDs.
-- Demo email addresses use the non-deliverable .invalid domain. Passwords are random
-- bcrypt hashes with no retained plaintext, so these accounts are not login fixtures.
-- Demo image_key values are direct Wikimedia Commons JPEG URLs, not R2 object keys.
-- See README.md (Nguồn ảnh và giấy phép) for source pages and image attribution.
-- Run once against bcv after 001, 002, and 003. Existing target rows cause an error.

BEGIN;
SET LOCAL client_encoding = 'UTF8';
SET LOCAL search_path = public;

DO $check$
BEGIN
    IF (SELECT count(*) FROM pg_catalog.pg_tables
        WHERE schemaname = 'public' AND tablename <> 'spatial_ref_sys') <> 25 THEN
        RAISE EXCEPTION 'Expected exactly 25 application tables in public';
    END IF;
    IF (SELECT count(*) FROM public.place
        WHERE place_id IN (2, 3, 4, 11, 12)) <> 5 THEN
        RAISE EXCEPTION 'Required place rows from 003_import_places.sql are missing';
    END IF;
    IF (SELECT count(*) FROM public.place_type
        WHERE name IN ('Thiên nhiên', 'Di tích lịch sử',
                       'Tôn giáo và tín ngưỡng', 'Khu vui chơi và nghỉ dưỡng')) <> 4 THEN
        RAISE EXCEPTION 'Required place_type rows are missing';
    END IF;
    IF (SELECT count(*) FROM public.app_user) <> 0
       OR (SELECT count(*) FROM public.user_preference) <> 0
       OR (SELECT count(*) FROM public.user_preference_place_type) <> 0
       OR (SELECT count(*) FROM public.step_data) <> 0
       OR (SELECT count(*) FROM public.virtual_location) <> 0
       OR (SELECT count(*) FROM public.place_image) <> 0
       OR (SELECT count(*) FROM public.journey) <> 0
       OR (SELECT count(*) FROM public.user_discovered_place) <> 0
       OR (SELECT count(*) FROM public.card) <> 0
       OR (SELECT count(*) FROM public.collection) <> 0
       OR (SELECT count(*) FROM public.collection_card) <> 0
       OR (SELECT count(*) FROM public.user_card) <> 0
       OR (SELECT count(*) FROM public.item) <> 0
       OR (SELECT count(*) FROM public.user_item) <> 0
       OR (SELECT count(*) FROM public.quest) <> 0
       OR (SELECT count(*) FROM public.quest_progress) <> 0
       OR (SELECT count(*) FROM public.notification) <> 0 THEN
        RAISE EXCEPTION 'Demo import targets must be empty; existing rows were left unchanged';
    END IF;
END
$check$;

INSERT INTO public.app_user
    (user_id, full_name, email, password_hash, role, status) VALUES
    (900001, 'An Demo', 'an.demo@example.invalid',
     '$2b$12$/ZOcTmrYw0dPC3jnEkpLbeqE1r.RXQ105aR/SUoJ34.hZtC4rGCN2', 'USER', 'ACTIVE'),
    (900002, 'Bình Demo', 'binh.demo@example.invalid',
     '$2b$12$6w0GVjf8GBMdQB2bMhxoWe60UkRMazG6z.KT4ktbrad92pNKRfQdi', 'USER', 'ACTIVE'),
    (900003, 'Chi Demo', 'chi.demo@example.invalid',
     '$2b$12$ej6uVrTheqrrevAErdo.R.Fqm8qT9qSGanIzW7fOXHx86xvRHsFjm', 'USER', 'ACTIVE');

INSERT INTO public.user_preference
    (user_preference_id, user_id, activity_level) VALUES
    (900001, 900001, 'LOW'),
    (900002, 900002, 'MEDIUM'),
    (900003, 900003, 'HIGH');

INSERT INTO public.user_preference_place_type
    (user_preference_id, place_type_id)
SELECT v.user_preference_id, pt.place_type_id
FROM (VALUES
    (900001, 'Di tích lịch sử'),
    (900001, 'Tôn giáo và tín ngưỡng'),
    (900002, 'Thiên nhiên'),
    (900002, 'Khu vui chơi và nghỉ dưỡng'),
    (900003, 'Thiên nhiên'),
    (900003, 'Di tích lịch sử')
) AS v(user_preference_id, place_type_name)
JOIN public.place_type AS pt ON pt.name = v.place_type_name;

INSERT INTO public.step_data
    (step_data_id, user_id, source_data_id, step_count, start_time, end_time, synced_at) VALUES
    (900001, 900001, 'demo-an-20261020-am', 8200,
     '2026-10-20 06:00:00', '2026-10-20 09:00:00', '2026-10-20 09:05:00'),
    (900002, 900001, 'demo-an-20261020-pm', 7800,
     '2026-10-20 09:30:00', '2026-10-20 12:30:00', '2026-10-20 12:35:00'),
    (900003, 900002, 'demo-binh-20261023-am', 1100,
     '2026-10-23 07:00:00', '2026-10-23 08:00:00', '2026-10-23 08:05:00'),
    (900004, 900002, 'demo-binh-20261023-pm', 700,
     '2026-10-23 09:00:00', '2026-10-23 10:00:00', '2026-10-23 10:05:00'),
    (900005, 900003, 'demo-chi-20261022-am', 450,
     '2026-10-22 08:00:00', '2026-10-22 08:30:00', '2026-10-22 08:35:00'),
    (900006, 900003, 'demo-chi-20261022-pm', 550,
     '2026-10-22 09:00:00', '2026-10-22 09:30:00', '2026-10-22 09:35:00');

INSERT INTO public.virtual_location
    (virtual_location_id, user_id, region_id, nearest_place_id, latitude, longitude)
SELECT v.virtual_location_id, v.user_id, p.region_id, p.place_id, p.latitude, p.longitude
FROM (VALUES (900001, 900001, 4), (900002, 900002, 11), (900003, 900003, 2))
    AS v(virtual_location_id, user_id, place_id)
JOIN public.place AS p ON p.place_id = v.place_id;

INSERT INTO public.place_image (place_image_id, place_id, image_key) VALUES
    (900001, 2, 'https://upload.wikimedia.org/wikipedia/commons/b/b5/Po_Nagar_Cham_Towers.jpeg'),
    (900002, 4, 'https://upload.wikimedia.org/wikipedia/commons/a/ad/Vinpearl_Land_Nha_Trang_Ferris_wheel.jpg'),
    (900003, 11, 'https://upload.wikimedia.org/wikipedia/commons/a/ac/Kontum_wooden_catholic_church.jpg'),
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

WITH routes AS (
    SELECT v.journey_id, v.user_id, a.place_id AS start_place_id,
           a.latitude AS start_latitude, a.longitude AS start_longitude,
           b.place_id AS destination_place_id, v.status, v.sample_recorded_steps,
           v.started_at, v.completed_at,
           ST_Distance(
               ST_SetSRID(ST_MakePoint(a.longitude::double precision,
                                       a.latitude::double precision), 4326)::geography,
               ST_SetSRID(ST_MakePoint(b.longitude::double precision,
                                       b.latitude::double precision), 4326)::geography
           ) AS distance_m
    FROM (VALUES
        (900001, 900001, 4, 2, 'COMPLETED', 0,
         '2026-10-20 06:00:00'::timestamp, '2026-10-20 12:00:00'::timestamp),
        (900002, 900002, 11, 12, 'IN_PROGRESS', 200,
         '2026-10-23 09:00:00'::timestamp, NULL::timestamp),
        (900003, 900003, 2, 3, 'ABANDONED', 600,
         '2026-10-22 08:00:00'::timestamp, NULL::timestamp)
    ) AS v(journey_id, user_id, start_place_id, destination_place_id,
           status, sample_recorded_steps, started_at, completed_at)
    JOIN public.place AS a ON a.place_id = v.start_place_id
    JOIN public.place AS b ON b.place_id = v.destination_place_id
)
INSERT INTO public.journey
    (journey_id, user_id, start_place_id, start_latitude, start_longitude,
     destination_place_id, distance, required_steps, recorded_steps,
     status, started_at, completed_at)
SELECT journey_id, user_id, start_place_id, start_latitude, start_longitude,
       destination_place_id, round(distance_m::numeric, 2),
       greatest(1, ceil(distance_m * 1.0 / 0.75)::integer),
       CASE WHEN status = 'COMPLETED'
            THEN greatest(1, ceil(distance_m * 1.0 / 0.75)::integer)
            ELSE sample_recorded_steps END,
       status, started_at, completed_at
FROM routes;

INSERT INTO public.user_discovered_place
    (user_discovered_place_id, user_id, place_id, discovered_at) VALUES
    (900001, 900001, 4, '2026-10-20 06:00:00'),
    (900002, 900001, 2, '2026-10-20 12:00:00'),
    (900003, 900002, 11, '2026-10-23 08:00:00'),
    (900004, 900003, 2, '2026-10-22 07:30:00');

INSERT INTO public.card
    (card_id, place_id, name, image_key, description, status)
SELECT v.card_id, p.place_id, p.name, v.image_url,
       'Thẻ sưu tầm dữ liệu mẫu cho địa danh ' || p.name, 'DRAFT'
FROM (VALUES
    (900001, 2, 'https://upload.wikimedia.org/wikipedia/commons/b/b5/Po_Nagar_Cham_Towers.jpeg'),
    (900002, 4, 'https://upload.wikimedia.org/wikipedia/commons/a/ad/Vinpearl_Land_Nha_Trang_Ferris_wheel.jpg'),
    (900003, 11, 'https://upload.wikimedia.org/wikipedia/commons/a/ac/Kontum_wooden_catholic_church.jpg')
) AS v(card_id, place_id, image_url)
JOIN public.place AS p ON p.place_id = v.place_id;

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

INSERT INTO public.collection
    (collection_id, name, description, status) VALUES
    (900001, 'Di sản và kiến trúc mẫu',
     'Bộ sưu tập thử nghiệm các điểm văn hóa và kiến trúc.', 'DRAFT'),
    (900002, 'Vui chơi và khám phá mẫu',
     'Bộ sưu tập thử nghiệm các điểm tham quan và giải trí.', 'DRAFT');

INSERT INTO public.collection_card
    (collection_card_id, collection_id, card_id) VALUES
    (900001, 900001, 900001),
    (900002, 900001, 900003),
    (900003, 900002, 900002),
    (900004, 900001, 900004),
    (900005, 900002, 900005),
    (900006, 900002, 900006),
    (900007, 900001, 900007),
    (900008, 900002, 900008),
    (900009, 900001, 900009),
    (900010, 900002, 900010),
    (900011, 900002, 900011),
    (900012, 900002, 900012),
    (900013, 900002, 900013),
    (900014, 900002, 900014),
    (900015, 900001, 900015);

INSERT INTO public.user_card
    (user_card_id, user_id, card_id, received_at) VALUES
    (900001, 900001, 900001, '2026-10-20 12:05:00'),
    (900002, 900001, 900002, '2026-10-20 06:05:00'),
    (900003, 900002, 900003, '2026-10-23 08:05:00');

INSERT INTO public.item
    (item_id, name, item_type, description, effect_value,
     effective_range, usage_limit, status) VALUES
    (900001, 'Giày tăng tốc', 'SPEED_SHOES',
     'Nhân đôi số bước tính vào tiến độ hành trình trong tối đa 5.000 bước thực tế sau khi kích hoạt.',
     2.00, 5000, 1, 'DRAFT'),
    (900002, 'Vé máy bay', 'FLIGHT_TICKET',
     'Chuyển vị trí ảo đến địa danh thuộc vùng hợp lệ; chỉ trừ vé khi cập nhật vị trí thành công.',
     NULL, NULL, 1, 'DRAFT');

INSERT INTO public.user_item
    (user_item_id, user_id, item_id, quantity) VALUES
    (900001, 900001, 900001, 1),
    (900002, 900001, 900002, 1),
    (900003, 900002, 900002, 2);

INSERT INTO public.quest
    (quest_id, reward_item_id, name, quest_type, condition_type,
     target_value, cycle_type, reward_quantity, status) VALUES
    (900001, 900001, 'Đi 5.000 bước mẫu', 'DAILY', 'STEP_COUNT',
     5000, 'DAILY', 1, 'DRAFT'),
    (900002, 900002, 'Khám phá 2 địa danh mẫu', 'WEEKLY', 'DISCOVER_PLACE',
     2, 'WEEKLY', 1, 'DRAFT'),
    (900003, 900002, 'Hoàn thành 1 hành trình mẫu', 'WEEKLY', 'COMPLETE_JOURNEY',
     1, 'WEEKLY', 1, 'DRAFT'),
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

INSERT INTO public.quest_progress
    (quest_progress_id, user_id, quest_id, cycle_key, progress_value,
     completion_status, completed_at, reward_status) VALUES
    (900001, 900001, 900001, '2026-10-20', 5000,
     TRUE, '2026-10-20 09:00:00', TRUE),
    (900002, 900001, 900003, '2026-W43', 1,
     TRUE, '2026-10-20 12:00:00', TRUE),
    (900003, 900002, 900001, '2026-10-23', 1200,
     FALSE, NULL, FALSE),
    (900004, 900003, 900002, '2026-W43', 1,
     FALSE, NULL, FALSE);

INSERT INTO public.notification
    (notification_id, user_id, notification_type, title, content,
     read_status, created_at) VALUES
    (900001, 900001, 'JOURNEY_COMPLETED', 'Hoàn thành hành trình mẫu',
     'Bạn đã hoàn thành hành trình đến Tháp Bà Ponagar.', TRUE,
     '2026-10-20 12:01:00'),
    (900002, 900001, 'QUEST_REWARD', 'Nhận vật phẩm mẫu',
     'Bạn đã nhận phần thưởng từ nhiệm vụ đi bộ mẫu.', FALSE,
     '2026-10-20 12:02:00'),
    (900003, 900002, 'QUEST_PROGRESS', 'Tiến độ nhiệm vụ mẫu',
     'Bạn đã đi được 1.200 trên 5.000 bước.', FALSE,
     '2026-10-23 10:06:00'),
    (900004, 900003, 'PLACE_DISCOVERED', 'Khám phá địa danh mẫu',
     'Bạn đã khám phá Tháp Bà Ponagar.', FALSE,
     '2026-10-22 07:31:00');

DO $check$
BEGIN
    IF (SELECT count(*) FROM public.app_user) <> 3
       OR (SELECT count(*) FROM public.user_preference) <> 3
       OR (SELECT count(*) FROM public.user_preference_place_type) <> 6
       OR (SELECT count(*) FROM public.step_data) <> 6
       OR (SELECT count(*) FROM public.virtual_location) <> 3
       OR (SELECT count(*) FROM public.place_image) <> 15
       OR (SELECT count(*) FROM public.journey) <> 3
       OR (SELECT count(*) FROM public.user_discovered_place) <> 4
       OR (SELECT count(*) FROM public.card) <> 15
       OR (SELECT count(*) FROM public.collection) <> 2
       OR (SELECT count(*) FROM public.collection_card) <> 15
       OR (SELECT count(*) FROM public.user_card) <> 3
       OR (SELECT count(*) FROM public.item) <> 2
       OR (SELECT count(*) FROM public.user_item) <> 3
       OR (SELECT count(*) FROM public.quest) <> 11
       OR (SELECT count(*) FROM public.quest_progress) <> 4
       OR (SELECT count(*) FROM public.notification) <> 4 THEN
        RAISE EXCEPTION 'Demo row-count check failed; transaction rolled back';
    END IF;
    IF (SELECT count(*) FROM public.journey WHERE status = 'IN_PROGRESS') <> 1
       OR (SELECT count(*) FROM public.journey WHERE status = 'COMPLETED'
           AND recorded_steps >= required_steps) <> 1 THEN
        RAISE EXCEPTION 'Demo journey consistency check failed; transaction rolled back';
    END IF;
END
$check$;

COMMIT;
