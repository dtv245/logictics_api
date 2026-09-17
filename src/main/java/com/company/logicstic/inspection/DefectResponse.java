package com.company.logicstic.inspection;

import java.util.UUID;

public record DefectResponse(UUID id, String partCategory, String description, String severity) {

  public static DefectResponse from(ConditionDefect defect) {
    return new DefectResponse(
        defect.getId(), defect.getPartCategory(), defect.getDescription(), defect.getSeverity());
  }
}
