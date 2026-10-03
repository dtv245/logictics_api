package com.company.logicstic.service.payroll;

import com.company.logicstic.entity.PayPeriod;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.repository.PayPeriodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;

@Service @RequiredArgsConstructor
public class PayPeriodService {
    private final PayPeriodRepository periods;
    @Transactional(readOnly = true)
    public List<PayPeriod> list() { return periods.findAllByOrderByStartDateDesc(); }
    @Transactional
    public PayPeriod create(String code, LocalDate start, LocalDate end, LocalDate payment) {
        if (code == null || code.isBlank() || code.length() > 50 || start == null || end == null || end.isBefore(start))
            throw new BadRequestException("INVALID_PAY_PERIOD", "Code and ordered start/end dates are required");
        if (periods.existsByPeriodCode(code)) throw new BadRequestException("PAY_PERIOD_EXISTS", "Pay period code already exists");
        PayPeriod period = new PayPeriod(); period.setPeriodCode(code); period.setStartDate(start); period.setEndDate(end);
        period.setPaymentDate(payment); period.setStatus("OPEN"); return periods.save(period);
    }
}
