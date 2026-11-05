package com.company.logicstic.ai.gap;

import com.company.logicstic.entity.Load;
import org.springframework.stereotype.Service;

@Service
public class GapQuestionTemplateService {

    public String formatQuestion(GapRuleEngine.GapViolation violation, Load load) {
        String loadNum = load != null && load.getNumber() != null ? "#" + load.getNumber() : "đơn hàng";
        return switch (violation.ruleCode()) {
            case "MISSING_ORIGIN_COORDINATES" ->
                "Đơn hàng " + loadNum + " chưa có tọa độ GPS điểm lấy hàng. Bạn có thể cung cấp tọa độ hoặc địa chỉ chi tiết không?";
            case "MISSING_DESTINATION_COORDINATES" ->
                "Đơn hàng " + loadNum + " chưa có tọa độ GPS điểm giao hàng. Bạn có thể cung cấp tọa độ hoặc địa chỉ chi tiết không?";
            case "MISSING_ORIGIN_ADDRESS" ->
                "Đơn hàng " + loadNum + " thiếu thành phố hoặc bang của điểm lấy hàng. Vui lòng bổ sung địa chỉ.";
            case "MISSING_DESTINATION_ADDRESS" ->
                "Đơn hàng " + loadNum + " thiếu thành phố hoặc bang của điểm giao hàng. Vui lòng bổ sung địa chỉ.";
            case "INVALID_APPOINTMENT_WINDOW" ->
                "Đơn hàng " + loadNum + " có giờ lấy hàng muộn hơn giờ giao hàng. Vui lòng điều chỉnh lại lịch trình.";
            case "MISSING_PICKUP_SCHEDULE" ->
                "Đơn hàng " + loadNum + " chưa có lịch hẹn lấy hàng dự kiến. Vui lòng chỉ định ngày giờ lấy hàng.";
            case "MISSING_DELIVERY_SCHEDULE" ->
                "Đơn hàng " + loadNum + " chưa có lịch hẹn giao hàng dự kiến. Vui lòng chỉ định ngày giờ giao hàng.";
            case "MISSING_REVENUE" ->
                "Đơn hàng " + loadNum + " chưa có giá cước (deliveryCostAmount). Vui lòng điền giá cước để hệ thống tính toán biên lợi nhuận.";
            case "HAZMAT_DETAILS_MISSING" ->
                "Đơn hàng " + loadNum + " là hàng nguy hiểm nhưng thiếu phân loại Hazmat hoặc mã UN. Vui lòng cung cấp chi tiết.";
            case "NOTE_CONFLICT" ->
                "Ghi chú đơn hàng " + loadNum + " yêu cầu xe đông lạnh nhưng loại đơn đang là " + (load != null ? load.getType() : "") + ". Bạn có muốn đổi loại xe sang Reefer không?";
            case "MISSING_DISPATCHER" ->
                "Đơn hàng " + loadNum + " chưa có người điều phối phụ trách. Vui lòng phân công người điều phối.";
            case "MISSING_DISTANCE" ->
                "Đơn hàng " + loadNum + " chưa có khoảng cách dặm vận chuyển.";
            default -> violation.questionTemplate();
        };
    }
}
