package com.company.logicstic.integration.lark.base;

import com.company.logicstic.entity.Employee;
import com.company.logicstic.entity.Load;
import com.company.logicstic.exception.ApiException;
import com.company.logicstic.exception.ResourceNotFoundException;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.repository.LoadRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class LarkBaseService {

  private static final Logger log = LoggerFactory.getLogger(LarkBaseService.class);

  private final LarkBaseProperties baseProperties;
  private final LarkBaseClient baseClient;
  private final LoadRepository loadRepository;
  private final EmployeeRepository employeeRepository;

  public LarkBaseService(
      LarkBaseProperties baseProperties,
      LarkBaseClient baseClient,
      LoadRepository loadRepository,
      EmployeeRepository employeeRepository) {
    this.baseProperties = baseProperties;
    this.baseClient = baseClient;
    this.loadRepository = loadRepository;
    this.employeeRepository = employeeRepository;
  }

  @Transactional(readOnly = true)
  public LarkBaseRecord syncLoad(UUID loadId) {
    ensureBaseConfigured(baseProperties.loadsTableId(), "Loads Table");

    Load load = loadRepository.findById(loadId)
        .orElseThrow(() -> new ResourceNotFoundException("Load not found with id: " + loadId));

    Map<String, Object> fields = new HashMap<>();
    if (load.getNumber() != null) {
      fields.put("Load Number", load.getNumber());
    }
    if (StringUtils.hasText(load.getName())) {
      fields.put("Load Name", load.getName());
    }
    if (StringUtils.hasText(load.getStatus())) {
      fields.put("Status", load.getStatus());
    }
    if (load.getCustomer() != null && StringUtils.hasText(load.getCustomer().getName())) {
      fields.put("Customer", load.getCustomer().getName());
    }
    if (load.getAssignedTruck() != null && StringUtils.hasText(load.getAssignedTruck().getNumber())) {
      fields.put("Truck Number", load.getAssignedTruck().getNumber());
    }
    if (load.getAssignedDispatcher() != null) {
      String dispatcherName = (load.getAssignedDispatcher().getFirstName() + " " + load.getAssignedDispatcher().getLastName()).trim();
      fields.put("Dispatcher", dispatcherName);
    }
    if (load.getRequestedDeliveryDate() != null) {
      fields.put("Delivery Date", load.getRequestedDeliveryDate().toString());
    }

    LarkBaseRecord record = LarkBaseRecord.of(fields);
    LarkBaseRecord created = baseClient.createRecord(baseProperties.appToken(), baseProperties.loadsTableId(), record);
    log.info("Successfully synced load [{}] to Lark Base record [{}]", loadId, created.recordId());
    return created;
  }

  @Transactional(readOnly = true)
  public LarkBaseRecord syncDriver(UUID employeeId) {
    ensureBaseConfigured(baseProperties.driversTableId(), "Drivers Table");

    Employee employee = employeeRepository.findById(employeeId)
        .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + employeeId));

    Map<String, Object> fields = new HashMap<>();
    fields.put("Full Name", (employee.getFirstName() + " " + employee.getLastName()).trim());
    fields.put("Email", employee.getEmail());
    if (StringUtils.hasText(employee.getPhoneNumber())) {
      fields.put("Phone", employee.getPhoneNumber());
    }
    if (StringUtils.hasText(employee.getStatus())) {
      fields.put("Status", employee.getStatus());
    }
    if (StringUtils.hasText(employee.getSalaryType())) {
      fields.put("Salary Type", employee.getSalaryType());
    }

    LarkBaseRecord record = LarkBaseRecord.of(fields);
    LarkBaseRecord created = baseClient.createRecord(baseProperties.appToken(), baseProperties.driversTableId(), record);
    log.info("Successfully synced employee [{}] to Lark Base record [{}]", employeeId, created.recordId());
    return created;
  }

  public List<LarkBaseRecord> listSyncedLoads(Integer pageSize, String pageToken) {
    ensureBaseConfigured(baseProperties.loadsTableId(), "Loads Table");
    return baseClient.listRecords(baseProperties.appToken(), baseProperties.loadsTableId(), pageSize, pageToken);
  }

  private void ensureBaseConfigured(String tableId, String targetName) {
    if (!StringUtils.hasText(baseProperties.appToken())) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "LARK_BASE_NOT_CONFIGURED",
          "Lark Base appToken chưa được cấu hình trong app.lark.base.app-token");
    }
    if (!StringUtils.hasText(tableId)) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "LARK_BASE_NOT_CONFIGURED",
          "Table ID cho " + targetName + " chưa được cấu hình");
    }
  }
}
