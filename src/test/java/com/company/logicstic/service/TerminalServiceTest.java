package com.company.logicstic.service;

import com.company.logicstic.controller.TerminalController;
import com.company.logicstic.dto.terminal.CreateTerminalRequest;
import com.company.logicstic.entity.Terminal;
import com.company.logicstic.exception.ConflictException;
import com.company.logicstic.exception.GlobalExceptionHandler;
import com.company.logicstic.exception.ResourceNotFoundException;
import com.company.logicstic.repository.TerminalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class TerminalServiceTest {

    @Mock
    private TerminalRepository terminalRepository;

    @InjectMocks
    private TerminalService terminalService;

    private MockMvc mockMvc;

    private static final String VALID_TERMINAL_JSON = """
            {
                "name": "New York Terminal",
                "code": "USNYC",
                "countryCode": "US",
                "type": "SEA_PORT",
                "notes": "Main eastern sea port",
                "addressCity": "New York",
                "addressCountry": "USA",
                "addressLine1": "100 Port St",
                "addressLine2": "Suite 1",
                "addressState": "NY",
                "addressZipCode": "10001"
            }
            """;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TerminalController(terminalService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("Search returns paged terminals over HTTP")
    void testSearchTerminals() throws Exception {
        UUID id = UUID.randomUUID();
        Terminal terminal = new Terminal();
        terminal.setId(id);
        terminal.setName("New York Terminal");
        terminal.setCode("USNYC");
        terminal.setCountryCode("US");
        terminal.setType("SEA_PORT");
        terminal.setAddressCity("New York");
        terminal.setAddressCountry("USA");
        terminal.setAddressLine1("100 Port St");
        terminal.setAddressState("NY");
        terminal.setAddressZipCode("10001");

        when(terminalRepository.search(any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(terminal)));

        mockMvc.perform(get("/api/terminals").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].code").value("USNYC"))
                .andExpect(jsonPath("$.data.items[0].name").value("New York Terminal"));
    }

    @Test
    @DisplayName("Get terminal by ID returns 200 when found")
    void testGetByIdSuccess() throws Exception {
        UUID id = UUID.randomUUID();
        Terminal terminal = new Terminal();
        terminal.setId(id);
        terminal.setName("New York Terminal");
        terminal.setCode("USNYC");
        terminal.setCountryCode("US");
        terminal.setType("SEA_PORT");
        terminal.setAddressCity("New York");
        terminal.setAddressCountry("USA");
        terminal.setAddressLine1("100 Port St");
        terminal.setAddressState("NY");
        terminal.setAddressZipCode("10001");

        when(terminalRepository.findById(id)).thenReturn(Optional.of(terminal));

        mockMvc.perform(get("/api/terminals/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(id.toString()))
                .andExpect(jsonPath("$.data.code").value("USNYC"));
    }

    @Test
    @DisplayName("Get terminal by ID returns 404 when not found")
    void testGetByIdNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(terminalRepository.findById(id)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/terminals/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("Create terminal succeeds when code is unique")
    void testCreateSuccess() throws Exception {
        UUID id = UUID.randomUUID();
        when(terminalRepository.existsByCodeIgnoreCase("USNYC")).thenReturn(false);
        when(terminalRepository.save(any(Terminal.class))).thenAnswer(invocation -> {
            Terminal t = invocation.getArgument(0);
            t.setId(id);
            return t;
        });

        mockMvc.perform(post("/api/terminals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_TERMINAL_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(id.toString()))
                .andExpect(jsonPath("$.data.code").value("USNYC"));
    }

    @Test
    @DisplayName("Create terminal returns 409 when code already exists")
    void testCreateDuplicateCode() throws Exception {
        when(terminalRepository.existsByCodeIgnoreCase("USNYC")).thenReturn(true);

        mockMvc.perform(post("/api/terminals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_TERMINAL_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("DUPLICATE_VALUE"))
                .andExpect(jsonPath("$.errors[0].field").value("code"));
    }
}
