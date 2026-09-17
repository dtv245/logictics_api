package com.company.logicstic.employee.employee;

import java.util.Optional;
import java.util.UUID;

/** Exposes the tenant-local employee mapping needed by authenticated identity workflows. */
public interface CurrentEmployeeLookupService {

  Optional<UUID> findEmployeeIdByEmail(String email);
}
