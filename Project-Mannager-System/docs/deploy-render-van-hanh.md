# Hướng dẫn vận hành & link ứng dụng — Deploy Render.com + Neon (Free)

> Trạng thái: **ĐANG TRIỂN KHAI** — cập nhật 2026-08-26.
> Hệ thống: PM Daily Work Management (Spring Boot + Angular + PostgreSQL).
> Môi trường: Render.com (free web service) + Neon (PostgreSQL free).

---

## 1. Link ứng dụng

| Thành phần | URL | Trạng thái |
|---|---|---|
| **Frontend (Web App)** | `https://pmdaily-frontend.onrender.com` | ⏳ Tạo sau khi deploy |
| **Backend (API)** | `https://pmdaily-backend.onrender.com` | ⏳ Tạo sau khi deploy |
| **Health check** | `https://pmdaily-backend.onrender.com/actuator/health` | ⏳ |
| **Swagger UI** | `https://pmdaily-backend.onrender.com/swagger-ui.html` | ⏳ |

> ⏳ = chưa hoạt động. Link chỉ hoạt động sau khi bạn tạo 2 Web Service trên Render (xem mục 3).

---

## 2. Cấu hình code đã chuẩn bị (đã commit & push)

| File | Thay đổi | Mục đích |
|---|---|---|
| `frontend/nginx.conf` | `proxy_pass ${BACKEND_URL}` thay vì hardcode `http://backend:8080` | Cho phép nginx proxy `/api` sang URL backend trên Render |
| `frontend/Dockerfile` | Copy nginx.conf vào `/etc/nginx/templates/` | nginx tự thay biến env `BACKEND_URL` khi khởi động |
| `docker-compose.yml` | Frontend nhận `BACKEND_URL` (mặc định `http://backend:8080`) | Giữ local chạy như cũ |

---

## 3. Các bước cần bạn thao tác trên web (tôi không thể làm vì cần đăng nhập)

### 3.1 Tạo PostgreSQL Neon (free)
1. **https://neon.tech** → Sign in bằng GitHub → **Create a project** (`pmdaily`, region `Singapore`).
2. **Connect** → tab **JDBC** → copy connection string dạng:
   `postgresql://pmdaily_owner:XXX@ep-xxx.ap-southeast-1.aws.neon.tech/pmdaily`
3. Nếu cần, tạo DB `pmdaily` bằng **SQL Editor**: `CREATE DATABASE pmdaily;`

### 3.2 Tạo Backend Web Service trên Render
1. **https://render.com** → Sign up bằng GitHub.
2. **New + → Web Service** → chọn repo `tichnv-pm/Project-Mannager-System`.
3. Cấu hình:

| Mục | Giá trị |
|---|---|
| Name | `pmdaily-backend` |
| Region | `Singapore` |
| Root Directory | `Project-Mannager-System/backend` |
| Runtime | `Docker` |
| Instance Type | `Free` |
| Health Check Path | `/actuator/health` |

4. **Environment variables:**

| Variable | Value |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `local` |
| `DB_URL` | `jdbc:postgresql://ep-xxx...neon.tech:5432/pmdaily?sslmode=require` |
| `DB_USERNAME` | `<user Neon>` |
| `DB_PASSWORD` | `<password Neon>` |
| `JWT_SECRET` | chuỗi ≥ 32 ký tự ngẫu nhiên |
| `CORS_ALLOWED_ORIGINS` | `https://pmdaily-frontend.onrender.com` (điền sau) |
| `JWT_ACCESS_EXPIRATION` | `900000` |
| `JWT_REFRESH_EXPIRATION` | `604800000` |

5. **Create Web Service** → chờ build (~5–10 phút, Maven). Ghi lại URL backend.

### 3.3 Tạo Frontend Web Service
1. **New + → Web Service** → cùng repo.
2. Cấu hình: Name `pmdaily-frontend`, Region `Singapore`, Root Directory `Project-Mannager-System/frontend`, Runtime `Docker`, Instance `Free`.
3. Environment: `BACKEND_URL = https://pmdaily-backend.onrender.com`
4. **Create Web Service** → chờ build Angular + nginx.
5. **Quay lại backend** → Edit → đổi `CORS_ALLOWED_ORIGINS` = URL frontend → **Deploy**.

---

## 4. Vận hành & kiểm tra sau khi deploy

### 4.1 Kiểm tra sức khỏe hệ thống
- Mở: `https://pmdaily-backend.onrender.com/actuator/health` → phải trả `{"status":"UP"}`.
- Mở frontend → trang đăng nhập hiển thị được → frontend + nginx proxy OK.

### 4.2 Tài khoản demo (seed tự động do profile `local`)

| Username | Password | Vai trò |
|---|---|---|
| `admin` | `Admin@123` | ADMIN |
| `pm.minh` | `Pm@12345` | PROJECT_MANAGER |
| `member1` | `Member@123` | PROJECT_MEMBER |

### 4.3 Vận hành hằng ngày

| Việc | Cách làm |
|---|---|
| Xem log | Render → service → **Logs** (hoặc **Deploy → build log**) |
| Deploy bản mới | Push commit mới lên GitHub → Render tự rebuild (có **Manual Deploy** → Deploy latest commit) |
| Dừng/tạm dừng để tiết kiệm giờ | Render → service → **Suspend** (khi ngưng test) / **Resume** khi cần lại |
| Reset dữ liệu test | Xóa `pgdata`? Không dùng Docker ở đây → trên Neon xóa DB `pmdaily` rồi tạo lại (backend sẽ tự chạy Flyway seed lại) |
| Đổi secret | Sửa `JWT_SECRET` trong Environment → **Manual Deploy** |

### 4.4 Giới hạn bản Free (quan trọng)
- **Cold start**: web service ngủ sau ~15 phút không có request; request đầu tiên sau đó chậm ~1–2 phút → **refresh lại 1 lần** nữa.
- **750 giờ/tháng / service**: 2 service = 1500 giờ ⇒ nếu chạy 24/7 sẽ hết giờ giữa tháng → nên **Suspend** khi không test.
- **Neon free**: 0.5GB storage, tắt khoảng sau 1 tuần không truy cập (bật lại bằng cách gọi đến DB) — phù hợp test.

### 4.5 Xử lý sự cố thường gặp

| Triệu chứng | Nguyên nhân | Xử lý |
|---|---|---|
| Health `{"status":"DOWN"}` | DB URL sai / DB chưa có / mất quyền | Kiểm tra `DB_URL/DB_USERNAME/DB_PASSWORD`, Neon SQL Editor chạy `SELECT 1` |
| Frontend mở được nhưng login lỗi mạng | BACKEND_URL sai / backend chưa up / CORS | Kiểm tra `BACKEND_URL`, health backend, `CORS_ALLOWED_ORIGINS` đúng URL frontend |
| 502 Bad Gateway ở `/api` | nginx không gọi được backend | Chỉnh lại `BACKEND_URL` (không có dấu `/` cuối) → Deploy |
| Login báo 401 mọi lúc | `JWT_SECRET` không đủ 32 ký tự | Sinh chuỗi dài, khởi động lại |
| Build fail ở frontend | Node/budget | Xem build log, kiểm tra lại commit |
| Dữ liệu demo không có | Profile không phải `local` / Flyway chưa chạy | Đảm bảo `SPRING_PROFILES_ACTIVE=local`, xem log Flyway |

---

## 5. Checklist triển khai

- [ ] Đăng ký Neon, tạo project + DB `pmdaily`, lưu JDBC URL
- [ ] Đăng ký Render bằng GitHub
- [ ] Tạo Web Service backend (`pmdaily-backend`) + env
- [ ] Tạo Web Service frontend (`pmdaily-frontend`) + `BACKEND_URL`
- [ ] Cập nhật `CORS_ALLOWED_ORIGINS` backend = URL frontend, deploy lại
- [ ] Health check backend = UP
- [ ] Mở URL frontend, đăng nhập `admin/Admin@123`
- [ ] Điền URL thật vào bảng mục 1 (thay ⏳)
