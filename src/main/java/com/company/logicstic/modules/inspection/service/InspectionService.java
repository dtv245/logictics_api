package com.company.logicstic.modules.inspection.service;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.logicstic.modules.employee.repository.EmployeeRepository;
import com.company.logicstic.modules.inspection.dto.CreateInspectionRequest;
import com.company.logicstic.modules.inspection.dto.InspectionView;
import com.company.logicstic.modules.inspection.mapper.InspectionMapper;
import com.company.logicstic.modules.load.entity.LoadConditionReport;
import com.company.logicstic.modules.load.repository.LoadConditionReportRepository;
import com.company.logicstic.modules.load.repository.LoadRepository;
import com.company.logicstic.shared.AbstractBaseService;
import com.company.logicstic.shared.dto.PagedResponse;
import com.company.logicstic.shared.exception.ResourceNotFoundException;

@Profile("!nodb")
@Service
@Transactional(readOnly = true)
public class InspectionService extends AbstractBaseService<LoadConditionReport, InspectionView, CreateInspectionRequest> {

    private final LoadConditionReportRepository reportRepository;
    private final LoadRepository loadRepository;
    private final EmployeeRepository employeeRepository;
    private final InspectionMapper inspectionMapper;

    public InspectionService(LoadConditionReportRepository reportRepository,
                             LoadRepository loadRepository,
                             EmployeeRepository employeeRepository,
                             InspectionMapper inspectionMapper) {
        super(reportRepository, inspectionMapper::toView, inspectionMapper::toEntity, inspectionMapper::updateEntity);
        this.reportRepository = reportRepository;
        this.loadRepository = loadRepository;
        this.employeeRepository = employeeRepository;
        this.inspectionMapper = inspectionMapper;
    }

    @Override
    protected String entityName() {
        return "Inspection";
    }

    public PagedResponse<InspectionView> search(UUID loadId, String type,
                                                  int page, int pageSize, String orderBy, boolean descending) {
        var pageable = pageRequest(page, pageSize, orderBy, descending);
        return toPagedResponse(reportRepository.search(loadId, type, pageable));
    }

    @Override
    protected void beforeCreate(LoadConditionReport report, CreateInspectionRequest request) {
        resolveRelations(report, request);
    }

    @Override
    protected void beforeUpdate(LoadConditionReport report, CreateInspectionRequest request) {
        resolveRelations(report, request);
    }

    private void resolveRelations(LoadConditionReport report, CreateInspectionRequest req) {
        report.setLoad(loadRepository.findById(req.loadId())
                .orElseThrow(() -> new ResourceNotFoundException("Load not found: " + req.loadId())));

        report.setInspectedBy(employeeRepository.findById(req.inspectedById())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + req.inspectedById())));
    }
}