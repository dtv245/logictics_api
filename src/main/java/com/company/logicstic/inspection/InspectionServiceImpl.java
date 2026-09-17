package com.company.logicstic.inspection;

import com.company.logicstic.employee.employee.EmployeeService;
import com.company.logicstic.load.core.LoadService;
import com.company.logicstic.shared.persistence.AbstractBaseService;
import com.company.logicstic.shared.web.PagedResponse;
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
