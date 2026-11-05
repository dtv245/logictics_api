package com.company.logicstic.mapper;

import com.company.logicstic.dto.driverprofile.DriverAccidentView;
import com.company.logicstic.entity.AccidentReport;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DriverAccidentMapper {
    public DriverAccidentView toView(AccidentReport accident, List<UUID> documentIds) {
        return new DriverAccidentView(
                accident.getId(), accident.getDriver().getId(), accident.getTruck().getId(),
                accident.getTrip() == null ? null : accident.getTrip().getId(), accident.getAccidentType(),
                accident.getSeverity(), accident.getAccidentDateTime(), accident.getLatitude(),
                accident.getLongitude(), accident.getAddress(), accident.getDescription(),
                accident.getWeatherConditions(), accident.getRoadConditions(), accident.getAnyInjuries(),
                accident.getNumberOfInjuries(), accident.getInjuryDescription(), accident.getVehicleDamaged(),
                accident.getVehicleDamageDescription(), accident.getEstimatedDamageCost(),
                accident.getVehicleDrivable(), accident.getPoliceReportFiled(), accident.getPoliceReportNumber(),
                accident.getInsuranceNotified(), accident.getInsuranceNotifiedAt(),
                accident.getInsuranceClaimNumber(), accident.getStatus(), List.copyOf(documentIds));
    }
}
