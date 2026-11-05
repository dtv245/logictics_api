package com.company.logicstic.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.logicstic.dto.PagedResponse;
import com.company.logicstic.dto.employee.CreateEmployeeRequest;
import com.company.logicstic.dto.employee.EmployeeView;
import com.company.logicstic.entity.Employee;
import com.company.logicstic.entity.EmployeeType;
import com.company.logicstic.exception.ResourceNotFoundException;
import com.company.logicstic.exception.ConflictException;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.repository.DriverLicenseRepository;
import com.company.logicstic.repository.DriverHealthCheckRepository;
import com.company.logicstic.repository.DriverCertificationRepository;
import com.company.logicstic.repository.EmploymentContractRepository;
import com.company.logicstic.repository.DriverInsuranceRepository;
import com.company.logicstic.repository.DriverDependentRepository;
import com.company.logicstic.repository.DriverBankAccountRepository;
import com.company.logicstic.repository.DriverDirectoryRepository;
import com.company.logicstic.common.PaginationValidator;
import com.company.logicstic.common.QuerySortPolicy;
import com.company.logicstic.exception.BadRequestException;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.Page;

@Service
@Transactional(readOnly = true)
public class DriverService {

    private static final Set<String> AVAILABILITIES = Set.of("AVAILABLE", "ON_TRIP", "OFF_DUTY", "ON_LEAVE", "SUSPENDED");

    private final EmployeeService employeeService;
    private final EmployeeRepository employeeRepository;
    private final DriverLicenseRepository driverLicenses;
    private final DriverHealthCheckRepository healthChecks;
    private final DriverCertificationRepository certifications;
    private final EmploymentContractRepository contracts;
    private final DriverInsuranceRepository insurances;
    private final DriverDependentRepository dependents;
    private final DriverBankAccountRepository bankAccounts;
    private final DriverDirectoryRepository driverDirectory;

    public DriverService(
            EmployeeService employeeService,
            EmployeeRepository employeeRepository,
            DriverLicenseRepository driverLicenses,
            DriverHealthCheckRepository healthChecks,
            DriverCertificationRepository certifications,
            EmploymentContractRepository contracts,
            DriverInsuranceRepository insurances,
            DriverDependentRepository dependents,
            DriverBankAccountRepository bankAccounts,
            DriverDirectoryRepository driverDirectory
    ) {
        this.employeeService = employeeService;
        this.employeeRepository = employeeRepository;
        this.driverLicenses = driverLicenses;
        this.healthChecks = healthChecks;
        this.certifications = certifications;
        this.contracts = contracts;
        this.insurances = insurances;
        this.dependents = dependents;
        this.bankAccounts = bankAccounts;
        this.driverDirectory = driverDirectory;
    }

    public PagedResponse<EmployeeView> search(
            String search,
            String status,
            int page,
            int pageSize,
            String orderBy,
            boolean descending
    ) {
        return search(search, status, page, pageSize, orderBy, descending, null);
    }

    public PagedResponse<EmployeeView> search(
            String search,
            String status,
            int page,
            int pageSize,
            String orderBy,
            boolean descending,
            String availability
    ) {
        if (availability == null || availability.isBlank()) {
            return employeeService.searchDrivers(search, status, page, pageSize, orderBy, descending);
        }
        String normalizedAvailability = availability.trim().toUpperCase(Locale.ROOT);
        if (!AVAILABILITIES.contains(normalizedAvailability)) {
            throw new BadRequestException("INVALID_DRIVER_AVAILABILITY", "Choose a supported driver availability");
        }
        var sort = QuerySortPolicy.sort("employees", orderBy, descending);
        var pageable = PaginationValidator.pageRequest(page, pageSize, sort);
        return PagedResponse.from(driverDirectory.searchByAvailability(
                EmployeeType.DRIVER, search == null ? "" : search.trim(), status, normalizedAvailability, pageable).map(EmployeeView::from));
    }

    public EmployeeView getById(UUID id) {
        return EmployeeView.from(requireDriver(id));
    }

    @Transactional
    public EmployeeView create(CreateEmployeeRequest request) {
        return employeeService.createDriver(request);
    }

    @Transactional
    public EmployeeView update(UUID id, CreateEmployeeRequest request) {
        requireDriver(id);
        return employeeService.updateDriver(id, request);
    }

    @Transactional
    public void delete(UUID id) {
        requireDriver(id);
        if (driverLicenses.existsByEmployee_Id(id)
                || healthChecks.existsByEmployee_Id(id)
                || certifications.existsByEmployee_Id(id)
                || contracts.existsByEmployee_Id(id)
                || insurances.existsByEmployee_Id(id)
                || dependents.existsByEmployee_Id(id)
                || bankAccounts.existsByEmployee_Id(id)) {
            throw new ConflictException("DRIVER_HAS_COMPLIANCE_RECORDS",
                    "Driver cannot be permanently deleted while compliance records exist");
        }
        employeeService.delete(id);
    }

    private Employee requireDriver(UUID id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found: " + id));
        if (employee.getEmployeeType() != EmployeeType.DRIVER) {
            throw new ResourceNotFoundException("Driver not found: " + id);
        }
        return employee;
    }


}
