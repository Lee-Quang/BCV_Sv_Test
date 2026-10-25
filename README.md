# Bước Chân Việt

Theo [tài liệu đồ án](https://docs.google.com/document/d/1GAQdAqCcwr_IivfHzInilF0LhbWyniN6AONmzqEHWjo/edit?tab=t.0), hệ thống dùng Android → Backend API → PostgreSQL/PostGIS. Android không kết nối trực tiếp cơ sở dữ liệu.

| Thư mục | Nội dung |
| --- | --- |
| `android/` | Ứng dụng Android Kotlin. `feature/` chia theo module nghiệp vụ (account, location, discovery, journey, collection, questitem, statsnotify); mỗi tính năng có `presentation` (Activity + ViewModel), `domain` (model, interface repository) và `data` (cài đặt, nguồn dữ liệu). Phần dùng chung ở `core/` (network, presentation, ui, map, di). |
| `backend/` | ASP.NET Core API dạng modular monolith theo mục 3.2 của tài liệu kiến trúc: các module Account, StepLocation, Discovery, Journey, PlaceCollection, QuestItem, StatsNotify (và Shared), mỗi module chia lớp Api, Application, Domain, Infrastructure. |
| `db/` | Schema 25 bảng và dữ liệu mẫu PostgreSQL BCV hiện có. |

## Chạy backend

Cài .NET SDK 10.0 và đảm bảo PostgreSQL BCV đang chạy tại `127.0.0.1:5433`. Đặt chuỗi kết nối bằng biến môi trường trong terminal cá nhân, không lưu mật khẩu vào dự án:

```powershell
$env:ConnectionStrings__Bcv = 'Host=127.0.0.1;Port=5433;Database=bcv;Username=postgres;Password=<mật khẩu>'
dotnet run --project backend/Bcv.Api --urls http://0.0.0.0:5080
```

Hoặc chạy `./backend/run-local.ps1`; script hỏi mật khẩu PostgreSQL bằng hộp nhập an toàn rồi khởi động API trên cổng 5080.

Các endpoint: `GET /health`, `POST /api/account/register`, `POST /api/account/login`, `GET /api/account/me`, `POST /api/account/logout`, `GET/PUT /api/interests`, `GET/PUT /api/activity-level`, `GET/PUT /api/starting-point`, `POST /api/starting-point/resolve-current`, `GET /api/explore`, `POST /api/explore/sync-steps`. Cả đăng ký và đăng nhập đều trả phiên cùng cờ `needsInterestSelection`, `needsActivityLevel`, `needsVirtualLocation`. Các API thiết lập và Khám phá yêu cầu Bearer token.

Tài khoản mới được ghi vào `app_user` với mật khẩu PBKDF2-SHA256 có salt. Vì thiết kế đăng ký chỉ có email/mật khẩu, `full_name` ban đầu là `Người khám phá`; người dùng có thể sửa ở luồng hồ sơ sau này. Tài khoản demo có bcrypt hash của mật khẩu không còn biết, nên không dùng để đăng nhập.

Phiên hiện lưu trong bộ nhớ tiến trình Backend; khởi động lại API sẽ đăng xuất các phiên. Logout hủy token ở Backend. Dùng HTTPS khi triển khai ngoài máy phát triển.

## Chạy Android

Mở thư mục `android/` bằng Android Studio và chạy app trên emulator. Bản debug mặc định gọi API qua `http://10.0.2.2:5080/`. Với thiết bị thật, tạo `android/dev.properties` chứa `bcvApiBaseUrl=http://<IP-máy-tính>:5080/`; điện thoại và máy tính cần cùng mạng. Cấu hình bản đồ bằng `bcvGoongMaptilesKey=<Maptiles Key>` trong cùng file. `dev.properties` được bỏ qua bởi `.gitignore`; bản release nhận key qua Gradle property `-PbcvGoongMaptilesKey=...`. Key REST API của Goong không được đóng gói vào Android vì màn này không gọi REST API Goong. Script `backend/run-local.ps1` cho API lắng nghe trên cổng 5080 của máy tính. Nếu IP máy tính đổi, cập nhật `dev.properties` rồi build lại. Bản release cần URL HTTPS.

Nếu điện thoại báo “Chưa thể kết nối”, thử mở `http://<IP-máy-tính>:5080/health` trên điện thoại. Máy tính trả về `{"status":"ok"}` nhưng điện thoại bị timeout thì kiểm tra Windows Firewall. Với mạng Wi-Fi loại Public, mở PowerShell bằng quyền Administrator và tạo ngoại lệ chỉ cho điện thoại đang thử (thay hai IP nếu mạng đổi):

```powershell
New-NetFirewallRule -DisplayName 'BCV API for Android phone' -Direction Inbound -Action Allow -Protocol TCP -LocalPort 5080 -LocalAddress 192.168.0.103 -RemoteAddress 192.168.0.102 -Profile Public
```

Thử lại URL `/health` trên điện thoại trước khi đăng ký. Quy tắc này chỉ dùng cho phát triển trong mạng nội bộ; xóa bằng `Remove-NetFirewallRule -DisplayName 'BCV API for Android phone'` khi không còn cần.

Một số điện thoại chọn dữ liệu di động làm mạng mặc định khi Wi-Fi yếu, dù vẫn kết nối cùng Wi-Fi với máy tính. Dữ liệu di động không truy cập được IP nội bộ `192.168.x.x`. Bản debug hiện chọn kết nối Wi-Fi cho API có địa chỉ IP nội bộ; cần giữ Wi-Fi bật và cài lại APK sau khi build. Các API công khai và bản release tiếp tục dùng mạng mặc định của thiết bị.

App mở ở màn Đăng nhập; liên kết “Đăng ký” chuyển sang form tạo tài khoản bằng hiệu ứng trượt và mờ nhẹ. Màn đăng ký kiểm tra dữ liệu trên thiết bị và phản ánh lỗi từ Backend, gồm thiếu trường, sai định dạng, email trùng, lỗi mạng, trạng thái chờ và thành công. Lỗi của từng ô biến mất khi người dùng chạm hoặc sửa ô đó và chỉ được kiểm tra lại khi gửi form; sửa mật khẩu cũng xóa lỗi xác nhận mật khẩu cũ. Chỗ hiển thị lỗi được giữ sẵn để các ô nhập và nút không dịch chuyển khi có thông báo đỏ. Màn đăng nhập hiển thị “Quên mật khẩu?” theo thiết kế; hiện liên kết thông báo tính năng chưa khả dụng vì Backend chưa có luồng đặt lại mật khẩu và gửi email xác minh.

Sau đăng ký, form mờ và trượt lên nhẹ, màn “Đăng ký thành công!” xuất hiện; vòng tròn dấu xác nhận phóng nhẹ và nét tích được vẽ dần. Khi tắt animation hệ thống, dấu tích vẫn hiện đầy đủ. Nút “Tiếp tục thiết lập” mới chuyển mượt sang màn “Chọn sở thích”. Đăng nhập tài khoản chưa chọn sở thích chuyển thẳng tới màn này. Vòng tròn trang trí quay chậm khi màn đang hiển thị. Danh sách đọc từ `place_type` (hiện có 10 nhóm), không dùng nhãn mẫu trong ảnh thiết kế. Cần chọn từ 1 đến 3 nhóm; cả Android và `PUT /api/interests` đều kiểm tra giới hạn. API lưu vào `user_preference` và `user_preference_place_type` trong một giao dịch.

Sau khi lưu sở thích, app mở màn “Mức vận động”. Người dùng chọn đúng một mức: `LOW` (Ít vận động), `MEDIUM` (Trung bình), hoặc `HIGH` (Năng động). `PUT /api/activity-level` cập nhật cột `user_preference.activity_level`, từ chối giá trị khác và yêu cầu sở thích đã được lưu. Nút quay lại mở lại màn sở thích với các nhóm đã chọn. Sau khi lưu mức vận động, app mở màn Health Connect theo thiết kế. Các biểu tượng của màn mức vận động dùng [Material Symbols Rounded](https://github.com/google/material-design-icons).

Màn Health Connect chỉ yêu cầu quyền **đọc số bước** (`READ_STEPS`), qua hộp quyền của Android. App kiểm tra Health Connect có sẵn không, quyền đã cấp hay bị thu hồi, và hiển thị các trạng thái đang kiểm tra, chờ hệ thống, đã cấp, từ chối, cần cài/cập nhật, không hỗ trợ, lỗi mở yêu cầu và cần bật trong cài đặt. Người dùng có thể chọn “Để sau”; bước thiết lập vẫn hoàn tất nhưng chức năng cần số bước chưa dùng được. Sau khi được cấp quyền, app kiểm tra lại quyền thực tế trước khi báo đã kết nối. Màn Khám phá đọc tổng số bước từ thời điểm bắt đầu hành trình để đồng bộ tiến độ khi người dùng yêu cầu.

Sau Health Connect, app mở màn “Điểm xuất phát” nếu tài khoản chưa có `virtual_location`. Khi đăng nhập tài khoản đã chọn sở thích và mức vận động nhưng chưa có vị trí ảo, app mở thẳng màn này. Người dùng có thể xác nhận điểm hệ thống đề xuất từ dữ liệu `place`/`region`, hoặc tự chọn dùng vị trí hiện tại. Cách thứ hai chỉ xin quyền vị trí gần đúng khi được chọn; app lấy một vị trí, gửi qua API để kiểm tra phạm vi hỗ trợ rồi chờ xác nhận trước khi lưu. Hiện phạm vi hỗ trợ được xác định trong bán kính 75 km quanh một địa danh thuộc vùng đang hoạt động. Từ chối quyền, không lấy được vị trí hay ở ngoài phạm vi đều có đường trở về điểm đề xuất. Bản đồ dùng MapLibre để hiển thị tile Goong thật theo tọa độ đang chọn; đây là bản xem trước, không kéo bản đồ để đổi vị trí và không theo dõi vị trí thực. API chỉ tạo một hàng `virtual_location` cho mỗi tài khoản và không ghi đè điểm đã lưu khi gửi lại yêu cầu.

Sau khi có điểm xuất phát, app mở màn “Khám phá”. Bản đồ Goong hiển thị vị trí ảo đã lưu; khi có hành trình thì hiển thị thêm đường nối trực quan, điểm đầu và điểm đến. Tấm thông tin ở dưới có thể vuốt lên hoặc xuống từ tay nắm/phần thông báo, chạm tay nắm để đổi trạng thái; chạm bản đồ sẽ thu gọn tấm thông tin để xem bản đồ rộng hơn. `GET /api/explore` đọc vị trí ảo cùng hành trình đang thực hiện hoặc vừa hoàn thành. Màn này có các trạng thái trống, đang tải, lỗi tải, đang khôi phục, lỗi khôi phục, đang đồng bộ, lỗi đồng bộ, chưa có bước mới, thiếu quyền bước chân và hoàn thành. `POST /api/explore/sync-steps` nhận số bước cộng dồn của một hành trình; Backend khóa hành trình trong giao dịch, không cộng trùng khi gửi lại cùng số liệu, chỉ cập nhật tiến độ sau khi lưu thành công. Khi đạt mục tiêu, Backend đánh dấu hoàn thành, cập nhật vị trí ảo tới điểm đích và ghi địa danh đã khám phá. Đường nối trên bản đồ hiện là đường thẳng minh họa, chưa phải chỉ đường theo đường phố. Nút “Gợi ý điểm đến”, chi tiết hành trình và các tab khác hiện thông báo chờ màn tương ứng; phần tạo hành trình và gợi ý là chức năng tiếp theo.

Ảnh chụp trên emulator: [đăng ký](android/preview/registration-form.png), [đăng nhập](android/preview/login-form.png), [đang đăng nhập](android/preview/login-loading.png), [đăng ký thành công](android/preview/registration-success.png), [lỗi nhập liệu](android/preview/error-spacing.png), [lỗi từ API](android/preview/api-error.png), [chọn sở thích](android/preview/interests.png), [mức vận động](android/preview/activity-level.png), [mức vận động đã chọn](android/preview/activity-level-selected.png), [Health Connect](android/preview/health-connect-initial.png), [hộp quyền Android](android/preview/health-connect-system-permission.png), [đã kết nối](android/preview/health-connect-granted.png), [từ chối](android/preview/health-connect-denied.png), [bản đồ Goong ở điểm đề xuất](android/preview/starting-point-goong.png), [bản đồ Goong ở vị trí hiện tại](android/preview/starting-point-goong-current.png), [từ chối quyền vị trí](android/preview/starting-point-denied.png), [lưu thành công](android/preview/starting-point-saved.png), [Khám phá trống](android/preview/explore-empty.png), [Khám phá có hành trình](android/preview/explore-active-final.png), [hành trình hoàn thành](android/preview/explore-completed-final2.png). Video: [dấu tích được vẽ khi đăng ký thành công](android/preview/registration-check-animation.mp4).
