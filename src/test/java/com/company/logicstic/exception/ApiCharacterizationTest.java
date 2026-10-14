package com.company.logicstic.exception;

import com.company.logicstic.controller.TruckController;
import com.company.logicstic.dto.PagedResponse;
import com.company.logicstic.service.TruckService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Pins the valid API envelopes before the shared error transport is changed. */
class ApiCharacterizationTest {
    @Test void realTruckListAndMissingDetailRetainEnvelope() throws Exception {
        var service=mock(TruckService.class);
        when(service.search(null,null,null,1,20,"number",false)).thenReturn(new PagedResponse<>(List.of(),0,0,1,20));
        UUID id=UUID.fromString("00000000-0000-0000-0000-000000000001");
        when(service.getById(id)).thenThrow(new ResourceNotFoundException("Truck not found: "+id));
        var mvc=MockMvcBuilders.standaloneSetup(new TruckController(service)).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/api/trucks").header("X-Request-Id","characterization"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK")).andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.currentPage").value(1)).andExpect(jsonPath("$.data.pageSize").value(20))
                .andExpect(jsonPath("$.meta.requestId").value("characterization"));
        mvc.perform(get("/api/trucks/"+id).header("X-Request-Id","characterization"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Truck not found: "+id)).andExpect(jsonPath("$.errors").isEmpty());
    }
}
