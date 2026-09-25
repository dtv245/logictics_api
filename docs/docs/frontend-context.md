# Frontend Context & Feature Checklist

<!-- markdownlint-disable MD013 MD024 MD060 -->

> Nguồn chuẩn để xây frontend cho backend Spring Boot `logictics_api`.
>
> Cập nhật và đối chiếu với source ngày 2026-07-27. Khi tài liệu này khác với
> controller/DTO Java hoặc `/v3/api-docs`, source backend là nguồn quyết định.

## 0. Login UI prototype

Repo hiện có một login UI tĩnh tại `/` để làm shell giao diện cho frontend tương lai:
`src/main/resources/static/index.html`, `login.css` và `login.js`. Trang này mô phỏng
avatar SVG hoạt ảnh, form responsive và validation phía client theo mẫu tham chiếu;
nó chưa thực hiện OAuth2 login thật. Xem [frontend-login.md](frontend-login.md) để biết
rõ phạm vi và cách nối Identity Server.

## 1. Mục tiêu và phạm vi

File này cung cấp đủ context để một developer hoặc coding agent có thể:

- tạo kiến trúc frontend và API client;
- triển khai route guard, authentication và error handling;
- tạo model TypeScript đúng với request/response hiện tại;
- xây list/detail/create/edit/action cho từng module;
- hiểu dependency giữa các form;
- biết chức năng nào backend đã có, chức năng nào chưa có;
- nghiệm thu bằng checklist chức năng và test case.

Phạm vi hiện có của Spring Boot:

1. Health;
2. Roles;
3. Customers;
4. Employees và Drivers;
5. Terminals;
6. Trucks;
7. Loads;
8. Trips;
9. Invoices;
10. Payments;
11. Inspections;
12. Messaging;
13. Notifications;
14. Documents.

Không lấy các tài liệu .NET cũ làm hợp đồng API nếu Spring controller tương ứng
không tồn tại.

---

## 2. Quick start

### 2.1 URL local

| Thành phần | IntelliJ | Docker Compose |
|---|---:|---:|
| API | `http://localhost:18080` | `http://localhost:8080` |
| Swagger UI | `/swagger-ui.html` | `/swagger-ui.html` |
| OpenAPI | `/v3/api-docs` | `/v3/api-docs` |
| Health | `/api/health` | `/api/health` |
| Identity Server | `https://localhost:7001` | dịch vụ ngoài repo |

Chạy business API bằng profile `local`. Profile `nodb` chỉ phù hợp health/OpenAPI;
các controller dùng database bị tắt.

```text
--spring.profiles.active=local
```

### 2.2 Postman contract test

Import:

- `scripts/postman/logicstic-api.postman_collection.json`
- `scripts/postman/logicstic-local.postman_environment.json`

Chạy folder:

```text
00 - RUN THIS - E2E Smoke Flow
```

Flow này là mẫu dependency chuẩn:

```text
Role
  └─ Employee
      └─ Truck
Customer ─┐
Terminal ─┼─ Load
Truck ────┤
Employee ─┘
```

---

## 3. Các blocker phải xử lý trước khi frontend chạy production

### BLOCKER-01 — CORS chưa được cấu hình trong Spring

Không tìm thấy `CorsConfiguration`, `CorsFilter` hoặc cấu hình allowed origins.
Frontend chạy ở origin khác API có thể bị browser chặn dù Postman hoạt động.

Backend cần whitelist chính xác origin frontend theo môi trường và cho phép tối
thiểu:

- methods: `GET, POST, PUT, DELETE, OPTIONS`;
- headers: `Authorization, Content-Type, X-Request-Id`;
- exposed headers: `Content-Disposition` cho download;
- credentials chỉ bật nếu thực sự dùng cookie; API hiện dùng Bearer token.

### BLOCKER-02 — Identity Server không nằm trong repo này

API chỉ là OAuth2 Resource Server. Nó không có `/login`, `/logout`, `/refresh`
hay `/me`. Frontend phải tích hợp Identity Server riêng.

Local hiện yêu cầu Identity Server ở `https://localhost:7001`. Nếu dịch vụ đó
không chạy, chỉ có thể dùng access token hợp lệ lấy từ môi trường khác.

### BLOCKER-03 — Thiếu endpoint current-user/current-employee

JWT cung cấp `sub`, `email`, `role/roles`, `tenant`, nhưng API không có endpoint
đổi identity thành `employeeId`. Messaging, unread count và mark-read lại yêu cầu
`employeeId`.

Trước khi hoàn thiện messaging cần một trong hai:

- thêm `employeeId` vào JWT và validate server-side; hoặc
- thêm `GET /api/me` trả về identity + employee mapping.

Không nên yêu cầu người dùng chọn chính mình từ dropdown.

### BLOCKER-04 — Messaging/notification chưa scope đủ theo người dùng

Các endpoint messaging nhận `employeeId` từ query/body. Backend chưa đối chiếu ID
đó với principal trong JWT ở controller/service. Notification hiện là tenant-wide
và `mark-all-read` đánh dấu toàn bộ notification trong tenant.

Frontend không thể thay thế authorization server-side. Cần sửa backend trước khi
dùng dữ liệu nhạy cảm production.

### BLOCKER-05 — Upload chưa có policy MIME/size/malware

Document upload chỉ kiểm tra file không rỗng và tên file an toàn. Chưa có:

- kích thước tối đa ở contract;
- MIME whitelist/content sniffing;
- antivirus/quarantine;
- signed URL;
- idempotency/retry policy.

Frontend nên giới hạn tạm thời theo product decision, nhưng backend vẫn phải là
nguồn enforcement.

---

## 4. Hợp đồng HTTP dùng chung

### 4.1 Headers

Mọi request protected:

```http
Authorization: Bearer <access-token>
Accept: application/json
X-Request-Id: <uuid-v4>
```

JSON mutation:

```http
Content-Type: application/json
```

Upload không tự đặt `Content-Type` thủ công trong browser. Dùng `FormData` để
browser sinh multipart boundary.

### 4.2 Kiểu TypeScript dùng chung

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

Ngoại lệ không dùng `ApiResponse`:

- `GET /`, `/health`, `/api/health`: JSON health trực tiếp;
- `GET /api/documents/{id}/download`: binary body trực tiếp.

### 4.3 Success status

| Loại request | Status thực tế |
|---|---:|
| List/get/update/delete/action | `200` |
| Create JSON | `201` |
| Upload document | `201` |
| Create conversation/message | `201` |
| Download | `200` binary |

OpenAPI hiện chưa mô tả đầy đủ status `201`; frontend và test phải theo controller
thực tế.

### 4.4 Error contract

```json
{
  "success": false,
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "data": null,
  "errors": [
    {
      "field": "email",
      "code": "Email",
      "message": "must be a well-formed email address"
    }
  ],
  "meta": {
    "timestamp": "2026-07-27T03:00:00Z",
    "path": "/api/employees",
    "requestId": "..."
  }
}
```

| HTTP | `code` | Frontend behavior |
|---:|---|---|
| 400 | `BAD_REQUEST` | toast/form summary |
| 400 | `VALIDATION_FAILED` | map `errors[].field` vào control |
| 400 | `MALFORMED_REQUEST` | báo payload không hợp lệ |
| 400 | `INVALID_STATE_TRANSITION` | refresh entity, báo action không còn hợp lệ |
| 401 | `UNAUTHENTICATED` | refresh token một lần hoặc logout |
| 403 | `ACCESS_DENIED` | trang/inline forbidden, không retry |
| 404 | `RESOURCE_NOT_FOUND`, `NOT_FOUND` | detail not-found hoặc quay về list |
| 409 | `CONFLICT`, custom code | hiển thị message nghiệp vụ |
| 409 | `DATA_INTEGRITY_VIOLATION` | record đang được tham chiếu |
| 500 | `INTERNAL_ERROR` | lỗi chung + request ID để support |

### 4.5 Pagination, filtering và sorting

- Wire pagination là **1-based**.
- `pageSize` hợp lệ từ `1..100`.
- Khi filter/search thay đổi phải reset `page=1`.
- Debounce search khoảng 300–500 ms.
- Sort field là tên property backend; gửi field không tồn tại có thể gây `500`.
- Chỉ dùng sort field được liệt kê trong từng module của tài liệu này.
- Không giả định list API trả array trực tiếp; data nằm ở
  `response.data.items`.

### 4.6 Date/time và money

- Gửi `OffsetDateTime` dưới dạng ISO-8601 có offset hoặc `Z`.
- Hiển thị theo timezone người dùng nhưng giữ nguyên instant khi gửi lại.
- `BigDecimal` được serialize thành JSON number. Không tính tiền bằng floating
  point thuần trong UI; dùng decimal library và round theo nghiệp vụ.
- Currency là string, ví dụ `USD`, `EUR`; backend hiện chưa validate ISO code.

---

## 5. Authentication và authorization

### 5.1 Token validation

JWT phải thỏa tất cả:

- signature hợp lệ theo JWK Set;
- chưa hết hạn;
- issuer đúng;
- audience chứa `logisticsx.api`;
- có claim `tenant` khác rỗng.

API đọc cả `role` và `roles`, dạng string hoặc list. Role được normalize về:

```text
SUPERADMIN
OWNER
MANAGER
DISPATCHER
DRIVER
```

`SUPER_ADMIN` cũng được normalize thành `SUPERADMIN`.

### 5.2 Browser login

Khuyến nghị browser dùng Authorization Code + PKCE. Không lưu refresh token hoặc
access token dài hạn trong `localStorage` nếu có thể tránh; ưu tiên memory +
secure refresh strategy của Identity Server.

API client cần interceptor:

1. gắn Bearer token;
2. gắn `X-Request-Id`;
3. unwrap `ApiResponse`;
4. thử refresh tối đa một lần khi `401`;
5. không refresh khi `403`;
6. normalize error về một type dùng chung.

### 5.3 Role matrix

Ký hiệu:

- `SA`: SUPERADMIN
- `O`: OWNER
- `M`: MANAGER
- `D`: DISPATCHER
- `DR`: DRIVER

| Module/action | SA | O | M | D | DR |
|---|:---:|:---:|:---:|:---:|:---:|
| Roles CRUD | ✓ | ✓ |  |  |  |
| Employees CRUD/list | ✓ | ✓ |  |  |  |
| Customers CRUD/list | ✓ | ✓ | ✓ |  |  |
| Invoice/payment read | ✓ | ✓ | ✓ | ✓ |  |
| Invoice/payment write | ✓ | ✓ | ✓ |  |  |
| Load/trip read | ✓ | ✓ | ✓ | ✓ | ✓ |
| Load/trip create/update/delete/dispatch/cancel | ✓ | ✓ | ✓ | ✓ |  |
| Load pickup/deliver | ✓ | ✓ | ✓ | ✓ | ✓ |
| Documents read/upload | ✓ | ✓ | ✓ | ✓ | ✓ |
| Documents delete | ✓ | ✓ | ✓ | ✓ |  |
| Drivers/trucks | ✓ | ✓ | ✓ | ✓ |  |
| Terminals read | ✓ | ✓ | ✓ | ✓ | ✓ |
| Terminals create/update | ✓ | ✓ | ✓ | ✓ |  |
| Terminals delete | ✓ | ✓ | ✓ |  |  |
| Inspections/messages/notifications | ✓ | ✓ | ✓ | ✓ | ✓ |

Frontend route guard chỉ để UX; backend vẫn là nguồn authorization.

### 5.4 Hai khái niệm role khác nhau

Không nhầm:

1. JWT roles phía Identity Server quyết định quyền truy cập route;
2. `TenantRole` trong `/api/roles` gán cho Employee và chứa claims nghiệp vụ.

Tạo role qua `/api/roles` không tự cấp quyền Spring Security cho token hiện tại.

Driver không được xác định bằng tên role. `/api/drivers` chỉ trả Employee có claim:

```text
claimType = permission
claimValue = update_trip_status
```

---

## 6. Kiến trúc frontend đề xuất

Framework có thể thay đổi, nhưng nên giữ các boundary:

```text
src/
├── app/
│   ├── router/
│   ├── providers/
│   └── shell/
├── core/
│   ├── auth/
│   ├── api/
│   ├── errors/
│   ├── permissions/
│   └── config/
├── shared/
│   ├── components/
│   ├── forms/
│   ├── table/
│   ├── formatters/
│   └── types/
└── features/
    ├── roles/
    ├── customers/
    ├── employees/
    ├── terminals/
    ├── trucks/
    ├── loads/
    ├── trips/
    ├── invoices/
    ├── payments/
    ├── inspections/
    ├── messaging/
    ├── notifications/
    └── documents/
```

Mỗi feature nên có:

```text
api.ts       // endpoint adapter
types.ts     // request/response/view models
queries.ts   // server state query keys/hooks
schema.ts    // client validation
routes.ts
components/
pages/
```

### Query key convention

```ts
['loads', 'list', filters]
['loads', 'detail', id]
['customers', 'options', search]
```

Sau mutation:

- invalidate list của chính resource;
- invalidate detail ID;
- invalidate các resource chứa denormalized name liên quan;
- state transition phải thay response ngay rồi invalidate list/detail;
- xóa record phải remove detail cache và quay về list.

---

## 7. TypeScript domain contract

Các field không đánh dấu `?` là bắt buộc ở request theo Bean Validation. Response
có thể chứa `null` ở các quan hệ optional dù TypeScript dưới đây dùng `?`.

### 7.1 Shared address

```ts
export interface AddressInput {
  addressLine1: string;
  addressLine2?: string | null;
  addressCity: string;
  addressState: string;
  addressZipCode: string;
  addressCountry: string;
}
```

### 7.2 Roles

```ts
export interface ClaimInput {
  claimType: string;
  claimValue: string;
}

export interface CreateRoleRequest {
  name: string;
  displayName?: string | null;
  claims: ClaimInput[];
}

export interface RoleResponse {
  id: UUID;
  name: string;
  displayName?: string | null;
  normalizedName: string;
  claims: Array<ClaimInput & { id: UUID }>;
}
```

### 7.3 Customers

```ts
export interface CreateCustomerRequest {
  name: string;
  email?: string | null;
  phone?: string | null;
  status: string;
  notes?: string | null;
  taxId?: string | null;
  isVatExempt: boolean;
  addressLine1?: string | null;
  addressLine2?: string | null;
  addressCity?: string | null;
  addressState?: string | null;
  addressZipCode?: string | null;
  addressCountry?: string | null;
}

export interface CustomerResponse extends CreateCustomerRequest {
  id: UUID;
}
```

### 7.4 Employees

```ts
export interface CreateEmployeeRequest {
  email: string;
  firstName: string;
  lastName: string;
  phoneNumber?: string | null;
  salaryType: string;
  status: string;
  joinedDate: ISODateTime;
  roleId?: UUID | null;
  salaryAmount: number;
  salaryCurrency: string;
  addressLine1?: string | null;
  addressLine2?: string | null;
  addressCity?: string | null;
  addressState?: string | null;
  addressZipCode?: string | null;
  addressCountry?: string | null;
}

export interface EmployeeResponse extends CreateEmployeeRequest {
  id: UUID;
  roleName?: string | null;
}
```

### 7.5 Terminals

```ts
export type TerminalType =
  | 'SEA_PORT'
  | 'RAIL_TERMINAL'
  | 'INLAND_DEPOT'
  | 'AIR_CARGO'
  | 'BORDER_CROSSING';

export interface CreateTerminalRequest extends AddressInput {
  name: string;          // max 200
  code: string;          // exactly 5 ASCII letters, UN/LOCODE
  countryCode: string;   // exactly 2 ASCII letters
  type: TerminalType;
  notes?: string | null; // max 2000
}

export interface TerminalResponse extends CreateTerminalRequest {
  id: UUID;
  createdAt: ISODateTime;
  lastModifiedAt?: ISODateTime | null;
}
```

Backend normalize `code` và country code về uppercase. Gửi enum constant như
`SEA_PORT`, không gửi DB value `SeaPort`.

### 7.6 Trucks

```ts
export interface CreateTruckRequest {
  number: string;
  type: string;
  vehicleCapacity: number;
  status: string;
  make?: string | null;
  model?: string | null;
  year?: number | null;
  vin?: string | null;
  licensePlate?: string | null;
  licensePlateState?: string | null;
  isHazmatPlacarded: boolean;
  mainDriverId?: UUID | null;
  secondaryDriverId?: UUID | null;
  adrEquipmentIsAdrCertified?: boolean | null;
  adrEquipmentAllowedClasses?: string | null;
  adrEquipmentOrangePlateNumber?: string | null;
}

export interface TruckResponse
  extends Omit<CreateTruckRequest, 'adrEquipmentOrangePlateNumber'> {
  id: UUID;
  mainDriverName?: string | null;
  secondaryDriverName?: string | null;
  currentLocationLatitude?: number | null;
  currentLocationLongitude?: number | null;
}
```

### 7.7 Loads

```ts
export type LoadStatus =
  | 'draft'
  | 'dispatched'
  | 'picked_up'
  | 'delivered'
  | 'cancelled';

export interface CreateLoadRequest {
  name: string;
  type: string;
  status: LoadStatus;
  distance: number;
  isInProximity: boolean;
  customerId: UUID;
  assignedTruckId?: UUID | null;
  assignedDispatcherId?: UUID | null;
  source: string;
  requestedPickupDate?: ISODateTime | null;
  requestedDeliveryDate?: ISODateTime | null;
  notes?: string | null;
  isHazmat: boolean;
  hazmatClass?: string | null;
  unNumber?: string | null;
  containerId?: UUID | null;
  originTerminalId?: UUID | null;
  destinationTerminalId?: UUID | null;
  externalSourceProvider?: string | null;
  externalSourceId?: string | null;
  externalBrokerReference?: string | null;
  deliveryCostAmount: number;
  deliveryCostCurrency: string;
  originAddressLine1: string;
  originAddressLine2?: string | null;
  originAddressCity: string;
  originAddressState: string;
  originAddressZipCode: string;
  originAddressCountry: string;
  originLocationLatitude: number;
  originLocationLongitude: number;
  destinationAddressLine1: string;
  destinationAddressLine2?: string | null;
  destinationAddressCity: string;
  destinationAddressState: string;
  destinationAddressZipCode: string;
  destinationAddressCountry: string;
  destinationLocationLatitude: number;
  destinationLocationLongitude: number;
}

export interface LoadResponse
  extends Omit<
    CreateLoadRequest,
    'externalSourceProvider' | 'externalSourceId' | 'externalBrokerReference'
  > {
  id: UUID;
  number: number;
  dispatchedAt?: ISODateTime | null;
  pickedUpAt?: ISODateTime | null;
  deliveredAt?: ISODateTime | null;
  cancelledAt?: ISODateTime | null;
  customerName: string;
  assignedTruckNumber?: string | null;
  assignedDispatcherName?: string | null;
}
```

Response hiện không trả tên origin/destination terminal và không trả external
source fields dù request có nhận chúng.

### 7.8 Trips

```ts
export type TripStatus = 'draft' | 'dispatched' | 'completed' | 'cancelled';

export interface TripStopInput {
  type: string;
  order: number; // unique; UI dùng 1-based
  loadId: UUID;
  addressLine1: string;
  addressLine2?: string | null;
  addressCity: string;
  addressState: string;
  addressZipCode: string;
  addressCountry: string;
  locationLatitude: number;
  locationLongitude: number;
}

export interface CreateTripRequest {
  name: string;
  totalDistance: number;
  status: TripStatus;
  truckId?: UUID | null;
  stops: TripStopInput[];
}

export interface TripStopResponse extends TripStopInput {
  id: UUID;
  arrivedAt?: ISODateTime | null;
}

export interface TripResponse {
  id: UUID;
  number: number;
  name: string;
  totalDistance: number;
  status: TripStatus;
  dispatchedAt?: ISODateTime | null;
  completedAt?: ISODateTime | null;
  cancelledAt?: ISODateTime | null;
  truckId?: UUID | null;
  truckNumber?: string | null;
  stops: TripStopResponse[];
}
```

### 7.9 Invoices

```ts
export interface CreateInvoiceRequest {
  type: string;
  status: string;
  taxBehavior?: string | null;
  notes?: string | null;
  dueDate?: ISODateTime | null;
  loadId?: UUID | null;
  customerId?: UUID | null;
  employeeId?: UUID | null;
  subtotalAmount: number;
  subtotalCurrency: string;
  taxTotalAmount: number;
  taxTotalCurrency: string;
  totalAmount: number;
  totalCurrency: string;
  periodStart?: ISODateTime | null;
  periodEnd?: ISODateTime | null;
  totalDistanceDriven?: number | null;
}

export interface InvoiceResponse extends CreateInvoiceRequest {
  id: UUID;
  number: number;
  customerName?: string | null;
  employeeName?: string | null;
  sentAt?: ISODateTime | null;
  sentToEmail?: string | null;
}
```

### 7.10 Payments

```ts
export interface CreatePaymentRequest {
  status: string;
  invoiceId?: UUID | null;
  amountAmount: number;
  amountCurrency: string;
  description?: string | null;
  referenceNumber?: string | null;
  stripePaymentMethodId?: string | null;
  stripePaymentIntentId?: string | null;
  recordedAt?: ISODateTime | null;
  billingAddressLine1: string;
  billingAddressLine2?: string | null;
  billingAddressCity: string;
  billingAddressState: string;
  billingAddressZipCode: string;
  billingAddressCountry: string;
}

export interface PaymentResponse {
  id: UUID;
  status: string;
  invoiceId?: UUID | null;
  invoiceNumber?: number | null;
  amountAmount: number;
  amountCurrency: string;
  description?: string | null;
  referenceNumber?: string | null;
  recordedAt?: ISODateTime | null;
  billingAddressLine1: string;
  billingAddressLine2?: string | null;
  billingAddressCity: string;
  billingAddressState: string;
  billingAddressZipCode: string;
  billingAddressCountry: string;
}
```

Stripe IDs được nhận ở request nhưng không trả lại trong response.

### 7.11 Inspections

```ts
export interface DefectInput {
  partCategory: string;
  description: string;
  severity: string;
}

export interface CreateInspectionRequest {
  loadId: UUID;
  type: string;
  vin?: string | null;
  vehicleYear?: number | null;
  vehicleMake?: string | null;
  vehicleModel?: string | null;
  vehicleBodyClass?: string | null;
  containerNumber?: string | null;
  sealNumber?: string | null;
  notes?: string | null;
  inspectorSignature?: string | null;
  latitude?: number | null;
  longitude?: number | null;
  inspectedAt: ISODateTime;
  inspectedById: UUID;
  defects: DefectInput[];
}

export interface InspectionResponse
  extends Omit<CreateInspectionRequest, 'defects' | 'inspectorSignature'> {
  id: UUID;
  inspectedByName?: string | null;
  defects: Array<DefectInput & { id: UUID }>;
}
```

`inspectorSignature` hiện được nhận nhưng không có trong response DTO.

### 7.12 Messaging

```ts
export interface CreateConversationRequest {
  name?: string | null;
  loadId?: UUID | null;
  isTenantChat: boolean;
  participantIds: UUID[]; // non-empty, unique Set ở backend
}

export interface ConversationResponse {
  id: UUID;
  name?: string | null;
  loadId?: UUID | null;
  isTenantChat: boolean;
  createdAt: ISODateTime;
  lastMessageAt?: ISODateTime | null;
  participantIds: UUID[];
}

export interface SendMessageRequest {
  conversationId: UUID;
  senderId: UUID;
  content: string; // 1..2000
}

export interface MessageResponse {
  id: UUID;
  conversationId: UUID;
  senderId?: UUID | null;
  senderName?: string | null;
  content: string;
  sentAt: ISODateTime;
  isDeleted: boolean;
}
```

### 7.13 Notifications

```ts
export interface NotificationResponse {
  id: UUID;
  title: string;
  message: string;
  isRead: boolean;
  createdDate: ISODateTime;
}
```

### 7.14 Documents

```ts
export interface DocumentUploadRequest {
  ownerType: string;
  type: string;
  description?: string | null;
  uploadedById: UUID;
  loadId?: UUID | null;
  truckId?: UUID | null;
  employeeId?: UUID | null;
  recipientName?: string | null;
  capturedAt?: ISODateTime | null;
  captureLatitude?: number | null;
  captureLongitude?: number | null;
  notes?: string | null;
}

export interface DocumentResponse extends DocumentUploadRequest {
  id: UUID;
  fileName: string;
  originalFileName: string;
  contentType: string;
  fileSizeBytes: number;
  blobPath: string;
  blobContainer: string;
  status: string; // upload tạo "active"
  uploadedByName?: string | null;
}
```

Không hiển thị `blobPath`/`blobContainer` như public URL.

---

## 8. Endpoint và checklist theo chức năng

## 8.1 App shell, health và API availability

### API

| Method | Path | Auth | Kết quả |
|---|---|---|---|
| GET | `/api/health` | Public | `{status, application, profiles, database}` |

### Checklist

- [ ] Có runtime config cho `apiBaseUrl` và `identityBaseUrl`.
- [ ] Có trang lỗi cấu hình khi base URL thiếu.
- [ ] Health check không gửi Bearer token rỗng.
- [ ] Phân biệt API unreachable, API unhealthy và browser CORS.
- [ ] Hiển thị environment/profile ở trang diagnostics, không ở UI người dùng thường.
- [ ] Không cho vào business route khi backend chạy `nodb`.
- [ ] Mỗi request sinh `X-Request-Id`.
- [ ] Error screen cho phép copy request ID.

### Acceptance tests

- [ ] API `UP` → app tiếp tục bootstrap.
- [ ] Connection refused → thông báo retry, không crash shell.
- [ ] Health trả `database=disabled` → business features bị khóa.

---

## 8.2 Authentication/session

### External endpoints

```text
GET  {identityUrl}/.well-known/openid-configuration
POST {identityUrl}/connect/token
GET  {identityUrl}/connect/authorize
```

### Checklist

- [ ] Authorization Code + PKCE cho browser.
- [ ] Validate `state` và `nonce`.
- [ ] Access token giữ trong memory hoặc cơ chế an toàn tương đương.
- [ ] Parse `exp`; refresh trước khi hết hạn theo clock skew hợp lý.
- [ ] Chỉ retry request một lần sau refresh.
- [ ] Refresh thất bại → xóa session và chuyển login.
- [ ] Decode role/roles để ẩn action không được phép.
- [ ] 401 và 403 có UX khác nhau.
- [ ] Logout đồng bộ với Identity Server nếu được hỗ trợ.
- [ ] Không log token, refresh token hoặc Authorization header.
- [ ] Có pending state trong lúc auth bootstrap để tránh route flash.
- [ ] Chặn app nếu token không có tenant claim.

### Acceptance tests

- [ ] Token hết hạn giữa phiên → refresh và replay đúng một request.
- [ ] Refresh token hết hạn → logout sạch.
- [ ] User DRIVER không thấy menu Role/Employee.
- [ ] Deep link protected khi chưa login quay lại đúng URL sau login.

---

## 8.3 Roles

### API

| Method | Path | Quyền | Ghi chú |
|---|---|---|---|
| GET | `/api/roles?page&pageSize` | SA/O | list |
| GET | `/api/roles/{id}` | SA/O | detail |
| POST | `/api/roles` | SA/O | `201` |
| PUT | `/api/roles/{id}` | SA/O | full payload |
| DELETE | `/api/roles/{id}` | SA/O | có thể `409` nếu đang dùng |

### Business rules

- `name` unique theo normalized uppercase.
- Update claims là full replacement.
- Driver lookup cần claim `permission:update_trip_status`.

### Checklist

- [ ] Route list/create/edit/detail có guard SA/O.
- [ ] Table: name, displayName, claim count.
- [ ] Form name required; displayName optional.
- [ ] Claim editor hỗ trợ add/remove row.
- [ ] Không gửi claim row rỗng.
- [ ] Cảnh báo khi xóa role đang được employee dùng.
- [ ] Map `409 CONFLICT` thành lỗi duplicate name.
- [ ] Sau mutation invalidate role list và employee lookup.
- [ ] Có preset/tooltip cho `update_trip_status`.
- [ ] Không trình bày TenantRole như JWT route role.

---

## 8.4 Customers

### API

| Method | Path | Filters/quyền |
|---|---|---|
| GET | `/api/customers` | `search,status,page,pageSize,orderBy,descending`; SA/O/M |
| GET | `/api/customers/{id}` | SA/O/M |
| POST | `/api/customers` | SA/O/M |
| PUT | `/api/customers/{id}` | SA/O/M |
| DELETE | `/api/customers/{id}` | SA/O/M |

Search áp dụng cho `name` và `email`. Sort an toàn: `name`, `email`, `status`.

### Checklist

- [ ] List có search debounce, status filter, sort và pagination.
- [ ] Table tối thiểu: name, email, phone, status, taxId.
- [ ] Form: name/status/isVatExempt required.
- [ ] Email client validation nhưng cho phép rỗng.
- [ ] Address group có country/state/ZIP.
- [ ] Detail hiển thị loads/invoices chỉ khi có endpoint filter tương ứng.
- [ ] Delete confirmation nêu ảnh hưởng load/invoice.
- [ ] `404` sau mở detail → quay list với thông báo.
- [ ] Customer selector trong Load/Invoice dùng server search.
- [ ] Không fetch toàn bộ customers chỉ để làm dropdown.

---

## 8.5 Employees và Drivers

### API

| Method | Path | Filters/quyền |
|---|---|---|
| GET | `/api/employees` | `search,status,roleId,...`; SA/O |
| GET | `/api/employees/{id}` | SA/O |
| POST | `/api/employees` | SA/O |
| PUT | `/api/employees/{id}` | SA/O |
| DELETE | `/api/employees/{id}` | SA/O |
| GET | `/api/drivers` | `search,status,...`; SA/O/M/D |
| GET | `/api/drivers/{id}` | SA/O/M/D |

Drivers là read-only view của Employee có claim
`permission:update_trip_status`.

### Checklist

- [ ] Employee list và Driver picker là hai query khác nhau.
- [ ] Employee form load role options trước.
- [ ] Required: email, firstName, lastName, salaryType, status, joinedDate,
      salaryAmount, salaryCurrency.
- [ ] Email duplicate hiển thị inline từ `409`.
- [ ] joinedDate gửi ISO-8601.
- [ ] Salary dùng decimal input, không dùng float arithmetic.
- [ ] roleId có thể null.
- [ ] Truck driver dropdown chỉ gọi `/api/drivers`.
- [ ] Driver detail trả `404` nếu employee không có driver permission.
- [ ] Update employee name phải invalidate truck views vì response truck chứa driver name.
- [ ] Delete employee xử lý conflict khi đang được truck/load/document tham chiếu.

---

## 8.6 Terminals

### API

| Method | Path | Filters/quyền |
|---|---|---|
| GET | `/api/terminals` | `search,type,countryCode,...`; mọi tenant role |
| GET | `/api/terminals/{id}` | mọi tenant role |
| POST/PUT | `/api/terminals/{id?}` | SA/O/M/D |
| DELETE | `/api/terminals/{id}` | SA/O/M |

Sort an toàn: `name`, `code`, `countryCode`, `type`, `createdAt`.

### Checklist

- [ ] Enum UI dùng đúng 5 `TerminalType`.
- [ ] `code` exactly 5 letters; uppercase khi blur.
- [ ] `countryCode` exactly 2 letters; uppercase khi blur.
- [ ] name max 200; notes max 2000.
- [ ] Address line 1/city/state/ZIP/country required.
- [ ] Duplicate code (`409`) map vào field code.
- [ ] Delete `TERMINAL_IN_USE` hiển thị message không cho retry vô hạn.
- [ ] Terminal selector hỗ trợ search theo name/code.
- [ ] Driver được xem nhưng không thấy edit/delete action.

---

## 8.7 Trucks

### API

| Method | Path | Filters/quyền |
|---|---|---|
| GET | `/api/trucks` | `search,status,type,...`; SA/O/M/D |
| GET | `/api/trucks/{id}` | SA/O/M/D |
| POST | `/api/trucks` | SA/O/M/D |
| PUT | `/api/trucks/{id}` | SA/O/M/D |
| DELETE | `/api/trucks/{id}` | SA/O/M/D |

Search: number, VIN, license plate. Sort an toàn: `number`, `status`, `type`,
`year`.

### Checklist

- [ ] Required: number, type, vehicleCapacity, status, isHazmatPlacarded.
- [ ] number duplicate → inline `409`.
- [ ] Main/secondary driver dùng `/api/drivers`.
- [ ] Không cho cùng một driver ở cả hai select trong UI.
- [ ] Cho phép driver null vì backend hiện cho phép.
- [ ] ADR fields chỉ hiện khi certified/hazmat context phù hợp.
- [ ] Validate year và capacity phía client; backend hiện chỉ kiểm tra non-null.
- [ ] Detail hiển thị last known location nếu có.
- [ ] Không mong chờ endpoint cập nhật GPS — hiện chưa có.
- [ ] Delete xử lý foreign-key conflict khi truck đang được dùng.

---

## 8.8 Loads

### API

| Method | Path | Quyền |
|---|---|---|
| GET | `/api/loads` | mọi tenant role |
| GET | `/api/loads/{id}` | mọi tenant role |
| POST/PUT/DELETE | `/api/loads/{id?}` | SA/O/M/D |
| POST | `/api/loads/{id}/dispatch` | SA/O/M/D |
| POST | `/api/loads/{id}/pick-up` | mọi tenant role |
| POST | `/api/loads/{id}/deliver` | mọi tenant role |
| POST | `/api/loads/{id}/cancel` | SA/O/M/D |

Filters: `search,status,customerId,truckId,dispatcherId`. Search trên name và
external broker reference. Sort an toàn: `name`, `number`, `status`,
`requestedPickupDate`, `requestedDeliveryDate`.

### State machine

```text
draft ──dispatch──> dispatched ──pick-up──> picked_up ──deliver──> delivered
  └────────────── cancel <──────────────┘
```

Cancel hợp lệ từ `draft`, `dispatched`, `picked_up`. `delivered` và `cancelled`
là terminal.

Create bắt buộc `status=draft`. Generic update phải gửi lại đúng status hiện tại;
không đổi status bằng PUT.

### Checklist list/detail

- [ ] List có search, status, customer, truck, dispatcher filter.
- [ ] Badge status dùng canonical lowercase values.
- [ ] Detail hiển thị customer/truck/dispatcher names từ response.
- [ ] Timeline dùng dispatchedAt/pickedUpAt/deliveredAt/cancelledAt.
- [ ] Origin/destination card hiển thị address + coordinates.
- [ ] Currency/amount format theo deliveryCostCurrency.
- [ ] Hazmat section chỉ hiện khi isHazmat.
- [ ] Link sang inspections/documents bằng `loadId`.
- [ ] Không giả định Trip/Invoice list lọc được bằng `loadId`; endpoint hiện chưa
      có filter đó.

### Checklist form

- [ ] Create luôn gửi `draft`.
- [ ] Edit giữ nguyên `status` từ entity.
- [ ] Customer required.
- [ ] Truck, dispatcher, container, terminals optional.
- [ ] Không hiển thị container picker cho đến khi backend có container endpoint
      hoặc có nguồn ID hợp lệ.
- [ ] Pickup/delivery date validate thứ tự.
- [ ] Address và lat/lng đầy đủ ở cả hai đầu.
- [ ] Money dùng decimal.
- [ ] Optional relationship bị clear phải gửi `null`.

### Checklist actions

- [ ] Chỉ hiện Dispatch khi status `draft`.
- [ ] Chỉ hiện Pick up khi `dispatched`.
- [ ] Chỉ hiện Deliver khi `picked_up`.
- [ ] Chỉ hiện Cancel khi chưa terminal và role phù hợp.
- [ ] Confirm destructive/status action.
- [ ] Disable action trong lúc request pending.
- [ ] Sau action dùng response cập nhật detail rồi invalidate list.
- [ ] `INVALID_STATE_TRANSITION` → refresh detail trước khi báo lỗi.

### Known backend issue

Load dispatch phát event để đổi Invoice `Draft` thành `Issued`, nhưng listener đang
tìm exact string `"Draft"` trong khi Postman/request mẫu dùng `"draft"`. Frontend
không được giả định invoice tự đổi status cho tới khi backend normalize case.

---

## 8.9 Trips

### API

| Method | Path | Quyền |
|---|---|---|
| GET | `/api/trips` | mọi tenant role |
| GET | `/api/trips/{id}` | mọi tenant role |
| POST | `/api/trips` | SA/O/M/D |
| PUT | `/api/trips/{id}` | SA/O/M/D |
| DELETE | `/api/trips/{id}` | SA/O/M/D |
| POST | `/api/trips/{id}/dispatch` | SA/O/M/D |
| POST | `/api/trips/{id}/complete` | SA/O/M/D |
| POST | `/api/trips/{id}/cancel` | SA/O/M/D |

Filters: `search,status,truckId`. Sort an toàn: `name`, `number`, `status`,
`totalDistance`.

### State machine

```text
draft ──dispatch──> dispatched ──complete──> completed
  └────────────── cancel <──────────┘
```

Cancel hợp lệ từ draft/dispatched. Create bắt buộc draft; PUT không đổi status.

### Checklist

- [ ] Trip form có truck optional và stop builder.
- [ ] Mỗi stop có load required.
- [ ] Stop order unique; UI dùng 1, 2, 3...
- [ ] Drag/drop phải reindex order trước submit.
- [ ] Backend rebuild toàn bộ stops khi update; gửi full list.
- [ ] Validate ít nhất một stop ở UI dù backend chỉ yêu cầu list non-null.
- [ ] Detail sắp stops theo order.
- [ ] Action visibility theo state machine.
- [ ] Trip transition tạo tenant notification; refresh notification count/list.
- [ ] Không hiển thị arrivedAt editor vì request không hỗ trợ field này.
- [ ] Delete trip xử lý relation conflict.

---

## 8.10 Invoices

### API

| Method | Path | Quyền |
|---|---|---|
| GET | `/api/invoices` | SA/O/M/D |
| GET | `/api/invoices/{id}` | SA/O/M/D |
| POST | `/api/invoices` | SA/O/M |
| PUT | `/api/invoices/{id}` | SA/O/M |
| DELETE | `/api/invoices/{id}` | SA/O/M |

Filters: `status,type,customerId,employeeId`. Sort an toàn: `number`, `status`,
`dueDate`, `totalAmount`.

### Checklist

- [ ] List: number, type, status, customer, due date, total.
- [ ] Form có load/customer/employee optional theo backend, nhưng business UI nên
      yêu cầu theo invoice type.
- [ ] Currency fields nhất quán.
- [ ] Preview subtotal + tax = total bằng decimal library.
- [ ] Không tự tin rằng backend sẽ recalculate; request hiện gửi cả ba total.
- [ ] Detail hiển thị payments bằng filter `invoiceId`.
- [ ] Không xây line-item UI dựa trên API hiện tại — DTO chưa có line items.
- [ ] Không xây Send/Approve/Cancel action — chưa có endpoint.
- [ ] Generic update hiện có thể thay status string; frontend nên giới hạn conservatively.
- [ ] Xử lý one-to-one load conflict khi tạo invoice trùng load.
- [ ] Kiểm thử issue-on-load-dispatch sau khi backend sửa case mismatch.

---

## 8.11 Payments

### API

| Method | Path | Quyền |
|---|---|---|
| GET | `/api/payments` | SA/O/M/D |
| GET | `/api/payments/{id}` | SA/O/M/D |
| POST | `/api/payments` | SA/O/M |
| PUT | `/api/payments/{id}` | SA/O/M |
| DELETE | `/api/payments/{id}` | SA/O/M |

Filters: `status,invoiceId`. Sort an toàn: `recordedAt`, `status`,
`amountAmount`, `referenceNumber`.

### Checklist

- [ ] Invoice picker ưu tiên required trong UI dù backend cho null.
- [ ] Amount positive client validation.
- [ ] Currency phải khớp invoice.
- [ ] Billing address required trừ line 2.
- [ ] recordedAt mặc định now ISO.
- [ ] Reference number/search UI chỉ khi endpoint hỗ trợ; list hiện chưa có search.
- [ ] Không giả định payment tự đổi invoice status; service hiện chưa làm việc đó.
- [ ] Stripe fields không dùng làm full Stripe flow; chưa có intent/webhook endpoint.
- [ ] Delete payment refresh invoice detail/totals.
- [ ] Không lưu/display Stripe secret values.

---

## 8.12 Inspections

### API

| Method | Path | Quyền |
|---|---|---|
| GET | `/api/inspections?loadId&type...` | mọi tenant role |
| GET | `/api/inspections/{id}` | mọi tenant role |
| POST | `/api/inspections` | mọi tenant role |
| PUT | `/api/inspections/{id}` | mọi tenant role |
| DELETE | `/api/inspections/{id}` | mọi tenant role |

Sort an toàn: `inspectedAt`, `type`.

### Checklist

- [ ] Load và inspector required.
- [ ] inspectedAt ISO datetime required.
- [ ] Defects là array required; cho phép empty nếu business chấp nhận.
- [ ] Defect row required partCategory, description, severity.
- [ ] Validate lat `[-90,90]`, lng `[-180,180]` ở UI.
- [ ] Update gửi toàn bộ defects; backend xóa/rebuild danh sách.
- [ ] Xác nhận trước khi xóa defect row có dữ liệu.
- [ ] Detail hiển thị vehicle/container/seal tùy dữ liệu.
- [ ] Không mong chờ inspectorSignature trong response hiện tại.
- [ ] Không xây `/parts` hoặc VIN decode API — chưa tồn tại.
- [ ] Attach documents bằng generic document API với load/employee relation.

---

## 8.13 Messaging

### API

| Method | Path | Ghi chú |
|---|---|---|
| GET | `/api/messages/conversations?employeeId&page&pageSize` | newest conversation first |
| GET | `/api/messages/conversations/{id}` | detail |
| POST | `/api/messages/conversations` | create |
| GET | `/api/messages?conversationId&page&pageSize` | messages ascending |
| POST | `/api/messages` | send, content max 2000 |
| GET | `/api/messages/unread-count?employeeId` | `data: number` |
| POST | `/api/messages/conversations/{conversationId}/read?employeeId` | `data: markedCount` |

### Checklist

- [ ] Chưa triển khai cho production trước khi có current employee mapping.
- [ ] Conversation list dùng employeeId của authenticated principal, không cho user sửa.
- [ ] Conversation participant picker dùng employee endpoint theo quyền phù hợp.
- [ ] Create yêu cầu participantIds non-empty.
- [ ] Sender phải là participant; map `BAD_REQUEST` rõ ràng.
- [ ] Message composer giới hạn 2000 chars và trim whitespace.
- [ ] Optimistic message cần reconcile bằng ID server hoặc tránh nếu chưa có idempotency.
- [ ] List message ascending; pagination cũ hơn phải prepend không đảo order.
- [ ] Mark read khi conversation đã render/active, không chỉ khi mở route.
- [ ] Refresh unread count sau send/mark read.
- [ ] Không xây edit/delete message — chưa có endpoint.
- [ ] Không giả định real-time/SignalR/WebSocket — Spring hiện chỉ REST.
- [ ] Nếu polling, pause khi tab hidden và dùng backoff.

---

## 8.14 Notifications

### API

| Method | Path | Ghi chú |
|---|---|---|
| GET | `/api/notifications?page&pageSize` | newest first |
| GET | `/api/notifications/{id}` | detail |
| POST | `/api/notifications/mark-all-read` | trả số row update |

### Checklist

- [ ] Badge unread tính từ list hiện có hoặc endpoint bổ sung; chưa có unread-count.
- [ ] Mark all read cập nhật cache optimistic rồi reconcile count.
- [ ] Không xây mark-one-read — chưa có endpoint.
- [ ] Không giả định notification per-user; hiện là tenant-wide.
- [ ] Poll có backoff; chưa có real-time endpoint.
- [ ] Trip transition phải làm notification list stale.
- [ ] Empty state riêng cho không có notification.
- [ ] createdDate format theo locale/timezone.

---

## 8.15 Documents

### API

| Method | Path | Quyền |
|---|---|---|
| GET | `/api/documents` và `/{id}` | mọi tenant role |
| POST | `/api/documents` multipart | mọi tenant role |
| GET | `/api/documents/{id}/download` | mọi tenant role |
| DELETE | `/api/documents/{id}` | SA/O/M/D |

Filters: `type,status,loadId,truckId,employeeId`. Sort an toàn:
`fileName`, `originalFileName`, `fileSizeBytes`, `capturedAt`, `status`.

Multipart:

```ts
const form = new FormData();
form.append('file', file);
form.append(
  'metadata',
  new Blob([JSON.stringify(metadata)], { type: 'application/json' })
);
```

### Checklist

- [ ] Không set multipart Content-Type bằng tay.
- [ ] File và metadata là hai part đúng tên.
- [ ] uploadedById required và phải lấy từ current employee mapping.
- [ ] ownerType/type required.
- [ ] Load/truck/employee relations optional.
- [ ] Progress UI và cancel upload.
- [ ] Retry phải cảnh báo nguy cơ duplicate vì chưa có idempotency.
- [ ] Download dùng blob response và parse `Content-Disposition`.
- [ ] Không dùng blobPath làm URL.
- [ ] Delete confirmation và permission guard.
- [ ] Client-side size/MIME policy phải được cấu hình, không hard-code bí mật.
- [ ] Filename không cho path traversal; backend cũng kiểm tra.
- [ ] Sau upload/delete invalidate document list của entity liên quan.

---

## 9. Route map đề xuất

```text
/login
/auth/callback
/forbidden
/diagnostics

/roles
/roles/new
/roles/:id
/roles/:id/edit

/customers
/customers/new
/customers/:id
/customers/:id/edit

/employees
/employees/new
/employees/:id
/employees/:id/edit
/drivers
/drivers/:id

/terminals
/terminals/new
/terminals/:id
/terminals/:id/edit

/trucks
/trucks/new
/trucks/:id
/trucks/:id/edit

/loads
/loads/new
/loads/:id
/loads/:id/edit

/trips
/trips/new
/trips/:id
/trips/:id/edit

/invoices
/invoices/new
/invoices/:id
/invoices/:id/edit

/payments
/payments/new
/payments/:id
/payments/:id/edit

/inspections
/inspections/new
/inspections/:id
/inspections/:id/edit

/messages
/messages/:conversationId

/notifications
/documents
/documents/:id
```

Không tạo route production cho dashboard/reports/maps/container/user management
nếu chưa có API tương ứng.

---

## 10. Chức năng chưa có trong Spring API hiện tại

Các mục sau xuất hiện trong business spec hoặc tài liệu cũ nhưng **không có
controller contract hiện tại**:

- dashboard/stats/reports;
- users, invitations, profile/current user;
- tenants, subscription plans, subscriptions;
- container CRUD;
- tracking links và public tracking;
- GPS/location update;
- maintenance, HOS/ELD, DVIR, accident, expenses;
- load board;
- AI dispatch;
- Stripe checkout/payment intent/webhook;
- invoice line items và invoice action endpoints;
- POD/BOL endpoint chuyên biệt;
- VIN decode và inspection part catalogue;
- WebSocket/SignalR/SSE;
- individual notification mark-read;
- message edit/delete;
- upload policy/sign URL.

Frontend có thể dựng placeholder có feature flag, nhưng không được gọi endpoint
tưởng tượng hoặc dùng dữ liệu mock trong production.

---

## 11. Global UI checklist

### UX states

- [ ] Mọi page có loading, error, empty, populated states.
- [ ] Mutation có disabled/pending state chống double submit.
- [ ] Destructive action có confirm.
- [ ] Toast không thay thế inline validation.
- [ ] Giữ filter/sort/page trên URL.
- [ ] Back navigation phục hồi list state.
- [ ] Detail stale/404 có recovery path.
- [ ] Mobile layout cho table/form dài.

### Forms

- [ ] Dùng schema validation đồng bộ required/format/max length.
- [ ] Map backend `errors[].field` vào đúng field.
- [ ] Focus field lỗi đầu tiên.
- [ ] Trim string theo policy trước submit.
- [ ] Phân biệt `undefined` (không gửi) và `null` (clear relationship).
- [ ] Date serialize ISO-8601.
- [ ] Money dùng decimal-safe logic.
- [ ] Prevent navigation khi form dirty.

### Tables

- [ ] Server pagination 1-based.
- [ ] pageSize không vượt 100.
- [ ] Filter đổi thì reset page.
- [ ] Debounce/cancel request search cũ.
- [ ] Sort whitelist theo module.
- [ ] Stable row key dùng UUID.
- [ ] Action menu theo permission + entity state.

### Accessibility

- [ ] Keyboard navigation đầy đủ.
- [ ] Label/error liên kết bằng ARIA.
- [ ] Focus management cho modal.
- [ ] Status không chỉ thể hiện bằng màu.
- [ ] Contrast đạt WCAG AA.
- [ ] Loading/action có accessible announcement.

### Security

- [ ] Không render message/document filename bằng unsafe HTML.
- [ ] Không log token hoặc payload nhạy cảm.
- [ ] Không dựa vào hidden button như authorization.
- [ ] Không nhận employeeId/tenantId tùy ý từ URL cho hành động current-user.
- [ ] Download blob không nhúng inline nếu MIME nguy hiểm.
- [ ] CSP và dependency audit trong pipeline.

---

## 12. Test checklist

### Unit

- [ ] API envelope unwrap.
- [ ] Error normalization theo code/status.
- [ ] Role matrix và action visibility.
- [ ] State-machine action visibility cho Load/Trip.
- [ ] Date/time và money formatter.
- [ ] Form schema required/format.
- [ ] Query serializer bỏ filter rỗng.

### Component

- [ ] Table loading/error/empty.
- [ ] Validation field mapping.
- [ ] Delete confirmation.
- [ ] Dynamic defect/claim/stop rows.
- [ ] Multipart FormData đúng part.
- [ ] Download blob filename.

### Integration

- [ ] 401 → refresh một lần.
- [ ] 403 không refresh.
- [ ] 409 hiển thị conflict message.
- [ ] Search race không overwrite kết quả mới.
- [ ] Mutation invalidates đúng cache.
- [ ] State transition refresh entity.

### E2E critical path

- [ ] Login.
- [ ] Create Role có driver claim.
- [ ] Create Customer.
- [ ] Create Employee với Role.
- [ ] Employee xuất hiện trong Drivers.
- [ ] Create Terminal.
- [ ] Create Truck gán Driver.
- [ ] Create draft Load.
- [ ] Dispatch → Pick up → Deliver Load.
- [ ] Create Trip với ordered stops.
- [ ] Dispatch → Complete Trip.
- [ ] Create Invoice và Payment.
- [ ] Create Inspection có defects.
- [ ] Create Conversation và send/mark-read Message.
- [ ] Upload/download/delete Document.
- [ ] Cleanup theo thứ tự dependency ngược.

### Permission E2E

- [ ] DRIVER đọc Load/Trip/Terminal.
- [ ] DRIVER không sửa Truck/Customer/Invoice.
- [ ] DRIVER được pickup/deliver Load.
- [ ] DISPATCHER đọc invoice/payment nhưng không write.
- [ ] MANAGER write customer/invoice/payment.
- [ ] OWNER quản lý role/employee.
- [ ] Invalid token → 401; valid wrong role → 403.

---

## 13. Definition of Done cho từng feature

Một feature chỉ hoàn thành khi:

- [ ] route và permission guard đúng;
- [ ] list/detail/form/action theo endpoint thật;
- [ ] type request/response đúng DTO;
- [ ] loading/error/empty/pending đầy đủ;
- [ ] backend field errors map đúng form;
- [ ] filter/sort/page hoạt động qua URL;
- [ ] mutation invalidation đúng;
- [ ] responsive + keyboard + accessible;
- [ ] unit/component/integration tests pass;
- [ ] Postman hoặc E2E test contract pass;
- [ ] không gọi endpoint chưa tồn tại;
- [ ] known backend gap được ticket hóa, không che bằng frontend workaround nguy hiểm.

---

## 14. Thứ tự triển khai đề xuất

### Phase 0 — Integration foundation

1. runtime config;
2. CORS backend;
3. OAuth/PKCE;
4. API client/envelope/errors/request ID;
5. route guard/permission;
6. shared table/form/confirm/toast.

### Phase 1 — Master data

1. Roles;
2. Customers;
3. Employees/Drivers;
4. Terminals;
5. Trucks.

### Phase 2 — Operations

1. Loads + state machine;
2. Trips + stop builder + state machine;
3. Inspections;
4. Documents.

### Phase 3 — Finance

1. Invoices;
2. Payments;
3. resolve invoice/payment status consistency.

### Phase 4 — Collaboration

1. `/api/me` hoặc employee claim;
2. messaging authorization fix;
3. Messages;
4. Notifications;
5. realtime nếu backend bổ sung.

### Phase 5 — Missing product modules

Chỉ bắt đầu sau khi backend có contract: dashboard, containers, tracking,
maintenance, HOS, Stripe, reporting, subscriptions.

---

## 15. Nguồn đối chiếu trong repository

Các nguồn chính:

- `src/main/java/**/controller/*Controller.java`
- `src/main/java/**/dto/request/*.java`
- `src/main/java/**/dto/response/*.java`
- `src/main/java/**/service/impl/*ServiceImpl.java`
- `src/main/java/com/company/logicstic/shared/config/SecurityConfiguration.java`
- `src/main/java/com/company/logicstic/shared/exception/GlobalExceptionHandler.java`
- `src/main/java/com/company/logicstic/shared/exception/ErrorCode.java`
- `src/main/java/com/company/logicstic/shared/dto/ApiResponse.java`
- `src/main/java/com/company/logicstic/shared/dto/PagedResponse.java`
- `scripts/postman/logicstic-api.postman_collection.json`
- runtime OpenAPI: `/v3/api-docs`

Khi backend thay đổi DTO/controller:

1. cập nhật OpenAPI annotations/status;
2. regenerate API client nếu dùng generator;
3. regenerate Postman collection;
4. cập nhật file context này;
5. chạy contract + frontend E2E.
