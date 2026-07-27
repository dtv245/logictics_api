package com.company.logicstic.devtools.seed;

import com.company.logicstic.modules.customer.dto.request.CreateCustomerRequest;
import com.company.logicstic.modules.customer.dto.response.CustomerResponse;
import com.company.logicstic.modules.customer.repository.CustomerRepository;
import com.company.logicstic.modules.customer.service.CustomerService;
import com.company.logicstic.modules.document.entity.Document;
import com.company.logicstic.modules.document.repository.DocumentRepository;
import com.company.logicstic.modules.employee.dto.request.CreateEmployeeRequest;
import com.company.logicstic.modules.employee.dto.response.EmployeeResponse;
import com.company.logicstic.modules.employee.entity.Employee;
import com.company.logicstic.modules.employee.repository.EmployeeRepository;
import com.company.logicstic.modules.employee.service.EmployeeService;
import com.company.logicstic.modules.finance.dto.request.CreateInvoiceRequest;
import com.company.logicstic.modules.finance.dto.request.CreatePaymentRequest;
import com.company.logicstic.modules.finance.dto.response.InvoiceResponse;
import com.company.logicstic.modules.finance.service.InvoiceService;
import com.company.logicstic.modules.finance.service.PaymentService;
import com.company.logicstic.modules.fleet.dto.request.CreateTruckRequest;
import com.company.logicstic.modules.fleet.dto.response.TruckResponse;
import com.company.logicstic.modules.fleet.entity.Truck;
import com.company.logicstic.modules.fleet.repository.TruckRepository;
import com.company.logicstic.modules.fleet.service.TruckService;
import com.company.logicstic.modules.inspection.dto.request.CreateInspectionRequest;
import com.company.logicstic.modules.inspection.service.InspectionService;
import com.company.logicstic.modules.load.dto.request.CreateLoadRequest;
import com.company.logicstic.modules.load.dto.response.LoadResponse;
import com.company.logicstic.modules.load.entity.Load;
import com.company.logicstic.modules.load.entity.LoadStatus;
import com.company.logicstic.modules.load.repository.LoadRepository;
import com.company.logicstic.modules.load.service.LoadService;
import com.company.logicstic.modules.messaging.dto.request.SendMessageRequest;
import com.company.logicstic.modules.messaging.entity.Conversation;
import com.company.logicstic.modules.messaging.entity.ConversationParticipant;
import com.company.logicstic.modules.messaging.repository.ConversationRepository;
import com.company.logicstic.modules.messaging.service.ConversationService;
import com.company.logicstic.modules.messaging.service.MessageService;
import com.company.logicstic.modules.role.dto.request.CreateRoleRequest;
import com.company.logicstic.modules.role.dto.response.RoleResponse;
import com.company.logicstic.modules.role.repository.TenantRoleRepository;
import com.company.logicstic.modules.role.service.RoleService;
import com.company.logicstic.modules.terminal.entity.Terminal;
import com.company.logicstic.modules.trip.dto.request.CreateTripRequest;
import com.company.logicstic.modules.trip.repository.TripRepository;
import com.company.logicstic.modules.trip.service.TripService;
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

      int docsForThisLoad = FAKER.number().numberBetween(1, 4);
      for (int i = 0; i < docsForThisLoad; i++) {
        createAndPersistDocument(load, uploaderOpt.get());
      }
    }
  }

  private void seedInspections() {
    log.info("── Seeding Inspections ──");
    List<Load> loads = loadRepository.findAllById(loadIds);

    for (Load load : loads) {
      if (FAKER.random().nextInt(0, 9) >= 4) continue; // 60% chance to skip

      Optional<UUID> inspectorId = randomDriver();
      if (inspectorId.isEmpty()) continue;

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
              Double.parseDouble(FAKER.address().latitude()),
              Double.parseDouble(FAKER.address().longitude()),
              inspectedAt,
              inspectorId.get(),
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
    UUID truckId = randomTruck(tenantIndex).orElseThrow();
    UUID dispatcherId = randomDispatcher(tenantIndex).orElseThrow();

    String loadType = randomFromList(LOAD_TYPES).orElse("general_freight");
    boolean isHazmat = loadType.equals(HAZARDOUS_LOAD_TYPE);
    double distance = FAKER.number().randomDouble(1, 50, 1800);
    BigDecimal cost =
        BigDecimal.valueOf(FAKER.number().numberBetween(MIN_LOAD_COST, MAX_LOAD_COST));

    String status = determineLoadStatusForSeeding(loadNum);
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
            status,
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
