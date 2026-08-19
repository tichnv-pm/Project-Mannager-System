# PM Daily Work Management

Ứng dụng quản lý công việc hằng ngày cho quản lý dự án phần mềm (PM).

## Công nghệ

- **Backend**: Java 21, Spring Boot 3.x, Spring Security + JWT, Spring Data JPA, Flyway, MapStruct, springdoc (Swagger). Package gốc `com.example.pmdaily`.
- **Frontend**: Angular (Material, Reactive Forms, SCSS).
- **Database**: PostgreSQL 16 (PK UUID, `snake_case`, `timestamptz` UTC).
- **Local**: Docker Compose (postgres/backend/frontend-Nginx) hoặc chạy thủ công Maven + npm.

## Cấu trúc thư mục

```
├── backend/       # Spring Boot API
├── frontend/      # Angular SPA
├── database/      # schema.sql + seed-data.sql (tham chiếu cho Flyway)
├── docs/          # Tài liệu (requirements, design, api, database...)
├── docker-compose.yml
└── .env.example   # Biến môi trường mẫu (copy thành .env khi chạy)
```

## Chạy local

### Docker Compose (khuyến nghị)

```powershell
copy .env.example .env   # chỉnh password/secret nếu cần
docker compose up -d --build
```

- Frontend: http://localhost:4200
- Backend/Swagger: http://localhost:8080/swagger-ui.html

### Thủ công

| Thành phần | Lệnh |
|---|---|
| PostgreSQL | Docker riêng, tạo DB `pmdaily` |
| Backend | `cd backend; mvn spring-boot:run -Dspring-boot.run.profiles=local` |
| Frontend | `cd frontend; npm install; npm start` |

## Tài khoản demo (chỉ dùng local, seed qua Flyway V2)

| Username | Password | Vai trò |
|---|---|---|
| `admin` | `Admin@123` | ADMIN |
| `pm.minh` | `Pm@12345` | PROJECT_MANAGER |
| `member1` | `Member@123` | PROJECT_MEMBER |

## Yêu cầu môi trường (xem `docs/build/environment-check.md`)

- JDK 21 + Maven 3.9 (hoặc Docker) cho backend
- Node 24 + npm cho frontend

## Tài liệu

- Tổng quan + trạng thái các giai đoạn: `docs/00-project-overview.md`
- Hướng dẫn triển khai local (Windows): `docs/design/07-deployment-design.md`
- **Giai đoạn v1.1 — Project Planning**: `docs/planning/01..15` (requirement, design, acceptance, traceability), API tại `docs/api/13-planning-api.md`
