package com.company.logicstic.repository;

import com.company.logicstic.service.rating.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository @RequiredArgsConstructor
public class RatingMileageRepository {
    private final JdbcTemplate jdbc;
    public void capture(ContractMileageEvidence e) {
        jdbc.update("""
                insert into contract_load_rating_mileage(id,load_id,component_type,contract_id,contract_version,currency,
                original_value,original_unit,normalized_miles,provenance,captured_by,captured_at) values (?,?,?,?,?,?,?,?,?,?,?,?)
                """, e.id(), e.loadId(), e.componentType().name(), e.contractId(), e.contractVersion(), e.currency(),
                e.originalValue(), e.originalUnit(), e.normalizedMiles(), e.provenance(), e.capturedBy(), e.capturedAt());
    }
    public Optional<ContractMileageEvidence> evidence(UUID id) {
        return jdbc.query("select * from contract_load_rating_mileage where id=?", (rs, n) -> new ContractMileageEvidence(
                rs.getObject("id",UUID.class), rs.getObject("load_id",UUID.class), RatingMileageComponent.valueOf(rs.getString("component_type")),
                rs.getObject("contract_id",UUID.class), rs.getInt("contract_version"), rs.getString("currency"), rs.getBigDecimal("original_value"),
                rs.getString("original_unit"), rs.getBigDecimal("normalized_miles"), rs.getString("provenance"),
                rs.getObject("captured_by",UUID.class), rs.getObject("captured_at",OffsetDateTime.class)), id).stream().findFirst();
    }
    public boolean hasMultiLoadTrip(UUID loadId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from trip_stops own where own.load_id=?
                and exists(select 1 from trip_stops other where other.trip_id=own.trip_id and other.load_id <> own.load_id))
                """, Boolean.class, loadId));
    }
}
