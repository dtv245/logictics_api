# 03. Luồng nghiệp vụ được thấy trong source

## 1. Quy ước

Sequence dưới đây dừng ở điểm sâu nhất có bằng chứng trong source. Đường gọi backend được mô tả theo controller/service/repository; phần provider hoặc API ngoài chỉ được vẽ khi có interface/gateway. Đây không phải bằng chứng một request đã chạy trong UAT. Trong source được khảo sát, Load và Trip có field status dạng String; Trip Stop có các transition được kiểm tra rõ. (nguồn: src/main/java/com/company/logicstic/entity/Load.java:56-57; Trip.java:59-60; TripStop.java:46-47; TripExecutionService.java:92-148)

## 2. Flow F-01 — Lark login và bind tenant

### Activity Diagram

    @startuml
    start
    :Người dùng mở /login;
    :Frontend yêu cầu Lark authorize;
    :Lark trả authorization code + state;
    :POST /api/auth/lark/callback;
    if (state hợp lệ, chưa hết hạn?) then (có)
      :Đổi code lấy Lark user;
      :Tìm Employee liên kết;
      if (Tenant đang được cấp và hợp lệ?) then (có)
        :Tạo JWT Bearer có tenant và role;
        :Frontend nạp identity GET /api/me;
        if (app.tenancy.enabled=true) then (có)
          :JWT tenant claim -> TenantContext -> tenant DataSource;
        else (không)
          :Dùng cấu hình single-tenant hiện tại;
        endif
      else (không)
        :Trả lỗi tenant/auth;
      endif
    else (không)
      :Trả lỗi BAD_REQUEST;
    endif
    stop
    @enduml

### Sequence Diagram

    @startuml
    actor "Nhân viên" as User
    participant "React LoginPage/AuthProvider" as UI
    participant "LarkAuthController" as Controller
    participant "LarkAuthService" as Service
    participant "LarkAuthClient" as LarkClient
    participant "EmployeeRepository" as EmployeeRepo
    participant "TenantJwtClaimFilter" as TenantFilter
    participant "TenantRoutingDataSource" as Router

    User -> UI: bắt đầu Lark login
    UI -> Controller: GET /api/auth/lark/authorize
    Controller -> Service: getAuthorizeUrl(returnTo)
    Service --> UI: authorizationUrl
    User -> LarkClient: xác thực tại Lark
    LarkClient --> UI: code + state callback
    UI -> Controller: POST /api/auth/lark/callback
    Controller -> Service: handleCallback(code, state, ...)
    Service -> Service: validateState + TTL/HMAC
    Service -> LarkClient: exchangeCodeForToken + extractUser
    Service -> EmployeeRepo: resolve employee mapping
    Service -> Service: createInternalToken(subject,email,tenant,roles)
    Service --> UI: Bearer token + identity
    UI -> Controller: GET /api/me
    Controller --> UI: CurrentUserResponse
    TenantFilter -> Router: nếu tenancy bật, route bằng TenantContext
    @enduml

Tiền điều kiện cụ thể: Lark integration bật, state còn hạn, có Employee liên kết và cấu hình signing key hợp lệ. Không thấy API backend public để UI tự chọn tenant; tenant ID được đưa vào JWT nội bộ. Cấu hình tenancy ở application.yml mặc định tắt. (nguồn: src/main/java/com/company/logicstic/integration/lark/auth/LarkAuthController.java:17-60; LarkAuthService.java:90-153, 160-220; src/main/java/com/company/logicstic/controller/CurrentUserController.java:24-38; src/main/resources/application.yml:64-82)

## 3. Flow F-02 — Tạo Load và đọc timeline

### Activity Diagram

    @startuml
    start
    :Dispatcher mở /loads/create;
    :Nhập Load fields;
    :POST /api/loads;
    if (JWT/role/validation hợp lệ?) then (có)
      :LoadService tạo hoặc cập nhật entity;
      :LoadRepository lưu Load;
      :Trả ApiResponse<LoadView>;
      :Mở /loads/show/:id;
      :GET /api/loads/{loadId}/timeline;
      :Hiển thị các LoadEvent đã lưu;
    else (không)
      :Trả lỗi validation/auth/domain;
    endif
    stop
    @enduml

### Sequence Diagram

    @startuml
    actor Dispatcher
    participant "Load Create / Detail UI" as UI
    participant "Refine dataProvider / apiClient" as Provider
    participant "LoadController" as Controller
    participant "LoadService" as Service
    participant "LoadRepository" as Repository
    database "Tenant PostgreSQL" as DB
    participant "LoadTimelineController" as TimelineController
    participant "LoadTimelineService" as TimelineService

    Dispatcher -> UI: nhập thông tin Load
    UI -> Provider: create(loads, request)
    Provider -> Controller: POST /api/loads
    Controller -> Controller: @Valid + ApiResponse
    Controller -> Service: create(CreateLoadRequest)
    Service -> Repository: save(entity)
    Repository -> DB: INSERT/UPDATE
    DB --> Repository: persisted row
    Service --> Controller: LoadView
    Controller --> UI: 201 + ApiResponse
    Dispatcher -> UI: mở Load detail / timeline
    UI -> TimelineController: GET /api/loads/{loadId}/timeline
    TimelineController -> TimelineService: getTimeline(loadId)
    TimelineService -> DB: đọc LoadEvent/Load liên quan
    DB --> TimelineService: event records
    TimelineService --> UI: LoadTimelineResponse
    @enduml

Load status trong entity là text field; controller CRUD cho phép tạo/cập nhật Load nhưng không chứng minh một state machine đầy đủ như roadmap. API timeline có thật; không tìm thấy public tracking token endpoints nêu trong roadmap. (nguồn: src/main/java/com/company/logicstic/controller/LoadController.java:28-82; service/LoadService.java:21-149; controller/LoadTimelineController.java:19; src/main/java/com/company/logicstic/entity/Load.java:56-57; docs/plan.md:41-75)

## 4. Flow F-03 — Gán Driver và thực thi Trip Stop

### Activity Diagram

    @startuml
    start
    :Mở Trip detail;
    :Đọc assignment và danh sách Stop;
    :POST assign Driver nếu cần;
    :POST arrive;
    if (status là PENDING hoặc EN_ROUTE?) then (có)
      :Status = ARRIVED;
      :POST start-service;
      if (status là ARRIVED?) then (có)
        :Status = SERVICE_STARTED;
        :POST complete-service;
        if (status là SERVICE_STARTED?) then (có)
          :Status = SERVICE_COMPLETED;
          :POST depart;
          :Status = DEPARTED;
        else (không)
          :Lỗi transition;
        endif
      else (không)
        :Lỗi transition;
      endif
    else (không)
      :Lỗi transition;
    endif
    stop
    @enduml

### Sequence Diagram

    @startuml
    actor Dispatcher
    participant "Trip Detail UI" as UI
    participant "TripExecution API adapter" as Adapter
    participant "TripExecutionController" as Controller
    participant "TripExecutionService" as Service
    participant "TripStopRepository / AssignmentRepository" as Repository
    database "Tenant PostgreSQL" as DB

    Dispatcher -> UI: mở Trip
    UI -> Adapter: get drivers + stops
    Adapter -> Controller: GET /api/trips/{id}/drivers, /stops
    Controller -> Service: getTripDrivers/getTripStops
    Service -> Repository: findByTripId...
    Repository -> DB: SELECT
    DB --> UI: assignment + stop views
    Dispatcher -> UI: arrive / start / complete / depart
    UI -> Controller: POST /api/trip-stops/{id}/{action}
    Controller -> Service: transition method
    Service -> Service: requireStatus(current, allowed, target)
    Service -> Repository: save(stop)
    Repository -> DB: UPDATE trip_stops
    DB --> UI: new status + timestamps
    @enduml

### Trạng thái Stop

| Trạng thái hiện tại | Lệnh | Trạng thái tiếp theo | Điều kiện được kiểm tra |
|---|---|---|---|
| PENDING hoặc EN_ROUTE | arrive | ARRIVED | Chỉ chấp nhận một trong hai trạng thái nguồn. |
| ARRIVED | start-service | SERVICE_STARTED | Phải đã ghi nhận arrival. |
| SERVICE_STARTED | complete-service | SERVICE_COMPLETED | Phải đang phục vụ Stop. |
| SERVICE_COMPLETED | depart | DEPARTED | Phải hoàn tất service trước khi rời điểm. |

Nếu trạng thái hiện tại không nằm trong allowlist, service trả BadRequestException và không lưu trạng thái mới. Trip/Load status fields không có transition guard tương tự trong CRUD controller được khảo sát; không lấy danh sách trạng thái từ docs/plan làm trạng thái đang vận hành. (nguồn: src/main/java/com/company/logicstic/controller/TripExecutionController.java:26-103; TripExecutionService.java:92-148; entity/TripStop.java:46-47; entity/Trip.java:59-60)

## 5. Flow F-04 — AI Dispatch, đề xuất và duyệt

### Activity Diagram

    @startuml
    start
    :Dispatcher chọn Load và mode;
    :POST /api/ai/dispatch/runs;
    if (AI dispatch bật và request hợp lệ?) then (có)
      :Tạo session;
      :Lấy và chấm candidate;
      if (LLM/provider sẵn sàng?) then (có)
        :LLM rerank / giải thích;
      else (không)
        :Dùng optimization fallback nếu có;
      endif
      :Lưu decision Suggested và session;
      :UI tải pending/session;
      if (Người dùng approve?) then (approve)
        :Revalidate Load/Truck feasibility;
        if (vẫn khả thi?) then (có)
          :Decision Executed;
        else (không)
          :Trả FEASIBILITY_FAILED;
        endif
      else (reject)
        :Decision Rejected;
      endif
    else (không)
      :Trả disabled/validation/provider error;
    endif
    stop
    @enduml

### Sequence Diagram

    @startuml
    actor Dispatcher
    participant "AI Dispatch UI" as UI
    participant "ai.api.ts / hooks" as Hooks
    participant "AiDispatchController" as Controller
    participant "AiDispatchService" as Service
    participant "Optimization scoring / LLM client" as Candidate
    participant "AiDispatch repositories" as Repositories
    database "Tenant PostgreSQL" as DB

    Dispatcher -> UI: chạy đề xuất
    UI -> Hooks: run dispatch
    Hooks -> Controller: POST /api/ai/dispatch/runs
    Controller -> Service: runDispatch(request, actor, tenant)
    Service -> Candidate: score/rank candidate; tùy cấu hình rerank LLM
    Candidate --> Service: ranked result hoặc fallback
    Service -> Repositories: persist session, decisions, run audit
    Repositories -> DB: INSERT
    Service --> UI: run/session result
    Dispatcher -> UI: approve/reject suggestion
    UI -> Controller: POST decision approve/reject
    Controller -> Service: approveDecision/rejectDecision
    Service -> DB: revalidate source state và cập nhật decision
    DB --> UI: executed/rejected hoặc conflict
    @enduml

Service từ chối nếu decision không còn pending và khi approve sẽ kiểm tra lại feasibility. Từ run hiện có, decision status được ghi Suggested/Executed/Rejected/Failed, session kết thúc COMPLETED hoặc CANCELLED. AI gap là một luồng riêng, không đồng nghĩa mọi Load đều có AI recommendation. (nguồn: src/main/java/com/company/logicstic/ai/dispatch/AiDispatchService.java:55-195, 221-300; ai/dispatch/AiDispatchController.java:37-160; /home/vumoi/logictics-app/src/api/ai/ai.api.ts:30-56, 237-276)

## 6. Flow F-05 — Optimization và accept candidate

### Activity Diagram

    @startuml
    start
    :Dispatcher chọn policy, Load/Trip, Driver/Truck và input sources;
    :POST /api/optimization/runs;
    :Resolve database facts + qualified external evidence;
    :Kiểm tra tenant, freshness, source quality, HOS và feasibility;
    if (Candidate đủ evidence và feasible?) then (có)
      :Tính score và lưu run/candidate audit;
      :Hiển thị comparison;
      if (Người dùng accept?) then (có)
        :Đọc lại state + fingerprint;
        if (Không stale và chưa accepted candidate khác?) then (có)
          :Ghi assignment và idempotency command;
        else (không)
          :Trả conflict/stale;
        endif
      else (không)
        :Giữ run để tham khảo;
      endif
    else (không)
      :Lưu rejection reason / unavailable;
    endif
    stop
    @enduml

### Sequence Diagram

    @startuml
    actor Dispatcher
    participant "Optimization UI / hooks" as UI
    participant "OptimizationRunController" as Controller
    participant "OptimizationApplicationService" as AppService
    participant "OptimizationInputProvider" as Provider
    participant "HardFeasibility + Scoring" as Rules
    participant "Optimization audit repositories" as Repository
    participant "OptimizationAcceptanceService" as Accept
    database "Tenant PostgreSQL" as DB

    Dispatcher -> UI: chạy optimization
    UI -> Controller: POST /api/optimization/runs
    Controller -> AppService: run(request, actor)
    AppService -> DB: đọc Load/Trip/Driver/Truck/policy
    AppService -> Provider: fetch route/HOS/availability evidence
    Provider --> AppService: qualified input hoặc lỗi
    AppService -> Rules: hard feasibility rồi score
    AppService -> Repository: persist immutable run/candidate/audit
    Repository -> DB: INSERT
    AppService --> UI: ranked candidates + reasons
    Dispatcher -> UI: accept candidate
    UI -> Controller: POST /runs/{id}/assignments/{candidateId}/accept
    Controller -> Accept: accept(request, actor)
    Accept -> DB: lock/re-read state + fingerprint + feasibility
    Accept -> Repository: write assignment + idempotency command
    Repository --> UI: accepted assignment hoặc conflict
    @enduml

Acceptance không chỉ dùng score cũ: service kiểm tra candidate feasible, tenant scope, fingerprint, trạng thái mới nhất, conflict assignment và idempotency key trước khi ghi. Provider endpoint/credentials thực tế chưa được xác minh. (nguồn: src/main/java/com/company/logicstic/service/optimization/OptimizationApplicationService.java:30-92, 112-145; OptimizationAcceptanceService.java:34-84; src/main/java/com/company/logicstic/integration/optimization/OptimizationInputProvider.java:7-8)

## 7. Flow F-06 — Lark Base sync / event inbox

### Activity Diagram

    @startuml
    start
    if (Có yêu cầu sync Load/Driver?) then (sync)
      :POST /api/lark/base/.../sync;
      :Tạo projection từ entity và mapping;
      :Tìm remote record;
      if (Mapping/ownership hợp lệ?) then (có)
        :Create/update qua Lark gateway;
        :Persist link/hash/audit state;
        if (Provider trả lỗi?) then (có)
          :State ERROR / CONFLICT / MISSING;
        else (không)
          :State SYNCED;
        endif
      else (không)
        :State CONFLICT hoặc trả mapping error;
      endif
    else (event)
      :POST /api/lark/events;
      :Verify signature + timestamp;
      :Deduplicate và persist inbox receipt;
      :Dispatch handler, retry/lease theo cấu hình;
    endif
    stop
    @enduml

### Sequence Diagram

    @startuml
    actor "ADMIN/DISPATCHER hoặc Lark" as Actor
    participant "Lark Base API Controller" as Controller
    participant "LarkSyncService" as Sync
    participant "LarkSyncMapping/Store" as Store
    participant "LarkSyncGateway" as Gateway
    participant "Lark Base API" as External
    participant "LarkEventSecurity/Dispatcher" as EventDispatcher
    database "Tenant PostgreSQL" as DB

    Actor -> Controller: POST sync Load/Driver
    Controller -> Sync: sync(domain, id, recreateMissing)
    Sync -> DB: đọc source entity + persisted link
    Sync -> Store: prepare / update state and audit
    Sync -> Gateway: find/create/update remote record
    Gateway -> External: HTTPS request
    External --> Gateway: remote response/error
    Gateway --> Sync: record or provider error
    Sync -> Store: persist state/hash/audit
    Store -> DB: transaction commit
    Actor -> Controller: POST /api/lark/events (webhook path)
    Controller -> EventDispatcher: authenticate raw body + accept event
    EventDispatcher -> DB: deduplicate/enqueue, claim, complete/retry
    @enduml

Event callback được cấu hình permitAll ở Spring matcher nhưng xác thực chữ ký/timestamp thực hiện trong LarkEventSecurity; không ghi lại provider success giả. Các sync/event class mới đang ở working tree chưa commit. (nguồn: src/main/java/com/company/logicstic/integration/lark/base/LarkSyncService.java:14-118; LarkEventController.java:15-49; LarkEventDispatcher.java:46-115; SecurityConfig.java:44-55; git status ngày 20-10-2026)

## 8. Bảng state machine và giới hạn

| Thực thể | State/transition được chứng minh | Điều kiện |
|---|---|---|
| Load | Có cột status kiểu text | CRUD có; state transition matrix KHÔNG TÌM THẤY TRONG CODE trong LoadService/controller đã khảo sát. Không dùng status list trong docs/plan làm bằng chứng runtime. |
| Trip | Có cột status kiểu text | Trip CRUD có; state transition matrix KHÔNG TÌM THẤY TRONG CODE trong TripService/controller đã khảo sát. |
| TripStop | PENDING/EN_ROUTE → ARRIVED → SERVICE_STARTED → SERVICE_COMPLETED → DEPARTED | Mỗi action kiểm tra current status trong allowlist trước khi lưu. |
| AiDispatchSession | COMPLETED sau run; CANCELLED khi cancel | Source cũng lưu progress states; trạng thái đầy đủ cần đối chiếu toàn bộ service nếu đưa vào biểu đồ chi tiết hơn. |
| AiDispatchDecision | Suggested → Executed khi approve hợp lệ; Rejected; Failed nếu revalidation thất bại | Chỉ decision pending mới được xử lý; approve revalidates Load/Truck. |
| Optimization candidate/acceptance | Feasible candidate mới được accept; stale/infeasible/conflict bị từ chối | Freshness fingerprint, tenant scope, idempotency và assignment conflict được kiểm tra. |
| Lark Base sync link | PENDING / SYNCED / ERROR / CONFLICT / MISSING | Mapping, remote ownership và provider result ảnh hưởng trạng thái. |
| Driver Settlement | CALCULATED → IN_REVIEW → APPROVED → LOCKED; có VALIDATION_REQUIRED nhánh cần giải quyết | Approval/lock có guard nguồn doanh thu và fresh calculation. |
| Payroll Run | CALCULATED → IN_REVIEW → APPROVED → LOCKED; sau payment có PAYMENT_SCHEDULED/COMPLETED theo luồng khác | Workflow service chỉ cho phép target hợp lệ; không gộp state settlement với payroll. |

(nguồn: src/main/java/com/company/logicstic/entity/Load.java:56-57; Trip.java:59-60; TripStop.java:46-47; TripExecutionService.java:92-148; ai/dispatch/AiDispatchService.java:141-167, 221-300; service/optimization/OptimizationAcceptanceService.java:34-84; integration/lark/base/LarkSyncService.java:50-118; service/payroll/DriverPayEngine.java:220-252; PayrollWorkflowService.java:22-39; docs/plan.md:13-16)

