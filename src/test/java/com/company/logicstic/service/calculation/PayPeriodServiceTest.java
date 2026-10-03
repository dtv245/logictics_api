package com.company.logicstic.service.calculation;

import com.company.logicstic.repository.PayPeriodRepository;
import com.company.logicstic.service.payroll.PayPeriodService;
import com.company.logicstic.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PayPeriodServiceTest {
    final PayPeriodRepository periods = mock(PayPeriodRepository.class);
    final PayPeriodService service = new PayPeriodService(periods);
    @Test void createsOpenPeriodWithInclusiveOrderedDates() {
        when(periods.save(any())).thenAnswer(i -> i.getArgument(0));
        var p = service.create("JAN",LocalDate.of(2026,1,1),LocalDate.of(2026,1,31),LocalDate.of(2026,2,5));
        assertEquals("OPEN",p.getStatus()); assertEquals(LocalDate.of(2026,2,5),p.getPaymentDate());
    }
    @Test void rejectsDuplicateCodeAndInvalidDates() {
        var date = LocalDate.of(2026,1,1);
        assertThrows(BadRequestException.class, () -> service.create("JAN",date,date.minusDays(1),null));
        assertThrows(BadRequestException.class, () -> service.create("JAN",null,date,null));
        when(periods.existsByPeriodCode("JAN")).thenReturn(true);
        assertThrows(BadRequestException.class, () -> service.create("JAN",date,date,null));
    }
}
