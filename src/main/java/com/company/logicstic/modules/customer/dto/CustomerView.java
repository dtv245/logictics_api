package com.company.logicstic.modules.customer.dto;

import com.company.logicstic.modules.customer.entity.Customer;

import java.util.UUID;

public record CustomerView(
        UUID id,
        String name,
        String email,
        String phone,
        String status,
        String notes,
        String taxId,
        Boolean isVatExempt,
        String addressLine1,
        String addressLine2,
        String addressCity,
        String addressState,
        String addressZipCode,
        String addressCountry
) {
    public static CustomerView from(Customer c) {
        return new CustomerView(
                c.getId(), c.getName(), c.getEmail(), c.getPhone(),
                c.getStatus(), c.getNotes(), c.getTaxId(), c.getIsVatExempt(),
                c.getAddressLine1(), c.getAddressLine2(), c.getAddressCity(),
                c.getAddressState(), c.getAddressZipCode(), c.getAddressCountry()
        );
    }
}
