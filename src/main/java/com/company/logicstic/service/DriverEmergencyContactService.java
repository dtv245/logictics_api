package com.company.logicstic.service;

import com.company.logicstic.common.PaginationValidator;
import com.company.logicstic.dto.CurrentUserResponse;
import com.company.logicstic.dto.PagedResponse;
import com.company.logicstic.dto.driverprofile.CreateDriverEmergencyContactRequest;
import com.company.logicstic.dto.driverprofile.DriverEmergencyContactView;
import com.company.logicstic.dto.driverprofile.UpdateDriverEmergencyContactRequest;
import com.company.logicstic.entity.DriverEmergencyContact;
import com.company.logicstic.entity.Employee;
import com.company.logicstic.entity.EmployeeType;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.exception.ConflictException;
import com.company.logicstic.exception.ForbiddenException;
import com.company.logicstic.exception.ResourceNotFoundException;
import com.company.logicstic.mapper.DriverEmergencyContactMapper;
import com.company.logicstic.repository.DriverEmergencyContactRepository;
import com.company.logicstic.repository.EmployeeRepository;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional(readOnly = true)
public class DriverEmergencyContactService {
    private static final String DRIVER_ROLE = "DRIVER";
    private final DriverEmergencyContactRepository contacts;
    private final EmployeeRepository employees;
    private final DriverEmergencyContactMapper mapper;
    private final CurrentUserService currentUserService;

    public DriverEmergencyContactService(DriverEmergencyContactRepository contacts, EmployeeRepository employees,
            DriverEmergencyContactMapper mapper, CurrentUserService currentUserService) {
        this.contacts = contacts;
        this.employees = employees;
        this.mapper = mapper;
        this.currentUserService = currentUserService;
    }

    public PagedResponse<DriverEmergencyContactView> list(UUID driverId, int page, int pageSize,
            String orderBy, boolean descending) {
        requireDriver(driverId);
        Sort.Direction direction = descending ? Sort.Direction.DESC : Sort.Direction.ASC;
        String property = switch (orderBy == null ? "createdAt" : orderBy) {
            case "createdAt" -> "createdAt";
            case "fullName" -> "fullName";
            default -> throw new BadRequestException("INVALID_SORT_FIELD", "Choose a supported emergency contact sort field");
        };
        var pageable = PaginationValidator.pageRequest(page, pageSize, Sort.by(direction, property));
        return PagedResponse.from(contacts.findByEmployee_IdAndActiveTrue(driverId, pageable).map(mapper::toView));
    }

    public PagedResponse<DriverEmergencyContactView> listMine(int page, int pageSize,
            String orderBy, boolean descending) {
        return list(requireDriverIdentity().employeeId(), page, pageSize, orderBy, descending);
    }

    public DriverEmergencyContactView get(UUID driverId, UUID contactId) {
        requireDriver(driverId);
        return mapper.toView(requireContact(driverId, contactId));
    }

    @Transactional
    public DriverEmergencyContactView create(UUID driverId, CreateDriverEmergencyContactRequest request) {
        Employee driver = requireDriverForUpdate(driverId);
        DriverEmergencyContact contact = new DriverEmergencyContact();
        contact.setEmployee(driver);
        apply(contact, request.fullName(), request.relationship(), request.phoneNumber(), request.address());
        contact.setActive(true);
        if (Boolean.TRUE.equals(request.primary())) clearExistingPrimary(driverId, null);
        contact.setPrimaryContact(Boolean.TRUE.equals(request.primary()));
        return mapper.toView(save(contact));
    }

    @Transactional
    public DriverEmergencyContactView update(UUID driverId, UUID contactId, UpdateDriverEmergencyContactRequest request) {
        Employee driver = requireDriverForUpdate(driverId);
        DriverEmergencyContact contact = requireContact(driverId, contactId);
        apply(contact, request.fullName(), request.relationship(), request.phoneNumber(), request.address());
        if (Boolean.TRUE.equals(request.primary()) && Boolean.TRUE.equals(request.active())) {
            clearExistingPrimary(driverId, contactId);
        }
        contact.setPrimaryContact(Boolean.TRUE.equals(request.primary()));
        contact.setActive(Boolean.TRUE.equals(request.active()));
        if (!contact.isActive()) contact.setPrimaryContact(false);
        contact.setEmployee(driver);
        return mapper.toView(save(contact));
    }

    @Transactional
    public void deactivate(UUID driverId, UUID contactId) {
        requireDriverForUpdate(driverId);
        DriverEmergencyContact contact = requireContact(driverId, contactId);
        contact.setActive(false);
        contact.setPrimaryContact(false);
        save(contact);
    }

    private void clearExistingPrimary(UUID driverId, UUID exceptId) {
        contacts.findFirstByEmployee_IdAndPrimaryContactTrueAndActiveTrue(driverId)
                .filter(existing -> !existing.getId().equals(exceptId))
                .ifPresent(existing -> {
                    existing.setPrimaryContact(false);
                    save(existing); // Release the partial unique index before promoting the next contact.
                });
    }

    private DriverEmergencyContact save(DriverEmergencyContact contact) {
        try {
            return contacts.saveAndFlush(contact);
        } catch (DataIntegrityViolationException conflict) {
            throw new ConflictException("DRIVER_EMERGENCY_CONTACT_CONFLICT",
                    "The emergency contact change conflicts with another active primary contact");
        }
    }

    private static void apply(DriverEmergencyContact contact, String fullName, String relationship,
            String phoneNumber, String address) {
        contact.setFullName(fullName.trim());
        contact.setRelationship(relationship.trim());
        contact.setPhoneNumber(phoneNumber.trim());
        contact.setAddress(StringUtils.hasText(address) ? address.trim() : null);
    }

    private DriverEmergencyContact requireContact(UUID driverId, UUID contactId) {
        return contacts.findByIdAndEmployee_Id(contactId, driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Emergency contact not found: " + contactId));
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
}
