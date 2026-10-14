package com.company.logicstic.exception;

import com.company.logicstic.dto.ApiError;
import com.company.logicstic.config.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import java.util.*;
import org.postgresql.util.PSQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Classifies existing exception metadata without reading a rollback-only transaction. */
public final class DatabaseFailures {
    private static final Logger log=LoggerFactory.getLogger(DatabaseFailures.class);
    public record Failure(int status,String code,String message,List<ApiError> errors,Map<String,Object> params,String sqlState,String constraint) {}
    private record FieldMapping(String resource,String field,String domainCode,boolean collectionOnly) {
        FieldMapping(String resource,String field,String domainCode) { this(resource,field,domainCode,false); }
    }
    private static final Map<String,FieldMapping> FIELDS=Map.ofEntries(
            Map.entry("ix_trucks_number",new FieldMapping("trucks","number",null)),
            Map.entry("ix_loads_number",new FieldMapping("loads","number",null)),
            Map.entry("ix_trips_number",new FieldMapping("trips","number",null)),
            Map.entry("ix_invoices_number",new FieldMapping("invoices","number",null)),
            Map.entry("ix_containers_number",new FieldMapping("containers","number",null)),
            Map.entry("ix_expenses_number",new FieldMapping("expenses","number",null)),
            Map.entry("pay_periods_period_code_key",new FieldMapping("pay-periods","periodCode",null)),
            // Version 1 uses an entered policyCode; /{id}/new-version fixes that code and generates the version.
            Map.entry("uq_driver_pay_policies_version",new FieldMapping("driver-pay-policies","policyCode",null,true)),
            // V27 replaces the V1 index with a partial index for ordinary, unrated invoices.
            Map.entry("ix_invoices_load_id",new FieldMapping("invoices","loadId",null)),
            Map.entry("ux_legacy_invoice_load",new FieldMapping("invoices","loadId",null)),
            Map.entry("uq_payroll_jurisdiction",new FieldMapping("payroll/configuration","jurisdiction",null)),
            Map.entry("fk_trucks_employees_main_driver_id",new FieldMapping("trucks","mainDriverId",null)),
            Map.entry("fk_trucks_employees_secondary_driver_id",new FieldMapping("trucks","secondaryDriverId",null)),
            Map.entry("fk_employees_tenant_role_role_id",new FieldMapping("employees","roleId",null)),
            Map.entry("fk_loads_customers_customer_id",new FieldMapping("loads","customerId",null)),
            Map.entry("fk_loads_truck_assigned_truck_id",new FieldMapping("loads","assignedTruckId",null)),
            Map.entry("fk_loads_employees_assigned_dispatcher_id",new FieldMapping("loads","assignedDispatcherId",null)),
            Map.entry("fk_invoices_customers_customer_id",new FieldMapping("invoices","customerId",null)),
            Map.entry("fk_invoices_employees_employee_id",new FieldMapping("invoices","employeeId",null)),
            Map.entry("fk_invoices_loads_load_id",new FieldMapping("invoices","loadId",null)),
            Map.entry("fk_payments_invoices_invoice_id",new FieldMapping("payments","invoiceId",null)),
            // Command identity conflicts belong in a toast, without marking an input as invalid.
            Map.entry("uq_payments_idempotency_key",new FieldMapping("payments",null,"PAYMENT_IDEMPOTENCY_CONFLICT")));
    private DatabaseFailures() {}
    public static Failure classify(Throwable exception,HttpServletRequest request) {
        String state=null, constraint=null;
        Set<Throwable> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<Throwable> queue=new ArrayDeque<>();queue.add(exception);
        while(!queue.isEmpty() && seen.size()<64) {
            Throwable cause=queue.removeFirst();if(!seen.add(cause))continue;
            if(cause instanceof org.hibernate.exception.ConstraintViolationException hibernate && hibernate.getConstraintName()!=null)
                constraint=hibernate.getConstraintName();
            if(cause instanceof SQLException sql) {
                if(sql.getSQLState()!=null)state=sql.getSQLState();
                if(sql.getNextException()!=null)queue.add(sql.getNextException());
            }
            if(cause instanceof PSQLException pg && pg.getServerErrorMessage()!=null)
                constraint=pg.getServerErrorMessage().getConstraint();
            if(cause.getCause()!=null)queue.add(cause.getCause());
        }
        if("40001".equals(state)||"40P01".equals(state))
            return new Failure(503,"TRANSACTION_RETRY_REQUIRED","Transaction was rolled back; retry the original command with the same idempotency key",
                    List.of(),Map.of("retryable",true),state,constraint);
        if(state!=null&&state.startsWith("08"))
            return new Failure(503,"DATABASE_UNAVAILABLE","Database is temporarily unavailable",List.of(),Map.of(),state,constraint);
        if(!"23505".equals(state)&&!"23503".equals(state))
            return new Failure(500,"INTERNAL_ERROR","An unexpected error occurred",List.of(),Map.of(),state,constraint);
        List<ApiError> errors=List.of();
        String code="23505".equals(state)?"DUPLICATE_VALUE":"DATA_INTEGRITY_VIOLATION";
        var mapping=constraint==null?null:FIELDS.get(constraint);
        if("23505".equals(state) && mapping==null)
            log.warn("unmapped_unique_constraint constraint={} path={} requestId={}",constraint,request.getRequestURI(),RequestIds.getOrCreate(request));
        // A FK failure while deleting its referenced parent is not an error in a child form field.
        if(mapping!=null && !"DELETE".equals(request.getMethod()) && matchesResource(request,mapping)) {
            if(mapping.field()!=null)errors=List.of(new ApiError(mapping.field(),"23505".equals(state)?"UNIQUE":"REFERENCE_CONFLICT",
                    "23505".equals(state)?"This value is already in use":"Referenced record is unavailable or still in use"));
            if(mapping.domainCode()!=null)code=mapping.domainCode();
        }
        return new Failure(409,code,"Cannot complete request due to a data conflict",errors,Map.of(),state,constraint);
    }
    private static boolean matchesResource(HttpServletRequest request,FieldMapping mapping) {
        String path=request.getRequestURI();
        String context=request.getContextPath();
        if(context!=null && !context.isEmpty() && path.startsWith(context+"/"))path=path.substring(context.length());
        String[] actual=path.split("/",-1), expected=("/api/"+mapping.resource()).split("/",-1);
        if(actual.length<expected.length)return false;
        for(int i=0;i<expected.length;i++)if(!expected[i].equals(actual[i]))return false;
        return !mapping.collectionOnly() || actual.length==expected.length
                || (actual.length==expected.length+1 && actual[actual.length-1].isEmpty());
    }
    public static boolean hasSqlCause(Throwable exception) {
        Set<Throwable> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        for(Throwable cause=exception;cause!=null&&seen.add(cause);cause=cause.getCause()) if(cause instanceof SQLException)return true;
        return false;
    }
}
