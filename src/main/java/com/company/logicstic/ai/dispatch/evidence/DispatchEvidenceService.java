package com.company.logicstic.ai.dispatch.evidence;

import com.company.logicstic.entity.DriverHosStatus;
import com.company.logicstic.entity.DriverPayPolicy;
import com.company.logicstic.entity.Load;
import com.company.logicstic.entity.Truck;
import com.company.logicstic.repository.DriverHosStatusRepository;
import com.company.logicstic.repository.DriverPayPolicyRepository;
import com.company.logicstic.repository.TruckRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Builds deterministic, source-backed dispatch evidence. It never uses Load.distance as road distance. */
@Service
public class DispatchEvidenceService {
    public record Candidate(Truck truck, double deadheadMiles, double totalRouteMiles,
            Duration driveTime, OffsetDateTime eta, int drivingMinutesRemaining,
            BigDecimal driverPayEstimate, String payCurrency, BigDecimal score,
            String evidenceJson, String reasoning) { }
    public record Assessment(List<Candidate> candidates, List<String> missingEvidence) {
        public Assessment { candidates = List.copyOf(candidates); missingEvidence = List.copyOf(missingEvidence); }
    }
    private record Route(double miles, Duration duration) { }

    private final TruckRepository truckRepository;
    private final DriverHosStatusRepository hosRepository;
    private final DriverPayPolicyRepository payRepository;
    private final ObjectMapper objectMapper;
    private final String routingBaseUrl;
    private final Duration hosMaxAge;
    private final RestClient restClient;

    public DispatchEvidenceService(TruckRepository truckRepository, DriverHosStatusRepository hosRepository,
            DriverPayPolicyRepository payRepository, ObjectMapper objectMapper,
            RestClient.Builder restClientBuilder,
            @Value("${ai.dispatch.routing.base-url:}") String routingBaseUrl,
            @Value("${ai.dispatch.hos.max-age:}") String hosMaxAge) {
        this.truckRepository = truckRepository;
        this.hosRepository = hosRepository;
        this.payRepository = payRepository;
        this.objectMapper = objectMapper;
        this.routingBaseUrl = routingBaseUrl == null ? "" : routingBaseUrl.trim();
        this.hosMaxAge = parseDuration(hosMaxAge);
        this.restClient = restClientBuilder.build();
    }

    public Assessment assess(Load load, Integer requestedLimit) {
        Set<String> missing = new LinkedHashSet<>();
        List<Candidate> eligible = new ArrayList<>();
        if (!hasCoordinates(load.getOriginLocationLatitude(), load.getOriginLocationLongitude(),
                load.getDestinationLocationLatitude(), load.getDestinationLocationLongitude())) {
            missing.add("Load " + load.getNumber() + " thiếu tọa độ lấy hàng hoặc giao hàng.");
        }
        if (load.getRequestedPickupDate() == null) missing.add("Load " + load.getNumber() + " chưa có thời điểm lấy hàng để tính ETA.");
        if (load.getRequestedDeliveryDate() == null) missing.add("Load " + load.getNumber() + " chưa có deadline giao hàng để đối chiếu ETA.");
        if (routingBaseUrl.isBlank()) missing.add("Chưa cấu hình AI_DISPATCH_ROUTING_BASE_URL cho dịch vụ định tuyến.");
        if (hosMaxAge == null || hosMaxAge.isZero() || hosMaxAge.isNegative()) {
            missing.add("Chưa cấu hình AI_DISPATCH_HOS_MAX_AGE để xác định HOS còn mới.");
        }

        List<Truck> trucks = truckRepository.findAll().stream()
                .filter(t -> "AVAILABLE".equalsIgnoreCase(t.getStatus()))
                .filter(t -> t.getMainDriver() != null)
                .filter(t -> !Boolean.TRUE.equals(load.getIsHazmat()) || Boolean.TRUE.equals(t.getIsHazmatPlacarded()))
                .toList();
        if (trucks.isEmpty()) missing.add("Không có xe trạng thái AVAILABLE có tài xế chính phù hợp trong cơ sở dữ liệu.");

        if (!hasCoordinates(load.getOriginLocationLatitude(), load.getOriginLocationLongitude(),
                load.getDestinationLocationLatitude(), load.getDestinationLocationLongitude())
                || load.getRequestedPickupDate() == null || load.getRequestedDeliveryDate() == null
                || routingBaseUrl.isBlank() || hosMaxAge == null || hosMaxAge.isZero() || hosMaxAge.isNegative()) {
            return new Assessment(List.of(), List.copyOf(missing));
        }

        OffsetDateTime now = OffsetDateTime.now();
        LocalDate payDate = load.getRequestedPickupBusinessDate() != null
                ? load.getRequestedPickupBusinessDate() : load.getRequestedPickupDate().toLocalDate();
        for (Truck truck : trucks) {
            var driver = truck.getMainDriver();
            List<String> candidateMissing = new ArrayList<>();
            if (!"ACTIVE".equalsIgnoreCase(driver.getStatus())) {
                candidateMissing.add("Tài xế " + driverName(driver) + " không ở trạng thái ACTIVE.");
            }
            if (!hasCoordinates(truck.getCurrentLocationLatitude(), truck.getCurrentLocationLongitude())) {
                candidateMissing.add("Xe " + truck.getNumber() + " chưa có tọa độ vị trí hiện tại.");
            }
            Optional<DriverHosStatus> hosOptional = hosRepository.findByEmployee_Id(driver.getId());
            DriverHosStatus hos = hosOptional.orElse(null);
            if (hos == null) candidateMissing.add("Tài xế " + driverName(driver) + " chưa có bản ghi HOS.");
            else if (hos.getLastUpdatedAt() == null || hos.getLastUpdatedAt().isBefore(now.minus(hosMaxAge))) {
                candidateMissing.add("Bản ghi HOS của tài xế " + driverName(driver) + " đã quá hạn (cập nhật lần cuối: "
                        + hos.getLastUpdatedAt() + ").");
            } else if (Boolean.TRUE.equals(hos.getIsInViolation())) {
                candidateMissing.add("Tài xế " + driverName(driver) + " đang có vi phạm HOS.");
            }
            if (hos != null && hos.getTimeUntilBreakRequired() == null) {
                candidateMissing.add("Bản ghi HOS của tài xế " + driverName(driver) + " chưa có thời gian đến kỳ nghỉ bắt buộc.");
            }
            if (hos != null && load.getRequestedPickupDate().isAfter(now)
                    && hos.getLastUpdatedAt() != null && hos.getLastUpdatedAt().isBefore(load.getRequestedPickupDate())) {
                candidateMissing.add("HOS hiện tại của tài xế " + driverName(driver)
                        + " không xác nhận được giờ lái còn lại tại thời điểm lấy hàng dự kiến.");
            }
            DriverPayPolicy pay = currentPayPolicy(driver.getId(), payDate);
            if (pay == null || !hasSupportedPayBasis(pay)) {
                candidateMissing.add("Chưa có đơn giá lương hiệu lực có thể tính từ tuyến dự kiến cho tài xế " + driverName(driver) + ".");
            }
            if (!candidateMissing.isEmpty()) {
                missing.addAll(candidateMissing);
                continue;
            }
            if (!hasCoordinates(truck.getCurrentLocationLatitude(), truck.getCurrentLocationLongitude())) continue;
            try {
                Route deadhead = route(truck.getCurrentLocationLongitude(), truck.getCurrentLocationLatitude(),
                        load.getOriginLocationLongitude(), load.getOriginLocationLatitude());
                Route loaded = route(load.getOriginLocationLongitude(), load.getOriginLocationLatitude(),
                        load.getDestinationLocationLongitude(), load.getDestinationLocationLatitude());
                Duration driveTime = deadhead.duration().plus(loaded.duration());
                OffsetDateTime departure = load.getRequestedPickupDate().isAfter(now)
                        ? load.getRequestedPickupDate() : now;
                OffsetDateTime eta = departure.plus(driveTime);
                if (eta.isAfter(load.getRequestedDeliveryDate())) {
                    missing.add("Tài xế " + driverName(driver) + " không thể đến trước deadline giao hàng theo ETA định tuyến.");
                    continue;
                }
                int requiredDrivingMinutes = (int) Math.ceil(driveTime.toSeconds() / 60.0);
                int requiredOnDutyMinutes = requiredDrivingMinutes;
                if (hos.getTimeUntilBreakRequired() != null && driveTime.compareTo(hos.getTimeUntilBreakRequired()) > 0) {
                    missing.add("Tài xế " + driverName(driver) + " cần nghỉ bắt buộc trước khi hết tuyến; chưa có dữ liệu thời lượng nghỉ/điểm dừng để tính lại ETA.");
                    continue;
                }
                if (hos.getDrivingMinutesRemaining() < requiredDrivingMinutes
                        || hos.getOnDutyMinutesRemaining() < requiredOnDutyMinutes
                        || hos.getCycleMinutesRemaining() < requiredDrivingMinutes) {
                    missing.add("HOS của tài xế " + driverName(driver) + " không đủ cho thời gian chạy tuyến đã tính.");
                    continue;
                }
                double totalMiles = deadhead.miles() + loaded.miles();
                BigDecimal payEstimate = estimatePay(pay, totalMiles);
                String evidence = objectMapper.writeValueAsString(java.util.Map.ofEntries(
                        java.util.Map.entry("source", "configured_routing_api+loads+trucks+driver_hos_statuses+driver_pay_policies"),
                        java.util.Map.entry("retrievedAt", now.toString()),
                        java.util.Map.entry("routingBaseUrl", routingBaseUrl),
                        java.util.Map.entry("deadheadMiles", deadhead.miles()),
                        java.util.Map.entry("loadedMiles", loaded.miles()),
                        java.util.Map.entry("driveSeconds", driveTime.toSeconds()),
                        java.util.Map.entry("eta", eta.toString()),
                        java.util.Map.entry("hosUpdatedAt", hos.getLastUpdatedAt().toString()),
                        java.util.Map.entry("drivingMinutesRemaining", hos.getDrivingMinutesRemaining()),
                        java.util.Map.entry("onDutyMinutesRemaining", hos.getOnDutyMinutesRemaining()),
                        java.util.Map.entry("cycleMinutesRemaining", hos.getCycleMinutesRemaining()),
                        java.util.Map.entry("driverPayPolicyId", pay.getId().toString()),
                        java.util.Map.entry("driverPayEstimate", payEstimate),
                        java.util.Map.entry("driverPayCurrency", pay.getCurrency())));
                eligible.add(new Candidate(truck, deadhead.miles(), totalMiles, driveTime, eta,
                        hos.getDrivingMinutesRemaining(), payEstimate, pay.getCurrency(), BigDecimal.ZERO,
                        evidence, "ETA " + eta + "; chạy rỗng " + oneDecimal(deadhead.miles())
                        + " mi; tổng tuyến " + oneDecimal(totalMiles) + " mi; chi phí lương ước tính "
                        + payEstimate + " " + pay.getCurrency() + " theo policy " + pay.getPolicyCode() + "."));
            } catch (Exception e) {
                missing.add("Không lấy được tuyến đường xác thực cho xe " + truck.getNumber() + ": " + safeMessage(e));
            }
        }
        List<String> currencies = eligible.stream().map(Candidate::payCurrency).distinct().sorted().toList();
        if (currencies.size() > 1) {
            missing.add("Không thể so sánh chi phí lương giữa các đơn vị tiền tệ " + String.join(", ", currencies)
                    + " vì chưa có nguồn tỷ giá được cấu hình.");
            return new Assessment(List.of(), List.copyOf(missing));
        }
        eligible = rank(eligible);
        int limit = requestedLimit == null || requestedLimit < 1 ? 5 : Math.min(requestedLimit, 20);
        if (eligible.size() > limit) eligible = new ArrayList<>(eligible.subList(0, limit));
        if (eligible.isEmpty() && missing.isEmpty()) missing.add("Không có tài xế/xe nào đáp ứng đồng thời các điều kiện HOS, ETA và chi phí.");
        if (!eligible.isEmpty()) missing.add("Chưa có cấu hình nhiên liệu/tiêu hao xe; điểm xếp hạng hiện chỉ dùng lương tài xế, ETA và quãng chạy rỗng.");
        return new Assessment(eligible, List.copyOf(new LinkedHashSet<>(missing)));
    }

    private Route route(double fromLon, double fromLat, double toLon, double toLat) throws Exception {
        String url = routingBaseUrl.replaceAll("/$", "") + "/route/v1/driving/" + fromLon + "," + fromLat
                + ";" + toLon + "," + toLat + "?overview=false&steps=false";
        String body = restClient.get().uri(URI.create(url)).retrieve().body(String.class);
        JsonNode root = body == null ? null : objectMapper.readTree(body);
        JsonNode route = root == null ? null : root.path("routes").path(0);
        if (route == null || route.isMissingNode() || !route.path("distance").isNumber()
                || !route.path("duration").isNumber()) throw new IllegalStateException("dịch vụ không trả về distance/duration hợp lệ");
        return new Route(route.path("distance").asDouble() / 1609.344,
                Duration.ofSeconds((long) Math.ceil(route.path("duration").asDouble())));
    }

    private DriverPayPolicy currentPayPolicy(java.util.UUID driverId, LocalDate date) {
        List<DriverPayPolicy> driverPolicies = payRepository.findDriverPoliciesAt(driverId, date);
        return driverPolicies.stream().filter(p -> p.getEffectiveTo() == null || !p.getEffectiveTo().isBefore(date))
                .findFirst().orElseGet(() -> payRepository.findDefaultPoliciesAt(date).stream()
                        .filter(p -> p.getEffectiveTo() == null || !p.getEffectiveTo().isBefore(date)).findFirst().orElse(null));
    }

    private static BigDecimal estimatePay(DriverPayPolicy policy, double miles) {
        if ("PER_MILE".equalsIgnoreCase(policy.getPayMethod()) && isPlannedMileageBasis(policy.getMileageBasis())) {
            return policy.getPerMileRate().multiply(BigDecimal.valueOf(miles)).setScale(2, RoundingMode.HALF_UP);
        }
        if ("PER_LOAD".equalsIgnoreCase(policy.getPayMethod()) && policy.getPerLoadRate() != null)
            return policy.getPerLoadRate().setScale(2, RoundingMode.HALF_UP);
        return null;
    }

    private static boolean hasSupportedPayBasis(DriverPayPolicy policy) {
        if ("PER_LOAD".equalsIgnoreCase(policy.getPayMethod())) return policy.getPerLoadRate() != null;
        return "PER_MILE".equalsIgnoreCase(policy.getPayMethod()) && policy.getPerMileRate() != null
                && isPlannedMileageBasis(policy.getMileageBasis());
    }
    private static boolean isPlannedMileageBasis(String basis) {
        return basis != null && switch (basis.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "PLANNED", "PLANNED_MILES", "PLANNED_ALL_MILES" -> true;
            default -> false;
        };
    }

    private static List<Candidate> rank(List<Candidate> candidates) {
        if (candidates.isEmpty()) return candidates;
        double minDistance = candidates.stream().mapToDouble(Candidate::totalRouteMiles).min().orElse(0);
        double minHours = candidates.stream().mapToDouble(c -> c.driveTime().toSeconds() / 3600.0).min().orElse(0);
        BigDecimal minPay = candidates.stream().map(Candidate::driverPayEstimate).min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        return candidates.stream().map(c -> {
            double distanceUtility = ratio(minDistance, c.totalRouteMiles());
            double timeUtility = ratio(minHours, c.driveTime().toSeconds() / 3600.0);
            double payUtility = ratio(minPay.doubleValue(), c.driverPayEstimate().doubleValue());
            BigDecimal score = BigDecimal.valueOf((distanceUtility * .4 + timeUtility * .3 + payUtility * .3) * 100)
                    .setScale(2, RoundingMode.HALF_UP);
            return new Candidate(c.truck(), c.deadheadMiles(), c.totalRouteMiles(), c.driveTime(), c.eta(),
                    c.drivingMinutesRemaining(), c.driverPayEstimate(), c.payCurrency(), score, c.evidenceJson(), c.reasoning());
        }).sorted(Comparator.comparing(Candidate::score).reversed().thenComparing(c -> c.truck().getNumber())).toList();
    }

    private static double ratio(double best, double value) {
        if (best <= 0) return value <= 0 ? 1 : 0;
        return value <= 0 ? 0 : best / value;
    }
    private static boolean hasCoordinates(Double... values) {
        for (Double value : values) if (value == null || !Double.isFinite(value)) return false;
        return true;
    }
    private static String driverName(com.company.logicstic.entity.Employee e) {
        return ((e.getFirstName() == null ? "" : e.getFirstName()) + " " + (e.getLastName() == null ? "" : e.getLastName())).trim();
    }
    private static String oneDecimal(double value) { return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).toPlainString(); }
    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) return "lỗi không xác định";
        return message.replaceAll("https?://\\S+", "dịch vụ định tuyến");
    }
    private static Duration parseDuration(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Duration.parse(value.trim()); }
        catch (RuntimeException ignored) { return null; }
    }
}
