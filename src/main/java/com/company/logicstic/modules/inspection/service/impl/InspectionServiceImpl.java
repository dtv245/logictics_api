package com.company.logicstic.modules.inspection.service.impl;

import com.company.logicstic.modules.employee.service.EmployeeService;
import com.company.logicstic.modules.inspection.dto.request.CreateInspectionRequest;
import com.company.logicstic.modules.inspection.dto.request.DefectRequest;
import com.company.logicstic.modules.inspection.dto.response.InspectionResponse;
import com.company.logicstic.modules.inspection.entity.ConditionDefect;
import com.company.logicstic.modules.inspection.entity.LoadConditionReport;
import com.company.logicstic.modules.inspection.mapper.InspectionMapper;
import com.company.logicstic.modules.inspection.repository.LoadConditionReportRepository;
import com.company.logicstic.modules.inspection.service.InspectionService;
import com.company.logicstic.modules.load.service.LoadService;
import com.company.logicstic.shared.dto.PagedResponse;
import com.company.logicstic.shared.service.AbstractBaseService;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Profile("!nodb")
@Service
@Transactional(readOnly = true)
public class InspectionServiceImpl
    extends AbstractBaseService<LoadConditionReport, InspectionResponse, CreateInspectionRequest>
    implements InspectionService {

  private final LoadConditionReportRepository reportRepository;
  private final LoadService loadService;
  private final EmployeeService employeeService;
  private final InspectionMapper inspectionMapper;

  public InspectionServiceImpl(
      LoadConditionReportRepository reportRepository,
      LoadService loadService,
      EmployeeService employeeService,
      InspectionMapper inspectionMapper) {
    super(
        reportRepository,
        inspectionMapper::toResponse,
        inspectionMapper::toEntity,
        inspectionMapper::updateEntity);
    this.reportRepository = reportRepository;
    this.loadService = loadService;
    this.employeeService = employeeService;
    this.inspectionMapper = inspectionMapper;
  }

  @Override
  protected String entityName() {
    return "Inspection";
  }

  public PagedResponse<InspectionResponse> search(
      UUID loadId, String type, int page, int pageSize, String orderBy, boolean descending) {
    var pageable = pageRequest(page, pageSize, orderBy, descending);
    return toPagedResponse(reportRepository.search(loadId, type, pageable));
  }

  @Override
  protected void beforeCreate(LoadConditionReport report, CreateInspectionRequest request) {
    resolveRelations(report, request);
    rebuildDefects(report, request);
  }

  @Override
  protected void beforeUpdate(LoadConditionReport report, CreateInspectionRequest request) {
    resolveRelations(report, request);
    rebuildDefects(report, request);
  }

  private void resolveRelations(LoadConditionReport report, CreateInspectionRequest req) {
    report.setLoad(loadService.getEntityById(req.loadId()));

    report.setInspectedBy(employeeService.getEntityById(req.inspectedById()));
  }

  private void rebuildDefects(LoadConditionReport report, CreateInspectionRequest request) {
    report.getDefects().clear();
    for (DefectRequest item : request.defects()) {
      ConditionDefect defect = new ConditionDefect();
      defect.setLoadConditionReport(report);
      defect.setPartCategory(item.partCategory());
      defect.setDescription(item.description());
      defect.setSeverity(item.severity());
      report.getDefects().add(defect);
    }
  }
}
