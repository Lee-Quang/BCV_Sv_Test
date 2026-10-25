-- Replace seven generated snippets that contain dataset notes or need
-- disambiguation. Fresh databases receive these directly from script 010.

BEGIN;
SET LOCAL client_encoding = 'UTF8';
SET LOCAL search_path = public;

DO $check$
BEGIN
    IF (SELECT count(*) FROM public.place) <> 311
       OR (SELECT count(*) FROM public.place
           WHERE highlights LIKE 'Bạn có biết? %') <> 311
       OR (SELECT count(*) FROM public.place
           WHERE place_id IN (15, 82, 90, 108, 240, 295, 305)) <> 7 THEN
        RAISE EXCEPTION 'Unexpected place highlight snapshot; no changes applied';
    END IF;
END
$check$;

UPDATE public.place AS p
SET highlights = v.fun_fact
FROM (VALUES
    (15, 'Thác Đăk Sing', 'Bạn có biết? Thác Đăk Sing là điểm thác tự nhiên ở khu vực Kon Đào, Đắk Tô.'),
    (82, 'Bãi biển Mỹ Khê (Quảng Ngãi)', 'Bạn có biết? Bãi biển Mỹ Khê ở Quảng Ngãi khác với bãi biển cùng tên tại Đà Nẵng.'),
    (90, 'Động Thiên Cung (Hạ Long)', 'Bạn có biết? Động Thiên Cung tại vịnh Hạ Long khác với động cùng tên ở Suối Giàng.'),
    (108, 'Hồ Dầu Tiếng', 'Bạn có biết? Hồ Dầu Tiếng là hồ chứa nước trên lưu vực sông Sài Gòn và phục vụ thủy lợi.'),
    (240, 'Động Thiên Cung Suối Giàng', 'Bạn có biết? Động Thiên Cung Suối Giàng ở Yên Bái khác với động Thiên Cung tại vịnh Hạ Long.'),
    (295, 'Chợ Đông Ngô Quyền', 'Bạn có biết? Chợ Đông Ngô Quyền là điểm mua sắm và giao thương quen thuộc của người dân địa phương.'),
    (305, 'Sông Cái Lớn', 'Bạn có biết? Sông Cái Lớn gắn với cảnh quan sông nước và đời sống ven sông ở Tây Nam Bộ.')
) AS v(place_id, place_name, fun_fact)
WHERE p.place_id = v.place_id AND p.name = v.place_name;

DO $check$
BEGIN
    IF (SELECT count(*) FROM public.place
        WHERE highlights LIKE 'Bạn có biết? %') <> 311
       OR (SELECT count(DISTINCT highlights) FROM public.place) <> 311
       OR (SELECT count(*) FROM public.place
           WHERE highlights ILIKE '%dataset%'
              OR highlights ILIKE '%địa giới cũ%'
              OR highlights ILIKE '%dữ liệu gốc%') <> 0 THEN
        RAISE EXCEPTION 'Refined highlight validation failed; transaction rolled back';
    END IF;
END
$check$;

COMMIT;
