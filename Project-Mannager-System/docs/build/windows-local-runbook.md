# Windows Local Runbook — Chạy thủ công KHÔNG cần Docker (Prompt 25)

> Nguồn: `docs/design/07-deployment-design.md` mục 8.4, `docs/build/environment-check.md`.
> Cập nhật: 2026-08-03 — **đã kiểm chứng thực tế trên Windows** (backend local jar + frontend dev proxy). Xem mục 7.

## 1. Khi nào dùng

- Máy chưa cài Docker Desktop (chỉ JDK 21 + Node) — chạy backend + frontend trực tiếp, dùng PostgreSQL ở bất kỳ đâu (container Docker, bản native, hoặc remote).
- Cần debug nhanh frontend/backend không qua container.

## 2. Yêu cầu

| Công cụ | Phiên bản đã kiểm chứng |
|---|---|
| JDK | Temurin 21 (PATH) |
| Maven | 3.9.16 |
| Node.js | v24.18.1, npm 11+ |
| PostgreSQL | 16 (bất kỳ: container/local/remote) đang nghe `localhost:5432` |

## 3. Chuẩn bị database

Tạo DB + đảm bảo user/pass khớp biến môi trường (mặc định `pmdaily`/`pmdaily`, pass trong `.env` → `DB_PASSWORD`).

Nếu dùng container Postgres mà không muốn full stack (chỉ cần DB):

```powershell
docker run -d --name pmdaily-postgres-only -e POSTGRES_DB=pmdaily -e POSTGRES_USER=pmdaily -e POSTGRES_PASSWORD=<DB_PASSWORD> -p 5432:5432 postgres:16-alpine
```

Backend sẽ tự chạy migration Flyway `V1` + `V2` (seed demo) lúc khởi động.

## 4. Chạy Backend (local profile)

### 4.1 Trực tiếp từ source (mvn spring-boot:run)

```powershell
cd backend

# Cách 1: để Maven đọc env mặc định (DB_PASSWORD mặc định: change-me-local — điều chỉnh nếu khác)
mvn spring-boot:run -Dspring-boot.run.profiles=local

# Cách 2: truyền env rõ ràng (khớp .env)
$env:DB_PASSWORD = '<DB_PASSWORD>'
$env:DB_USERNAME = 'pmdaily'
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### 4.2 Từ jar đã đóng gói (nhanh, mang đi được)

```powershell
cd backend
mvn -q clean package -DskipTests
java -jar target/pmdaily-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=local --spring.datasource.username=pmdaily --spring.datasource.password=<DB_PASSWORD>
```

**Xác minh**: `Invoke-WebRequest http://localhost:8080/actuator/health` → `{"status":"UP"}`; Swagger tại `http://localhost:8080/swagger-ui/index.html`.

## 5. Chạy Frontend (Angular dev server + proxy)

```powershell
cd frontend
npm install          # lần đầu
npm start            # http://localhost:4200
```

- Dev server có **proxy** `/api` → `http://localhost:8080` (file `frontend/proxy.conf.json`; đã cấu hình `serve.options.proxyConfig` trong `angular.json`).
- Đổi cổng nếu 4200 bận: `npm start -- --port 4201`.

**Xác minh**: mở `http://localhost:4200` → đăng nhập `admin` / `Admin@123` → dashboard hiển thị dữ liệu seed.

## 6. Kiểm chứng end-to-end thủ công (không Docker)

```powershell
# (1) Backend đang chạy ở 8080
# (2) Frontend đang chạy ở 4200 (proxy → 8080)
Invoke-WebRequest http://localhost:4200/api/v1/auth/login `
  -Method POST -ContentType 'application/json' `
  -Body '{"username":"admin","password":"Admin@123"}' `
  -UseBasicParsing
# → HTTP 200 + accessToken (đã kiểm chứng ngày 2026-08-03)
```

## 7. Kết quả kiểm chứng thực tế (2026-08-03)

Trên máy này (JDK 21, Maven 3.9, Node 24, Postgres 16 container chỉ giữ port 5432):

| Bước | Lệnh | Kết quả |
|---|---|---|
| Package backend | `mvn -q package -DskipTests` | ✅ EXIT 0, jar 81.7 MB |
| Boot backend | `java -jar ... --spring.profiles.active=local` | ✅ `/actuator/health` = `UP` |
| Login trực tiếp backend | `POST :8080/api/v1/auth/login` | ✅ HTTP 200, roles=ADMIN |
| FE dev proxy | `npm start -- --port 4200` | ✅ SPA 200 + `POST :4200/api/v1/auth/login` → 200 (proxy `/api`→8080 hoạt động) |

Kết luận: chuỗi chạy thủ công (không cần image backend/frontend) hoạt động đúng.

## 8. Xử lý sự cố chạy thủ công

| Sự cố | Nguyên nhân | Xử lý |
|---|---|---|
| `java: not recognized` | JDK chưa vào PATH | Kiểm tra `java -version`; set PATH JDK 21 |
| `mvn: not recognized` | Maven chưa cài / PATH | Dùng `C:\Users\tichnv1\tools\apache-maven-3.9.16\bin\mvn` |
| `Connection refused` khi boot backend | Postgres chưa nghe 5432 hoặc sai password | Kiểm tra `Test-NetConnection localhost -Port 5432`; đúng `DB_PASSWORD`/`DB_URL` |
| `FlywayException` khi khởi động | DB đã có dữ liệu từ phiên khác / version schema cũ | Dùng DB mới hoặc xóa schema `pmdaily` (không destroy bản production) |
| Backend 8080 bận | Service khác giữ 8080 | Đổi `--server.port=8081`; proxy FE phải trỏ đúng target trong `frontend/proxy.conf.json` |
| Frontend 4200 bận | Angular dev khác | `npm start -- --port 4201` |
| `/api` trả HTML/404 trên dev | Proxy cấu hình thiếu | Xác nhận `frontend/proxy.conf.json` tồn tại + `angle.json` có `serve.options.proxyConfig`; restart `npm start` |
| Login sai mật khẩu dù đúng demo | Không phải profile `local` (seed chưa chạy) | Chắc `--spring.profiles.active=local` với migration `target:latest` (V2 seed) |
| Mã mất jar sau build rồi spawn | Windows Defender quét file interim | Rebuild `mvn -q clean package`; chạy `java -jar` ngay sau khi build |

## 9. Sự khác biệt so với Docker (design 07)

| Hạng mục | Docker (chuẩn) | Thủ công (runbook này) |
|---|---|---|
| DB | `postgres:16-alpine` container | Postgres bị chạy chỗ nào (container/native/remote) |
| Backend | image `pmdaily-backend` | `java -jar` / `mvn spring-boot:run` |
| Frontend | Nginx (port 4200, proxy `/api`) | Angular dev server (port 4200, proxy `/api`) |
| Profiles | `local` (qua env compose) | `local` (qua arg) |
| Port | 5432/8080/4200 | 5432/8080/4200 |