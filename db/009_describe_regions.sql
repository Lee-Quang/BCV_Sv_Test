-- Fill descriptions for the 63 region rows in the supplied BCV dataset.
-- These descriptions refer to places already present in the database.
-- No schema objects are created.

BEGIN;
SET LOCAL client_encoding = 'UTF8';
SET LOCAL search_path = public;

DO $check$
BEGIN
    IF (SELECT count(*) FROM pg_catalog.pg_tables
        WHERE schemaname = 'public' AND tablename <> 'spatial_ref_sys') <> 25
       OR (SELECT count(*) FROM public.region) <> 63
       OR (SELECT count(*) FROM public.region
           WHERE description IS NULL OR btrim(description) = '') <> 63
       OR (SELECT min(region_id) FROM public.region) <> 1
       OR (SELECT max(region_id) FROM public.region) <> 63 THEN
        RAISE EXCEPTION 'Unexpected region snapshot; no descriptions were changed';
    END IF;
END
$check$;

WITH descriptions(region_id, region_name, description) AS (VALUES
    (1, 'An Giang', 'An Giang kết hợp cảnh quan rừng tràm Trà Sư với dấu ấn tín ngưỡng tại Miếu Bà Chúa Xứ và các điểm tham quan tâm linh.'),
    (2, 'Bà Rịa - Vũng Tàu', 'Bà Rịa - Vũng Tàu có Bãi Sau, núi Dinh và Côn Đảo, phù hợp hành trình khám phá biển, núi và di tích.'),
    (3, 'Bình Dương', 'Bình Dương có khu du lịch Đại Nam cùng các điểm văn hóa, tín ngưỡng như chùa Châu Thới và chùa Tây Tạng.'),
    (4, 'Bình Phước', 'Bình Phước gợi mở hành trình giữa thiên nhiên Bù Gia Mập, núi Bà Rá và khu di tích lịch sử Tà Thiết.'),
    (5, 'Bình Thuận', 'Bình Thuận nổi bật với làng chài Mũi Né, đồi cát Bàu Trắng và hải đăng Kê Gà trên tuyến khám phá ven biển.'),
    (6, 'Bình Định', 'Bình Định có biển Kỳ Co, bãi tắm Quy Nhơn và Hòn Khô, mang đến nhiều điểm dừng chân bên biển.'),
    (7, 'Bạc Liêu', 'Bạc Liêu lưu giữ câu chuyện địa phương qua Nhà Công tử Bạc Liêu, đồng thời có các điểm tín ngưỡng như chùa Quan Đế.'),
    (8, 'Bắc Giang', 'Bắc Giang kết hợp không gian chùa Bổ Đà với cảnh quan hồ Cấm Sơn và những điểm tham quan thiên nhiên.'),
    (9, 'Bắc Kạn', 'Bắc Kạn hấp dẫn với hồ Ba Bể, động Puông và thác Đầu Đẳng trong hành trình khám phá núi rừng.'),
    (10, 'Bắc Ninh', 'Bắc Ninh có đền Bà Chúa Kho, chùa Phật Tích và chùa Bút Tháp, thích hợp cho hành trình tìm hiểu di sản tín ngưỡng.'),
    (11, 'Bến Tre', 'Bến Tre có Cồn Phụng và khu lăng mộ Nguyễn Đình Chiểu, kết hợp trải nghiệm miệt vườn với dấu ấn văn hóa.'),
    (12, 'Cao Bằng', 'Cao Bằng nổi bật với thác Bản Giốc, động Ngườm Ngao và cảnh quan núi Mắt Thần.'),
    (13, 'Cà Mau', 'Cà Mau có Vườn quốc gia U Minh Hạ, đầm Thị Tường và Hòn Đá Bạc cho hành trình khám phá sông nước, rừng và biển.'),
    (14, 'Cần Thơ', 'Cần Thơ gắn với chợ nổi Cái Răng, Chùa Ông và các điểm du lịch sinh thái ven sông.'),
    (15, 'Gia Lai', 'Gia Lai có Biển Hồ, chùa Minh Thành và quảng trường Đại Đoàn Kết, kết hợp cảnh quan cao nguyên với nhịp sống đô thị.'),
    (16, 'Hà Giang', 'Hà Giang nổi bật với cột cờ Lũng Cú, phố cổ Đồng Văn và đèo Mã Pí Lèng trên các cung đường núi.'),
    (17, 'Hà Nam', 'Hà Nam có chùa Tam Chúc, đền Trần Thương và đền Lảnh Giang trong tuyến tham quan văn hóa, tín ngưỡng.'),
    (18, 'Hà Nội', 'Hà Nội quy tụ Hồ Gươm, Văn Miếu - Quốc Tử Giám và Hoàng thành Thăng Long, nơi du khách tìm hiểu lịch sử và đời sống thủ đô.'),
    (19, 'Hà Tĩnh', 'Hà Tĩnh kết hợp điểm đến lịch sử Ngã ba Đồng Lộc với chùa Hương Tích và biển Thiên Cầm.'),
    (20, 'Hòa Bình', 'Hòa Bình có động Đá Bạc, Cửu thác Tú Sơn và các điểm tham quan núi rừng phù hợp cho chuyến đi thiên nhiên.'),
    (21, 'Hưng Yên', 'Hưng Yên có chùa Chuông, đền Phù Ủng và đình làng Nôm, gợi hành trình khám phá kiến trúc và văn hóa làng.'),
    (22, 'Hải Dương', 'Hải Dương có đền Kiếp Bạc, chùa Côn Sơn và Đảo Cò Chi Lăng Nam, kết hợp di sản với cảnh quan tự nhiên.'),
    (23, 'Hải Phòng', 'Hải Phòng có Cát Bà, Đồ Sơn và những điểm tham quan đô thị như cầu Hoàng Văn Thụ.'),
    (24, 'Hậu Giang', 'Hậu Giang gắn với chợ nổi Ngã Bảy, sông Cái Lớn và các trải nghiệm du lịch sông nước.'),
    (25, 'Khánh Hòa', 'Khánh Hòa có Tháp Bà Ponagar, VinWonders Nha Trang và Bãi Dài Cam Ranh, kết hợp di sản Chăm với vui chơi ven biển.'),
    (26, 'Kiên Giang', 'Kiên Giang có đảo Phú Quốc, biển Mũi Nai và các điểm gắn với lịch sử địa phương như Mộ chị Sứ.'),
    (27, 'Kon Tum', 'Kon Tum có Nhà Thờ Gỗ, cầu treo Kon Klor và Vườn quốc gia Chư Mom Ray, kết hợp kiến trúc với cảnh quan Tây Nguyên.'),
    (28, 'Lai Châu', 'Lai Châu có đèo Ô Quy Hồ, cánh đồng Mường Than và chợ phiên Sìn Hồ giữa không gian núi cao.'),
    (29, 'Long An', 'Long An có làng nổi Tân Lập, nhà cổ Trăm Cột và các điểm tham quan văn hóa, thiên nhiên vùng sông nước.'),
    (30, 'Lào Cai', 'Lào Cai gắn với đỉnh Fansipan, thác Tình Yêu và những tuyến khám phá cảnh quan vùng núi.'),
    (31, 'Lâm Đồng', 'Lâm Đồng có Hồ Xuân Hương, chợ đêm Đà Lạt và Thiền viện Trúc Lâm, phù hợp chuyến đi ngắm cảnh và dạo phố.'),
    (32, 'Lạng Sơn', 'Lạng Sơn có chợ Đông Kinh, chùa Tiên và cửa khẩu Hữu Nghị trong hành trình tìm hiểu vùng biên.'),
    (33, 'Nam Định', 'Nam Định có đền Trần, nhà thờ Phú Nhai và chợ Viềng, thể hiện nhiều nét sinh hoạt văn hóa, tín ngưỡng.'),
    (34, 'Nghệ An', 'Nghệ An có Làng Sen, biển Cửa Lò và khu di tích Truông Bồn cho hành trình kết hợp lịch sử với nghỉ biển.'),
    (35, 'Ninh Bình', 'Ninh Bình nổi bật với Tràng An, Tam Cốc và cố đô Hoa Lư, nơi cảnh quan núi đá vôi đi cùng di tích lịch sử.'),
    (36, 'Ninh Thuận', 'Ninh Thuận có Vườn quốc gia Núi Chúa, vịnh Vĩnh Hy và các vườn nho trong tuyến khám phá thiên nhiên ven biển.'),
    (37, 'Phú Thọ', 'Phú Thọ có Đền Hùng và Vườn quốc gia Xuân Sơn, kết hợp điểm đến cội nguồn với trải nghiệm núi rừng.'),
    (38, 'Phú Yên', 'Phú Yên có Gành Đá Đĩa, hải đăng Đại Lãnh và Tháp Nhạn, nổi bật với cảnh quan biển và dấu ấn kiến trúc.'),
    (39, 'Quảng Bình', 'Quảng Bình có động Phong Nha cùng các điểm tham quan lịch sử, văn hóa trong hành trình khám phá miền Trung.'),
    (40, 'Quảng Nam', 'Quảng Nam kết hợp phố cổ Hội An, thánh địa Mỹ Sơn và biển An Bàng trong một hành trình di sản và nghỉ biển.'),
    (41, 'Quảng Ngãi', 'Quảng Ngãi có biển Mỹ Khê, thác Trắng và đèo Vi Ô Lắc, mang đến nhiều lựa chọn khám phá biển và núi.'),
    (42, 'Quảng Ninh', 'Quảng Ninh nổi bật với vịnh Hạ Long, biển Bãi Cháy và các điểm vui chơi quanh thành phố Hạ Long.'),
    (43, 'Quảng Trị', 'Quảng Trị có địa đạo Vịnh Mốc, nghĩa trang Trường Sơn và biển Cửa Tùng, kết hợp ký ức lịch sử với cảnh biển.'),
    (44, 'Sóc Trăng', 'Sóc Trăng có chùa Dơi, chùa Som Rong và các điểm tham quan gắn với đời sống văn hóa địa phương.'),
    (45, 'Sơn La', 'Sơn La có đỉnh Tà Xùa, thác Dải Yếm và rừng thông bản Áng, phù hợp hành trình khám phá cao nguyên.'),
    (46, 'Thanh Hóa', 'Thanh Hóa có Vườn quốc gia Bến En, suối cá Cẩm Thủy và động Từ Thức cho chuyến đi thiên nhiên.'),
    (47, 'Thành phố Hồ Chí Minh', 'Thành phố Hồ Chí Minh có chợ Bến Thành, Dinh Độc Lập và Nhà thờ Đức Bà Sài Gòn, nổi bật với nhịp sống đô thị và di tích.'),
    (48, 'Thái Bình', 'Thái Bình có chùa Keo, biển Đồng Châu và các điểm kiến trúc tôn giáo gắn với vùng đồng bằng.'),
    (49, 'Thái Nguyên', 'Thái Nguyên có hồ Núi Cốc, thác Đát Đắng và hồ Vai Miếu, phù hợp các chuyến đi ngắm cảnh, dã ngoại.'),
    (50, 'Thừa Thiên Huế', 'Thừa Thiên Huế có Đại Nội, chùa Thiên Mụ và lăng Khải Định, tạo nên hành trình khám phá di sản cố đô.'),
    (51, 'Tiền Giang', 'Tiền Giang có biển Tân Thành, Nhà thờ chính tòa Mỹ Tho và các điểm tham quan ven sông ở Cái Bè.'),
    (52, 'Trà Vinh', 'Trà Vinh có Ao Bà Om, chùa Hang và biển Ba Động, kết hợp cảnh quan sông nước với kiến trúc tín ngưỡng.'),
    (53, 'Tuyên Quang', 'Tuyên Quang có đền Hạ, hồ thủy điện và thác Bản Ba trong hành trình khám phá văn hóa, thiên nhiên.'),
    (54, 'Tây Ninh', 'Tây Ninh có núi Bà Đen, Tòa thánh Tây Ninh và hồ Dầu Tiếng, kết hợp cảnh núi với điểm đến tín ngưỡng.'),
    (55, 'Vĩnh Long', 'Vĩnh Long có khu du lịch Vinh Sang, Văn Thánh Miếu và chùa Tiên Châu, phù hợp hành trình tìm hiểu văn hóa sông nước.'),
    (56, 'Vĩnh Phúc', 'Vĩnh Phúc có hồ Đại Lải, đầm Vạc và các khu du lịch sinh thái cho chuyến nghỉ ngơi gần thiên nhiên.'),
    (57, 'Yên Bái', 'Yên Bái nổi bật với ruộng bậc thang Mù Cang Chải, hồ Thác Bà và các tuyến khám phá vùng núi.'),
    (58, 'Điện Biên', 'Điện Biên có đồi A1, hầm De Castries và tượng đài chiến thắng Điện Biên Phủ, phù hợp hành trình tìm hiểu lịch sử.'),
    (59, 'Đà Nẵng', 'Đà Nẵng có Bà Nà Hills, Ngũ Hành Sơn và cầu Rồng, kết hợp vui chơi, cảnh quan và điểm nhấn đô thị.'),
    (60, 'Đắk Lắk', 'Đắk Lắk có thác Dray Nur, Bảo tàng cà phê Thế giới và các điểm du lịch gắn với văn hóa Tây Nguyên.'),
    (61, 'Đắk Nông', 'Đắk Nông có khu bảo tồn Nâm Nung, hang động Chư Bluk và thác Dray Sap, nổi bật với cảnh quan địa chất, rừng và thác.'),
    (62, 'Đồng Nai', 'Đồng Nai có Vườn quốc gia Cát Tiên, khu du lịch Bửu Long và thác Giang Điền cho những chuyến khám phá thiên nhiên.'),
    (63, 'Đồng Tháp', 'Đồng Tháp có làng hoa Sa Đéc, Vườn quốc gia Tràm Chim và các điểm tham quan đậm nét sông nước.')
)
UPDATE public.region AS r
SET description = d.description
FROM descriptions AS d
WHERE r.region_id = d.region_id
  AND r.name = d.region_name;

DO $check$
BEGIN
    IF (SELECT count(*) FROM public.region
        WHERE description IS NOT NULL AND length(btrim(description)) >= 60) <> 63
       OR (SELECT count(DISTINCT description) FROM public.region) <> 63 THEN
        RAISE EXCEPTION 'Region description validation failed; transaction rolled back';
    END IF;
END
$check$;

COMMIT;
