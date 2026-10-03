package com.company.logicstic.service.calculation;

import com.company.logicstic.entity.DriverPayPolicy;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.repository.DriverPayPolicyRepository;
import com.company.logicstic.service.payroll.DriverPayPolicyResolver;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DriverPayPolicyResolverTest {
    final DriverPayPolicyRepository policies = mock(DriverPayPolicyRepository.class);
    final DriverPayPolicyResolver resolver = new DriverPayPolicyResolver(policies);
    final UUID driver = UUID.randomUUID(); final LocalDate date = LocalDate.of(2026,3,1);
    DriverPayPolicy policy(String code, LocalDate to) {
        DriverPayPolicy p = new DriverPayPolicy(); p.setId(UUID.randomUUID()); p.setPolicyCode(code); p.setEffectiveTo(to); return p;
    }
    @Test void driverPolicyOverridesDefaultAndNewestFamilyVersionWins() {
        var newer = policy("A",null); var historical = policy("A",null);
        when(policies.findDriverPoliciesAt(driver,date)).thenReturn(List.of(newer,historical));
        assertSame(newer,resolver.resolve(driver,date)); verify(policies,never()).findDefaultPoliciesAt(any());
    }
    @Test void expiredNewVersionNeverResurrectsHistoricalOpenVersion() {
        when(policies.findDriverPoliciesAt(driver,date)).thenReturn(List.of(policy("A",date.minusDays(1)),policy("A",null)));
        var fallback = policy("DEFAULT",null); when(policies.findDefaultPoliciesAt(date)).thenReturn(List.of(fallback));
        assertSame(fallback,resolver.resolve(driver,date));
        when(policies.findDefaultPoliciesAt(date)).thenReturn(List.of());
        assertEquals("DRIVER_PAY_POLICY_UNAVAILABLE",assertThrows(BadRequestException.class, () -> resolver.resolve(driver,date)).getCode());
    }
    @Test void inclusiveEndDateIsEligibleButIndependentPolicyFamiliesAreAmbiguous() {
        var ending = policy("A",date); when(policies.findDriverPoliciesAt(driver,date)).thenReturn(List.of(ending));
        assertSame(ending,resolver.resolve(driver,date));
        when(policies.findDriverPoliciesAt(driver,date)).thenReturn(List.of(ending,policy("B",null)));
        assertEquals("DRIVER_PAY_POLICY_AMBIGUOUS",assertThrows(BadRequestException.class, () -> resolver.resolve(driver,date)).getCode());
    }
    @Test void missingPolicyAndInputFailClosed() {
        assertThrows(BadRequestException.class, () -> resolver.resolve(driver,date));
        assertThrows(BadRequestException.class, () -> resolver.resolve(null,date));
        when(policies.findDefaultPoliciesAt(date)).thenReturn(List.of(policy("A",null),policy("B",null)));
        assertThrows(BadRequestException.class, () -> resolver.resolve(driver,date));
    }
}
