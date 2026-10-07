package com.company.logicstic.integration;

import com.company.logicstic.config.TenantContext;
import com.company.logicstic.config.TenantRoutingDataSource;
import com.company.logicstic.dto.load.CreateLoadRequest;
import com.company.logicstic.dto.trip.CreateTripRequest;
import com.company.logicstic.dto.truck.CreateTruckRequest;
import com.company.logicstic.entity.*;
import com.company.logicstic.integration.lark.auth.LarkAuthService;
import com.company.logicstic.mapper.TripMapper;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.config.import=","spring.flyway.enabled=false","app.tenancy.enabled=false","app.lark.base.enabled=false"})
@Import(CurrentUserApiPostgresTest.Tenants.class)
@EnabledIfEnvironmentVariable(named="TASK_DB_URL",matches="jdbc:postgresql:.*codex_.*")
class CoreStaleVersionPostgresTest {
    @Autowired WebApplicationContext context;
    @Autowired TenantRoutingDataSource routing;
    @Autowired LarkAuthService tokens;
    @Autowired jakarta.persistence.EntityManagerFactory factory;
    @MockitoSpyBean TripMapper tripMapper;
    private final JsonMapper json=JsonMapper.builder().build();
    private record Fixture(String resource,UUID id,ObjectNode body,String email,UUID actor,String field,String table,Class<?> entity) {}
    private MockMvc mvc(){return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}
    private JdbcTemplate db(){return new JdbcTemplate(routing.getRegisteredDataSource("identity-a").orElseThrow());}
    private String token(Fixture f,String tenant,String role){return "Bearer "+tokens.createInternalToken("fixture",f.email(),tenant,List.of(role),"Version fixture",f.actor());}
    private Fixture fixture(String resource) throws Exception {
        UUID actor=UUID.randomUUID(),customer=UUID.randomUUID();String email=actor+"@v.test";
        db().update("insert into employees(id,email,first_name,last_name,salary_type,status,joined_date,salary_amount,salary_currency) values (?,?,'Version','Fixture','HOURLY','ACTIVE',now(),0,'USD')",actor,email);
        Object command;String field,table;Class<?> entity;
        if(resource.equals("loads")){
            db().update("insert into customers(id,name,status,is_vat_exempt) values (?,'Version customer','ACTIVE',false)",customer);
            command=new CreateLoadRequest("Version fixture","FTL","DRAFT",0.0,false,customer,null,null,"MANUAL",null,null,null,false,null,null,null,null,null,null,null,null,BigDecimal.ZERO,"USD","Origin",null,"City","TX","00000","US",0.0,0.0,"Destination",null,"City","TX","00000","US",0.0,0.0,null,null);
            field="notes";table="loads";entity=Load.class;
        }else if(resource.equals("trips")){
            command=new CreateTripRequest("Version fixture",0.0,"PLANNED",null);field="name";table="trips";entity=Trip.class;
        }else{
            command=new CreateTruckRequest(UUID.randomUUID().toString(),"TRACTOR",100,"ACTIVE",null,null,null,null,null,null,false,null,null,false,"",null);field="model";table="trucks";entity=Truck.class;
        }
        var body=(ObjectNode)json.valueToTree(command);var f=new Fixture(resource,null,body,email,actor,field,table,entity);
        var response=mvc().perform(post("/api/"+resource).header("Authorization",token(f,"identity-a","ADMIN")).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isCreated()).andReturn().getResponse();
        var data=json.readTree(response.getContentAsString()).get("data");assertTrue(data.has("version"));body.put("expectedVersion",data.get("version").asLong());
        return new Fixture(resource,UUID.fromString(data.get("id").asText()),body,email,actor,field,table,entity);
    }
    private org.springframework.test.web.servlet.ResultActions update(Fixture f,ObjectNode body) throws Exception {
        return mvc().perform(put("/api/"+f.resource()+"/"+f.id()).header("Authorization",token(f,"identity-a","ADMIN")).contentType("application/json").content(json.writeValueAsString(body)));
    }
    @AfterEach void clear(){TenantContext.clear();}

    @Test void sequentialStaleFormsCannotOverwriteFirstWriterAndResponsesHaveFlushedVersion() throws Exception {
        for(String resource:List.of("loads","trips","trucks")){
            var f=fixture(resource);long version=f.body().get("expectedVersion").asLong();var winner=f.body().deepCopy().put(f.field(),"Winner");
            update(f,winner).andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(version+1));
            update(f,f.body().deepCopy().put(f.field(),"Stale")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION_CONFLICT"));
            String column=resource.equals("loads")?"notes":f.field();
            assertEquals("Winner",db().queryForObject("select "+column+" from "+f.table()+" where id=?",String.class,f.id()));
            assertEquals(version+1,db().queryForObject("select version from "+f.table()+" where id=?",Long.class,f.id()));
        }
    }
    @Test void missingVersionIs400AndAuthorizationPrecedesVersionInformation() throws Exception {
        for(String resource:List.of("loads","trips","trucks")){
            var f=fixture(resource);var missing=f.body().deepCopy();missing.remove("expectedVersion");
            update(f,missing).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("expectedVersion"));
            mvc().perform(put("/api/"+resource+"/"+f.id()).contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
            mvc().perform(put("/api/"+resource+"/"+f.id()).header("Authorization",token(f,"identity-a","DRIVER")).contentType("application/json").content("{}")).andExpect(status().isForbidden());
            mvc().perform(put("/api/"+resource+"/"+f.id()).header("Authorization",token(f,"identity-b","ADMIN")).contentType("application/json").content(json.writeValueAsString(f.body()))).andExpect(status().isNotFound());
        }
    }
    @Test void independentPersistenceContextsRejectStaleEntityForAllThreeCoreTables() throws Exception {
        for(String resource:List.of("loads","trips","trucks")){
            var f=fixture(resource);TenantContext.setTenantId("identity-a");
            try(var first=factory.createEntityManager();var second=factory.createEntityManager()){
                first.getTransaction().begin();second.getTransaction().begin();
                Object stale=first.find(f.entity(),f.id()),winner=second.find(f.entity(),f.id());
                mutate(winner,"Committed");second.flush();second.getTransaction().commit();mutate(stale,"Stale");
                assertThrows(jakarta.persistence.OptimisticLockException.class,first::flush);first.getTransaction().rollback();
            }
            String column=resource.equals("loads")?"notes":f.field();assertEquals("Committed",db().queryForObject("select "+column+" from "+f.table()+" where id=?",String.class,f.id()));
            TenantContext.clear();
        }
    }
    private void mutate(Object entity,String value){
        if(entity instanceof Load load)load.setNotes(value);
        else if(entity instanceof Trip trip)trip.setName(value);
        else if(entity instanceof Truck truck)truck.setModel(value);
        else throw new AssertionError("Unexpected entity");
    }
    @Test void overlappingActualTripRequestsNormalizeOptimisticFailureTo409() throws Exception {
        var f=fixture("trips");var loaded=new CountDownLatch(1);var release=new CountDownLatch(1);
        doAnswer(call->{call.callRealMethod();loaded.countDown();assertTrue(release.await(20,TimeUnit.SECONDS));return null;})
                .when(tripMapper).updateEntity(argThat(request->request.name().equals("Held writer")),any(Trip.class));
        try(var pool=Executors.newSingleThreadExecutor()){
            var pending=pool.submit(()->update(f,f.body().deepCopy().put("name","Held writer")).andReturn().getResponse());
            try{
                assertTrue(loaded.await(20,TimeUnit.SECONDS));update(f,f.body().deepCopy().put("name","Winner")).andExpect(status().isOk());
            }finally{release.countDown();}
            var response=pending.get(30,TimeUnit.SECONDS);assertEquals(409,response.getStatus(),response.getContentAsString());
            assertEquals("CONCURRENT_MODIFICATION_CONFLICT",json.readTree(response.getContentAsString()).get("code").asText());
        }
        assertEquals("Winner",db().queryForObject("select name from trips where id=?",String.class,f.id()));
    }
}
