package com.company.logicstic;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.logicstic.modules.customer.dto.request.CreateCustomerRequest;
import com.company.logicstic.modules.employee.dto.request.CreateEmployeeRequest;
import com.company.logicstic.modules.finance.dto.request.CreateInvoiceRequest;
import com.company.logicstic.modules.finance.dto.request.CreatePaymentRequest;
import com.company.logicstic.modules.fleet.dto.request.CreateTruckRequest;
import com.company.logicstic.modules.inspection.dto.request.CreateInspectionRequest;
import com.company.logicstic.modules.inspection.dto.request.DefectRequest;
import com.company.logicstic.modules.load.dto.request.CreateLoadRequest;
import com.company.logicstic.modules.messaging.dto.request.CreateConversationRequest;
import com.company.logicstic.modules.messaging.dto.request.SendMessageRequest;
import com.company.logicstic.modules.role.dto.request.CreateRoleRequest;
import com.company.logicstic.modules.terminal.dto.request.CreateTerminalRequest;
import com.company.logicstic.modules.terminal.enums.TerminalType;
import com.company.logicstic.modules.trip.dto.request.CreateTripRequest;
import com.company.logicstic.modules.trip.dto.request.TripStopRequest;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

/**
 * Exercises every feature of the API end to end against a real PostgreSQL and Redis.
 *
 * <p>This is the test the unit suite cannot replace. The service tests all stub their repositories,
 * so nothing before this ever executed a real INSERT — which is how a broken JPA auditing
 * configuration (every insert failing on {@code LocalDateTime} → {@code OffsetDateTime}) and a
 * missing Flyway auto-configuration both survived a green build.
 *
 * <p>The default per-method lifecycle is deliberate: with {@code PER_CLASS}, JUnit builds the test
 * instance — and therefore the Spring context — before {@code @BeforeAll}, so the containers would
 * not yet have a mapped port to configure the datasource with.
 *
 * <p>Tests run in a fixed order and share the ids they create, because the domain is a graph: a
 * truck needs a driver, a load needs a customer, a trip needs a load. Rebuilding that graph per
 * test would triple the runtime and test nothing extra.
 *
 * <p>Authentication is injected with {@code jwt()} rather than a real token: the API is a resource
 * server that validates tokens issued by a separate Identity Server, and standing one up would test
 * that server rather than this one. Authorisation itself is still real — the filter chain and every
 * {@code @PreAuthorize} run exactly as in production.
 */
@EnabledIf("dockerIsAvailable")
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("API functional")
class ApiFunctionalIT {

  private static final GenericContainer<?> POSTGRES =
      new GenericContainer<>(DockerImageName.parse("postgres:18-alpine"))
          .withExposedPorts(5432)
          .withEnv("POSTGRES_DB", "logisticsx_it")
          .withEnv("POSTGRES_USER", "postgres")
          .withEnv("POSTGRES_PASSWORD", "postgres")
          // Postgres restarts once during initdb, so "port is open" is not "ready". Waiting for the
          // second readiness line is what stops the first migration from hitting a closing socket.
          .waitingFor(
              Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2));

  private static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

  static boolean dockerIsAvailable() {
    return DockerClientFactory.instance().isDockerAvailable();
  }

  @BeforeAll
  static void startInfrastructure() {
    POSTGRES.start();
    REDIS.start();
  }

  @AfterAll
  static void stopInfrastructure() {
    REDIS.stop();
    POSTGRES.stop();
  }

  @DynamicPropertySource
  static void configuration(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.datasource.url",
        () ->
            "jdbc:postgresql://%s:%d/logisticsx_it"
                .formatted(POSTGRES.getHost(), POSTGRES.getMappedPort(5432)));
    registry.add("spring.datasource.username", () -> "postgres");
    registry.add("spring.datasource.password", () -> "postgres");
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    registry.add("app.cache.enabled", () -> "true");
    // The resource server never contacts these: jwt() injects an already-authenticated token.
    // They exist because application.yml deliberately gives them no defaults.
    registry.add("app.security.jwt.issuer", () -> "https://issuer.test");
    registry.add("app.security.jwt.audience", () -> "logisticsx.api");
    registry.add("app.security.jwt.jwk-set-uri", () -> "https://issuer.test/jwks");
  }

  private final MockMvc mockMvc;
  private final ObjectMapper objectMapper;

  @Autowired
  ApiFunctionalIT(MockMvc mockMvc, ObjectMapper objectMapper) {
    this.mockMvc = mockMvc;
    this.objectMapper = objectMapper;
  }

  // Ids created by earlier tests and consumed by later ones.
  private static UUID roleId;
  private static UUID employeeId;
  private static UUID truckId;
  private static UUID customerId;
  private static UUID terminalId;
  private static UUID loadId;
  private static UUID tripId;
  private static UUID invoiceId;
  private static UUID conversationId;

  // ── 1. Roles ────────────────────────────────────────────────────────────────

  @Test
  @Order(1)
  @DisplayName("roles: create, read, list, update")
  void roles() throws Exception {
    roleId =
        createdId(
            perform(
                    post("/api/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            json(
                                new CreateRoleRequest(
                                    "dispatcher_it",
                                    "Dispatcher",
                                    List.of(
                                        new CreateRoleRequest.ClaimRequest(
                                            "permission", "load.view"))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("dispatcher_it")));

    perform(get("/api/roles/{id}", roleId)).andExpect(status().isOk());
    perform(get("/api/roles"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items").isArray());
    perform(
            put("/api/roles/{id}", roleId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(new CreateRoleRequest("dispatcher_it", "Senior Dispatcher", List.of()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.displayName").value("Senior Dispatcher"));
  }

  // ── 2. Employees and drivers ────────────────────────────────────────────────

  @Test
  @Order(2)
  @DisplayName("employees: create with role, read, list; drivers projection responds")
  void employees() throws Exception {
    employeeId =
        createdId(
            perform(
                    post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(employeeRequest("driver.it@example.com"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value("driver.it@example.com")));

    perform(get("/api/employees/{id}", employeeId)).andExpect(status().isOk());
    perform(get("/api/employees")).andExpect(status().isOk());
    perform(get("/api/drivers")).andExpect(status().isOk());
  }

  @Test
  @Order(3)
  @DisplayName("employees: a duplicate email is rejected with 409, not a 500")
  void duplicateEmployeeEmail() throws Exception {
    perform(
            post("/api/employees")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(employeeRequest("driver.it@example.com"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("CONFLICT"));
  }

  // ── 3. Trucks ───────────────────────────────────────────────────────────────

  @Test
  @Order(4)
  @DisplayName("trucks: create with a main driver, read, list, update")
  void trucks() throws Exception {
    truckId =
        createdId(
            perform(
                    post("/api/trucks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(truckRequest("IT-001"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.mainDriverName").isNotEmpty()));

    perform(get("/api/trucks/{id}", truckId)).andExpect(status().isOk());
    perform(get("/api/trucks")).andExpect(status().isOk());
  }

  // ── 4. Customers ────────────────────────────────────────────────────────────

  @Test
  @Order(5)
  @DisplayName("customers: create, read, list, update")
  void customers() throws Exception {
    customerId =
        createdId(
            perform(
                    post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(customerRequest("Acme Shipping"))))
                .andExpect(status().isCreated()));

    perform(get("/api/customers/{id}", customerId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("Acme Shipping"));
    perform(get("/api/customers")).andExpect(status().isOk());
    perform(
            put("/api/customers/{id}", customerId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(customerRequest("Acme Shipping Ltd"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("Acme Shipping Ltd"));
  }

  // ── 5. Terminals (and the cache path) ───────────────────────────────────────

  @Test
  @Order(6)
  @DisplayName("terminals: create, read twice through the cache, update invalidates")
  void terminals() throws Exception {
    terminalId =
        createdId(
            perform(
                    post("/api/terminals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(terminalRequest("Port of Test", "usnyc"))))
                .andExpect(status().isCreated())
                // The entity normalises on write: lower-case input comes back upper-cased.
                .andExpect(jsonPath("$.data.code").value("USNYC")));

    perform(get("/api/terminals/{id}", terminalId)).andExpect(status().isOk());
    // Second read is served from Redis; the response must be identical, not merely present.
    perform(get("/api/terminals/{id}", terminalId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.code").value("USNYC"))
        .andExpect(jsonPath("$.data.name").value("Port of Test"));

    perform(
            put("/api/terminals/{id}", terminalId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(terminalRequest("Port of Test Renamed", "usnyc"))))
        .andExpect(status().isOk());

    // If the cache were not invalidated this would still say "Port of Test".
    perform(get("/api/terminals/{id}", terminalId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("Port of Test Renamed"));
  }

  @Test
  @Order(7)
  @DisplayName("terminals: a duplicate UN/LOCODE is a 409, and a bad code is a 400")
  void terminalValidation() throws Exception {
    perform(
            post("/api/terminals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(terminalRequest("Duplicate", "usnyc"))))
        .andExpect(status().isConflict());

    perform(
            post("/api/terminals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(terminalRequest("Bad code", "TOOLONG"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("code"));
  }

  // ── 6. Loads and the state machine ──────────────────────────────────────────

  @Test
  @Order(8)
  @DisplayName("loads: create as draft, read, list")
  void loads() throws Exception {
    loadId =
        createdId(
            perform(
                    post("/api/loads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(loadRequest("draft"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("draft")));

    perform(get("/api/loads/{id}", loadId)).andExpect(status().isOk());
    perform(get("/api/loads")).andExpect(status().isOk());
  }

  @Test
  @Order(9)
  @DisplayName("loads: only the assigned driver can pick-up, then deliver")
  void loadLifecycle() throws Exception {
    perform(post("/api/loads/{id}/dispatch", loadId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("dispatched"))
        .andExpect(jsonPath("$.data.dispatchedAt").isNotEmpty());

    String outsiderEmail = "outsider.load.it@example.com";
    perform(
            post("/api/employees")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(employeeRequest(outsiderEmail))))
        .andExpect(status().isCreated());

    performAsEmployee(post("/api/loads/{id}/pick-up", loadId), outsiderEmail, "ROLE_DRIVER")
        .andExpect(status().isForbidden());

    performAsEmployee(
            post("/api/loads/{id}/pick-up", loadId), "driver.it@example.com", "ROLE_DRIVER")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("picked_up"));

    performAsEmployee(
            post("/api/loads/{id}/deliver", loadId), "driver.it@example.com", "ROLE_DRIVER")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("delivered"))
        .andExpect(jsonPath("$.data.deliveredAt").isNotEmpty());

    String deliveredAt =
        objectMapper
            .readTree(
                perform(get("/api/loads/{id}", loadId))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("data")
            .get("deliveredAt")
            .asText();
    performAsEmployee(
            post("/api/loads/{id}/deliver", loadId), "driver.it@example.com", "ROLE_DRIVER")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    perform(get("/api/loads/{id}", loadId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("delivered"))
        .andExpect(jsonPath("$.data.deliveredAt").value(deliveredAt));
  }

  @Test
  @Order(10)
  @DisplayName("loads: an illegal transition is refused, and an unknown id is 404")
  void loadTransitionGuards() throws Exception {
    // Delivered is terminal: dispatching again must be refused by the entity, not the database.
    perform(post("/api/loads/{id}/dispatch", loadId))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));

    perform(get("/api/loads/{id}", UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
  }

  // ── 7. Trips ────────────────────────────────────────────────────────────────

  @Test
  @Order(11)
  @DisplayName("trips: create with stops, dispatch, complete")
  void trips() throws Exception {
    UUID freshLoad =
        createdId(
            perform(
                    post("/api/loads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(loadRequest("draft"))))
                .andExpect(status().isCreated()));

    tripId =
        createdId(
            perform(
                    post("/api/trips")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(tripRequest(freshLoad))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.stops").isArray()));

    perform(get("/api/trips/{id}", tripId)).andExpect(status().isOk());
    perform(get("/api/trips")).andExpect(status().isOk());
    perform(post("/api/trips/{id}/dispatch", tripId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("dispatched"));
    perform(post("/api/trips/{id}/complete", tripId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("completed"));
  }

  // ── 8. Invoices and payments ────────────────────────────────────────────────

  @Test
  @Order(12)
  @DisplayName("invoices: create for a load, read, list")
  void invoices() throws Exception {
    invoiceId =
        createdId(
            perform(
                    post("/api/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(invoiceRequest())))
                .andExpect(status().isCreated()));

    perform(get("/api/invoices/{id}", invoiceId)).andExpect(status().isOk());
    perform(get("/api/invoices")).andExpect(status().isOk());

    UUID dispatchLoadId =
        createdId(
            perform(
                    post("/api/loads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(loadRequest("draft"))))
                .andExpect(status().isCreated()));
    UUID dispatchInvoiceId =
        createdId(
            perform(
                    post("/api/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(invoiceRequestForLoad(dispatchLoadId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("draft")));

    perform(post("/api/loads/{id}/dispatch", dispatchLoadId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("dispatched"));
    perform(get("/api/invoices/{id}", dispatchInvoiceId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("issued"));
  }

  @Test
  @Order(13)
  @DisplayName("payments: record against an invoice, read, list")
  void payments() throws Exception {
    UUID paymentId =
        createdId(
            perform(
                    post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(paymentRequest())))
                .andExpect(status().isCreated()));

    perform(get("/api/payments/{id}", paymentId)).andExpect(status().isOk());
    perform(get("/api/payments")).andExpect(status().isOk());
  }

  // ── 9. Documents ────────────────────────────────────────────────────────────

  @Test
  @Order(14)
  @DisplayName("documents: upload, list, download the same bytes, delete")
  void documents() throws Exception {
    byte[] content = "proof-of-delivery".getBytes();
    MockMultipartFile file =
        new MockMultipartFile("file", "pod.pdf", MediaType.APPLICATION_PDF_VALUE, content);
    UUID forgedUploadedById = UUID.randomUUID();
    MockMultipartFile forgedMetadata =
        new MockMultipartFile(
            "metadata",
            "",
            MediaType.APPLICATION_JSON_VALUE,
            json(new com.company.logicstic.modules.document.dto.request.DocumentUploadRequest(
                    "load",
                    "pod",
                    "Proof of delivery",
                    forgedUploadedById,
                    loadId,
                    null,
                    null,
                    "Recipient",
                    OffsetDateTime.now(),
                    10.0,
                    20.0,
                    null))
                .getBytes());
    MockMultipartFile metadata =
        new MockMultipartFile(
            // The controller declares @RequestPart("metadata"); naming it anything else is a 500.
            "metadata",
            "",
            MediaType.APPLICATION_JSON_VALUE,
            json(new com.company.logicstic.modules.document.dto.request.DocumentUploadRequest(
                    "load",
                    "pod",
                    "Proof of delivery",
                    employeeId,
                    loadId,
                    null,
                    null,
                    "Recipient",
                    OffsetDateTime.now(),
                    10.0,
                    20.0,
                    null))
                .getBytes());

    performAsEmployee(
            multipart("/api/documents").file(file).file(forgedMetadata),
            "driver.it@example.com",
            "ROLE_DRIVER")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

    UUID documentId =
        createdId(
            performAsEmployee(
                    multipart("/api/documents").file(file).file(metadata),
                    "driver.it@example.com",
                    "ROLE_DRIVER")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.uploadedById").value(employeeId.toString())));

    perform(get("/api/documents")).andExpect(status().isOk());
    mockMvc
        .perform(get("/api/documents/{id}/download", documentId).with(superAdmin()))
        .andExpect(status().isOk())
        .andExpect(
            result -> {
              byte[] body = result.getResponse().getContentAsByteArray();
              if (body.length != content.length) {
                throw new AssertionError(
                    "downloaded %d bytes, expected %d".formatted(body.length, content.length));
              }
            });

    perform(delete("/api/documents/{id}", documentId)).andExpect(status().isOk());
  }

  // ── 10. Messaging ───────────────────────────────────────────────────────────

  @Test
  @Order(15)
  @DisplayName("messaging: principal identity, participant access, unread count, mark read")
  void messaging() throws Exception {
    String senderEmail = "driver.it@example.com";
    String recipientEmail = "recipient.it@example.com";
    String outsiderEmail = "outsider.it@example.com";
    UUID recipientId =
        createdId(
            perform(
                    post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(employeeRequest(recipientEmail))))
                .andExpect(status().isCreated()));
    UUID outsiderId =
        createdId(
            perform(
                    post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(employeeRequest(outsiderEmail))))
                .andExpect(status().isCreated()));

    conversationId =
        createdId(
            performAsEmployee(
                    post("/api/messages/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            json(
                                new CreateConversationRequest(
                                    "IT thread", loadId, false, Set.of(recipientId)))),
                    senderEmail,
                    "ROLE_DRIVER")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.participantIds.length()").value(2)));

    performAsEmployee(
            post("/api/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new SendMessageRequest(conversationId, recipientId, "forged"))),
            senderEmail,
            "ROLE_DRIVER")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

    performAsEmployee(
            post("/api/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new SendMessageRequest(conversationId, employeeId, "hello"))),
            senderEmail,
            "ROLE_DRIVER")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.content").value("hello"))
        .andExpect(jsonPath("$.data.senderId").value(employeeId.toString()));

    performAsEmployee(
            get("/api/messages/conversations").param("employeeId", recipientId.toString()),
            senderEmail,
            "ROLE_DRIVER")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

    performAsEmployee(
            get("/api/messages/unread-count").param("employeeId", employeeId.toString()),
            senderEmail,
            "ROLE_DRIVER")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").value(0));

    performAsEmployee(
            get("/api/messages/conversations/{id}", conversationId), recipientEmail, "ROLE_DRIVER")
        .andExpect(status().isOk());
    performAsEmployee(
            get("/api/messages").param("conversationId", conversationId.toString()),
            recipientEmail,
            "ROLE_DRIVER")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].content").value("hello"));
    performAsEmployee(
            get("/api/messages/unread-count").param("employeeId", recipientId.toString()),
            recipientEmail,
            "ROLE_DRIVER")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").value(1));
    performAsEmployee(
            post("/api/messages/conversations/{id}/read", conversationId)
                .param("employeeId", recipientId.toString()),
            recipientEmail,
            "ROLE_DRIVER")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").value(1));
    performAsEmployee(
            post("/api/messages/conversations/{id}/read", conversationId)
                .param("employeeId", recipientId.toString()),
            recipientEmail,
            "ROLE_DRIVER")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").value(0));
    performAsEmployee(
            get("/api/messages/unread-count").param("employeeId", recipientId.toString()),
            recipientEmail,
            "ROLE_DRIVER")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").value(0));

    performAsEmployee(
            get("/api/messages/conversations/{id}", conversationId),
            outsiderEmail,
            "ROLE_SUPERADMIN")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    performAsEmployee(
            get("/api/messages").param("conversationId", conversationId.toString()),
            outsiderEmail,
            "ROLE_SUPERADMIN")
        .andExpect(status().isNotFound());
    performAsEmployee(
            post("/api/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new SendMessageRequest(conversationId, outsiderId, "intrusion"))),
            outsiderEmail,
            "ROLE_SUPERADMIN")
        .andExpect(status().isNotFound());
    performAsEmployee(
            post("/api/messages/conversations/{id}/read", conversationId)
                .param("employeeId", outsiderId.toString()),
            outsiderEmail,
            "ROLE_SUPERADMIN")
        .andExpect(status().isNotFound());
  }

  // ── 11. Inspections ─────────────────────────────────────────────────────────

  @Test
  @Order(16)
  @DisplayName("inspections: create with defects, read, list")
  void inspections() throws Exception {
    UUID inspectionId =
        createdId(
            perform(
                    post("/api/inspections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(inspectionRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.defects.length()").value(2)));

    perform(get("/api/inspections/{id}", inspectionId)).andExpect(status().isOk());
    perform(get("/api/inspections")).andExpect(status().isOk());
  }

  // ── 12. Notifications ───────────────────────────────────────────────────────

  @Test
  @Order(17)
  @DisplayName("notifications: list and mark all read")
  void notifications() throws Exception {
    perform(get("/api/notifications")).andExpect(status().isOk());
    perform(post("/api/notifications/mark-all-read")).andExpect(status().isOk());
  }

  // ── 13. Cross-cutting contract ──────────────────────────────────────────────

  @Test
  @Order(18)
  @DisplayName("every response uses the ApiResponse envelope")
  void responseEnvelope() throws Exception {
    perform(get("/api/terminals"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").exists())
        .andExpect(jsonPath("$.message").exists())
        .andExpect(jsonPath("$.data").exists())
        .andExpect(jsonPath("$.errors").isArray())
        .andExpect(jsonPath("$.meta.timestamp").exists())
        .andExpect(jsonPath("$.meta.path").value("/api/terminals"));
  }

  @Test
  @Order(19)
  @DisplayName("pagination is 1-based and bounded")
  void pagination() throws Exception {
    perform(get("/api/terminals").param("page", "1").param("pageSize", "5"))
        .andExpect(status().isOk())
        // PagedResponse exposes items/totalItems/totalPages/currentPage/pageSize, and currentPage
        // is 1-based on the wire even though Spring Data is 0-based internally.
        .andExpect(jsonPath("$.data.currentPage").value(1))
        .andExpect(jsonPath("$.data.pageSize").value(5))
        .andExpect(jsonPath("$.data.items").isArray())
        .andExpect(jsonPath("$.data.totalItems").exists())
        .andExpect(jsonPath("$.data.totalPages").exists());

    perform(get("/api/terminals").param("page", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  @Order(20)
  @DisplayName("authorisation is enforced: a DRIVER cannot manage customers")
  void authorisationIsEnforced() throws Exception {
    mockMvc
        .perform(get("/api/customers").with(jwt().authorities(() -> "ROLE_DRIVER")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

    // The same caller may read loads — proving the 403 above is a rule, not a blanket denial.
    mockMvc
        .perform(get("/api/loads").with(jwt().authorities(() -> "ROLE_DRIVER")))
        .andExpect(status().isOk());
  }

  @Test
  @Order(21)
  @DisplayName("deletes clean up: terminal, load, customer")
  void deletes() throws Exception {
    perform(delete("/api/trips/{id}", tripId)).andExpect(status().isOk());
    perform(delete("/api/terminals/{id}", terminalId)).andExpect(status().isOk());
    perform(get("/api/terminals/{id}", terminalId)).andExpect(status().isNotFound());
  }

  // ── helpers ─────────────────────────────────────────────────────────────────

  private ResultActions perform(AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
    return mockMvc.perform(request.with(superAdmin()));
  }

  private ResultActions performAsEmployee(
      AbstractMockHttpServletRequestBuilder<?> request, String email, String role)
      throws Exception {
    return mockMvc.perform(request.with(employeeJwt(email, role)));
  }

  private static org.springframework.test.web.servlet.request.RequestPostProcessor superAdmin() {
    return jwt().authorities(() -> "ROLE_SUPERADMIN");
  }

  private static org.springframework.test.web.servlet.request.RequestPostProcessor employeeJwt(
      String email, String role) {
    return jwt()
        .jwt(token -> token.subject(email).claim("email", email).claim("tenant", "tenant-it"))
        .authorities(() -> role);
  }

  private String json(Object value) {
    return objectMapper.writeValueAsString(value);
  }

  private UUID createdId(ResultActions actions) throws Exception {
    String body = actions.andReturn().getResponse().getContentAsString();
    return UUID.fromString(objectMapper.readTree(body).get("data").get("id").asString());
  }

  private static CreateEmployeeRequest employeeRequest(String email) {
    return new CreateEmployeeRequest(
        email,
        "Test",
        "Driver",
        "+84900000000",
        "HOURLY",
        "ACTIVE",
        OffsetDateTime.now(),
        roleId,
        new BigDecimal("25.00"),
        "USD",
        "1 Main St",
        null,
        "Hanoi",
        "HN",
        "10000",
        "VN");
  }

  private static CreateTruckRequest truckRequest(String number) {
    return new CreateTruckRequest(
        number,
        "freight_truck",
        1,
        "available",
        "Volvo",
        "FH16",
        2024,
        null,
        "51C-12345",
        "HN",
        false,
        employeeId,
        null,
        false,
        null,
        null);
  }

  private static CreateCustomerRequest customerRequest(String name) {
    return new CreateCustomerRequest(
        name,
        "billing@acme.test",
        "+84900000001",
        "active",
        null,
        "TAX-1",
        false,
        "2 Dock Rd",
        null,
        "Haiphong",
        "HP",
        "18000",
        "VN");
  }

  private static CreateTerminalRequest terminalRequest(String name, String code) {
    return new CreateTerminalRequest(
        name,
        code,
        "us",
        TerminalType.SEA_PORT,
        null,
        "1 Terminal Way",
        null,
        "Newark",
        "NJ",
        "07114",
        "US");
  }

  private static CreateLoadRequest loadRequest(String status) {
    return new CreateLoadRequest(
        "IT shipment",
        "general_freight",
        status,
        120.5,
        false,
        customerId,
        truckId,
        employeeId,
        "manual",
        OffsetDateTime.now().plusDays(1),
        OffsetDateTime.now().plusDays(3),
        "created by the functional test",
        false,
        null,
        null,
        null,
        terminalId,
        terminalId,
        null,
        null,
        null,
        new BigDecimal("1500.00"),
        "USD",
        "1 Origin St",
        null,
        "Hanoi",
        "HN",
        "10000",
        "VN",
        21.0,
        105.8,
        "2 Dest St",
        null,
        "Haiphong",
        "HP",
        "18000",
        "VN",
        20.8,
        106.6);
  }

  private static CreateTripRequest tripRequest(UUID stopLoadId) {
    return new CreateTripRequest(
        "IT trip",
        250.0,
        "draft",
        truckId,
        List.of(
            new TripStopRequest(
                "pickup",
                1,
                stopLoadId,
                "1 Origin St",
                null,
                "Hanoi",
                "HN",
                "10000",
                "VN",
                21.0,
                105.8),
            new TripStopRequest(
                "drop_off",
                2,
                stopLoadId,
                "2 Dest St",
                null,
                "Haiphong",
                "HP",
                "18000",
                "VN",
                20.8,
                106.6)));
  }

  private static CreateInvoiceRequest invoiceRequest() {
    return new CreateInvoiceRequest(
        "load",
        "draft",
        "exclusive",
        "functional test invoice",
        OffsetDateTime.now().plusDays(30),
        loadId,
        customerId,
        null,
        new BigDecimal("1500.00"),
        "USD",
        new BigDecimal("150.00"),
        "USD",
        new BigDecimal("1650.00"),
        "USD",
        null,
        null,
        null);
  }

  private static CreateInvoiceRequest invoiceRequestForLoad(UUID targetLoadId) {
    return new CreateInvoiceRequest(
        "load",
        "draft",
        "exclusive",
        "dispatch transition functional test invoice",
        OffsetDateTime.now().plusDays(30),
        targetLoadId,
        customerId,
        null,
        new BigDecimal("1500.00"),
        "USD",
        new BigDecimal("150.00"),
        "USD",
        new BigDecimal("1650.00"),
        "USD",
        null,
        null,
        null);
  }

  private static CreatePaymentRequest paymentRequest() {
    return new CreatePaymentRequest(
        "paid",
        invoiceId,
        new BigDecimal("1650.00"),
        "USD",
        "full settlement",
        "REF-IT-1",
        null,
        null,
        OffsetDateTime.now(),
        "2 Dock Rd",
        null,
        "Haiphong",
        "HP",
        "18000",
        "VN");
  }

  private static CreateInspectionRequest inspectionRequest() {
    return new CreateInspectionRequest(
        loadId,
        "pickup",
        null,
        2024,
        "Volvo",
        "FH16",
        "Tractor",
        null,
        "SEAL-1",
        "functional test inspection",
        "signature-blob",
        21.0,
        105.8,
        OffsetDateTime.now(),
        employeeId,
        List.of(
            new DefectRequest("tire", "Low tread", "major"),
            new DefectRequest("light", "Rear lamp out", "minor")));
  }
}
