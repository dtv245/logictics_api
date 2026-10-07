package com.company.logicstic.integration;

import com.company.logicstic.config.TenantContext;
import com.company.logicstic.config.TenantRoutingDataSource;
import com.company.logicstic.integration.lark.auth.LarkAuthService;
import java.util.List;
import java.util.UUID;
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
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.config.import=", "spring.flyway.enabled=false", "app.tenancy.enabled=false", "app.lark.base.enabled=false"})
@Import(CurrentUserApiPostgresTest.Tenants.class)
@EnabledIfEnvironmentVariable(named="TASK_DB_URL",matches="jdbc:postgresql:.*codex_.*")
class MessagingAuthorizationPostgresTest {
    @Autowired WebApplicationContext context;
    @Autowired LarkAuthService tokens;
    @Autowired TenantRoutingDataSource routing;
    private final JsonMapper json=JsonMapper.builder().build();
    private MockMvc mvc(){return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}
    private JdbcTemplate db(String tenant){return new JdbcTemplate(routing.getRegisteredDataSource(tenant).orElseThrow());}
    private record Actor(UUID id,String email,String tenant){}
    private Actor actor(String tenant,String email){
        UUID id=UUID.randomUUID();db(tenant).update("insert into employees(id,email,first_name,last_name,salary_type,status,joined_date,salary_amount,salary_currency) values (?,?,'Message','Fixture','HOURLY','ACTIVE',now(),0,'USD')",id,email);
        return new Actor(id,email,tenant);
    }
    private Actor actor(String tenant){return actor(tenant,UUID.randomUUID()+"@message.invalid");}
    private String bearer(Actor actor,String role){return "Bearer "+tokens.createInternalToken("subject",actor.email(),actor.tenant(),List.of(role),"Fixture",actor.id());}
    private UUID conversation(Actor actor,boolean tenantChat) throws Exception {
        var response=mvc().perform(post("/api/messages/conversations").header("Authorization",bearer(actor,"ADMIN"))
                .contentType("application/json").content("{\"name\":\"Fixture\",\"isTenantChat\":"+tenantChat+"}"))
                .andExpect(status().isCreated()).andReturn().getResponse();
        return UUID.fromString(json.readTree(response.getContentAsString()).get("data").get("id").asText());
    }
    private String send(UUID conversation,UUID sender){return json.writeValueAsString(new com.company.logicstic.dto.message.SendMessageRequest(conversation,sender,"Fixture message"));}
    @AfterEach void clear(){TenantContext.clear();}

    @Test void privateMembershipAndDeprecatedSenderCompatibilityAreEnforcedAtActualApi() throws Exception {
        Actor owner=actor("identity-a"),other=actor("identity-a");UUID id=conversation(owner,false);
        for(UUID sender:new UUID[]{null,owner.id()})mvc().perform(post("/api/messages").header("Authorization",bearer(owner,"DRIVER")).contentType("application/json").content(send(id,sender)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.senderId").value(owner.id().toString()));
        mvc().perform(get("/api/messages").param("conversationId",id.toString()).header("Authorization",bearer(owner,"DRIVER"))).andExpect(status().isOk());
        mvc().perform(post("/api/messages").header("Authorization",bearer(owner,"DRIVER")).contentType("application/json").content(send(id,other.id()))).andExpect(status().isForbidden());
        for(String role:List.of("DRIVER","ADMIN")) {
            mvc().perform(get("/api/messages").param("conversationId",id.toString()).header("Authorization",bearer(other,role))).andExpect(status().isForbidden());
            mvc().perform(get("/api/messages/conversations/"+id).header("Authorization",bearer(other,role))).andExpect(status().isForbidden());
            mvc().perform(post("/api/messages").header("Authorization",bearer(other,role)).contentType("application/json").content(send(id,null))).andExpect(status().isForbidden());
        }
        assertEquals(2,db("identity-a").queryForObject("select count(*) from messages where conversation_id=?",Integer.class,id));
    }
    @Test void unmappedPrincipalIsDeniedOnEveryMessagingPathIncludingReadSelectors() throws Exception {
        Actor owner=actor("identity-a");UUID id=conversation(owner,true);Actor missing=new Actor(UUID.randomUUID(),UUID.randomUUID()+"@unmapped.invalid","identity-a");
        for(var request:List.of(get("/api/messages/conversations").param("employeeId",owner.id().toString()),get("/api/messages/conversations/"+id),
                get("/api/messages/unread-count").param("employeeId",owner.id().toString()),get("/api/messages").param("conversationId",id.toString()),
                post("/api/messages").contentType("application/json").content(send(id,null)),post("/api/messages/conversations").contentType("application/json").content("{\"isTenantChat\":false}")))
            mvc().perform(request.header("Authorization",bearer(missing,"ADMIN"))).andExpect(status().isForbidden());
        assertEquals(0,db("identity-a").queryForObject("select count(*) from messages where conversation_id=?",Integer.class,id));
    }
    @Test void onlyAdminCreatesTenantChatButOtherMappedEmployeesCanListReadAndSend() throws Exception {
        Actor admin=actor("identity-a"),employee=actor("identity-a");
        mvc().perform(post("/api/messages/conversations").header("Authorization",bearer(employee,"DRIVER")).contentType("application/json").content("{\"isTenantChat\":true}"))
                .andExpect(status().isForbidden());
        UUID id=conversation(admin,true);
        mvc().perform(get("/api/messages/conversations").param("employeeId",employee.id().toString()).header("Authorization",bearer(employee,"DRIVER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[?(@.id == '"+id+"')]").exists());
        mvc().perform(get("/api/messages/conversations/"+id).header("Authorization",bearer(employee,"DRIVER"))).andExpect(status().isOk());
        mvc().perform(post("/api/messages").header("Authorization",bearer(employee,"DRIVER")).contentType("application/json").content(send(id,null))).andExpect(status().isCreated());
    }
    @Test void removedParticipantIsDeniedOnTheNextRequestAndSelectorsMustBeSelf() throws Exception {
        Actor owner=actor("identity-a"),other=actor("identity-a");UUID id=conversation(owner,false);
        mvc().perform(get("/api/messages/unread-count").param("employeeId",other.id().toString()).header("Authorization",bearer(owner,"ADMIN"))).andExpect(status().isForbidden());
        mvc().perform(get("/api/messages/conversations").param("employeeId",other.id().toString()).header("Authorization",bearer(owner,"ADMIN"))).andExpect(status().isForbidden());
        db("identity-a").update("delete from conversation_participants where conversation_id=? and employee_id=?",id,owner.id());
        mvc().perform(get("/api/messages").param("conversationId",id.toString()).header("Authorization",bearer(owner,"ADMIN"))).andExpect(status().isForbidden());
        mvc().perform(post("/api/messages").header("Authorization",bearer(owner,"ADMIN")).contentType("application/json").content(send(id,null))).andExpect(status().isForbidden());
    }
    @Test void sameEmailInTwoPhysicalTenantsCannotReadOrWriteAnotherTenantsConversation() throws Exception {
        String email=UUID.randomUUID()+"@both.invalid";Actor a=actor("identity-a",email),b=actor("identity-b",email);UUID id=conversation(b,false);
        mvc().perform(get("/api/messages/conversations/"+id).header("Authorization",bearer(a,"ADMIN"))).andExpect(status().isNotFound());
        mvc().perform(post("/api/messages").header("Authorization",bearer(a,"ADMIN")).contentType("application/json").content(send(id,b.id()))).andExpect(status().isNotFound());
        assertEquals(0,db("identity-b").queryForObject("select count(*) from messages where conversation_id=?",Integer.class,id));
        assertTrue(TenantContext.getTenantId().isEmpty());
    }
}
