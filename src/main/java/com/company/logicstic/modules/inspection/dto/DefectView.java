package com.company.logicstic.modules.inspection.dto;

import com.company.logicstic.modules.load.entity.ConditionDefect;
import java.util.UUID;

public record DefectView(UUID id, String partCategory, String description, String severity) {

  public static DefectView from(ConditionDefect defect) {
    return new DefectView(
        defect.getId(), defect.getPartCategory(), defect.getDescription(), defect.getSeverity());
  }
}
