package com.company.logicstic.integration;

import com.company.logicstic.config.TenantContext;
import com.company.logicstic.config.TenantRoutingDataSource;
import com.company.logicstic.dto.payment.CreatePaymentRequest;
import com.company.logicstic.integration.lark.auth.LarkAuthService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.config.import=","spring.flyway.enabled=false","app.tenancy.enabled=false","app.lark.base.enabled=false"})
@Import(CurrentUserApiPostgresTest.Tenants.class)
@EnabledIfEnvironmentVariable(named="TASK_DB_URL",matches="jdbc:postgresql:.*codex_.*")
class HttpRequestValidationPostgresTest {
    @Autowired WebApplicationContext web;
    @Autowired TenantRoutingDataSource routing;
    @Autowired LarkAuthService tokens;
    private final JsonMapper json=JsonMapper.builder().build();
    private MockMvc mvc(){return MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();}
    private JdbcTemplate db(){return new JdbcTemplate(routing.getRegisteredDataSource("identity-a").orElseThrow());}
    private String bearer(String role){return "Bearer "+tokens.createInternalToken("fixture","validation@v.test","identity-a",List.of(role),"Validation",null);}
    private org.springframework.test.web.servlet.ResultActions postBody(String path,Object body) throws Exception {
        return mvc().perform(post(path).header("Authorization",bearer("ADMIN")).header("X-Request-Id","validation-fixture").contentType("application/json").content(json.writeValueAsString(body)));
    }
    private void invalid(String path,Object body,String field) throws Exception {
        postBody(path,body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field",hasItem(field))).andExpect(jsonPath("$.errors[*].code",not(empty())))
                .andExpect(jsonPath("$.meta.requestId").value("validation-fixture")).andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }
    private Map<String,Integer> counts(){var result=new TreeMap<String,Integer>();for(String table:List.of("invoices","payments","payment_command_events","optimization_policy_versions","optimization_qualified_inputs","optimization_runs","settlements","load_pickup_business_date_changes"))result.put(table,db().queryForObject("select count(*) from "+table,Integer.class));return result;}
    @AfterEach void clear(){TenantContext.clear();}
    @Test void allNamedAndRemainingCommandControllersValidateMissingFieldsBeforeAnyWrite() throws Exception {
        var before=counts();String id=UUID.randomUUID().toString();
        var routes=Map.ofEntries(
                Map.entry("/api/rating/contracts","customerId"),Map.entry("/api/rating/rules","baseRate"),
                Map.entry("/api/loads/"+id+"/rating/accept","rating"),Map.entry("/api/loads/"+id+"/rating/contract-mileage","originalValue"),
                Map.entry("/api/invoices/tax-assessments","taxAmount"),Map.entry("/api/fleet/status-events","truckId"),
                Map.entry("/api/invoices/billing/primary","snapshotId"),Map.entry("/api/invoices/billing/"+id+"/regenerate","generation"),
                Map.entry("/api/invoices/billing/"+id+"/issue","idempotencyKey"),Map.entry("/api/invoices/billing/"+id+"/supplemental","chargeIds"),
                Map.entry("/api/invoices/billing/"+id+"/credit","lines"),Map.entry("/api/invoices/billing/"+id+"/rebill","creditEvidenceIds"),
                Map.entry("/api/optimization/policies","code"),Map.entry("/api/optimization/qualified-inputs","scope"),Map.entry("/api/optimization/qualified-inputs/forecasts","scope"),
                Map.entry("/api/optimization/runs","targets"),Map.entry("/api/optimization/runs/"+id+"/assignments/"+id+"/accept","expectedInputFingerprint"),
                Map.entry("/api/driver-settlements/"+id+"/recalculate-revenue","expectedSnapshotId"),Map.entry("/api/driver-settlements/"+id+"/billing-adjustments","affectedDocumentId"));
        for(var route:routes.entrySet())invalid(route.getKey(),route.getKey().equals("/api/optimization/policies")?Map.of("version",1):Map.of(),route.getValue());
        mvc().perform(post("/api/loads/"+id+"/requested-pickup-business-date").header("Authorization",bearer("ADMIN")).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertEquals(before,counts());
    }
    private Map<String,Object> tax(){return Map.of("requirement","NOT_REQUIRED","reasonCode","EXEMPT","reason","Documented exemption","sourceReference","Approved decision");}
    private Map<String,Object> generation(){return Map.of("idempotencyKey"," ","snapshotId",UUID.randomUUID(),"taxDecision",tax(),"lineTaxes",List.of());}
    @Test void nestedGenerationListsRatingAndProvenanceExposeFieldPaths() throws Exception {
        var before=counts();String id=UUID.randomUUID().toString();
        invalid("/api/invoices/billing/"+id+"/regenerate",Map.of("expectedSnapshotId",UUID.randomUUID(),"generation",generation()),"generation.idempotencyKey");
        invalid("/api/invoices/billing/"+id+"/credit",Map.of("idempotencyKey","credit","taxDecision",tax(),"reasonCode","CORRECTION","reason","Verified correction","lines",List.of(Map.of("originalLineId",UUID.randomUUID(),"amount",-1,"taxAmount",0))),"lines[0].amount");
        invalid("/api/loads/"+id+"/rating/accept",Map.of("idempotencyKey","rating","expectedInputHash","hash","expectedResultHash","hash","rating",Map.of("contractVersion",0)),"rating.contractVersion");
        mvc().perform(post("/api/loads/"+id+"/requested-pickup-business-date").header("Authorization",bearer("ADMIN")).contentType("application/json").content(json.writeValueAsString(Map.of("requestedPickupBusinessDate","2026-10-06","provenance",Map.of("reasonCode"," ","reason","Verified","source","Approved")))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[*].field",hasItem("provenance.reasonCode")));
        invalid("/api/optimization/runs",Map.of("idempotencyKey","run","policyId",UUID.randomUUID(),"targets",List.of(Map.of("tripId",UUID.randomUUID(),"ratingSnapshotId",UUID.randomUUID(),"pickupStopId",UUID.randomUUID())),"driverIds",List.of(UUID.randomUUID()),"truckIds",List.of(UUID.randomUUID()),"sourceSelections",List.of()),"targets[0].loadId");
        var scope=Map.of("loadId",UUID.randomUUID(),"tripId",UUID.randomUUID(),"driverId",UUID.randomUUID(),"truckId",UUID.randomUUID());
        var capacity=new HashMap<String,Object>();capacity.put("id",UUID.randomUUID());capacity.put("policyId",UUID.randomUUID());capacity.put("kind","CAPACITY");capacity.put("scope",scope);capacity.put("source",Map.of("type","TEST","reference","fixture","version","1","classification","AUTHORITATIVE_DB"));capacity.put("unit","POUND");capacity.put("observedAt",Instant.now().toString());capacity.put("expiresAt",Instant.now().plusSeconds(3600).toString());capacity.put("approvalReference","APPROVED");capacity.put("reasonCode","VERIFIED");capacity.put("reason","Verified evidence");capacity.put("capacity",Map.of("cargo",Map.of("value",-1,"unit","POUND"),"truck",Map.of("value",100,"unit","POUND")));
        invalid("/api/optimization/qualified-inputs",capacity,"capacity.cargo.value");assertEquals(before,counts());
    }
    @Test void malformedAndForbiddenRequestsHaveNormalizedEnvelopeWithAuthenticationFirst() throws Exception {
        var before=counts();String path="/api/invoices/billing/primary";
        mvc().perform(post(path).contentType("application/json").content("{" )).andExpect(status().isUnauthorized());
        mvc().perform(post(path).header("Authorization",bearer("DRIVER")).contentType("application/json").content("{" )).andExpect(status().isForbidden());
        mvc().perform(post(path).header("Authorization",bearer("ADMIN")).header("X-Request-Id","malformed-fixture").contentType("application/json").content("{" ))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST")).andExpect(jsonPath("$.meta.requestId").value("malformed-fixture"));
        assertEquals(before,counts());
    }
    @Test void optionalRatingSelectorsAndProviderErrorCallbackRemainValidTransport() throws Exception {
        postBody("/api/loads/"+UUID.randomUUID()+"/rating/preview",Map.of("contextSource","Approved fixture","accessorialIds",List.of(),"currency","USD")).andExpect(status().isNotFound());
        mvc().perform(post("/api/auth/lark/callback").contentType("application/json").content("{\"error\":\"access_denied\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc().perform(post("/api/auth/lark/callback").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
    @Test void validTransportWithCurrencyBusinessFailureUses422AndCannotWrite() throws Exception {
        UUID actor=UUID.randomUUID(),invoice=UUID.randomUUID();String email="validation@v.test";
        db().update("insert into employees(id,email,first_name,last_name,salary_type,status,joined_date,salary_amount,salary_currency) values (?,?,'Validation','Fixture','HOURLY','ACTIVE',now(),0,'USD')",actor,email);
        db().update("insert into invoices(id,type,status,tax_behavior,subtotal_amount,subtotal_currency,tax_total_amount,tax_total_currency,total_amount,total_currency) values (?,'FREIGHT','ISSUED','exclusive',100,'USD',0,'USD',100,'USD')",invoice);
        var before=counts();var payment=new CreatePaymentRequest("PENDING",invoice,new BigDecimal("10.00"),"EUR",null,null,UUID.randomUUID().toString(),null,null,null,"Address",null,"City","State","00000","US");
        postBody("/api/payments",payment).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("CURRENCY_MISMATCH")).andExpect(jsonPath("$.meta.requestId").value("validation-fixture"));
        assertEquals(before,counts());
    }
}
