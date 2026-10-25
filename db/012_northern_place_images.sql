-- Additional northern Vietnam place photos from Wikimedia Commons.
-- Reviewed against each place name; broken image URLs are excluded.
-- Run after 003_import_places.sql. Safe to run more than once.
BEGIN;
SET client_encoding = 'UTF8';
WITH photos(place_id, place_name, image_key) AS (VALUES
    (156, 'Hồ Ba Bể', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/b/b2/Ba_Be_Lake_2014.jpg/960px-Ba_Be_Lake_2014.jpg'),
    (156, 'Hồ Ba Bể', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/7/71/Ba_Be_Lake_5.jpg/960px-Ba_Be_Lake_5.jpg'),
    (157, 'Động Puông', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/b/b6/Ba_Be_Lake_-_Puong_cave.JPG/960px-Ba_Be_Lake_-_Puong_cave.JPG'),
    (157, 'Động Puông', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/7/79/Hang_Puong_Cave_-_Ba_Be_Lake_-_Ba_Be_National_Park_-_Vietnam_-_01_%2848102406852%29.jpg/960px-Hang_Puong_Cave_-_Ba_Be_Lake_-_Ba_Be_National_Park_-_Vietnam_-_01_%2848102406852%29.jpg'),
    (158, 'Thác Đầu Đẳng', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/5/53/Dau_Dang_Waterfall_-_Near_Ba_Be_Lake_-_Ba_Be_National_Park_-_Vietnam_%2848102296396%29.jpg/960px-Dau_Dang_Waterfall_-_Near_Ba_Be_Lake_-_Ba_Be_National_Park_-_Vietnam_%2848102296396%29.jpg'),
    (158, 'Thác Đầu Đẳng', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/7/7d/Panorama_of_Dau_Dang_Waterfall_-_Near_Ba_Be_Lake_-_Ba_Be_National_Park_-_Vietnam_%2848102307023%29.jpg/960px-Panorama_of_Dau_Dang_Waterfall_-_Near_Ba_Be_Lake_-_Ba_Be_National_Park_-_Vietnam_%2848102307023%29.jpg'),
    (206, 'Thác Bản Giốc', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/7/71/Ban_Gioc_Waterfall_-_Trung_Kanh_District_-_Cao_Bang_Province_-_Vietnam_-_05_%2848119871617%29.jpg/960px-Ban_Gioc_Waterfall_-_Trung_Kanh_District_-_Cao_Bang_Province_-_Vietnam_-_05_%2848119871617%29.jpg'),
    (206, 'Thác Bản Giốc', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/3/3c/Ban_Gioc_Waterfall_-_Trung_Kanh_District_-_Cao_Bang_Province_-_Vietnam_-_06_%2848119780846%29.jpg/960px-Ban_Gioc_Waterfall_-_Trung_Kanh_District_-_Cao_Bang_Province_-_Vietnam_-_06_%2848119780846%29.jpg'),
    (207, 'Động Ngườm Ngao', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/1/18/Nguom_Ngao_Cave_-_Cao_Bang_Province_-_Vietnam_-_02_%2848119821703%29.jpg/960px-Nguom_Ngao_Cave_-_Cao_Bang_Province_-_Vietnam_-_02_%2848119821703%29.jpg'),
    (207, 'Động Ngườm Ngao', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/6/6b/Nguom_Ngao_Cave_-_Cao_Bang_Province_-_Vietnam_-_03_%2848119821128%29.jpg/960px-Nguom_Ngao_Cave_-_Cao_Bang_Province_-_Vietnam_-_03_%2848119821128%29.jpg'),
    (273, 'Phố cổ Đồng Văn', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/5/55/Dong_Van_old_town.jpg/960px-Dong_Van_old_town.jpg'),
    (276, 'Chùa Tam Chúc', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/0/06/Tam_Chuc_Pagoda_-_Ha_Nam.jpg/960px-Tam_Chuc_Pagoda_-_Ha_Nam.jpg'),
    (281, 'Lăng Chủ tịch Hồ Chí Minh', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/3/33/2012_Ho_Chi_Minh_Mausoleum.jpg/960px-2012_Ho_Chi_Minh_Mausoleum.jpg'),
    (281, 'Lăng Chủ tịch Hồ Chí Minh', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/2/28/Ho_Chi_Minh_Mausoleum%2C_Hanoi_%283%29_%2824622750848%29.jpg/960px-Ho_Chi_Minh_Mausoleum%2C_Hanoi_%283%29_%2824622750848%29.jpg'),
    (283, 'Văn Miếu - Quốc Tử Giám', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/7/75/Constellation_of_Literature_pavilion%2C_Temple_of_Literature%2C_Hanoi%2C_Vietnam%2C_20240123_0939_3103.jpg/960px-Constellation_of_Literature_pavilion%2C_Temple_of_Literature%2C_Hanoi%2C_Vietnam%2C_20240123_0939_3103.jpg'),
    (283, 'Văn Miếu - Quốc Tử Giám', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/d/db/Dai_Trung_gate%2C_Temple_of_Literature%2C_Hanoi%2C_Vietnam%2C_20240123_0928_3065.jpg/960px-Dai_Trung_gate%2C_Temple_of_Literature%2C_Hanoi%2C_Vietnam%2C_20240123_0928_3065.jpg'),
    (284, 'Hoàng thành Thăng Long', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/0/0f/Central_Sector_of_the_Imperial_Citadel_of_Thang_Long_-_Hanoi.jpg/960px-Central_Sector_of_the_Imperial_Citadel_of_Thang_Long_-_Hanoi.jpg'),
    (284, 'Hoàng thành Thăng Long', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/9/9f/Central_Sector_of_the_Imperial_Citadel_of_Thang_Long_-_Hanoi_%2826246667851%29.jpg/960px-Central_Sector_of_the_Imperial_Citadel_of_Thang_Long_-_Hanoi_%2826246667851%29.jpg'),
    (285, 'Cầu Long Biên', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/c/c9/LONG_BIEN_BRIDGE_HANOI_VIETNAM_FEB_2012.jpg/960px-LONG_BIEN_BRIDGE_HANOI_VIETNAM_FEB_2012.jpg'),
    (285, 'Cầu Long Biên', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/c/cf/LONG_BIEN_BRIDGE_HANOI_VIETNAM_FEB_2012_%287011980509%29.jpg/960px-LONG_BIEN_BRIDGE_HANOI_VIETNAM_FEB_2012_%287011980509%29.jpg'),
    (306, 'Thung Nai', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/a/a4/Thung_Nai%2C_Cao_Phong%2C_H%C3%B2a_B%C3%ACnh%2C_Vietnam_-_panoramio.jpg/960px-Thung_Nai%2C_Cao_Phong%2C_H%C3%B2a_B%C3%ACnh%2C_Vietnam_-_panoramio.jpg'),
    (294, 'Đền Kiếp Bạc', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/e/ea/Ki%E1%BA%BFp_B%E1%BA%A1c_temple_in_1904.jpg/960px-Ki%E1%BA%BFp_B%E1%BA%A1c_temple_in_1904.jpg'),
    (296, 'Đồ Sơn', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/c/cb/%C4%90%E1%BB%93_S%C6%A1n_beach_-_panoramio.jpg/960px-%C4%90%E1%BB%93_S%C6%A1n_beach_-_panoramio.jpg'),
    (297, 'Cát Bà', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/a/a5/Cat_Ba_Island%2C_Vietnam%2C_20240131_1431_4699.jpg/960px-Cat_Ba_Island%2C_Vietnam%2C_20240131_1431_4699.jpg'),
    (297, 'Cát Bà', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/2/27/Cat_Ba_Island.jpg/960px-Cat_Ba_Island.jpg'),
    (300, 'Cầu Hoàng Văn Thụ', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/8/88/Hoang_Van_Thu_Bridge.jpg/960px-Hoang_Van_Thu_Bridge.jpg'),
    (16, 'Đèo Ô Quy Hồ', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/1/19/O_Quy_Ho_pass.jpg/960px-O_Quy_Ho_pass.jpg'),
    (16, 'Đèo Ô Quy Hồ', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/a/a0/Sunset_on_O_Quy_Ho_pass.jpg/960px-Sunset_on_O_Quy_Ho_pass.jpg'),
    (31, 'Đỉnh Núi Fansipan', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/1/19/Fansipan_Summit_Mark.jpg/960px-Fansipan_Summit_Mark.jpg'),
    (31, 'Đỉnh Núi Fansipan', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/1/1d/Fansipan_summit_2_in_sunrise.jpg/960px-Fansipan_summit_2_in_sunrise.jpg'),
    (51, 'Tràng An', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/a/a5/Trang_An%2C_Ninh_Binh.jpg/960px-Trang_An%2C_Ninh_Binh.jpg'),
    (51, 'Tràng An', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/f/fd/Trang_An%2C_Ninh_Binh_%2824089563857%29.jpg/960px-Trang_An%2C_Ninh_Binh_%2824089563857%29.jpg'),
    (52, 'Tam Cốc', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/0/0e/Tam_Coc-Paysage_karstique.jpg/960px-Tam_Coc-Paysage_karstique.jpg'),
    (52, 'Tam Cốc', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/e/ef/Tam_Coc_%283%29.jpg/960px-Tam_Coc_%283%29.jpg'),
    (54, 'Vườn quốc gia Cúc Phương', 'https://upload.wikimedia.org/wikipedia/commons/3/3c/Cuc_Phuong_National_Park_-_Vietnam.JPG'),
    (54, 'Vườn quốc gia Cúc Phương', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/9/98/Forest_in_Cuc_Phuong_National_Park_%2815706323528%29.jpg/960px-Forest_in_Cuc_Phuong_National_Park_%2815706323528%29.jpg'),
    (101, 'Mộc Châu', 'https://upload.wikimedia.org/wikipedia/commons/8/86/M%E1%BB%99c_Ch%C3%A2u.jpg'),
    (101, 'Mộc Châu', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/6/67/Nha_san_Moc_Chau.jpg/960px-Nha_san_Moc_Chau.jpg'),
    (116, 'Hồ Núi Cốc', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/4/4b/Nui_Coc_Lake_25.jpg/960px-Nui_Coc_Lake_25.jpg'),
    (117, 'Đồi chè Tân Cương', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/a/a4/Tan_Cuong_Tea_culture.jpg/960px-Tan_Cuong_Tea_culture.jpg'),
    (237, 'Hồ Thác Bà', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/f/fd/Th%C3%A1c_B%C3%A0_Lake.jpg/960px-Th%C3%A1c_B%C3%A0_Lake.jpg'),
    (237, 'Hồ Thác Bà', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/0/0d/Th%C3%A1c_B%C3%A0_lake_-_panorama.jpg/960px-Th%C3%A1c_B%C3%A0_lake_-_panorama.jpg'),
    (253, 'Bảo tàng chiến thắng Điện Biên Phủ', 'https://upload.wikimedia.org/wikipedia/commons/3/39/Captured_French_artillery_at_the_Dien_Bien_Phu_Museum.jpg'),
    (254, 'Cánh đồng Mường Thanh', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/a/a1/Panorama_of_Ricefields_in_Muong_Thanh_Valley_-_Dien_Bien_Phu_-_Vietnam_-_02_%2848178461326%29.jpg/960px-Panorama_of_Ricefields_in_Muong_Thanh_Valley_-_Dien_Bien_Phu_-_Vietnam_-_02_%2848178461326%29.jpg'),
    (151, 'Chùa Bổ Đà', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/d/d7/NNU_Ch%C3%B9ab%E1%BB%95%C4%91%C3%A0.jpg/960px-NNU_Ch%C3%B9ab%E1%BB%95%C4%91%C3%A0.jpg'),
    (280, 'Làng Vũ Đại', 'https://thumb.wikimedia.org/wikipedia/commons/thumb/4/4c/%C4%90%C3%ACnh_l%C3%A0ng_V%C5%A9_%C4%90%E1%BA%A1i.JPG/960px-%C4%90%C3%ACnh_l%C3%A0ng_V%C5%A9_%C4%90%E1%BA%A1i.JPG')
), checked AS (
    SELECT p.place_id, photos.image_key
    FROM photos JOIN public.place p ON p.place_id = photos.place_id
                     AND p.name = photos.place_name
    JOIN public.region r ON r.region_id = p.region_id
    WHERE r.region_id IN (8,9,10,12,16,17,18,20,21,22,23,28,30,32,33,35,37,42,45,48,49,53,56,57,58)
)
INSERT INTO public.place_image (place_id, image_key)
SELECT c.place_id, c.image_key FROM checked c
WHERE NOT EXISTS (SELECT 1 FROM public.place_image existing
                  WHERE existing.place_id = c.place_id AND existing.image_key = c.image_key);
COMMIT;
