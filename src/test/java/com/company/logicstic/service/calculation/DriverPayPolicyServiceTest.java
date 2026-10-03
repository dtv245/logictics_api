package com.company.logicstic.service.calculation;

import com.company.logicstic.dto.payroll.DriverPayPolicyRequest;
import com.company.logicstic.entity.*;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.repository.*;
import com.company.logicstic.service.payroll.DriverPayPolicyService;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DriverPayPolicyServiceTest {
    final DriverPayPolicyRepository policies = mock(DriverPayPolicyRepository.class);
    final EmployeeRepository employees = mock(EmployeeRepository.class);
    final DriverPayPolicyService service = new DriverPayPolicyService(policies,employees);
    static DriverPayPolicyRequest request(String code, UUID driver, String method, String rate, String mileage, String revenue,
            LocalDate from, LocalDate to) {
        BigDecimal amount = rate == null ? null : new BigDecimal(rate);
        return new DriverPayPolicyRequest(code,"Test policy",driver,method,method.equals("PER_MILE")?amount:null,
                method.equals("PER_LOAD")?amount:null,method.equals("HOURLY")?amount:null,method.equals("DAILY")?amount:null,
                method.equals("FLAT_RATE")?amount:null,method.equals("PERCENT_REVENUE")?amount:null,mileage,revenue,
                null,null,null,null,null,"USD",from,to);
    }
    DriverPayPolicyRequest request(String method, String rate, String mileage, String revenue) {
        return request("TEST",null,method,rate,mileage,revenue,LocalDate.of(2026,1,1),null);
    }
    @Test void validatesEveryPayMethodWithExplicitRequiredInputs() {
        when(policies.save(any())).thenAnswer(i -> i.getArgument(0));
        for (var method : List.of("PER_MILE","PER_LOAD","HOURLY","DAILY","FLAT_RATE","PERCENT_REVENUE")) {
            var p = service.create(request(method,method.equals("PERCENT_REVENUE")?"0.25":"10","ACTUAL_ALL_MILES","INVOICE_SUBTOTAL"));
            assertEquals(method,p.getPayMethod()); assertEquals(1,p.getPolicyVersion()); assertEquals("USD",p.getCurrency());
            assertThrows(BadRequestException.class, () -> service.create(request(method,null,"ACTUAL_ALL_MILES","INVOICE_SUBTOTAL")));
        }
    }
    @Test void rejectsNegativeOrUnpersistableRatesAndWrongRatioConvention() {
        assertThrows(BadRequestException.class, () -> service.create(request("PER_MILE","-1","ACTUAL_ALL_MILES",null)));
        assertThrows(BadRequestException.class, () -> service.create(request("PER_MILE","0.1234567","ACTUAL_ALL_MILES",null)));
        assertThrows(BadRequestException.class, () -> service.create(request("PER_LOAD","0.12345",null,null)));
        assertThrows(BadRequestException.class, () -> service.create(request("PERCENT_REVENUE","25",null,"INVOICE_SUBTOTAL")));
    }
    @Test void refusesUnsourcedMileageAndRevenueBasesRatherThanGuessing() {
        for (var basis : List.of("ACTUAL_LOADED_MILES","PLANNED_LOADED_MILES","PRACTICAL_MILES","CONTRACT_MILES","UNKNOWN"))
            assertThrows(BadRequestException.class, () -> service.create(request("PER_MILE","1",basis,null)));
        assertThrows(BadRequestException.class, () -> service.create(request("PERCENT_REVENUE","0.25",null,"INVOICE_TOTAL")));
        assertThrows(BadRequestException.class, () -> service.create(request("PERCENT_REVENUE","0.25",null,"LINEHAUL")));
    }
    @Test void canonicalizesLegacyExplicitMileageAliasesWithoutChangingBasis() {
        when(policies.save(any())).thenAnswer(i -> i.getArgument(0));
        assertEquals("ACTUAL_ALL_MILES",service.create(request("PER_MILE","1","ACTUAL_MILES",null)).getMileageBasis());
        assertEquals("PLANNED_ALL_MILES",service.create(request("PER_MILE","1","PLANNED",null)).getMileageBasis());
    }
    @Test void newVersionAppendsAndNeverMutatesHistoricalVersion() {
        DriverPayPolicy old = new DriverPayPolicy(); old.setId(UUID.randomUUID()); old.setPolicyCode("TEST"); old.setPolicyVersion(1);
        old.setEffectiveFrom(LocalDate.of(2026,1,1)); old.setPerMileRate(new BigDecimal("1"));
        when(policies.findByIdForUpdate(old.getId())).thenReturn(Optional.of(old));
        when(policies.findTopByPolicyCodeOrderByPolicyVersionDesc("TEST")).thenReturn(Optional.of(old));
        when(policies.save(any())).thenAnswer(i -> i.getArgument(0));
        var fresh = service.newVersion(old.getId(),request("TEST",null,"PER_MILE","2","ACTUAL_ALL_MILES",null,LocalDate.of(2026,2,1),null));
        assertEquals(2,fresh.getPolicyVersion()); assertEquals(new BigDecimal("1"),old.getPerMileRate()); assertNull(old.getEffectiveTo());
        verify(policies,never()).save(old);
    }
    @Test void rejectsStaleVersionScopeChangesAndInvalidDates() {
        DriverPayPolicy old = new DriverPayPolicy(); old.setId(UUID.randomUUID()); old.setPolicyCode("TEST"); old.setEffectiveFrom(LocalDate.of(2026,1,1));
        DriverPayPolicy latest = new DriverPayPolicy(); latest.setId(UUID.randomUUID());
        when(policies.findByIdForUpdate(old.getId())).thenReturn(Optional.of(old));
        when(policies.findTopByPolicyCodeOrderByPolicyVersionDesc("TEST")).thenReturn(Optional.of(latest));
        assertThrows(BadRequestException.class, () -> service.newVersion(old.getId(),request("PER_LOAD","1",null,null)));
        when(policies.findTopByPolicyCodeOrderByPolicyVersionDesc("TEST")).thenReturn(Optional.of(old));
        assertThrows(BadRequestException.class, () -> service.newVersion(old.getId(),request("TEST",UUID.randomUUID(),"PER_LOAD","1",null,null,LocalDate.of(2026,2,1),null)));
        assertThrows(BadRequestException.class, () -> service.newVersion(old.getId(),request("PER_LOAD","1",null,null)));
        assertThrows(BadRequestException.class, () -> service.create(request("TEST",null,"PER_LOAD","1",null,null,LocalDate.of(2026,2,1),LocalDate.of(2026,1,1))));
    }
}
