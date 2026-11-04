package com.company.logicstic.ai.dispatch.evidence;

import com.company.logicstic.entity.Employee;
import com.company.logicstic.entity.DriverHosStatus;
import com.company.logicstic.entity.DriverPayPolicy;
import com.company.logicstic.entity.Load;
import com.company.logicstic.entity.Truck;
import com.company.logicstic.repository.DriverHosStatusRepository;
import com.company.logicstic.repository.DriverPayPolicyRepository;
import com.company.logicstic.repository.TruckRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.containsString;

class DispatchEvidenceServiceTest {
    private final TruckRepository trucks = mock(TruckRepository.class);
    private final DriverHosStatusRepository hos = mock(DriverHosStatusRepository.class);
    private final DriverPayPolicyRepository pay = mock(DriverPayPolicyRepository.class);

    @Test
    void reportsRoutingAndHosFreshnessConfigurationAsMissing() {
        DispatchEvidenceService service = service("", null, RestClient.builder());
        when(trucks.findAll()).thenReturn(List.of());

        DispatchEvidenceService.Assessment assessment = service.assess(load(), 5);

        assertTrue(assessment.candidates().isEmpty());
        assertTrue(assessment.missingEvidence().contains("Chưa cấu hình AI_DISPATCH_ROUTING_BASE_URL cho dịch vụ định tuyến."));
        assertTrue(assessment.missingEvidence().contains("Chưa cấu hình AI_DISPATCH_HOS_MAX_AGE để xác định HOS còn mới."));
        assertTrue(assessment.missingEvidence().stream().anyMatch(s -> s.contains("Không có xe trạng thái AVAILABLE")));
        verifyNoInteractions(hos, pay);
    }

    @Test
    void reportsDriverHosRecordMissingAfterRoutingIsAvailable() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        Employee driver = new Employee(); driver.setId(UUID.randomUUID()); driver.setFirstName("Lan"); driver.setLastName("Nguyen"); driver.setStatus("ACTIVE");
        Truck truck = new Truck(); truck.setId(UUID.randomUUID()); truck.setNumber("T-1"); truck.setStatus("AVAILABLE");
        truck.setCurrentLocationLatitude(37.0); truck.setCurrentLocationLongitude(-122.0); truck.setMainDriver(driver);
        when(trucks.findAll()).thenReturn(List.of(truck));

        var assessment = service("https://routing.test", Duration.ofHours(1), builder).assess(load(), 5);

        assertTrue(assessment.candidates().isEmpty());
        assertTrue(assessment.missingEvidence().stream().anyMatch(s -> s.contains("Tài xế Lan Nguyen chưa có bản ghi HOS.")));
        server.verify();
        verify(hos).findByEmployee_Id(driver.getId());
        verify(pay).findDriverPoliciesAt(eq(driver.getId()), any());
    }

    @Test
    void createsDeterministicCandidateFromRoutingHosAndEffectivePayPolicy() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(containsString("routing.test/route/v1/driving/")))
                .andRespond(withSuccess("{\"routes\":[{\"distance\":1609.344,\"duration\":600}]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("routing.test/route/v1/driving/")))
                .andRespond(withSuccess("{\"routes\":[{\"distance\":3218.688,\"duration\":1200}]}", MediaType.APPLICATION_JSON));
        Employee driver = new Employee(); driver.setId(UUID.randomUUID()); driver.setFirstName("Lan"); driver.setLastName("Nguyen"); driver.setStatus("ACTIVE");
        Truck truck = new Truck(); truck.setId(UUID.randomUUID()); truck.setNumber("T-1"); truck.setStatus("AVAILABLE");
        truck.setCurrentLocationLatitude(37.0); truck.setCurrentLocationLongitude(-122.0); truck.setMainDriver(driver);
        DriverHosStatus status = new DriverHosStatus(); status.setEmployee(driver); status.setLastUpdatedAt(OffsetDateTime.now());
        status.setDrivingMinutesRemaining(900); status.setOnDutyMinutesRemaining(1000); status.setCycleMinutesRemaining(2000);
        status.setIsInViolation(false); status.setTimeUntilBreakRequired(Duration.ofHours(10));
        DriverPayPolicy policy = new DriverPayPolicy(); policy.setId(UUID.randomUUID()); policy.setPolicyCode("DRIVER-MILE");
        policy.setPayMethod("PER_MILE"); policy.setPerMileRate(new java.math.BigDecimal("0.50"));
        policy.setMileageBasis("PLANNED_ALL_MILES"); policy.setCurrency("USD");
        Load load = load(); load.setRequestedPickupDate(OffsetDateTime.now().minusMinutes(1));
        policy.setEffectiveFrom(load.getRequestedPickupDate().toLocalDate());
        when(trucks.findAll()).thenReturn(List.of(truck));
        when(hos.findByEmployee_Id(driver.getId())).thenReturn(java.util.Optional.of(status));
        when(pay.findDriverPoliciesAt(eq(driver.getId()), any())).thenReturn(List.of(policy));

        var assessment = service("https://routing.test", Duration.ofHours(1), builder).assess(load, 5);

        assertEquals(1, assessment.candidates().size(), assessment.missingEvidence().toString());
        var candidate = assessment.candidates().getFirst();
        assertEquals(3.0, candidate.totalRouteMiles(), 0.001);
        assertEquals(1800, candidate.driveTime().toSeconds());
        assertEquals(new java.math.BigDecimal("1.50"), candidate.driverPayEstimate());
        assertTrue(candidate.evidenceJson().contains("driver_hos_statuses"));
        assertTrue(assessment.missingEvidence().stream().anyMatch(s -> s.contains("nhiên liệu/tiêu hao xe")));
        server.verify();
    }

    private DispatchEvidenceService service(String routingUrl, Duration hosAge, RestClient.Builder builder) {
        return new DispatchEvidenceService(trucks, hos, pay, new ObjectMapper(), builder, routingUrl,
                hosAge == null ? "" : hosAge.toString());
    }

    private static Load load() {
        Load load = new Load();
        load.setId(UUID.randomUUID()); load.setNumber(123L);
        load.setOriginLocationLatitude(37.0); load.setOriginLocationLongitude(-122.0);
        load.setDestinationLocationLatitude(38.0); load.setDestinationLocationLongitude(-121.0);
        load.setRequestedPickupDate(OffsetDateTime.now().minusMinutes(2));
        load.setRequestedDeliveryDate(OffsetDateTime.now().plusDays(1));
        return load;
    }
}
