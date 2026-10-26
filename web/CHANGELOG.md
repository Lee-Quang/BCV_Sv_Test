# Nhật ký thay đổi

## web_v2 — 26/10/2026

- Thêm màn hình chi tiết cho người dùng, khu vực, loại địa danh, địa danh, thẻ, bộ sưu tập, nhiệm vụ và vật phẩm.
- Mở chi tiết bằng dòng/tên đối tượng; điều hướng giữa các đối tượng liên quan, từ dashboard và bản đồ.
- Đưa ảnh địa danh và thẻ lên trên thông tin; ảnh đầu hiển thị lớn và ưu tiên tải.
- Thêm animation chuyển trang, danh sách, nút và mở/đóng bảng; hỗ trợ giảm chuyển động.
- Thêm Xóa ở danh sách và phần chi tiết, kèm hộp thoại xác nhận tên/ID, trạng thái chờ và thông báo lỗi.
- Bổ sung DELETE API có kiểm tra quyền quản trị, bảo vệ tài khoản quản trị và bản ghi có liên kết/lịch sử.
- Xóa ảnh địa danh/liên kết bộ sưu tập trong giao dịch; lỗi xóa khôi phục dữ liệu liên quan. Không xóa tệp R2.
- Cập nhật danh sách sau xóa, điều chỉnh trang cuối khi hết bản ghi.
- Đặt Xóa cạnh Chỉnh sửa và căn giữa nhóm nút với tiêu đề Thao tác.
- Bỏ phần Phạm vi theo tài liệu đồ án trong Thông tin hệ thống.
- Cập nhật README, hướng dẫn sử dụng, kiểm thử tích hợp và loại trừ thư mục build backend.

### Kiểm tra

- React/Vite build thành công.
- .NET backend build thành công, không có cảnh báo/lỗi.
- Kiểm thử tích hợp trên backend cục bộ: CRUD, xóa dữ liệu thử, trả 404 sau xóa, bảo vệ quản trị viên, chặn liên kết và rollback ảnh. Dữ liệu thử đã được dọn.
- Hộp thoại xóa chưa được kiểm thử trực tiếp trên trình duyệt; chi tiết này được ghi trong TESTING.md.

Các cấu hình từng máy, mật khẩu, khóa dịch vụ, Android dev.properties, node_modules và đầu ra build không thuộc bản commit.
