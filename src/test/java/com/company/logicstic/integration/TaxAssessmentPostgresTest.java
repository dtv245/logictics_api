package com.company.logicstic.integration;

import com.company.logicstic.dto.invoice.TaxAssessmentRequest;
import com.company.logicstic.exception.ApiException;
import com.company.logicstic.service.billing.TaxAssessmentService;
import com.company.logicstic.service.billing.domain.TaxAssessment;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.config.import=","spring.flyway.enabled=true","spring.flyway.locations=classpath:db/migration/tenant","app.tenancy.enabled=false","app.lark.base.enabled=false"})
@EnabledIfEnvironmentVariable(named="TASK_DB_URL",matches="jdbc:postgresql:.*codex_.*")
class TaxAssessmentPostgresTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry p){p.add("spring.datasource.url",()->System.getenv("TASK_DB_URL"));p.add("spring.datasource.username",()->System.getenv("TASK_DB_USER"));p.add("spring.datasource.password",()->System.getenv("TASK_DB_PASSWORD"));}
    @Autowired JdbcTemplate jdbc;@Autowired TaxAssessmentService service;
    @Autowired org.springframework.web.context.WebApplicationContext web;
    @Autowired tools.jackson.databind.ObjectMapper json;
    private record Fixture(UUID load,UUID customer,UUID actor,String email) { }
    private Fixture fixture() {
        UUID load=UUID.randomUUID(),customer=UUID.randomUUID(),actor=UUID.randomUUID();String email=actor+"@tax.invalid";
        jdbc.update("insert into customers(id,name,status,is_vat_exempt) values (?,'Tax assessment','ACTIVE',false)",customer);
        jdbc.update("insert into employees(id,email,first_name,last_name,salary_type,status,joined_date,salary_amount,salary_currency) values (?,?,'Tax','Audit','HOURLY','ACTIVE',now(),0,'USD')",actor,email);
        jdbc.update("""
            insert into loads(id,name,type,status,distance,is_in_proximity,customer_id,source,is_hazmat,delivery_cost_amount,delivery_cost_currency,
            destination_address_city,destination_address_country,destination_address_line1,destination_address_state,destination_address_zip_code,
            destination_location_latitude,destination_location_longitude,origin_address_city,origin_address_country,origin_address_line1,
            origin_address_state,origin_address_zip_code,origin_location_latitude,origin_location_longitude)
            values (?,'Tax assessment','FTL','DELIVERED',0,false,?,'MANUAL',false,0,'USD',
            'City','US','Address','TX','00000',0,0,'City','US','Address','TX','00000',0,0)
            """,load,customer);
        return new Fixture(load,customer,actor,email);
    }
    private TaxAssessmentRequest request(Fixture f,UUID id,String tax) {
        return new TaxAssessmentRequest(id,f.load(),f.customer(),"ACCOUNTING","signed accounting reference",
                "accounting jurisdiction",new BigDecimal("100"),new BigDecimal(tax),"USD","accounting-policy-v1",
                OffsetDateTime.parse("2026-01-12T09:00:00+07:00"),"original assessor");
    }
    @Test void persistsExactAuditAndRejectsHistoryMutationAndInvalidDatabaseInputs() {
        var f=fixture();var a=service.capture(request(f,UUID.randomUUID(),"0"),f.actor());
        assertEquals(f.actor(),a.capturedBy());assertEquals("original assessor",a.assessment().assessedBy());
        assertEquals(OffsetDateTime.parse("2026-01-12T02:00:00Z"),a.assessment().assessedAt());
        assertThrows(DataAccessException.class,()->jdbc.update("update invoice_tax_assessments set tax_amount=10 where id=?",a.assessment().assessmentId()));
        assertThrows(DataAccessException.class,()->jdbc.update("delete from invoice_tax_assessments where id=?",a.assessment().assessmentId()));
        assertThrows(DataAccessException.class,()->jdbc.update("""
            insert into invoice_tax_assessments select ?,load_id,customer_id,source_type,source_reference,jurisdiction,
            taxable_basis,-1,currency,policy_version,assessed_at,assessed_by,captured_by,captured_at,input_hash
            from invoice_tax_assessments where id=?
            """,UUID.randomUUID(),a.assessment().assessmentId()));
    }
    @Test void concurrentCaptureReplaysAndDifferentInputsConflict() throws Exception {
        var f=fixture();var request=request(f,UUID.randomUUID(),"12.34");var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            Callable<TaxAssessment> call=()->{start.await();return service.capture(request,f.actor());};
            var one=pool.submit(call);var two=pool.submit(call);start.countDown();var a=one.get(20,TimeUnit.SECONDS);var b=two.get(20,TimeUnit.SECONDS);
            assertEquals(a,b);assertEquals(1,jdbc.queryForObject("select count(*) from invoice_tax_assessments where id=?",Integer.class,request.assessmentId()));
            var ex=assertThrows(ApiException.class,()->service.capture(request(f,request.assessmentId(),"12.35"),f.actor()));
            assertEquals("TAX_ASSESSMENT_CONFLICT",ex.getCode());assertEquals(409,ex.getStatus().value());
        } finally { pool.shutdownNow(); }
    }
    @Test void tenantOwnedReferencesAndCustomerContextCannotBeForged() {
        var f=fixture();var wrong=new TaxAssessmentRequest(UUID.randomUUID(),f.load(),UUID.randomUUID(),"ACCOUNTING","reference","jurisdiction",
                BigDecimal.ZERO,BigDecimal.ZERO,"USD",null,OffsetDateTime.parse("2026-01-01T00:00:00Z"),"assessor");
        assertEquals("INVALID_TAX_ASSESSMENT",assertThrows(ApiException.class,()->service.capture(wrong,f.actor())).getCode());
        assertThrows(ApiException.class,()->service.capture(request(f,UUID.randomUUID(),"1"),UUID.randomUUID()));
        assertThrows(ApiException.class,()->service.get(UUID.randomUUID()));
    }
    @Test void apiRequiresAccountingAndDerivesCaptureActor() throws Exception {
        var f=fixture();var body=request(f,UUID.randomUUID(),"10.25");
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(web).apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/invoices/tax-assessments")
            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(f.email()).roles("DRIVER"))
            .contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/invoices/tax-assessments")
            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(f.email()).roles("ACCOUNTANT"))
            .contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.capturedBy").value(f.actor().toString()));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/invoices/tax-assessments/"+body.assessmentId())
            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(f.email()).roles("DISPATCHER")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
    }
}
