package com.company.logicstic.ai.dispatch;

import com.company.logicstic.ai.dispatch.tool.AiDispatchToolRegistry;
import com.company.logicstic.entity.Employee;
import com.company.logicstic.entity.Load;
import com.company.logicstic.entity.Truck;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.repository.LoadRepository;
import com.company.logicstic.repository.TruckRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiDispatchToolRegistryTest {

    @Mock private LoadRepository loadRepository;
    @Mock private TruckRepository truckRepository;
    @Mock private EmployeeRepository employeeRepository;

    private AiDispatchToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new AiDispatchToolRegistry(loadRepository, truckRepository, employeeRepository);
    }

    @Test
    @DisplayName("ToolRegistry lists expected tools and excludes dangerous mutation tools")
    void getAvailableTools_expectedToolsOnly() {
        var tools = registry.getAvailableTools();
        assertFalse(tools.isEmpty());

        List<String> names = tools.stream().map(AiDispatchToolRegistry.ToolDefinition::name).toList();
        assertTrue(names.contains("get_unassigned_loads"));
        assertTrue(names.contains("get_available_trucks"));
        assertFalse(names.contains("get_driver_hos_status"));
        assertFalse(names.contains("check_hos_feasibility"));
        assertTrue(names.contains("calculate_distance"));
        assertFalse(names.contains("calculate_assignment_metrics"));
        assertFalse(names.contains("create_suggestion"));

        // Dangerous direct write tools must NOT be directly exposed to LLM
        assertFalse(names.contains("assign_load_to_truck"));
        assertFalse(names.contains("dispatch_trip"));
    }

    @Test
    @DisplayName("execute calculate_distance calculates distance between coordinates")
    void execute_calculateDistance() {
        var params = Map.<String, Object>of(
            "originLat", 41.8781,
            "originLon", -87.6298,
            "destLat", 32.7767,
            "destLon", -96.7970
        );
        Object result = registry.execute("calculate_distance", params);
        assertInstanceOf(Map.class, result);
        Map<?, ?> map = (Map<?, ?>) result;
        assertTrue(map.containsKey("distanceMiles"));
        assertTrue(((Number) map.get("distanceMiles")).doubleValue() > 700.0);
    }

    @Test
    @DisplayName("HOS checks report unavailable without an authoritative data source")
    void execute_checkHosFeasibility() {
        var params = Map.<String, Object>of(
            "driverId", UUID.randomUUID().toString(),
            "durationHours", 6.5
        );
        Object result = registry.execute("check_hos_feasibility", params);
        assertInstanceOf(Map.class, result);
        Map<?, ?> map = (Map<?, ?>) result;
        assertEquals(false, map.get("available"));
        assertFalse(map.containsKey("feasible"));
        assertFalse(map.containsKey("headroomHours"));
    }
}
