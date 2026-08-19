# API 08 — Risk

> Dự án: PM Daily Work Management | Trạng thái: Draft
> Nguồn: Prompt 06, `docs/02-functional-requirements.md` (FR-RISK-01..05)
> **Quy ước chung**: prefix `/api/v1`, JSON ISO-8601 UTC, pagination `page/size/sort`, error response theo `docs/design/05-error-handling-design.md`.

## 1. Mô tả tổng quan

Nhận diện, theo dõi risk; mã tự sinh `RSK000001` (global sequence); khi risk xảy ra (OCCURRED) có thể chuyển thành issue với liên kết 1–1 (unique index `uk_risks_linked_issue`).

### 1.1 Cách tính level (mặc định v1)

| Probability × Impact | Impact |
|---|---|
| | LOW | MEDIUM | HIGH |
| **LOW** | LOW | LOW | MEDIUM |
| **MEDIUM** | LOW | MEDIUM | HIGH |
| **HIGH** | MEDIUM | HIGH | CRITICAL |

- `level` tự tính khi không gửi; cho phép override nếu gửi kèm (chờ xác nhận FR-RISK-01).

## 2. Danh sách endpoint

| Method | Endpoint | Phân quyền | Mục đích | FR |
|---|---|---|---|---|
| GET | `/api/v1/risks` | `risk:view` | Danh sách, lọc | FR-RISK-03 |
| POST | `/api/v1/risks` | `risk:manage` | Tạo | FR-RISK-01 |
| GET | `/api/v1/risks/{id}` | `risk:view` | Chi tiết | FR-RISK-03 |
| PUT | `/api/v1/risks/{id}` | `risk:manage` / owner | Cập nhật | FR-RISK-02 |
| DELETE | `/api/v1/risks/{id}` | `risk:manage` | Xóa mềm | FR-RISK-04 |
| POST | `/api/v1/risks/{id}/convert-to-issue` | `risk:manage` | Risk → Issue | FR-RISK-05 |

## 3. Chi tiết endpoint

### 3.1 GET `/api/v1/risks`

- **Query params**: `page, size, sort` (whitelist: `code, title, level, status, dueDate, createdAt`), `projectId`, `status`, `level`, `ownerId`.
- **Response `200`** — `PageResponse<RiskResponse>` (xem 3.3).

### 3.2 POST `/api/v1/risks`

- **Phân quyền**: `risk:manage` (ADMIN, PM dự án). Audit: có.
- **Request body** — `RiskCreateRequest`:

| Field | Type | Bắt buộc | Ràng buộc |
|---|---|---|---|
| `projectId` | uuid | ✔ | — |
| `title` | string | ✔ | ≤ 200 ký tự |
| `description` | string | — | — |
| `probability` | string | ✔ | `LOW, MEDIUM, HIGH` |
| `impact` | string | ✔ | `LOW, MEDIUM, HIGH` |
| `level` | string | — | Tự tính theo bảng 1.1 nếu bỏ trống; cho phép override |
| `ownerId` | uuid | ✔ | Thuộc project |
| `mitigationPlan` | string | — | — |
| `contingencyPlan` | string | — | — |
| `status` | string | — | Mặc định `OPEN` |
| `dueDate` | date | — | — |

- **Response `201`** — `RiskResponse`:

```json
{
  "id": "00000000-0000-0000-0000-000000000801",
  "code": "RSK000001",
  "projectId": "00000000-0000-0000-0000-000000000301",
  "title": "Rủi ro chậm release do phụ thuộc bên thứ ba",
  "probability": "HIGH",
  "impact": "HIGH",
  "level": "CRITICAL",
  "owner": { "id": "00000000-0000-0000-0000-000000000002", "fullName": "Nguyễn Văn Minh" },
  "status": "MONITORING",
  "dueDate": "2026-08-15",
  "linkedIssueId": null,
  "createdAt": "2026-08-01T03:00:00Z",
  "version": 0
}
```

- **Lỗi**: `400 VALIDATION_ERROR`; `400 NOT_PROJECT_MEMBER` (owner ngoài project); `409` (mã trùng — tự sinh lại, hiếm gặp); `403`, `404`.

### 3.3 GET `/api/v1/risks/{id}`

- **Response `200`** — `RiskResponse` kèm `mitigationPlan, contingencyPlan`. **Lỗi**: `403`, `404`.

### 3.4 PUT `/api/v1/risks/{id}`

- **Phân quyền**: `risk:manage` (ADMIN, PM dự án) — mọi trường; owner — chỉ `status` (chờ xác nhận FR-RISK-02). Audit: có.
- **Request body** — `RiskUpdateRequest`: các field như Create + `version`.
- **Response `200`**. **Lỗi**: `404`, `403`, `409 CONFLICT`.

### 3.5 DELETE `/api/v1/risks/{id}`

- **Phân quyền**: `risk:manage`. **Response `204`** (xóa mềm). **Lỗi**: `403`, `404`. Audit: có.

### 3.6 POST `/api/v1/risks/{id}/convert-to-issue`

- **Phân quyền**: `risk:manage`. Audit: có.
- **Điều kiện**: chỉ khi risk `status = OCCURRED`.
- **Request body**: `{ "severity": "CRITICAL", "dueDate": "2026-08-05" }` (severity mặc định lấy theo `level` của risk).
- **Response `201`** — `IssueResponse`; risk được liên kết `linkedIssueId`.
- **Lỗi**: `400 BAD_REQUEST` (risk chưa OCCURRED); `409 ALREADY_LINKED` (đã liên kết issue); `403`, `404`.

## 4. Traceability

| Endpoint | UC | AC |
|---|---|---|
| GET /risks, /{id} | UC-008 | AC-008-* |
| POST/PUT/DELETE | UC-008 | AC-008-* |
| convert-to-issue | UC-008 | AC-008-* |

## 5. Điểm cần xác nhận

1. FR-RISK-01 — cho phép override `level` hay luôn tự tính theo xác suất × ảnh hưởng?
2. FR-RISK-02 — owner có được cập nhật trạng thái risk của mình không (mặc định: có, chỉ status).
