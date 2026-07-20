package com.company.logicstic.modules.fleet.repository;

import com.company.logicstic.modules.fleet.entity.Terminal;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TerminalRepository extends JpaRepository<Terminal, UUID> {}
