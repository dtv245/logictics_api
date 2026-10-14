package com.company.logicstic.service;

import com.company.logicstic.controller.TruckController;
import com.company.logicstic.dto.employee.CreateEmployeeRequest;
import com.company.logicstic.dto.role.CreateRoleRequest;
import com.company.logicstic.entity.Employee;
import com.company.logicstic.entity.TenantRole;
import com.company.logicstic.entity.Truck;
import com.company.logicstic.exception.ConflictException;
import com.company.logicstic.exception.GlobalExceptionHandler;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.repository.TenantRoleRepository;
import com.company.logicstic.repository.TruckRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UniquePrecheckServiceTest {
    private static final String TRUCK_INPUT = """
            {"number":"TRUCK-EXISTING","type":"TRACTOR","vehicleCapacity":1,
             "status":"ACTIVE","isHazmatPlacarded":false}
            """;
    private static final String TRUCK_UPDATE_INPUT = """
            {"number":"TRUCK-EXISTING","type":"TRACTOR","vehicleCapacity":1,
             "status":"ACTIVE","isHazmatPlacarded":false,"expectedVersion":0}
            """;

    @Test
    void truckCreatePrecheckReturnsNumberFieldOverHttp() throws Exception {
        var trucks = mock(TruckRepository.class);
        var employees = mock(EmployeeRepository.class);
        when(trucks.existsByNumber("TRUCK-EXISTING")).thenReturn(true);
        var mvc = MockMvcBuilders.standaloneSetup(new TruckController(new TruckService(trucks, employees)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(post("/api/trucks").contentType(MediaType.APPLICATION_JSON).content(TRUCK_INPUT))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_VALUE"))
                .andExpect(jsonPath("$.message").value("Truck with number 'TRUCK-EXISTING' already exists"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("number"))
                .andExpect(jsonPath("$.errors[0].code").value("UNIQUE"));
        verify(trucks, never()).save(any());
        verifyNoInteractions(employees);
    }

    @Test
    void truckUpdatePrecheckReturnsNumberFieldOverHttp() throws Exception {
        var trucks = mock(TruckRepository.class);
        var employees = mock(EmployeeRepository.class);
        UUID id = UUID.randomUUID();
        var existing = new Truck();
        existing.setNumber("TRUCK-OLD");
        existing.setVersion(0L);
        when(trucks.findById(id)).thenReturn(Optional.of(existing));
        when(trucks.existsByNumber("TRUCK-EXISTING")).thenReturn(true);
        var mvc = MockMvcBuilders.standaloneSetup(new TruckController(new TruckService(trucks, employees)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(put("/api/trucks/" + id).contentType(MediaType.APPLICATION_JSON).content(TRUCK_UPDATE_INPUT))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_VALUE"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("number"))
                .andExpect(jsonPath("$.errors[0].code").value("UNIQUE"));
        verify(trucks, never()).saveAndFlush(any());
        assertEquals("TRUCK-OLD", existing.getNumber());
        verifyNoInteractions(employees);
    }

    @Test
    void employeeCreatePrecheckIdentifiesEmail() {
        var employees = mock(EmployeeRepository.class);
        when(employees.existsByEmail("used@example.com")).thenReturn(true);
        var service = new EmployeeService(employees, mock(TenantRoleRepository.class));

        assertUnique(assertThrows(ConflictException.class, () -> service.create(employeeInput())), "email");
        verify(employees, never()).save(any());
    }

    @Test
    void employeeUpdatePrecheckIdentifiesEmail() {
        var employees = mock(EmployeeRepository.class);
        UUID id = UUID.randomUUID();
        var existing = new Employee();
        existing.setEmail("old@example.com");
        when(employees.findById(id)).thenReturn(Optional.of(existing));
        when(employees.existsByEmail("used@example.com")).thenReturn(true);
        var service = new EmployeeService(employees, mock(TenantRoleRepository.class));

        assertUnique(assertThrows(ConflictException.class, () -> service.update(id, employeeInput())), "email");
        verify(employees, never()).save(any());
        assertEquals("old@example.com", existing.getEmail());
    }

    @Test
    void roleCreatePrecheckMapsNormalizedNameToDtoName() {
        var roles = mock(TenantRoleRepository.class);
        when(roles.existsByNormalizedName("DRIVER")).thenReturn(true);
        var service = new RoleService(roles);

        assertUnique(assertThrows(ConflictException.class,
                () -> service.create(new CreateRoleRequest("driver", "Driver", List.of()))), "name");
        verify(roles, never()).save(any());
    }

    @Test
    void roleUpdatePrecheckMapsNormalizedNameToDtoName() {
        var roles = mock(TenantRoleRepository.class);
        UUID id = UUID.randomUUID();
        var existing = new TenantRole();
        existing.setName("dispatch");
        existing.setNormalizedName("DISPATCH");
        when(roles.findById(id)).thenReturn(Optional.of(existing));
        when(roles.existsByNormalizedName("DRIVER")).thenReturn(true);
        var service = new RoleService(roles);

        assertUnique(assertThrows(ConflictException.class,
                () -> service.update(id, new CreateRoleRequest("driver", "Driver", List.of()))), "name");
        verify(roles, never()).save(any());
        assertEquals("DISPATCH", existing.getNormalizedName());
    }

    private static CreateEmployeeRequest employeeInput() {
        return new CreateEmployeeRequest("used@example.com", "Test", "Driver", null, "SALARY", "ACTIVE",
                OffsetDateTime.parse("2026-10-13T00:00:00Z"), null, BigDecimal.ONE, "USD",
                null, null, null, null, null, null);
    }

    private static void assertUnique(ConflictException exception, String field) {
        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("DUPLICATE_VALUE", exception.getCode());
        assertEquals(1, exception.getErrors().size());
        assertEquals(field, exception.getErrors().getFirst().field());
        assertEquals("UNIQUE", exception.getErrors().getFirst().code());
        assertEquals("This value is already in use", exception.getErrors().getFirst().message());
    }
}
