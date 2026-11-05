package com.company.logicstic.service;

import com.company.logicstic.common.PaginationValidator;
import com.company.logicstic.dto.CurrentUserResponse;
import com.company.logicstic.dto.PagedResponse;
import com.company.logicstic.dto.driverprofile.CreateEmploymentContractRequest;
import com.company.logicstic.dto.driverprofile.EmploymentContractView;
import com.company.logicstic.dto.driverprofile.UpdateEmploymentContractRequest;
import com.company.logicstic.entity.Document;
import com.company.logicstic.entity.Employee;
import com.company.logicstic.entity.EmployeeType;
import com.company.logicstic.entity.EmploymentContract;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.exception.ConflictException;
import com.company.logicstic.exception.ForbiddenException;
import com.company.logicstic.exception.ResourceNotFoundException;
import com.company.logicstic.mapper.EmploymentContractMapper;
import com.company.logicstic.repository.DocumentRepository;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.repository.EmploymentContractRepository;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional(readOnly = true)
public class EmploymentContractService {
    private static final String DRIVER_ROLE = "DRIVER";
    private static final Set<String> CONTRACT_DOCUMENT_TYPES = Set.of("CONTRACT", "EMPLOYMENT_CONTRACT");

    private final EmploymentContractRepository contracts;
    private final EmployeeRepository employees;
    private final DocumentRepository documents;
    private final EmploymentContractMapper mapper;
    private final CurrentUserService currentUserService;

    public EmploymentContractService(EmploymentContractRepository contracts, EmployeeRepository employees,
            DocumentRepository documents, EmploymentContractMapper mapper, CurrentUserService currentUserService) {
        this.contracts = contracts;
        this.employees = employees;
        this.documents = documents;
        this.mapper = mapper;
        this.currentUserService = currentUserService;
    }

    public PagedResponse<EmploymentContractView> list(UUID driverId, int page, int pageSize,
            String orderBy, boolean descending) {
        requireDriver(driverId);
        var pageable = PaginationValidator.pageRequest(page, pageSize,
                Sort.by(descending ? Sort.Direction.DESC : Sort.Direction.ASC, sortProperty(orderBy)));
        return PagedResponse.from(contracts.findByEmployee_Id(driverId, pageable).map(mapper::toView));
    }

    public PagedResponse<EmploymentContractView> listMine(int page, int pageSize,
            String orderBy, boolean descending) {
        return list(requireDriverIdentity().employeeId(), page, pageSize, orderBy, descending);
    }

    public EmploymentContractView get(UUID driverId, UUID contractId) {
        requireDriver(driverId);
        return mapper.toView(requireContract(driverId, contractId));
    }

    @Transactional
    public EmploymentContractView create(UUID driverId, CreateEmploymentContractRequest request) {
        Employee driver = requireDriverForUpdate(driverId);
        EmploymentContract contract = new EmploymentContract();
        contract.setEmployee(driver);
        apply(contract, request.contractNumber(), request.contractType(), request.signedDate(),
                request.effectiveDate(), request.expiryDate(), request.documentId(),
                request.terminationDate(), request.terminationReason(), driverId);
        contract.setActive(true);
        return mapper.toView(save(contract));
    }

    @Transactional
    public EmploymentContractView update(UUID driverId, UUID contractId, UpdateEmploymentContractRequest request) {
        Employee driver = requireDriverForUpdate(driverId);
        EmploymentContract contract = requireContract(driverId, contractId);
        apply(contract, request.contractNumber(), request.contractType(), request.signedDate(),
                request.effectiveDate(), request.expiryDate(), request.documentId(),
                request.terminationDate(), request.terminationReason(), driverId);
        contract.setActive(Boolean.TRUE.equals(request.active()));
        contract.setEmployee(driver);
        return mapper.toView(save(contract));
    }

    @Transactional
    public void deactivate(UUID driverId, UUID contractId) {
        requireDriverForUpdate(driverId);
        EmploymentContract contract = requireContract(driverId, contractId);
        contract.setActive(false);
        save(contract);
    }

    private void apply(EmploymentContract contract, String contractNumber, String contractType,
            LocalDate signedDate, LocalDate effectiveDate, LocalDate expiryDate, UUID documentId,
            LocalDate terminationDate, String terminationReason, UUID driverId) {
        String number = contractNumber.trim().toUpperCase(Locale.ROOT);
        UUID currentId = contract.getId();
        boolean duplicate = currentId == null
                ? contracts.existsByContractNumberIgnoreCase(number)
                : contracts.existsByContractNumberIgnoreCaseAndIdNot(number, currentId);
        if (duplicate) throw ConflictException.forUniqueField("contractNumber", "This contract number is already in use");

        if (signedDate.isAfter(effectiveDate)) {
            throw new BadRequestException("INVALID_CONTRACT_DATES", "A contract cannot take effect before it is signed");
        }
        if ("INDEFINITE".equals(contractType)) {
            if (expiryDate != null) throw new BadRequestException("INVALID_CONTRACT_DATES", "An indefinite contract cannot have an expiry date");
        } else if (expiryDate == null || expiryDate.isBefore(effectiveDate)) {
            throw new BadRequestException("INVALID_CONTRACT_DATES", "A fixed-term contract expiry must be on or after its effective date");
        }
        if (terminationDate != null && terminationDate.isBefore(effectiveDate)) {
            throw new BadRequestException("INVALID_CONTRACT_DATES", "Termination date cannot precede the contract effective date");
        }
        if (terminationDate != null && !StringUtils.hasText(terminationReason)) {
            throw new BadRequestException("TERMINATION_REASON_REQUIRED", "Termination reason is required when a termination date is supplied");
        }

        contract.setContractNumber(number);
        contract.setContractType(contractType);
        contract.setSignedDate(signedDate);
        contract.setEffectiveDate(effectiveDate);
        contract.setExpiryDate(expiryDate);
        contract.setDocument(requireDocument(documentId, driverId));
        contract.setTerminationDate(terminationDate);
        contract.setTerminationReason(blankToNull(terminationReason));
    }

    private Document requireDocument(UUID documentId, UUID driverId) {
        if (documentId == null) return null;
        Document document = documents.findByIdAndEmployee_Id(documentId, driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver contract document not found: " + documentId));
        String type = document.getType() == null ? "" : document.getType().trim().toUpperCase(Locale.ROOT);
        if (!CONTRACT_DOCUMENT_TYPES.contains(type)) {
            throw new BadRequestException("INVALID_DRIVER_DOCUMENT_TYPE", "Linked document must be an employment contract document");
        }
        return document;
    }

    private EmploymentContract save(EmploymentContract contract) {
        try {
            return contracts.saveAndFlush(contract);
        } catch (DataIntegrityViolationException conflict) {
            throw ConflictException.forUniqueField("contractNumber", "This contract number is already in use");
        }
    }

    private EmploymentContract requireContract(UUID driverId, UUID contractId) {
        return contracts.findByIdAndEmployee_Id(contractId, driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Employment contract not found: " + contractId));
    }

    private Employee requireDriver(UUID id) {
        Employee driver = employees.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found: " + id));
        if (driver.getEmployeeType() != EmployeeType.DRIVER) {
            throw new ResourceNotFoundException("Driver not found: " + id);
        }
        return driver;
    }

    private Employee requireDriverForUpdate(UUID id) {
        Employee driver = employees.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found: " + id));
        if (driver.getEmployeeType() != EmployeeType.DRIVER) {
            throw new ResourceNotFoundException("Driver not found: " + id);
        }
        return driver;
    }

    private CurrentUserResponse requireDriverIdentity() {
        CurrentUserResponse identity = currentUserService.requireMappedEmployee();
        if (!identity.roles().contains(DRIVER_ROLE)) {
            throw new ForbiddenException("Driver self-service is restricted to driver accounts");
        }
        return identity;
    }

    private static String sortProperty(String field) {
        return switch (field == null ? "effectiveDate" : field) {
            case "contractNumber" -> "contractNumber";
            case "effectiveDate" -> "effectiveDate";
            case "expiryDate" -> "expiryDate";
            case "createdAt" -> "createdAt";
            default -> throw new BadRequestException("INVALID_SORT_FIELD", "Choose a supported contract sort field");
        };
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
