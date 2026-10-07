package com.company.logicstic.integration;

import com.company.logicstic.service.LoadService;
import com.company.logicstic.service.TripService;
import java.util.*;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.config.import=","spring.flyway.enabled=true","spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false","spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.jpa.properties.hibernate.session.events.log=false","app.tenancy.enabled=false","app.lark.base.enabled=false"})
@EnabledIfEnvironmentVariable(named="TASK_DB_URL",matches="jdbc:postgresql:.*codex_.*")
class PersistenceReadPostgresTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry p){
        p.add("spring.datasource.url",()->System.getenv("TASK_DB_URL"));
        p.add("spring.datasource.username",()->System.getenv("TASK_DB_USER"));
        p.add("spring.datasource.password",()->System.getenv("TASK_DB_PASSWORD"));
    }
    @Autowired LoadService loads;
    @Autowired TripService trips;
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManagerFactory factory;
    @Autowired WebApplicationContext web;
    private final JsonMapper json=JsonMapper.builder().build();
    private record Fixture(String prefix,List<UUID> loads,List<UUID> trips){}
    private Statistics statistics(){return factory.unwrap(SessionFactory.class).getStatistics();}
    private Fixture fixture(){
        String prefix="fetch-"+UUID.randomUUID();var loadIds=new ArrayList<UUID>();var tripIds=new ArrayList<UUID>();
        for(int i=0;i<25;i++){
            UUID customer=UUID.randomUUID(),driver=UUID.randomUUID(),truck=UUID.randomUUID(),load=UUID.randomUUID(),trip=UUID.randomUUID();
            jdbc.update("insert into customers(id,name,status,is_vat_exempt) values (?,?,'ACTIVE',false)",customer,"Customer "+i);
            jdbc.update("insert into employees(id,email,first_name,last_name,salary_type,status,joined_date,salary_amount,salary_currency) values (?,?,'Dispatcher',?,'HOURLY','ACTIVE',now(),0,'USD')",driver,driver+"@fetch.test",String.valueOf(i));
            jdbc.update("insert into trucks(id,number,type,vehicle_capacity,status,is_hazmat_placarded,adr_equipment_allowed_classes,adr_equipment_is_adr_certified) values (?,?,'SEMI',1,'ACTIVE',false,'',false)",truck,truck.toString());
            String name=prefix+String.format("-%02d",i);
            jdbc.update("""
                    insert into loads(id,name,type,status,distance,is_in_proximity,customer_id,source,is_hazmat,delivery_cost_amount,delivery_cost_currency,
                    destination_address_city,destination_address_country,destination_address_line1,destination_address_state,destination_address_zip_code,
                    destination_location_latitude,destination_location_longitude,origin_address_city,origin_address_country,origin_address_line1,
                    origin_address_state,origin_address_zip_code,origin_location_latitude,origin_location_longitude,assigned_truck_id,assigned_dispatcher_id)
                    values (?,?,'FTL','OPEN',1,false,?,'MANUAL',false,0,'USD','City','US','Address','TX','00000',0,0,'City','US','Address','TX','00000',0,0,?,?)
                    """,load,name,customer,i%2==0?null:truck,i%3==0?null:driver);
            jdbc.update("insert into trips(id,name,total_distance,status,truck_id) values (?,?,1,'PLANNED',?)",trip,name,i%2==0?null:truck);
            loadIds.add(load);tripIds.add(trip);
        }
        return new Fixture(prefix,loadIds,tripIds);
    }
    @Test void fiveAndTwentyRowPagesHaveBoundedQueriesCorrectTotalsAndDetachedSerialization(){
        var f=fixture();var stats=statistics();
        for(int size:List.of(5,20)){
            stats.clear();var page=loads.search(f.prefix(),null,null,null,null,1,size,"name",false);
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());assertEquals(size,page.items().size());assertEquals(25,page.totalItems());
            assertEquals((25+size-1)/size,page.totalPages());assertEquals(f.prefix()+"-00",page.items().getFirst().name());
            long count=stats.getPrepareStatementCount();assertTrue(count<=2,"Load page select count: "+count);json.writeValueAsString(page);assertEquals(count,stats.getPrepareStatementCount());
            stats.clear();var tripPage=trips.search(f.prefix(),null,null,1,size,"name",false);
            assertEquals(size,tripPage.items().size());assertEquals(25,tripPage.totalItems());assertEquals(f.prefix()+"-00",tripPage.items().getFirst().name());
            count=stats.getPrepareStatementCount();assertTrue(count<=2,"Trip page select count: "+count);json.writeValueAsString(tripPage);assertEquals(count,stats.getPrepareStatementCount());
        }
        var last=loads.search(f.prefix(),null,null,null,null,2,20,"name",false);assertEquals(5,last.items().size());assertEquals(f.prefix()+"-20",last.items().getFirst().name());assertEquals(25,last.totalItems());
    }
    @Test void detailFetchesRequiredToOneViewsInOneSelectIncludingNullAssignments(){
        var f=fixture();var stats=statistics();
        for(int i:List.of(0,1)){
            stats.clear();var load=loads.getById(f.loads().get(i));assertEquals("Customer "+i,load.customerName());
            assertEquals(i==0,null==load.assignedTruckId());assertEquals(i==0,null==load.assignedDispatcherId());
            assertEquals(1,stats.getPrepareStatementCount());assertFalse(TransactionSynchronizationManager.isActualTransactionActive());json.writeValueAsString(load);assertEquals(1,stats.getPrepareStatementCount());
            stats.clear();var trip=trips.getById(f.trips().get(i));assertEquals(i==0,null==trip.truckId());assertEquals(1,stats.getPrepareStatementCount());json.writeValueAsString(trip);assertEquals(1,stats.getPrepareStatementCount());
        }
    }
    @Test void defaultListWithoutSearchHasCorrectPostgresParameterTypes() throws Exception {
        fixture();var mvc=MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();
        for(String resource:List.of("loads","trips"))mvc.perform(get("/api/"+resource).with(user("reader").roles("DISPATCHER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
    }
    @Test void actualAuthorizedReadControllersSerializePagesAndNullRelationDetails() throws Exception {
        var f=fixture();var mvc=MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();
        for(String resource:List.of("loads","trips")){
            mvc.perform(get("/api/"+resource).with(user("reader").roles("DISPATCHER")).param("search",f.prefix()).param("pageSize","20"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(20)).andExpect(jsonPath("$.data.totalItems").value(25));
            UUID id=resource.equals("loads")?f.loads().getFirst():f.trips().getFirst();
            mvc.perform(get("/api/"+resource+"/"+id).with(user("reader").roles("DISPATCHER")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(id.toString())).andExpect(jsonPath("$.data.version").value(0));
        }
    }
}
