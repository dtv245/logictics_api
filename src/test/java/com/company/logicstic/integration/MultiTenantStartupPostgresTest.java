package com.company.logicstic.integration;

import com.company.logicstic.config.*;
import com.company.logicstic.integration.lark.auth.LarkAuthService;
import com.company.logicstic.integration.lark.auth.LarkAuthClient;
import com.company.logicstic.integration.lark.auth.LarkTokenResponse;
import com.company.logicstic.integration.lark.auth.LarkUserResponse;
import com.company.logicstic.service.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Actual app-managed bootstrap with an empty router and a separately owned registry. */
@SpringBootTest(properties={"spring.config.import=","spring.flyway.enabled=true","app.tenancy.enabled=true",
        "app.tenancy.registry.auto-initialize=false","app.tenancy.pool.minimum-idle=0","app.tenancy.pool.maximum-pool-size=4",
        "app.tenancy.pool.connection-timeout-ms=300","app.lark.base.enabled=false","app.lark.default-tenant-id=startup-b"})
@EnabledIfEnvironmentVariable(named="TASK_DB_URL",matches="jdbc:postgresql:.*codex_.*")
class MultiTenantStartupPostgresTest {
    private static final String KEY=Base64.getEncoder().encodeToString("fixture-registry-key-32-bytes!!!".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    private static PGSimpleDataSource registry,a,b,future;
    private static PGSimpleDataSource source(String database){var s=new PGSimpleDataSource();s.setURL(System.getenv("TASK_DB_URL"));s.setUser(System.getenv("TASK_DB_USER"));s.setPassword(System.getenv("TASK_DB_PASSWORD"));if(database!=null)s.setDatabaseName(database);return s;}
    private static String jdbcUrl(PGSimpleDataSource source){return "jdbc:postgresql://"+source.getServerNames()[0]+":"+source.getPortNumbers()[0]+"/"+source.getDatabaseName();}
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p){
        if(!System.getenv("TASK_DB_URL").matches("jdbc:postgresql://[^/]+/codex_[a-z0-9_]+"))throw new IllegalStateException("Disposable database required");
        var admin=new JdbcTemplate(source(null));String suffix=UUID.randomUUID().toString().replace("-","");
        String registryName="codex_registry_"+suffix,aName="codex_startup_a_"+suffix,bName="codex_startup_b_"+suffix,futureName="codex_future_"+suffix;
        for(String name:List.of(registryName,aName,bName,futureName))admin.execute("create database "+name);
        registry=source(registryName);a=source(aName);b=source(bName);future=source(futureName);
        Flyway.configure().dataSource(registry).locations("classpath:db/migration/registry").load().migrate();
        var props=new TenancyProperties();props.getRegistry().setEncryptionKey(KEY);var cipher=new TenantCredentialCipher(props);
        var jdbc=new JdbcTemplate(registry);
        for(var tenant:Map.of("startup-a",a,"startup-b",b).entrySet())jdbc.update("insert into tenant_registry(tenant_id,db_url,db_username,db_password,status) values (?,?,?,?,'ACTIVE')",tenant.getKey(),jdbcUrl(tenant.getValue()),tenant.getValue().getUser(),cipher.encrypt(System.getenv("TASK_DB_PASSWORD")));
        p.add("app.tenancy.registry.url",()->jdbcUrl(registry));p.add("app.tenancy.registry.username",registry::getUser);p.add("app.tenancy.registry.password",registry::getPassword);p.add("app.tenancy.registry.encryption-key",()->KEY);
        // This unbound router is the primary bean; a default Boot migration would fail.
        p.add("spring.datasource.url",()->"jdbc:postgresql://localhost:1/codex_must_not_be_used");
    }
    @Autowired TenantRoutingDataSource routing;
    @Autowired TenantRegistryService tenants;
    @Autowired TenantMigrationService migrations;
    @Autowired LarkAuthService tokens;
    @Autowired WebApplicationContext web;
    @Autowired TenantDataSourceService dataSources;
    @MockitoBean LarkAuthClient provider;
    @AfterEach void clear(){TenantContext.clear();}
    @Test void startupMigratesPhysicalTenantsAndNeverMixesRegistryAndTenantChains(){
        int latest=new JdbcTemplate(a).queryForObject("select max(version::int) from flyway_schema_history where success",Integer.class);
        assertTrue(latest>=38);assertEquals(latest,new JdbcTemplate(b).queryForObject("select max(version::int) from flyway_schema_history where success",Integer.class));
        assertEquals(1,new JdbcTemplate(registry).queryForObject("select max(version::int) from flyway_schema_history where success",Integer.class));
        assertNull(new JdbcTemplate(registry).queryForObject("select to_regclass('public.payments')",String.class));
        assertNull(new JdbcTemplate(a).queryForObject("select to_regclass('public.tenant_registry')",String.class));
        assertTrue(TenantContext.getTenantId().isEmpty());
        assertThrows(IllegalStateException.class,routing::getConnection);
        assertTrue(Flyway.configure().dataSource(a).locations("classpath:db/migration/tenant").load().validateWithResult().validationSuccessful);
    }
    @Test void actualJwtRoutingReadsOnlyTheBootstrappedPhysicalTenantAndBuildIsAdminOnly() throws Exception {
        var mvc=MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();String email=UUID.randomUUID()+"@s.test";
        for(var tenant:Map.of("startup-a",a,"startup-b",b).entrySet()){
            UUID employee=UUID.randomUUID();var jdbc=new JdbcTemplate(tenant.getValue());
            jdbc.update("insert into employees(id,email,first_name,last_name,salary_type,status,joined_date,salary_amount,salary_currency) values (?,?,'Startup','Fixture','HOURLY','ACTIVE',now(),0,'USD')",employee,email);
            UUID customer=UUID.randomUUID();jdbc.update("insert into customers(id,name,status,is_vat_exempt) values (?,?,'ACTIVE',false)",customer,tenant.getKey());
            String token=tokens.createInternalToken("fixture",email,tenant.getKey(),List.of("ADMIN"),"Startup",employee);
            mvc.perform(get("/api/customers/"+customer).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value(tenant.getKey()));
            mvc.perform(get("/api/internal/build").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.data.buildId").exists());
            assertTrue(TenantContext.getTenantId().isEmpty());
        }
        mvc.perform(get("/api/internal/build").header("Authorization","Bearer "+tokens.createInternalToken("fixture",email,"startup-a",List.of("ACCOUNTANT"),"Startup",null))).andExpect(status().isForbidden());
        mvc.perform(get("/api/customers").header("Authorization","Bearer "+tokens.createInternalToken("fixture",email,"inactive-fixture",List.of("ADMIN"),"Startup",null)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("INVALID_TENANT_CONTEXT"));
        assertTrue(TenantContext.getTenantId().isEmpty());
    }
    @Test void failedTenantStopsBatchAndReportsPendingTenantsWithoutMigratingLaterDatabase(){
        String missing="codex_deliberately_missing_"+UUID.randomUUID().toString().replace("-","");
        var bad=source(missing);tenants.registerActiveTenant("z-broken",jdbcUrl(bad),bad.getUser(),System.getenv("TASK_DB_PASSWORD"));
        tenants.registerActiveTenant("z-future",jdbcUrl(future),future.getUser(),System.getenv("TASK_DB_PASSWORD"));
        try{
            var report=migrations.migrateAllActiveTenants();assertFalse(report.success());assertEquals(List.of("startup-a","startup-b"),report.migratedTenants());
            assertEquals("z-broken",report.failedTenant());assertEquals(List.of("z-broken","z-future"),report.pendingTenants());
            assertNull(new JdbcTemplate(future).queryForObject("select to_regclass('public.flyway_schema_history')",String.class));
        }finally{new JdbcTemplate(registry).update("delete from tenant_registry where tenant_id in ('z-broken','z-future')");}
    }

    @Test void anonymousCallbackUsesOnlyConfiguredTenantEmployeeAndItsRealRole() throws Exception {
        String email="login-"+UUID.randomUUID().toString().substring(0,8)+"@fixture.test";
        UUID targetEmployee=null;
        for(var tenant:Map.of("startup-a",a,"startup-b",b).entrySet()){
            var jdbc=new JdbcTemplate(tenant.getValue());UUID role=UUID.randomUUID(),employee=UUID.randomUUID();
            String roleName=tenant.getKey().equals("startup-a")?"ADMIN":"DRIVER";
            jdbc.update("insert into tenant_roles(id,name,normalized_name) values (?,?,?)",role,roleName,roleName);
            jdbc.update("insert into employees(id,email,first_name,last_name,salary_type,status,joined_date,salary_amount,salary_currency,role_id) values (?,?,'Login','Fixture','HOURLY','ACTIVE',now(),0,'USD',?)",employee,email,role);
            if(tenant.getKey().equals("startup-b"))targetEmployee=employee;
        }
        var response=new LarkTokenResponse(0,"success",null);
        when(provider.exchangeCodeForToken(anyString(),anyString())).thenReturn(response);
        when(provider.extractUser(response)).thenReturn(new LarkUserResponse("fixture-subject",null,null,email,null,"Fixture",null,null,null));
        String state=tokens.createState("/");
        var mvc=MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();
        var result=mvc.perform(post("/api/auth/lark/callback").param("tenantId","startup-a")
                .contentType("application/json").content("{\"code\":\"fixture-code\",\"state\":\""+state+"\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.tenantId").value("startup-b"))
                .andExpect(jsonPath("$.data.employeeId").value(targetEmployee.toString()))
                .andExpect(jsonPath("$.data.roles[0]").value("DRIVER")).andReturn();
        var json=tools.jackson.databind.json.JsonMapper.builder().build();
        String issued=json.readTree(result.getResponse().getContentAsString()).get("data").get("accessToken").asText();
        assertEquals("startup-b",tokens.validateInternalToken(issued).get("tenant",String.class));
        mvc.perform(get("/api/payments").header("Authorization","Bearer "+issued)).andExpect(status().isForbidden());
        assertTrue(TenantContext.getTenantId().isEmpty());
    }

    @Test void callbackRejectsDifferentAuthenticatedTenantBeforeProviderExchange() throws Exception {
        var mvc=MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();
        String bearer=tokens.createInternalToken("fixture","login@fixture.test","startup-a",List.of("ADMIN"),"Fixture",null);
        String state=tokens.createState("/");
        mvc.perform(post("/api/auth/lark/callback").header("Authorization","Bearer "+bearer)
                .contentType("application/json").content("{\"code\":\"fixture-code\",\"state\":\""+state+"\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("IDENTITY_TENANT_MISMATCH"));
        verifyNoInteractions(provider);
        assertTrue(TenantContext.getTenantId().isEmpty());
    }

    @Test void disablingPreviouslyRegisteredTenantRevokesNextRequestWithoutPoolFallback() throws Exception {
        dataSources.ensureTenantDataSource("startup-b");
        var jdbc=new JdbcTemplate(registry);jdbc.update("update tenant_registry set status='INACTIVE' where tenant_id='startup-b'");
        var mvc=MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();
        try{
            String bearer=tokens.createInternalToken("fixture","login@fixture.test","startup-b",List.of("ADMIN"),"Fixture",null);
            mvc.perform(get("/api/internal/build").header("Authorization","Bearer "+bearer))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("INVALID_TENANT_CONTEXT"));
            String state=tokens.createState("/");
            mvc.perform(post("/api/auth/lark/callback").contentType("application/json")
                    .content("{\"code\":\"fixture-code\",\"state\":\""+state+"\"}"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("INVALID_TENANT_CONTEXT"));
            verifyNoInteractions(provider);
            assertTrue(TenantContext.getTenantId().isEmpty());
        }finally{jdbc.update("update tenant_registry set status='ACTIVE' where tenant_id='startup-b'");}
    }
}
