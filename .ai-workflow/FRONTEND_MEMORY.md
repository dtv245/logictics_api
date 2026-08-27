# AI Frontend Project Memory

> Persistent source of truth cho frontend. Đọc trước khi bắt đầu bất kỳ tác vụ frontend nào.
> Cập nhật sau mỗi lần làm việc. Nguồn quyết định là controller/DTO Java và `/v3/api-docs`
> (xem `docs/docs/frontend-context.md`, file này là bản tóm tắt điều hành của nó).

## 1. Control

| Field | Value |
|---|---|
| Schema Version | 1 |
| Revision | 1 |
| Project | Logistics TMS frontend (kết nối backend Spring Boot `logictics_api`) |
| Backend Repo Root | /home/vumoi/logictics_api |
| Frontend Repo / Root | Chưa có (backend repo; frontend chưa tạo) |
| Frontend Strategy | Framework linh hoạt, nhưng giữ các boundary trong mục 6 của frontend-context.md |
| Last Updated | 2026-08-15T15:25:00+07:00 |
| Current Phase | Phase 0 — Integration foundation (chưa bắt đầu, chờ tách repo/dự án) |
| Next Action | Khởi tạo project frontend: runtime config, API client/envelope/error, OAuth/PKCE, route guard, permission, shared UI |

## 2. Trạng thái backend liên quan frontend

### Đã verify (PASS)

- **GET /api/me** — endpoint current-user đã có (SLICE-001 VERIFIED). Trả identity + employee mapping.
  - Giải quyết BLOCKER-03 cũ. **Frontend PHẢI dùng nó**, không cho người dùng tự chọn employee.
- **Messaging** — SLICE-003 VERIFIED: server tự resolve sender/reader từ JWT; legacy `employeeId`/`senderId`
  vẫn bắt buộc gửi lên và phải **khớp** với JWT-mapped employee, nếu lệch → `403`.
- **Invoice dispatch→issued** — SLICE-002 VERIFIED: dispatch Load tự chuyển Invoice `draft` → `issued`.
  Frontend không cần workaround case nữa.

### Chưa có / không được giả định

- CORS chưa cấu hình backend — cần whitelist origin phía backend (BLOCKER-01).
- Identity Server ở ngoài repo (`https://localhost:7001` local). API là resource server; không có `/login`, `/logout`, `/refresh`.
- Upload document chưa có policy MIME/size/malware; chỉ không rỗng + tên an toàn (BLOCKER-05).
- Document relation access (ai xem/delete) chưa đúng scope người dùng (SLICE-004 mới xử lý attribution/filename; BLK-008 còn mở).
- Notifications là tenant-wide; chưa có per-user unread-count hay mark-one-read.
- **Chưa gọi** các endpoint: dashboard/reports, AI dispatch, Stripe, container CRUD, tracking/GPS, maintenance/HOS/DVIR,
  POD/BOL, VIN decode, WebSocket/SignalR/SSE, message edit/delete, notification mark-one-read.

## 3. Cấu trúc frontend đề xuất

```text
src/
├── app/          router/, providers/, shell/
├── core/         auth/, api/, errors/, permissions/, config/
├── shared/       components/, forms/, table/, formatters/, types/
└── features/
    roles/ customers/ employees/ terminals/ trucks/
    loads/ trips/ invoices/ payments/ inspections/
    messaging/ notifications/ documents/
```

Mỗi feature: `api.ts`, `types.ts`, `queries.ts`, `schema.ts`, `routes.ts`, `components/`, `pages/`.

Query key convention:
```ts
['loads', 'list', filters]
['loads', 'detail', id]
['customers', 'options', search]
```

## 4. HTTP contract chung

### Headers
```http
Authorization: Bearer <access-token>
Accept: application/json
X-Request-Id: <uuid-v4>
```
Upload dùng `FormData` (browser tự sinh boundary), **không** tự set multipart Content-Type.

### Envelope
```ts
export type UUID = string;
export type ISODateTime = string; // ISO-8601 có offset, ví dụ 2026-07-27T10:00:00+07:00

export interface ApiError {
  field: string | null;
  code: string;
  message: string;
}

export interface ResponseMeta {
  timestamp: string;
  path: string;
  requestId: string | null;
}

export interface ApiResponse<T> {
  success: boolean;
  code: string;
  message: string;
  data: T | null;
  errors: ApiError[];
  meta: ResponseMeta;
}

export interface PagedResponse<T> {
  items: T[];
  totalItems: number;
  totalPages: number;
  currentPage: number; // 1-based
  pageSize: number;
}

export interface ListQuery {
  page?: number;       // default 1
  pageSize?: number;   // default 20, max 100
  orderBy?: string;
  descending?: boolean;
}
```

Ngoại lệ không dùng envelope: `GET /`, `/health`, `/api/health` (JSON trực tiếp);
`GET /api/documents/{id}/download` (binary trực tiếp).

### Status & error
- Create JSON / upload / conversation / message → `201`; list/get/update/delete/action → `200`; download → `200` binary.
- Error map bắt buộc:
  | HTTP | code | Hành vi |
  |---:|---|---|
  | 400 | `BAD_REQUEST` | toast/form summary |
  | 400 | `VALIDATION_FAILED` | map `errors[].field` vào control |
  | 400 | `INVALID_STATE_TRANSITION` | refresh entity rồi báo |
  | 401 | `UNAUTHENTICATED` | refresh 1 lần hoặc logout |
  | 403 | `ACCESS_DENIED` | forbidden, không retry |
  | 404 | `RESOURCE_NOT_FOUND` / `NOT_FOUND` | về list với thông báo |
  | 409 | `CONFLICT` / `DATA_INTEGRITY_VIOLATION` | message nghiệp vụ / record đang được tham chiếu |
  | 500 | `INTERNAL_ERROR` | cung cấp request ID cho support |

- Pagination 1-based, pageSize ≤ 100, filter đổi thì reset page, search debounce 300–500ms,
  sort whitelist theo module (gửi field lạ có thể `500`).
- Tiền: `BigDecimal` = JSON number; dùng decimal library, không float arithmetic.
- Date: gửi `OffsetDateTime` ISO-8601.

## 5. Auth / Permission

### JWT
- Xác thực: signature, exp, issuer, audience `logisticsx.api`, claim `tenant` không rỗng.
- Role normalize: `SUPERADMIN`, `OWNER`, `MANAGER`, `DISPATCHER`, `DRIVER` (`SUPER_ADMIN` → `SUPERADMIN`).
- Decode cả `role` và `roles` (string hoặc list).
- Browser: Authorization Code + PKCE; access token trong memory; interceptor gắn Bearer + `X-Request-Id`,
  unwrap `ApiResponse`, refresh tối đa 1 lần khi 401, không refresh khi 403.

### Role matrix (guard frontend chỉ là UX; backend là nguồn authorization)
| Module/action | SA | O | M | D | DR |
|---|:---:|:---:|:---:|:---:|:---:|
| Roles, Employees CRUD | ✓ | ✓ | | | |
| Customers CRUD/list | ✓ | ✓ | ✓ | | |
| Invoice/payment read | ✓ | ✓ | ✓ | ✓ | |
| Invoice/payment write | ✓ | ✓ | ✓ | | |
| Load/Trip read | ✓ | ✓ | ✓ | ✓ | ✓ |
| Load/Trip create/update/delete/dispatch/cancel | ✓ | ✓ | ✓ | ✓ | |
| Load pickup/deliver | ✓ | ✓ | ✓ | ✓ | ✓ |
| Documents read/upload | ✓ | ✓ | ✓ | ✓ | ✓ |
| Documents delete | ✓ | ✓ | ✓ | ✓ | |
| Drivers/trucks | ✓ | ✓ | ✓ | ✓ | |
| Terminals read / create-update / delete | ✓ | ✓ | ✓ | ✓ | ✓ / ✓ / ✓ |
| Inspections/messages/notifications | ✓ | ✓ | ✓ | ✓ | ✓ |

Lưu ý:
- JWT role (route) ≠ `TenantRole` qua `/api/roles` (claims nghiệp vụ). Tạo role không cấp quyền Spring Security.
- Driver = Employee có claim `permission:update_trip_status` (chỉ qua `/api/drivers`).

## 6. Feature cheat sheet (endpoint + điểm quan trọng)

| Module | Endpoints (đủ CRUD trừ khi ghi chú) | Ghi chú |
|---|---|---|
| Health | `GET /api/health` | public; phân biệt unreachable / unhealthy / CORS |
| Current user | `GET /api/me` | identity + employee mapping; **nguồn cho employeeId** |
| Roles | `/api/roles` | SA/O; `name` unique; claims full-replace; delete có thể `409` |
| Customers | `/api/customers` | SA/O/M; search `name`,`email`; sort `name,email,status` |
| Employees | `/api/employees` | SA/O; search `search,status,roleId`; email duplicate `409` |
| Drivers | `/api/drivers` (+`/{id}`) | SA/O/M/D read-only; là view Employee có permission `update_trip_status` |
| Terminals | `/api/terminals` | read mọi role; write SA/O/M/D; type 5 enum; `code` 5 letters, `countryCode` 2 letters (uppercase) |
| Trucks | `/api/trucks` | SA/O/M/D; search number/VIN/license; main/secondary driver từ `/api/drivers`; không có GPS update |
| Loads | `/api/loads` + `/{id}/dispatch`, `/pick-up`, `/deliver`, `/cancel` | read mọi role; write/actions SA/O/M/D trừ pickup/deliver mọi role; state máy: draft→dispatched→picked_up→delivered, cancel từ draft/dispatched/picked_up; create luôn `draft`, PUT không đổi status |
| Trips | `/api/trips` + `/{id}/dispatch`, `/complete`, `/cancel` | read mọi role; actions SA/O/M/D; stops rebuild full khi update; stop order unique; create luôn `draft` |
| Invoices | `/api/invoices` | read SA/O/M/D; write SA/O/M; dispatch load tự đổi draft→issued; không có line items/send/approve/cancel action |
| Payments | `/api/payments` | read SA/O/M/D; write SA/O/M; không tự đổi invoice status; Stripe fields không phải full flow |
| Inspections | `/api/inspections` | mọi role full; `loadId`,`inspectedById`,`inspectedAt` required; defects array rebuild full; không có VIN decode/parts |
| Messaging | `/api/messages/conversations`, `/api/messages`, `unread-count`, `conversations/{id}/read` | đã principal-scoped; gửi đúng employeeId, lệch → `403`; content ≤ 2000; message asc; chưa edit/delete |
| Notifications | `/api/notifications`, `/{id}`, `mark-all-read` | tenant-wide; chưa unread-count riêng, chưa mark-one-read; không realtime (poll có backoff) |
| Documents | `/api/documents`, `/{id}`, `/{id}/download`, DELETE | upload multipart 2 part `file` + `metadata` (JSON blob); `uploadedById` **bắt buộc từ `/api/me`**; download parse `Content-Disposition`; không dùng `blobPath`; delete SA/O/M/D |

## 7. Critical frontend do's / don'ts

### DO
- Employee ID mọi hành động current-user (message, upload, read) lấy từ `GET /api/me`.
- `FormData` cho upload: `file` + `metadata` (Blob JSON), không tự set Content-Type.
- Vô hiệu hóa nút submit/action khi pending; confirm destructive.
- Invariant sau mutation: invalidate list + detail + resource có denormalized name.
- State transition: dùng response cập nhật ngay, rồi invalidate list/detail.
- Giữ filter/sort/page trên URL; debounce search; hủy request search cũ.
- Money: decimal-safe; preview subtotal + tax = total.
- Date/time: hiển thị theo timezone người dùng, gửi lại nguyên instant ISO-8601.

### DON'T
- Không gọi endpoint chưa tồn tại: dashboard/reports, AI dispatch, Stripe, container, tracking/GPS, HOS/DVIR, POD/BOL, realtime, message edit/delete, notification mark-one-read, invoice actions (send/approve/cancel).
- Không nhận `employeeId`/`tenantId` tùy ý từ URL cho hành động hiện tại.
- Không dùng `blobPath`/`blobContainer` làm URL; không inline download MIME nguy hiểm.
- Không giả định list trả array trực tiếp — data ở `response.data.items` (trừ 3 ngoại lệ mục 4).
- Không `localStorage` cho access/refresh token (ưu tiên memory + secure refresh).
- Không log token / Authorization / payload nhạy cảm.
- Không render message/filename bằng unsafe HTML.
- Không fetch toàn bộ table để làm dropdown — dùng server search.
- Không xây optimistic message nếu chưa có idempotency (chưa có).
- Không tự tính toán GPS/location — chưa có endpoint cập nhật.

## 8. Danh sách việc tiếp theo (backlog frontend)

### Phase 0 — Integration foundation
- [ ] Tạo project frontend (framework tùy chọn, giữ boundary mục 3).
- [ ] Runtime config: `apiBaseUrl`, `identityBaseUrl`; trang lỗi khi thiếu.
- [ ] Yêu cầu backend cấu hình CORS whitelist origin (BLOCKER-01) trước khi nối browser.
- [ ] OAuth/PKCE với Identity Server; validate `state`/`nonce`; access token memory.
- [ ] API client: interceptor Bearer + `X-Request-Id`; unwrap envelope; refresh 1 lần khi 401; không retry 403; normalize error.
- [ ] Route guard + permission theo role matrix mục 5.
- [ ] Shared: table (server pagination 1-based), form (field error map, dirty guard), confirm, toast, loading/error/empty.

### Phase 1 — Master data
- [ ] Roles → Customers → Employees/Drivers → Terminals → Trucks.

### Phase 2 — Operations
- [ ] Loads + state machine; Trips + stop builder + state machine; Inspections; Documents.

### Phase 3 — Finance
- [ ] Invoices; Payments (kiểm thử issue-on-load-dispatch sau SLICE-002).

### Phase 4 — Collaboration
- [ ] `/api/me` làm employee source; Messages (principal-scoped sau SLICE-003); Notifications.

### Phase 5 — Chờ backend có contract
- Dashboard/reports, containers, tracking, maintenance, HOS, Stripe, AI dispatch, realtime, subscriptions.

## 9. Nguồn đối chiếu

- `docs/docs/frontend-context.md` — chi tiết đầy đủ (1742 dòng), nguồn chính của file này.
- `docs/docs/business/feature-delivery-plan.md` — trạng thái FR + bằng chứng SLICE-001→004.
- `src/main/java/**/controller/*Controller.java`, `dto/request|response/*.java` — nguồn quyết định contract.
- `src/main/java/com/company/logicstic/shared/dto/ApiResponse.java`, `PagedResponse.java` — envelope.
- `src/main/java/com/company/logicstic/shared/exception/GlobalExceptionHandler.java`, `ErrorCode.java` — error contract.
- `scripts/postman/logicstic-api.postman_collection.json` — mẫu E2E smoke và dependency chuẩn.
- Runtime OpenAPI: `GET /v3/api-docs`.

Khi backend đổi DTO/controller:
1. cập nhật OpenAPI;
2. regenerate API client (nếu dùng generator);
3. regenerate Postman;
4. cập nhật `frontend-context.md` và file memory này;
5. chạy contract + frontend E2E.