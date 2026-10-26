# Web quản trị Bước Chân Việt (*bcv-admin*)

Website quản trị nội dung Bước Chân Việt (React 19 + Vite), lấy dữ liệu từ Backend qua `/api/admin`.

Phạm vi theo tài liệu đồ án, mục 2.3.8–2.3.11 (chức năng F33–F50). Node.js và Vite chỉ dùng khi phát triển và build; sau khi build, web là các file tĩnh, còn nghiệp vụ vẫn do Backend ASP.NET Core xử lý. Tên gói trong `package.json` là `bcv-admin`.

## Mục lục

- [Cài đặt](#cài-đặt)
- [Sử dụng](#sử-dụng)
- [Chức năng](#chức-năng)
- [Build và triển khai](#build-và-triển-khai)
- [Kiểm thử tích hợp](#kiểm-thử-tích-hợp)
- [Xử lý sự cố](#xử-lý-sự-cố)
- [API quản trị](#api-quản-trị)
- [Đóng góp](#đóng-góp)
- [Giấy phép](#giấy-phép)

## Cài đặt

Cần Node.js 22.12 trở lên và Backend đang chạy ([backend/README.md](../backend/README.md#cài-đặt)).

```powershell
cd web
npm ci
```

### Cấu hình (tùy chọn)

Chỉ cần khi khác mặc định. Tạo `web/.env.local` (Git bỏ qua file này):

```powershell
Copy-Item web/.env.example web/.env.local
```

| Biến | Ý nghĩa | Mặc định |
| --- | --- | --- |
| `BCV_API_TARGET` | Địa chỉ Backend | `http://127.0.0.1:5081` |
| `WEB_HOST` | Địa chỉ web lắng nghe; `0.0.0.0` để máy khác trong mạng truy cập | `127.0.0.1` |
| `WEB_PORT` | Cổng web | `5173` |
| `VITE_GOONG_MAPTILES_KEY` | Khóa bản đồ Goong | Lấy `bcvGoongMaptilesKey` từ `android/dev.properties` |

Sửa `.env.local` xong phải chạy lại `npm run dev`. Không đặt mật khẩu hay khóa bí mật vào biến `VITE_*`, vì chúng được gửi xuống trình duyệt.

### Tài khoản quản trị

Chỉ tài khoản vai trò `ADMIN` đăng nhập được; tài khoản đăng ký từ app là `USER` và bị từ chối. Tạo admin (một lần cho mỗi database), từ thư mục gốc:

```powershell
.\backend\create-admin.ps1 -Email admin@bcv.local
```

Script hỏi mật khẩu (ít nhất 12 ký tự, có cả chữ và số). Email đã tồn tại sẽ bị từ chối; script không đổi mật khẩu hay quyền của tài khoản cũ. Nếu báo lỗi build vì backend đang chạy, dừng backend rồi chạy lại.

## Sử dụng

```powershell
cd web
npm run dev
```

Mở http://127.0.0.1:5173 và đăng nhập bằng tài khoản quản trị. Vite tự chuyển tiếp `/api` và `/health` sang Backend. Nhấn Ctrl+C để dừng.

## Chức năng

| Màn hình | Chức năng |
| --- | --- |
| Bảng điều khiển | Số người dùng, hành trình, lượt khám phá, thẻ thu thập; biểu đồ 7/30/90 ngày; nội dung thiếu ảnh |
| Người dùng | Tìm kiếm, xem thống kê cá nhân, khóa/mở tài khoản |
| Khu vực | Tạo, sửa tên, mô tả, trạng thái |
| Địa danh | Nội dung, fun fact, tọa độ, loại hình, khu vực, đánh giá, trạng thái, ảnh (URL hoặc tải lên) |
| Bản đồ địa danh | Xem ghim trên bản đồ Goong, kéo ghim để chọn tọa độ, tạo địa danh từ ghim |
| Loại địa danh | 12 loại chuẩn ([danh sách và ánh xạ](../db/README.md#loại-địa-danh)) |
| Thẻ | Ảnh, nội dung, địa danh liên kết (mỗi địa danh tối đa một thẻ) |
| Bộ sưu tập | Chọn các thẻ thuộc bộ sưu tập |
| Nhiệm vụ | Nhiệm vụ ngày/tuần, điều kiện, mục tiêu, phần thưởng |
| Vật phẩm | Cấu hình Giày tăng tốc và Vé máy bay |
| Thông tin hệ thống | Kiểm tra kết nối Backend |

Mọi danh sách có tìm kiếm, lọc trạng thái, phân trang và xuất CSV. Bấm một dòng để xem chi tiết; từ đó có thể chỉnh sửa, xóa hoặc mở đối tượng liên quan.

### Quy tắc nội dung

- **Trạng thái:** `DRAFT` (bản nháp, chỉ thấy trong quản trị), `ACTIVE` (app hiển thị), `INACTIVE` (ngừng dùng).
- **Thứ tự kích hoạt:** địa danh trước, rồi thẻ và bộ sưu tập. Thẻ chỉ kích hoạt được khi có ảnh và địa danh đang hoạt động.
- **Xóa:** cần xác nhận tên và mã. Không xóa được tài khoản quản trị và bản ghi đã có liên kết hoặc lịch sử; khi đó chuyển sang `INACTIVE`.
- **Nhiệm vụ đã có người làm:** chỉ đổi được tên và trạng thái; muốn đổi điều kiện hay phần thưởng thì tạo nhiệm vụ mới.
- **Khóa người dùng** đăng xuất người đó ngay; không thể tự khóa chính mình.
- **Ảnh** được nén thành JPEG (cạnh dài tối đa 1600 px, tối đa 5 MB). Nếu Backend đã cấu hình Cloudflare R2, ảnh địa danh vào thư mục `places/`, ảnh thẻ vào `cards/`, tách khỏi ảnh đại diện người dùng ở `avatars/`. Nếu chưa cấu hình, ảnh được lưu trong database.
- **Địa danh mới thêm** chưa có dữ liệu đặc trưng của mô hình nên chưa được đưa vào gợi ý điểm đến; **loại địa danh mới** chỉ được tính điểm sở thích khi được thêm vào bảng ánh xạ ([db/README.md](../db/README.md#loại-địa-danh)).

## Build và triển khai

```powershell
npm run build
```

Kết quả nằm trong `dist/`. Khi triển khai, phục vụ thư mục này bằng web server tĩnh (IIS, Nginx…) qua HTTPS và cấu hình reverse proxy `/api` tới Backend. Proxy trong `vite.config.js` và `npm run preview` chỉ dùng khi phát triển.

## Kiểm thử tích hợp

Khi Backend đang chạy và đã có tài khoản admin, từ thư mục gốc:

```powershell
.\web\scripts\verify-admin.ps1
```

Script hỏi mật khẩu admin và cần biến `BCV_DB_PASSWORD` để dọn dữ liệu thử; nó chỉ tạo và xóa bản ghi thử của chính nó. Tham số tùy chọn: `-ApiBase`, `-Email`, `-Psql`, `-ConfigPath`. Kết quả kiểm thử gần nhất: [TESTING.md](TESTING.md); lịch sử thay đổi: [CHANGELOG.md](CHANGELOG.md).

## Xử lý sự cố

| Hiện tượng | Cách xử lý |
| --- | --- |
| Lỗi mạng khi đăng nhập | Kiểm tra http://127.0.0.1:5081/health và `BCV_API_TARGET` |
| "Không đủ quyền" | Tài khoản không phải `ADMIN` |
| Đột nhiên bị đăng xuất | Phiên hết hạn (30 ngày không dùng) hoặc tài khoản bị khóa; đăng nhập lại |
| Bản đồ trống | Chưa có Maptiles key, hoặc vừa thêm key mà chưa chạy lại `npm run dev` |
| Báo trùng cổng 5173 | Một server web cũ còn chạy; dừng nó hoặc đổi `WEB_PORT` |

## API quản trị

Mọi API ngoài `login` yêu cầu Bearer token của tài khoản `ADMIN`.

```text
POST   /api/admin/login
GET    /api/admin/me
GET    /api/admin/dashboard?days=30
GET    /api/admin/lookups
GET    /api/admin/data/{resource}?q=&status=&page=1&pageSize=12
GET    /api/admin/data/{resource}/{id}
POST   /api/admin/data/{resource}
PUT    /api/admin/data/{resource}/{id}
DELETE /api/admin/data/{resource}/{id}
POST   /api/admin/images?purpose=place|card   (image/jpeg)
```

`resource` gồm `users`, `regions`, `place-types`, `places`, `cards`, `collections`, `quests`, `items`. Người dùng và vật phẩm chỉ cho cập nhật. Đăng xuất dùng chung `POST /api/account/logout`.

## Đóng góp

Xem [Đóng góp](../README.md#đóng-góp) ở README gốc.

## Giấy phép

UNLICENSED © 2026 Nhóm ĐAHK1262703. Xem [Giấy phép](../README.md#giấy-phép) ở README gốc.
