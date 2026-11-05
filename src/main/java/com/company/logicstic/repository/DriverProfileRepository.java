package com.company.logicstic.repository;

import com.company.logicstic.entity.DriverProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverProfileRepository extends JpaRepository<DriverProfile, UUID> {
    Optional<DriverProfile> findByEmployee_Id(UUID employeeId);

    boolean existsByNationalIdNumberHmac(String hmac);
    boolean existsByNationalIdNumberHmacAndIdNot(String hmac, UUID id);

    boolean existsByPersonalTaxCodeHmac(String hmac);
    boolean existsByPersonalTaxCodeHmacAndIdNot(String hmac, UUID id);
}
