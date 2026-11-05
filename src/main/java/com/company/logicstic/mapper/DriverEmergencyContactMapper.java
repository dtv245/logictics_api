package com.company.logicstic.mapper;

import com.company.logicstic.dto.driverprofile.DriverEmergencyContactView;
import com.company.logicstic.entity.DriverEmergencyContact;
import org.springframework.stereotype.Component;

@Component
public class DriverEmergencyContactMapper {
    public DriverEmergencyContactView toView(DriverEmergencyContact contact) {
        return new DriverEmergencyContactView(contact.getId(), contact.getEmployee().getId(), contact.getFullName(),
                contact.getRelationship(), contact.getPhoneNumber(), contact.getAddress(), contact.isPrimaryContact(),
                contact.isActive(), contact.getCreatedAt(), contact.getLastModifiedAt());
    }
}
