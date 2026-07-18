# Plan triển khai JPA Entity từ `sql.md`

## 1. Mục tiêu

Chuyển toàn bộ schema PostgreSQL trong `sql.md` thành JPA Entity:

- Package đích: `com.company.logicstic.entity`.
- Một entity trên một file.
- Bỏ qua bảng `__EFMigrationsHistory`.
- Thay thế entity cũ tại `com.example.logicstic.entity`.
- Kết quả phải biên dịch và khởi động được với Spring Boot/JPA.

## 2. Quy tắc ánh xạ

### Class và Lombok

Mỗi entity phải có:

```java
@Entity
@Table(name = "table_name")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EntityName {
}
```

Không sử dụng:

- `@Data`.
- `@EqualsAndHashCode`.
- `@ToString`.
- Logic nghiệp vụ trong entity.

### Kiểu dữ liệu

| PostgreSQL | Java |
|---|---|
| `uuid` | `UUID` |
| `int4` | `Integer` |
| `int8` | `Long` |
| `numeric` | `BigDecimal` |
| `float8` | `Double` |
| `bool` | `Boolean` |
| `varchar`, `text` | `String` |
| `timestamptz` | `OffsetDateTime` |
| `interval` | `Duration` |

DDL không khai báo PostgreSQL enum, vì vậy các cột trạng thái dạng `text` giữ kiểu `String`.

### UUID primary key

Các bảng dùng UUID nhưng không có database default. Mapping:

```java
@Id
@GeneratedValue
@UuidGenerator
@Column(name = "id", nullable = false, updatable = false)
private UUID id;
```

### Identity không phải primary key

Các cột `number GENERATED ALWAYS AS IDENTITY` không được dùng `@GeneratedValue`, vì annotation này chỉ dành cho identifier:

```java
@Column(
    name = "number",
    nullable = false,
    insertable = false,
    updatable = false,
    unique = true
)
private Long number;
```

### Cột thông thường

Mọi field cơ bản phải ghi rõ tên cột:

```java
@Column(name = "external_account_id", length = 100)
private String externalAccountId;
```

Cột `NOT NULL` phải có `nullable = false`.

Với `numeric(p, s)`:

```java
@Column(name = "amount", nullable = false, precision = 18, scale = 2)
private BigDecimal amount;
```

### Audit

Tạo `BaseAuditableEntity` dưới dạng `@MappedSuperclass` cho các bảng có:

- `"CreatedAt"`.
- `"CreatedBy"`.
- `"LastModifiedAt"`.
- `"LastModifiedBy"`.

Mapping:

```java
@CreationTimestamp
@Column(name = "\"CreatedAt\"", nullable = false, updatable = false)
private OffsetDateTime createdAt;

@Column(name = "\"CreatedBy\"", length = 50)
private String createdBy;

@UpdateTimestamp
@Column(name = "\"LastModifiedAt\"")
private OffsetDateTime lastModifiedAt;

@Column(name = "\"LastModifiedBy\"", length = 50)
private String lastModifiedBy;
```

Các cột lowercase như `created_at` cũng dùng `@CreationTimestamp` khi mang ý nghĩa thời điểm tạo.

## 3. Mapping quan hệ

### Foreign key thông thường

Thay field UUID bằng association:

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "truck_id")
private Truck truck;
```

Foreign key `NOT NULL`:

```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "customer_id", nullable = false)
private Customer customer;
```

### Foreign key unique

Nếu foreign key có unique index, dùng `@OneToOne(fetch = FetchType.LAZY)`. Ví dụ `driver_hos_statuses.employee_id`:

```java
@OneToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "employee_id", nullable = false, unique = true)
private Employee employee;
```

### Hướng quan hệ

- Chỉ map phía giữ foreign key.
- Không tự động thêm `@OneToMany`.
- Không thêm `CascadeType.REMOVE` hoặc `CascadeType.ALL`.
- Các hành vi `ON DELETE CASCADE`, `SET NULL`, `RESTRICT` tiếp tục do PostgreSQL thực thi.
- Tất cả association dùng `LAZY` để tránh tải graph lớn và vòng serialize.
- UUID không có foreign key thật, ví dụ `tenant_id`, `created_by_user_id`, vẫn giữ kiểu `UUID`.

## 4. Unique constraint và index

Unique một cột có thể dùng:

```java
@Column(name = "token", nullable = false, length = 128, unique = true)
```

Unique nhiều cột phải khai báo ở `@Table`:

```java
@Table(
    name = "eld_driver_mappings",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "ix_eld_driver_mappings_provider_type_employee_id",
            columnNames = {"provider_type", "employee_id"}
        )
    }
)
```

Các index không unique quan trọng được giữ bằng `@Index`.

## 5. Cấu trúc file

Tạo:

```text
src/main/java/com/company/logicstic/entity/
├── BaseAuditableEntity.java
├── AccidentReport.java
├── AccidentThirdParty.java
├── AccidentWitness.java
├── AiDispatchDecision.java
├── AiDispatchSession.java
├── ApiKey.java
├── ...
├── Trip.java
├── TripStop.java
└── Truck.java
```

Tổng cộng:

- 50 `@Entity`.
- 1 `@MappedSuperclass`.
- Không tạo entity cho `__EFMigrationsHistory`.

Sau khi bộ mới hoàn thành và compile thành công, xóa bộ cũ:

```text
src/main/java/com/example/logicstic/entity/
```

## 6. Cấu hình entity scanning

Do package entity nằm ngoài package của `LogicsticApplication`, cập nhật main class:

```java
@SpringBootApplication
@EntityScan(basePackages = "com.company.logicstic.entity")
@EnableJpaRepositories(basePackages = "com.example.logicstic.repository")
public class LogicsticApplication {
}
```

Chỉ thêm `@EnableJpaRepositories` nếu repository vẫn nằm dưới `com.example.logicstic.repository`.

## 7. Trình tự thực hiện

1. Parse toàn bộ `CREATE TABLE`, primary key, foreign key, unique index và index trong `sql.md`.
2. Lập danh sách 50 bảng nghiệp vụ và dependency giữa chúng.
3. Tạo `BaseAuditableEntity`.
4. Sinh các entity không có foreign key trước.
5. Sinh entity phụ thuộc theo thứ tự quan hệ.
6. Thêm đầy đủ `@Column(name = "...")`.
7. Thêm association cho 76 foreign key.
8. Thêm unique constraints và index.
9. Thêm `@EntityScan` vào application.
10. Compile bộ entity mới.
11. Chỉ xóa entity cũ sau khi bộ mới compile thành công.
12. Chạy test và Hibernate schema validation.

## 8. Kiểm thử

Chạy:

```bash
./mvnw -DskipTests compile
./mvnw test
```

Khi PostgreSQL đã chứa schema:

```bash
export DB_URL=jdbc:postgresql://localhost:5432/logicstic
export DB_USERNAME=postgres
export DB_PASSWORD=your_password

./mvnw spring-boot:run
```

Giữ cấu hình:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

## 9. Tiêu chí hoàn thành

- Có chính xác 50 class `@Entity`.
- Không map `__EFMigrationsHistory`.
- Mọi field cơ bản đều có `@Column(name = "...")`.
- Mọi foreign key đều có association LAZY phù hợp.
- UUID primary key sử dụng `@UuidGenerator`.
- Không dùng `@GeneratedValue` cho các cột identity phụ `number`.
- Mọi entity có đủ bốn annotation Lombok yêu cầu.
- Không có hai entity cùng map một bảng.
- Maven compile và test thành công.
- Hibernate `ddl-auto: validate` thành công khi kết nối database thật.
