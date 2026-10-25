# Bước Chân Việt Admin

Website quản trị React, dùng dữ liệu PostgreSQL thật thông qua module `/api/admin` của backend ASP.NET Core. Phạm vi bám theo tài liệu **ĐAHK1262703 – Đồ án tốt nghiệp HK1 2026**, mục 2.3.8–2.3.11, chức năng F33–F50.

## Vai trò React, Vite và Node.js

Giao diện được xây bằng **ReactJS và JavaScript/JSX**, đúng công nghệ web quản trị trong tài liệu. **Vite** là công cụ chạy môi trường phát triển và build mã React; **Node.js/npm** dùng để chạy các công cụ và cài thư viện. Backend nghiệp vụ vẫn là **ASP.NET Core/C#**, truy cập PostgreSQL/PostGIS; dự án không dùng Node.js làm backend nghiệp vụ.

Khi chạy `npm run build`, Vite tạo các tệp HTML/CSS/JavaScript trong `dist/`. Có thể triển khai thư mục này trên IIS, Nginx hoặc dịch vụ hosting tĩnh, cấu hình `/api` đến ASP.NET Core; không bắt buộc chạy Node.js trên máy phục vụ web sau khi đã build. Tài liệu chính thức của React hướng dẫn dùng Vite tại https://react.dev/learn/build-a-react-app-from-scratch.

Có thể mô tả trong báo cáo: “Website quản trị được xây dựng bằng ReactJS; sử dụng Vite và Node.js/npm làm công cụ phát triển, quản lý thư viện và đóng gói; giao tiếp với Backend API ASP.NET Core.”

## Thiết lập theo từng máy

Mở thư mục gốc `BCV-main` trong VS Code. Dùng PowerShell 7 cho các lệnh dưới đây. Máy cần Node.js 22.12+ hoặc 24+, .NET SDK 10 và PostgreSQL có PostGIS. Không cần đặt dự án trong ổ đĩa hoặc thư mục giống máy của người khác.

### 1. Tạo file cấu hình riêng

Chạy từ thư mục gốc:

```powershell
Copy-Item backend/local.settings.example.json backend/local.settings.json
Copy-Item web/.env.example web/.env.local
```

Chỉ sao chép lần đầu; không ghi đè nếu đã có cấu hình riêng. Hai file đích được bỏ qua bởi Git. Các file mẫu không chứa mật khẩu và có thể dùng chung khi chuyển dự án. Nếu chuyển dự án bằng ZIP hoặc chép cả thư mục, hãy loại các file cấu hình riêng của máy cũ và tạo lại từ file mẫu trên máy mới.

Sửa `backend/local.settings.json` theo máy đang chạy:

```json
{
  "DbHost": "127.0.0.1",
  "DbPort": 5432,
  "DbName": "bcv",
  "DbUser": "postgres",
  "ApiUrls": "http://0.0.0.0:5081",
  "SmtpHost": "smtp.gmail.com",
  "SmtpPort": 587,
  "SmtpEnableSsl": true,
  "SmtpUser": "",
  "SmtpFrom": "",
  "PsqlPath": ""
}
```

| Trường | Đặt theo máy của bạn |
| --- | --- |
| `DbHost` | Máy PostgreSQL; dùng `127.0.0.1` nếu chạy cùng máy |
| `DbPort` | Cổng PostgreSQL thực tế, ví dụ 5432 hoặc 5433 |
| `DbName`, `DbUser` | Tên database và tài khoản PostgreSQL đã tạo trên máy đó |
| `ApiUrls` | Địa chỉ/cổng backend; `http://0.0.0.0:5081` nhận kết nối từ các thiết bị trong mạng |
| `SmtpUser` | Email SMTP của chính bạn; để trống nếu chưa dùng gửi thư |
| `SmtpFrom` | Địa chỉ gửi thư; để trống để dùng `SmtpUser` |
| `PsqlPath` | Đường dẫn `psql.exe` nếu chưa có trong PATH, ví dụ `C:/Program Files/PostgreSQL/18/bin/psql.exe`; macOS/Linux có thể dùng đường dẫn `psql` tương ứng |

`run-local.ps1`, `create-admin.ps1` và `verify-admin.ps1` đọc cùng file này. Nếu muốn dùng file khác, truyền `-ConfigPath <đường-dẫn-file>`. Thứ tự ưu tiên: biến môi trường của terminal → biến môi trường người dùng → file cấu hình máy → giá trị mặc định.

Có thể ghi đè tạm trong terminal, chẳng hạn:

```powershell
$env:BCV_DB_PORT = '5433'
$env:BCV_API_URLS = 'http://0.0.0.0:5082'
.\backend\run-local.ps1
```

Các biến hỗ trợ: `BCV_DB_HOST`, `BCV_DB_PORT`, `BCV_DB_NAME`, `BCV_DB_USER`, `BCV_API_URLS`, `BCV_SMTP_HOST`, `BCV_SMTP_PORT`, `BCV_SMTP_ENABLE_SSL`, `BCV_SMTP_USER`, `BCV_SMTP_FROM`, `BCV_PSQL_PATH`. Trên macOS/Linux, dùng biến môi trường của terminal và chạy các script bằng PowerShell 7 (`pwsh`).

### 2. Thiết lập mật khẩu và database

Không ghi mật khẩu database, SMTP hoặc R2 vào JSON hay mã nguồn. Trong mỗi terminal cần dùng backend/kiểm thử, nhập:

```powershell
$pw = Read-Host 'Mat khau PostgreSQL tren may nay' -AsSecureString
$env:BCV_DB_PASSWORD = [System.Net.NetworkCredential]::new('', $pw).Password
```

Trên Windows, nếu muốn lưu cho tài khoản Windows hiện tại:

```powershell
[Environment]::SetEnvironmentVariable('BCV_DB_PASSWORD', $env:BCV_DB_PASSWORD, 'User')
```

Database và tài khoản admin nằm trong PostgreSQL, không được sao chép theo mã nguồn. Với máy mới, tạo database có tên khớp `DbName`, cài PostGIS cho PostgreSQL, rồi nạp schema/dữ liệu theo [hướng dẫn database](../db/README.md). Không chạy lại schema trên database đã có bảng. Nếu khôi phục một bản backup đầy đủ thì dùng tài khoản đã có trong bản backup.

SMTP là tùy chọn. Khi cần gửi email, điền `SmtpUser` và nhập App Password riêng vào biến `BCV_SMTP_PASSWORD` theo cách an toàn tương tự. Không cấu hình SMTP thì mã đặt lại mật khẩu được ghi vào log phát triển. R2 là tùy chọn; đặt năm biến `BCV_R2_ACCOUNT_ID`, `BCV_R2_BUCKET`, `BCV_R2_PUBLIC_URL`, `BCV_R2_ACCESS_KEY_ID`, `BCV_R2_SECRET_ACCESS_KEY` của tài khoản bạn. Có thể dùng `backend/set-local-secrets.ps1 -R2` trên Windows. Chưa có R2 thì ảnh được lưu inline.

### 3. Chạy backend và tạo admin trên máy mới

Terminal thứ nhất, tại thư mục gốc:

```powershell
.\backend\run-local.ps1
```

Mặc định kiểm tra http://127.0.0.1:5081/health phải trả `{"status":"ok"}`. Nếu đổi `ApiUrls`, kiểm tra theo cổng đã chọn. `0.0.0.0` là địa chỉ lắng nghe, khi mở trên trình duyệt cùng máy hãy dùng `127.0.0.1` hoặc `localhost`.

Nếu database mới chưa có tài khoản admin, dừng backend bằng Ctrl+C rồi chạy:

```powershell
.\backend\create-admin.ps1 -Email admin@bcv.local
.\backend\run-local.ps1
```

Script hỏi mật khẩu admin ít nhất 12 ký tự, có chữ và số, rồi lưu mã băm PBKDF2. Email đã tồn tại sẽ bị từ chối; script không nâng quyền hoặc đổi mật khẩu tài khoản cũ. Tài khoản đăng ký qua app là `USER`, không đăng nhập được trang quản trị.

### 4. Cấu hình và chạy React

Sửa `web/.env.local` cho khớp backend:

```dotenv
BCV_API_TARGET=http://127.0.0.1:5081
WEB_HOST=127.0.0.1
WEB_PORT=5173
VITE_GOONG_MAPTILES_KEY=
```

Nếu backend ở cổng 5082, đổi `BCV_API_TARGET` thành `http://127.0.0.1:5082`. Nếu backend ở máy khác, thay `127.0.0.1` bằng IP/hostname của máy backend. Địa chỉ này được dùng bởi tiến trình Vite trên máy chạy web. `WEB_PORT` là cổng web, có thể đổi nếu bị trùng. `WEB_HOST=0.0.0.0` cho phép thiết bị khác trong cùng mạng mở web bằng IP máy chạy Vite; kiểm tra tường lửa theo mạng thực tế.

Nhập Maptiles Key của chính bạn vào `VITE_GOONG_MAPTILES_KEY`. Nếu để trống, web thử đọc `bcvGoongMaptilesKey` từ `android/dev.properties` trên cùng máy. Key này cần để hiển thị bản đồ, không ảnh hưởng đăng nhập/danh sách. Không đưa mật khẩu hoặc khóa R2 vào biến `VITE_*`.

Terminal thứ hai:

```powershell
cd web
npm ci
npm run dev
```

Mở URL `Local` mà Vite in ra, mặc định http://127.0.0.1:5173. Sau khi sửa `.env.local` hoặc cấu hình backend, dừng và chạy lại server tương ứng. `npm run dev` không tự khởi động PostgreSQL hoặc backend. Để dừng server, nhấn Ctrl+C trong terminal đang chạy.

## Đăng nhập và sử dụng

Tài khoản trong database của máy thiết lập ban đầu: **`admin@bcv.local`**. Mật khẩu đăng nhập được cung cấp trong cuộc trò chuyện, không ghi trong mã nguồn. Máy mới dùng database riêng phải tạo admin bằng script ở trên; tài khoản không tự xuất hiện chỉ vì sao chép dự án. Tài khoản app `test.bcv@example.com` là người dùng thường và sẽ bị từ chối khi đăng nhập web quản trị.

1. Mở http://127.0.0.1:5173 trong trình duyệt.
2. Nhập email quản trị và mật khẩu, chọn **Đăng nhập quản trị**.
3. Dùng thanh điều hướng bên trái để chuyển nhóm chức năng. Trên màn hình nhỏ, chọn biểu tượng menu ở góc trên bên trái.
4. Nhập từ khóa hoặc chọn bộ lọc; dùng **Trang trước / Trang sau** để phân trang và **Xuất CSV** để lấy toàn bộ kết quả đang lọc.
5. Chọn **Thêm** hoặc **Chỉnh sửa**, nhập dữ liệu và chọn **Lưu thay đổi**. Các thay đổi trạng thái cần xác nhận thêm; lỗi lưu giữ lại biểu mẫu để sửa.
6. Với dữ liệu mẫu ở **Bản nháp**, kiểm tra nội dung trước khi chuyển **Đang hoạt động**. Kích hoạt địa danh trước, sau đó thẻ và bộ sưu tập liên quan. Thẻ hoạt động bắt buộc có ảnh. Nhiệm vụ/vật phẩm chỉ được sử dụng trên app khi đang hoạt động.
7. Với bộ sưu tập, tìm và tích/bỏ tích các thẻ rồi lưu. Với bản đồ, bật **Chọn tọa độ trên bản đồ**, đặt hoặc kéo ghim, mở biểu mẫu và xác nhận lưu tọa độ.
8. Chọn biểu tượng đăng xuất cạnh tài khoản ở cuối thanh điều hướng để kết thúc phiên.

Nếu vừa khởi động lại backend, hãy đăng nhập lại vì phiên hiện lưu trong bộ nhớ. Nếu báo lỗi mạng, kiểm tra `/health`, backend cổng 5081 và proxy trong `.env.local`. Nếu báo không đủ quyền, kiểm tra email đang đăng nhập có vai trò `ADMIN`. Nếu báo trùng cổng, dừng server cũ trong terminal trước khi chạy lại; không khởi chạy đồng thời hai backend ở cổng 5081.

Trên Windows, nếu script bị chặn vì tệp tải xuống, sau khi kiểm tra nội dung có thể gỡ dấu chặn riêng cho script bằng `Unblock-File .\backend\run-local.ps1` từ thư mục gốc. Không cần thay đổi ExecutionPolicy toàn máy.

## Cấu hình

Vite tự chuyển tiếp `/api` và `/health` sang backend cổng 5081. Nếu đổi cổng backend, sao chép `.env.example` thành `.env.local` và sửa `BCV_API_TARGET`, sau đó khởi động lại Vite.

Goong Maptiles Key được đọc từ `../android/dev.properties` (`bcvGoongMaptilesKey`) hoặc `VITE_GOONG_MAPTILES_KEY` trong `.env.local`. Chỉ Maptiles Key dùng để hiển thị bản đồ được đưa vào trình duyệt. Không đưa mật khẩu database, Gmail hoặc R2 vào biến `VITE_*`.

## Chức năng

| Màn hình | Chức năng |
| --- | --- |
| Đăng nhập | Xác thực, kiểm tra tài khoản `ADMIN` đang hoạt động, đăng xuất, xử lý hết phiên |
| Bảng điều khiển | Tổng người dùng, hành trình, hoàn thành, lượt khám phá, thẻ thu thập; biểu đồ 7/30/90 ngày; khu vực theo ID tăng dần; hành trình gần đây; nội dung thiếu ảnh |
| Người dùng | Tìm tên/email, lọc trạng thái, xem thông tin và số liệu cá nhân, khóa/mở tài khoản có xác nhận |
| Khu vực | Tạo/sửa tên, mô tả, trạng thái; tìm kiếm, phân trang, xuất CSV |
| Địa danh | Tạo/sửa nội dung, fun fact, tọa độ, loại hình, khu vực, đánh giá, trạng thái; thêm/bỏ ảnh bằng URL hoặc tải tệp |
| Bản đồ địa danh | Xem ghim Goong, tìm địa danh, chọn/kéo ghim, đưa tọa độ vào biểu mẫu để lưu; tạo địa danh từ ghim |
| Loại địa danh | Quản lý danh mục dùng chung |
| Thẻ | Tạo/sửa ảnh, nội dung, liên kết địa danh; mỗi địa danh tối đa một thẻ |
| Bộ sưu tập | Tạo/sửa và chọn/bỏ các thẻ bằng danh sách tìm kiếm được |
| Nhiệm vụ | Tạo/sửa nhiệm vụ ngày/tuần, điều kiện, mục tiêu, vật phẩm và số lượng thưởng, trạng thái |
| Vật phẩm | Cấu hình Giày tăng tốc, Vé máy bay; hiệu ứng áp dụng cho các lần sử dụng sau |
| Thông tin hệ thống | Kiểm tra kết nối và bảng đối chiếu chức năng đồ án |

Các danh sách có tìm kiếm, phân trang và xuất CSV toàn bộ kết quả đang lọc. Khu vực được sắp theo ID tăng dần (1, 2, 3, …), thống nhất giữa danh sách, dashboard và các ô chọn khu vực; ID được hiển thị để đối chiếu. Trường bắt buộc, kiểu dữ liệu, tọa độ, trạng thái và khóa ngoại đều được kiểm tra ở backend.

Nội dung được ngưng sử dụng bằng trạng thái `INACTIVE`, không xóa cứng dữ liệu đang liên quan đến lịch sử người dùng. Một nhiệm vụ đã có tiến độ chỉ được đổi tên/trạng thái; tạo nhiệm vụ mới để thay đổi điều kiện và phần thưởng. Thao tác lưu địa danh/ảnh và bộ sưu tập/thẻ thực hiện trong giao dịch.

Các API Android chỉ hiển thị/cấp mới nội dung `ACTIVE`; dữ liệu `DRAFT` chỉ xuất hiện trong quản trị. Một số dữ liệu demo được tạo ở trạng thái `DRAFT`, vì vậy quản trị viên cần kiểm tra và kích hoạt trước khi sử dụng trên app. Kích hoạt thẻ yêu cầu có ảnh và địa danh đang hoạt động.

Khóa người dùng sẽ hủy các phiên của người dùng đó. Không cho tự khóa tài khoản quản trị hoặc đổi trạng thái tài khoản quản trị khác qua màn hình người dùng. API kiểm tra vai trò và trạng thái quản trị trong database trên mỗi yêu cầu; mã băm mật khẩu không được trả về.

Ảnh được chuyển sang JPEG và tối ưu ở trình duyệt (cạnh dài tối đa 1600 px). Backend giới hạn 5 MB/ảnh JPEG. Nếu R2 được cấu hình, ảnh dùng kho lưu trữ phía server; nếu chưa cấu hình, ảnh được lưu dạng data URI trong cột ảnh. Tải ảnh là thao tác riêng trước khi lưu nội dung, vì vậy hủy biểu mẫu sau khi tải có thể để lại tệp R2 chưa được tham chiếu.

## Build và triển khai

```powershell
npm run build
```

Kết quả ở `dist/`. Khi triển khai, phục vụ nội dung tĩnh bằng HTTPS và cấu hình reverse proxy `/api` đến backend; proxy trong `vite.config.js` chỉ dành cho phát triển. Không dùng `npm run preview` làm server production. Phiên hiện theo backend: tồn tại tối đa 12 giờ trong bộ nhớ và mất khi backend khởi động lại.

## Các API bổ sung

```text
POST /api/admin/login
GET  /api/admin/me
GET  /api/admin/dashboard?days=30
GET  /api/admin/lookups
GET  /api/admin/data/{resource}?q=&status=&page=1&pageSize=12
GET  /api/admin/data/{resource}/{id}
POST /api/admin/data/{resource}
PUT  /api/admin/data/{resource}/{id}
POST /api/admin/images              (image/jpeg)
```

Các resource: `users`, `regions`, `place-types`, `places`, `cards`, `collections`, `quests`, `items`. Người dùng và vật phẩm chỉ được cập nhật. Mọi API ngoài login yêu cầu Bearer token có quyền `ADMIN`. Resource địa danh hỗ trợ thêm `regionId` và `typeId`. `/api/account/logout` dùng chung với ứng dụng Android.

Không tự thay đổi schema 25 bảng hoặc dữ liệu mẫu khi chạy web. Các số liệu hiển thị lấy từ database hiện có, không dùng dữ liệu mô phỏng.

## Kiểm tra tích hợp

Sau khi backend chạy và đã có tài khoản quản trị:

```powershell
.\web\scripts\verify-admin.ps1
```

Chạy lệnh từ thư mục gốc, nhập mật khẩu quản trị khi được hỏi. Tham số `-ApiBase`, `-Email`, `-Psql`, `-ConfigPath` cho phép đổi backend, email, đường dẫn psql và file cấu hình. Mặc định script đọc thông tin backend và database từ cùng `backend/local.settings.json`, không cố định ổ đĩa PostgreSQL. Biến `BCV_DB_PASSWORD` trong terminal hoặc môi trường người dùng phải được thiết lập để dọn dữ liệu thử. Script tạo bản ghi có tiền tố ngẫu nhiên riêng rồi chỉ xóa đúng các ID đã tạo, có đối chiếu tên; không sửa dữ liệu nghiệp vụ có sẵn. Các kiểm tra bao gồm phân trang, không lộ mã băm, tọa độ, ảnh địa danh, chống trùng thẻ, gán/bỏ thẻ, giao dịch khi lưu lỗi, nhiệm vụ và bảo vệ tài khoản quản trị.
