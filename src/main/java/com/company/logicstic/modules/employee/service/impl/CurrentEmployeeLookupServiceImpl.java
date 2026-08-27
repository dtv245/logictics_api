package com.company.logicstic.modules.employee.service.impl;

import com.company.logicstic.modules.employee.entity.Employee;
import com.company.logicstic.modules.employee.repository.EmployeeRepository;
import com.company.logicstic.modules.employee.service.CurrentEmployeeLookupService;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Profile("!nodb")
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CurrentEmployeeLookupServiceImpl implements CurrentEmployeeLookupService {

  private final EmployeeRepository employeeRepository;

  @Override
  public Optional<UUID> findEmployeeIdByEmail(String email) {
    if (!StringUtils.hasText(email)) {
      return Optional.empty();
    }
    return employeeRepository.findByEmail(email.trim()).map(Employee::getId);
  }
}
