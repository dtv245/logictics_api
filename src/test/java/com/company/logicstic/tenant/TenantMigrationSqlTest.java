package com.company.logicstic.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class TenantMigrationSqlTest {

  private static final Pattern CREATE_TABLE =
      Pattern.compile(
          "CREATE TABLE\\s+(\\w+)\\s*\\((.*?)\\n\\);", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern RENAME =
      Pattern.compile(
          "ALTER TABLE\\s+(\\w+)\\s+RENAME COLUMN\\s+\"([^\"]+)\"\\s+TO\\s+(\\w+);",
          Pattern.CASE_INSENSITIVE);

  @Test
  void everyV2RenameReferencesAColumnCreatedByV1() throws IOException {
    String baseline = resource("/db/migration/tenant/V1__baseline_business_schema.sql");
    String migration = resource("/db/migration/tenant/V2__rename_audit_columns_to_snake_case.sql");

    var matcher = RENAME.matcher(migration);
    int checked = 0;
    while (matcher.find()) {
      String table = matcher.group(1);
      String sourceColumn = matcher.group(2);
      String tableDefinition = tableDefinition(baseline, table);
      assertThat(tableDefinition)
          .as("V1 table %s must contain source column %s", table, sourceColumn)
          .contains("\"" + sourceColumn + "\"");
      checked++;
    }

    assertThat(checked).isGreaterThan(0);
  }

  @Test
  void everyLegacyAuditColumnCreatedByV1IsRenamedByV2() throws IOException {
    String baseline = resource("/db/migration/tenant/V1__baseline_business_schema.sql");
    String migration = resource("/db/migration/tenant/V2__rename_audit_columns_to_snake_case.sql");
    Pattern legacyAuditColumn =
        Pattern.compile("\\\"(CreatedAt|CreatedBy|LastModifiedAt|LastModifiedBy)\\\"");

    var tableMatcher = CREATE_TABLE.matcher(baseline);
    int checked = 0;
    while (tableMatcher.find()) {
      String table = tableMatcher.group(1);
      var columnMatcher = legacyAuditColumn.matcher(tableMatcher.group(2));
      while (columnMatcher.find()) {
        String sourceColumn = columnMatcher.group(1);
        assertThat(migration)
            .as("V2 must rename legacy column %s.%s", table, sourceColumn)
            .contains(
                "ALTER TABLE "
                    + table
                    + " RENAME COLUMN \""
                    + sourceColumn
                    + "\" TO "
                    + snakeCase(sourceColumn)
                    + ";");
        checked++;
      }
    }

    assertThat(checked).isGreaterThan(0);
  }

  private String resource(String path) throws IOException {
    try (var stream = getClass().getResourceAsStream(path)) {
      assertThat(stream).as("resource %s", path).isNotNull();
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private String tableDefinition(String baseline, String table) {
    Pattern tablePattern =
        Pattern.compile(
            "CREATE TABLE\\s+" + Pattern.quote(table) + "\\s*\\((.*?)\\n\\);",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    var matcher = tablePattern.matcher(baseline);
    assertThat(matcher.find()).as("V1 must create table %s", table).isTrue();
    return matcher.group(1);
  }

  private String snakeCase(String sourceColumn) {
    return switch (sourceColumn) {
      case "CreatedAt" -> "created_at";
      case "CreatedBy" -> "created_by";
      case "LastModifiedAt" -> "last_modified_at";
      case "LastModifiedBy" -> "last_modified_by";
      default -> throw new IllegalArgumentException("Unexpected audit column: " + sourceColumn);
    };
  }
}
