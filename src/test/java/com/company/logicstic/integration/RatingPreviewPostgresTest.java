package com.company.logicstic.integration;

import com.company.logicstic.dto.load.*;
import com.company.logicstic.dto.rating.*;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.service.rating.*;
import com.company.logicstic.service.rating.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.context.WebApplicationContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.config.import=","spring.flyway.enabled=true","spring.flyway.locations=classpath:db/migration/tenant","app.tenancy.enabled=false","app.lark.base.enabled=false"})
@EnabledIfEnvironmentVariable(named="TASK_DB_URL",matches="jdbc:postgresql:.*codex_.*")
class RatingPreviewPostgresTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry p){p.add("spring.datasource.url",()->System.getenv("TASK_DB_URL"));p.add("spring.datasource.username",()->System.getenv("TASK_DB_USER"));p.add("spring.datasource.password",()->System.getenv("TASK_DB_PASSWORD"));}
    @Autowired JdbcTemplate jdbc; @Autowired RatingPreviewService previews; @Autowired RatePolicyService policies;
    @Autowired RatingMileageService miles; @Autowired LoadPickupBusinessDateService dates; @Autowired WebApplicationContext web;
    private static final LocalDate DATE=LocalDate.of(2026,1,12);
    private record Fixture(UUID load,UUID customer,UUID actor,String email,RatingContract contract){}
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    private Fixture fixture(boolean captureDate){
        UUID load=UUID.randomUUID(),c=UUID.randomUUID(),actor=UUID.randomUUID();String email=actor+"@rating-preview.invalid";
        jdbc.update("insert into customers(id,name,status,is_vat_exempt) values (?,'Rating preview','ACTIVE',false)",c);
        jdbc.update("insert into employees(id,email,first_name,last_name,salary_type,status,joined_date,salary_amount,salary_currency) values (?,?,'Rating','Preview','HOURLY','ACTIVE',now(),0,'USD')",actor,email);
        jdbc.update("""
                insert into loads(id,name,type,status,distance,is_in_proximity,customer_id,source,is_hazmat,delivery_cost_amount,delivery_cost_currency,
                destination_address_city,destination_address_country,destination_address_line1,destination_address_state,destination_address_zip_code,
                destination_location_latitude,destination_location_longitude,origin_address_city,origin_address_country,origin_address_line1,
                origin_address_state,origin_address_zip_code,origin_location_latitude,origin_location_longitude,requested_pickup_date)
                values (?,'Rating preview','FTL','DELIVERED',999999,false,?,'MANUAL',false,0,'USD',
                'City','US','Address','TX','00000',0,0,'City','US','Address','TX','00000',0,0,'2026-01-12T00:30:00+14')
                """,load,c);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(email,"test",List.of(new SimpleGrantedAuthority("ROLE_ACCOUNTANT"))));
        if(captureDate)dates.remediate(load,new SetPickupBusinessDateRequest(DATE,new PickupBusinessDateProvenance("CUSTOMER_CONFIRMED_PICKUP","Agreement promise","fixture agreement"),null));
        return new Fixture(load,c,actor,email,policies.createContract(new RatingContractRequest(c,"USD",DATE,DATE.plusMonths(1)),actor));
    }
    private void rule(Fixture f,RatingMethod method,String rate){policies.createRule(new RateRuleRequest(10,f.customer(),f.contract().contractId(),1,null,null,null,null,"USD",DATE,DATE.plusMonths(1),method,new BigDecimal(rate),method==RatingMethod.PER_MILE?RatingMileageBasis.CONTRACT_MILES:null,null,null,null),f.actor());}
    private RatingPreviewRequest request(Fixture f,UUID evidence,List<UUID> charges){return new RatingPreviewRequest(f.contract().contractId(),1,null,null,null,null,"USD","fixture agreement dimensions",evidence,null,charges);}
    private UUID charge(Fixture f,String type,String status){
        UUID id=UUID.randomUUID();jdbc.update("""
                insert into accessorial_charges(id,load_id,type,status,customer_amount,company_cost_amount,driver_pay_amount,currency,approved_by,approved_at,version,created_at)
                values (?,?,?, ?,1.005,77,88,'USD',?,now(),1,now())
                """,id,f.load(),type,status,f.actor());return id;
    }
    @Test void realLoadPerMileAndApprovedCustomerAmountsAreExplainableAndPreviewWritesNothing(){
        var f=fixture(true);rule(f,RatingMethod.PER_MILE,"2.5");
        var evidence=miles.capture(f.load(),new ContractMileageRequest(RatingMileageComponent.LINEHAUL,f.contract().contractId(),1,new BigDecimal("123.456"),"MILE","fixture mileage"),f.actor());
        var charge=charge(f,"DETENTION","APPROVED");Long before=jdbc.queryForObject("select count(*) from calculation_snapshots",Long.class);
        var r=previews.preview(f.load(),request(f,evidence.id(),List.of(charge)),"pg-preview");
        assertEquals(new BigDecimal("309.65"),r.subtotal());assertEquals(DATE,r.inputs().pricingDate().pricingDate());
        assertEquals("LOAD_REQUESTED_PICKUP_DATE",r.inputs().pricingDate().pricingDateSource());assertEquals(f.customer(),r.inputs().matchContext().customerId());
        assertEquals(new BigDecimal("1.01"),r.lines().get(1).amount());assertEquals(charge,r.inputs().accessorials().getFirst().chargeId());
        assertEquals(before,jdbc.queryForObject("select count(*) from calculation_snapshots",Long.class));
        assertEquals("APPROVED",jdbc.queryForObject("select status from accessorial_charges where id=?",String.class,charge));
    }
    @Test void historicalNullDateRejectsDespiteTimestampAndSelectedChargeMustBeApprovedOwnLoad(){
        var historical=fixture(false);rule(historical,RatingMethod.FLAT,"10");
        assertEquals("RATING_PRICING_DATE_REQUIRED",assertThrows(BadRequestException.class,()->previews.preview(historical.load(),request(historical,null,List.of()),"pg")).getCode());
        var f=fixture(true);rule(f,RatingMethod.FLAT,"10");
        for(String status:List.of("PENDING_APPROVAL","INVOICED")){var charge=charge(f,"DETENTION",status);assertEquals("RATING_VALIDATION_REQUIRED",assertThrows(BadRequestException.class,()->previews.preview(f.load(),request(f,null,List.of(charge)),"pg")).getCode());}
        var wrong=charge(historical,"DETENTION","APPROVED");assertThrows(BadRequestException.class,()->previews.preview(f.load(),request(f,null,List.of(wrong)),"pg"));
        var good=charge(f,"DETENTION","APPROVED");assertThrows(BadRequestException.class,()->previews.preview(f.load(),request(f,null,List.of(good,good)),"pg"));
    }
    @Test void previewEndpointSerializesDateComponentsAndEnforcesAccountingRole() throws Exception {
        var f=fixture(true);rule(f,RatingMethod.FLAT,"10.005");
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(web).apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
        String body="{\"contractId\":\""+f.contract().contractId()+"\",\"contractVersion\":1,\"currency\":\"USD\",\"contextSource\":\"fixture agreement\",\"accessorialIds\":[]}";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/loads/"+f.load()+"/rating/preview").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(f.email()).roles("DRIVER")).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json").content(body)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/loads/"+f.load()+"/rating/preview").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(f.email()).roles("ACCOUNTANT")).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json").content(body)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.inputs.pricingDate.pricingDate").value("2026-01-12")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.subtotal").value(10.01));
    }
}
