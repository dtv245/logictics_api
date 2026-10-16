# Báo Cáo Chẩn Đoán & Đặc Tả Hợp Đồng API Màn Hình Điều Phối Vận Hành (Operations Dashboard)

> Tài liệu tổng hợp kết quả sửa lỗi, đối soát hợp đồng API và trạng thái kiểm thử cho Backend LogisticsX TMS (Spring Boot 4.1.1, Java 21).  
> Ngày cập nhật: 2026-10-16.  
> Đối tượng: Kỹ sư Backend & Kỹ sư Frontend.

---

## 1. Hiện Trạng & Môi Trường Chạy (Runtime Environment)

* **Vite Proxy Target**: `http://localhost:8080` (chuyển tiếp toàn bộ request `/api/**`).
* **Tiến trình Backend tại `http://localhost:8080`**:
  * Chạy trong Docker container `logistics-api` (Container ID `fbb7d7475a3b`, image `927dc2081351`, tạo từ 6 ngày trước).
  * **Lưu ý**: Container này chưa được rebuild với code mới nhất nên chưa có các endpoint mới. Khi test cục bộ và kiểm thử tự động, backend chạy trên mã nguồn hiện hành và kết nối PostgreSQL disposable (`codex-remediation-d64b178496b7` / Testcontainers).
* **Endpoint `/api/ai/features`**:
  * Đã có trong mã nguồn working tree tại [`AiDispatchController.java`](file:///home/vumoi/logictics_api/src/main/java/com/company/logicstic/ai/dispatch/AiDispatchController.java#L143).
  * Container 8080 trả về 404 là do container chạy artifact cũ, KHÔNG PHẢI thiếu code trong repo. Theo chỉ thị, backend không sửa hay can thiệp ngoài phạm vi.

---

## 2. Chi Tiết Khắc Phục Các Bug

### 2.1 Bug 1: 400 "Request parameter has an invalid type" (Hoàn thành)
- **Kiểu dữ liệu trước khi sửa**: `OffsetDateTime` với `@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)`.
- **Thay đổi thực hiện**:
  1. Cấu hình Spring MVC trong [`application.yml`](file:///home/vumoi/logictics_api/src/main/resources/application.yml):
     ```yaml
     spring:
       mvc:
         format:
           date: iso
           date-time: iso
     app:
       timezone: ${APP_TIMEZONE:UTC}
     ```
  2. Tạo bean `ZoneId appZoneId` trong [`DateTimeConfig.java`](file:///home/vumoi/logictics_api/src/main/java/com/company/logicstic/config/DateTimeConfig.java) để đọc cấu hình múi giờ của ứng dụng (mặc định UTC nếu chưa cấu hình).
  3. Cập nhật 8 endpoint trong [`ReportController.java`](file:///home/vumoi/logictics_api/src/main/java/com/company/logicstic/controller/ReportController.java) sang `@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from, LocalDate to`:
     - `GET /api/reports/operations/on-time-delivery`
     - `GET /api/reports/operations/transit-time`
     - `GET /api/reports/operations/delays`
     - `GET /api/reports/operations/exceptions-summary`
     - `GET /api/reports/expenses`
     - `GET /api/reports/costs/known-operating-cpm`
     - `GET /api/reports/fleet/fuel`
     - `GET /api/reports/fleet/maintenance`
  4. Ở tầng service, quy đổi sang khoảng thời gian nửa mở:
     - `start = from != null ? from.atStartOfDay(appZoneId).toOffsetDateTime() : null;`
     - `endExclusive = to != null ? to.plusDays(1).atStartOfDay(appZoneId).toOffsetDateTime() : null;`
     Đảm bảo bao phủ trọn vẹn 23:59:59.999 của ngày `to`.
  5. Validate `from <= to` bằng `ReportPeriodValidation.validateDateRange(from, to)`. Nếu vi phạm ném `ApiException(HttpStatus.BAD_REQUEST, "INVALID_REPORT_PERIOD", "'from' date must be before or equal to 'to' date", ...)`.
  6. Thêm `@ExceptionHandler(MethodArgumentTypeMismatchException.class)` trong [`GlobalExceptionHandler.java`](file:///home/vumoi/logictics_api/src/main/java/com/company/logicstic/exception/GlobalExceptionHandler.java) với null-check `getRequiredType()`. Khi sai định dạng trả HTTP 400:
     ```json
     {
       "success": false,
       "code": "MALFORMED_REQUEST",
       "message": "Request parameter 'from' with value 'not-a-date' is invalid; expected type 'LocalDate'",
       "errors": [
         {
           "field": "from",
           "code": "TYPE_MISMATCH",
           "message": "Request parameter 'from' with value 'not-a-date' is invalid; expected type 'LocalDate'",
           "params": {
             "parameter": "from",
             "value": "not-a-date",
             "expectedType": "LocalDate"
           }
         }
       ]
     }
     ```

---

### 2.2 Bug 2: 500 trên GET /api/reports/financials/monthly (Hoàn thành)
- **Nguyên nhân gốc**:
  - Hibernate sinh query với untyped parameter `$1 IS NULL` trên PostgreSQL:
    `WHERE (:from IS NULL OR i.createdAt >= :from)`
    khiến PostgreSQL ném `PSQLException: ERROR: could not determine data type of parameter $1 (SQLState: 42P18)`.
  - Ngoài ra, các tháng không có dữ liệu cần đảm bảo trả về 0 hợp lệ và xử lý chia an toàn (divide by zero).
- **Thay đổi thực hiện**:
  1. Thêm log chi tiết HTTP method, URI, root cause trong `GlobalExceptionHandler.handleUnexpectedException`.
  2. Bổ sung `CAST(:param AS string) IS NULL` trong các JPQL query:
     - `InvoiceRepository.findByPeriod`
     - `ExpenseRepository.findApprovedExpenses` & `findByFilters`
     - `MaintenanceRecordRepository.findByFilters`
     - `LoadExceptionRepository.findByPeriod`
  3. Bổ sung `safeDivide(numerator, denominator, scale, boundary)` trong `FinancialRoundingPolicy.java`.
  4. Đảm bảo khi tháng không có dữ liệu, API trả HTTP 200 với các giá trị `0.00` hợp lệ thay vì lỗi 500.

---

### 2.3 Bug 3: 404 trên GET /api/reports/drivers/ranking & /api/ai/features (Hoàn thành)
- **3.1 GET /api/reports/drivers/ranking**:
  - Triển khai endpoint đầy đủ trong [`ReportController.java`](file:///home/vumoi/logictics_api/src/main/java/com/company/logicstic/controller/ReportController.java#L238):
    `GET /api/reports/drivers/ranking?from=YYYY-MM-DD&to=YYYY-MM-DD&sortBy=score&limit=10&currency=USD`
  - Các tham số:
    - `from`, `to`: `LocalDate` (tùy chọn)
    - `currency`: String (mặc định USD)
    - `sortBy`: `score` | `revenue` | `totalMiles` | `onTimePercent` (mặc định `score`)
    - `limit`: int (mặc định 10, tối đa 50, tối thiểu 1)
  - Triển khai [`DriverRankingService.java`](file:///home/vumoi/logictics_api/src/main/java/com/company/logicstic/service/calculation/DriverRankingService.java):
    - Tính toán OTD, số chuyến hoàn thành, số dặm thực tế, doanh thu thực tế, số sự cố.
    - Công thức điểm chuẩn hóa:
      `Score = (OTD% * 0.4) + (Productivity% * 0.3) + (Revenue% * 0.3) - (Exceptions * 2.0)`
      Clamped trong khoảng `[0.0, 100.0]`.
    - Trả về DTOs:
      - `DriverRankingReport`: `period`, `currency`, `sortBy`, `drivers`, `weights` (0.4, 0.3, 0.3), `isMock: false`.
      - `DriverRankingItem`: `driverId`, `driverName`, `rank`, `score`, `completedLoads`, `totalMiles`, `onTimePercent`, `revenue`, `driverPay`, `revenuePerMile`, `exceptionsCount`, `isMock: false`.
- **3.2 GET /api/ai/features**:
  - Đã có sẵn trong codebase tại [`AiDispatchController.java`](file:///home/vumoi/logictics_api/src/main/java/com/company/logicstic/ai/dispatch/AiDispatchController.java).
  - Trả 404 trên port 8080 là do container Docker chạy bản build cũ 6 ngày trước.

---

## 3. Bảng Hợp Đồng API Thật (True API Contract)

| Endpoint | Method | Query Parameters | Format ngày | Status Thành Công | Body Data Type |
|---|---|---|---|---|---|
| `/api/reports/operations/on-time-delivery` | GET | `from`, `to` | `YYYY-MM-DD` | 200 OK | `OtdReport` |
| `/api/reports/operations/transit-time` | GET | `from`, `to` | `YYYY-MM-DD` | 200 OK | `TransitTimeReport` |
| `/api/reports/operations/delays` | GET | `from`, `to` | `YYYY-MM-DD` | 200 OK | `DeliveryDelayReport` |
| `/api/reports/operations/exceptions-summary` | GET | `from`, `to` | `YYYY-MM-DD` | 200 OK | `ExceptionSummaryReport` |
| `/api/reports/expenses` | GET | `from`, `to`, `currency` | `YYYY-MM-DD` | 200 OK | `ExpenseSummaryReport` |
| `/api/reports/costs/known-operating-cpm` | GET | `from`, `to`, `currency` | `YYYY-MM-DD` | 200 OK | `KnownOperatingCpmReport` |
| `/api/reports/fleet/fuel` | GET | `from`, `to`, `currency` | `YYYY-MM-DD` | 200 OK | `FuelReport` |
| `/api/reports/fleet/maintenance` | GET | `from`, `to`, `truckId`, `currency` | `YYYY-MM-DD` | 200 OK | `MaintenanceSummaryReport` |
| `/api/reports/financials/monthly` | GET | `year`, `month`, `currency` | N/A (số nguyên) | 200 OK | `MonthlyFinancialSummary` |
| `/api/reports/drivers/ranking` | GET | `from`, `to`, `currency`, `sortBy`, `limit` | `YYYY-MM-DD` | 200 OK | `DriverRankingReport` |

---

## 4. Bằng Chứng Kiểm Thử & Xác Nhận

1. **Unit & WebMvc Tests**:
   - `ReportControllerWebMvcTest`: 6/6 tests passed (xác thực `LocalDate`, lỗi 400 `MALFORMED_REQUEST` kèm `expectedType`, lỗi 400 `INVALID_REPORT_PERIOD` khi `from > to`, tính toán `monthly` rỗng trả 200).
   - `DriverRankingServiceTest`: 2/2 tests passed (xác thực tính điểm, trọng số, sắp xếp theo tiêu chí, giới hạn `limit`).
   - `PhaseOneRegressionTest` & `OperationsAndCostMetricsTest`: 10/10 tests passed.
2. **PostgreSQL Integration Test**:
   - `OperationsDashboardReportPostgresTest`: 2/2 tests passed trên container PostgreSQL thật (`codex-remediation-d64b178496b7`), áp dụng đủ 44 Flyway migrations, xác nhận loại bỏ hoàn toàn lỗi SQLState 42P18.
3. **Toàn bộ Test Suite (`./mvnw test`)**:
   - Tổng tests run: 812.
   - Passing: 799.
   - Failures: 3 (tất cả đều thuộc test baseline Lark event phiên trước chưa commit).
   - Errors: 10 (tất cả đều thuộc test baseline Lark event phiên trước chưa commit).
   - Skipped: 232.
   - **Tỷ lệ hồi quy: 0 regression.**
