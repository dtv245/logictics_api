package com.company.logicstic.devtools.seed;

import com.company.logicstic.customer.CreateCustomerRequest;
import com.company.logicstic.customer.CustomerRepository;
import com.company.logicstic.customer.CustomerResponse;
import com.company.logicstic.customer.CustomerService;
import com.company.logicstic.document.Document;
import com.company.logicstic.document.DocumentRepository;
import com.company.logicstic.employee.employee.CreateEmployeeRequest;
import com.company.logicstic.employee.employee.Employee;
import com.company.logicstic.employee.employee.EmployeeRepository;
import com.company.logicstic.employee.employee.EmployeeResponse;
import com.company.logicstic.employee.employee.EmployeeService;
import com.company.logicstic.finance.invoice.CreateInvoiceRequest;
import com.company.logicstic.finance.invoice.InvoiceResponse;
import com.company.logicstic.finance.invoice.InvoiceService;
import com.company.logicstic.finance.payment.CreatePaymentRequest;
import com.company.logicstic.finance.payment.PaymentService;
import com.company.logicstic.fleet.maintenance.MaintenanceRecord;
import com.company.logicstic.fleet.maintenance.MaintenanceSchedule;
import com.company.logicstic.fleet.truck.CreateTruckRequest;
import com.company.logicstic.fleet.truck.Expense;
import com.company.logicstic.fleet.truck.Truck;
import com.company.logicstic.fleet.truck.TruckRepository;
import com.company.logicstic.fleet.truck.TruckResponse;
import com.company.logicstic.fleet.truck.TruckService;
import com.company.logicstic.fleet.truck.VehicleMileageReading;
import com.company.logicstic.inspection.CreateInspectionRequest;
import com.company.logicstic.inspection.InspectionService;
import com.company.logicstic.load.core.CreateLoadRequest;
import com.company.logicstic.load.core.Load;
import com.company.logicstic.load.core.LoadRepository;
import com.company.logicstic.load.core.LoadResponse;
import com.company.logicstic.load.core.LoadService;
import com.company.logicstic.load.core.LoadStatus;
import com.company.logicstic.messaging.conversation.Conversation;
import com.company.logicstic.messaging.conversation.ConversationParticipant;
import com.company.logicstic.messaging.conversation.ConversationRepository;
import com.company.logicstic.messaging.conversation.ConversationService;
import com.company.logicstic.messaging.message.MessageService;
import com.company.logicstic.messaging.message.SendMessageRequest;
import com.company.logicstic.role.CreateRoleRequest;
import com.company.logicstic.role.RoleResponse;
import com.company.logicstic.role.RoleService;
import com.company.logicstic.role.TenantRoleRepository;
import com.company.logicstic.terminal.Terminal;
import com.company.logicstic.trip.CreateTripRequest;
import com.company.logicstic.trip.TripRepository;
import com.company.logicstic.trip.TripService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.datafaker.Faker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile({"dev", "seed"})
public class DataSeeder implements CommandLineRunner {

  // ===============================================================================================
  // Constants
  // ===============================================================================================

  private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
  private static final Faker FAKER = new Faker(new java.util.Locale("vi"));
  private static final ZoneOffset VIETNAM_ZONE_OFFSET = ZoneOffset.ofHours(7);

  // --- General Constants ---
  private static final String VND_CURRENCY = "VND";
  private static final String COUNTRY_CODE_VN = "VN";
  private static final String COUNTRY_NAME_VIETNAM = "Việt Nam";
  private static final String ACTIVE_STATUS = "Active";
  private static final BigDecimal TAX_RATE_TEN_PERCENT = new BigDecimal("0.10");

  // --- Magic Numbers ---
  private static final int TERMINALS_PER_TENANT = 3;
  private static final int ADMINS_PER_TENANT = 2;
  private static final int DISPATCHERS_PER_TENANT = 4;
  private static final int DRIVERS_PER_TENANT = 5;
  private static final int ACCOUNTANTS_PER_TENANT = 2;
  private static final int CUSTOMERS_PER_TENANT = 10;
  private static final int TRUCKS_PER_TENANT = 5;
  private static final int LOADS_PER_TENANT = 12;

  // --- Employee Constants ---
  private static final int MIN_HIRE_DAYS_AGO = 30;
  private static final int MAX_HIRE_DAYS_AGO = 730;
  private static final long MIN_SALARY_FIXED = 8_000_000;
  private static final long MAX_SALARY_FIXED = 25_000_000;
  private static final double MIN_SALARY_SHARE = 0.15;
  private static final double MAX_SALARY_SHARE = 0.35;

  // --- Truck Constants ---
  private static final int MIN_TRUCK_YEAR = 2018;
  private static final int MAX_TRUCK_YEAR = 2024;
  private static final int BASE_TRUCK_CAPACITY = 15000;
  private static final int MIN_CAPACITY_ADJUSTMENT = -3000;
  private static final int MAX_CAPACITY_ADJUSTMENT = 10000;

  // --- Load Constants ---
  private static final long MIN_LOAD_COST = 5_000_000;
  private static final long MAX_LOAD_COST = 50_000_000;

  // --- Fleet Cost & Health Fixture Constants ---
  // Toàn bộ dữ liệu dưới đây là fixture tổng hợp phục vụ màn hình Executive Overview, KHÔNG phải
  // giao dịch thật. Trước khi có chúng, `expenses` và `maintenance_records` rỗng ở mọi môi trường
  // nên các chỉ số chi phí trả về unavailable — đúng, nhưng không kiểm chứng được phép tính nào.
  // Ngày tháng trải khắp cửa sổ báo cáo mặc định (12 tháng) để endpoint tổng hợp thật sự có gì đó
  // để cộng theo từng tháng.
  private static final int COST_FIXTURE_WINDOW_DAYS = 330;
  private static final int EXPENSES_PER_TRUCK_MIN = 2;
  private static final int EXPENSES_PER_TRUCK_MAX = 5;

  // Số lần đọc odometer cho mỗi xe. Phải >= 2: bộ tổng hợp tính quãng đường bằng hiệu giữa hai
  // lần đọc, nên một lần đọc đơn lẻ bị loại khỏi phép tính chứ không được coi là "xe không chạy".
  private static final int MILEAGE_READINGS_PER_TRUCK = 13;
  private static final int MILEAGE_READING_INTERVAL_DAYS = 28;
  private static final int BASE_ODOMETER = 120_000;
  private static final int MAX_ODOMETER_OFFSET = 40_000;
  private static final int MIN_DISTANCE_PER_INTERVAL = 4_000;
  private static final int MAX_DISTANCE_PER_INTERVAL = 7_500;

  private static final int MAINTENANCE_RECORDS_PER_TRUCK = 3;
  private static final long MIN_LABOR_COST = 1_500_000;
  private static final long MAX_LABOR_COST = 9_000_000;
  private static final long MIN_PARTS_COST = 500_000;
  private static final long MAX_PARTS_COST = 14_000_000;

  // Một lịch bảo trì định kỳ cho mỗi xe theo lịch. Cố ý để cả lịch quá hạn lẫn lịch chưa tới hạn:
  // nếu mọi lịch đều chưa tới hạn thì `pmCompliancePct` luôn bằng 100%, một con số trông đẹp nhưng
  // không kiểm chứng được phép chia.
  private static final int PM_INTERVAL_DAYS = 90;
  private static final int PM_DUE_SOON_DAYS = 30;
  private static final int PM_OVERDUE_DAYS = 15;

  // --- Document Constants ---
  private static final long MIN_DOC_SIZE_BYTES = 50_000;
  private static final long MAX_DOC_SIZE_BYTES = 5_000_000;

  // --- Status & Type Strings ---
  private static final String EMPLOYEE_SALARY_TYPE_FIXED = "Fixed";
  private static final String EMPLOYEE_SALARY_TYPE_SHARE = "ShareOfGross";
  private static final String INVOICE_STATUS_DRAFT = "Draft";
  private static final String INVOICE_STATUS_ISSUED = "Issued";
  private static final String INVOICE_STATUS_PAID = "Paid";
  private static final String PAYMENT_STATUS_COMPLETED = "completed";
  private static final String INSPECTION_TYPE_PRE_TRIP = "pre_trip";
  private static final String INSPECTION_TYPE_POST_TRIP = "post_trip";
  private static final String HAZARDOUS_LOAD_TYPE = "hazardous";
  private static final String DOCUMENT_OWNER_TYPE_LOAD = "load";
  private static final String DOCUMENT_STATUS_ACTIVE = "active";
  private static final String SEED_BLOB_CONTAINER = "seed-demo";
  private static final String EXPENSE_STATUS_APPROVED = "Approved";
  private static final String MAINTENANCE_TYPE_PREVENTIVE = "preventive";
  private static final String MAINTENANCE_INTERVAL_CALENDAR = "calendar";

  /**
   * Dấu nhận biết dữ liệu fixture tổng hợp.
   *
   * <p>Ghi vào trường ghi chú / số hoá đơn của mọi bản ghi chi phí và bảo trì do seeder tạo. Khi
   * một con số chi phí xuất hiện trên màn hình điều hành, người đọc phải phân biệt được nó đến từ
   * fixture dev hay từ giao dịch thật — nếu không thì "dữ liệu tổng hợp" và "dữ liệu thật" trông
   * giống hệt nhau trên màn hình.
   */
  private static final String SEED_FIXTURE_MARKER = "SEED-FIXTURE";

  // ===============================================================================================
  // Static Seed Data
  // ===============================================================================================

  private static final List<String> TENANTS =
      List.of(
          "Công ty Vận tải Sao Việt",
          "Vận tải & Logistics Nam Hải",
          "Express Logistics Miền Trung");

  private static final List<String> TRUCK_TYPES =
      List.of("flatbed", "refrigerated", "dry_van", "tanker", "container");
  private static final List<String> TRUCK_MAKES =
      List.of("Hino", "Isuzu", "Hyundai", "Thaco", "Daewoo", "Kia", "Fuso");
  private static final List<String> TRUCK_MODELS =
      List.of("X300", "Q370", "Mighty", "Porter", "New Power", "Tractors", "Canter");
  private static final List<String> TRUCK_PROVINCES =
      List.of("Hà Nội", "TP.HCM", "Đà Nẵng", "Hải Phòng", "Cần Thơ", "Đồng Nai", "Bình Dương");
  private static final List<String> LOAD_TYPES =
      List.of("general_freight", "perishable", HAZARDOUS_LOAD_TYPE, "container", "bulk");
  private static final List<String> LOAD_SOURCES =
      List.of("manual", "crm", "portal", "load_board", "api");
  private static final List<String> DOCUMENT_TYPES =
      List.of("bol", "pod", "invoice_copy", "security_check", "weight_ticket");
  private static final List<String> DOCUMENT_CONTENT_TYPES =
      List.of("application/pdf", "image/jpeg", "image/png");
  private static final List<String> INVOICE_TYPES = List.of("standard", "credit_note");

  /** Một loại chi phí fixture: chuỗi `type` ghi vào entity và khoảng tiền VND. */
  private record ExpenseFixture(String type, long minAmount, long maxAmount) {}

  /**
   * Các loại chi phí fixture, chọn sao cho {@code CostCategory.classify(type, null, null)} xếp được
   * từng dòng vào đúng nhóm hiển thị.
   *
   * <p>Chuỗi ở đây không phải nhãn hiển thị mà là khoá phân loại: {@code classify} so khớp từ khoá
   * con trên chính chuỗi này. Đổi một chuỗi thành giá trị lạ sẽ đẩy cả nhóm vào {@code
   * UNCLASSIFIED} — bản ghi vẫn được tính vào tổng nhưng biến mất khỏi nhóm mà người đọc đang xem,
   * nên phải giữ đúng các từ khoá mà enum nhận.
   */
  private static final List<ExpenseFixture> EXPENSE_FIXTURES =
      List.of(
          new ExpenseFixture("fuel", 8_000_000, 24_000_000),
          new ExpenseFixture("driver pay", 15_000_000, 32_000_000),
          new ExpenseFixture("maintenance", 2_000_000, 18_000_000),
          new ExpenseFixture("tire", 3_000_000, 15_000_000),
          new ExpenseFixture("toll", 500_000, 4_500_000),
          new ExpenseFixture("insurance", 5_000_000, 12_000_000),
          new ExpenseFixture("permit", 1_000_000, 5_000_000));

  private static final List<String> BILLING_CITIES =
      List.of("Hà Nội", "TP. Hồ Chí Minh", "Đà Nẵng", "Hải Phòng", "Cần Thơ");

  private record VietnamCity(
      String name, String state, String zipCode, double latitude, double longitude) {}

  private static final List<VietnamCity> VIETNAM_CITIES =
      List.of(
          new VietnamCity("Hà Nội", "Hà Nội", "100000", 21.0285, 105.8542),
          new VietnamCity("TP. Hồ Chí Minh", "TP. Hồ Chí Minh", "700000", 10.8231, 106.6297),
          new VietnamCity("Đà Nẵng", "Đà Nẵng", "550000", 16.0544, 108.2022),
          new VietnamCity("Hải Phòng", "Hải Phòng", "180000", 20.8458, 106.6881),
          new VietnamCity("Cần Thơ", "Cần Thơ", "900000", 10.0452, 105.7469),
          new VietnamCity("Đồng Nai", "Đồng Nai", "810000", 10.9574, 106.8425),
          new VietnamCity("Bình Dương", "Bình Dương", "820000", 10.9804, 106.6519),
          new VietnamCity("Nghệ An", "Nghệ An", "460000", 18.6747, 105.6892),
          new VietnamCity("Khánh Hòa", "Khánh Hòa", "650000", 12.2388, 109.1967),
          new VietnamCity("Lạng Sơn", "Lạng Sơn", "240000", 21.8533, 106.7615));

  private static final List<String> MESSAGE_TEMPLATES =
      List.of(
          "Đã nhận được thông tin lô hàng. Tôi sẽ chuẩn bị xe.",
          "Lịch trình đã được xác nhận. Sẽ khởi hành đúng giờ.",
          "Đã đến điểm lấy hàng. Đang chờ xếp hàng.",
          "Hàng đã được xếp lên xe. Đang kiểm tra chứng từ.",
          "Đã khởi hành. Dự kiến đến nơi sau %d giờ.",
          "Tình trạng giao thông ổn định. Đang đúng lộ trình.",
          "Đã giao hàng thành công. Khách hàng đã ký nhận.",
          "Có vấn đề phát sinh: %s",
          "Vui lòng cập nhật thông tin cho khách hàng.",
          "Đã cập nhật trạng thái. Cảm ơn đội xe.",
          "Xe đang gặp sự cố nhỏ, cần thêm %d phút để khắc phục.",
          "Hàng đã được kiểm tra, không có hư hại.");

  // ===============================================================================================
  // Dependencies
  // ===============================================================================================

  private final RoleService roleService;
  private final TenantRoleRepository roleRepository;
  private final EmployeeService employeeService;
  private final EmployeeRepository employeeRepository;
  private final CustomerService customerService;
  private final TruckService truckService;
  private final LoadService loadService;
  private final LoadRepository loadRepository;
  private final TripService tripService;
  private final TripRepository tripRepository;
  private final DocumentRepository documentRepository;
  private final InspectionService inspectionService;
  private final InvoiceService invoiceService;
  private final PaymentService paymentService;
  private final MessageService messageService;
  private final EntityManager entityManager;

  // ===============================================================================================
  // Seeder State
  // ===============================================================================================

  private final List<UUID> adminRoleIds = new ArrayList<>();
  private final List<UUID> dispatcherRoleIds = new ArrayList<>();
  private final List<UUID> driverRoleIds = new ArrayList<>();
  private final List<UUID> accountantRoleIds = new ArrayList<>();

  private final List<UUID> adminIds = new ArrayList<>();
  private final List<UUID> dispatcherIds = new ArrayList<>();
  private final List<UUID> driverIds = new ArrayList<>();
  private final List<UUID> employeeIds = new ArrayList<>();
  private final List<UUID> customerIds = new ArrayList<>();
  private final List<UUID> truckIds = new ArrayList<>();
  private final List<UUID> terminalIds = new ArrayList<>();
  private final List<UUID> loadIds = new ArrayList<>();

  private int countTerminals;
  private int countDocuments;
  private int countInspections;
  private int countPayments;
  private int countConversations;
  private int countMessages;
  private int countExpenses;
  private int countMaintenanceRecords;
  private int countMaintenanceSchedules;
  private int countMileageReadings;

  public DataSeeder(
      RoleService roleService,
      TenantRoleRepository roleRepository,
      EmployeeService employeeService,
      EmployeeRepository employeeRepository,
      CustomerService customerService,
      CustomerRepository customerRepository, // unused but kept for constructor consistency
      TruckService truckService,
      TruckRepository truckRepository, // unused
      LoadService loadService,
      LoadRepository loadRepository,
      TripService tripService,
      TripRepository tripRepository,
      DocumentRepository documentRepository,
      InspectionService inspectionService,
      InvoiceService invoiceService,
      PaymentService paymentService,
      ConversationService conversationService, // unused
      MessageService messageService,
      ConversationRepository conversationRepository, // unused
      EntityManager entityManager) {
    this.roleService = roleService;
    this.roleRepository = roleRepository;
    this.employeeService = employeeService;
    this.employeeRepository = employeeRepository;
    this.customerService = customerService;
    this.truckService = truckService;
    this.loadService = loadService;
    this.loadRepository = loadRepository;
    this.tripService = tripService;
    this.tripRepository = tripRepository;
    this.documentRepository = documentRepository;
    this.inspectionService = inspectionService;
    this.invoiceService = invoiceService;
    this.paymentService = paymentService;
    this.messageService = messageService;
    this.entityManager = entityManager;
  }

  @Override
  @Transactional
  public void run(String... args) {
    if (isSeedNeeded()) {
      log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
      log.info("  🚀 Starting seed data generation (profile: dev/seed)");
      log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

      seedRoles();
      seedTerminals();
      seedEmployees();
      seedCustomers();
      seedTrucks();
      seedLoads();
      seedTrips();
      seedDocuments();
      seedInspections();
      seedInvoicesAndPayments();
      seedFleetCostsAndHealth();
      seedConversationsAndMessages();

      printSummary();
    } else {
      log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
      log.info("  Seed data already exists — skipping.");
      log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
  }

  private boolean isSeedNeeded() {
    return roleRepository.count() == 0;
  }

  // ===============================================================================================
  // Main Seeding Methods
  // ===============================================================================================

  private void seedRoles() {
    log.info("── Seeding Roles ──");
    for (String tenant : TENANTS) {
      adminRoleIds.add(createRole(tenant, "Quản trị viên", "full_access").id());
      dispatcherRoleIds.add(
          createRole(tenant, "Điều phối viên", "dispatch", "view_loads", "view_trips").id());
      driverRoleIds.add(
          createRole(tenant, "Tài xế", "view_assigned_loads", "update_trip_status").id());
      accountantRoleIds.add(createRole(tenant, "Kế toán", "view_invoices", "manage_payments").id());
    }
  }

  private void seedTerminals() {
    log.info("── Seeding Terminals ──");
    for (int tenantIndex = 0; tenantIndex < TENANTS.size(); tenantIndex++) {
      for (int i = 0; i < TERMINALS_PER_TENANT; i++) {
        createAndPersistTerminal(tenantIndex, i);
      }
    }
  }

  private void seedEmployees() {
    log.info("── Seeding Employees ──");
    for (int tenantIndex = 0; tenantIndex < TENANTS.size(); tenantIndex++) {
      String tenant = TENANTS.get(tenantIndex);
      createEmployeesForRole(tenant, adminRoleIds.get(tenantIndex), ADMINS_PER_TENANT, adminIds);
      createEmployeesForRole(
          tenant, dispatcherRoleIds.get(tenantIndex), DISPATCHERS_PER_TENANT, dispatcherIds);
      createEmployeesForRole(tenant, driverRoleIds.get(tenantIndex), DRIVERS_PER_TENANT, driverIds);
      createEmployeesForRole(
          tenant, accountantRoleIds.get(tenantIndex), ACCOUNTANTS_PER_TENANT, null);
    }
  }

  private void seedCustomers() {
    log.info("── Seeding Customers ──");
    for (int i = 0; i < TENANTS.size() * CUSTOMERS_PER_TENANT; i++) {
      String companyName = FAKER.company().name();
      String email = "info@" + companyName.toLowerCase().replaceAll("[^a-z0-9]", "") + ".vn";

      CreateCustomerRequest request =
          new CreateCustomerRequest(
              companyName,
              email,
              "0" + FAKER.number().digits(9),
              ACTIVE_STATUS,
              FAKER.lorem().sentence(),
              FAKER.number().digits(10) + "-" + FAKER.number().digits(3),
              false,
              FAKER.address().streetAddress(),
              null,
              FAKER.address().cityName(),
              FAKER.address().state(),
              FAKER.address().zipCode(),
              COUNTRY_NAME_VIETNAM);

      CustomerResponse customer = customerService.create(request);
      customerIds.add(customer.id());
    }
  }

  private void seedTrucks() {
    log.info("── Seeding Trucks ──");
    for (int tenantIndex = 0; tenantIndex < TENANTS.size(); tenantIndex++) {
      for (int i = 0; i < TRUCKS_PER_TENANT; i++) {
        createSingleTruck(tenantIndex, i);
      }
    }
  }

  private void seedLoads() {
    log.info("── Seeding Loads ──");
    for (int tenantIndex = 0; tenantIndex < TENANTS.size(); tenantIndex++) {
      for (int i = 0; i < LOADS_PER_TENANT; i++) {
        createSingleLoad(tenantIndex, i);
      }
    }
  }

  private void seedTrips() {
    log.info("── Seeding Trips ──");
    List<Load> loads = loadRepository.findAllById(loadIds);

    for (Load load : loads) {
      String status = load.getStatus();
      boolean isDraftOrCancelled =
          status.equals(LoadStatus.DRAFT.dbValue())
              || status.equals(LoadStatus.CANCELLED.dbValue());
      if (isDraftOrCancelled) {
        continue;
      }

      UUID truckId = Optional.ofNullable(load.getAssignedTruck()).map(Truck::getId).orElse(null);
      CreateTripRequest request =
          new CreateTripRequest(
              "Chuyến " + load.getName(), load.getDistance(), "draft", truckId, List.of());
      var trip = tripService.create(request);
      tripService.dispatch(trip.id());
      if (status.equals(LoadStatus.DELIVERED.dbValue())) {
        tripService.complete(trip.id());
      }
    }
  }

  private void seedDocuments() {
    log.info("── Seeding Documents ──");
    List<Load> loads = loadRepository.findAllById(loadIds);

    for (Load load : loads) {
      if (FAKER.random().nextInt(0, 9) < 4) continue; // 40% chance to skip

      Optional<Employee> uploaderOpt = randomAdmin().flatMap(employeeRepository::findById);
      if (uploaderOpt.isEmpty()) continue;
      Employee uploader = uploaderOpt.orElseThrow();

      int docsForThisLoad = FAKER.number().numberBetween(1, 4);
      for (int i = 0; i < docsForThisLoad; i++) {
        createAndPersistDocument(load, uploader);
      }
    }
  }

  private void seedInspections() {
    log.info("── Seeding Inspections ──");
    List<Load> loads = loadRepository.findAllById(loadIds);

    for (Load load : loads) {
      if (FAKER.random().nextInt(0, 9) >= 4) continue; // 60% chance to skip

      Optional<UUID> inspectorIdOpt = randomDriver();
      if (inspectorIdOpt.isEmpty()) continue;
      UUID inspectorId = inspectorIdOpt.orElseThrow();

      String inspectionType =
          FAKER.bool().bool() ? INSPECTION_TYPE_PRE_TRIP : INSPECTION_TYPE_POST_TRIP;
      OffsetDateTime inspectedAt =
          OffsetDateTime.now(VIETNAM_ZONE_OFFSET).minusDays(FAKER.number().numberBetween(0, 30));

      CreateInspectionRequest request =
          new CreateInspectionRequest(
              load.getId(),
              inspectionType,
              FAKER.number().digits(17),
              FAKER.number().numberBetween(MIN_TRUCK_YEAR, MAX_TRUCK_YEAR),
              FAKER.company().name(),
              FAKER.commerce().productName(),
              null,
              null,
              null,
              FAKER.lorem().sentence(),
              null,
              parseLocalisedDecimal(FAKER.address().latitude()),
              parseLocalisedDecimal(FAKER.address().longitude()),
              inspectedAt,
              inspectorId,
              List.of());

      inspectionService.create(request);
      countInspections++;
    }
  }

  private void seedInvoicesAndPayments() {
    log.info("── Seeding Invoices & Payments ──");
    List<Load> loads = loadRepository.findAllById(loadIds);

    for (Load load : loads) {
      String loadStatus = load.getStatus();
      boolean isDraftOrCancelled =
          loadStatus.equals(LoadStatus.DRAFT.dbValue())
              || loadStatus.equals(LoadStatus.CANCELLED.dbValue());

      if (isDraftOrCancelled) {
        continue;
      }

      InvoiceResponse invoice = createInvoiceForLoad(load);
      if (invoice.status().equals(INVOICE_STATUS_PAID)) {
        createPaymentForInvoice(invoice, load);
      }
    }
  }

  private void seedConversationsAndMessages() {
    log.info("── Seeding Conversations & Messages ──");
    List<Load> loads = loadRepository.findAllById(loadIds);

    for (Load load : loads) {
      if (FAKER.random().nextInt(0, 9) >= 3) continue; // 70% chance to skip

      Conversation conversation = createAndPersistConversation(load);
      List<UUID> participantIds = addParticipantsToConversation(conversation, load);

      if (!participantIds.isEmpty()) {
        seedMessagesForConversation(conversation.getId(), participantIds);
      }
      countConversations++;
    }
  }

  // ===============================================================================================
  // Fleet Cost & Health Fixtures (Executive Overview)
  // ===============================================================================================

  /**
   * Tạo dữ liệu nguồn cho các chỉ số chi phí và sức khoẻ đội xe của màn hình Executive Overview.
   *
   * <p>Đây là <b>fixture tổng hợp</b>, không phải giao dịch thật: mọi bản ghi đều mang dấu {@link
   * #SEED_FIXTURE_MARKER}. Trước khi có phương thức này, {@code expenses} và {@code
   * maintenance_records} rỗng ở mọi môi trường, nên các endpoint báo cáo trả về {@code
   * NO_SOURCE_ROWS} cho mọi chỉ số chi phí và {@code NO_PM_SCHEDULE} cho tỷ lệ tuân thủ bảo trì.
   * Cách trả lời đó đúng, nhưng nó cũng có nghĩa là không phép tính nào ở tầng báo cáo từng được
   * chạy trên dữ liệu thật.
   *
   * <p>Dữ liệu ở đây KHÔNG làm sống lại những chỉ số mà tầng service cố ý giữ ở trạng thái
   * unavailable. {@code unplannedDowntimePct} và {@code breakdownsPer100kMiles} vẫn trả về
   * unavailable vì chính {@code ReportServiceImpl} ghi cứng như vậy, bất kể seeder có điền cột
   * {@code downtime_start_at} hay {@code is_unplanned} hay không. Điền vào một cột không ai đọc chỉ
   * tạo ra thứ trông có ý nghĩa mà thực ra không có ý nghĩa nào.
   */
  private void seedFleetCostsAndHealth() {
    log.info("── Seeding Fleet Costs, Maintenance & Odometer (SYNTHETIC FIXTURES) ──");
    OffsetDateTime now = OffsetDateTime.now(VIETNAM_ZONE_OFFSET);

    for (int tenantIndex = 0; tenantIndex < TENANTS.size(); tenantIndex++) {
      for (int truckNum = 0; truckNum < TRUCKS_PER_TENANT; truckNum++) {
        Truck truck =
            entityManager.find(
                Truck.class, truckIds.get(tenantIndex * TRUCKS_PER_TENANT + truckNum));
        if (truck == null) {
          continue;
        }

        createMileageReadingsFixture(truck, now);
        createExpensesFixture(truck, now);
        createMaintenanceRecordsFixture(truck, now);
        createMaintenanceScheduleFixture(truck, now, truckNum);
      }
    }
  }

  /**
   * Lịch sử đọc công tơ cho một xe.
   *
   * <p>Phải có ít nhất hai lần đọc trong cửa sổ báo cáo: quãng đường được tính bằng hiệu giữa lần
   * đọc cao nhất và thấp nhất của từng xe, nên xe chỉ có một lần đọc bị loại khỏi phép cộng và được
   * đếm riêng — một điểm dữ liệu không phải là "xe không chạy".
   */
  private void createMileageReadingsFixture(Truck truck, OffsetDateTime now) {
    // Lần đọc mới nhất lùi lại 2 ngày để mọi bản ghi chắc chắn nằm trong cửa sổ báo cáo, kể cả khi
    // mốc `to` của client được tính theo múi giờ khác.
    OffsetDateTime newest = now.minusDays(2);
    int odometer = BASE_ODOMETER + FAKER.number().numberBetween(0, MAX_ODOMETER_OFFSET);

    // Ghi từ cũ nhất tới mới nhất: chỉ số công tơ chỉ tăng theo thời gian. Ghi ngược lại sẽ tạo ra
    // hiệu số âm, và tầng báo cáo loại bỏ hiệu số âm như một lỗi dữ liệu chứ không cộng nó vào.
    for (int i = MILEAGE_READINGS_PER_TRUCK - 1; i >= 0; i--) {
      VehicleMileageReading reading = new VehicleMileageReading();
      reading.setTruck(truck);
      reading.setReadingValue(odometer);
      reading.setRecordedAt(newest.minusDays((long) i * MILEAGE_READING_INTERVAL_DAYS));
      reading.setSource(SEED_FIXTURE_MARKER);
      entityManager.persist(reading);
      countMileageReadings++;

      odometer +=
          FAKER.number().numberBetween(MIN_DISTANCE_PER_INTERVAL, MAX_DISTANCE_PER_INTERVAL + 1);
    }
  }

  /**
   * Chi phí vận hành theo xe.
   *
   * <p>Chỉ đặt liên kết xe thứ nhất. {@code Expense} có hai khoá ngoại trỏ tới xe; đặt cả hai sẽ
   * khiến dòng đó được báo là "không quy được về một xe" nếu hai giá trị khác nhau, và bị đếm hai
   * lần nếu tầng báo cáo cộng gộp cả hai.
   */
  private void createExpensesFixture(Truck truck, OffsetDateTime now) {
    int rows = FAKER.number().numberBetween(EXPENSES_PER_TRUCK_MIN, EXPENSES_PER_TRUCK_MAX + 1);
    for (int i = 0; i < rows; i++) {
      ExpenseFixture fixture = randomFromList(EXPENSE_FIXTURES).orElseThrow();

      Expense expense = new Expense();
      expense.setType(fixture.type());
      expense.setStatus(EXPENSE_STATUS_APPROVED);
      expense.setVendorName(FAKER.company().name());
      expense.setExpenseDate(
          now.minusDays(FAKER.number().numberBetween(0, COST_FIXTURE_WINDOW_DAYS + 1)));
      expense.setAmountAmount(
          BigDecimal.valueOf(
                  FAKER.number().numberBetween(fixture.minAmount(), fixture.maxAmount() + 1))
              .setScale(2, RoundingMode.HALF_UP));
      expense.setAmountCurrency(VND_CURRENCY);
      expense.setTruck(truck);
      expense.setNotes(SEED_FIXTURE_MARKER + " — dữ liệu dev tổng hợp, không phải giao dịch thật");
      entityManager.persist(expense);
      countExpenses++;
    }
  }

  /** Bảo trì đã thực hiện, có phí tổn và số công tơ tại thời điểm làm. */
  private void createMaintenanceRecordsFixture(Truck truck, OffsetDateTime now) {
    for (int i = 0; i < MAINTENANCE_RECORDS_PER_TRUCK; i++) {
      BigDecimal labor =
          BigDecimal.valueOf(FAKER.number().numberBetween(MIN_LABOR_COST, MAX_LABOR_COST + 1))
              .setScale(2, RoundingMode.HALF_UP);
      BigDecimal parts =
          BigDecimal.valueOf(FAKER.number().numberBetween(MIN_PARTS_COST, MAX_PARTS_COST + 1))
              .setScale(2, RoundingMode.HALF_UP);

      MaintenanceRecord record = new MaintenanceRecord();
      record.setTruck(truck);
      record.setMaintenanceType(MAINTENANCE_TYPE_PREVENTIVE);
      record.setServiceDate(
          now.minusDays(FAKER.number().numberBetween(0, COST_FIXTURE_WINDOW_DAYS + 1)));
      record.setOdometerReading(
          FAKER.number().numberBetween(BASE_ODOMETER, BASE_ODOMETER + MAX_ODOMETER_OFFSET));
      record.setVendorName(FAKER.company().name());
      record.setVendorAddress(FAKER.address().streetAddress());
      record.setInvoiceNumber(SEED_FIXTURE_MARKER + "-" + truck.getNumber() + "-" + (i + 1));
      record.setLaborCost(labor);
      record.setPartsCost(parts);
      record.setTotalCost(labor.add(parts));
      record.setTotalCostCurrency(VND_CURRENCY);
      record.setDescription("Bảo trì định kỳ — dữ liệu dev tổng hợp");
      record.setWorkPerformed("Thay dầu, kiểm tra phanh, vệ sinh lọc gió");
      // Cố ý để isUnplanned, isBreakdown, downtimeStartAt và downtimeEndAt là null. Cột nullable
      // này nghĩa là "chưa phân loại"; tầng báo cáo loại các dòng đó khỏi cả tử lẫn mẫu và báo cáo
      // số lượng. Điền false sẽ là khẳng định mọi bản ghi đều "đã lên kế hoạch và không hỏng" —
      // một khẳng định seeder không có bằng chứng để đưa ra, kể cả với dữ liệu tự sinh.
      entityManager.persist(record);
      countMaintenanceRecords++;
    }
  }

  /** Lịch bảo trì định kỳ theo lịch cho một xe. */
  private void createMaintenanceScheduleFixture(Truck truck, OffsetDateTime now, int truckNum) {
    MaintenanceSchedule schedule = new MaintenanceSchedule();
    schedule.setTruck(truck);
    schedule.setMaintenanceType(MAINTENANCE_TYPE_PREVENTIVE);
    schedule.setIntervalType(MAINTENANCE_INTERVAL_CALENDAR);
    schedule.setDaysInterval(PM_INTERVAL_DAYS);
    schedule.setLastServiceDate(now.minusDays(PM_INTERVAL_DAYS));
    // Xen kẽ xe quá hạn và xe chưa tới hạn. Nếu mọi lịch cùng nằm một phía thì tỷ lệ tuân thủ luôn
    // là 0% hoặc 100% — một con số trông hợp lý nhưng không kiểm chứng được phép chia nào.
    schedule.setNextDueDate(
        truckNum % 2 == 0 ? now.plusDays(PM_DUE_SOON_DAYS) : now.minusDays(PM_OVERDUE_DAYS));
    schedule.setIsActive(true);
    schedule.setNotes(SEED_FIXTURE_MARKER + " — lịch bảo trì dev tổng hợp");
    // Không đặt mileageInterval: lịch theo lịch không cần mốc công tơ, và đặt mileageInterval mà
    // bỏ trống nextDueMileage sẽ tự đẩy lịch này vào schedulesRequiringOdometerCount — một cảnh báo
    // chỉ vì seeder điền nửa vời.
    entityManager.persist(schedule);
    countMaintenanceSchedules++;
  }

  // ===============================================================================================
  // Creation & Persistence Helper Methods
  // ===============================================================================================

  private RoleResponse createRole(String tenant, String roleNameSuffix, String... permissions) {
    String roleName = tenant + " - " + roleNameSuffix;
    List<CreateRoleRequest.ClaimRequest> claims = new ArrayList<>();
    for (String permission : permissions) {
      claims.add(new CreateRoleRequest.ClaimRequest("permission", permission));
    }
    return roleService.create(new CreateRoleRequest(roleName, roleName, claims));
  }

  private void createAndPersistTerminal(int tenantIndex, int terminalNum) {
    String[] terminalTypes = {"origin", "transit", "destination"};

    Terminal terminal = new Terminal();
    terminal.setName(FAKER.address().cityName() + " Terminal " + (terminalNum + 1));
    terminal.setCode("T" + (tenantIndex + 1) + String.format("%02d", (terminalNum + 1)));
    terminal.setCountryCode(COUNTRY_CODE_VN);
    terminal.setType(terminalTypes[terminalNum % terminalTypes.length]);
    terminal.setNotes(FAKER.lorem().sentence());
    terminal.setAddressLine1(FAKER.address().streetAddress());
    terminal.setAddressCity(FAKER.address().cityName());
    terminal.setAddressState(FAKER.address().state());
    terminal.setAddressZipCode(FAKER.address().zipCode());
    terminal.setAddressCountry(COUNTRY_NAME_VIETNAM);

    entityManager.persist(terminal);
    terminalIds.add(terminal.getId());
    countTerminals++;
  }

  private void createEmployeesForRole(
      String tenant, UUID roleId, int count, List<UUID> idListToPopulate) {
    for (int i = 0; i < count; i++) {
      EmployeeResponse employee = createSingleEmployee(tenant, roleId);
      employeeIds.add(employee.id());
      if (idListToPopulate != null) {
        idListToPopulate.add(employee.id());
      }
    }
  }

  private EmployeeResponse createSingleEmployee(String tenant, UUID roleId) {
    String firstName = FAKER.name().firstName();
    String lastName = FAKER.name().lastName();
    String email =
        (firstName
                + "."
                + lastName
                + "@"
                + tenant.toLowerCase().replaceAll("[^a-z0-9]", "")
                + ".com")
            .toLowerCase();

    boolean isDriver = driverRoleIds.contains(roleId);
    String salaryType = isDriver ? EMPLOYEE_SALARY_TYPE_SHARE : EMPLOYEE_SALARY_TYPE_FIXED;
    BigDecimal salaryAmount;
    if (isDriver) {
      double share =
          FAKER
              .number()
              .randomDouble(2, (int) (MIN_SALARY_SHARE * 100), (int) (MAX_SALARY_SHARE * 100));
      salaryAmount = BigDecimal.valueOf(share / 100.0);
    } else {
      salaryAmount =
          BigDecimal.valueOf(FAKER.number().numberBetween(MIN_SALARY_FIXED, MAX_SALARY_FIXED));
    }

    OffsetDateTime hireDate =
        OffsetDateTime.now(VIETNAM_ZONE_OFFSET)
            .minusDays(FAKER.number().numberBetween(MIN_HIRE_DAYS_AGO, MAX_HIRE_DAYS_AGO));

    CreateEmployeeRequest request =
        new CreateEmployeeRequest(
            email,
            firstName,
            lastName,
            "0" + FAKER.number().digits(9),
            salaryType,
            ACTIVE_STATUS,
            hireDate,
            roleId,
            salaryAmount,
            VND_CURRENCY,
            FAKER.address().streetAddress(),
            null,
            FAKER.address().cityName(),
            FAKER.address().state(),
            FAKER.address().zipCode(),
            COUNTRY_NAME_VIETNAM);

    return employeeService.create(request);
  }

  private void createSingleTruck(int tenantIndex, int truckNum) {
    int driversPerTenant = DRIVERS_PER_TENANT;
    int baseDriverIndex = tenantIndex * driversPerTenant;
    UUID mainDriverId = driverIds.get(baseDriverIndex + truckNum);
    UUID secondaryDriverId =
        (truckNum + 1 < driversPerTenant) ? driverIds.get(baseDriverIndex + truckNum + 1) : null;

    int year = FAKER.number().numberBetween(MIN_TRUCK_YEAR, MAX_TRUCK_YEAR + 1);
    String plateLetter = String.valueOf((char) ('A' + FAKER.number().numberBetween(0, 20)));
    String plateNumber =
        String.format(
            "%s %03d.%02d",
            plateLetter,
            FAKER.number().numberBetween(100, 999),
            FAKER.number().numberBetween(1, 99));

    CreateTruckRequest request =
        new CreateTruckRequest(
            "XE-" + (tenantIndex + 1) + String.format("%03d", truckNum + 1),
            randomFromList(TRUCK_TYPES).orElse("dry_van"),
            BASE_TRUCK_CAPACITY
                + FAKER.number().numberBetween(MIN_CAPACITY_ADJUSTMENT, MAX_CAPACITY_ADJUSTMENT),
            ACTIVE_STATUS,
            randomFromList(TRUCK_MAKES).orElse("Hino"),
            randomFromList(TRUCK_MODELS).orElse("X300"),
            year,
            FAKER.number().digits(17),
            plateNumber,
            randomFromList(TRUCK_PROVINCES).orElse("Hà Nội"),
            false,
            mainDriverId,
            secondaryDriverId,
            false,
            "",
            null);

    TruckResponse truck = truckService.create(request);
    truckIds.add(truck.id());
  }

  private void createSingleLoad(int tenantIndex, int loadNum) {
    var locations = selectRandomOriginAndDestination();
    VietnamCity origin = locations.origin;
    VietnamCity destination = locations.destination;

    UUID customerId = randomCustomer(tenantIndex).orElseThrow();
    int truckIndexInTenant = FAKER.number().numberBetween(0, TRUCKS_PER_TENANT - 1);
    UUID truckId = truckIds.get(tenantIndex * TRUCKS_PER_TENANT + truckIndexInTenant);
    UUID driverId = driverIds.get(tenantIndex * DRIVERS_PER_TENANT + truckIndexInTenant);
    UUID dispatcherId = randomDispatcher(tenantIndex).orElseThrow();

    String loadType = randomFromList(LOAD_TYPES).orElse("general_freight");
    boolean isHazmat = loadType.equals(HAZARDOUS_LOAD_TYPE);
    double distance = FAKER.number().randomDouble(1, 50, 1800);
    BigDecimal cost =
        BigDecimal.valueOf(FAKER.number().numberBetween(MIN_LOAD_COST, MAX_LOAD_COST));

    String targetStatus = determineLoadStatusForSeeding(loadNum);
    OffsetDateTime now = OffsetDateTime.now(VIETNAM_ZONE_OFFSET);
    OffsetDateTime pickupDate = now.plusDays(FAKER.number().numberBetween(1, 14));
    OffsetDateTime deliveryDate = pickupDate.plusDays(FAKER.number().numberBetween(1, 5));

    int terminalsPerTenant = TERMINALS_PER_TENANT;
    UUID originTerminalId = terminalIds.get(tenantIndex * terminalsPerTenant);
    UUID destTerminalId = terminalIds.get(tenantIndex * terminalsPerTenant + 2);

    CreateLoadRequest request =
        new CreateLoadRequest(
            FAKER.commerce().productName(),
            loadType,
            LoadStatus.DRAFT.dbValue(),
            distance,
            false,
            customerId,
            truckId,
            dispatcherId,
            randomFromList(LOAD_SOURCES).orElse("manual"),
            pickupDate,
            deliveryDate,
            FAKER.lorem().sentence(),
            isHazmat,
            isHazmat ? "Class " + FAKER.number().numberBetween(1, 9) : null,
            isHazmat ? "UN" + FAKER.number().numberBetween(1000, 9999) : null,
            null,
            originTerminalId,
            destTerminalId,
            null,
            null,
            null,
            cost,
            VND_CURRENCY,
            FAKER.address().streetAddress(),
            null,
            origin.name(),
            origin.state(),
            origin.zipCode(),
            COUNTRY_NAME_VIETNAM,
            origin.latitude(),
            origin.longitude(),
            FAKER.address().streetAddress(),
            null,
            destination.name(),
            destination.state(),
            destination.zipCode(),
            COUNTRY_NAME_VIETNAM,
            destination.latitude(),
            destination.longitude());

    LoadResponse load = loadService.create(request);
    if (targetStatus.equals(LoadStatus.DISPATCHED.dbValue())) {
      loadService.dispatch(load.id());
    } else if (targetStatus.equals(LoadStatus.PICKED_UP.dbValue())) {
      loadService.dispatch(load.id());
      loadService.pickUp(load.id(), driverId);
    } else if (targetStatus.equals(LoadStatus.DELIVERED.dbValue())) {
      loadService.dispatch(load.id());
      loadService.pickUp(load.id(), driverId);
      loadService.deliver(load.id(), driverId);
    } else if (targetStatus.equals(LoadStatus.CANCELLED.dbValue())) {
      loadService.cancel(load.id());
    }
    loadIds.add(load.id());
  }

  private void createAndPersistDocument(Load load, Employee uploader) {
    String docType = randomFromList(DOCUMENT_TYPES).orElse("bol");
    String fileName = docType + "_" + load.getNumber() + "_" + (countDocuments + 1) + ".pdf";

    Document doc = new Document();
    doc.setOwnerType(DOCUMENT_OWNER_TYPE_LOAD);
    doc.setFileName(fileName);
    doc.setOriginalFileName(fileName);
    doc.setContentType(randomFromList(DOCUMENT_CONTENT_TYPES).orElse("application/pdf"));
    doc.setFileSizeBytes(FAKER.number().numberBetween(MIN_DOC_SIZE_BYTES, MAX_DOC_SIZE_BYTES));
    doc.setBlobPath("seed/" + load.getId() + "/" + fileName);
    doc.setBlobContainer(SEED_BLOB_CONTAINER);
    doc.setType(docType);
    doc.setStatus(DOCUMENT_STATUS_ACTIVE);
    doc.setUploadedBy(uploader);
    doc.setLoad(load);
    doc.setDescription(getDocumentDescription(docType));

    documentRepository.save(doc);
    countDocuments++;
  }

  private InvoiceResponse createInvoiceForLoad(Load load) {
    BigDecimal subTotal = load.getDeliveryCostAmount();
    BigDecimal taxAmount =
        subTotal.multiply(TAX_RATE_TEN_PERCENT).setScale(2, RoundingMode.HALF_UP);
    BigDecimal total = subTotal.add(taxAmount);
    String invoiceStatus = determineInvoiceStatus(load.getStatus());

    UUID dispatcherId =
        Optional.ofNullable(load.getAssignedDispatcher()).map(Employee::getId).orElse(null);

    CreateInvoiceRequest request =
        new CreateInvoiceRequest(
            randomFromList(INVOICE_TYPES).orElse("standard"),
            invoiceStatus,
            "exclusive",
            null,
            OffsetDateTime.now(VIETNAM_ZONE_OFFSET).plusDays(30),
            load.getId(),
            load.getCustomer().getId(),
            dispatcherId,
            subTotal,
            VND_CURRENCY,
            taxAmount,
            VND_CURRENCY,
            total,
            VND_CURRENCY,
            null,
            null,
            null);
    return invoiceService.create(request);
  }

  private void createPaymentForInvoice(InvoiceResponse invoice, Load load) {
    CreatePaymentRequest request =
        new CreatePaymentRequest(
            PAYMENT_STATUS_COMPLETED,
            invoice.id(),
            invoice.totalAmount(),
            VND_CURRENCY,
            "Thanh toán hóa đơn vận chuyển " + load.getNumber(),
            "PMT-" + load.getNumber(),
            null,
            null,
            OffsetDateTime.now(VIETNAM_ZONE_OFFSET),
            FAKER.address().streetAddress(),
            null,
            randomFromList(BILLING_CITIES).orElse("Hà Nội"),
            FAKER.address().state(),
            FAKER.address().zipCode(),
            COUNTRY_NAME_VIETNAM);
    paymentService.create(request);
    countPayments++;
  }

  private Conversation createAndPersistConversation(Load load) {
    Conversation conversation = new Conversation();
    conversation.setName("Trao đổi: " + load.getName());
    conversation.setLoad(load);
    conversation.setIsTenantChat(false);
    conversation.setCreatedAt(OffsetDateTime.now(VIETNAM_ZONE_OFFSET).minusDays(3));
    entityManager.persist(conversation);
    return conversation;
  }

  private List<UUID> addParticipantsToConversation(Conversation conversation, Load load) {
    List<UUID> participantIds = new ArrayList<>();

    Optional.ofNullable(load.getAssignedDispatcher())
        .ifPresent(
            employee -> {
              createAndPersistParticipant(conversation, employee);
              participantIds.add(employee.getId());
            });

    Optional.ofNullable(load.getAssignedTruck())
        .ifPresent(
            truck -> {
              Optional.ofNullable(truck.getMainDriver())
                  .ifPresent(
                      employee -> {
                        createAndPersistParticipant(conversation, employee);
                        participantIds.add(employee.getId());
                      });
              Optional.ofNullable(truck.getSecondaryDriver())
                  .ifPresent(
                      employee -> {
                        createAndPersistParticipant(conversation, employee);
                        participantIds.add(employee.getId());
                      });
            });

    return participantIds;
  }

  private void createAndPersistParticipant(Conversation conversation, Employee employee) {
    ConversationParticipant participant = new ConversationParticipant();
    participant.setConversation(conversation);
    participant.setEmployee(employee);
    participant.setJoinedAt(OffsetDateTime.now(VIETNAM_ZONE_OFFSET).minusDays(7));
    participant.setIsMuted(false);
    entityManager.persist(participant);
  }

  private void seedMessagesForConversation(UUID conversationId, List<UUID> participantIds) {
    int messageCount = FAKER.number().numberBetween(2, 7);
    for (int i = 0; i < messageCount; i++) {
      UUID senderId = randomFromList(participantIds).orElseThrow();
      String content = pickRandomVietnameseMessage();
      messageService.create(new SendMessageRequest(conversationId, senderId, content));
      countMessages++;
    }
  }

  // ===============================================================================================
  // Logic & Randomization Helper Methods
  // ===============================================================================================

  /**
   * Đọc một số thập phân do Faker sinh ra theo ngôn ngữ đang dùng.
   *
   * <p>Faker được dựng với {@code Locale("vi")}, nên {@code address().latitude()} trả về chuỗi dùng
   * dấu phẩy làm dấu thập phân ("-21,56260295"). {@code Double.parseDouble} chỉ hiểu dấu chấm và
   * ném {@code NumberFormatException} — lỗi này làm hỏng cả tiến trình seed, không chỉ một dòng, vì
   * nó ném ra từ trong {@code CommandLineRunner}.
   */
  private static double parseLocalisedDecimal(String value) {
    return Double.parseDouble(value.replace(',', '.'));
  }

  private <T> Optional<T> randomFromList(List<T> list) {
    if (list == null || list.isEmpty()) {
      return Optional.empty();
    }
    // Bug fix: FAKER.number().numberBetween is inclusive. Using list.size() would cause
    // IndexOutOfBoundsException.
    return Optional.of(list.get(FAKER.number().numberBetween(0, list.size() - 1)));
  }

  private Optional<UUID> randomAdmin() {
    return randomFromList(adminIds);
  }

  private Optional<UUID> randomDriver() {
    return randomFromList(driverIds);
  }

  private Optional<UUID> randomDispatcher(int tenantIndex) {
    int start = tenantIndex * DISPATCHERS_PER_TENANT;
    int end = start + DISPATCHERS_PER_TENANT;
    return Optional.of(dispatcherIds.get(FAKER.number().numberBetween(start, end - 1)));
  }

  private Optional<UUID> randomCustomer(int tenantIndex) {
    int start = tenantIndex * CUSTOMERS_PER_TENANT;
    int end = start + CUSTOMERS_PER_TENANT;
    return Optional.of(customerIds.get(FAKER.number().numberBetween(start, end - 1)));
  }

  private Optional<UUID> randomTruck(int tenantIndex) {
    int start = tenantIndex * TRUCKS_PER_TENANT;
    int end = start + TRUCKS_PER_TENANT;
    return Optional.of(truckIds.get(FAKER.number().numberBetween(start, end - 1)));
  }

  private record LocationPair(VietnamCity origin, VietnamCity destination) {}

  private LocationPair selectRandomOriginAndDestination() {
    int originIndex = FAKER.number().numberBetween(0, VIETNAM_CITIES.size() - 1);
    int destinationIndex;
    do {
      destinationIndex = FAKER.number().numberBetween(0, VIETNAM_CITIES.size() - 1);
    } while (destinationIndex == originIndex);

    return new LocationPair(VIETNAM_CITIES.get(originIndex), VIETNAM_CITIES.get(destinationIndex));
  }

  private String determineLoadStatusForSeeding(int index) {
    if (index < 3) return LoadStatus.DRAFT.dbValue();
    if (index < 6) return LoadStatus.DISPATCHED.dbValue();
    if (index < 9) return LoadStatus.PICKED_UP.dbValue();
    if (index < 11) return LoadStatus.DELIVERED.dbValue();
    return LoadStatus.CANCELLED.dbValue();
  }

  private String determineInvoiceStatus(String loadStatus) {
    if (loadStatus.equals(LoadStatus.DELIVERED.dbValue())) {
      return FAKER.random().nextInt(0, 9) < 6 ? INVOICE_STATUS_PAID : INVOICE_STATUS_ISSUED;
    } else if (loadStatus.equals(LoadStatus.PICKED_UP.dbValue())) {
      return INVOICE_STATUS_ISSUED;
    } else {
      return INVOICE_STATUS_DRAFT;
    }
  }

  private String getDocumentDescription(String docType) {
    return switch (docType) {
      case "bol" -> "Bill of Lading";
      case "pod" -> "Proof of Delivery";
      case "invoice_copy" -> "Hóa đơn vận chuyển";
      case "security_check" -> "Phiếu kiểm tra an ninh";
      case "weight_ticket" -> "Phiếu cân";
      default -> "Tài liệu chung";
    };
  }

  private String pickRandomVietnameseMessage() {
    String template = randomFromList(MESSAGE_TEMPLATES).orElse("");
    if (template.contains("%d")) {
      return String.format(template, FAKER.number().numberBetween(10, 120));
    } else if (template.contains("%s")) {
      return String.format(template, FAKER.lorem().sentence(3));
    }
    return template;
  }

  // ===============================================================================================
  // Summary & Output
  // ===============================================================================================

  private void printSummary() {
    log.info("");
    log.info("╔══════════════════════════════════════════════════════════╗");
    log.info("║           SEED DATA GENERATION COMPLETE                 ║");
    log.info("╠══════════════════════════════════════════════════════════╣");
    log.info("║  Tenants (virtual):  {}", padRight(String.valueOf(TENANTS.size()), 25));
    TENANTS.forEach(tenant -> log.info("║    • {}", tenant));
    log.info("╠══════════════════════════════════════════════════════════╣");
    log.info("║  Roles:              {}", padRight(String.valueOf(adminRoleIds.size() * 4), 25));
    log.info("║  Terminals:          {}", padRight(String.valueOf(countTerminals), 25));
    log.info("║  Employees:          {}", padRight(String.valueOf(employeeIds.size()), 25));
    log.info("║    • Admins:         {}", padRight(String.valueOf(adminIds.size()), 25));
    log.info("║    • Dispatchers:    {}", padRight(String.valueOf(dispatcherIds.size()), 25));
    log.info("║    • Drivers:        {}", padRight(String.valueOf(driverIds.size()), 25));
    log.info("║  Customers:          {}", padRight(String.valueOf(customerIds.size()), 25));
    log.info("║  Trucks:             {}", padRight(String.valueOf(truckIds.size()), 25));
    log.info("║  Loads:              {}", padRight(String.valueOf(loadIds.size()), 25));
    log.info("║  Trips:              {}", padRight(String.valueOf(tripRepository.count()), 25));
    log.info("║  Documents:          {}", padRight(String.valueOf(countDocuments), 25));
    log.info("║  Inspections:        {}", padRight(String.valueOf(countInspections), 25));
    log.info(
        "║  Invoices:           {}", padRight(String.valueOf(loadIds.size()), 25)); // simplified
    log.info("║  Payments:           {}", padRight(String.valueOf(countPayments), 25));
    log.info("║  Conversations:      {}", padRight(String.valueOf(countConversations), 25));
    log.info("║  Messages:           {}", padRight(String.valueOf(countMessages), 25));
    log.info("╠══════════════════════════════════════════════════════════╣");
    log.info("║  SYNTHETIC fixtures (not real transactions):             ║");
    log.info("║    • Expenses:       {}", padRight(String.valueOf(countExpenses), 25));
    log.info("║    • Maintenance:    {}", padRight(String.valueOf(countMaintenanceRecords), 25));
    log.info("║    • PM schedules:   {}", padRight(String.valueOf(countMaintenanceSchedules), 25));
    log.info("║    • Odometer reads: {}", padRight(String.valueOf(countMileageReadings), 25));
    log.info("╚══════════════════════════════════════════════════════════╝");
    log.info("");

    log.info("── Demo login credentials (use these emails to log in per tenant) ──");
    for (int t = 0; t < TENANTS.size(); t++) {
      log.info("  [{}]", TENANTS.get(t));
      int baseAdminIndex = t * ADMINS_PER_TENANT;
      int baseDispatcherIndex = t * DISPATCHERS_PER_TENANT;
      int baseDriverIndex = t * DRIVERS_PER_TENANT;

      logEmployeeLogin(adminIds, baseAdminIndex, "Admin");
      logEmployeeLogin(dispatcherIds, baseDispatcherIndex, "Dispatcher");
      logEmployeeLogin(driverIds, baseDriverIndex, "Driver");
    }
  }

  private void logEmployeeLogin(List<UUID> idList, int index, String role) {
    if (index < idList.size()) {
      employeeRepository
          .findById(idList.get(index))
          .ifPresent(
              e ->
                  log.info(
                      "    {}:   {} / mật khẩu (qua Keycloak)", padRight(role, 12), e.getEmail()));
    }
  }

  private static String padRight(String s, int n) {
    return String.format("%-" + n + "s", s);
  }
}
