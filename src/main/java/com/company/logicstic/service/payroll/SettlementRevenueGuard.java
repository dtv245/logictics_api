package com.company.logicstic.service.payroll;

import com.company.logicstic.entity.*;
import com.company.logicstic.exception.*;
import com.company.logicstic.repository.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** The Load lock serializes billing issue with settlement approval/finalization. */
@Service @RequiredArgsConstructor
public class SettlementRevenueGuard {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final DriverPayPolicyRepository policies;
    private final InvoiceRepository invoices;
    private final PercentagePayCalculator calculator;

    public void lockSources(UUID settlementId) {
        var loads=jdbc.queryForList("select distinct load_id from settlement_lines where settlement_id=? and line_type='REVENUE_PERCENT' and load_id is not null order by load_id",UUID.class,settlementId);
        for(UUID load:loads) jdbc.queryForList("select id from loads where id=? for update",UUID.class,load);
    }
    public List<PercentagePayCalculator.Result> frozen(DriverSettlement settlement) {
        var input=json.readTree(settlement.getCalculationSnapshot().getInputJson());var rows=input.get("percentageCalculations");
        if(rows==null || !rows.isArray()) return List.of();
        List<PercentagePayCalculator.Result> result=new ArrayList<>();
        for(var row:rows) result.add(json.treeToValue(row,PercentagePayCalculator.Result.class));return result;
    }
    public DriverPayPolicy policy(PercentagePayCalculator.Result old,SettlementLine line) {
        if(line.getBusinessDate()==null)throw new BadRequestException("SETTLEMENT_REVENUE_SOURCE_REQUIRED","Canonical earning business date missing; no invoice/current-date fallback");
        var p=policies.findById(old.policyId()).orElseThrow(()->new BadRequestException("SETTLEMENT_REVENUE_SOURCE_REQUIRED","Historical policy version missing"));
        if(!Objects.equals(p.getPolicyVersion(),old.policyVersion()) || !Objects.equals(p.getRevenueBasis(),old.revenueBasis())
                || p.getRevenuePercentage()==null || p.getRevenuePercentage().compareTo(old.ratio())!=0
                || line.getBusinessDate().isBefore(p.getEffectiveFrom()) || p.getEffectiveTo()!=null && line.getBusinessDate().isAfter(p.getEffectiveTo()))
            throw new BadRequestException("SETTLEMENT_REVENUE_SOURCE_REQUIRED","Frozen earning-date policy identity no longer proves original inputs");
        return p;
    }
    public SettlementLine line(PercentagePayCalculator.Result old,List<SettlementLine> lines) {
        var matching=lines.stream().filter(l->"REVENUE_PERCENT".equals(l.getLineType()) && old.invoiceId().equals(l.getSourceId()) && l.getLoad()!=null).toList();
        if(matching.size()!=1)throw new BadRequestException("SETTLEMENT_REVENUE_SOURCE_REQUIRED","Exactly one attributed revenue line required");return matching.getFirst();
    }
    public PercentagePayCalculator.Result current(PercentagePayCalculator.Result old,SettlementLine line) {
        return calculator.calculateBillingChain(invoices.findAllByLoadId(line.getLoad().getId()),policy(old,line));
    }
    public void requireFresh(DriverSettlement settlement,List<SettlementLine> lines) {
        if(!"ORIGINAL".equals(settlement.getSettlementType()))return;
        var frozen=frozen(settlement);
        if(frozen.isEmpty() && lines.stream().anyMatch(l->"REVENUE_PERCENT".equals(l.getLineType()))) throw stale();
        for(var old:frozen) {
            PercentagePayCalculator.Result current;
            try { current=current(old,line(old,lines)); } catch(ApiException e) {throw stale();}
            if(!sameEconomicInputs(old,current)) throw stale();
        }
    }
    public boolean sameEconomicInputs(PercentagePayCalculator.Result old,PercentagePayCalculator.Result current) {
        if(!old.invoiceId().equals(current.invoiceId()) || old.eligibleRevenue().compareTo(current.eligibleRevenue())!=0 || old.amount().compareTo(current.amount())!=0)return false;
        // Pre-V27 snapshots have a proven single invoice but no document array; no historical relabeling.
        if(old.documents()==null)return "INVOICE_SUBTOTAL".equals(old.revenueBasis());
        return economicSources(old).equals(economicSources(current));
    }
    private Map<UUID,String> economicSources(PercentagePayCalculator.Result r) {
        Map<UUID,String> values=new TreeMap<>();for(var d:r.documents())values.put(d.invoiceId(),d.purpose()+":"+d.economicSign()+":"+d.eligibleSubtotal().stripTrailingZeros().toPlainString());return values;
    }
    private ApiException stale() {return new ApiException(HttpStatus.CONFLICT,"SETTLEMENT_REVENUE_BASIS_STALE","Billing economic inputs changed; explicit recalculation and repeated approval required");}
}
