-- Populate PLACE.highlights for all 311 imported places.
-- Thirty-seven places receive hand-written facts based on the supplied
-- introductions. The others get a concise first fact from their introduction.
-- No new tables or columns are created.

BEGIN;
SET LOCAL client_encoding = 'UTF8';
SET LOCAL search_path = public;

DO $check$
BEGIN
    IF (SELECT count(*) FROM pg_catalog.pg_tables
        WHERE schemaname = 'public' AND tablename <> 'spatial_ref_sys') <> 25
       OR (SELECT count(*) FROM public.place) <> 311
       OR (SELECT count(*) FROM public.place
           WHERE highlights IS NULL OR btrim(highlights) = '') <> 311
       OR (SELECT count(*) FROM public.place
           WHERE introduction IS NULL OR btrim(introduction) = '') <> 0 THEN
        RAISE EXCEPTION 'Unexpected place snapshot; no highlights were changed';
    END IF;
END
$check$;

WITH curated(place_id, fun_fact) AS (VALUES
    (2, 'Bạn có biết? Tháp Bà Ponagar là công trình Chăm cổ lưu giữ dấu ấn văn hóa Chăm ở Nha Trang.'),
    (4, 'Bạn có biết? VinWonders Nha Trang nằm trên đảo Hòn Tre.'),
    (5, 'Bạn có biết? Suối Ba Hồ có ba hồ nước trong xanh nằm trên núi.'),
    (6, 'Bạn có biết? Phú Quốc còn được gọi là Đảo Ngọc.'),
    (11, 'Bạn có biết? Nhà Thờ Gỗ Kon Tum kết hợp phong cách Roman với nét văn hóa Tây Nguyên.'),
    (15, 'Bạn có biết? Thác Đăk Sing là điểm thác tự nhiên ở khu vực Kon Đào, Đắk Tô.'),
    (16, 'Bạn có biết? Đèo Ô Quy Hồ nối Lào Cai và Lai Châu, thuộc nhóm Tứ đại đỉnh đèo của Tây Bắc.'),
    (21, 'Bạn có biết? Hồ Xuân Hương nằm ngay trung tâm Đà Lạt, giữa rừng thông và những vườn hoa.'),
    (26, 'Bạn có biết? Trong động Tam Thanh có tượng Phật A Di Đà được tạc từ thế kỷ 15.'),
    (31, 'Bạn có biết? Fansipan được mệnh danh là Nóc nhà Đông Dương.'),
    (51, 'Bạn có biết? Tràng An là di sản thế giới kết hợp giá trị văn hóa và cảnh quan thiên nhiên.'),
    (52, 'Bạn có biết? Tam Cốc được mệnh danh là vịnh Hạ Long trên cạn.'),
    (53, 'Bạn có biết? Hoa Lư từng là kinh đô Đại Cồ Việt dưới triều Đinh và Tiền Lê.'),
    (66, 'Bạn có biết? Các cột đá bazan ở Gành Đá Đĩa xếp chồng như những chiếc đĩa.'),
    (71, 'Bạn có biết? Động Phong Nha thuộc khu vực Phong Nha - Kẻ Bàng, Di sản Thiên nhiên Thế giới.'),
    (76, 'Bạn có biết? Phố cổ Hội An nổi bật với những bức tường vàng và các ngôi nhà cổ bên sông Hoài.'),
    (82, 'Bạn có biết? Bãi biển Mỹ Khê ở Quảng Ngãi khác với bãi biển cùng tên tại Đà Nẵng.'),
    (86, 'Bạn có biết? Vịnh Hạ Long có gần 2.000 đảo lớn nhỏ và nhiều hang động đá vôi.'),
    (90, 'Bạn có biết? Động Thiên Cung tại vịnh Hạ Long khác với động cùng tên ở Suối Giàng.'),
    (92, 'Bạn có biết? Bên trong địa đạo Vịnh Mốc từng có cả trường học, bệnh viện và khu sinh hoạt gia đình.'),
    (108, 'Bạn có biết? Hồ Dầu Tiếng là hồ chứa nước trên lưu vực sông Sài Gòn và phục vụ thủy lợi.'),
    (126, 'Bạn có biết? Đại Nội Huế từng là trung tâm hành chính và nơi sinh hoạt của hoàng gia triều Nguyễn.'),
    (127, 'Bạn có biết? Tháp Phước Duyên bảy tầng đứng ở sân trước chùa Thiên Mụ.'),
    (128, 'Bạn có biết? Lăng Khải Định kết hợp kiến trúc Đông - Tây với nghệ thuật trang trí sành sứ, thủy tinh.'),
    (138, 'Bạn có biết? Nhà thờ Đức Bà Sài Gòn nằm ở trung tâm Quận 1 và mang dấu ấn kiến trúc châu Âu.'),
    (156, 'Bạn có biết? Hồ Ba Bể là hồ nước ngọt tự nhiên được bao quanh bởi núi đá vôi và rừng.'),
    (201, 'Bạn có biết? Ở chợ nổi Cái Răng, hoạt động mua bán diễn ra ngay trên thuyền.'),
    (206, 'Bạn có biết? Thác Bản Giốc nằm trên biên giới Việt Nam - Trung Quốc và được chia thành nhiều tầng.'),
    (211, 'Bạn có biết? Bà Nà Hills có Cầu Vàng và khu vui chơi trong nhà Fantasy Park.'),
    (240, 'Bạn có biết? Động Thiên Cung Suối Giàng ở Yên Bái khác với động Thiên Cung tại vịnh Hạ Long.'),
    (251, 'Bạn có biết? Đồi A1 là một địa điểm gắn với những trận đánh trong chiến dịch Điện Biên Phủ.'),
    (261, 'Bạn có biết? Vườn quốc gia Tràm Chim là nơi sinh sống của sếu đầu đỏ.'),
    (262, 'Bạn có biết? Làng hoa Sa Đéc trồng hàng trăm loài hoa và cây cảnh.'),
    (282, 'Bạn có biết? Hồ Gươm gắn với hai hình ảnh quen thuộc của Hà Nội: Tháp Rùa và cầu Thê Húc.'),
    (284, 'Bạn có biết? Hoàng thành Thăng Long từng là trung tâm quyền lực qua nhiều triều đại.'),
    (295, 'Bạn có biết? Chợ Đông Ngô Quyền là điểm mua sắm và giao thương quen thuộc của người dân địa phương.'),
    (305, 'Bạn có biết? Sông Cái Lớn gắn với cảnh quan sông nước và đời sống ven sông ở Tây Nam Bộ.')
),
drafts AS (
    SELECT p.place_id,
           COALESCE(
               c.fun_fact,
               'Bạn có biết? ' ||
               CASE
                   WHEN s.snippet ~* '^Là[[:space:]]+' THEN
                       p.name || ' ' || lower(left(s.snippet, 1)) || substr(s.snippet, 2)
                   WHEN s.snippet ~* '^Một[[:space:]]+' THEN
                       p.name || ' là ' || lower(left(s.snippet, 1)) || substr(s.snippet, 2)
                   WHEN s.snippet ~* '^Nơi[[:space:]]+' THEN
                       p.name || ' là ' || lower(left(s.snippet, 1)) || substr(s.snippet, 2)
                   WHEN s.snippet ~* '^Được[[:space:]]+' THEN
                       p.name || ' ' || lower(left(s.snippet, 1)) || substr(s.snippet, 2)
                   WHEN lower(left(s.snippet, length(p.name))) = lower(p.name) THEN
                       s.snippet
                   ELSE p.name || ' — ' || s.snippet
               END || '.'
           ) AS fun_fact
    FROM public.place AS p
    LEFT JOIN curated AS c ON c.place_id = p.place_id
    CROSS JOIN LATERAL (
        SELECT rtrim(
            regexp_replace(btrim(split_part(p.introduction, '. ', 1)),
                           '[[:space:]]+', ' ', 'g'), ' .') AS snippet
    ) AS s
)
UPDATE public.place AS p
SET highlights = d.fun_fact
FROM drafts AS d
WHERE p.place_id = d.place_id;

DO $check$
BEGIN
    IF (SELECT count(*) FROM public.place
        WHERE highlights LIKE 'Bạn có biết? %'
          AND length(btrim(highlights)) BETWEEN 40 AND 370) <> 311
       OR (SELECT count(DISTINCT highlights) FROM public.place) <> 311 THEN
        RAISE EXCEPTION 'Place highlight validation failed; transaction rolled back';
    END IF;
END
$check$;

COMMIT;
