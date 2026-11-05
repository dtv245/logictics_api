package com.company.logicstic.ai.gap;

import com.company.logicstic.entity.Load;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class GapRuleEngine {

    public record GapViolation(
        String ruleCode,
        String fieldName,
        String severity, // "BLOCKING", "WARNING"
        String questionTemplate,
        String proposedValue
    ) {}

    public List<GapViolation> evaluate(Load load) {
        List<GapViolation> violations = new ArrayList<>();
        if (load == null) return violations;

        // 1. Missing Origin Coordinates (BLOCKING)
        Double originLat = load.getOriginLocationLatitude();
        Double originLon = load.getOriginLocationLongitude();
        if (originLat == null || originLon == null || (Math.abs(originLat) < 0.0001 && Math.abs(originLon) < 0.0001)) {
            violations.add(new GapViolation(
                "MISSING_ORIGIN_COORDINATES",
                "originLocationLatitude",
                "BLOCKING",
                "Điểm lấy hàng chưa có tọa độ GPS hợp lệ để tính toán lộ trình.",
                null
            ));
        }

        // 2. Missing Destination Coordinates (BLOCKING)
        Double destLat = load.getDestinationLocationLatitude();
        Double destLon = load.getDestinationLocationLongitude();
        if (destLat == null || destLon == null || (Math.abs(destLat) < 0.0001 && Math.abs(destLon) < 0.0001)) {
            violations.add(new GapViolation(
                "MISSING_DESTINATION_COORDINATES",
                "destinationLocationLatitude",
                "BLOCKING",
                "Điểm giao hàng chưa có tọa độ GPS hợp lệ để tính toán lộ trình.",
                null
            ));
        }

        // 3. Missing Origin Address City/State (BLOCKING)
        if (isBlank(load.getOriginAddressCity()) || isBlank(load.getOriginAddressState())) {
            violations.add(new GapViolation(
                "MISSING_ORIGIN_ADDRESS",
                "originAddressCity",
                "BLOCKING",
                "Vui lòng cung cấp đầy đủ thông tin thành phố và bang/tỉnh của điểm lấy hàng.",
                null
            ));
        }

        // 4. Missing Destination Address City/State (BLOCKING)
        if (isBlank(load.getDestinationAddressCity()) || isBlank(load.getDestinationAddressState())) {
            violations.add(new GapViolation(
                "MISSING_DESTINATION_ADDRESS",
                "destinationAddressCity",
                "BLOCKING",
                "Vui lòng cung cấp đầy đủ thông tin thành phố và bang/tỉnh của điểm giao hàng.",
                null
            ));
        }

        // 5. Invalid Appointment Window: Pickup after Delivery (BLOCKING)
        OffsetDateTime pickup = load.getRequestedPickupDate();
        OffsetDateTime delivery = load.getRequestedDeliveryDate();
        if (pickup != null && delivery != null && pickup.isAfter(delivery)) {
            violations.add(new GapViolation(
                "INVALID_APPOINTMENT_WINDOW",
                "requestedPickupDate",
                "BLOCKING",
                "Khung giờ lấy hàng đang muộn hơn khung giờ giao hàng. Vui lòng điều chỉnh lại lịch trình hợp lý.",
                null
            ));
        }

        // 6. Missing Pickup Schedule (BLOCKING)
        if (pickup == null && load.getRequestedPickupBusinessDate() == null) {
            violations.add(new GapViolation(
                "MISSING_PICKUP_SCHEDULE",
                "requestedPickupDate",
                "BLOCKING",
                "Chuyến hàng chưa có ngày/giờ hẹn lấy hàng dự kiến.",
                null
            ));
        }

        // 7. Missing Delivery Schedule (BLOCKING)
        if (delivery == null) {
            violations.add(new GapViolation(
                "MISSING_DELIVERY_SCHEDULE",
                "requestedDeliveryDate",
                "BLOCKING",
                "Chuyến hàng chưa có ngày/giờ hẹn giao hàng dự kiến.",
                null
            ));
        }

        // 8. Missing Revenue (BLOCKING)
        BigDecimal amount = load.getDeliveryCostAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            violations.add(new GapViolation(
                "MISSING_REVENUE",
                "deliveryCostAmount",
                "BLOCKING",
                "Chuyến hàng chưa có giá cước/doanh thu (deliveryCostAmount) để tính toán hiệu quả biên lợi nhuận.",
                null
            ));
        }

        // 9. Hazmat Details Missing (BLOCKING)
        if (Boolean.TRUE.equals(load.getIsHazmat()) && (isBlank(load.getHazmatClass()) || isBlank(load.getUnNumber()))) {
            violations.add(new GapViolation(
                "HAZMAT_DETAILS_MISSING",
                "hazmatClass",
                "BLOCKING",
                "Chuyến hàng đánh dấu hàng nguy hiểm (Hazmat) nhưng chưa có thông tin phân loại (Hazmat Class) hoặc mã UN.",
                null
            ));
        }

        // 10. Note Conflict (WARNING)
        String notes = load.getNotes();
        String loadType = load.getType();
        if (notes != null && !notes.isBlank()) {
            String lowerNotes = notes.toLowerCase(Locale.ROOT);
            if (lowerNotes.contains("reefer") || lowerNotes.contains("đông lạnh") || lowerNotes.contains("nhiệt độ")) {
                if (loadType != null && !loadType.equalsIgnoreCase("reefer")) {
                    violations.add(new GapViolation(
                        "NOTE_CONFLICT",
                        "notes",
                        "WARNING",
                        "Ghi chú đơn hàng có yêu cầu xe đông lạnh nhưng loại chuyến hàng hiện tại là " + loadType + ".",
                        "REEFER"
                    ));
                }
            }
        }

        // 11. Missing Dispatcher (WARNING)
        if (load.getAssignedDispatcher() == null) {
            violations.add(new GapViolation(
                "MISSING_DISPATCHER",
                "assignedDispatcher",
                "WARNING",
                "Chuyến hàng chưa được phân công cho người điều phối phụ trách.",
                null
            ));
        }

        // 12. Missing Distance (WARNING)
        Double distance = load.getDistance();
        if (distance == null || distance <= 0.0) {
            violations.add(new GapViolation(
                "MISSING_DISTANCE",
                "distance",
                "WARNING",
                "Khoảng cách vận chuyển chưa được ước tính.",
                null
            ));
        }

        return violations;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
