package com.company.logicstic.modules.employee.dto;

import com.company.logicstic.modules.employee.entity.Employee;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record EmployeeView(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String phoneNumber,
        String salaryType,
        String status,
        OffsetDateTime joinedDate,
        UUID roleId,
        String roleName,
        BigDecimal salaryAmount,
        String salaryCurrency,
        String addressLine1,
        String addressLine2,
        String addressCity,
        String addressState,
        String addressZipCode,
        String addressCountry
) {
    public static EmployeeView from(Employee e) {
        return new EmployeeView(
                e.getId(), e.getEmail(), e.getFirstName(), e.getLastName(),
                e.getPhoneNumber(), e.getSalaryType(), e.getStatus(), e.getJoinedDate(),
                e.getRole() != null ? e.getRole().getId() : null,
                e.getRole() != null ? e.getRole().getName() : null,
                e.getSalaryAmount(), e.getSalaryCurrency(),
                e.getAddressLine1(), e.getAddressLine2(), e.getAddressCity(),
                e.getAddressState(), e.getAddressZipCode(), e.getAddressCountry()
        );
    }
}
