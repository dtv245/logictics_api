package com.company.logicstic.integration.lark.base;

import com.company.logicstic.entity.*;
import com.company.logicstic.repository.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@EnabledIfEnvironmentVariable(named="TASK_DB_URL",matches="jdbc:postgresql:.*codex_.*")
class LarkSyncPostgresTest {
  LarkSyncMapping mapping;LarkSyncStore store;LarkSyncGateway gateway;LoadRepository loads;EmployeeRepository employees;
  LarkSyncService service;UUID id;Load load;AtomicReference<LarkBaseRecord> remote;
  @BeforeEach void setup() {
    var source=new PGSimpleDataSource();source.setURL(System.getenv("TASK_DB_URL"));
    source.setUser(System.getenv("TASK_DB_USER"));source.setPassword(System.getenv("TASK_DB_PASSWORD"));
    String schema="lark_sync_"+UUID.randomUUID().toString().replace("-","");
    new JdbcTemplate(source).execute("CREATE SCHEMA "+schema);source.setCurrentSchema(schema);
    new ResourceDatabasePopulator(new ClassPathResource("db/migration/tenant/V43__track_lark_base_sync.sql")).execute(source);
    store=new LarkSyncStore(source,new DataSourceTransactionManager(source));
    mapping=new LarkSyncMapping(LarkSyncMappingTest.environment());
    gateway=mock(LarkSyncGateway.class);loads=mock(LoadRepository.class);employees=mock(EmployeeRepository.class);
    service=new LarkSyncService(mapping,store,gateway,loads,employees);
    id=UUID.randomUUID();load=LarkSyncMappingTest.load(id);remote=new AtomicReference<>();
    when(loads.findById(id)).thenReturn(Optional.of(load));
    doAnswer(i->((Supplier<?>)i.getArgument(0)).get()).when(gateway).retry(any());
    when(gateway.find(anyString(),eq(id))).thenAnswer(i->remote.get()==null?List.of():List.of(remote.get()));
    when(gateway.create(anyString(),anyMap(),any())).thenAnswer(i->{
      var record=LarkBaseRecord.of("record-fixture",new TreeMap<String,Object>(i.<Map<String,Object>>getArgument(1)));remote.set(record);return record;
    });
    when(gateway.get(anyString(),anyString())).thenAnswer(i->{
      if(remote.get()==null) throw new com.company.logicstic.exception.ApiException(org.springframework.http.HttpStatus.NOT_FOUND,"LARK_BASE_RECORD_MISSING","missing");
      return remote.get();
    });
    when(gateway.update(anyString(),anyString(),anyMap())).thenAnswer(i->{
      var record=LarkBaseRecord.of(i.getArgument(1),new TreeMap<String,Object>(i.<Map<String,Object>>getArgument(2)));remote.set(record);return record;
    });
  }
  @Test void createsOnceSkipsUnchangedAndUpdatesSameRecord() {
    assertEquals("SYNCED",service.sync("load",id,false).state());
    assertTrue(service.sync("load",id,false).unchanged());
    load.setName("Updated");assertEquals("record-fixture",service.sync("load",id,false).recordId());
    verify(gateway,times(1)).create(anyString(),anyMap(),any());verify(gateway,times(1)).update(anyString(),anyString(),anyMap());
    assertNotNull(service.status("load",id).lastSyncedAt());
  }
  @Test void concurrentCommandsCreateOneRecord() throws Exception {
    try(var pool=Executors.newFixedThreadPool(2)) {
      var jobs=List.<Callable<LarkSyncView>>of(()->service.sync("load",id,false),()->service.sync("load",id,false));
      for(var f:pool.invokeAll(jobs)) assertEquals("SYNCED",f.get().state());
    }
    verify(gateway,times(1)).create(anyString(),anyMap(),any());
  }
  @Test void uncertainCreateRecoversWithoutAnotherCreateAndPersistsError() {
    doAnswer(i->{remote.set(LarkBaseRecord.of("record-fixture",i.getArgument(1)));throw new com.company.logicstic.exception.ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"LARK_UNAVAILABLE","unavailable");})
        .when(gateway).create(anyString(),anyMap(),any());
    assertEquals("ERROR",service.sync("load",id,false).state());
    assertEquals("LARK_UNAVAILABLE",service.status("load",id).lastError());
    assertEquals("SYNCED",service.sync("load",id,false).state());
    verify(gateway,times(1)).create(anyString(),anyMap(),any());
  }
  @Test void missingRecordNeverRecreatesUntilExplicitCommand() {
    service.sync("load",id,false);remote.set(null);
    assertEquals("MISSING",service.sync("load",id,false).state());
    assertEquals(1,service.missing("load",20,0).size());
    assertEquals("MISSING",service.sync("load",id,false).state());
    verify(gateway,times(1)).create(anyString(),anyMap(),any());
    assertEquals("SYNCED",service.sync("load",id,true).state());
    verify(gateway,times(2)).create(anyString(),anyMap(),any());
  }
  @Test void unknownFieldsConflictWhileOwnedDriftIsAuditedAndOverwritten() {
    service.sync("load",id,false);
    remote.get().fields().put("Load Name","Edited on Base");
    assertEquals("SYNCED",service.sync("load",id,false).state());
    assertEquals("Fixture",remote.get().fields().get("Load Name"));
    assertTrue(store.jdbc.queryForObject("SELECT previous_fields FROM lark_base_sync_audit",String.class).contains("Edited on Base"));
    remote.get().fields().put("Unknown business field","unowned");
    assertEquals("CONFLICT",service.sync("load",id,false).state());
  }
  @Test void oldRecordNeedsExplicitBackfillAndDryRunDoesNotLinkOrWrite() {
    remote.set(LarkBaseRecord.of("old-record",mapping.project("load",load,false,false)));
    assertEquals("CONFLICT",service.sync("load",id,false).state());
    assertTrue(service.backfill("load",id,"old-record",true).eligible());
    assertNull(service.status("load",id).recordId());
    service.backfill("load",id,"old-record",false);
    assertEquals("SYNCED",service.sync("load",id,false).state());
    verify(gateway,never()).create(anyString(),anyMap(),any());
  }
  @Test void onlyActiveDriversAreCreatedButLinkedInactiveDriverIsUpdated() {
    var employee=new Employee();employee.setId(id);employee.setEmployeeType(EmployeeType.DRIVER);employee.setFirstName("Test");employee.setLastName("Driver");
    employee.setEmail("driver@example.test");employee.setStatus("ACTIVE");employee.setSalaryType("HOURLY");
    var role=new TenantRole();role.setName("DRIVER");employee.setRole(role);
    when(employees.findById(id)).thenReturn(Optional.of(employee));
    assertEquals("SYNCED",service.sync("driver",id,false).state());
    employee.setStatus("INACTIVE");
    assertEquals("SYNCED",service.sync("driver",id,false).state());assertEquals("INACTIVE",remote.get().fields().get("Status"));
    remote.set(null);var another=UUID.randomUUID();employee.setId(another);
    when(employees.findById(another)).thenReturn(Optional.of(employee));
    assertThrows(com.company.logicstic.exception.ApiException.class,()->service.sync("driver",another,false));
  }
  @Test void sourceDeletionTombstonesExistingProjectionAndNeverDeletesRemote() {
    service.sync("load",id,false);when(loads.findById(id)).thenReturn(Optional.empty());
    assertEquals("SYNCED",service.sync("load",id,false).state());
    assertEquals("DELETED",remote.get().fields().get("TMS Lifecycle"));
  }
  @Test void changedPendingCreateIntentCannotReuseTokenWithNewPayload() {
    doThrow(new com.company.logicstic.exception.ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"LARK_UNAVAILABLE","unavailable"))
      .when(gateway).create(anyString(),anyMap(),any());
    service.sync("load",id,false);load.setName("Changed while outcome unknown");
    assertEquals("CONFLICT",service.sync("load",id,false).state());
    verify(gateway,times(1)).create(anyString(),anyMap(),any());
  }
}

