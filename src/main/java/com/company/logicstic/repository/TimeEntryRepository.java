package com.company.logicstic.repository;

import com.company.logicstic.entity.TimeEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, UUID> {
    List<TimeEntry> findByEmployeeIdAndDateGreaterThanEqualAndDateLessThan(UUID employeeId, OffsetDateTime from, OffsetDateTime to);
}
