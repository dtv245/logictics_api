# 05 — Database, multi-tenancy và infrastructure

## 1. PostgreSQL credentials, database name và API URL

Các mặc định tại [`docker-compose.yml`](../../docker-compose.yml#L1) **không thống nhất**:

| Cấu hình | Database mặc định | User mặc định | Password mặc định | Host/port |
|---|---|---|---|---|
| PostgreSQL service | `logistics` | `logistics` | `logistics` | Compose network `logistics-postgres:5432`; host port `5433` |
| API `DB_URL` / `SPRING_DATASOURCE_URL` | `us_logisticsx` | — | — | `logistics-postgres:5432` |
| API `DB_USERNAME` / `SPRING_DATASOURCE_USERNAME` | — | `postgres` | `Test12345#` | lấy từ `POSTGRES_PASSWORD` với default khác service |
| `.env.example` | `us_logisticsx` | `postgres` | `change-me` cho `DB_PASSWORD` | `localhost:5433` — phù hợp app chạy trên host, không phải host nội bộ Compose |

Nếu không override, PostgreSQL tạo database/user/password `logistics`/`logistics`/`logistics`, còn API kết nối `us_logisticsx`/`postgres`/`Test12345#`; ba giá trị mặc định không cùng một bộ. Cùng override `POSTGRES_DB` và `POSTGRES_USER` sẽ làm URL/user API dùng chung hai giá trị đó, nhưng password defaults vẫn khác (`logistics` so với `Test12345#`). `.env.example` chỉ khai báo `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, không khai báo `POSTGRES_DB`/`POSTGRES_USER`/`POSTGRES_PASSWORD`; Compose dựng lại API URL và credentials từ các biến `POSTGRES_*`, nên copy file đó không tự đồng bộ container.

Ngoài ra:

- PostgreSQL healthcheck dùng user cố định `logistics`, nên có thể sai khi đổi `POSTGRES_USER`.
- Compose `depends_on` chỉ phụ thuộc container, không yêu cầu service PostgreSQL ở trạng thái healthy.
- API service tham chiếu image `logisticsx-api:local` mà không có `build:` trong Compose; Dockerfile tồn tại riêng, Compose không tự build source từ Dockerfile này.
- Compose đặt `SPRING_FLYWAY_ENABLED` mặc định false trong khi [`application.yml`](../../src/main/resources/application.yml#L12) đặt `ddl-auto: validate`. Trên database rỗng, schema phải được tạo trước bằng một quy trình ngoài Compose nếu không ứng dụng sẽ validate schema chưa có.
- `.env.example` đặt `SPRING_PROFILES_ACTIVE=nodb`; profile đó loại datasource/Flyway autoconfiguration. Dù file có DB_URL, profile nodb không phải cấu hình chạy full persistence.

Kết luận từ source: trạng thái “credentials/database/API URL thống nhất” là **NO** theo default. Cần chốt một nguồn env chuẩn và xác nhận fresh-database bootstrap trước khi gọi Compose là đường chạy TMS.

## 2. Redis

Compose khai báo Redis 7, port/volume/healthcheck và truyền host/port tới API. Nhưng `pom.xml` không có `spring-boot-starter-data-redis`/Spring Data Redis dependency và source không có Redis client/config/use-site tìm thấy. Vì vậy hiện Redis là container hạ tầng được dựng nhưng chưa có bằng chứng backend dùng làm cache, queue hay session store.

## 3. Flyway và schema

- Backend dùng PostgreSQL driver, Flyway core + PostgreSQL database module; location mặc định tenant migration `classpath:db/migration/tenant`.
- Tenant schema có baseline V1 đến V53. Hibernate validate, không tự tạo schema (`ddl-auto: validate`; tenancy mode thêm `hbm2ddl.auto=none`).
- Khi multi-tenancy bật, Boot datasource-level Flyway bị no-op và `TenantMigrationService` migrate từng ACTIVE tenant qua datasource tạm. Migration batch fail thì startup runner throw exception; readiness phụ thuộc toàn bộ batch.
- Registry table `tenant_registry` không có Flyway migration: optional `autoInitialize` tạo nó trực tiếp bằng JDBC ở startup.
- Không chạy/kiểm tra checksum/version của database trong audit; trạng thái migrations đã apply ngoài thực tế **chưa xác minh**.

## 4. Tenant provisioning và routing

Mô hình source là **database-per-tenant**, không phải shared tables có `tenant_id`: JWT claim `tenant` chọn datasource vật lý trong `TenantRoutingDataSource`; tenant DB dùng schema `public`. Registry datasource độc lập giữ tenant ID, DB URL, username, encrypted password, status. Connection pools được tạo khi tenant được resolve và giới hạn mặc định 100 pool.

Các điều kiện để chạy mode này:

1. `TENANCY_ENABLED=true` (mặc định false).
2. `TENANT_REGISTRY_DB_URL`, username/password và encryption key đầy đủ.
3. Tenant claim từ JWT phải có trong registry với status ACTIVE.
4. Mỗi tenant database phải được tạo, migrate đến cùng version; boot migration dừng khi có tenant fail.
5. Provisioning cần admin URL/user có quyền `CREATE DATABASE`, tenant DB credentials, template tùy chọn.

`TenantProvisioningService` là service async, không tìm thấy controller/service façade API cho tenant create/list/status. Registry insertion là insert thuần, không phải upsert; flow tạo DB → migrate → register không có cleanup/transaction xuyên DB nếu bước sau lỗi. Lark callback sử dụng tenant default trong config. Frontend `/select-tenant` nhận danh sách một phần tử từ `/api/me` và xin IdP xác thực lại thay vì gọi tenant directory.

## 5. Khả năng tách `logistics_control` làm Control Plane

**Có thể dùng làm registry database theo cấu hình hiện có, nhưng Control Plane chưa được triển khai như một domain hoàn chỉnh.** `TenantDataSourceConfiguration` nhận registry URL riêng (`TENANT_REGISTRY_DB_URL`), nên về mặt kết nối có thể trỏ vào `logistics_control`; code hiện chỉ quản lý bảng registry nhỏ, không chứa provisioning API, tenant lifecycle/audit, subscription/billing, user/identity mapping hay control-plane migration set.

Để vận hành riêng cần external database/user, grant, backup/restore và cấu hình secret; cần quyết định schema/migration ownership cho registry. Compose hiện chưa khai báo `logistics_control` hoặc registry environment variables. Không tạo hoặc thay đổi database trong audit.

## 6. Thứ tự ưu tiên hạ tầng

1. Chốt PostgreSQL DB/user/credentials và profile vận hành; không dùng `nodb` nếu cần persistence.
2. Chốt cách chạy migration trên môi trường mới: startup Flyway hay deployment step; đảm bảo cùng policy với `ddl-auto: validate`.
3. Nếu chuyển multi-tenant, bật và kiểm tra routing/registry/provisioning trên disposable environment riêng; chưa bật production chỉ bằng cách đặt `TENANCY_ENABLED=true`.
4. Quyết định Redis có vai trò runtime hay bỏ khỏi topology; hiện source chưa cho thấy consumer.
5. Nếu tách control plane, định nghĩa schema/API/lifecycle riêng trước; chỉ đổi registry URL chưa tạo ra control plane.

