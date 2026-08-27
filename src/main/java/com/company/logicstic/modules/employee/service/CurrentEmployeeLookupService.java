package com.company.logicstic.modules.employee.service;

import java.util.Optional;
import java.util.UUID;

/** Exposes the tenant-local employee mapping needed by authenticated identity workflows. */
public interface CurrentEmployeeLookupService {

  Optional<UUID> findEmployeeIdByEmail(String email);
}
