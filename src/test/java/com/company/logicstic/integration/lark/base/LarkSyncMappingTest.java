package com.company.logicstic.integration.lark.base;

import com.company.logicstic.entity.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class LarkSyncMappingTest {
  static MockEnvironment environment() {
    var env=new MockEnvironment().withProperty("LARK_BASE_ENVIRONMENT","sandbox")
        .withProperty("LARK_BASE_ENABLED","true").withProperty("LARK_APP_ID","test-app")
        .withProperty("LARK_APP_SECRET","test-credential").withProperty("LARK_BASE_APP_TOKEN","sandbox-fixture")
        .withProperty("LARK_BASE_LOADS_TABLE_ID","table-loads").withProperty("LARK_BASE_DRIVERS_TABLE_ID","table-drivers");
    var mapping=new LarkSyncMapping(env);
    for(var domain:List.of("load","driver")) for(var f:mapping.domain(domain).fields()) env.setProperty(f.fieldEnv(),f.proposal());
    return env;
  }
  static Load load(UUID id) {
    var load=new Load();load.setId(id);load.setNumber(123L);load.setName("Fixture");load.setStatus("DRAFT");
    var customer=new Customer();customer.setName("Customer fixture");load.setCustomer(customer);return load;
  }
  @Test void epochMillisecondsPreserveInstantAcrossMidnightAndOffsets() {
    var mapping=new LarkSyncMapping(environment());var load=load(UUID.randomUUID());
    for(var value:List.of("2026-10-16T00:01:00+07:00","2026-10-15T23:59:00-07:00",
        "2026-11-01T01:30:00-04:00","2026-11-01T01:30:00-05:00")) {
      load.setRequestedDeliveryDate(OffsetDateTime.parse(value));
      assertEquals(load.getRequestedDeliveryDate().toInstant().toEpochMilli(),mapping.project("load",load,true,false).get("Delivery Date"));
    }
  }
  @Test void createOmitsEmptyAndUpdateClearsOptionalFields() {
    var mapping=new LarkSyncMapping(environment());var load=load(UUID.randomUUID());
    assertFalse(mapping.project("load",load,false,false).containsKey("Delivery Date"));
    assertNull(mapping.project("load",load,true,false).get("Delivery Date"));
    assertEquals("",mapping.project("load",load,true,false).get("Truck Number"));
    load.setName(null);
    assertThrows(com.company.logicstic.exception.ApiException.class,()->mapping.project("load",load,true,false));
  }
  @Test void fieldNamesOnlyComeFromEnvironmentAndUnknownFieldsAreConflicts() {
    var env=environment().withProperty("LARK_BASE_LOAD_NAME_FIELD","Chosen by operator");
    var mapping=new LarkSyncMapping(env);var values=mapping.project("load",load(UUID.randomUUID()),false,false);
    assertTrue(values.containsKey("Chosen by operator"));assertFalse(values.containsKey("Load Name"));
    assertTrue(mapping.unknown("load",Map.of("Unclassified column","value")));
  }
  @Test void placeholderDisabledDuplicateNamesAndInvalidZoneFailClosed() {
    var env=environment();var mapping=new LarkSyncMapping(env);mapping.validate("load");
    env.setProperty("LARK_BASE_APP_TOKEN","bascnxxxxxxxxxxxx");
    assertThrows(com.company.logicstic.exception.ApiException.class,()->mapping.validate("load"));
    env.setProperty("LARK_BASE_APP_TOKEN","sandbox-fixture");env.setProperty("LARK_BASE_ENVIRONMENT","production");
    assertThrows(com.company.logicstic.exception.ApiException.class,()->mapping.validate("load"));
    env.setProperty("LARK_BASE_ENVIRONMENT","sandbox");env.setProperty("LARK_BASE_DELIVERY_TIME_ZONE","invented-zone");
    assertThrows(com.company.logicstic.exception.ApiException.class,()->mapping.validate("load"));
  }
  @Test void tombstoneRetainsProjectionWithoutDeletingBase() {
    var mapping=new LarkSyncMapping(environment());var values=mapping.project("load",load(UUID.randomUUID()),true,true);
    assertEquals("DELETED",values.get("TMS Lifecycle"));assertEquals("Fixture",values.get("Load Name"));
  }
}

