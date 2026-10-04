package com.company.logicstic.service.payroll;

import com.company.logicstic.entity.PayrollRun;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.repository.PayrollRunItemRepository;
import com.company.logicstic.repository.PayrollRunRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PayrollRunCompletionService {
    private static final Set<String> TERMINAL_ITEM_STATES = Set.of("PAID", "NO_PAYMENT_REQUIRED");
    private final PayrollRunItemRepository items;
    private final PayrollRunRepository runs;

    /** Marks a run complete exactly once after every item reaches its own terminal outcome. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean completeIfReady(PayrollRun run, UUID actor, String source) {
        if ("COMPLETED".equals(run.getStatus())) return false;
        var runItems = items.findByPayrollRunIdOrderById(run.getId());
        if (runItems.isEmpty() || runItems.stream().anyMatch(item -> !TERMINAL_ITEM_STATES.contains(item.getStatus()))) {
            return false;
        }
        if (!Set.of("LOCKED", "PAYMENT_SCHEDULED").contains(run.getStatus())) {
            throw new BadRequestException("PAYROLL_RUN_COMPLETION_STATE_INVALID", "Only a locked payroll run can complete");
        }
        if (!Set.of("PAYMENT_PROVIDER", "BANK_RECONCILIATION", "NO_PAYMENT_DISPOSITION").contains(source)) {
            throw new BadRequestException("PAYROLL_RUN_COMPLETION_SOURCE_INVALID", "An explicit completion source is required");
        }
        run.setStatus("COMPLETED");
        run.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS));
        run.setCompletedBy(actor);
        run.setCompletionSource(source);
        runs.saveAndFlush(run);
        return true;
    }
}
