# Báo cáo chức năng chưa hoàn thiện trong `modules`

> Trạng thái: hoàn tất lượt rà soát ban đầu — báo cáo sống, cập nhật khi sửa/xác minh lại
>
> Cập nhật gần nhất: 2026-07-21
>
> Phạm vi: `src/main/java/com/company/logicstic/modules`
>
> Nguyên tắc: chỉ ghi nhận phát hiện có bằng chứng trực tiếp từ mã nguồn, hợp đồng API,
> cấu hình build hoặc kết quả kiểm thử. Các nghi vấn chưa xác minh không được tính là lỗi.

## Tiến độ

- [x] Lập danh sách file và kiểm tra dấu hiệu stub/TODO rõ ràng.
- [x] Đối chiếu các đường chạy chính của customer, employee, fleet, load, finance và messaging.
- [x] Đối chiếu đầy đủ controller, service, repository, mapper và entity của các module còn lại.
- [x] Đối chiếu phạm vi kiểm thử hiện có.
- [x] Chạy build/test lần đầu (`./mvnw test`).
- [x] Chốt mức độ ưu tiên và phạm vi ảnh hưởng.

## Tóm tắt theo dõi

Có 16 phát hiện đã xác minh: 5 P0, 7 P1 và 4 P2. Cả 16 mục hiện đã `FIXED`; bảng này là điểm
đối chiếu giữa phát hiện, bản sửa và regression test.

| ID | Mức | Trạng thái | Nội dung ngắn |
|---|---:|---|---|
| MOD-001 | P0 | FIXED | JPQL messaging dùng thuộc tính không tồn tại |
| MOD-002 | P0 | FIXED | Message thiếu `sentAt` và `isDeleted` khi insert |
| MOD-003 | P0 | FIXED | Payment có `tenantId` UUID dư thừa nhưng không có nguồn gán |
| MOD-015 | P0 | FIXED | Migration V2 rename sai và bỏ sót cột audit |
| MOD-016 | P0 | FIXED | `DataSeeder` thiếu import messaging, fresh compile thất bại |
| MOD-004 | P1 | FIXED | Duplicate check Employee/Truck update bị vô hiệu |
| MOD-005 | P1 | FIXED | Driver API trả mọi Employee |
| MOD-006 | P1 | FIXED | Load bỏ qua container/terminal IDs |
| MOD-007 | P1 | FIXED | Conversation API không tạo participant |
| MOD-008 | P1 | FIXED | Update Load bypass state machine |
| MOD-009 | P1 | FIXED | Messaging thiếu last-message/read workflow |
| MOD-010 | P1 | FIXED | Document thiếu upload/download/storage lifecycle |
| MOD-011 | P2 | FIXED | Inspection không quản lý defects |
| MOD-012 | P2 | FIXED | Trip thiếu lifecycle và stops |
| MOD-013 | P2 | FIXED | Notification không có producer |
| MOD-014 | P2 | FIXED | Driver share không chuẩn hóa scale tiền |

## Phát hiện đã xác minh

### MOD-001 — P0 — FIXED — Messaging dùng thuộc tính JPQL không tồn tại

**Bằng chứng**

- `ConversationRepository.java:17` truy cập `p.employeeId`, nhưng
  `ConversationParticipant` chỉ có quan hệ `employee` tại
  `ConversationParticipant.java:39-41`.
- `MessageRepository.java:19` cũng dùng `p.employeeId`.
- `MessageRepository.java:23` dùng `r.employeeId`, nhưng `MessageReadReceipt` chỉ có quan hệ
  `readBy` tại `MessageReadReceipt.java:37-39`.

**Tác động**

Hibernate/Spring Data không thể biên dịch các query này khi khởi tạo repository. Khi profile có
JPA được bật, bean `ConversationRepository`/`MessageRepository` và các service phụ thuộc vào chúng
không thể khởi tạo; toàn bộ API messaging bị chặn.

**Sửa tối thiểu đề xuất**

Đổi các path thành `p.employee.id` và `r.readBy.id`, sau đó thêm context/repository integration test
để query được parse và chạy thật.

### MOD-002 — P0 — FIXED — Gửi message tạo bản ghi thiếu hai cột `NOT NULL`

**Bằng chứng**

- `Message.sentAt` và `Message.isDeleted` đều được khai báo `nullable = false` tại
  `Message.java:45-49`.
- Migration cũng khai báo `sent_at` và `is_deleted` là `NOT NULL` và không có default tại
  `V1__baseline_business_schema.sql:1218-1225`.
- `MessageMapper.java:22-24` chủ động bỏ qua hai field này.
- `MessageService.java:58-60` chỉ resolve quan hệ; không gán timestamp hoặc cờ xóa trước khi save.

**Tác động**

`POST /api/messages` đi tới `AbstractBaseService.create()` và insert message với hai giá trị null,
dẫn tới lỗi ràng buộc database.

**Sửa tối thiểu đề xuất**

Trong `beforeCreate`, gán `sentAt = OffsetDateTime.now()` và `isDeleted = false`; thêm integration
test cho endpoint gửi message.

### MOD-003 — P0 — FIXED — Payment có `tenant_id` UUID dư thừa

**Bằng chứng**

- `Payment.java:35-36` ánh xạ `tenantId` với `nullable = false`.
- Schema tại `V1__baseline_business_schema.sql:1268-1272` khai báo `tenant_id uuid NOT NULL`, không
  có default.
- `CreatePaymentRequest` không có `tenantId`; `PaymentMapper` và `PaymentService` cũng không gán
  field này.

**Tác động**

`POST /api/payments` và luồng seed gọi `paymentService.create()` sẽ insert `tenant_id = null` và bị
database từ chối.

**Cách sửa đã áp dụng**

Loại field/cột `tenant_id` khỏi Payment bằng migration V3. Tenant routing của dự án dùng tenant ID
dạng chuỗi và database riêng theo tenant; không có nguồn UUID đáng tin cậy để gán vào cột cũ.

### MOD-015 — P0 — FIXED — Migration tenant V2 vừa rename sai vừa bỏ sót cột audit

**Bằng chứng**

- V2 chạy `ALTER TABLE ... RENAME COLUMN createdAt/updatedAt` trên `messages`, `notifications`,
  `tenant_roles`, `telegram_chats`, `trip_stops` và `trucks`, nhưng các bảng này không khai báo hai
  cột nguồn trong V1.
- V2 còn alter bảng `tenants`, trong khi tenant baseline V1 không tạo bảng này.
- V2 bỏ sót các bảng có cột audit legacy như `terminals`, `maintenance_schedules`, `documents` và
  `driver_licenses`.
- Test đối chiếu hai chiều DDL V1/V2 tái hiện được cả tham chiếu sai lẫn rename bị bỏ sót.

**Tác động**

Flyway dừng ở V2 khi provision database tenant mới; application không thể hoàn tất khởi tạo schema.

**Sửa tối thiểu đề xuất**

Chỉ giữ các lệnh rename mà bảng và cột nguồn thực sự tồn tại trong V1, bổ sung mọi cột audit legacy
bị bỏ sót, đồng thời thêm regression test đối chiếu hai chiều với baseline.

**Lưu ý triển khai**

V2 là versioned migration đã bị chỉnh nội dung. Nếu môi trường nào đã ghi nhận V2, cần kiểm tra
lịch sử Flyway và xử lý checksum có kiểm soát; không tự động `repair` trước khi đối chiếu schema.

### MOD-016 — P0 — FIXED — `DataSeeder` không fresh compile do thiếu import

**Bằng chứng**

- Constructor của `DataSeeder` dùng `ConversationService` và `ConversationRepository` nhưng file
  không import hai kiểu này.
- Lần biên dịch sạch báo hai lỗi `cannot find symbol` tại các tham số constructor tương ứng.

**Tác động**

Build sạch thất bại ở pha `maven-compiler-plugin`; trạng thái target cũ có thể che lỗi này trên máy
đã từng biên dịch.

**Sửa tối thiểu đề xuất**

Thêm đúng hai import từ module messaging và xác minh lại bằng Maven compile/test.

### MOD-004 — P1 — FIXED — Kiểm tra trùng email/mã xe bị vô hiệu khi update

**Bằng chứng**

- `AbstractBaseService.java:71-72` gọi mapper cập nhật entity trước rồi mới gọi `beforeUpdate`.
- Mapper thật cập nhật `Employee.email` và `Truck.number` từ request.
- `EmployeeService.java:75-79` và `TruckService.java:150-154` sau đó so sánh giá trị trên entity với
  chính request. Hai giá trị luôn bằng nhau nên nhánh `existsBy...` không chạy khi đổi email/mã xe.
- `EmployeeServiceTest.java:128-139` dùng mock mapper; mock không mutate entity nên test hiện tại
  không tái hiện hành vi của mapper production.

**Tác động**

API update có thể đi qua validation service với giá trị đã tồn tại và chỉ thất bại muộn bằng lỗi
unique constraint (nếu schema có constraint), tạo response không đúng hợp đồng và khó chẩn đoán.

**Sửa tối thiểu đề xuất**

Giữ giá trị cũ trước khi map, hoặc chuyển duplicate check sang hook chạy trước mapping. Thêm test
dùng mapper thật cho cả Employee và Truck.

### MOD-005 — P1 — FIXED — API Driver thực tế trả về mọi Employee

**Bằng chứng**

- `/api/drivers` gọi `EmployeeService.searchDrivers()` tại `DriverController.java:33-46`.
- `searchDrivers()` gọi `employeeRepository.search(..., roleId = null, ...)` tại
  `EmployeeService.java:60-64`.
- Query tại `EmployeeRepository.java:26` chỉ lọc role khi `roleId` khác null.
- `GET /api/drivers/{id}` gọi thẳng `getById()` và không xác minh employee là driver tại
  `DriverController.java:50-54`.

**Tác động**

Cả danh sách và chi tiết Driver có thể trả dispatcher, manager hoặc employee thuộc role khác.

**Sửa tối thiểu đề xuất**

Thêm query theo normalized role/claim hoặc một thuộc tính driver có hợp đồng rõ ràng; áp dụng cùng
điều kiện cho endpoint chi tiết.

### MOD-006 — P1 — FIXED — Load nhận ID container/terminal nhưng không lưu

**Bằng chứng**

- `CreateLoadRequest.java:25-27` công khai `containerId`, `originTerminalId` và
  `destinationTerminalId`.
- Entity có ba quan hệ tương ứng tại `Load.java:123-133`.
- `LoadMapper` không thể map UUID sang entity quan hệ và implementation sinh ra không gán ba field.
- `LoadService.resolveRelations()` tại `LoadService.java:79-108` chỉ resolve customer, truck và
  dispatcher.

**Tác động**

Client nhận request thành công nhưng ba giá trị intermodal bị bỏ im lặng; dữ liệu Load lưu không
đúng nội dung request.

**Sửa tối thiểu đề xuất**

Thêm repository cho Container/Terminal, resolve đủ ba quan hệ trong create/update, trả các ID trong
`LoadView`, và test round-trip.

### MOD-007 — P1 — FIXED — Tạo Conversation không thể tạo participant

**Bằng chứng**

- `CreateConversationRequest.java:6` không nhận participant IDs.
- `ConversationMapper.java:24-25` bỏ qua collection participants.
- `ConversationService.beforeCreate()` tại `ConversationService.java:57-66` chỉ resolve Load.
- Endpoint list conversation chỉ tìm qua `JOIN c.participants` tại
  `ConversationRepository.java:15-18`.

**Tác động**

Conversation được tạo qua API không có participant nên không xuất hiện trong endpoint danh sách
theo employee. Hiện cũng không có endpoint/service nào thêm participant sau đó.

**Sửa tối thiểu đề xuất**

Định nghĩa participant IDs trong command tạo conversation (ít nhất gồm người tạo), tạo các
`ConversationParticipant` với `joinedAt`/`isMuted`, và kiểm tra membership khi đọc/gửi message.

**Cách sửa đã áp dụng**

Request tạo conversation bắt buộc có tập participant ID không rỗng. Service resolve Employee, dựng
quan hệ hai chiều với `joinedAt` và `isMuted`, view trả participant IDs, và thao tác gửi/đánh dấu đã
đọc đều kiểm tra membership. `ConversationServiceTest` và `MessageServiceTest` bảo vệ luồng này.

### MOD-008 — P1 — FIXED — `PUT /api/loads/{id}` bypass toàn bộ state machine

**Bằng chứng**

- `CreateLoadRequest.java:12` bắt client gửi trực tiếp `status` cho cả create và update.
- `LoadMapper.updateEntity()` map field cùng tên này; implementation sinh ra gọi
  `load.setStatus(req.status())` tại `LoadMapperImpl.java:100`.
- Các timestamp lifecycle được mapper bỏ qua tại `LoadMapper.java:47-50`.
- Trong khi đó các transition có kiểm tra hợp lệ và gán timestamp chỉ nằm ở
  `LoadService.java:116-157`.

**Tác động**

Client có thể đổi trực tiếp từ `draft` sang `delivered`, hoặc tạo Load ở trạng thái bất kỳ, mà không
đi qua validation và không có `dispatchedAt`/`pickedUpAt`/`deliveredAt`. Invariant mà state machine
bảo vệ vì vậy không có hiệu lực trên endpoint update chung.

**Sửa tối thiểu đề xuất**

Tách create/update DTO, không cho update chung map `status`; status chỉ thay đổi qua các command
transition. Create chỉ chấp nhận trạng thái ban đầu hợp lệ.

### MOD-009 — P1 — FIXED — Messaging không cập nhật thứ tự hội thoại và chưa có read workflow

**Bằng chứng**

- Danh sách conversation sort theo `lastMessageAt` tại `ConversationService.java:48-54`.
- Entity có `ConversationParticipant.lastReadAt` và `MessageReadReceipt`, nhưng controller/service
  không có thao tác mark-read hay tạo receipt.
- Endpoint unread phụ thuộc `MessageReadReceipt` tại `MessageRepository.java:22-24`.

**Tác động**

Unread count không có đường chạy để giảm xuống vì không thể ghi receipt/last-read qua application
layer.

**Sửa tối thiểu đề xuất**

Bổ sung command mark-read idempotent để cập nhật participant và/hoặc read receipt, cùng test unread
trước/sau.

**Cách sửa đã áp dụng**

Gửi message cập nhật `conversation.lastMessageAt`. Endpoint mark-read tạo receipt cho các message
chưa đọc, loại message do chính employee gửi, tránh tạo trùng receipt và cập nhật
`ConversationParticipant.lastReadAt`. Query unread mới được Hibernate parse trong regression test.

### MOD-010 — P1 — FIXED — Document mới có read/delete metadata, chưa có upload/download

**Bằng chứng**

- Javadoc của `DocumentMapper.java:12-13` xác định document được tạo qua file upload.
- `DocumentController` chỉ có search, get và delete tại `DocumentController.java:34-67`.
- `DocumentService` không phụ thuộc storage client và truyền mapper create/update bằng `null` tại
  `DocumentService.java:22-25`.
- Không có `MultipartFile`, upload/download hoặc storage implementation nào trong source.
- Delete hiện kế thừa `AbstractBaseService.delete()` nên chỉ xóa row database, không xóa blob.

**Tác động**

Không thể đưa file thật vào hệ thống hoặc tải file qua API. Xóa metadata có thể để lại blob mồ côi
nếu dữ liệu được tạo bởi luồng ngoài/seeder.

**Sửa tối thiểu đề xuất**

Xây storage port + upload/download/delete flow; lưu metadata và blob theo chiến lược rollback rõ
ràng, đồng thời test lỗi giữa chừng để tránh row/blob lệch nhau.

**Cách sửa đã áp dụng**

Đã thêm storage port và filesystem adapter, multipart upload, download có content metadata và delete
cả row lẫn blob. Nếu ghi metadata thất bại sau upload, blob được dọn lại; nếu xóa blob thất bại,
transaction metadata bị rollback. `DocumentServiceTest` kiểm tra lifecycle và cleanup khi save lỗi.

### MOD-011 — P2 — FIXED — Inspection không nhận hoặc trả defects

**Bằng chứng**

- `LoadConditionReport` sở hữu collection `defects` tại `LoadConditionReport.java:84-88`.
- `CreateInspectionRequest` và `InspectionView` không có defects.
- `InspectionMapper.java:23,33` bỏ qua defects khi create/update.
- Không có repository/service/controller riêng cho `ConditionDefect`.

**Tác động**

API inspection chỉ lưu phần header của báo cáo; không thể ghi hoặc đọc các lỗi hư hỏng dù schema và
entity đã có mô hình này.

**Sửa tối thiểu đề xuất**

Thêm defect request/view lồng trong inspection, dựng quan hệ hai chiều và replace/merge collection
trong transaction; thêm test orphan removal.

**Cách sửa đã áp dụng**

Inspection request/view đã có defects lồng nhau. Create/update rebuild collection, gán parent hai
chiều và dựa vào `orphanRemoval` để thay thế defect cũ. `InspectionDefectsTest` kiểm tra create,
update replacement và ownership.

### MOD-012 — P2 — FIXED — Trip lifecycle và stops mới dừng ở entity

**Bằng chứng**

- `Trip` có `dispatchedAt`, `completedAt`, `cancelledAt` và collection `stops` tại
  `Trip.java:56-76`.
- `TripMapper.java:17-19,29-31` bỏ qua ba timestamp.
- `CreateTripRequest.java:7-8` chỉ chứa name, distance, status và truckId; DTO/view không quản lý
  stops.
- `TripController` chỉ có CRUD chung; không có dispatch/complete/cancel hoặc quản lý stop.

**Tác động**

Trip có thể đổi status như chuỗi dữ liệu thô nhưng không có lifecycle command để đảm bảo timestamp
và không thể xây route bằng các TripStop qua API.

**Sửa tối thiểu đề xuất**

Định nghĩa state machine Trip và command riêng; thêm API/service cho stop theo thứ tự, tránh cho
update chung ghi status trực tiếp.

**Cách sửa đã áp dụng**

Đã thêm `TripStatus`, transition dispatch/complete/cancel kèm timestamp, chặn CRUD bypass status,
và quản lý danh sách stop có thứ tự duy nhất cùng quan hệ Load. `TripWorkflowTest` kiểm tra stop,
state transition và timestamp.

### MOD-013 — P2 — FIXED — Notification chỉ có read-side nhưng không có producer

**Bằng chứng**

- `NotificationMapper.java:11-12` nói notification được tạo nội bộ.
- Toàn bộ source không có `new Notification`, `notificationRepository.save(...)` hoặc service tạo
  notification.
- API hiện chỉ list/get/mark-all-read tại `NotificationController.java:34-57`.

**Tác động**

Trong codebase hiện tại không có sự kiện/luồng nghiệp vụ nào sinh notification; module chỉ đọc các
row đã có từ nguồn ngoài. `TelegramChat` cũng chỉ có entity, chưa có application path.

**Sửa tối thiểu đề xuất**

Xác định event tạo notification, viết consumer/service idempotent và test từ event đến API. Nếu
notification là toàn-tenant thay vì theo người nhận, cần ghi rõ contract vì entity hiện không có
recipient.

**Cách sửa đã áp dụng**

Contract được chốt là notification toàn tenant: tenant boundary chính là database đang được route,
do schema không có recipient. Các transition Trip phát `TenantNotificationEvent`; listener đồng bộ
lưu notification mặc định chưa đọc trong cùng transaction. Test producer kiểm tra event được lưu
với `isRead=false` và thời điểm tạo.

### MOD-014 — P2 — FIXED — Driver share không chuẩn hóa scale tiền tệ

**Bằng chứng**

- `Load.calcDriverShare()` trả trực tiếp `deliveryCostAmount.multiply(ratio)` tại `Load.java:339`.
- 5 test business tương ứng thất bại do kết quả scale 4 hoặc 2 không khớp scale 2/0 mong đợi.

**Tác động**

Giá trị số học đúng nhưng representation tiền không ổn định, làm build đỏ và có thể gây so sánh
`BigDecimal.equals`, serialize hoặc ghi sổ không nhất quán.

**Cách sửa đã áp dụng**

Giá trị zero được chuẩn hóa thành `BigDecimal.ZERO`; kết quả khác zero bỏ trailing zero nhưng vẫn
giữ tối thiểu scale của delivery amount. Nếu phép nhân sinh phần lẻ có nghĩa nhỏ hơn cent, các chữ
số đó được giữ nguyên thay vì tự chọn một rounding mode chưa có trong contract.

## Các capability mới dừng ở entity/schema

Đây là **khoảng trống triển khai**, không phải kết luận runtime bug. Tiêu chí đưa vào bảng: capability
có entity/schema nhưng không có controller/service/repository/DTO để thực thi từ application layer.

| Module | Đã có đường chạy | Mới có mô hình dữ liệu, chưa có application path |
|---|---|---|
| `ai` | Không | AI dispatch session/decision |
| `customer` | Customer CRUD | Customer user / customer portal identity |
| `document` | Document upload/download/search/get/delete blob và metadata | Accident report, third party, witness |
| `employee` | Employee CRUD; driver read-side theo claim | Driver license, behavior event, HOS status/log/violation, ELD driver mapping, time entry |
| `finance` | Invoice/Payment CRUD | Invoice line item, payment link; invoice approval/send lifecycle |
| `fleet` | Truck CRUD | Container, terminal, DVIR/defect, maintenance/part/schedule, expense, ELD provider/vehicle mapping, posted truck |
| `inspection` | Inspection CRUD gồm defects | Không thấy capability entity-only khác trong module |
| `load` | Load CRUD + four transitions + intermodal relations | Tracking link, load exception, load-board config/listing |
| `messaging` | Conversation participants, send/list message và mark-read/unread | Không thấy capability entity-only khác trong module |
| `notification` | Tenant notification producer + list/get/mark-all-read | Telegram chat integration |
| `role` | Role + claims CRUD | Không thấy capability entity-only khác trong module |
| `trip` | Trip CRUD, ordered stops và lifecycle commands | Không thấy capability entity-only khác trong module |

Danh sách này dựa trên reference scan toàn bộ `src/main/java/com/company/logicstic/modules`: các entity
nêu trên không được tham chiếu từ application code ngoài package `entity`, hoặc chỉ xuất hiện như
collection/quan hệ JPA mà không có DTO/service quản lý.

## Kết quả kiểm thử hiện tại

Lần chạy full suite mới nhất dùng `mvn -Dspotless.apply.skip=true test`: **112 test, 0 failure,
0 error, 0 skipped**. Wrapper `mvnw` đang bị xóa sẵn trong worktree nên dùng Maven hệ thống;
Spotless apply được tắt để quality gate không tự ghi lại các file ngoài phạm vi.

- Spring Boot context smoke test chạy bằng profile `nodb`, không cần PostgreSQL ngoài.
- `MessagingRepositoryQueryTest` dựng Hibernate metadata và parse các HQL conversation, unread,
  unread-message và driver; test này được bật trong suite mặc định.
- Các regression mới bao phủ conversation participants, mark-read, document blob lifecycle,
  inspection defects, trip lifecycle/stops và notification producer.
- Mapping audit của bản sao `com.company.logicstic.entity.BaseAuditableEntity` đã đồng bộ snake_case.
- Maven Surefire nạp Byte Buddy bằng `-javaagent`, tránh lỗi Mockito self-attach trên JDK 21.

## Khoảng trống kiểm thử (không tính là lỗi chức năng)

- Chưa có test application-path cho module `ai` và các capability entity-only liệt kê phía trên.
- Chưa có controller test bao phủ multipart upload/download và các lifecycle endpoint mới.
- Repository HQL được parse bằng Hibernate metadata nhưng full integration với PostgreSQL/Flyway
  thật vẫn cần môi trường database riêng; suite mặc định chỉ smoke-test context bằng profile `nodb`.

## Ghi chú phạm vi

Working tree đã có nhiều thay đổi chưa commit từ trước. Lượt sửa chỉ nhắm vào các mục đã xác minh và
giữ nguyên các thay đổi không liên quan; cần tách commit cẩn thận khi bàn giao.
