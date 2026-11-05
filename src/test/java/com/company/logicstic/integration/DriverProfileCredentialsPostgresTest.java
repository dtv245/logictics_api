package com.company.logicstic.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.company.logicstic.entity.DriverLicense;
import com.company.logicstic.service.DriverLicensePiiBackfill;
import com.company.logicstic.service.DriverPiiCipher;
import com.company.logicstic.integration.lark.auth.LarkAuthService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

/** PostgreSQL/Flyway plus real Spring Security, tenant claims, controller and JPA behavior. */
@SpringBootTest(properties = {
        "spring.config.import=", "spring.flyway.enabled=true", "spring.flyway.locations=classpath:db/migration/tenant",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.open-in-view=false",
        "app.tenancy.enabled=false", "app.lark.base.enabled=false",
        "app.driver-profile.expiry-scan.enabled=false",
        "app.lark.jwt-secret=driver-profile-test-signing-key-012345678901234567890",
        "app.driver-profile.pii.active-key-id=test-v1",
        "app.driver-profile.pii.encryption-keys=test-v1:"
                + "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=",
        "app.driver-profile.pii.index-key=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE="
})
class DriverProfileCredentialsPostgresTest {
    private static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("driver_profile");
    static { DATABASE.start(); }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", DATABASE::getJdbcUrl);
        properties.add("spring.datasource.username", DATABASE::getUsername);
        properties.add("spring.datasource.password", DATABASE::getPassword);
    }

    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired javax.sql.DataSource dataSource;
    @Autowired LarkAuthService tokens;
    @Autowired DriverPiiCipher piiCipher;
    @Autowired DriverLicensePiiBackfill piiBackfill;
    @Autowired Flyway flyway;
    private final JsonMapper json = JsonMapper.builder().build();

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void licenseCrudMasksNormalViewsAndRestrictsCleartextAndEmployeeDeletion() throws Exception {
        Fixture driver = employee("DRIVER");
        String licenseNumber = "TEST-VN-1234567";
        String path = "/api/drivers/" + driver.id() + "/licenses";

        var created = mvc().perform(post(path).header("Authorization", token("DISPATCHER", "dispatcher@example.test"))
                        .contentType("application/json").content(licenseBody(licenseNumber, 12)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.licenseNumber").value("****4567"))
                .andExpect(jsonPath("$.data.remainingPoints").value(12))
                .andExpect(jsonPath("$.data.active").value(true))
                .andReturn().getResponse();
        String createdBody = created.getContentAsString();
        assertFalse(createdBody.contains(licenseNumber));
        UUID licenseId = UUID.fromString(json.readTree(createdBody).path("data").path("id").asString());

        Map<String, Object> stored = jdbc.queryForMap(
                "select license_number, license_number_hmac from driver_licenses where id = ?", licenseId);
        String ciphertext = (String) stored.get("license_number");
        assertTrue(ciphertext.startsWith("DLENC:v1:test-v1:"));
        assertFalse(ciphertext.contains(licenseNumber));
        assertEquals(64, ((String) stored.get("license_number_hmac")).length());

        mvc().perform(get(path).param("page", "1").param("pageSize", "1")
                        .header("Authorization", token("DISPATCHER", "dispatcher@example.test")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].licenseNumber").value("****4567"))
                .andExpect(jsonPath("$.data.pageSize").value(1));
        mvc().perform(get(path + "/" + licenseId)
                        .header("Authorization", token("DISPATCHER", "dispatcher@example.test")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.licenseNumber").value("****4567"));

        mvc().perform(get(path + "/" + licenseId + "/sensitive")
                        .header("Authorization", token("DISPATCHER", "dispatcher@example.test")))
                .andExpect(status().isForbidden());
        mvc().perform(get(path + "/" + licenseId + "/sensitive")
                        .header("Authorization", token("ADMIN", "admin@example.test")))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.licenseNumber").value(licenseNumber));

        mvc().perform(put(path + "/" + licenseId)
                        .header("Authorization", token("DISPATCHER", "dispatcher@example.test"))
                        .contentType("application/json").content(updateLicenseBody(null, false)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.licenseNumber").value("****4567"))
                .andExpect(jsonPath("$.data.active").value(false));
        assertEquals(licenseNumber, piiCipher.decryptLicenseNumber(
                jdbc.queryForObject("select license_number from driver_licenses where id = ?", String.class, licenseId),
                driver.id(), "VN"));

        mvc().perform(delete(path + "/" + licenseId)
                        .header("Authorization", token("ADMIN", "admin@example.test")))
                .andExpect(status().isNoContent());
        mvc().perform(delete("/api/drivers/" + driver.id())
                        .header("Authorization", token("ADMIN", "admin@example.test")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DRIVER_HAS_COMPLIANCE_RECORDS"));
        mvc().perform(delete("/api/employees/" + driver.id())
                        .header("Authorization", token("ADMIN", "admin@example.test")))
                .andExpect(status().isConflict());
        mvc().perform(get(path + "/" + UUID.randomUUID())
                        .header("Authorization", token("ADMIN", "admin@example.test")))
                .andExpect(status().isNotFound());
        assertTrue(flyway.validateWithResult().validationSuccessful);
    }

    @Test
    void driverDirectoryFiltersByEmployeeTypeWhenLegacyRoleDisagrees() throws Exception {
        String marker = "employee-type-" + UUID.randomUUID();
        Fixture typeDriver = employee("ADMIN");
        Fixture roleOnly = employee("DRIVER");
        jdbc.update("update employees set email = ?, employee_type = 'DRIVER', role_id = null where id = ?",
                marker + "-typed@example.test", typeDriver.id());
        jdbc.update("update employees set email = ?, employee_type = 'OFFICE_STAFF' where id = ?",
                marker + "-role@example.test", roleOnly.id());

        mvc().perform(get("/api/drivers").param("search", marker)
                        .header("Authorization", token("ADMIN", "admin@example.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(typeDriver.id().toString()));
    }

    @Test
    void selfRoutesUseOnlyPrincipalEmployeeAndASecondDriverCannotReadTheirRoute() throws Exception {
        Fixture driverA = employee("DRIVER");
        Fixture driverB = employee("DRIVER");
        createLicense(driverA, "SELF-A-0001");
        createLicense(driverB, "SELF-B-0002");

        mvc().perform(get("/api/driver/me/licenses").param("driverId", driverB.id().toString())
                        .header("Authorization", tokenFor(driverA, "DRIVER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.items[0].driverId").value(driverA.id().toString()))
                .andExpect(jsonPath("$.data.items[0].licenseNumber").value("****0001"));
        mvc().perform(get("/api/drivers/" + driverB.id() + "/licenses")
                        .header("Authorization", tokenFor(driverA, "DRIVER")))
                .andExpect(status().isForbidden());
        mvc().perform(get("/api/driver/me/licenses"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthChecksAndCertificationsValidateDatesEncryptHealthConclusionAndKeepHistory() throws Exception {
        Fixture driver = employee("DRIVER");
        String base = "/api/drivers/" + driver.id();
        String admin = token("ADMIN", "admin@example.test");
        String healthBody = """
                {"checkedDate":"2025-01-02","expiryDate":"2026-01-02","facility":"Phòng khám vận tải",
                 "conclusion":"FIT"}
                """;

        var healthResponse = mvc().perform(post(base + "/health-checks").header("Authorization", admin)
                        .contentType("application/json").content(healthBody))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.conclusion").value("FIT"))
                .andReturn().getResponse();
        UUID healthId = UUID.fromString(json.readTree(healthResponse.getContentAsString()).path("data").path("id").asString());
        String storedConclusion = jdbc.queryForObject(
                "select conclusion from driver_health_checks where id = ?", String.class, healthId);
        assertNotEquals("FIT", storedConclusion);
        assertTrue(storedConclusion.startsWith("DLENC:v1:test-v1:"));

        mvc().perform(get("/api/driver/me/health-checks").header("Authorization", tokenFor(driver, "DRIVER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].conclusion").value("FIT"));
        mvc().perform(post(base + "/health-checks").header("Authorization", admin).contentType("application/json")
                        .content("{\"checkedDate\":\"2025-01-02\",\"expiryDate\":\"2025-01-02\",\"facility\":\"Clinic\",\"conclusion\":\"FIT\"}"))
                .andExpect(status().isBadRequest());
        mvc().perform(delete(base + "/health-checks/" + healthId).header("Authorization", admin))
                .andExpect(status().isNoContent());
        assertEquals("inactive", jdbc.queryForObject("select status from driver_health_checks where id = ?", String.class, healthId));

        mvc().perform(post(base + "/certifications").header("Authorization", admin).contentType("application/json")
                        .content("{\"type\":\"SAFETY\",\"name\":\"An toàn vận tải\",\"issuedDate\":\"2025-01-02\",\"expiryDate\":\"2027-01-02\",\"issuer\":\"Trung tâm đào tạo\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.type").value("SAFETY"));
        mvc().perform(post(base + "/certifications").header("Authorization", admin).contentType("application/json")
                        .content("{\"type\":\"UNKNOWN\",\"name\":\"Bad\",\"issuedDate\":\"2025-01-02\"}"))
                .andExpect(status().isBadRequest());
        mvc().perform(get(base + "/certifications?orderBy=licenseNumber")
                        .header("Authorization", token("DISPATCHER", "dispatcher@example.test")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void driverProfileMasksIdentifiersAndRestrictsFullViewToAdmin() throws Exception {
        Fixture driverA = employee("DRIVER");
        Fixture driverB = employee("DRIVER");
        String path = "/api/drivers/" + driverA.id() + "/profile";
        String nationalId = "079123456789";
        String taxCode = "1234567890";

        var saved = mvc().perform(put(path).header("Authorization", token("DISPATCHER", "dispatcher@example.test"))
                        .contentType("application/json").content(profileBody(nationalId, taxCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nationalIdNumber").value("****6789"))
                .andExpect(jsonPath("$.data.personalTaxCode").value("****7890"))
                .andReturn().getResponse();
        String response = saved.getContentAsString();
        assertFalse(response.contains(nationalId));
        assertFalse(response.contains(taxCode));

        Map<String, Object> encrypted = jdbc.queryForMap(
                "select national_id_number, personal_tax_code from driver_profiles where employee_id = ?", driverA.id());
        assertTrue(((String) encrypted.get("national_id_number")).startsWith("DLENC:v1:test-v1:"));
        assertTrue(((String) encrypted.get("personal_tax_code")).startsWith("DLENC:v1:test-v1:"));
        assertFalse(((String) encrypted.get("national_id_number")).contains(nationalId));

        mvc().perform(get(path).header("Authorization", token("DISPATCHER", "dispatcher@example.test")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.nationalIdNumber").value("****6789"));
        mvc().perform(get(path + "/sensitive").header("Authorization", token("DISPATCHER", "dispatcher@example.test")))
                .andExpect(status().isForbidden());
        mvc().perform(get(path + "/sensitive").header("Authorization", token("ADMIN", "admin@example.test")))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.nationalIdNumber").value(nationalId))
                .andExpect(jsonPath("$.data.personalTaxCode").value(taxCode));

        mvc().perform(put(path).header("Authorization", token("DISPATCHER", "dispatcher@example.test"))
                        .contentType("application/json").content(profileBody("****6789", "****7890")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.nationalIdNumber").value("****6789"));
        mvc().perform(get("/api/driver/me/profile").header("Authorization", tokenFor(driverA, "DRIVER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.driverId").value(driverA.id().toString()))
                .andExpect(jsonPath("$.data.nationalIdNumber").value("****6789"));
        mvc().perform(get("/api/driver/me/profile").header("Authorization", tokenFor(driverB, "DRIVER")))
                .andExpect(status().isNotFound());
        mvc().perform(get("/api/drivers/" + driverB.id() + "/profile")
                        .header("Authorization", tokenFor(driverA, "DRIVER")))
                .andExpect(status().isForbidden());

        Fixture duplicateDriver = employee("DRIVER");
        mvc().perform(put("/api/drivers/" + duplicateDriver.id() + "/profile")
                        .header("Authorization", token("ADMIN", "admin@example.test"))
                        .contentType("application/json").content(profileBody(nationalId, "9876543210")))
                .andExpect(status().isConflict());
        mvc().perform(get("/api/drivers/" + driverB.id() + "/profile")
                        .header("Authorization", token("ADMIN", "admin@example.test")))
                .andExpect(status().isNotFound());
        mvc().perform(put("/api/drivers/" + driverB.id() + "/profile")
                        .header("Authorization", token("ADMIN", "admin@example.test"))
                        .contentType("application/json").content(profileBody(null, null, "2099-01-01")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void emergencyContactsPageEnforceSinglePrimaryAndAreDriverSelfReadOnly() throws Exception {
        Fixture driverA = employee("DRIVER");
        Fixture driverB = employee("DRIVER");
        String pathA = "/api/drivers/" + driverA.id() + "/emergency-contacts";
        String pathB = "/api/drivers/" + driverB.id() + "/emergency-contacts";
        String admin = token("ADMIN", "admin@example.test");

        var firstResponse = mvc().perform(post(pathA).header("Authorization", admin).contentType("application/json")
                        .content(emergencyContactBody("Nguyễn An", true)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.primary").value(true))
                .andReturn().getResponse();
        UUID firstId = UUID.fromString(json.readTree(firstResponse.getContentAsString()).path("data").path("id").asString());
        var secondResponse = mvc().perform(post(pathA).header("Authorization", admin).contentType("application/json")
                        .content(emergencyContactBody("Trần Bình", true)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.primary").value(true))
                .andReturn().getResponse();
        UUID secondId = UUID.fromString(json.readTree(secondResponse.getContentAsString()).path("data").path("id").asString());
        assertFalse(jdbc.queryForObject("select is_primary from driver_emergency_contacts where id = ?", Boolean.class, firstId));
        assertTrue(jdbc.queryForObject("select is_primary from driver_emergency_contacts where id = ?", Boolean.class, secondId));

        mvc().perform(get(pathA).param("page", "1").param("pageSize", "1")
                        .header("Authorization", token("DISPATCHER", "dispatcher@example.test")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(1));
        mvc().perform(get(pathA + "/" + secondId).header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.fullName").value("Trần Bình"));
        mvc().perform(put(pathA + "/" + firstId).header("Authorization", admin).contentType("application/json")
                        .content(emergencyContactUpdateBody("Nguyễn An", true, true)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.primary").value(true));
        assertFalse(jdbc.queryForObject("select is_primary from driver_emergency_contacts where id = ?", Boolean.class, secondId));

        mvc().perform(get("/api/driver/me/emergency-contacts").header("Authorization", tokenFor(driverA, "DRIVER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalItems").value(2))
                .andExpect(jsonPath("$.data.items[0].driverId").value(driverA.id().toString()));
        mvc().perform(get(pathB).header("Authorization", tokenFor(driverA, "DRIVER"))).andExpect(status().isForbidden());
        mvc().perform(post(pathB).header("Authorization", tokenFor(driverA, "DRIVER")).contentType("application/json")
                        .content(emergencyContactBody("Unauthorized", false)))
                .andExpect(status().isForbidden());
        mvc().perform(post(pathA).header("Authorization", admin).contentType("application/json")
                        .content("{\"fullName\":\"Invalid\",\"relationship\":\"Parent\",\"phoneNumber\":\"bad\",\"primary\":false}"))
                .andExpect(status().isBadRequest());
        mvc().perform(delete(pathA + "/" + firstId).header("Authorization", admin)).andExpect(status().isNoContent());
        assertFalse(jdbc.queryForObject("select active from driver_emergency_contacts where id = ?", Boolean.class, firstId));
        mvc().perform(get(pathA).header("Authorization", admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(1));
    }

    @Test
    void contractsKeepHistoryAndValidateRoleDatesAndDocumentOwnership() throws Exception {
        Fixture driver = employee("DRIVER");
        String path = "/api/drivers/" + driver.id() + "/contracts";
        String admin = token("ADMIN", "admin@example.test");
        var created = mvc().perform(post(path).header("Authorization", admin).contentType("application/json")
                        .content(contractBody("HD-2026-001")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.contractNumber").value("HD-2026-001"))
                .andExpect(jsonPath("$.data.active").value(true))
                .andReturn().getResponse();
        UUID contractId = UUID.fromString(json.readTree(created.getContentAsString()).path("data").path("id").asString());

        mvc().perform(get(path).header("Authorization", token("PAYROLL", "payroll@example.test")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalItems").value(1));
        mvc().perform(get(path).header("Authorization", token("DISPATCHER", "dispatcher@example.test")))
                .andExpect(status().isForbidden());
        mvc().perform(get(path + "/" + contractId).header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.contractType").value("FIXED_TERM"));
        mvc().perform(put(path + "/" + contractId).header("Authorization", admin).contentType("application/json")
                        .content(contractUpdateBody("HD-2026-001", "2026-02-01", "Điều chuyển công việc", true)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.terminationReason").value("Điều chuyển công việc"));
        mvc().perform(post(path).header("Authorization", admin).contentType("application/json")
                        .content(contractBody("HD-2026-002", "INDEFINITE", "2027-01-01")))
                .andExpect(status().isBadRequest());
        mvc().perform(post(path).header("Authorization", admin).contentType("application/json")
                        .content(contractBody("HD-2026-003", "FIXED_TERM", "2027-01-01", UUID.randomUUID().toString())))
                .andExpect(status().isNotFound());
        mvc().perform(post(path).header("Authorization", admin).contentType("application/json")
                        .content(contractBody("hd-2026-001")))
                .andExpect(status().isConflict());

        mvc().perform(get("/api/driver/me/contracts").header("Authorization", tokenFor(driver, "DRIVER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.items[0].driverId").value(driver.id().toString()));
        mvc().perform(delete(path + "/" + contractId).header("Authorization", admin)).andExpect(status().isNoContent());
        assertFalse(jdbc.queryForObject("select active from employment_contracts where id = ?", Boolean.class, contractId));
        mvc().perform(delete("/api/drivers/" + driver.id()).header("Authorization", admin))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DRIVER_HAS_COMPLIANCE_RECORDS"));
    }

    @Test
    void insuranceIdentifiersAreEncryptedMaskedAndVisibleToDriverOnlyThroughSelfRoute() throws Exception {
        Fixture driver = employee("DRIVER");
        Fixture otherDriver = employee("DRIVER");
        String path = "/api/drivers/" + driver.id() + "/insurances";
        String socialNumber = "0123456789";
        String healthNumber = "ABC1234567";
        String admin = token("ADMIN", "admin@example.test");
        var created = mvc().perform(post(path).header("Authorization", admin).contentType("application/json")
                        .content(insuranceBody(socialNumber, healthNumber, "2024-01-02", null)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.socialInsuranceNumber").value("****6789"))
                .andExpect(jsonPath("$.data.healthInsuranceCardNumber").value("****4567"))
                .andReturn().getResponse();
        String response = created.getContentAsString();
        assertFalse(response.contains(socialNumber));
        assertFalse(response.contains(healthNumber));
        UUID insuranceId = UUID.fromString(json.readTree(response).path("data").path("id").asString());

        Map<String, Object> stored = jdbc.queryForMap(
                "select social_insurance_number, health_insurance_card_number from driver_insurances where id = ?", insuranceId);
        assertTrue(((String) stored.get("social_insurance_number")).startsWith("DLENC:v1:test-v1:"));
        assertTrue(((String) stored.get("health_insurance_card_number")).startsWith("DLENC:v1:test-v1:"));
        mvc().perform(get(path + "/" + insuranceId + "/sensitive").header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.socialInsuranceNumber").value(socialNumber))
                .andExpect(jsonPath("$.data.healthInsuranceCardNumber").value(healthNumber));
        mvc().perform(get(path + "/" + insuranceId + "/sensitive")
                        .header("Authorization", token("PAYROLL", "payroll@example.test")))
                .andExpect(status().isForbidden());
        mvc().perform(get(path).header("Authorization", token("PAYROLL", "payroll@example.test")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].socialInsuranceNumber").value("****6789"));

        mvc().perform(put(path + "/" + insuranceId).header("Authorization", admin).contentType("application/json")
                        .content(insuranceUpdateBody("****6789", "****4567", true)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.socialInsuranceNumber").value("****6789"));
        mvc().perform(get("/api/driver/me/insurances").header("Authorization", tokenFor(driver, "DRIVER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].driverId").value(driver.id().toString()))
                .andExpect(jsonPath("$.data.items[0].healthInsuranceCardNumber").value("****4567"));
        mvc().perform(get("/api/driver/me/insurances").header("Authorization", tokenFor(otherDriver, "DRIVER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalItems").value(0));
        mvc().perform(get(path).header("Authorization", tokenFor(otherDriver, "DRIVER"))).andExpect(status().isForbidden());
        mvc().perform(post(path).header("Authorization", tokenFor(otherDriver, "DRIVER")).contentType("application/json")
                        .content(insuranceBody(null, null, "2024-01-02", null)))
                .andExpect(status().isForbidden());
        mvc().perform(post(path).header("Authorization", admin).contentType("application/json")
                        .content(insuranceBody(null, null, "2025-01-02", "2024-01-02")))
                .andExpect(status().isBadRequest());

        mvc().perform(delete(path + "/" + insuranceId).header("Authorization", admin)).andExpect(status().isNoContent());
        assertFalse(jdbc.queryForObject("select active from driver_insurances where id = ?", Boolean.class, insuranceId));
        mvc().perform(delete("/api/drivers/" + driver.id()).header("Authorization", admin))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DRIVER_HAS_COMPLIANCE_RECORDS"));
    }

    @Test
    void dependentsProtectTaxIdentifiersAndPreserveDeductionHistory() throws Exception {
        Fixture driver = employee("DRIVER");
        Fixture otherDriver = employee("DRIVER");
        String path = "/api/drivers/" + driver.id() + "/dependents";
        String nationalId = "079123456789";
        String taxCode = "1234567890";
        String admin = token("ADMIN", "admin@example.test");

        var created = mvc().perform(post(path).header("Authorization", admin).contentType("application/json")
                        .content(dependentBody(nationalId, taxCode, "2024-01-01", "2024-12-31")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.nationalIdNumber").value("****6789"))
                .andExpect(jsonPath("$.data.taxCode").value("****7890"))
                .andReturn().getResponse();
        String response = created.getContentAsString();
        assertFalse(response.contains(nationalId));
        assertFalse(response.contains(taxCode));
        UUID dependentId = UUID.fromString(json.readTree(response).path("data").path("id").asString());
        Map<String, Object> stored = jdbc.queryForMap(
                "select national_id_number, tax_code from driver_dependents where id = ?", dependentId);
        assertTrue(((String) stored.get("national_id_number")).startsWith("DLENC:v1:test-v1:"));
        assertTrue(((String) stored.get("tax_code")).startsWith("DLENC:v1:test-v1:"));

        mvc().perform(get(path).header("Authorization", token("PAYROLL", "payroll@example.test")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.items[0].nationalIdNumber").value("****6789"));
        mvc().perform(get(path + "/" + dependentId + "/sensitive")
                        .header("Authorization", token("PAYROLL", "payroll@example.test")))
                .andExpect(status().isForbidden());
        mvc().perform(get(path + "/" + dependentId + "/sensitive").header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.nationalIdNumber").value(nationalId))
                .andExpect(jsonPath("$.data.taxCode").value(taxCode));
        mvc().perform(get(path + "/" + UUID.randomUUID()).header("Authorization", admin))
                .andExpect(status().isNotFound());
        mvc().perform(put(path + "/" + dependentId).header("Authorization", admin).contentType("application/json")
                        .content(dependentUpdateBody("****6789", "****7890", true)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.nationalIdNumber").value("****6789"));
        mvc().perform(post(path).header("Authorization", admin).contentType("application/json")
                        .content(dependentBody(null, null, null, "2024-12-31")))
                .andExpect(status().isBadRequest());
        mvc().perform(get(path).header("Authorization", token("DISPATCHER", "dispatcher@example.test")))
                .andExpect(status().isForbidden());
        mvc().perform(get("/api/driver/me/dependents").header("Authorization", tokenFor(driver, "DRIVER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].driverId").value(driver.id().toString()))
                .andExpect(jsonPath("$.data.items[0].taxCode").value("****7890"));
        mvc().perform(get(path).header("Authorization", tokenFor(otherDriver, "DRIVER")))
                .andExpect(status().isForbidden());
        mvc().perform(delete(path + "/" + dependentId).header("Authorization", admin)).andExpect(status().isNoContent());
        assertFalse(jdbc.queryForObject("select active from driver_dependents where id = ?", Boolean.class, dependentId));
        mvc().perform(delete("/api/drivers/" + driver.id()).header("Authorization", admin))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DRIVER_HAS_COMPLIANCE_RECORDS"));
    }

    @Test
    void bankAccountsKeepNumbersEncryptedAndEnforceOneActivePrimary() throws Exception {
        Fixture driver = employee("DRIVER");
        Fixture otherDriver = employee("DRIVER");
        String path = "/api/drivers/" + driver.id() + "/bank-accounts";
        String firstNumber = "123456781111";
        String secondNumber = "876543212222";
        String admin = token("ADMIN", "admin@example.test");

        var firstResponse = mvc().perform(post(path).header("Authorization", admin).contentType("application/json")
                        .content(bankAccountBody("Ngân hàng A", firstNumber, true)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.accountNumber").value("****1111"))
                .andExpect(jsonPath("$.data.primary").value(true))
                .andReturn().getResponse();
        UUID firstId = UUID.fromString(json.readTree(firstResponse.getContentAsString()).path("data").path("id").asString());
        var secondResponse = mvc().perform(post(path).header("Authorization", admin).contentType("application/json")
                        .content(bankAccountBody("Ngân hàng B", secondNumber, true)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.accountNumber").value("****2222"))
                .andExpect(jsonPath("$.data.primary").value(true))
                .andReturn().getResponse();
        UUID secondId = UUID.fromString(json.readTree(secondResponse.getContentAsString()).path("data").path("id").asString());
        assertFalse(jdbc.queryForObject("select is_primary from driver_bank_accounts where id = ?", Boolean.class, firstId));
        assertTrue(jdbc.queryForObject("select is_primary from driver_bank_accounts where id = ?", Boolean.class, secondId));
        String encryptedAccount = jdbc.queryForObject("select account_number from driver_bank_accounts where id = ?", String.class, firstId);
        assertTrue(encryptedAccount.startsWith("DLENC:v1:test-v1:"));
        assertFalse(encryptedAccount.contains(firstNumber));

        mvc().perform(put(path + "/" + firstId).header("Authorization", admin).contentType("application/json")
                        .content(bankAccountUpdateBody("****1111", true)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.primary").value(true));
        assertTrue(jdbc.queryForObject("select is_primary from driver_bank_accounts where id = ?", Boolean.class, firstId));
        assertFalse(jdbc.queryForObject("select is_primary from driver_bank_accounts where id = ?", Boolean.class, secondId));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("update driver_bank_accounts set is_primary = true where id = ?", secondId));

        mvc().perform(get(path).header("Authorization", token("PAYROLL_MANAGER", "payroll-manager@example.test")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalItems").value(2))
                .andExpect(jsonPath("$.data.items[0].accountNumber").value("****2222"));
        mvc().perform(get(path + "/" + firstId + "/sensitive")
                        .header("Authorization", token("PAYROLL", "payroll@example.test")))
                .andExpect(status().isForbidden());
        mvc().perform(get(path + "/" + firstId + "/sensitive").header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.accountNumber").value(firstNumber));
        mvc().perform(get(path + "/" + UUID.randomUUID()).header("Authorization", admin))
                .andExpect(status().isNotFound());
        mvc().perform(get("/api/driver/me/bank-accounts").header("Authorization", tokenFor(driver, "DRIVER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalItems").value(2))
                .andExpect(jsonPath("$.data.items[0].accountNumber").value("****2222"));
        mvc().perform(get(path).header("Authorization", tokenFor(otherDriver, "DRIVER")))
                .andExpect(status().isForbidden());
        mvc().perform(post(path).header("Authorization", admin).contentType("application/json")
                        .content(bankAccountBody("Bad Bank", "bad", false)))
                .andExpect(status().isBadRequest());
        mvc().perform(delete(path + "/" + firstId).header("Authorization", admin)).andExpect(status().isNoContent());
        assertFalse(jdbc.queryForObject("select active from driver_bank_accounts where id = ?", Boolean.class, firstId));
        mvc().perform(delete("/api/drivers/" + driver.id()).header("Authorization", admin))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DRIVER_HAS_COMPLIANCE_RECORDS"));
    }

    @Test
    void operationalStatusIsSeparateFromEmployeeStatusAndFiltersDriverDirectory() throws Exception {
        Fixture driver = employee("DRIVER");
        Fixture otherDriver = employee("DRIVER");
        UUID vehicleId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        jdbc.update("""
                insert into trucks(id,number,type,vehicle_capacity,status,is_hazmat_placarded,
                    adr_equipment_allowed_classes,adr_equipment_is_adr_certified)
                values (?,?,'SEMI',1,'ACTIVE',false,'',false)
                """, vehicleId, "OPS-" + vehicleId);
        jdbc.update("insert into trips(id,name,total_distance,status) values (?, 'Current driver trip', 1, 'PLANNED')",
                tripId);
        jdbc.update("""
                insert into trip_driver_assignments(id,trip_id,driver_id,assignment_type,effective_from)
                values (?,?,?,'PRIMARY',?)
                """, UUID.randomUUID(), tripId, driver.id(), OffsetDateTime.now().minusHours(1));

        String path = "/api/drivers/" + driver.id() + "/operational-status";
        String dispatcher = token("DISPATCHER", "dispatcher@example.test");
        mvc().perform(get(path).header("Authorization", token("ADMIN", "admin@example.test")))
                .andExpect(status().isNotFound());
        mvc().perform(put(path).header("Authorization", dispatcher).contentType("application/json")
                        .content(operationalStatusBody("AVAILABLE", vehicleId, "10.7626220", "106.6601720",
                                "2026-10-30T07:00:00Z", "5.50")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availability").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.defaultVehicleId").value(vehicleId.toString()))
                .andExpect(jsonPath("$.data.currentTripId").value(tripId.toString()))
                .andExpect(jsonPath("$.data.lastKnownLatitude").value(10.7626220))
                .andExpect(jsonPath("$.data.leaveBalanceDays").value(5.50));
        assertEquals("ACTIVE", jdbc.queryForObject("select status from employees where id = ?", String.class, driver.id()));

        mvc().perform(get(path).header("Authorization", token("ADMIN", "admin@example.test")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.driverId").value(driver.id().toString()));
        mvc().perform(get("/api/driver/me/operational-status").param("driverId", otherDriver.id().toString())
                        .header("Authorization", tokenFor(driver, "DRIVER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.driverId").value(driver.id().toString()));
        mvc().perform(get(path).header("Authorization", token("PAYROLL", "payroll@example.test")))
                .andExpect(status().isForbidden());
        mvc().perform(get(path).header("Authorization", tokenFor(otherDriver, "DRIVER")))
                .andExpect(status().isForbidden());
        mvc().perform(put(path).header("Authorization", tokenFor(driver, "DRIVER")).contentType("application/json")
                        .content(operationalStatusBody("AVAILABLE", null, null, null, null, null)))
                .andExpect(status().isForbidden());

        mvc().perform(get("/api/drivers").param("availability", "AVAILABLE").param("page", "1").param("pageSize", "20")
                        .header("Authorization", dispatcher))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(driver.id().toString()));
        mvc().perform(get("/api/drivers").param("availability", "UNKNOWN")
                        .header("Authorization", dispatcher))
                .andExpect(status().isBadRequest());

        mvc().perform(put(path).header("Authorization", dispatcher).contentType("application/json")
                        .content(operationalStatusBody("ON_LEAVE", null, "10.0", null, null, null)))
                .andExpect(status().isBadRequest());
        mvc().perform(put(path).header("Authorization", dispatcher).contentType("application/json")
                        .content(operationalStatusBody("DRIVING", null, null, null, null, null)))
                .andExpect(status().isBadRequest());
        mvc().perform(put(path).header("Authorization", dispatcher).contentType("application/json")
                        .content(operationalStatusBody("OFF_DUTY", UUID.randomUUID(), null, null, null, null)))
                .andExpect(status().isNotFound());

        mvc().perform(put(path).header("Authorization", dispatcher).contentType("application/json")
                        .content(operationalStatusBody("OFF_DUTY", null, null, null, null, "2.00")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.availability").value("OFF_DUTY"))
                .andExpect(jsonPath("$.data.defaultVehicleId").doesNotExist());
        mvc().perform(get("/api/drivers").param("availability", "AVAILABLE")
                        .header("Authorization", dispatcher))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalItems").value(0));
    }

    @Test
    void legacyCleartextLicenseRowsAreBackfilledAndTenantForeignKeyRestrictsDeletion() {
        Fixture driver = employee("DRIVER");
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into driver_licenses(id,employee_id,license_number,license_class,endorsements,
                    issuing_country,issued_date,expires_at,status)
                values (?,?,?,'C','', 'VN', now(), now() + interval '3 years', 'active')
                """, id, driver.id(), "LEGACY-1234");

        assertEquals(1, piiBackfill.backfill(dataSource));
        String stored = jdbc.queryForObject("select license_number from driver_licenses where id = ?", String.class, id);
        assertTrue(stored.startsWith("DLENC:v1:test-v1:"));
        assertEquals("LEGACY-1234", piiCipher.decryptLicenseNumber(stored, driver.id(), "VN"));
        assertEquals(64, jdbc.queryForObject("select length(license_number_hmac) from driver_licenses where id = ?", Integer.class, id));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("delete from employees where id = ?", driver.id()));
    }

    @Test
    void violationsArePagedOwnedMaskedFromDriversAndRetainedOnDeactivate() throws Exception {
        Fixture driver = employee("DRIVER");
        Fixture otherDriver = employee("DRIVER");
        Fixture uploader = employee("ADMIN");
        UUID documentId = document(driver, uploader, "DRIVER_VIOLATION");
        String path = "/api/drivers/" + driver.id() + "/violations";
        String dispatcher = token("DISPATCHER", "dispatcher@example.test");

        var created = mvc().perform(post(path).header("Authorization", dispatcher).contentType("application/json")
                        .content(violationBody(documentId, "250000.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.driverId").value(driver.id().toString()))
                .andExpect(jsonPath("$.data.fineCurrency").value("VND"))
                .andExpect(jsonPath("$.data.resolutionStatus").value("OPEN"))
                .andExpect(jsonPath("$.data.documentId").value(documentId.toString()))
                .andReturn().getResponse();
        UUID violationId = UUID.fromString(json.readTree(created.getContentAsString()).path("data").path("id").asString());

        mvc().perform(get(path).param("page", "1").param("pageSize", "5")
                        .header("Authorization", dispatcher))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(violationId.toString()));
        mvc().perform(get(path + "/" + UUID.randomUUID()).header("Authorization", dispatcher))
                .andExpect(status().isNotFound());
        mvc().perform(get(path).param("orderBy", "description;drop")
                        .header("Authorization", dispatcher))
                .andExpect(status().isBadRequest());
        mvc().perform(get("/api/drivers/" + otherDriver.id() + "/violations")
                        .header("Authorization", tokenFor(driver, "DRIVER")))
                .andExpect(status().isForbidden());
        mvc().perform(post(path).header("Authorization", token("PAYROLL", "payroll@example.test"))
                        .contentType("application/json").content(violationBody(documentId, "250000.00")))
                .andExpect(status().isForbidden());
        mvc().perform(post(path).header("Authorization", dispatcher).contentType("application/json")
                        .content(violationBody(documentId, "-1.00")))
                .andExpect(status().isBadRequest());
        mvc().perform(put(path + "/" + violationId).header("Authorization", dispatcher)
                        .contentType("application/json").content(violationUpdateBody(documentId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.resolutionStatus").value("UNDER_REVIEW"));
        mvc().perform(delete(path + "/" + violationId).header("Authorization", dispatcher))
                .andExpect(status().isNoContent());
        assertFalse(jdbc.queryForObject("select active from driver_violations where id = ?", Boolean.class, violationId));
        assertEquals(1, jdbc.queryForObject("select count(*) from driver_violations where id = ?", Integer.class, violationId));
        mvc().perform(get(path).header("Authorization", dispatcher))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalItems").value(0));
        mvc().perform(delete("/api/drivers/" + driver.id())
                        .header("Authorization", token("ADMIN", "admin@example.test")))
                .andExpect(status().isConflict());
    }

    @Test
    void accidentProfileApiReusesReportHistoryAndCancelsWithoutDeleting() throws Exception {
        Fixture driver = employee("DRIVER");
        Fixture otherDriver = employee("DRIVER");
        Fixture uploader = employee("ADMIN");
        UUID truckId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        OffsetDateTime accidentAt = OffsetDateTime.now().minusMinutes(10).withNano(0);
        jdbc.update("""
                insert into trucks(id,number,type,vehicle_capacity,status,is_hazmat_placarded,
                    adr_equipment_allowed_classes,adr_equipment_is_adr_certified)
                values (?,?,'SEMI',1,'ACTIVE',false,'',false)
                """, truckId, "ACC-" + truckId);
        jdbc.update("insert into trips(id,name,total_distance,status,truck_id) values (?, 'Accident trip', 1, 'IN_PROGRESS', ?)",
                tripId, truckId);
        jdbc.update("""
                insert into trip_driver_assignments(id,trip_id,driver_id,assignment_type,effective_from)
                values (?,?,?,'PRIMARY',?)
                """, UUID.randomUUID(), tripId, driver.id(), accidentAt.minusHours(1));
        UUID documentId = document(driver, uploader, "ACCIDENT_REPORT");
        String path = "/api/drivers/" + driver.id() + "/accidents";
        String dispatcher = token("DISPATCHER", "dispatcher@example.test");

        var created = mvc().perform(post(path).header("Authorization", dispatcher).contentType("application/json")
                        .content(accidentBody(truckId, tripId, accidentAt, documentId, true, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.driverId").value(driver.id().toString()))
                .andExpect(jsonPath("$.data.truckId").value(truckId.toString()))
                .andExpect(jsonPath("$.data.tripId").value(tripId.toString()))
                .andExpect(jsonPath("$.data.status").value("REPORTED"))
                .andExpect(jsonPath("$.data.documentIds[0]").value(documentId.toString()))
                .andReturn().getResponse();
        UUID accidentId = UUID.fromString(json.readTree(created.getContentAsString()).path("data").path("id").asString());

        mvc().perform(get(path + "/" + accidentId).header("Authorization", token("ADMIN", "admin@example.test")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.estimatedDamageCost").value(125000.00));
        mvc().perform(put(path + "/" + accidentId).header("Authorization", dispatcher).contentType("application/json")
                        .content(accidentUpdateBody(truckId, tripId, accidentAt)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.data.documentIds[0]").value(documentId.toString()));
        mvc().perform(get(path).param("page", "1").param("pageSize", "5")
                        .header("Authorization", dispatcher))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalItems").value(1));
        mvc().perform(get("/api/drivers/" + otherDriver.id() + "/accidents/" + accidentId)
                        .header("Authorization", dispatcher))
                .andExpect(status().isNotFound());
        mvc().perform(get(path).header("Authorization", tokenFor(driver, "DRIVER")))
                .andExpect(status().isForbidden());
        mvc().perform(post(path).header("Authorization", dispatcher).contentType("application/json")
                        .content(accidentBody(truckId, tripId, accidentAt, null, false, 1)))
                .andExpect(status().isBadRequest());
        mvc().perform(delete(path + "/" + accidentId).header("Authorization", dispatcher))
                .andExpect(status().isNoContent());
        assertEquals("CANCELLED", jdbc.queryForObject("select status from accident_reports where id = ?", String.class, accidentId));
        assertEquals(1, jdbc.queryForObject("select count(*) from accident_reports where id = ?", Integer.class, accidentId));
        mvc().perform(get(path).header("Authorization", dispatcher))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalItems").value(0));
        mvc().perform(delete("/api/drivers/" + driver.id())
                        .header("Authorization", token("ADMIN", "admin@example.test")))
                .andExpect(status().isConflict());
    }

    @Test
    void expiryDashboardCombinesComplianceRecordsAndEnforcesManagementPermissions() throws Exception {
        Fixture driver = employee("DRIVER");
        Fixture otherDriver = employee("DRIVER");
        Fixture nonDriver = employee("ADMIN");
        String admin = token("ADMIN", "admin@example.test");
        String dispatcher = token("DISPATCHER", "dispatcher@example.test");
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        LocalDate healthExpiry = today.plusDays(10);
        LocalDate certificationExpiry = today.plusDays(15);
        LocalDate contractExpiry = today.plusDays(6);
        LocalDate insuranceExpiry = today.plusDays(3);

        mvc().perform(post("/api/drivers/" + driver.id() + "/licenses")
                        .header("Authorization", dispatcher).contentType("application/json")
                        .content(licenseBodyWithExpiry("SUMMARY-LICENSE-1234", 12,
                                OffsetDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(20))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.expiryStatus").value("EXPIRING_SOON"));

        UUID healthCheckId = UUID.randomUUID();
        jdbc.update("""
                insert into driver_health_checks(id, employee_id, checked_date, expiry_date, facility, conclusion, status)
                values (?, ?, ?, ?, 'Test clinic', 'FIT', 'active')
                """, healthCheckId, driver.id(), today.minusDays(30), healthExpiry);
        UUID certificationId = UUID.randomUUID();
        jdbc.update("""
                insert into driver_certifications(id, employee_id, type, name, issued_date, expiry_date, status)
                values (?, ?, 'SAFETY', 'Synthetic safety credential', ?, ?, 'active')
                """, certificationId, driver.id(), today.minusDays(100), certificationExpiry);
        UUID contractId = UUID.randomUUID();
        jdbc.update("""
                insert into employment_contracts(id, employee_id, contract_number, contract_type, signed_date,
                    effective_date, expiry_date, active)
                values (?, ?, ?, 'FIXED_TERM', ?, ?, ?, true)
                """, contractId, driver.id(), "SUMMARY-" + UUID.randomUUID(),
                today.minusDays(60), today.minusDays(30), contractExpiry);
        UUID insuranceId = UUID.randomUUID();
        jdbc.update("""
                insert into driver_insurances(id, employee_id, participation_start_date, health_insurance_status,
                    health_insurance_expiry_date, active)
                values (?, ?, ?, 'ACTIVE', ?, true)
                """, insuranceId, driver.id(), today.minusDays(120), insuranceExpiry);

        String summaryPath = "/api/drivers/" + driver.id() + "/profile-summary";
        mvc().perform(get(summaryPath).header("Authorization", dispatcher))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.counts.licenses").value(1))
                .andExpect(jsonPath("$.data.counts.healthChecks").value(1))
                .andExpect(jsonPath("$.data.counts.certifications").value(1))
                .andExpect(jsonPath("$.data.counts.contracts").value(1))
                .andExpect(jsonPath("$.data.counts.insurances").value(1))
                .andExpect(jsonPath("$.data.license.expiryStatus").value("EXPIRING_SOON"))
                .andExpect(jsonPath("$.data.healthCheck.expiryStatus").value("EXPIRING_SOON"))
                .andExpect(jsonPath("$.data.contract.expiryStatus").value("EXPIRING_SOON"))
                .andExpect(jsonPath("$.data.healthInsurance.expiryStatus").value("EXPIRING_SOON"))
                .andExpect(jsonPath("$.data.upcomingExpirations.length()").value(5));

        mvc().perform(get("/api/drivers/" + driver.id() + "/health-checks/" + healthCheckId)
                        .header("Authorization", dispatcher))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.expiryStatus").value("EXPIRING_SOON"));
        mvc().perform(get("/api/drivers/" + driver.id() + "/certifications/" + certificationId)
                        .header("Authorization", dispatcher))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.expiryStatus").value("EXPIRING_SOON"));
        mvc().perform(get("/api/drivers/" + driver.id() + "/contracts/" + contractId)
                        .header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.expiryStatus").value("EXPIRING_SOON"));
        mvc().perform(get("/api/drivers/" + driver.id() + "/insurances/" + insuranceId + "/sensitive")
                        .header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.expiryStatus").value("EXPIRING_SOON"));

        mvc().perform(get("/api/drivers/expiring?days=30&type=health_insurance&page=1&pageSize=20")
                        .header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.items[0].type").value("HEALTH_INSURANCE"))
                .andExpect(jsonPath("$.data.items[0].daysUntilExpiry").value(3));
        mvc().perform(get("/api/drivers/expiring?days=0").header("Authorization", dispatcher))
                .andExpect(status().isBadRequest());
        mvc().perform(get("/api/drivers/expiring?type=UNKNOWN").header("Authorization", dispatcher))
                .andExpect(status().isBadRequest());
        mvc().perform(get(summaryPath).header("Authorization", tokenFor(driver, "DRIVER")))
                .andExpect(status().isForbidden());
        mvc().perform(get("/api/drivers/expiring").header("Authorization", tokenFor(otherDriver, "DRIVER")))
                .andExpect(status().isForbidden());
        mvc().perform(get("/api/drivers/" + UUID.randomUUID() + "/profile-summary")
                        .header("Authorization", dispatcher))
                .andExpect(status().isNotFound());
        mvc().perform(get("/api/drivers/" + nonDriver.id() + "/profile-summary")
                        .header("Authorization", dispatcher))
                .andExpect(status().isNotFound());
    }

    private Fixture employee(String roleName) {
        UUID roleId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        String email = employeeId + "@driver-profile.test";
        jdbc.update("insert into tenant_roles(id,name,normalized_name) values (?,?,?)", roleId, roleName, roleName);
        jdbc.update("""
                insert into employees(id,email,first_name,last_name,salary_type,status,joined_date,
                    salary_amount,salary_currency,employee_type,role_id)
                values (?,?, 'Driver', 'Fixture', 'HOURLY', 'ACTIVE', now(), 0, 'VND', ?, ?)
                """, employeeId, email, roleName.equalsIgnoreCase("DRIVER") ? "DRIVER" : "OFFICE_STAFF", roleId);
        return new Fixture(employeeId, email);
    }

    private void createLicense(Fixture driver, String licenseNumber) throws Exception {
        mvc().perform(post("/api/drivers/" + driver.id() + "/licenses")
                        .header("Authorization", token("DISPATCHER", "dispatcher@example.test"))
                        .contentType("application/json").content(licenseBody(licenseNumber, 12)))
                .andExpect(status().isCreated());
    }

    private String licenseBody(String number, int points) {
        return """
                {"licenseNumber":"%s","licenseClass":"C1E","endorsements":"Hàng hóa",
                 "issuingCountry":"VN","issuedDate":"2024-01-01T00:00:00Z",
                 "expiryDate":"2030-01-01T00:00:00Z","issuedPlace":"Hà Nội","remainingPoints":%d}
                """.formatted(number, points);
    }

    private String licenseBodyWithExpiry(String number, int points, OffsetDateTime expiryDate) {
        return """
                {"licenseNumber":"%s","licenseClass":"C1","endorsements":"",
                 "issuingCountry":"VN","issuedDate":"2024-01-01T00:00:00Z",
                 "expiryDate":"%s","issuedPlace":"Hà Nội","remainingPoints":%d}
                """.formatted(number, expiryDate, points);
    }

    private String updateLicenseBody(String number, boolean active) {
        String numberJson = number == null ? "" : "\"licenseNumber\":\"" + number + "\",";
        return """
                {%s"licenseClass":"C1E","endorsements":"Hàng hóa","issuingCountry":"VN",
                 "issuedDate":"2024-01-01T00:00:00Z","expiryDate":"2030-01-01T00:00:00Z",
                 "issuedPlace":"Hà Nội","remainingPoints":12,"active":%s}
                """.formatted(numberJson, active);
    }

    private String profileBody(String nationalId, String taxCode) {
        return profileBody(nationalId, taxCode, "2020-01-02");
    }

    private String profileBody(String nationalId, String taxCode, String issuedDate) {
        String national = nationalId == null ? "" : "\"nationalIdNumber\":\"" + nationalId + "\",";
        String tax = taxCode == null ? "" : "\"personalTaxCode\":\"" + taxCode + "\",";
        String issued = issuedDate == null ? "" : "\"nationalIdIssuedDate\":\"" + issuedDate + "\",";
        return """
                {"dateOfBirth":"1985-05-02","gender":"MALE","nationality":"Vietnam",
                 "maritalStatus":"MARRIED","educationLevel":"HIGH_SCHOOL",%s%s%s
                 "nationalIdIssuedPlace":"Hà Nội","permanentAddress":"Hà Nội",
                 "temporaryAddress":"Bình Dương","hometown":"Nam Định"}
                """.formatted(national, tax, issued);
    }

    private String emergencyContactBody(String name, boolean primary) {
        return """
                {"fullName":"%s","relationship":"Thân nhân","phoneNumber":"+84901234567",
                 "address":"Hà Nội","primary":%s}
                """.formatted(name, primary);
    }

    private String emergencyContactUpdateBody(String name, boolean primary, boolean active) {
        return """
                {"fullName":"%s","relationship":"Thân nhân","phoneNumber":"+84901234567",
                 "address":"Hà Nội","primary":%s,"active":%s}
                """.formatted(name, primary, active);
    }

    private String contractBody(String contractNumber) {
        return contractBody(contractNumber, "FIXED_TERM", "2027-01-01", null);
    }

    private String contractBody(String contractNumber, String type, String expiryDate) {
        return contractBody(contractNumber, type, expiryDate, null);
    }

    private String contractBody(String contractNumber, String type, String expiryDate, String documentId) {
        String expiry = expiryDate == null ? "" : "\"expiryDate\":\"" + expiryDate + "\",";
        String document = documentId == null ? "" : "\"documentId\":\"" + documentId + "\",";
        return """
                {"contractNumber":"%s","contractType":"%s","signedDate":"2026-01-01",
                 "effectiveDate":"2026-01-02",%s%s"terminationDate":null}
                """.formatted(contractNumber, type, expiry, document);
    }

    private String contractUpdateBody(String contractNumber, String terminationDate, String reason, boolean active) {
        return """
                {"contractNumber":"%s","contractType":"FIXED_TERM","signedDate":"2026-01-01",
                 "effectiveDate":"2026-01-02","expiryDate":"2027-01-01","terminationDate":"%s",
                 "terminationReason":"%s","active":%s}
                """.formatted(contractNumber, terminationDate, reason, active);
    }

    private String insuranceBody(String social, String health, String start, String end) {
        String socialJson = social == null ? "" : "\"socialInsuranceNumber\":\"" + social + "\",";
        String healthJson = health == null ? "" : "\"healthInsuranceCardNumber\":\"" + health + "\",";
        String endJson = end == null ? "" : "\"participationEndDate\":\"" + end + "\",";
        return """
                {%s%s"registeredClinic":"Phòng khám Hà Nội","participationStartDate":"%s",%s
                 "socialInsuranceStatus":"ACTIVE","healthInsuranceStatus":"ACTIVE",
                 "unemploymentInsuranceStatus":"ACTIVE","insuredSalary":8000000.00,
                 "healthInsuranceExpiryDate":"2027-01-01","note":"Fixture"}
                """.formatted(socialJson, healthJson, start, endJson);
    }

    private String insuranceUpdateBody(String social, String health, boolean active) {
        return """
                {"socialInsuranceNumber":"%s","healthInsuranceCardNumber":"%s",
                 "registeredClinic":"Phòng khám Hà Nội","participationStartDate":"2024-01-02",
                 "socialInsuranceStatus":"ACTIVE","healthInsuranceStatus":"ACTIVE",
                 "unemploymentInsuranceStatus":"ACTIVE","insuredSalary":8000000.00,
                 "healthInsuranceExpiryDate":"2027-01-01","note":"Updated","active":%s}
                """.formatted(social, health, active);
    }

    private String dependentBody(String nationalId, String taxCode, String start, String end) {
        String national = nationalId == null ? "" : "\"nationalIdNumber\":\"" + nationalId + "\",";
        String tax = taxCode == null ? "" : "\"taxCode\":\"" + taxCode + "\",";
        String startDate = start == null ? "" : "\"deductionStartDate\":\"" + start + "\",";
        String endDate = end == null ? "" : "\"deductionEndDate\":\"" + end + "\"";
        return """
                {"fullName":"Nguyễn An","relationship":"Child","dateOfBirth":"2015-01-02",%s%s%s%s}
                """.formatted(national, tax, startDate, endDate);
    }

    private String dependentUpdateBody(String nationalId, String taxCode, boolean active) {
        return """
                {"fullName":"Nguyễn An","relationship":"Child","dateOfBirth":"2015-01-02",
                 "nationalIdNumber":"%s","taxCode":"%s","deductionStartDate":"2024-01-01",
                 "deductionEndDate":"2024-12-31","active":%s}
                """.formatted(nationalId, taxCode, active);
    }

    private String bankAccountBody(String bankName, String number, boolean primary) {
        return """
                {"bankName":"%s","accountNumber":"%s","accountHolderName":"NGUYEN AN","primary":%s}
                """.formatted(bankName, number, primary);
    }

    private String bankAccountUpdateBody(String number, boolean primary) {
        return """
                {"bankName":"Ngân hàng A","accountNumber":"%s","accountHolderName":"NGUYEN AN",
                 "primary":%s,"active":true}
                """.formatted(number, primary);
    }

    private String operationalStatusBody(String availability, UUID vehicleId, String latitude, String longitude,
            String lastKnownAt, String leaveBalanceDays) {
        String vehicle = vehicleId == null ? "null" : "\"" + vehicleId + "\"";
        String lat = latitude == null ? "null" : latitude;
        String lon = longitude == null ? "null" : longitude;
        String captured = lastKnownAt == null ? "null" : "\"" + lastKnownAt + "\"";
        String leave = leaveBalanceDays == null ? "null" : leaveBalanceDays;
        return """
                {"availability":"%s","defaultVehicleId":%s,"lastKnownLatitude":%s,
                 "lastKnownLongitude":%s,"lastKnownAt":%s,"leaveBalanceDays":%s}
                """.formatted(availability, vehicle, lat, lon, captured, leave);
    }

    private UUID document(Fixture owner, Fixture uploader, String type) {
        UUID documentId = UUID.randomUUID();
        jdbc.update("""
                insert into documents(id,owner_type,file_name,original_file_name,content_type,file_size_bytes,
                    blob_path,blob_container,type,status,uploaded_by_id,employee_id)
                values (?,'EMPLOYEE','report.pdf','report.pdf','application/pdf',1024,?,?,?,'active',?,?)
                """, documentId, "profile/" + documentId, "private", type, uploader.id(), owner.id());
        return documentId;
    }

    private String violationBody(UUID documentId, String fineAmount) {
        return """
                {"violationDate":"2026-10-01T09:00:00Z","violationType":"speed",
                 "description":"Exceeded the posted speed limit","fineAmount":%s,
                 "fineCurrency":"vnd","documentId":"%s"}
                """.formatted(fineAmount, documentId);
    }

    private String violationUpdateBody(UUID documentId) {
        return """
                {"violationDate":"2026-10-01T09:00:00Z","violationType":"speed",
                 "description":"Exceeded the posted speed limit","fineAmount":250000.00,
                 "fineCurrency":"VND","documentId":"%s","resolutionStatus":"UNDER_REVIEW","active":true}
                """.formatted(documentId);
    }

    private String accidentBody(UUID truckId, UUID tripId, OffsetDateTime accidentAt,
            UUID documentId, boolean damaged, Integer numberOfInjuries) {
        String document = documentId == null ? "" : ",\"documentId\":\"" + documentId + "\"";
        String injuryCount = numberOfInjuries == null ? "" : ",\"numberOfInjuries\":" + numberOfInjuries;
        return """
                {"truckId":"%s","tripId":"%s","accidentType":"collision","severity":"minor",
                 "accidentDateTime":"%s","latitude":10.762622,"longitude":106.660172,
                 "address":"Ho Chi Minh City","description":"Minor road collision",
                 "anyInjuries":false%s,"vehicleDamaged":%s,"estimatedDamageCost":%s,
                 "vehicleDrivable":true,"policeReportFiled":true,"policeReportNumber":"VN-123",
                 "insuranceNotified":true%s}
                """.formatted(truckId, tripId, accidentAt, injuryCount, damaged,
                damaged ? "125000.00" : "null", document);
    }

    private String accidentUpdateBody(UUID truckId, UUID tripId, OffsetDateTime accidentAt) {
        return """
                {"truckId":"%s","tripId":"%s","accidentType":"COLLISION","severity":"MINOR",
                 "accidentDateTime":"%s","latitude":10.762622,"longitude":106.660172,
                 "address":"Ho Chi Minh City","description":"Minor road collision updated",
                 "anyInjuries":false,"vehicleDamaged":true,"estimatedDamageCost":125000.00,
                 "vehicleDrivable":true,"policeReportFiled":false,"insuranceNotified":false,
                 "status":"UNDER_REVIEW"}
                """.formatted(truckId, tripId, accidentAt);
    }

    private String token(String role, String email) {
        return "Bearer " + tokens.createInternalToken("driver-profile-test", email, "driver-profile-tenant",
                List.of(role), "Driver profile test", null);
    }

    private String tokenFor(Fixture fixture, String role) {
        return "Bearer " + tokens.createInternalToken("driver-profile-test", fixture.email(), "driver-profile-tenant",
                List.of(role), "Driver profile test", fixture.id());
    }

    private record Fixture(UUID id, String email) {}
}
