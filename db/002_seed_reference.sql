-- Review before applying: maps the supplied data into the existing public schema.
-- feature_group and place_type metadata have no matching columns in this schema.
-- 10 place types; 35 feature tags + 5 scoring criteria in recommendation_criterion.
BEGIN;
SET client_encoding = 'UTF8';

INSERT INTO public.place_type (name) VALUES
    ('Thiên nhiên'),
    ('Di tích lịch sử'),
    ('Tôn giáo và tín ngưỡng'),
    ('Bảo tàng'),
    ('Công trình và không gian xây dựng'),
    ('Chợ'),
    ('Làng nghề và thủ công'),
    ('Cảnh quan nông nghiệp'),
    ('Làng, bản và phố cổ'),
    ('Khu vui chơi và nghỉ dưỡng')
ON CONFLICT (name) DO NOTHING;

INSERT INTO public.recommendation_criterion (code, name, description, status) VALUES
    ('kind__nature', 'Thiên nhiên', 'Cảnh quan hoặc điểm bảo tồn tự nhiên.', 'ACTIVE'),
    ('kind__heritage', 'Di tích, công trình lịch sử', 'Di tích, nơi lưu niệm hoặc công trình có giá trị lịch sử được mô tả.', 'ACTIVE'),
    ('kind__religious', 'Tín ngưỡng, tôn giáo', 'Chùa, đền, đình, miếu, nhà thờ hoặc thánh địa; không suy từ sản phẩm làng nghề.', 'ACTIVE'),
    ('kind__museum', 'Bảo tàng', 'Cơ sở trưng bày được xác định là bảo tàng.', 'ACTIVE'),
    ('kind__structure', 'Công trình, không gian xây dựng', 'Cầu, hải đăng, quảng trường, cột cờ, công trình quan sát; không gán cho mọi tòa nhà.', 'ACTIVE'),
    ('kind__market', 'Chợ', 'Chợ hoặc chợ nổi; không tự suy ra ẩm thực.', 'ACTIVE'),
    ('kind__craft', 'Nghề thủ công', 'Điểm sản xuất, trưng bày hoặc trải nghiệm nghề thủ công; không gồm ruộng, vườn trồng cây.', 'ACTIVE'),
    ('kind__agriculture', 'Cảnh quan, sản xuất nông nghiệp', 'Ruộng, vườn nho/hoa/quả, đồi chè, đồng muối; đồng muối được xếp nhóm sản xuất cảnh quan.', 'ACTIVE'),
    ('kind__community', 'Khu dân cư, văn hóa cộng đồng', 'Làng, bản, phố cổ hoặc khu vực cư trú là đối tượng tham quan.', 'ACTIVE'),
    ('kind__recreation', 'Khu vui chơi, nghỉ dưỡng', 'Công viên, khu du lịch, tổ hợp nghỉ dưỡng; không mặc định có trò chơi.', 'ACTIVE'),
    ('landform__coast', 'Biển, ven biển', 'Bãi biển, vịnh biển, mũi biển hoặc cảnh quan bờ biển thuộc điểm đến.', 'ACTIVE'),
    ('landform__island', 'Đảo, cù lao', 'Đảo biển hoặc đảo sông/hồ có bằng chứng; không suy từ tên thương hiệu.', 'ACTIVE'),
    ('landform__mountain', 'Núi, đồi', 'Núi, đồi, đèo, vách núi trong phạm vi điểm đến.', 'ACTIVE'),
    ('landform__cave', 'Hang động tự nhiên', 'Hang, động tự nhiên; loại trừ địa đạo, hầm và hang nhân tạo.', 'ACTIVE'),
    ('landform__river', 'Sông', 'Sông, chợ trên sông hoặc cầu qua sông; loại trừ tên sông chỉ nhắc trong vùng lân cận.', 'ACTIVE'),
    ('landform__lake', 'Hồ, ao', 'Mặt nước hồ, ao hoặc hồ chứa; Hồ Tràm không thuộc nhãn này.', 'ACTIVE'),
    ('landform__waterfall', 'Thác', 'Thác thực tế; không suy từ Hồ Thác Mơ hoặc Đền Thác Bờ.', 'ACTIVE'),
    ('landform__stream', 'Suối, nguồn khoáng', 'Suối hoặc nguồn khoáng; Suối Giàng là tên riêng nên không tự gán.', 'ACTIVE'),
    ('landform__lagoon', 'Đầm ven biển', 'Đầm ven biển, khác với hồ nội địa; Đầm Vạc được xếp hồ nội địa.', 'ACTIVE'),
    ('landform__dunes', 'Cồn, đồi cát', 'Địa hình cồn cát tự nhiên được mô tả; giữ dù ít xuất hiện.', 'ACTIVE'),
    ('landform__valley', 'Thung lũng', 'Thung lũng được xác định trong mô tả, không suy từ tên địa danh.', 'ACTIVE'),
    ('ecosystem__forest', 'Rừng', 'Có rừng thuộc điểm đến; không đồng nhất cây trong vườn hoặc danh xưng vườn quốc gia với rừng.', 'ACTIVE'),
    ('ecosystem__flooded_habitat', 'Sinh cảnh ngập nước', 'Rừng ngập nước, vùng trũng ngập nước hoặc đầm lầy được xác nhận. Định nghĩa hẹp dùng cho dataset, không phải toàn bộ phân loại Ramsar (vốn bao gồm cả hồ, sông...).', 'ACTIVE'),
    ('ecosystem__grassland', 'Trảng, đồng cỏ', 'Trảng cỏ tự nhiên; không đồng nhất với ruộng canh tác.', 'ACTIVE'),
    ('theme__history', 'Lịch sử', 'Di tích, nhân vật hoặc sự kiện lịch sử có trong nội dung; không mặc định mọi chùa đều có giá trị lịch sử.', 'ACTIVE'),
    ('theme__culture', 'Văn hóa', 'Văn hóa cộng đồng, lễ hội, phong tục, nghệ thuật hoặc đời sống địa phương.', 'ACTIVE'),
    ('theme__architecture', 'Kiến trúc', 'Công trình hoặc giá trị kiến trúc được xác định trong mô tả.', 'ACTIVE'),
    ('theme__spirituality', 'Tâm linh', 'Nội dung tôn giáo, tín ngưỡng hoặc hành hương.', 'ACTIVE'),
    ('theme__ecology', 'Sinh thái', 'Bảo tồn, động thực vật, hệ sinh thái hoặc du lịch sinh thái được nêu; không suy từ mọi cảnh quan xanh.', 'ACTIVE'),
    ('theme__geology', 'Địa chất', 'Hang động tự nhiên, đá vôi, núi lửa hoặc cấu tạo địa chất được nêu.', 'ACTIVE'),
    ('theme__craft', 'Thủ công', 'Nội dung nghề thủ công, sản phẩm hoặc trải nghiệm chế tác.', 'ACTIVE'),
    ('theme__cuisine', 'Ẩm thực', 'Món ăn, đồ uống, đặc sản hoặc văn hóa ẩm thực được nêu.', 'ACTIVE'),
    ('theme__games', 'Vui chơi', 'Trò chơi, giải trí chủ động hoặc công viên giải trí được nêu.', 'ACTIVE'),
    ('theme__relaxation', 'Nghỉ dưỡng', 'Nghỉ dưỡng, thư giãn, resort hoặc tắm khoáng được nêu.', 'ACTIVE'),
    ('theme__agriculture', 'Nông nghiệp', 'Nội dung trồng trọt, đồng muối hoặc trải nghiệm sản xuất nông nghiệp.', 'ACTIVE'),
    ('PREFERENCE', 'Khớp sở thích', 'P là tỷ lệ nhóm sở thích khớp tất cả loại địa danh.', 'ACTIVE'),
    ('CLUSTER', 'Khớp cụm', 'C là trung bình P trên địa danh chuẩn trong cụm.', 'ACTIVE'),
    ('ACTIVITY', 'Phù hợp mức vận động', 'A = min(1, T(u) / required_steps).', 'ACTIVE'),
    ('RATING', 'Điểm đánh giá', 'R = rating / 5 khi có rating.', 'ACTIVE'),
    ('POPULARITY', 'Mức phổ biến', 'N = ln(1 + rating_count) / ln(1 + Nmax) khi tính được.', 'ACTIVE')
ON CONFLICT (code) DO NOTHING;

COMMIT;
