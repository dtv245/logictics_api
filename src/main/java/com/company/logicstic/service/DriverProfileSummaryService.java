package com.company.logicstic.service;

import com.company.logicstic.dto.driverprofile.DriverComplianceStatusView;
import com.company.logicstic.dto.driverprofile.DriverProfileCounts;
import com.company.logicstic.dto.driverprofile.DriverProfileSummaryView;
import com.company.logicstic.entity.EmployeeType;
import com.company.logicstic.exception.ResourceNotFoundException;
import com.company.logicstic.repository.DriverProfileSummaryRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DriverProfileSummaryService {
    private final com.company.logicstic.repository.EmployeeRepository employeeRepository;
    private final DriverProfileSummaryRepository summaryRepository;
    private final DriverExpiryService expiryService;
    private final DriverExpiryStatusCalculator statusCalculator;

    public DriverProfileSummaryService(
            com.company.logicstic.repository.EmployeeRepository employeeRepository,
            DriverProfileSummaryRepository summaryRepository,
            DriverExpiryService expiryService,
            DriverExpiryStatusCalculator statusCalculator) {
        this.employeeRepository = employeeRepository;
        this.summaryRepository = summaryRepository;
        this.expiryService = expiryService;
        this.statusCalculator = statusCalculator;
    }

    public DriverProfileSummaryView get(UUID driverId) {
        employeeRepository.findById(driverId)
                .filter(employee -> employee.getEmployeeType() == EmployeeType.DRIVER)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found: " + driverId));
        var today = statusCalculator.currentDate();
        var summary = summaryRepository.findByDriverId(driverId, today, statusCalculator.timeZoneId())
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found: " + driverId));
        var counts = new DriverProfileCounts(summary.personalProfiles(), summary.licenses(), summary.healthChecks(),
                summary.certifications(), summary.emergencyContacts(), summary.contracts(), summary.insurances(),
                summary.dependents(), summary.bankAccounts(), summary.operationalStatuses(), summary.violations(),
                summary.accidents(), summary.documents());
        var upcoming = expiryService.findBetween(today, today.plusDays(statusCalculator.warningDays()), null,
                driverId, 1, DriverExpiryService.SUMMARY_EXPIRY_PAGE_SIZE);
        return new DriverProfileSummaryView(driverId, counts,
                new DriverComplianceStatusView(summary.hasLicense(), statusCalculator.status(summary.licenseExpiryDate())),
                new DriverComplianceStatusView(summary.hasHealthCheck(), statusCalculator.status(summary.healthCheckExpiryDate())),
                new DriverComplianceStatusView(summary.hasContract(), statusCalculator.status(summary.contractExpiryDate())),
                new DriverComplianceStatusView(summary.hasHealthInsurance(), statusCalculator.status(summary.healthInsuranceExpiryDate())),
                upcoming.items(), upcoming.hasNext());
    }
}
