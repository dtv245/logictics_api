package com.company.logicstic.repository;

import com.company.logicstic.dto.rating.RateRuleRequest;
import com.company.logicstic.dto.rating.RatingContractRequest;
import com.company.logicstic.service.rating.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Explicit JDBC keeps unrestricted decimal policy inputs intact (no ORM scale coercion). */
@Repository @RequiredArgsConstructor
public class RatePolicyRepository {
    private final JdbcTemplate jdbc;

    public boolean customerExists(UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from customers where id=?)", Boolean.class, id));
    }
    public boolean actorExists(UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from employees where id=?)", Boolean.class, id));
    }
    public void createContractIdentity(UUID id, UUID customer, UUID actor, OffsetDateTime at) {
        jdbc.update("insert into customer_rating_contracts(id,customer_id,created_by,created_at) values (?,?,?,?)", id, customer, actor, at);
    }
    public boolean lockContract(UUID id) {
        return !jdbc.query("select id from customer_rating_contracts where id=? for update", (rs, n) -> rs.getObject(1, UUID.class), id).isEmpty();
    }
    public int latestContractVersion(UUID id) {
        return jdbc.queryForObject("select coalesce(max(contract_version),0) from customer_rating_contract_versions where contract_id=?", Integer.class, id);
    }
    public void appendContract(UUID id, int version, RatingContractRequest r, String currency, UUID actor, OffsetDateTime at) {
        jdbc.update("""
                insert into customer_rating_contract_versions(contract_id,contract_version,customer_id,currency,
                effective_from,effective_to,created_by,created_at) values (?,?,?,?,?,?,?,?)
                """, id, version, r.customerId(), currency, r.effectiveFrom(), r.effectiveTo(), actor, at);
    }
    public Optional<RatingContract> contract(UUID id, int version) {
        return jdbc.query("select * from customer_rating_contract_versions where contract_id=? and contract_version=?",
                (rs, n) -> new RatingContract(uuid(rs,"contract_id"), rs.getInt("contract_version"), uuid(rs,"customer_id"),
                        rs.getString("currency"), rs.getObject("effective_from", LocalDate.class), rs.getObject("effective_to", LocalDate.class),
                        uuid(rs,"created_by"), rs.getObject("created_at", OffsetDateTime.class)), id, version).stream().findFirst();
    }
    public void createRuleIdentity(UUID id, UUID actor, OffsetDateTime at) {
        jdbc.update("insert into customer_rate_rules(id,created_by,created_at) values (?,?,?)", id, actor, at);
    }
    public boolean lockRule(UUID id) {
        return !jdbc.query("select id from customer_rate_rules where id=? for update", (rs, n) -> rs.getObject(1, UUID.class), id).isEmpty();
    }
    public int latestRuleVersion(UUID id) {
        return jdbc.queryForObject("select coalesce(max(rule_version),0) from customer_rate_rule_versions where rule_id=?", Integer.class, id);
    }
    public void appendRule(UUID id, int version, RateRuleRequest r, String currency, UUID actor, OffsetDateTime at) {
        var f = r.fsc();
        jdbc.update("""
                insert into customer_rate_rule_versions(rule_id,rule_version,priority,customer_id,contract_id,contract_version,
                lane,equipment,service,tier,currency,effective_from,effective_to,rating_method,base_rate,
                linehaul_mileage_basis,minimum_charge,maximum_charge,fsc_method,fsc_mileage_basis,index_provider,index_region,
                max_index_age_days,contract_mpg,base_fuel_price,fuel_price_currency,fuel_price_unit,
                rounding_policy_code,rounding_policy_version,created_by,created_at)
                values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'RatingPolicyV1',1,?,?)
                """, id, version, r.priority(), r.customerId(), r.contractId(), r.contractVersion(),
                r.lane(), r.equipment(), r.service(), r.tier(), currency, r.effectiveFrom(), r.effectiveTo(), r.method().name(),
                r.baseRate(), name(r.linehaulMileageBasis()), r.minimumCharge(), r.maximumCharge(),
                f == null ? null : "INDEX_BASED_MPG", f == null ? null : name(f.mileageBasis()),
                f == null ? null : f.indexProvider(), f == null ? null : f.indexRegion(), f == null ? null : f.maxIndexAgeDays(),
                f == null ? null : f.contractMpg(), f == null ? null : f.baseFuelPrice(),
                f == null ? null : f.priceCurrency(), f == null ? null : f.priceUnit(), actor, at);
    }
    public Optional<RateRule> rule(UUID id, int version) {
        return jdbc.query("select * from customer_rate_rule_versions where rule_id=? and rule_version=?", RULE, id, version).stream().findFirst();
    }
    public List<RateRule> effectiveRules(LocalDate date) {
        return jdbc.query("""
                select * from customer_rate_rule_versions where effective_from <= ?
                and (effective_to is null or effective_to >= ?)
                """, RULE, date, date);
    }
    private static String name(Enum<?> v) { return v == null ? null : v.name(); }
    private static UUID uuid(ResultSet rs, String column) throws SQLException { return rs.getObject(column, UUID.class); }
    private static RatingMileageBasis basis(String value) { return value == null ? null : RatingMileageBasis.valueOf(value); }
    private static final RowMapper<RateRule> RULE = (rs, n) -> new RateRule(
            uuid(rs,"rule_id"), rs.getInt("rule_version"), rs.getInt("priority"), uuid(rs,"customer_id"),
            uuid(rs,"contract_id"), rs.getObject("contract_version", Integer.class), rs.getString("lane"),
            rs.getString("equipment"), rs.getString("service"), rs.getString("tier"), rs.getString("currency"),
            rs.getObject("effective_from", LocalDate.class), rs.getObject("effective_to", LocalDate.class),
            RatingMethod.valueOf(rs.getString("rating_method")), rs.getBigDecimal("base_rate"),
            basis(rs.getString("linehaul_mileage_basis")), rs.getBigDecimal("minimum_charge"), rs.getBigDecimal("maximum_charge"),
            rs.getString("fsc_method") == null ? null : new IndexBasedFscPolicy(basis(rs.getString("fsc_mileage_basis")),
                    rs.getString("index_provider"),rs.getString("index_region"),rs.getObject("max_index_age_days",Integer.class),
                    rs.getBigDecimal("contract_mpg"),rs.getBigDecimal("base_fuel_price"),rs.getString("fuel_price_currency"),rs.getString("fuel_price_unit")),
            rs.getString("rounding_policy_code"),rs.getInt("rounding_policy_version"),uuid(rs,"created_by"),rs.getObject("created_at",OffsetDateTime.class));
}
