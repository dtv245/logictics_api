package com.company.logicstic.integration.lark.base;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** The legacy routes must use the same persisted command as the new UI routes. */
class LarkBaseServiceTest {
  @Test void syncLoad_success() {
    var sync=mock(LarkSyncService.class);var id=UUID.randomUUID();
    when(sync.sync("load",id,false)).thenReturn(new LarkSyncView(id,"load","SYNCED","record-fixture",null,null,false));
    var facade=new LarkBaseService(sync,mock(LarkSyncMapping.class),mock(LarkBaseClient.class));
    assertEquals("record-fixture",facade.syncLoad(id).recordId());
    verify(sync).sync("load",id,false);
  }
  @Test void syncDriver_success() {
    var sync=mock(LarkSyncService.class);var id=UUID.randomUUID();
    when(sync.sync("driver",id,false)).thenReturn(new LarkSyncView(id,"driver","SYNCED","record-fixture",null,null,false));
    var facade=new LarkBaseService(sync,mock(LarkSyncMapping.class),mock(LarkBaseClient.class));
    assertEquals("record-fixture",facade.syncDriver(id).recordId());
  }
  @Test void syncLoad_unconfiguredBase_throwsException() {
    var sync=mock(LarkSyncService.class);var id=UUID.randomUUID();
    when(sync.sync("load",id,false)).thenThrow(LarkSyncMapping.error("LARK_BASE_SANDBOX_REQUIRED"));
    var client=mock(LarkBaseClient.class);
    var facade=new LarkBaseService(sync,mock(LarkSyncMapping.class),client);
    assertThrows(com.company.logicstic.exception.ApiException.class,()->facade.syncLoad(id));
    verifyNoInteractions(client);
  }
}
