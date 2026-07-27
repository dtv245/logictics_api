package com.company.logicstic.modules.inspection.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.logicstic.modules.employee.entity.Employee;
import com.company.logicstic.modules.employee.service.EmployeeService;
import com.company.logicstic.modules.inspection.dto.request.CreateInspectionRequest;
import com.company.logicstic.modules.inspection.dto.request.DefectRequest;
import com.company.logicstic.modules.inspection.entity.LoadConditionReport;
import com.company.logicstic.modules.inspection.mapper.InspectionMapper;
import com.company.logicstic.modules.inspection.repository.LoadConditionReportRepository;
import com.company.logicstic.modules.inspection.service.impl.InspectionServiceImpl;
import com.company.logicstic.modules.load.entity.Load;
import com.company.logicstic.modules.load.service.LoadService;
import java.lang.reflect.Proxy;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class InspectionDefectsTest {

  @Test
  void createAndUpdateRebuildDefectsWithBidirectionalOwnership() {
    UUID reportId = UUID.randomUUID();
    UUID loadId = UUID.randomUUID();
    UUID inspectorId = UUID.randomUUID();
    Load load = new Load();
    load.setId(loadId);
    Employee inspector = new Employee();
    inspector.setId(inspectorId);
    AtomicReference<LoadConditionReport> stored = new AtomicReference<>();
    LoadConditionReportRepository reports =
        proxy(
            LoadConditionReportRepository.class,
            (method, args) -> {
              if (method.equals("save")) {
                LoadConditionReport report = (LoadConditionReport) args[0];
                report.setId(reportId);
                stored.set(report);
                return report;
              }
              if (method.equals("findById")) {
                return Optional.of(stored.get());
              }
              throw new AssertionError("Unexpected report repository call: " + method);
            });
    InspectionService service =
        new InspectionServiceImpl(
            reports,
            findByIdService(LoadService.class, load),
            findByIdService(EmployeeService.class, inspector),
            Mappers.getMapper(InspectionMapper.class));

    var created =
        service.create(
            request(
                loadId,
                inspectorId,
                List.of(
                    new DefectRequest("tire", "Low tread", "major"),
                    new DefectRequest("light", "Broken lens", "minor"))));

    assertThat(created.defects()).hasSize(2);
    assertThat(stored.get().getDefects())
        .allSatisfy(defect -> assertThat(defect.getLoadConditionReport()).isSameAs(stored.get()));

    var updated =
        service.update(
            reportId,
            request(
                loadId,
                inspectorId,
                List.of(new DefectRequest("door", "Latch damaged", "critical"))));

    assertThat(updated.defects()).extracting("partCategory").containsExactly("door");
    assertThat(stored.get().getDefects()).hasSize(1);
  }

  private CreateInspectionRequest request(
      UUID loadId, UUID inspectorId, List<DefectRequest> defects) {
    return new CreateInspectionRequest(
        loadId,
        "pre-trip",
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        OffsetDateTime.now(),
        inspectorId,
        defects);
  }

  /** Stubs the owning feature's service so it resolves the association to {@code entity}. */
  private static <T> T findByIdService(Class<T> type, Object entity) {
    return proxy(
        type,
        (method, args) -> {
          if (method.equals("getEntityById")) {
            return entity;
          }
          throw new AssertionError("Unexpected " + type.getSimpleName() + " call: " + method);
        });
  }

  @SuppressWarnings("unchecked")
  private static <T> T proxy(Class<T> type, RepositoryCall call) {
    return (T)
        Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[] {type},
            (proxy, method, args) -> call.invoke(method.getName(), args));
  }

  @FunctionalInterface
  private interface RepositoryCall {
    Object invoke(String method, Object[] args);
  }
}
