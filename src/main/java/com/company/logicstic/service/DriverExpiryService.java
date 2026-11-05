package com.company.logicstic.service;

import com.company.logicstic.common.PaginationValidator;
import com.company.logicstic.dto.PagedResponse;
import com.company.logicstic.dto.driverprofile.DriverExpiringItemView;
import com.company.logicstic.dto.driverprofile.ExpiryStatus;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.repository.DriverExpiringItemRepository;
import com.company.logicstic.repository.DriverExpiringItemRepository.DriverExpiringItemRow;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DriverExpiryService {
    public static final int MAX_DAYS = 365;
    public static final int SUMMARY_EXPIRY_PAGE_SIZE = 50;
    private static final Set<String> TYPES = Set.of("LICENSE", "LICENSE_MEDICAL_CERTIFICATE", "HEALTH_CHECK",
            "CERTIFICATION", "CONTRACT", "HEALTH_INSURANCE", "DOCUMENT");

    private final DriverExpiringItemRepository repository;
    private final DriverExpiryStatusCalculator statusCalculator;

    public DriverExpiryService(DriverExpiringItemRepository repository,
                               DriverExpiryStatusCalculator statusCalculator) {
        this.repository = repository;
        this.statusCalculator = statusCalculator;
    }

    public PagedResponse<DriverExpiringItemView> list(int days, String type, int page, int pageSize) {
        if (days < 1 || days > MAX_DAYS) {
            throw new BadRequestException("INVALID_EXPIRY_DAYS", "days must be between 1 and 365");
        }
        LocalDate today = statusCalculator.currentDate();
        return findBetween(today, today.plusDays(days), normalizeType(type), null, page, pageSize);
    }

    public PagedResponse<DriverExpiringItemView> findBetween(LocalDate fromDate, LocalDate toDate,
                                                              String type, UUID driverId, int page, int pageSize) {
        if (fromDate == null || toDate == null || toDate.isBefore(fromDate)) {
            throw new BadRequestException("INVALID_EXPIRY_WINDOW", "Expiry date window is invalid");
        }
        String normalizedType = normalizeType(type);
        var pageable = PaginationValidator.pageRequest(page, pageSize, Sort.unsorted());
        var items = repository.findPage(fromDate, toDate, normalizedType, driverId,
                statusCalculator.timeZoneId(), pageable);
        long total = repository.count(fromDate, toDate, normalizedType, driverId, statusCalculator.timeZoneId());
        var mapped = items.stream().map(row -> toView(row, fromDate)).toList();
        return PagedResponse.from(new PageImpl<>(mapped, pageable, total));
    }

    public DriverExpiringItemView toView(DriverExpiringItemRow row, LocalDate today) {
        return new DriverExpiringItemView(row.itemId(), row.driverId(), row.driverName(), row.type(), row.itemName(),
                row.expiryDate(), ChronoUnit.DAYS.between(today, row.expiryDate()),
                statusCalculator.status(row.expiryDate(), today));
    }

    private static String normalizeType(String type) {
        if (type == null || type.isBlank()) return null;
        String normalized = type.trim().toUpperCase(Locale.ROOT);
        if (!TYPES.contains(normalized)) {
            throw new BadRequestException("INVALID_EXPIRY_TYPE", "Choose a supported driver expiry type");
        }
        return normalized;
    }
}
