package com.company.logicstic.fleet.container;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContainerRepository extends JpaRepository<Container, UUID> {}
