package com.company.logicstic.repository;

import com.company.logicstic.entity.LarkUserMapping;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public interface LarkUserMappingRepository extends JpaRepository<LarkUserMapping, UUID> {

    // A losing unique-key insert must roll back before the caller reloads the
    // winning mapping, even if login was invoked inside an outer transaction.
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    <S extends LarkUserMapping> S saveAndFlush(S entity);

    @EntityGraph(attributePaths = {"employee", "employee.role"})
    @Query("SELECT m FROM LarkUserMapping m WHERE m.openId = :openId")
    Optional<LarkUserMapping> findByOpenIdWithEmployee(@Param("openId") String openId);

    @EntityGraph(attributePaths = {"employee", "employee.role"})
    @Query("SELECT m FROM LarkUserMapping m WHERE m.unionId = :unionId")
    Optional<LarkUserMapping> findByUnionIdWithEmployee(@Param("unionId") String unionId);

    Optional<LarkUserMapping> findByOpenId(String openId);

    Optional<LarkUserMapping> findByUnionId(String unionId);

    Optional<LarkUserMapping> findByEmployeeId(UUID employeeId);
}
