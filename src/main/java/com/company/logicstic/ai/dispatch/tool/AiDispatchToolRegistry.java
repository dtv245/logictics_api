package com.company.logicstic.ai.dispatch.tool;

import com.company.logicstic.entity.Employee;
import com.company.logicstic.entity.Load;
import com.company.logicstic.entity.Truck;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.repository.LoadRepository;
import com.company.logicstic.repository.TruckRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class AiDispatchToolRegistry {

    private final LoadRepository loadRepository;
    private final TruckRepository truckRepository;
    private final EmployeeRepository employeeRepository;

    public record ToolDefinition(
        String name,
        String description,
        Map<String, Object> inputSchema
    ) {}

    public List<ToolDefinition> getAvailableTools() {
        return List.of(
            new ToolDefinition(
                "get_unassigned_loads",
                "Lấy danh sách các chuyến hàng chưa được gán xe (Draft/Unassigned) trong tenant.",
                Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "limit", Map.of("type", "integer", "description", "Số lượng bản ghi tối đa, mặc định 20")
                    )
                )
            ),
            new ToolDefinition(
                "get_available_trucks",
                "Lấy danh sách các xe và tài xế đang khả dụng (Available).",
                Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "limit", Map.of("type", "integer", "description", "Số lượng xe tối đa, mặc định 20")
                    )
                )
            ),
            new ToolDefinition(
                "get_driver_hos_status",
                "Xem chi tiết giờ làm việc HOS (Hours of Service) của tài xế.",
                Map.of(
                    "type", "object",
                    "required", List.of("driverId"),
                    "properties", Map.of(
                        "driverId", Map.of("type", "string", "description", "UUID của tài xế")
                    )
                )
            ),
            new ToolDefinition(
                "check_hos_feasibility",
                "Kiểm tra xem tài xế có đủ quỹ giờ HOS để hoàn thành chuyến đi hay không.",
                Map.of(
                    "type", "object",
                    "required", List.of("driverId", "durationHours"),
                    "properties", Map.of(
                        "driverId", Map.of("type", "string", "description", "UUID của tài xế"),
                        "durationHours", Map.of("type", "number", "description", "Thời gian dự tính lái xe (giờ)")
                    )
                )
            ),
            new ToolDefinition(
                "calculate_distance",
                "Ước tính khoảng cách đường chim bay giữa hai tọa độ; không phải quãng đường lái xe hoặc ETA định tuyến.",
                Map.of(
                    "type", "object",
                    "required", List.of("originLat", "originLon", "destLat", "destLon"),
                    "properties", Map.of(
                        "originLat", Map.of("type", "number"),
                        "originLon", Map.of("type", "number"),
                        "destLat", Map.of("type", "number"),
                        "destLon", Map.of("type", "number")
                    )
                )
            ),
            new ToolDefinition(
                "calculate_assignment_metrics",
                "Tính toán chi tiết các chỉ số: doanh thu/mile, deadhead miles và tỷ lệ margin cho một cặp load và truck.",
                Map.of(
                    "type", "object",
                    "required", List.of("loadId", "truckId"),
                    "properties", Map.of(
                        "loadId", Map.of("type", "string"),
                        "truckId", Map.of("type", "string")
                    )
                )
            ),
            new ToolDefinition(
                "create_suggestion",
                "Tạo một đề xuất gán xe/tài xế cho đơn hàng ở trạng thái Suggested để Dispatcher phê duyệt.",
                Map.of(
                    "type", "object",
                    "required", List.of("loadId", "truckId", "driverId", "reasoning"),
                    "properties", Map.of(
                        "loadId", Map.of("type", "string", "description", "UUID của load"),
                        "truckId", Map.of("type", "string", "description", "UUID của truck"),
                        "driverId", Map.of("type", "string", "description", "UUID của driver"),
                        "reasoning", Map.of("type", "string", "description", "Lập luận giải thích lý do gợi ý bằng tiếng Việt"),
                        "confidence", Map.of("type", "string", "enum", List.of("HIGH", "MEDIUM", "LOW"))
                    )
                )
            )
        ).stream()
            .filter(tool -> Set.of("get_unassigned_loads", "get_available_trucks", "calculate_distance").contains(tool.name()))
            .toList();
    }

    public Object execute(String toolName, Map<String, Object> params) {
        log.info("Executing AI Tool name={} params={}", toolName, params);
        return switch (toolName) {
            case "get_unassigned_loads" -> executeGetUnassignedLoads(params);
            case "get_available_trucks" -> executeGetAvailableTrucks(params);
            case "get_driver_hos_status" -> executeGetDriverHosStatus(params);
            case "check_hos_feasibility" -> executeCheckHosFeasibility(params);
            case "calculate_distance" -> executeCalculateDistance(params);
            case "calculate_assignment_metrics" -> executeCalculateMetrics(params);
            case "create_suggestion" -> executeCreateSuggestion(params);
            default -> Map.of("error", "Unknown tool: " + toolName);
        };
    }

    private Object executeGetUnassignedLoads(Map<String, Object> params) {
        int limit = params != null && params.get("limit") instanceof Number n ? n.intValue() : 20;
        List<Load> unassigned = loadRepository.findUnassigned(
                org.springframework.data.domain.PageRequest.of(0, Math.max(1, limit)));

        return unassigned.stream().map(l -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", l.getId().toString());
            item.put("number", String.valueOf(l.getNumber()));
            item.put("status", Objects.toString(l.getStatus(), ""));
            item.put("originCity", Objects.toString(l.getOriginAddressCity(), ""));
            item.put("destCity", Objects.toString(l.getDestinationAddressCity(), ""));
            if (l.getDeliveryCostAmount() != null) item.put("deliveryCost", l.getDeliveryCostAmount().doubleValue());
            if (l.getDeliveryCostCurrency() != null) item.put("currency", l.getDeliveryCostCurrency());
            return item;
        }).toList();
    }

    private Object executeGetAvailableTrucks(Map<String, Object> params) {
        int limit = params != null && params.get("limit") instanceof Number n ? n.intValue() : 20;
        List<Truck> available = truckRepository.findAll().stream()
            .filter(t -> "AVAILABLE".equalsIgnoreCase(t.getStatus()) || t.getStatus() == null)
            .limit(limit)
            .toList();

        return available.stream().map(t -> {
            Map<String, Object> map = new HashMap<>();
            map.put("truckId", t.getId().toString());
            map.put("truckNumber", t.getNumber());
            if (t.getType() != null) map.put("type", t.getType());
            if (t.getVehicleCapacity() != null) map.put("capacity", t.getVehicleCapacity());
            if (t.getMainDriver() != null) {
                map.put("driverId", t.getMainDriver().getId().toString());
                map.put("driverName", t.getMainDriver().getFirstName() + " " + t.getMainDriver().getLastName());
            }
            return map;
        }).toList();
    }

    private Object executeGetDriverHosStatus(Map<String, Object> params) {
        String driverIdStr = (String) params.get("driverId");
        if (driverIdStr == null) return Map.of("error", "driverId is required");
        return Map.of("available", false, "reason", "HOS data source is not configured");
    }

    private Object executeCheckHosFeasibility(Map<String, Object> params) {
        return Map.of("available", false, "reason", "HOS data source is not configured");
    }

    private Object executeCalculateDistance(Map<String, Object> params) {
        double oLat = ((Number) params.get("originLat")).doubleValue();
        double oLon = ((Number) params.get("originLon")).doubleValue();
        double dLat = ((Number) params.get("destLat")).doubleValue();
        double dLon = ((Number) params.get("destLon")).doubleValue();

        // Haversine formula as fallback estimation
        double dLatRad = Math.toRadians(dLat - oLat);
        double dLonRad = Math.toRadians(dLon - oLon);
        double a = Math.sin(dLatRad / 2) * Math.sin(dLatRad / 2)
            + Math.cos(Math.toRadians(oLat)) * Math.cos(Math.toRadians(dLat))
            * Math.sin(dLonRad / 2) * Math.sin(dLonRad / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distanceMiles = 3958.8 * c;

        return Map.of(
            "distanceMiles", Math.round(distanceMiles * 10.0) / 10.0,
            "distanceType", "STRAIGHT_LINE_ESTIMATE"
        );
    }

    private Object executeCalculateMetrics(Map<String, Object> params) {
        return Map.of("available", false, "reason", "Qualified route and cost inputs are not configured");
    }

    private Object executeCreateSuggestion(Map<String, Object> params) {
        return Map.of("status", "UNAVAILABLE", "reason", "Suggestion persistence is not configured");
    }
}
