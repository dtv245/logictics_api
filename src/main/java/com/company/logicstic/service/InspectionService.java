package com.company.logicstic.service;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.logicstic.dto.PagedResponse;
import com.company.logicstic.dto.inspection.CreateInspectionRequest;
import com.company.logicstic.dto.inspection.InspectionView;
import com.company.logicstic.entity.LoadConditionReport;
import com.company.logicstic.exception.ResourceNotFoundException;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.repository.LoadConditionReportRepository;
import com.company.logicstic.repository.LoadRepository;

@Service
@Transactional(readOnly = true)
public class InspectionService {

    private final LoadConditionReportRepository reportRepository;
    private final LoadRepository loadRepository;
    private final EmployeeRepository employeeRepository;

    public InspectionService(LoadConditionReportRepository reportRepository,
                             LoadRepository loadRepository,
                             EmployeeRepository employeeRepository) {
        this.reportRepository = reportRepository;
        this.loadRepository = loadRepository;
        this.employeeRepository = employeeRepository;
    }

    public PagedResponse<InspectionView> search(UUID loadId, String type,
                                                 int page, int pageSize, String orderBy, boolean descending) {
        Sort sort = descending ? Sort.by(orderBy).descending() : Sort.by(orderBy).ascending();
        var pageable = PageRequest.of(page - 1, pageSize, sort);
        return PagedResponse.from(reportRepository.search(loadId, type, pageable).map(InspectionView::from));
    }

    public InspectionView getById(UUID id) {
        return reportRepository.findById(id)
                .map(InspectionView::from)
                .orElseThrow(() -> new ResourceNotFoundException("Inspection not found: " + id));
    }

    @Transactional
    public InspectionView create(CreateInspectionRequest request) {
        LoadConditionReport report = new LoadConditionReport();
        applyFields(report, request);
        return InspectionView.from(reportRepository.save(report));
    }

    @Transactional
    public InspectionView update(UUID id, CreateInspectionRequest request) {
        LoadConditionReport report = reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inspection not found: " + id));
        applyFields(report, request);
        return InspectionView.from(reportRepository.save(report));
    }

    @Transactional
    public void delete(UUID id) {
        if (!reportRepository.existsById(id)) {
            throw new ResourceNotFoundException("Inspection not found: " + id);
        }
        reportRepository.deleteById(id);
    }

    private void applyFields(LoadConditionReport report, CreateInspectionRequest req) {
        report.setType(req.type());
        report.setVin(req.vin());
        report.setVehicleYear(req.vehicleYear());
        report.setVehicleMake(req.vehicleMake());
        report.setVehicleModel(req.vehicleModel());
        report.setVehicleBodyClass(req.vehicleBodyClass());
        report.setContainerNumber(req.containerNumber());
        report.setSealNumber(req.sealNumber());
        report.setNotes(req.notes());
        report.setInspectorSignature(req.inspectorSignature());
        report.setLatitude(req.latitude());
        report.setLongitude(req.longitude());
        report.setInspectedAt(req.inspectedAt());

        report.setLoad(loadRepository.findById(req.loadId())
                .orElseThrow(() -> new ResourceNotFoundException("Load not found: " + req.loadId())));

        report.setInspectedBy(employeeRepository.findById(req.inspectedById())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + req.inspectedById())));
    }
}