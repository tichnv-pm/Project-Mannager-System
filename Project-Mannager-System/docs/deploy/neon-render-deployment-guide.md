# Hướng Dẫn Triển Khai PM Daily Work Management Lên Neon & Render

Tài liệu này hướng dẫn chi tiết từng bước để triển khai toàn bộ hệ thống **PM Daily Work Management** (Backend Spring Boot 3, Frontend Angular 22, Database PostgreSQL 16) lên nền tảng đám mây **Neon** (Serverless PostgreSQL) và **Render** (Cloud Hosting).

---

## 1. Chuẩn Bị Cơ Sở Dữ Liệu PostgreSQL Trên Neon

[Neon](https://neon.tech) là dịch vụ PostgreSQL Serverless trên nền tảng đám mây với gói Free Tier dồi dào, tự động scale và hỗ trợ chuẩn kết nối SSL.

### Bước 1.1: Tạo Project trên Neon
1. Truy cập [https://neon.tech](https://neon.tech) và đăng nhập (bằng GitHub / Google / Email).
2. Nhấn **Create Project**:
   * **Project name**: `pmdaily-db` (hoặc tên tùy chọn).
   * **Postgres version**: `16` (hoặc mới nhất).
   * **Region**: Chọn **Singapore (ap-southeast-1)** hoặc vùng gần bạn nhất để tối ưu độ trễ.
3. Nhấn **Create Project**.

### Bước 1.2: Lấy thông tin kết nối Database
Tại màn hình **Dashboard** của Neon (mục **Connection Details**):
1. Đảm bảo đã chọn tab **Connection string** $\rightarrow$ chọn **Pooled connection** (khuyến nghị) hoặc **Direct connection**.
2. Chọn hiển thị định dạng **JDBC** (hoặc copy Connection String):
   * URL chuẩn JDBC:
     ```text
     jdbc:postgresql://<NEON_HOST>/<NEON_DB>?sslmode=require
     ```
   * Ví dụ:
     * **Host**: `ep-aged-star-a1b2c3d4-pooler.ap-southeast-1.aws.neon.tech`
     * **Database**: `neondb`
     * **Username**: `neondb_owner` (hoặc user do Neon tạo)
     * **Password**: `xxxxxxxxxxxx`
     * **DB_URL**: `jdbc:postgresql://ep-aged-star-a1b2c3d4-pooler.ap-southeast-1.aws.neon.tech/neondb?sslmode=require`

> [!NOTE]
> Khi Backend khởi động lần đầu, **Flyway Migration** sẽ tự động chạy toàn bộ từ `V1` đến `V19` để tạo đầy đủ bảng, phân quyền, tài khoản quản trị và dữ liệu mẫu. Bạn **không cần** phải import SQL thủ công!

---

## 2. Triển Khai Lên Render (Khuyến Nghị: Blueprint 1-Click)

Dự án đã tích hợp sẵn file cấu hình Infrastructure as Code **`render.yaml`**. Bạn có thể triển khai cả Backend và Frontend chỉ bằng 1 thao tác Blueprint.

### Bước 2.1: Push mã nguồn lên GitHub/GitLab
Đảm bảo bạn đã commit và push toàn bộ mã nguồn của dự án lên repository GitHub hoặc GitLab cá nhân/tổ chức.

### Bước 2.2: Tạo Blueprint Instance trên Render
1. Truy cập [https://render.com](https://render.com) và đăng nhập.
2. Nhấn nút **New +** ở góc trên bên phải $\rightarrow$ chọn **Blueprint**.
3. Chọn Repository chứa mã nguồn của bạn $\rightarrow$ nhấn **Connect**.
4. Render sẽ tự động đọc file `render.yaml` và liệt kê 2 dịch vụ:
   * **`pmdaily-backend`** (Web Service Docker)
   * **`pmdaily-frontend`** (Web Service Docker)
5. Nhập các giá trị môi trường còn thiếu (lấy từ Neon ở Bước 1):
   * `DB_URL`: Nhập JDBC URL của Neon (`jdbc:postgresql://.../neondb?sslmode=require`).
   * `DB_USERNAME`: Nhập Database Username từ Neon.
   * `DB_PASSWORD`: Nhập Database Password từ Neon.
6. Các biến `JWT_SECRET` và `APP_WEBHOOK_GIT_SECRET` sẽ được Render tự động tạo chuỗi ngẫu nhiên bảo mật.
7. Nhấn **Apply**.

Render sẽ tự động tiến hành build và khởi chạy song song 2 dịch vụ.

---

## 3. Triển Khai Thủ Công Từng Dịch Vụ Trên Render (Cách Thủ Công)

Nếu bạn muốn tạo từng Web Service riêng lẻ qua giao diện Render Dashboard:

### 3.1 Cấu hình Backend (`pmdaily-backend`)
1. Nhấn **New +** $\rightarrow$ chọn **Web Service**.
2. Kết nối tới Git Repository của bạn.
3. Điền các thông số:
   * **Name**: `pmdaily-backend`
   * **Region**: `Singapore`
   * **Root Directory**: `Project-Mannager-System/backend` (hoặc để trống nếu repo root là thư mục backend)
   * **Runtime**: `Docker`
   * **Dockerfile Path**: `Dockerfile`
   * **Instance Type**: `Free`
   * **Health Check Path**: `/actuator/health`
4. Cấu hình **Environment Variables**:

| Tên biến | Giá trị mẫu | Mô tả |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` | Kích hoạt profile production |
| `DB_URL` | `jdbc:postgresql://<NEON_HOST>/neondb?sslmode=require` | JDBC URL kết nối Neon |
| `DB_USERNAME` | `neondb_owner` | Username DB |
| `DB_PASSWORD` | `<mat-khau-neon>` | Password DB |
| `JWT_SECRET` | *Chuỗi ngẫu nhiên tối thiểu 32 ký tự* | Khóa ký JWT Token |
| `JWT_ACCESS_EXPIRATION` | `900000` | Thời hạn Access Token (15 phút) |
| `JWT_REFRESH_EXPIRATION` | `604800000` | Thời hạn Refresh Token (7 ngày) |
| `CORS_ALLOWED_ORIGINS` | `*` | Cho phép kết nối CORS từ Frontend |
| `APP_WEBHOOK_GIT_SECRET` | `git-webhook-secret-key-12345` | Khóa xác thực Git Webhook |

5. Nhấn **Create Web Service**. Sau khi build xong, bạn sẽ nhận được URL Backend dạng:  
   👉 `https://pmdaily-backend.onrender.com`

---

### 3.2 Cấu hình Frontend (`pmdaily-frontend`)
1. Nhấn **New +** $\rightarrow$ chọn **Web Service**.
2. Kết nối tới Git Repository của bạn.
3. Điền các thông số:
   * **Name**: `pmdaily-frontend`
   * **Region**: `Singapore`
   * **Root Directory**: `Project-Mannager-System/frontend`
   * **Runtime**: `Docker`
   * **Dockerfile Path**: `Dockerfile`
   * **Instance Type**: `Free`
4. Cấu hình **Environment Variables**:

| Tên biến | Giá trị mẫu | Mô tả |
|---|---|---|
| `BACKEND_URL` | `https://pmdaily-backend.onrender.com` | URL của Backend vừa tạo ở bước 3.1 |

5. Nhấn **Create Web Service**. Sau khi hoàn tất, bạn sẽ nhận được URL Frontend dạng:  
   👉 `https://pmdaily-frontend.onrender.com`

---

## 4. Kiểm Tra & Xác Thực Sau Triển Khai

### 4.1 Kiểm tra Backend & Database
1. Mở trình duyệt và truy cập kiểm tra Health Check:
   ```text
   https://pmdaily-backend.onrender.com/actuator/health
   ```
   Kết quả trả về: `{"status":"UP"}`.

2. Truy cập tài liệu Swagger API:
   ```text
   https://pmdaily-backend.onrender.com/swagger-ui.html
   ```

### 4.2 Đăng nhập ứng dụng Frontend
Truy cập đường dẫn Frontend: `https://pmdaily-frontend.onrender.com`

Đăng nhập bằng các tài khoản đã được tự động seed:

| Tài khoản | Mật khẩu | Vai trò | Ghi chú |
|---|---|---|---|
| `admin` | `Admin@123` | **ADMIN** | Quản trị toàn quyền hệ thống |
| `pm.minh` | `Pm@12345` | **PROJECT_MANAGER** | Quản lý dự án mẫu Mobile Banking & Agile |
| `member1` | `Member@123` | **PROJECT_MEMBER** | Thành viên nhóm phát triển |
| `member2` | `Member@123` | **PROJECT_MEMBER** | Tester |

---

## 5. Cấu Hình Tích Hợp Git Webhook (GitHub / GitLab)

Sau khi deploy lên Render, bạn có thể kết nối kho lưu trữ GitHub/GitLab vào Webhook để tự động cập nhật task:

* **Payload URL**: `https://pmdaily-backend.onrender.com/api/v1/public/webhooks/git`
* **Secret**: Giá trị của biến `APP_WEBHOOK_GIT_SECRET` đã cấu hình ở Backend.
* **Events**: Tích chọn `Pushes` và `Pull requests` (hoặc `Merge requests`).
