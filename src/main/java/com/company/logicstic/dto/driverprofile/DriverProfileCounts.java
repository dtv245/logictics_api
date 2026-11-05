package com.company.logicstic.dto.driverprofile;

public record DriverProfileCounts(
        long personalProfiles,
        long licenses,
        long healthChecks,
        long certifications,
        long emergencyContacts,
        long contracts,
        long insurances,
        long dependents,
        long bankAccounts,
        long operationalStatuses,
        long violations,
        long accidents,
        long documents
) {
}
