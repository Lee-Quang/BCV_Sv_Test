# App Android Bước Chân Việt (*android*)

App Kotlin cho người dùng: đọc số bước, đi hành trình ảo trên bản đồ Goong và mở khóa địa danh.

App gọi Backend API qua HTTP, không truy cập database trực tiếp. Mã nguồn chia theo nghiệp vụ trong `feature/`; phần dùng chung nằm trong `core/`.

## Mục lục

- [Cài đặt](#cài-đặt)
- [Sử dụng](#sử-dụng)
- [Luồng sử dụng](#luồng-sử-dụng)
- [Ghi nhận bước chân](#ghi-nhận-bước-chân)
- [Cấu trúc mã nguồn](#cấu-trúc-mã-nguồn)
- [Kiểm thử](#kiểm-thử)
- [Xử lý sự cố](#xử-lý-sự-cố)
- [Đóng góp](#đóng-góp)
- [Giấy phép](#giấy-phép)

## Cài đặt

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

Khi trình diễn trên thiết bị không có bước thật, bấm nút icon người đi bộ có dấu "+" cạnh chữ **Khám phá** để ghi 2.000 bước mẫu vào Health Connect, rồi bấm **Đồng bộ** (hoặc chờ tự đồng bộ).

## Luồng sử dụng

1. **Đăng nhập / Đăng ký** bằng email và mật khẩu; có chức năng quên mật khẩu (mã gửi qua email nếu backend đã cấu hình SMTP).
2. **Chọn sở thích:** 1–3 trong 12 loại địa danh.
3. **Mức vận động:** Ít vận động, Trung bình hoặc Năng động.
4. **Health Connect:** cấp quyền đọc số bước; có thể chọn "Để sau".
5. **Điểm xuất phát:** dùng điểm hệ thống đề xuất hoặc vị trí hiện tại. Backend chọn địa danh `ACTIVE` gần nhất trong bán kính 75 km. Mỗi tài khoản chỉ lưu điểm xuất phát một lần.
6. **Khám phá:** bản đồ hiển thị vị trí ảo, hành trình đang đi và đường đi theo đường phố tới điểm đến. Icon người đi bộ quay mặt theo hướng đi. Số bước được tự động đồng bộ lên backend mỗi phút khi màn này mở (xem [Ghi nhận bước chân](#ghi-nhận-bước-chân)).
7. **Gợi ý điểm đến:** 3 địa danh chưa mở khóa phù hợp nhất, kèm khoảng cách và số bước. Chọn một địa danh để bắt đầu; nếu đang có hành trình khác, app hỏi có chuyển sang hành trình mới không.
8. **Hành trình:** tiến độ, bản đồ tuyến đường và lịch sử. Khi đã hoàn thành, bấm thẻ điểm đến để xem ảnh và nội dung địa danh vừa mở khóa.
9. Các tab và màn khác: **Bộ sưu tập** thẻ địa danh, **Nhiệm vụ** ngày/tuần kèm vật phẩm (Giày tăng tốc, Vé máy bay), **Hồ sơ** với thống kê, **Thông báo**.

Mọi màn tải dữ liệu từ server hiện skeleton có hình dạng giống nội dung thật trong lúc chờ.

## Ghi nhận bước chân

Health Connect là nguồn bước chính, vì nó đã gộp mọi ứng dụng ghi bước (Google Fit, đồng hồ, tính năng tự đếm của Health Connect…) và không tính hai lần những khoảng thời gian chồng nhau. Mã: `feature/discovery/data/HealthConnectStepSource.kt`.

| Tình huống | Nguồn bước của hành trình |
| --- | --- |
| Trong 30 ngày qua có ứng dụng **khác BCV** ghi bước vào Health Connect | Tổng bước của Health Connect |
| Không có ứng dụng nào ghi bước, máy có Google Play services **và** người dùng đã cho phép **Hoạt động thể chất** | Recording API của Google Play services (`RecordingStepCounter`), cộng với bước thử của BCV |
| Còn lại (chưa cho phép, hoặc máy không có Google Play services) | Chỉ bước thử của BCV |

- **Bước từ Recording API chỉ lưu trong app, không ghi vào Health Connect**, nên không bị đọc lại và cộng trùng. BCV chỉ ghi vào Health Connect bước thử (+2.000).
- Quyền Hoạt động thể chất chỉ được hỏi khi Health Connect không có nguồn đếm bước. Không cần cài thêm app nào: Recording API chạy trong Google Play services có sẵn trên máy.
- Recording API bắt đầu ghi khi app đăng ký lần đầu cho hành trình (lúc tạo hành trình hoặc mở màn Khám phá); bước đi trước đó không có.
- Sau khi đăng ký, Google Play services tự đếm cả khi app tắt hay điện thoại khởi động lại, và giữ dữ liệu 10 ngày trên máy. Không mở app đồng bộ quá 10 ngày thì phần bước cũ hơn bị mất.
- Hành trình đã bắt đầu đếm bằng Recording API thì dùng nó tới hết hành trình, để tổng bước không bị tụt khi nguồn thay đổi. Hành trình sau dùng Health Connect thì app hủy đăng ký.
- **Tự động đồng bộ:** khi màn Khám phá đang mở, app tự gửi bước mới lúc mở màn và mỗi phút một lần (chỉ gửi khi tổng bước tăng), không hiện trạng thái "Đang đồng bộ" và lỗi thì giữ nguyên tiến độ để lần sau thử lại. Nút **Đồng bộ** vẫn dùng được để cập nhật ngay.
- Nút "+2.000 bước thử" ghi bản ghi thử vào Health Connect; mỗi bản ghi bắt đầu sau bản ghi trước nên luôn cộng đủ 2.000.

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
