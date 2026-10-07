package com.company.logicstic.repository;

import com.company.logicstic.service.LoadService;
import com.company.logicstic.service.TripService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LoadTripPersistenceBoundaryTest {

    @Test
    void servicesAreAnnotatedWithTransactionalReadOnly() {
        Transactional loadTx = LoadService.class.getAnnotation(Transactional.class);
        assertNotNull(loadTx, "LoadService must have @Transactional");
        assertTrue(loadTx.readOnly(), "LoadService default transaction should be readOnly");

        Transactional tripTx = TripService.class.getAnnotation(Transactional.class);
        assertNotNull(tripTx, "TripService must have @Transactional");
        assertTrue(tripTx.readOnly(), "TripService default transaction should be readOnly");
    }

    @Test
    void loadRepositoryHasEntityGraphOnFindByIdAndSearch() throws NoSuchMethodException {
        Method findById = LoadRepository.class.getMethod("findById", UUID.class);
        EntityGraph findByIdGraph = findById.getAnnotation(EntityGraph.class);
        assertNotNull(findByIdGraph, "LoadRepository.findById must have @EntityGraph");
        assertTrue(Arrays.asList(findByIdGraph.attributePaths()).containsAll(
                Arrays.asList("customer", "assignedTruck", "assignedDispatcher")
        ));

        Method search = LoadRepository.class.getMethod("search",
                String.class, String.class, UUID.class, UUID.class, UUID.class, Pageable.class);
        EntityGraph searchGraph = search.getAnnotation(EntityGraph.class);
        assertNotNull(searchGraph, "LoadRepository.search must have @EntityGraph");
        assertTrue(Arrays.asList(searchGraph.attributePaths()).containsAll(
                Arrays.asList("customer", "assignedTruck", "assignedDispatcher")
        ));
    }

    @Test
    void tripRepositoryHasEntityGraphOnFindByIdAndSearch() throws NoSuchMethodException {
        Method findById = TripRepository.class.getMethod("findById", UUID.class);
        EntityGraph findByIdGraph = findById.getAnnotation(EntityGraph.class);
        assertNotNull(findByIdGraph, "TripRepository.findById must have @EntityGraph");
        assertTrue(Arrays.asList(findByIdGraph.attributePaths()).contains("truck"));

        Method search = TripRepository.class.getMethod("search",
                String.class, String.class, UUID.class, Pageable.class);
        EntityGraph searchGraph = search.getAnnotation(EntityGraph.class);
        assertNotNull(searchGraph, "TripRepository.search must have @EntityGraph");
        assertTrue(Arrays.asList(searchGraph.attributePaths()).contains("truck"));
    }
}
