# API 06 — Cuộc họp (Meeting)

> Dự án: PM Daily Work Management | Trạng thái: Draft
> Nguồn: Prompt 06, `docs/02-functional-requirements.md` (FR-MEET-01..07), `docs/04-business-rules.md` (BR-MEET)
> **Quy ước chung**: prefix `/api/v1`, JSON ISO-8601 UTC, pagination `page/size/sort`, error response theo `docs/design/05-error-handling-design.md`.

## 1. Mô tả tổng quan

Lên lịch, cập nhật, hoàn thành (khóa biên bản), xóa mềm cuộc họp; quản lý người tham gia; file đính kèm biên bản. Action item được quản lý riêng (API 07).

## 2. Danh sách endpoint

| Method | Endpoint | Phân quyền | Mục đích | FR |
|---|---|---|---|---|
| GET | `/api/v1/meetings` | `meeting:view` | Danh sách, lọc | FR-MEET-04 |
| GET | `/api/v1/meetings/today` | `meeting:view` | Họp hôm nay | FR-MEET-06 |
| POST | `/api/v1/meetings` | `meeting:manage` | Lên lịch họp | FR-MEET-01 |
| GET | `/api/v1/meetings/{id}` | `meeting:view` | Chi tiết (biên bản) | FR-MEET-03 |
| PUT | `/api/v1/meetings/{id}` | `meeting:manage` | Cập nhật | FR-MEET-02 |
| PUT | `/api/v1/meetings/{id}/complete` | `meeting:manage` / chủ trì | Hoàn thành, khóa biên bản | FR-MEET-05 |
| PUT | `/api/v1/meetings/{id}/participants` | `meeting:manage` | Thêm/bớt người tham gia | FR-MEET-02 |
| DELETE | `/api/v1/meetings/{id}` | `meeting:manage` | Xóa mềm | FR-MEET-07 |
| GET | `/api/v1/meetings/{id}/attachments` | `meeting:view` | File biên bản | FR-MEET-03 |
| POST | `/api/v1/meetings/{id}/attachments` | `meeting:manage` | Upload file | FR-MEET-03 |
| DELETE | `/api/v1/meetings/{id}/attachments/{attachmentId}` | `meeting:manage` | Xóa file | FR-MEET-03 |

## 3. Chi tiết endpoint

### 3.1 GET `/api/v1/meetings`

- **Query params**:

| Param | Type | Ghi chú |
|---|---|---|
| `page`, `size`, `sort` | int/string | Sort whitelist: `title, startTime, endTime, status, createdAt` |
| `projectId` | uuid | — |
| `status` | string | `SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED` |
| `fromTime` / `toTime` | datetime | Khoảng thời gian họp |
| `keyword` | string | LIKE trên title |

- **Response `200`** — `PageResponse<MeetingResponse>` (xem 3.3). **Lỗi**: `400 INVALID_DATE_RANGE`.

### 3.2 GET `/api/v1/meetings/today`

- Họp có `startTime` thuộc hôm nay theo timezone user, status ≠ CANCELLED.
- **Response `200`** — `List<MeetingResponse>`.

### 3.3 POST `/api/v1/meetings`

- **Phân quyền**: `meeting:manage` (ADMIN, PM dự án). Audit: có.
- **Request body** — `MeetingCreateRequest`:

| Field | Type | Bắt buộc | Ràng buộc |
|---|---|---|---|
| `projectId` | uuid | ✔ | — |
| `title` | string | ✔ | ≤ 200 ký tự |
| `startTime` / `endTime` | datetime | ✔ | endTime > startTime (BR-MEET-01) |
| `location` | string | — | Ít nhất location hoặc meetingLink (BR-MEET-04) |
| `meetingLink` | string | — | URL ≤ 500 ký tự |
| `chairpersonId` | uuid | ✔ | Thuộc project (BR-MEET-02) |
| `participantIds` | uuid[] | — | Không trùng, thuộc project (BR-MEET-03) |
| `agenda` | string | — | — |
| `status` | string | — | Mặc định `SCHEDULED` |

- **Response `201`** — `MeetingResponse`:

```json
{
  "id": "00000000-0000-0000-0000-000000000601",
  "projectId": "00000000-0000-0000-0000-000000000301",
  "projectCode": "PRJ001",
  "projectName": "App Mobile Banking",
  "title": "Họp sprint 12 — review & planning",
  "startTime": "2026-08-01T02:00:00Z",
  "endTime": "2026-08-01T03:00:00Z",
  "location": "Phòng họp 2",
  "meetingLink": null,
  "chairperson": { "id": "00000000-0000-0000-0000-000000000002", "fullName": "Nguyễn Văn Minh" },
  "participants": [{ "id": "00000000-0000-0000-0000-000000000003", "fullName": "Trần Thị Lan" }],
  "status": "SCHEDULED",
  "agenda": "1. Review sprint 11.",
  "content": null,
  "conclusion": null,
  "createdAt": "2026-07-29T02:00:00Z",
  "version": 0
}
```

- **Lỗi**: `400 VALIDATION_ERROR`; `400` (endTime ≤ startTime — BR-MEET-01); `400 NOT_PROJECT_MEMBER` (chủ trì/người tham gia ngoài project); `400` (participants trùng); `403`, `404`.
- **Hậu điều kiện**: notification `MEETING_INVITED` cho từng participant.

### 3.4 GET `/api/v1/meetings/{id}`

- **Response `200`** — `MeetingResponse` kèm `actionItems: [ActionItemSummary]`, `attachments: [AttachmentResponse]`. **Lỗi**: `403`, `404`.

### 3.5 PUT `/api/v1/meetings/{id}`

- **Phân quyền**: `meeting:manage` (ADMIN, PM dự án; quyền chủ trì sửa họp chờ xác nhận BR-MEET-05). Audit: có.
- **Request body** — `MeetingUpdateRequest`: các field như Create + `version` (bắt buộc).
- **Response `200`**. **Lỗi**: `404`, `403`, `409 CONFLICT`.

### 3.6 PUT `/api/v1/meetings/{id}/complete`

- **Phân quyền**: `meeting:manage` hoặc chủ trì họp. Audit: có (hoàn thành họp).
- **Request body**: `{ "content": "...", "conclusion": "..." }` — bắt buộc `conclusion` (nội dung khóa biên bản); `content` tùy chọn.
- **Response `200`** — `MeetingResponse` (status = `COMPLETED`).
- **Lỗi**: `400 BAD_REQUEST` (họp CANCELLED không chuyển được; thiếu conclusion); `403`; `404`.

### 3.7 PUT `/api/v1/meetings/{id}/participants`

- **Phân quyền**: `meeting:manage`. Audit: có (đổi người tham gia).
- **Request body**:

```json
{ "add": ["00000000-0000-0000-0000-000000000004"], "remove": [] }
```

- **Response `200`** — `MeetingResponse`. **Lỗi**: `400 NOT_PROJECT_MEMBER`, `400` (trùng), `404`, `403`.

### 3.8 DELETE `/api/v1/meetings/{id}`

- **Phân quyền**: `meeting:manage`. Audit: có (xóa mềm).
- **Response `204`**. Action item giữ nguyên (chính sách chờ xác nhận — FR-MEET-07; mặc định v1: giữ nguyên, gỡ liên kết họp? Không — action_items.meeting_id là NOT NULL, nên giữ nguyên bản ghi).
- **Lỗi**: `404`, `403`.

### 3.9 File đính kèm biên bản

| Endpoint | Body | Response | Ghi chú |
|---|---|---|---|
| GET `/meetings/{id}/attachments` | — | `200` `List<AttachmentResponse>` | Như API 05 (bảng `attachments.meeting_id`) |
| POST `/meetings/{id}/attachments` | multipart `file` | `201` | ≤ 10MB, whitelist mime |
| DELETE `/meetings/{id}/attachments/{attachmentId}` | — | `204` | — |

## 4. Traceability

| Endpoint | UC | AC |
|---|---|---|
| GET /meetings, /today, /{id} | UC-006 | AC-006-* |
| POST/PUT/DELETE /meetings | UC-006 | AC-006-* |
| complete, participants, attachments | UC-006 | AC-006-* |

## 5. Điểm cần xác nhận

1. BR-MEET-05 — chủ trì có quyền sửa họp không (mặc định: có quyền complete, không quyền sửa thông tin).
2. FR-MEET-07 — xóa họp có xóa kèm action item không (mặc định v1: giữ nguyên action item).
