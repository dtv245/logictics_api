package com.company.logicstic.integration;

import com.company.logicstic.config.TenantContext;
import com.company.logicstic.config.TenantRoutingDataSource;
import com.company.logicstic.dto.payment.CreatePaymentRequest;
import com.company.logicstic.integration.lark.auth.LarkAuthService;
import com.company.logicstic.service.PaymentService;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.config.import=","spring.flyway.enabled=false","app.tenancy.enabled=false","app.lark.base.enabled=false"})
@Import(CurrentUserApiPostgresTest.Tenants.class)
@EnabledIfEnvironmentVariable(named="TASK_DB_URL",matches="jdbc:postgresql:.*codex_.*")
class PaymentCommandPostgresTest {
    @Autowired WebApplicationContext context;
    @Autowired TenantRoutingDataSource routing;
    @Autowired LarkAuthService tokens;
    @Autowired PaymentService payments;
    @Autowired PlatformTransactionManager transactions;
    @Autowired jakarta.persistence.EntityManagerFactory entityManagers;
    private final JsonMapper json=JsonMapper.builder().build();
    private record Fixture(String tenant,UUID actor,String email,UUID invoice){}
    private record Result(int status,JsonNode body){}
    private MockMvc mvc(){return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}
    private JdbcTemplate db(String tenant){return new JdbcTemplate(routing.getRegisteredDataSource(tenant).orElseThrow());}
    private String bearer(Fixture f,String role){return "Bearer "+tokens.createInternalToken("subject",f.email(),f.tenant(),List.of(role),"Fixture",f.actor());}
    private Fixture fixture(String tenant){return fixture(tenant,db(tenant));}
    private Fixture fixture(String tenant,JdbcTemplate jdbc){
        UUID actor=UUID.randomUUID(),invoice=UUID.randomUUID();String email=actor+"@p.test";
        jdbc.update("insert into employees(id,email,first_name,last_name,salary_type,status,joined_date,salary_amount,salary_currency) values (?,?,'Payment','Fixture','HOURLY','ACTIVE',now(),0,'USD')",actor,email);
        jdbc.update("insert into invoices(id,type,status,tax_behavior,subtotal_amount,subtotal_currency,tax_total_amount,tax_total_currency,total_amount,total_currency) values (?,'FREIGHT','ISSUED','exclusive',100,'USD',0,'USD',100,'USD')",invoice);
        return new Fixture(tenant,actor,email,invoice);
    }
    private CreatePaymentRequest request(Fixture f,String key,String amount){return new CreatePaymentRequest("PENDING",f.invoice(),new BigDecimal(amount),"USD","Fixture","REF",key,null,null,null,"Address",null,"City","State","00000","US");}
    private Result create(Fixture f,CreatePaymentRequest request) throws Exception {
        var response=mvc().perform(post("/api/payments").header("Authorization",bearer(f,"ACCOUNTANT")).contentType("application/json").content(json.writeValueAsString(request)))
                .andReturn().getResponse();return new Result(response.getStatus(),json.readTree(response.getContentAsString()));
    }
    private UUID id(Result result){assertEquals(201,result.status(),result.body().toString());return UUID.fromString(result.body().get("data").get("id").asText());}
    @AfterEach void clear(){TenantContext.clear();SecurityContextHolder.clearContext();}

    @Test void sequentialReplayAndEveryMeaningfulCreationFieldHaveFrozenFingerprints() throws Exception {
        var f=fixture("identity-a");String key=UUID.randomUUID().toString();var request=request(f,key,"40.00");UUID id=id(create(f,request));
        assertEquals(id,id(create(f,request(f,key,"40.0"))));
        for(String field:List.of("description","referenceNumber","stripePaymentMethodId","stripePaymentIntentId","billingAddressLine1","billingAddressLine2","billingAddressCity","billingAddressState","billingAddressZipCode","billingAddressCountry")){
            var body=(tools.jackson.databind.node.ObjectNode)json.valueToTree(request);body.put(field,"Changed");
            mvc().perform(post("/api/payments").header("Authorization",bearer(f,"ACCOUNTANT")).contentType("application/json").content(json.writeValueAsString(body)))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PAYMENT_IDEMPOTENCY_CONFLICT"));
        }
        assertEquals(1,db(f.tenant()).queryForObject("select count(*) from payments where invoice_id=?",Integer.class,f.invoice()));
        assertEquals(1,db(f.tenant()).queryForObject("select count(*) from payment_command_events where payment_id=?",Integer.class,id));
        assertEquals(f.actor(),db(f.tenant()).queryForObject("select recorded_by_user_id from payments where id=?",UUID.class,id));
    }
    @Test void concurrentSameKeySameInputReturnsOneEffectAndSameId() throws Exception {
        var f=fixture("identity-a");var request=request(f,UUID.randomUUID().toString(),"60.00");var barrier=new CyclicBarrier(2);
        try(var pool=Executors.newFixedThreadPool(2)){
            var a=pool.submit(()->{barrier.await(10,TimeUnit.SECONDS);return create(f,request);});
            var b=pool.submit(()->{barrier.await(10,TimeUnit.SECONDS);return create(f,request);});
            assertEquals(id(a.get(30,TimeUnit.SECONDS)),id(b.get(30,TimeUnit.SECONDS)));
        }
        assertEquals(1,db(f.tenant()).queryForObject("select count(*) from payments where invoice_id=?",Integer.class,f.invoice()));
    }
    @Test void sameKeyDifferentPayloadConflictsAndDifferentKeysCannotOverReserveInvoice() throws Exception {
        for(boolean sameKey:List.of(true,false)){
            var f=fixture("identity-a");String key=UUID.randomUUID().toString();var barrier=new CyclicBarrier(2);
            try(var pool=Executors.newFixedThreadPool(2)){
                var a=pool.submit(()->{barrier.await(10,TimeUnit.SECONDS);return create(f,request(f,key,"60.00"));});
                var b=pool.submit(()->{barrier.await(10,TimeUnit.SECONDS);return create(f,request(f,sameKey?key:UUID.randomUUID().toString(),sameKey?"70.00":"60.00"));});
                var results=List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS));
                assertEquals(List.of(201,sameKey?409:422),results.stream().map(Result::status).sorted().toList());
                assertEquals(sameKey?"PAYMENT_IDEMPOTENCY_CONFLICT":"PAYMENT_EXCEEDS_INVOICE_BALANCE",results.stream().filter(r->r.status()!=201).findFirst().orElseThrow().body().get("code").asText());
            }
            assertEquals(1,db(f.tenant()).queryForObject("select count(*) from payments where invoice_id=?",Integer.class,f.invoice()));
        }
    }
    @Test void invalidTransportAndBusinessAmountsCannotInsertAndCurrencyHasDomain422() throws Exception {
        var f=fixture("identity-a");var r=request(f,UUID.randomUUID().toString(),"10.00");
        for(String field:List.of("invoiceId","idempotencyKey")){
            var body=(tools.jackson.databind.node.ObjectNode)json.valueToTree(r);body.remove(field);
            mvc().perform(post("/api/payments").header("Authorization",bearer(f,"ACCOUNTANT")).contentType("application/json").content(json.writeValueAsString(body)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        for(String amount:List.of("0","-1","1.001"))assertEquals(400,create(f,request(f,UUID.randomUUID().toString(),amount)).status());
        var body=(tools.jackson.databind.node.ObjectNode)json.valueToTree(r);body.put("status","PAID");
        mvc().perform(post("/api/payments").header("Authorization",bearer(f,"ACCOUNTANT")).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isBadRequest());
        body=(tools.jackson.databind.node.ObjectNode)json.valueToTree(r);body.put("amountCurrency","EUR");
        mvc().perform(post("/api/payments").header("Authorization",bearer(f,"ACCOUNTANT")).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isUnprocessableEntity());
        assertEquals(0,db(f.tenant()).queryForObject("select count(*) from payments where invoice_id=?",Integer.class,f.invoice()));
    }
    @Test void metadataAndCancellationPreserveFinancialIdentityAndOneCancellationAudit() throws Exception {
        var f=fixture("identity-a");UUID id=id(create(f,request(f,UUID.randomUUID().toString(),"60.00")));
        var before=db(f.tenant()).queryForMap("select amount_amount,amount_currency,invoice_id,idempotency_key,input_hash,input_hash_version,recorded_at,recorded_by_user_id,tenant_id from payments where id=?",id);
        for(String field:List.of("status","invoiceId","amountAmount","amountCurrency","idempotencyKey","inputHash","recordedAt","recordedByUserId"))
            mvc().perform(put("/api/payments/"+id).header("Authorization",bearer(f,"ACCOUNTANT")).contentType("application/json").content("{\""+field+"\":null}"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PAYMENT_FINANCIAL_FIELDS_IMMUTABLE"));
        mvc().perform(put("/api/payments/"+id).header("Authorization",bearer(f,"ACCOUNTANT")).contentType("application/json").content("{\"description\":\"Reviewed\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.referenceNumber").value("REF"));
        for(int n=0;n<2;n++)mvc().perform(post("/api/payments/"+id+"/cancel").param("reason","Customer request").header("Authorization",bearer(f,"ACCOUNTANT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELLED"));
        assertEquals(before,db(f.tenant()).queryForMap("select amount_amount,amount_currency,invoice_id,idempotency_key,input_hash,input_hash_version,recorded_at,recorded_by_user_id,tenant_id from payments where id=?",id));
        assertEquals(1,db(f.tenant()).queryForObject("select count(*) from payment_command_events where payment_id=? and command_type='CANCEL'",Integer.class,id));
        mvc().perform(delete("/api/payments/"+id).header("Authorization",bearer(f,"ACCOUNTANT"))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PAYMENT_DELETE_FORBIDDEN"));
        id(create(f,request(f,UUID.randomUUID().toString(),"100.00"))); // cancellation released its reservation
    }
    @Test void sqlCannotDeleteRewriteFinancialFieldsOrMutateWithoutAppendOnlyEvidence() throws Exception {
        var f=fixture("identity-a");UUID id=id(create(f,request(f,UUID.randomUUID().toString(),"40.00")));var jdbc=db(f.tenant());
        assertThrows(DataAccessException.class,()->jdbc.update("delete from payments where id=?",id));
        for(String assignment:List.of("amount_amount=1","amount_currency='EUR'","invoice_id=null","idempotency_key='changed'","input_hash=null","recorded_at=now()","recorded_by_user_id=null","tenant_id=gen_random_uuid()"))
            assertThrows(DataAccessException.class,()->jdbc.update("update payments set "+assignment+",version=version+1 where id=?",id));
        assertThrows(DataAccessException.class,()->jdbc.update("update payments set status='CANCELLED',version=version+1 where id=?",id));
        assertThrows(DataAccessException.class,()->jdbc.update("update payments set description='unaudited',version=version+1 where id=?",id));
        assertThrows(DataAccessException.class,()->jdbc.update("update payment_command_events set reason='rewrite' where payment_id=?",id));
        assertThrows(DataAccessException.class,()->jdbc.update("delete from payment_command_events where payment_id=?",id));
        assertEquals("PENDING",jdbc.queryForObject("select status from payments where id=?",String.class,id));
    }
    @Test void rollbackRemovesPaymentAndItsAuditTogether() {
        var f=fixture("identity-a");TenantContext.setTenantId(f.tenant());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(f.email(),"fixture",List.of(new SimpleGrantedAuthority("ROLE_ACCOUNTANT"))));
        String key=UUID.randomUUID().toString();
        assertThrows(IllegalStateException.class,()->new TransactionTemplate(transactions).execute(status->{payments.create(request(f,key,"40.00"));throw new IllegalStateException("Fixture rollback");}));
        assertEquals(0,db(f.tenant()).queryForObject("select count(*) from payments where idempotency_key=?",Integer.class,key));
        assertEquals(0,db(f.tenant()).queryForObject("select count(*) from payment_command_events e join payments p on p.id=e.payment_id where p.invoice_id=?",Integer.class,f.invoice()));
    }
    @Test void twoPhysicalTenantsHaveIndependentKeysAndCrossTenantObjectAccessFails() throws Exception {
        var a=fixture("identity-a");var b=fixture("identity-b");String key=UUID.randomUUID().toString();UUID first=id(create(a,request(a,key,"30.00"))),second=id(create(b,request(b,key,"30.00")));
        assertNotEquals(first,second);
        mvc().perform(get("/api/payments/"+second).header("Authorization",bearer(a,"ACCOUNTANT"))).andExpect(status().isNotFound());
        mvc().perform(post("/api/payments/"+second+"/cancel").param("reason","Denied").header("Authorization",bearer(a,"ACCOUNTANT"))).andExpect(status().isNotFound());
        assertEquals("PENDING",db(b.tenant()).queryForObject("select status from payments where id=?",String.class,second));
    }
    @Test void stalePersistenceContextCannotOverwriteAuditedMetadata() throws Exception {
        var f=fixture("identity-a");UUID id=id(create(f,request(f,UUID.randomUUID().toString(),"20.00")));TenantContext.setTenantId(f.tenant());
        try(var em=entityManagers.createEntityManager()){
            em.getTransaction().begin();var stale=em.find(com.company.logicstic.entity.Payment.class,id);
            mvc().perform(put("/api/payments/"+id).header("Authorization",bearer(f,"ACCOUNTANT")).contentType("application/json").content("{\"description\":\"Winner\"}"))
                    .andExpect(status().isOk());
            TenantContext.setTenantId(f.tenant());stale.setDescription("Stale");
            assertThrows(jakarta.persistence.OptimisticLockException.class,em::flush);em.getTransaction().rollback();
        }
        assertEquals("Winner",db(f.tenant()).queryForObject("select description from payments where id=?",String.class,id));
    }
    @Test void populatedLegacyUpgradeRetainsHashesAndRejectsUnknownOrTerminalCancellation() throws Exception {
        String database="codex_payment_legacy_"+UUID.randomUUID().toString().replace("-", ""),tenant="legacy-"+UUID.randomUUID();
        var source=new PGSimpleDataSource();source.setURL(System.getenv("TASK_DB_URL"));source.setUser(System.getenv("TASK_DB_USER"));source.setPassword(System.getenv("TASK_DB_PASSWORD"));
        new JdbcTemplate(source).execute("create database "+database);source.setDatabaseName(database);
        Flyway.configure().dataSource(source).locations("classpath:db/migration/tenant").target("37").load().migrate();var jdbc=new JdbcTemplate(source);var f=fixture(tenant,jdbc);
        String key=UUID.randomUUID().toString(),hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest((f.invoice()+"|10|USD").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        UUID legacy=legacyPayment(jdbc,f,"PENDING",key,hash);var terminal=new ArrayList<UUID>();
        for(String state:List.of("PAID","COMPLETED","SUCCEEDED","SETTLED"))terminal.add(legacyPayment(jdbc,f,state,null,null));
        legacyPayment(jdbc,f,"UNKNOWN_IMPORTED",null,null);
        var latest=Flyway.configure().dataSource(source).locations("classpath:db/migration/tenant").load();latest.migrate();assertTrue(latest.validateWithResult().validationSuccessful);
        routing.addTenantDataSource(tenant,source);
        assertEquals(legacy,id(create(f,request(f,key,"10.00"))));
        assertEquals(hash,jdbc.queryForObject("select input_hash from payments where id=?",String.class,legacy));
        assertNull(jdbc.queryForObject("select input_hash_version from payments where id=?",Short.class,legacy));
        for(UUID id:terminal)mvc().perform(post("/api/payments/"+id+"/cancel").param("reason","Denied").header("Authorization",bearer(f,"ACCOUNTANT")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PAYMENT_CANCEL_STATE_CONFLICT"));
        var rejected=create(f,request(f,UUID.randomUUID().toString(),"1.00"));assertEquals(409,rejected.status());assertEquals("PAYMENT_LEGACY_STATE_UNRESOLVED",rejected.body().get("code").asText());
        assertEquals(6,jdbc.queryForObject("select count(*) from payments where invoice_id=?",Integer.class,f.invoice()));
    }
    private UUID legacyPayment(JdbcTemplate jdbc,Fixture f,String state,String key,String hash){
        UUID id=UUID.randomUUID();jdbc.update("insert into payments(id,status,tenant_id,invoice_id,amount_amount,amount_currency,billing_address_line1,billing_address_city,billing_address_state,billing_address_zip_code,billing_address_country,idempotency_key,input_hash) values (?,?,?, ?,10,'USD','Address','City','State','00000','US',?,?)",id,state,UUID.randomUUID(),f.invoice(),key,hash);return id;
    }
}
