package com.company.logicstic.repository;

import com.company.logicstic.entity.DriverLicense;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverLicenseRepository extends JpaRepository<DriverLicense, UUID> {
    @EntityGraph(attributePaths = {"employee", "document", "frontImageDocument", "backImageDocument"})
    Page<DriverLicense> findByEmployee_Id(UUID employeeId, Pageable pageable);

    @EntityGraph(attributePaths = {"employee", "document", "frontImageDocument", "backImageDocument"})
    Optional<DriverLicense> findByIdAndEmployee_Id(UUID id, UUID employeeId);

    boolean existsByEmployee_IdAndLicenseNumberHmacAndIssuingCountry(
            UUID employeeId, String licenseNumberHmac, String issuingCountry);

    boolean existsByEmployee_IdAndLicenseNumberHmacAndIssuingCountryAndIdNot(
            UUID employeeId, String licenseNumberHmac, String issuingCountry, UUID id);

    boolean existsByEmployee_Id(UUID employeeId);
}
