package com.company.logicstic.repository;

import com.company.logicstic.entity.Truck;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TruckRepository extends JpaRepository<Truck, UUID> {

    @EntityGraph(attributePaths = "mainDriver")
    @Query("SELECT t FROM Truck t WHERE t.id IN :ids")
    List<Truck> findAllWithMainDriverByIdIn(@Param("ids") Collection<UUID> ids);

    boolean existsByNumber(String number);

    @EntityGraph(attributePaths = {"mainDriver", "secondaryDriver"})
    @Query("""
            SELECT t FROM Truck t
            WHERE (:search IS NULL
                   OR LOWER(t.number) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(t.vin) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(t.licensePlate) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:status IS NULL OR t.status = :status)
              AND (:type IS NULL OR t.type = :type)
            """)
    Page<Truck> search(
            @Param("search") String search,
            @Param("status") String status,
            @Param("type") String type,
            Pageable pageable
    );
}
