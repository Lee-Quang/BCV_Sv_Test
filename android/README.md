# App Android Bước Chân Việt (*android*)

App Kotlin cho người dùng: đếm bước bằng cảm biến điện thoại, đi hành trình ảo trên bản đồ Goong và mở khóa địa danh.

App gọi Backend API qua HTTP, không truy cập database trực tiếp. Mã nguồn chia theo nghiệp vụ trong `feature/`; phần dùng chung nằm trong `core/`.

## Mục lục

- [Cài đặt](#cài-đặt)
- [Sử dụng](#sử-dụng)
- [Luồng sử dụng](#luồng-sử-dụng)
- [Cấu trúc mã nguồn](#cấu-trúc-mã-nguồn)
- [Kiểm thử](#kiểm-thử)
- [Xử lý sự cố](#xử-lý-sự-cố)
- [Đóng góp](#đóng-góp)
- [Giấy phép](#giấy-phép)

## Cài đặt

### Đếm bước trực tiếp trên điện thoại

App ưu tiên cảm biến `TYPE_STEP_COUNTER`, xin quyền **Hoạt động thể chất** và dùng dịch vụ foreground loại `health` để tiếp tục đếm khi khóa màn hình. Thông báo “BCV đang đếm bước chân” hiện trong lúc có hành trình đang thực hiện. Thiết bị không có cảm biến này tiếp tục dùng Health Connect.

Mỗi hành trình có mốc cảm biến riêng; lần đọc đầu tiên chỉ lập mốc, không cộng các bước đã đi trước đó. Tổng bước được lưu trên thiết bị để tiếp tục sau khi mở lại app hoặc khởi động lại điện thoại. Đăng xuất hoặc hoàn thành hành trình sẽ dừng dịch vụ.

Khi màn Khám phá hoặc Hành trình đang hiển thị, app tự gửi bước mới mỗi 3 giây và chỉ hiển thị tiến độ đã được BE xác nhận. Cả hai màn đều có nút **Đồng bộ**; màn Hành trình giữ lại tiến độ khi lỗi để thử lại và ẩn nút khi hành trình đã hoàn thành. Khi ở nền, bước được lưu tại máy và gửi khi quay lại một trong hai màn hoặc bấm **Đồng bộ**. Đóng buộc ứng dụng sẽ dừng việc theo dõi cho đến khi mở lại app.

`journey.syncedSteps` trong API là tổng bước thực đã nhận, không gồm bước thưởng của Giày tăng tốc; app dùng giá trị này làm mốc khi tiếp tục đếm, tránh cộng lại bước đã đồng bộ.

### Yêu cầu

- Android Studio bản mới (kèm JDK) và Android SDK.
- Thiết bị hoặc emulator Android 8.1 trở lên (minSdk 27), có Health Connect để đọc số bước.
- Backend đang chạy (xem [backend/README.md](../backend/README.md#cài-đặt)).

### Cấu hình `dev.properties`

Tạo `android/dev.properties` (Git bỏ qua file này):

```properties
bcvApiBaseUrl=http://192.168.1.10:5081/
bcvGoongMaptilesKey=<Goong Maptiles Key>
bcvGoongApiKey=<Goong REST API Key>
```

| Khóa | Ý nghĩa | Nếu bỏ trống |
| --- | --- | --- |
| `bcvApiBaseUrl` | Địa chỉ Backend, kết thúc bằng `/` | `http://10.0.2.2:5081/` (máy tính, nhìn từ emulator) |
| `bcvGoongMaptilesKey` | Hiển thị bản đồ Goong | Bản đồ trống |
| `bcvGoongApiKey` | Tra địa chỉ từ tọa độ và chỉ đường theo đường phố | Không hiện địa chỉ, không vẽ đường đi |

- **Điện thoại thật:** dùng IP Wi-Fi của máy tính chạy backend (xem bằng `ipconfig`); điện thoại và máy tính phải cùng mạng.
- Giá trị được đóng vào app lúc build, nên **sửa xong phải build lại**.
- Bản release nhận giá trị qua Gradle property, ví dụ `-PbcvApiBaseUrl=https://... -PbcvGoongMaptilesKey=...`, và cần URL HTTPS.

### Build và cài

```powershell
cd android
.\gradlew.bat installDebug
```

Hoặc mở thư mục `android/` bằng Android Studio rồi bấm **Run**. Một số điện thoại (ví dụ vivo) hỏi xác nhận cài qua USB; nếu Gradle báo `INSTALL_FAILED_ABORTED`, bấm **Cài đặt** trên điện thoại rồi chạy lại.

## Sử dụng

Mở app, đăng ký hoặc đăng nhập. Phiên được giữ 30 ngày và tự gia hạn, nên lần sau mở app sẽ vào thẳng màn đang dở (thiết lập hoặc Khám phá); chỉ khi phiên bị từ chối (đăng xuất, đổi mật khẩu, bị khóa) mới phải đăng nhập lại.

Khi trình diễn trên thiết bị không có bước thật, bấm nút icon người đi bộ có dấu "+" cạnh chữ **Khám phá** để ghi 2.000 bước mẫu vào Health Connect, rồi bấm **Đồng bộ**.

## Luồng sử dụng

1. **Đăng nhập / Đăng ký** bằng email và mật khẩu; có chức năng quên mật khẩu (mã gửi qua email nếu backend đã cấu hình SMTP).
2. **Chọn sở thích:** 1–3 trong 12 loại địa danh.
3. **Mức vận động:** Ít vận động, Trung bình hoặc Năng động.
4. **Health Connect:** cấp quyền đọc số bước; có thể chọn "Để sau".
5. **Điểm xuất phát:** dùng điểm hệ thống đề xuất hoặc vị trí hiện tại. Backend chọn địa danh `ACTIVE` gần nhất trong bán kính 75 km. Mỗi tài khoản chỉ lưu điểm xuất phát một lần.
6. **Khám phá:** bản đồ hiển thị vị trí ảo, hành trình đang đi và đường đi theo đường phố tới điểm đến. Icon người đi bộ quay mặt theo hướng đi. Số bước từ Health Connect được đồng bộ lên backend để tính tiến độ.
7. **Gợi ý điểm đến:** 3 địa danh chưa mở khóa phù hợp nhất, kèm khoảng cách và số bước. Chọn một địa danh để bắt đầu; nếu đang có hành trình khác, app hỏi có chuyển sang hành trình mới không.
8. **Hành trình:** tiến độ, bản đồ tuyến đường và lịch sử. Khi đã hoàn thành, bấm thẻ điểm đến để xem ảnh và nội dung địa danh vừa mở khóa.
9. Các tab và màn khác: **Bộ sưu tập** thẻ địa danh, **Nhiệm vụ** ngày/tuần kèm vật phẩm (Giày tăng tốc, Vé máy bay), **Hồ sơ** với thống kê, **Thông báo**.

Mọi màn tải dữ liệu từ server hiện skeleton có hình dạng giống nội dung thật trong lúc chờ.

## Cấu trúc mã nguồn

```text
app/src/main/java/com/project/bcv/
├── core/        # Dùng chung: network, map, ui (skeleton, popup...), presentation, di
└── feature/     # Theo nghiệp vụ: account, location, discovery, journey,
                 # collection, questitem, statsnotify
```

Mỗi tính năng chia `presentation` (Activity + ViewModel), `domain` (model, interface repository) và `data` (cài đặt repository, nguồn dữ liệu).

## Kiểm thử

Test giao diện và tích hợp nằm trong `app/src/androidTest/`, chạy trên thiết bị hoặc emulator đang kết nối:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

## Xử lý sự cố

**App báo "Chưa thể kết nối":**

1. Trên điện thoại, mở trình duyệt vào `http://<IP-máy-tính>:5081/health`. Kết quả đúng là `{"status":"ok"}`.
2. Nếu bị timeout, Windows Firewall đang chặn. Mở PowerShell bằng quyền Administrator và mở cổng 5081 cho mạng nội bộ (thay dải IP cho đúng):

   ```powershell
   New-NetFirewallRule -DisplayName 'BCV API for Android phone' -Direction Inbound -Action Allow -Protocol TCP -LocalPort 5081 -RemoteAddress 192.168.1.0/24 -Profile Private,Public
   ```

   Xóa khi không cần nữa: `Remove-NetFirewallRule -DisplayName 'BCV API for Android phone'`.
3. Nếu IP máy tính thay đổi, sửa `bcvApiBaseUrl` rồi build lại.
4. Giữ Wi-Fi bật. Bản debug ưu tiên Wi-Fi khi gọi API có IP nội bộ, vì dữ liệu di động không truy cập được địa chỉ `192.168.x.x`.

**Bản đồ trống hoặc không có đường đi:** kiểm tra hai khóa Goong trong `dev.properties` rồi build lại.

**Không có điểm đề xuất, bộ sưu tập hoặc nhiệm vụ:** nội dung trong database còn `DRAFT`. Xem [db/README.md](../db/README.md#trạng-thái-nội-dung).

## Đóng góp

Xem [Đóng góp](../README.md#đóng-góp) ở README gốc.

## Giấy phép

UNLICENSED © 2026 Nhóm ĐAHK1262703. Biểu tượng Material Symbols dùng trong app theo giấy phép Apache 2.0, xem [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
