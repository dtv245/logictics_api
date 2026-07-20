package com.company.logicstic.modules.fleet.repository;

import com.company.logicstic.modules.fleet.entity.Container;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContainerRepository extends JpaRepository<Container, UUID> {}
