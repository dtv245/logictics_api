package com.company.logicstic.service.rating;

import com.company.logicstic.dto.load.PickupBusinessDateProvenance;
import com.company.logicstic.dto.load.SetPickupBusinessDateRequest;
import com.company.logicstic.entity.Load;
import com.company.logicstic.exception.*;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.repository.LoadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class LoadPickupBusinessDateService {
    private final JdbcTemplate jdbc;
    private final EmployeeRepository employees;
    private final LoadRepository loads;

    /** Called inside the create/update Load transaction; mapper deliberately ignores this field. */
    @Transactional
    public void capture(Load load, LocalDate value, PickupBusinessDateProvenance p) {
        if (value == null) {
            if (p != null) throw new BadRequestException("LOAD_PICKUP_DATE_REQUIRED", "Date provenance requires an explicit LocalDate");
            return; // Omission retains history; appointment edits never change the business date.
        }
        UUID actor = actor(); validateProvenance(p);
        if (value.equals(load.getRequestedPickupBusinessDate())) return;
        UUID change = UUID.randomUUID();
        jdbc.update("""
                insert into load_pickup_business_date_changes(id,load_id,previous_change_id,previous_value,value,
                actor_id,corrected_at,reason_code,reason,provenance) values (?,?,?,?,?,?,?,?,?,?)
                """, change, load.getId(), load.getPickupBusinessDateChangeId(), load.getRequestedPickupBusinessDate(),
                value, actor, OffsetDateTime.now(ZoneOffset.UTC), p.reasonCode(), p.reason(), p.source());
        load.setRequestedPickupBusinessDate(value); load.setPickupBusinessDateChangeId(change);
    }

    @Transactional
    public com.company.logicstic.dto.load.LoadView remediate(UUID id, SetPickupBusinessDateRequest r) {
        if (r == null || r.requestedPickupBusinessDate() == null)
            throw new BadRequestException("LOAD_PICKUP_DATE_REQUIRED", "Explicit business LocalDate required");
        actor(); validateProvenance(r.provenance());
        Load load = loads.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Load not found"));
        if (!Objects.equals(r.expectedChangeId(), load.getPickupBusinessDateChangeId()))
            throw new ApiException(HttpStatus.CONFLICT, "LOAD_PICKUP_DATE_CONFLICT", "Pickup business date changed; reload its audited version");
        capture(load, r.requestedPickupBusinessDate(), r.provenance());
        return com.company.logicstic.dto.load.LoadView.from(loads.saveAndFlush(load));
    }
    private void validateProvenance(PickupBusinessDateProvenance p) {
        if (p == null || p.reasonCode() == null || p.reasonCode().isBlank() || p.reasonCode().length() > 80
                || p.reason() == null || p.reason().isBlank() || p.source() == null || p.source().isBlank())
            throw new BadRequestException("LOAD_PICKUP_DATE_PROVENANCE_REQUIRED", "Explicit reason code, reason and source are required");
    }
    private UUID actor() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        Set<String> roles = Set.of("ROLE_ADMIN", "ROLE_ACCOUNTANT", "ROLE_DISPATCHER");
        if (auth == null || !auth.isAuthenticated() || auth.getAuthorities().stream().noneMatch(a -> roles.contains(a.getAuthority())))
            throw new ForbiddenException("Authorized authenticated pickup-date actor required");
        return employees.findByEmail(auth.getName()).orElseThrow(() -> new ForbiddenException("Pickup-date actor must map to tenant employee")).getId();
    }
}
