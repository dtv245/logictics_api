package com.company.logicstic.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import jakarta.persistence.Column;
import java.lang.reflect.Field;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies audit columns on {@link BaseAuditableEntity} use unquoted snake_case names aligned with
 * Flyway V2 migration.
 */
@DisplayName("BaseAuditableEntity column mapping")
class BaseAuditableEntityMappingTest {

  @Test
  @DisplayName("createdAt maps to created_at without quotes")
  void createdAt_snakeCase() throws NoSuchFieldException {
    assertColumnName("createdAt", "created_at");
  }

  @Test
  @DisplayName("createdBy maps to created_by without quotes")
  void createdBy_snakeCase() throws NoSuchFieldException {
    assertColumnName("createdBy", "created_by");
  }

  @Test
  @DisplayName("lastModifiedAt maps to last_modified_at without quotes")
  void lastModifiedAt_snakeCase() throws NoSuchFieldException {
    assertColumnName("lastModifiedAt", "last_modified_at");
  }

  @Test
  @DisplayName("lastModifiedBy maps to last_modified_by without quotes")
  void lastModifiedBy_snakeCase() throws NoSuchFieldException {
    assertColumnName("lastModifiedBy", "last_modified_by");
  }

  private void assertColumnName(String fieldName, String expectedColumn)
      throws NoSuchFieldException {
    Field field = BaseAuditableEntity.class.getDeclaredField(fieldName);
    Column column = field.getAnnotation(Column.class);
    assertNotNull(column, fieldName + " must have @Column");
    assertEquals(expectedColumn, column.name());
    assertFalse(column.name().contains("\""), fieldName + " must not use quoted identifiers");
  }
}
