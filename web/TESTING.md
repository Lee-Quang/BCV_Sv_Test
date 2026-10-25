# Kết quả kiểm tra web quản trị

Cập nhật sắp xếp và cấu hình máy: đã kiểm tra ID khu vực tăng dần `1, 2, 3, 4, 5` trên API danh sách, dashboard và danh mục chọn. React build thành công, backend build 0 lỗi/0 cảnh báo. Script đọc được file cấu hình máy, biến môi trường ghi đè file, từ chối cổng ngoài phạm vi và bảo toàn mật khẩu có dấu chấm phẩy trong chuỗi kết nối. Vite đã chạy thử bằng `WEB_PORT=5174`, trả HTTP 200 và proxy `/health` thành công; server kiểm thử đã dừng.

Đã build thành công React/Vite và backend .NET 10. Backend build cuối: 0 lỗi, 0 cảnh báo. Bộ kiểm tra tích hợp đã chạy thành công trên backend cổng 5081 sau khi kiểm tra trên server riêng.

| Phạm vi | Kết quả đã kiểm tra |
| --- | --- |
| Xác thực | Admin đăng nhập thành công; tài khoản thường bị từ chối 403; chưa đăng nhập bị từ chối 401; logout hủy token |
| Phân quyền | Tài khoản thường không đọc được dashboard/danh mục/dữ liệu và không tạo được khu vực; admin không tự khóa mình |
| Người dùng | Khóa người dùng thử hủy phiên và chặn đăng nhập; kích hoạt lại cho đăng nhập trở lại; dữ liệu trả về không chứa password_hash |
| Dashboard | SQL chạy trên dữ liệu thật; chuỗi thống kê đúng 7/30 ngày; danh mục và chỉ số hiển thị được trên React |
| Danh sách | Phân trang cả 8 resource; tìm kiếm kết hợp trạng thái |
| Khu vực | Tạo qua giao diện React thành công, cập nhật danh sách và thông báo kết quả |
| Địa danh | Tạo/sửa nội dung, tọa độ, ảnh và trạng thái; vĩ độ ngoài phạm vi bị từ chối; dữ liệu cũ giữ nguyên khi lỗi |
| Thẻ | Tạo thẻ liên kết địa danh; từ chối tạo thẻ thứ hai cho cùng địa danh |
| Bộ sưu tập | Gán/bỏ thẻ; cập nhật với thẻ không hợp lệ bị từ chối và giữ nguyên quan hệ cũ |
| Nhiệm vụ | Tạo nhiệm vụ ngày; từ chối chu kỳ lệch loại; nhiệm vụ thử có tiến độ không cho đổi mục tiêu |
| Vật phẩm | Từ chối hệ số giày không hợp lệ và giữ cấu hình cũ |
| Hình ảnh | Từ chối sai MIME và giả JPEG; JPEG hợp lệ trả ảnh thành công qua lưu trữ inline của server kiểm thử |
| Bản đồ | Goong tải được; hiển thị ghim; tìm kiếm lọc ghim; chọn địa danh và mở biểu mẫu đủ tọa độ/nội dung/ảnh |
| Giao diện | Menu màn hình nhỏ hoạt động; drawer tạo/sửa, nút lưu/hủy, trạng thái chờ/lỗi/thành công được kiểm tra trong trình duyệt |

Dữ liệu thử được đặt tên riêng và xóa theo đúng ID sau kiểm tra. Số lượng nội dung gốc được giữ: 63 khu vực, 311 địa danh, 135 thẻ. Tài khoản quản trị được giữ lại theo yêu cầu người dùng.

SMTP và R2 được nạp qua cấu hình backend. Chưa gửi email thật hoặc tải ảnh lên R2 trong bộ kiểm tra này; kiểm tra JPEG sử dụng lưu trữ inline để tránh tạo tệp ngoài không cần thiết. Không coi cấu hình tồn tại là bằng chứng hai dịch vụ bên ngoài đã được kiểm thử thành công.

Chạy lại kiểm tra API từ thư mục gốc:

```powershell
.\web\scripts\verify-admin.ps1
```

Xem [README](README.md) để chuẩn bị backend, tài khoản quản trị và biến môi trường database dùng dọn bản ghi thử.
