package com.company.logicstic.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.company.logicstic.dto.truck.CreateTruckRequest;
import com.company.logicstic.entity.Employee;
import com.company.logicstic.exception.ResourceNotFoundException;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.repository.TruckRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.company.logicstic.entity.Truck;

class TruckServiceDriverTypeTest {
    private final TruckRepository trucks = mock(TruckRepository.class);
    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final TruckService service = new TruckService(trucks, employees);

    @Test
    void officeStaffCannotBeAssignedToTruck() {
        UUID officeStaffId = UUID.randomUUID();
        when(employees.findById(officeStaffId)).thenReturn(Optional.of(new Employee()));
        when(trucks.existsByNumber("TRUCK-1")).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> service.create(new CreateTruckRequest(
                "TRUCK-1", "TRACTOR", 1, "ACTIVE", null, null, null, null, null, null,
                false, officeStaffId, null, false, "", null)));
        verify(trucks, never()).save(any(Truck.class));
    }
}
