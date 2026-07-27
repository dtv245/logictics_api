# Logicstic Backend

Backend Java sử dụng Spring Boot, Spring Data JPA và PostgreSQL. Source code chính dùng package gốc `com.company.logicstic`.

Quy ước phát triển được chuẩn hóa trong
[engineering-conventions.md](docs/docs/development/engineering-conventions.md).

## Công nghệ

- Java 21
- Spring Boot 4.1.0
- Spring Web MVC
- Spring Data JPA / Hibernate
- PostgreSQL
- Jakarta Validation
- Lombok
- Maven Wrapper

## Cấu trúc dự án

```text
logicstic/
├── docs/
│   └── docs/
│       ├── architecture/
│       │   ├── database-schema.sql
│       │   └── entity-relationships.md
│       └── development/
│           └── engineering-conventions.md
├── pom.xml
├── README.md
├── scripts/
│   └── generate_entities.py
├── src/
│   ├── main/java/com/company/logicstic/
│   │   ├── modules/
│   │   └── shared/
│   └── test/java/com/company/logicstic/
├── mvnw
└── mvnw.cmd
```

`target/` do Maven tự sinh và không nên commit vào Git.

## Vai trò các package

| Package | Trách nhiệm |
|---|---|
| `config` | Cấu hình Spring, Security, CORS, OpenAPI hoặc bean dùng chung |
| `controller` | Khai báo REST API và xử lý HTTP request/response |
| `dto` | Request/response model, không expose trực tiếp JPA entity |
| `entity` | Ánh xạ bảng PostgreSQL bằng JPA/Hibernate |
| `exception` | Custom exception và global exception handler |
| `mapper` | Chuyển đổi giữa entity và DTO |
| `repository` | Truy cập dữ liệu qua `JpaRepository` |
| `service` | Business logic và transaction boundary |

Luồng xử lý thông thường:

```text
HTTP Request → Controller → Service → Repository → PostgreSQL
                    ↓
                 DTO/Mapper
```

## Entity và database

Mô hình dữ liệu gốc được mô tả trong
[database-schema.sql](docs/docs/architecture/database-schema.sql):

- UUID primary key dùng Hibernate `@UuidGenerator`.
- Quan hệ foreign key dùng `FetchType.LAZY`.
- Hibernate chỉ kiểm tra schema bằng `ddl-auto: validate`, không tự tạo hoặc thay đổi bảng.
- Chi tiết quan hệ nằm trong
  [entity-relationships.md](docs/docs/architecture/entity-relationships.md).

Sinh lại entity sau khi thay đổi schema:

```bash
python3 scripts/generate_entities.py
./mvnw test
```

## Cấu hình PostgreSQL

Các biến môi trường được hỗ trợ:

| Biến | Ví dụ local |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5433/us_logisticsx` |
| `DB_USERNAME` | `postgres` |
| `DB_PASSWORD` | `change-me` |
| `SERVER_PORT` | `8080` |
| `SPRING_PROFILES_ACTIVE` | `nodb` để chạy không cần database |

Tạo cấu hình local từ file mẫu:

```bash
cp .env.example .env
```

`.env` đã được ignore và không nên commit mật khẩu database thật vào source code.
Ứng dụng tự động nạp file này khi chạy từ thư mục gốc của project; biến môi
trường của hệ điều hành vẫn có độ ưu tiên cao hơn giá trị trong `.env`.

## Chạy ứng dụng

Linux/macOS:

```bash
./mvnw spring-boot:run
```

File `.env` mặc định bật profile `nodb`, vì vậy ứng dụng có thể khởi động để
kiểm tra Web/Tomcat mà không kết nối PostgreSQL. Để chạy đầy đủ với database,
đổi hoặc bỏ `SPRING_PROFILES_ACTIVE=nodb` và cung cấp đúng ba biến `DB_*`.

Windows:

```bat
set SPRING_PROFILES_ACTIVE=nodb
set SERVER_PORT=8080
mvnw.cmd spring-boot:run
```

Ứng dụng mặc định chạy tại:

```text
http://localhost:8080
```

## Build và test

Chạy test:

```bash
./mvnw test
```

Build JAR:

```bash
./mvnw clean package
```

Chạy JAR:

```bash
java -jar target/logicstic-1.0.0.jar
```

`EntityMappingTests` kiểm tra số lượng entity, UUID strategy, mapping field và Hibernate metadata mà không cần kết nối database thật.
