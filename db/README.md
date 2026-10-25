# PostgreSQL BCV

Tài liệu nguồn: [Đồ án Bước Chân Việt](https://docs.google.com/document/d/1GAQdAqCcwr_IivfHzInilF0LhbWyniN6AONmzqEHWjo/edit?tab=t.0), mục 3.1.2 và 3.1.5.

## Instance trên máy này

- PostgreSQL 18, cluster dữ liệu: `D:\PostgreSQL\BCV\data`
- Tên server đã đăng ký trong pgAdmin 4 (nhóm `Servers`): **BCV**. DBeaver cũng có kết nối cùng tên.
- Host: `127.0.0.1`, port: `5433`, database: `bcv`, user: `postgres`
- PostGIS 3.6 được bật trong database `bcv`.
- Scheduled Task `BCV PostgreSQL` khởi động cluster khi người dùng Windows đăng nhập. Dữ liệu và log nằm ngoài thư mục dự án. Instance chỉ lắng nghe trên `localhost`.
- Mật khẩu không được lưu trong repository hoặc cấu hình DBeaver; nhập mật khẩu khi DBeaver hỏi.

PostgreSQL hiện có trên cổng 5432 là một cluster khác. Cluster BCV dùng cổng 5433 để tránh xung đột.

## Schema

`001_schema.sql` tạo 25 bảng theo bản đặc tả chi tiết, gồm khóa chính, 32 khóa ngoại, ràng buộc duy nhất, kiểm tra dữ liệu, các chỉ mục tra cứu và 2 chỉ mục không gian PostGIS. Tên `USER` trong tài liệu được triển khai thành `app_user` để tránh trùng từ khóa SQL. Tên cột dùng `snake_case` để thuận tiện cho truy vấn PostgreSQL.

Tài liệu có chỗ ghi 28 bảng ở mục 4.1.3, nhưng mục 3.1.2 và 3.1.5 liệt kê đúng 25 quan hệ. Schema này bám theo danh sách và đặc tả 25 quan hệ đó. Script schema chỉ tạo cấu trúc, chưa tạo tài khoản ứng dụng.

`002_seed_reference.sql` là dữ liệu danh mục đã được nhập vào database `bcv`: 10 loại địa danh trong `place_type` và 40 tiêu chí trong `recommendation_criterion` (35 nhãn đặc trưng cùng 5 tiêu chí chấm điểm). Có thể chạy lại script này; các dòng đã tồn tại được bỏ qua bằng `ON CONFLICT DO NOTHING`.

`003_import_places.sql` là query đã chuyển bộ dữ liệu địa danh người dùng cung cấp sang đúng schema 25 bảng hiện có. Query nhập 63 vùng, 311 địa danh, 1 cấu hình gợi ý, 311 cụm/địa danh, 10.885 giá trị đặc trưng và 5 trọng số. 345 lượt gán loại địa danh được bảo toàn qua loại chính trong `place` và các nhãn `kind__*` trong `place_feature_value`. Query yêu cầu các bảng đích đang trống, kiểm tra số dòng trước khi `COMMIT`, và không tạo bảng hoặc cột mới. Đã chạy thành công vào database `bcv` ngày 24/10/2026 và kiểm tra lại số dòng trực tiếp từ PostgreSQL.

Các URL nguồn, mã băm, bí danh nguồn và metadata mô hình không có cột tương ứng trong schema 25 bảng, nên chưa được ghi vào database; chúng vẫn còn trong file dữ liệu gốc. Giá trị `rating_count` bị thiếu được chuyển thành `0`, còn `rating` giữ `NULL`.

`004_seed_demo_remaining.sql` đã được nhập vào `bcv` ngày 24/10/2026 để bổ sung dữ liệu mẫu cho 17 bảng còn lại. Script có 3 người dùng giả (`example.invalid`), sở thích, bước chân, vị trí, hành trình, thẻ, bộ sưu tập, vật phẩm, nhiệm vụ, tiến độ và thông báo; các khóa ngoại và số dòng được kiểm tra trong cùng giao dịch. ID mẫu bắt đầu từ `900001` để dễ nhận biết. Mật khẩu là hash bcrypt của chuỗi ngẫu nhiên đã bỏ, nên các tài khoản này không dùng để đăng nhập. Bản script hiện dùng URL ảnh demo cho `place_image.image_key` và `card.image_key`. Sau khi nhập, cả 25/25 bảng ứng dụng đều có dữ liệu.

`005_correct_demo_items.sql` đã được áp dụng vào `bcv` để sửa dữ liệu mẫu `item` theo đồ án: chỉ còn **Giày tăng tốc** (`SPEED_SHOES`) và **Vé máy bay** (`FLIGHT_TICKET`). Giày tăng tốc có hệ số mẫu `2.00`, hiệu lực `5.000` bước và giới hạn một lần dùng; vé máy bay không có hệ số hay phạm vi bước. Script chuyển liên kết `user_item` và `quest` từ vật phẩm mẫu thứ ba sang vé máy bay trước khi xóa vật phẩm thừa. Bản `004_seed_demo_remaining.sql` cũng đã được cập nhật để database mới tạo nhận đúng hai vật phẩm ngay từ đầu. Cả hai vật phẩm mẫu vẫn ở trạng thái `DRAFT`.

`006_set_demo_image_urls.sql` đã thay ba khóa ảnh placeholder trong database đang chạy bằng URL JPEG trực tiếp từ Wikimedia Commons và điền cùng URL vào thẻ tương ứng. Bản `004_seed_demo_remaining.sql` cũng đã được cập nhật cho lần dựng database mới. Đây là URL ngoài để thử hiển thị ảnh, không phải khóa đối tượng Cloudflare R2. Khi hiển thị hoặc phân phối ảnh, giữ thông tin tác giả và giấy phép:

| Địa danh | Ảnh nguồn | Tác giả và giấy phép |
| --- | --- | --- |
| Tháp Bà Ponagar | [Po Nagar Cham Towers](https://commons.wikimedia.org/wiki/File:Po_Nagar_Cham_Towers.jpeg) | 4pf3154f7, CC BY-SA 3.0 |
| VinWonders Nha Trang | [Vinpearl Land Nha Trang Ferris wheel](https://commons.wikimedia.org/wiki/File:Vinpearl_Land_Nha_Trang_Ferris_wheel.jpg) | 유자차, CC BY-SA 4.0 |
| Nhà Thờ Gỗ Kon Tum | [Kontum wooden catholic church](https://commons.wikimedia.org/wiki/File:Kontum_wooden_catholic_church.jpg) | Rdavout, CC BY-SA 3.0 |

`007_more_demo_images_and_quests.sql` đã được áp dụng vào `bcv` ngày 24/10/2026: thêm 12 ảnh JPEG cỡ 960 px cho 12 địa danh khác nhau (tổng 15 ảnh/15 địa danh) và 8 nhiệm vụ ngày/tuần (tổng 11 nhiệm vụ). Nhiệm vụ mẫu hoàn thành hành trình được đổi từ chu kỳ một lần sang hàng tuần; bản ghi tiến độ cũ được chuyển sang khóa tuần `2026-W43`. Các nhiệm vụ dùng ba điều kiện `STEP_COUNT`, `DISCOVER_PLACE`, `COMPLETE_JOURNEY`, chỉ thưởng hai vật phẩm hiện có và vẫn ở trạng thái `DRAFT`. Script kiểm tra trạng thái trước/sau trong cùng giao dịch. Bản `004_seed_demo_remaining.sql` hiện đã chứa toàn bộ dữ liệu cuối cùng để dựng database mới từ đầu; các script `005`–`007` là migration dành cho database mẫu được tạo trước khi sửa dữ liệu.

Nguồn ảnh thêm trong `007` (URL ảnh trực tiếp nằm trong SQL):

| Địa danh | Trang ảnh nguồn | Tác giả và giấy phép |
| --- | --- | --- |
| Phố cổ Hội An | [Hoi An Ancient Town](https://commons.wikimedia.org/wiki/File:Hoi_An_Ancient_Town.jpg) | Andre Hospers, CC BY 4.0 |
| Vịnh Hạ Long | [Halong Bay in Vietnam](https://commons.wikimedia.org/wiki/File:Halong_Bay_in_Vietnam.jpg) | Thomas Hirsch / User:Ravn, CC BY-SA 3.0 |
| Thác Bản Giốc | [Ban Gioc](https://commons.wikimedia.org/wiki/File:Ban_Gioc.jpg) | Caobangpro, CC BY 3.0 |
| Hồ Gươm | [Ho Hoan Kiem](https://commons.wikimedia.org/wiki/File:Ho_Hoan_Kiem.jpg) | Trung geo, phạm vi công cộng |
| Đỉnh Núi Fansipan | [Fansipan summit new](https://commons.wikimedia.org/wiki/File:Fansipan_summit_new.jpg) | Thái Nhi, phạm vi công cộng |
| Nhà thờ Đức Bà Sài Gòn | [Saigon Notre-Dame Basilica](https://commons.wikimedia.org/wiki/File:Saigon_Notre-Dame_Basilica.jpg) | Ngô Trung, CC BY-SA 3.0 |
| Tràng An | [Trang An Landscape Complex](https://commons.wikimedia.org/wiki/File:Trang_An_Landscape_Complex,_Ninh_Binh_Province,_Vietnam,_20240202_1524_5364.jpg) | Jakub Hałun, CC BY 4.0 |
| Đảo Phú Quốc | [Phu Quoc Beach](https://commons.wikimedia.org/wiki/File:Phu_Quoc_Beach.jpg) | FrogsLegs71, CC BY-SA 3.0 |
| Hồ Xuân Hương | [Xuan Huong Lake in Da Lat](https://commons.wikimedia.org/wiki/File:Xuan_Huong_Lake_in_Da_Lat_(28219543381).jpg) | Le Anh Tuan, phạm vi công cộng |
| Bà Nà Hills | [The Golden Bridge](https://commons.wikimedia.org/wiki/File:The_Golden_Bridge,_Ba_Na_Hills,_Vietnam.jpg) | Ray in Manila, CC BY 2.0 |
| Động Phong Nha | [Phong Nha cave entrance](https://commons.wikimedia.org/wiki/File:Phong_Nha_cave_entrance.jpg) | Tycho, CC BY-SA 3.0 |
| Đại Nội Huế | [Imperial City, Hue](https://commons.wikimedia.org/wiki/File:Imperial_City,_Hue,_Vietnam_(49575145062).jpg) | flowcomm, CC BY 2.0 |

`008_add_demo_cards.sql` đã được áp dụng vào `bcv` để tạo thêm 12 card cho 12 địa danh vừa có ảnh (tổng 15 card). Mỗi card dùng đúng URL trong `place_image`, tên và phần giới thiệu từ `place`; mỗi card được gắn vào một trong hai bộ sưu tập hiện có. Script kiểm tra không có địa danh bị trùng card hoặc card thiếu bộ sưu tập. Bản `004_seed_demo_remaining.sql` cũng đã được cập nhật để lần dựng database mới có đủ 15 card. Không có bảng mới và không tự cấp card cho người dùng mẫu.

`009_describe_regions.sql` đã được áp dụng vào `bcv` để điền mô tả riêng cho toàn bộ 63 dòng `region` dựa trên các địa danh của bộ dữ liệu đã nhập. Sau khi chạy, 63/63 mô tả đều có nội dung và không trùng nhau; schema vẫn có 25 bảng ứng dụng. Với database mới tạo từ `001`–`004`, chạy thêm script `009` sau khi nhập `003_import_places.sql` để có các mô tả này. Script kiểm tra các mô tả cũ đang trống trước khi cập nhật, nhằm tránh ghi đè nội dung người dùng đã sửa.

`010_place_fun_facts.sql` đã được áp dụng vào `bcv` để điền `place.highlights` cho toàn bộ 311 địa danh. Có 37 fun fact viết riêng cho các địa danh tiêu biểu hoặc dễ nhầm tên; các dòng còn lại lấy một ý ngắn từ `place.introduction` đã nhập, giúp nội dung bám dữ liệu gốc. `011_refine_place_fun_facts.sql` đã sửa 7 highlight được tạo trước khi bổ sung các fun fact viết riêng, loại bỏ ghi chú về bộ dữ liệu và làm rõ các địa danh trùng tên. Với database mới, chạy `010` sau `003`; bản `010` hiện đã chứa 7 nội dung sửa nên không cần chạy `011`. Sau cập nhật, 311/311 highlight có nội dung riêng, không trùng nhau, và không có bảng mới.

`012_northern_place_images.sql` đã được áp dụng vào `bcv` ngày 25/10/2026: thêm 46 URL ảnh Wikimedia Commons đã kiểm tra truy cập, gắn với 29 địa danh miền Bắc. Script đối chiếu cả ID và tên địa danh, chỉ thêm URL chưa tồn tại nên có thể chạy lại. Tổng số ảnh trong `place_image` tăng từ 15 lên 61; 31/125 địa danh thuộc 25 tỉnh miền Bắc hiện có ảnh. Các địa danh chưa tìm được ảnh đúng nguồn vẫn để trống, tránh gán ảnh sai. Danh sách từng tệp nguồn nằm trong [012_northern_place_images_sources.md](012_northern_place_images_sources.md); ứng dụng cần hiển thị ghi công tác giả và giấy phép theo trang tệp khi sử dụng ảnh.

`013_northern_cards_collections.sql` đã được áp dụng vào `bcv` ngày 25/10/2026: tạo thêm 120 thẻ để đủ 125/125 địa danh miền Bắc có thẻ, giữ nguyên 5 thẻ đã tồn tại, và gắn mỗi thẻ vào đúng một trong 5 bộ sưu tập khu vực (Hà Nội và Kinh Bắc 20, đồng bằng Bắc Bộ 30, biển đảo Bắc Bộ 10, Đông Bắc 30, Tây Bắc 35). Có 31 thẻ miền Bắc dùng ảnh đã nhập; 94 thẻ còn lại giữ `image_key` trống cho tới khi có ảnh đúng địa danh. Script có kiểm tra số lượng và chạy lại không tạo bản ghi trùng. Tổng database hiện có 135 thẻ và 7 bộ sưu tập, kể cả dữ liệu mẫu trước đó.

## Lệnh quản trị

```powershell
& 'D:\Program Files\PostgreSQL\18\bin\pg_isready.exe' -h 127.0.0.1 -p 5433
& 'D:\Program Files\PostgreSQL\18\bin\psql.exe' -X -W -h 127.0.0.1 -p 5433 -U postgres -d bcv
```

Với một database `bcv` mới và trống, chạy schema từ thư mục gốc dự án:

```powershell
& 'D:\Program Files\PostgreSQL\18\bin\psql.exe' -X -W -v ON_ERROR_STOP=1 -h 127.0.0.1 -p 5433 -U postgres -d bcv -f db/001_schema.sql
```

Script dành cho database trống; không chạy lại trên database đã có bảng. Các thay đổi tiếp theo nên được thêm bằng migration mới.
