# Mô hình gợi ý Bước Chân Việt (*recommendation*)

Dịch vụ Python nạp mô hình K-Means (35 đặc trưng, K = 15) và trả các địa danh tương đồng về nội dung.

Ba tệp trong `service/data` là bản sao nguyên vẹn của mô hình (`model.joblib`), ma trận nhị phân 35 cột (`features.csv`) và thông tin 315 bản ghi địa danh đã rà soát (`places.csv`). Dịch vụ không huấn luyện trong request và không dùng trường vận động hay rating làm đầu vào K-Means. Nhãn cụm và 35 đặc trưng của 311 địa danh đã được nạp sẵn vào database (bảng `place_feature`, `place_feature_value`), nên Backend chấm điểm gợi ý mà **không cần dịch vụ này chạy**. Dịch vụ dùng để kiểm chứng mô hình và tra cứu địa danh tương đồng.

## Mục lục

- [Cài đặt](#cài-đặt)
- [Sử dụng](#sử-dụng)
- [Mô hình](#mô-hình)
- [API](#api)
- [Đóng góp](#đóng-góp)
- [Giấy phép](#giấy-phép)

## Cài đặt

Cần Python 3.12. Phiên bản scikit-learn và NumPy được khóa theo môi trường đã tạo artifact; dịch vụ từ chối mô hình không tương thích hoặc CSV sai.

```powershell
cd recommendation
py -3.12 -m venv .venv
.venv\Scripts\python -m pip install -r requirements-test.txt
```

Trên Linux/macOS dùng `python3.12 -m venv .venv` và `.venv/bin/python`. Thư mục `.venv` không được commit.

## Sử dụng

Chạy kiểm thử (10 test: nạp mô hình, chuẩn hóa, lân cận, API):

```powershell
.venv\Scripts\python -m unittest discover -s tests -v
```

Chạy dịch vụ cục bộ:

```powershell
.venv\Scripts\python -m uvicorn service.app:app --host 127.0.0.1 --port 8090
```

Hoặc bằng Docker (bind ra loopback):

```powershell
docker build -t bcv-recommendation .
docker run --rm -p 127.0.0.1:8090:8090 bcv-recommendation
```

Biến `BV_MODEL_DATA_DIR` chọn thư mục dữ liệu tin cậy khác khi triển khai; chỉ người vận hành cấu hình đường dẫn này, API không nhận upload mô hình. Dịch vụ chỉ dành cho mạng nội bộ; app Android và web quản trị không gọi trực tiếp.

## Mô hình

- **Dữ liệu:** 315 bản ghi nguồn, 311 địa danh phân biệt; 4 cặp bản ghi trùng được quy về mã chính (ví dụ `VN-0060` → `VN-0156`).
- **Đặc trưng:** 35 cột nhị phân, 4 nhóm: KindTags (10), LandformTags (11), EcosystemTags (3), ThemeTags (11).
- **Chuẩn hóa:** L2 riêng từng nhóm rồi nhân 0,5; nhóm không có nhãn giữ vector 0; không chuẩn hóa L2 toàn vector thêm lần nữa.
- **Huấn luyện (ngoại tuyến):** K-Means, k-means++, 20 lần khởi tạo, tối đa 500 vòng, ngưỡng 0,0001, Lloyd, K = 15, seed 42 (Silhouette 0,418647).
- **Suy luận:** khoảng cách tới tâm cụm đã fit, tính bằng NumPy, tương đương `predict` của K-Means. Khi khởi động, dịch vụ kiểm tra lại nhãn của đủ 315 bản ghi với artifact. Cách này tránh lỗi threadpoolctl đọc đường dẫn DLL bằng ANSI trên Windows khi dự án nằm trong thư mục có dấu tiếng Việt.

Kết quả ở đây là **độ tương đồng nội dung**, không phải điểm gợi ý cá nhân hóa P/C/A/R/N; phần đó do Backend tính ([backend/README.md](../backend/README.md#gợi-ý-điểm-đến)).

## API

| Endpoint | Trả về |
| --- | --- |
| `GET /health` | Mô hình đã nạp và số địa danh chuẩn (311) |
| `GET /model` | Thứ tự 35 cột, 4 nhóm, K = 15, seed = 42, phiên bản scikit-learn, SHA-256 của artifact |
| `GET /similar/{mã}?limit=5` | Địa danh cùng cụm, sắp theo khoảng cách nội dung tăng dần. `limit` từ 1 đến 20; mã không tồn tại trả 404; mã trùng được quy về mã chính; kết quả không gồm chính địa danh hỏi |

## Đóng góp

Xem [Đóng góp](../README.md#đóng-góp) ở README gốc. Huấn luyện lại mô hình phải giữ đúng thứ tự 35 cột và quy tắc chuẩn hóa, rồi nạp lại nhãn cụm vào `place_feature` của database.

## Giấy phép

UNLICENSED © 2026 Nhóm ĐAHK1262703. Xem [Giấy phép](../README.md#giấy-phép) ở README gốc.
