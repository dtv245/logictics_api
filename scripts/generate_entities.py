#!/usr/bin/env python3
"""Generate JPA entities from the PostgreSQL DDL in docs/docs/architecture/database-schema.sql."""

from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DDL_FILE = ROOT / "docs/docs/architecture/database-schema.sql"
OUTPUT_DIR = ROOT / "src/main/java/com/company/logicstic/entity"
RELATIONSHIP_REPORT = ROOT / "docs/docs/architecture/entity-relationships.md"

TABLE_TO_CLASS = {
    "ai_dispatch_sessions": "AiDispatchSession",
    "api_keys": "ApiKey",
    "customers": "Customer",
    "eld_provider_configurations": "EldProviderConfiguration",
    "load_board_configurations": "LoadBoardConfiguration",
    "notifications": "Notification",
    "telegram_chats": "TelegramChat",
    "tenant_roles": "TenantRole",
    "terminals": "Terminal",
    "ai_dispatch_decisions": "AiDispatchDecision",
    "containers": "Container",
    "customer_users": "CustomerUser",
    "employees": "Employee",
    "hos_logs": "HosLog",
    "hos_violations": "HosViolation",
    "tenant_role_claims": "TenantRoleClaim",
    "trucks": "Truck",
    "driver_behavior_events": "DriverBehaviorEvent",
    "driver_hos_statuses": "DriverHosStatus",
    "eld_driver_mappings": "EldDriverMapping",
    "eld_vehicle_mappings": "EldVehicleMapping",
    "expenses": "Expense",
    "loads": "Load",
    "maintenance_schedules": "MaintenanceSchedule",
    "posted_trucks": "PostedTruck",
    "tracking_links": "TrackingLink",
    "trips": "Trip",
    "accident_reports": "AccidentReport",
    "accident_third_parties": "AccidentThirdParty",
    "accident_witnesses": "AccidentWitness",
    "conversations": "Conversation",
    "dvir_reports": "DvirReport",
    "invoices": "Invoice",
    "load_board_listings": "LoadBoardListing",
    "load_condition_reports": "LoadConditionReport",
    "load_exceptions": "LoadException",
    "maintenance_records": "MaintenanceRecord",
    "messages": "Message",
    "payment_links": "PaymentLink",
    "payments": "Payment",
    "time_entries": "TimeEntry",
    "trip_stops": "TripStop",
    "condition_defects": "ConditionDefect",
    "conversation_participants": "ConversationParticipant",
    "documents": "Document",
    "driver_licenses": "DriverLicense",
    "dvir_defects": "DvirDefect",
    "invoice_line_items": "InvoiceLineItem",
    "maintenance_parts": "MaintenancePart",
    "message_read_receipts": "MessageReadReceipt",
}

AUDIT_COLUMNS = {"CreatedAt", "CreatedBy", "LastModifiedAt", "LastModifiedBy"}


@dataclass(frozen=True)
class Column:
    name: str
    quoted: bool
    sql_type: str
    nullable: bool
    identity: bool


@dataclass(frozen=True)
class ForeignKey:
    column: str
    target_table: str
    target_column: str


@dataclass(frozen=True)
class DbIndex:
    name: str
    columns: tuple[str, ...]
    unique: bool


@dataclass
class Table:
    name: str
    columns: list[Column]
    primary_keys: set[str]
    foreign_keys: dict[str, ForeignKey]
    indexes: list[DbIndex]


def unquote(identifier: str) -> tuple[str, bool]:
    identifier = identifier.strip()
    if identifier.startswith('"') and identifier.endswith('"'):
        return identifier[1:-1], True
    return identifier, False


def camel(name: str) -> str:
    if "_" not in name:
        return name[:1].lower() + name[1:]
    parts = name.split("_")
    return parts[0].lower() + "".join(part[:1].upper() + part[1:] for part in parts[1:])


def java_identifier(name: str) -> str:
    value = camel(name)
    if value in {"class", "default", "package", "public", "private", "protected"}:
        return value + "Value"
    return value


def annotation_identifier(name: str, quoted: bool = False) -> str:
    if quoted:
        return f'\\"{name}\\"'
    return name


def parse_columns(body: str) -> tuple[list[Column], set[str], dict[str, ForeignKey]]:
    columns: list[Column] = []
    primary_keys: set[str] = set()
    foreign_keys: dict[str, ForeignKey] = {}

    pk_match = re.search(r"PRIMARY KEY\s*\(([^)]+)\)", body, re.IGNORECASE)
    if pk_match:
        primary_keys = {unquote(value)[0] for value in pk_match.group(1).split(",")}

    fk_pattern = re.compile(
        r"FOREIGN KEY\s*\(([^)]+)\)\s*REFERENCES\s+(?:public\.)?(\"[^\"]+\"|\w+)\s*\(([^)]+)\)",
        re.IGNORECASE,
    )
    for match in fk_pattern.finditer(body):
        column = unquote(match.group(1))[0]
        target_table = unquote(match.group(2))[0]
        target_column = unquote(match.group(3))[0]
        foreign_keys[column] = ForeignKey(column, target_table, target_column)

    type_pattern = re.compile(
        r"^(varchar\(\d+\)|numeric(?:\(\s*\d+\s*,\s*\d+\s*\))?|int4|int8|text|uuid|timestamptz|float8|bool|interval)(?=\s|$)",
        re.IGNORECASE,
    )
    for raw_line in body.splitlines():
        line = raw_line.strip().rstrip(",")
        if not line or line.upper().startswith("CONSTRAINT"):
            continue
        match = re.match(r'^("[^"]+"|\w+)\s+(.+)$', line)
        if not match:
            continue
        name, quoted = unquote(match.group(1))
        remainder = match.group(2)
        type_match = type_pattern.match(remainder)
        if not type_match:
            continue
        columns.append(
            Column(
                name=name,
                quoted=quoted,
                sql_type=type_match.group(1).lower().replace(" ", ""),
                nullable="NOT NULL" not in remainder.upper(),
                identity="GENERATED ALWAYS AS IDENTITY" in remainder.upper(),
            )
        )
    return columns, primary_keys, foreign_keys


def parse_ddl(ddl: str) -> dict[str, Table]:
    tables: dict[str, Table] = {}
    table_pattern = re.compile(
        r"CREATE TABLE\s+(?:public\.)?(\"[^\"]+\"|\w+)\s*\((.*?)\n\);",
        re.IGNORECASE | re.DOTALL,
    )
    for match in table_pattern.finditer(ddl):
        table_name = unquote(match.group(1))[0]
        columns, primary_keys, foreign_keys = parse_columns(match.group(2))
        tables[table_name] = Table(table_name, columns, primary_keys, foreign_keys, [])

    index_pattern = re.compile(
        r"CREATE\s+(UNIQUE\s+)?INDEX\s+(\w+)\s+ON\s+public\.(\"[^\"]+\"|\w+)\s+USING\s+btree\s*\(([^)]+)\);",
        re.IGNORECASE,
    )
    for match in index_pattern.finditer(ddl):
        table_name = unquote(match.group(3))[0]
        if table_name not in tables:
            continue
        columns = tuple(unquote(value.strip())[0] for value in match.group(4).split(","))
        tables[table_name].indexes.append(
            DbIndex(match.group(2), columns, match.group(1) is not None)
        )
    return tables


def java_type(column: Column) -> str:
    sql_type = column.sql_type
    if sql_type == "uuid":
        return "UUID"
    if sql_type == "int4":
        return "Integer"
    if sql_type == "int8":
        return "Long"
    if sql_type.startswith("numeric"):
        return "BigDecimal"
    if sql_type == "float8":
        return "Double"
    if sql_type == "bool":
        return "Boolean"
    if sql_type == "timestamptz":
        return "OffsetDateTime"
    if sql_type == "interval":
        return "Duration"
    return "String"


def varchar_length(sql_type: str) -> int | None:
    match = re.fullmatch(r"varchar\((\d+)\)", sql_type)
    return int(match.group(1)) if match else None


def numeric_shape(sql_type: str) -> tuple[int, int] | None:
    match = re.fullmatch(r"numeric\((\d+),(\d+)\)", sql_type)
    return (int(match.group(1)), int(match.group(2))) if match else None


def unique_indexes(table: Table) -> list[DbIndex]:
    return [index for index in table.indexes if index.unique]


def is_single_unique(table: Table, column_name: str) -> bool:
    return any(index.columns == (column_name,) for index in unique_indexes(table))


def table_annotation(table: Table) -> list[str]:
    multi_unique = [index for index in unique_indexes(table) if len(index.columns) > 1]
    regular_indexes = [index for index in table.indexes if not index.unique]
    if not multi_unique and not regular_indexes:
        return [f'@Table(name = "{table.name}", schema = "public")']

    lines = ["@Table(", f'    name = "{table.name}",', '    schema = "public",']
    if multi_unique:
        lines.append("    uniqueConstraints = {")
        for position, index in enumerate(multi_unique):
            suffix = "," if position < len(multi_unique) - 1 else ""
            columns = ", ".join(f'"{column}"' for column in index.columns)
            lines.append(
                f'        @UniqueConstraint(name = "{index.name}", columnNames = {{{columns}}}){suffix}'
            )
        lines.append("    }," if regular_indexes else "    }")
    if regular_indexes:
        lines.append("    indexes = {")
        for position, index in enumerate(regular_indexes):
            suffix = "," if position < len(regular_indexes) - 1 else ""
            column_list = ",".join(index.columns)
            lines.append(
                f'        @Index(name = "{index.name}", columnList = "{column_list}"){suffix}'
            )
        lines.append("    }")
    lines.append(")")
    return lines


def column_annotation(column: Column, table: Table) -> str:
    attributes = [f'name = "{annotation_identifier(column.name, column.quoted)}"']
    if not column.nullable:
        attributes.append("nullable = false")
    if column.name in table.primary_keys:
        attributes.append("updatable = false")
    if column.identity:
        attributes.extend(["insertable = false", "updatable = false"])
    if is_single_unique(table, column.name):
        attributes.append("unique = true")
    length = varchar_length(column.sql_type)
    if length is not None:
        attributes.append(f"length = {length}")
    shape = numeric_shape(column.sql_type)
    if shape is not None:
        attributes.extend([f"precision = {shape[0]}", f"scale = {shape[1]}"])
    if column.sql_type == "text":
        attributes.append('columnDefinition = "text"')
    elif column.sql_type == "interval":
        attributes.append('columnDefinition = "interval"')
    return "@Column(" + ", ".join(dict.fromkeys(attributes)) + ")"


def relationship_field(column: Column, fk: ForeignKey, table: Table) -> list[str]:
    field_name = java_identifier(column.name[:-3] if column.name.endswith("_id") else column.name)
    target_class = TABLE_TO_CLASS[fk.target_table]
    unique = is_single_unique(table, column.name)
    relation = "@OneToOne" if unique else "@ManyToOne"
    relation_attributes = ["fetch = FetchType.LAZY"]
    if not column.nullable:
        relation_attributes.append("optional = false")
    join_attributes = [f'name = "{annotation_identifier(column.name, column.quoted)}"']
    if not column.nullable:
        join_attributes.append("nullable = false")
    if unique:
        join_attributes.append("unique = true")
    return [
        f'{relation}(' + ", ".join(relation_attributes) + ")",
        "@JoinColumn(" + ", ".join(join_attributes) + ")",
        f"private {target_class} {field_name};",
    ]


def basic_field(column: Column, table: Table) -> list[str]:
    lines: list[str] = []
    if column.name in table.primary_keys:
        lines.extend(["@Id", "@GeneratedValue", "@UuidGenerator"])
    if column.name == "created_at":
        lines.append("@CreationTimestamp")
    elif column.name == "updated_at":
        lines.append("@UpdateTimestamp")
    lines.append(column_annotation(column, table))
    lines.append(f"private {java_type(column)} {java_identifier(column.name)};")
    return lines


def render_entity(table: Table) -> str:
    class_name = TABLE_TO_CLASS[table.name]
    uses_audit_base = AUDIT_COLUMNS.issubset({column.name for column in table.columns})
    extends = " extends BaseAuditableEntity" if uses_audit_base else ""
    lines = [
        "package com.company.logicstic.entity;",
        "",
        "import jakarta.persistence.*;",
        "import lombok.AllArgsConstructor;",
        "import lombok.Getter;",
        "import lombok.NoArgsConstructor;",
        "import lombok.Setter;",
        "import org.hibernate.annotations.CreationTimestamp;",
        "import org.hibernate.annotations.UpdateTimestamp;",
        "import org.hibernate.annotations.UuidGenerator;",
        "",
        "import java.math.BigDecimal;",
        "import java.time.Duration;",
        "import java.time.OffsetDateTime;",
        "import java.util.UUID;",
        "",
        "@Entity",
        *table_annotation(table),
        "@Getter",
        "@Setter",
        "@NoArgsConstructor",
        "@AllArgsConstructor",
        f"public class {class_name}{extends} {{",
    ]

    for column in table.columns:
        if uses_audit_base and column.name in AUDIT_COLUMNS:
            continue
        lines.append("")
        field_lines = (
            relationship_field(column, table.foreign_keys[column.name], table)
            if column.name in table.foreign_keys
            else basic_field(column, table)
        )
        lines.extend(f"    {line}" for line in field_lines)
    lines.extend(["}", ""])
    return "\n".join(lines)


def render_base_entity() -> str:
    return '''package com.company.logicstic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@MappedSuperclass
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseAuditableEntity {

    @CreationTimestamp
    @Column(name = "\\\"CreatedAt\\\"", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "\\\"CreatedBy\\\"", length = 50)
    private String createdBy;

    @UpdateTimestamp
    @Column(name = "\\\"LastModifiedAt\\\"")
    private OffsetDateTime lastModifiedAt;

    @Column(name = "\\\"LastModifiedBy\\\"", length = 50)
    private String lastModifiedBy;
}
'''


def render_relationship_report(tables: dict[str, Table]) -> str:
    lines = [
        "# JPA entity relationships",
        "",
        "Tất cả quan hệ dùng `FetchType.LAZY` để tránh tải graph không cần thiết và hạn chế vòng tham chiếu khi serialize.",
        "",
    ]
    for table_name in TABLE_TO_CLASS:
        table = tables[table_name]
        lines.append(f"## {TABLE_TO_CLASS[table_name]}")
        lines.append("")
        if not table.foreign_keys:
            lines.append("- Không có quan hệ foreign key.")
        else:
            column_lookup = {column.name: column for column in table.columns}
            for fk in table.foreign_keys.values():
                relation = "OneToOne" if is_single_unique(table, fk.column) else "ManyToOne"
                field = java_identifier(fk.column[:-3] if fk.column.endswith("_id") else fk.column)
                optional = "bắt buộc" if not column_lookup[fk.column].nullable else "tùy chọn"
                lines.append(
                    f"- `{field}` → `{TABLE_TO_CLASS[fk.target_table]}`: `{relation}`, LAZY, {optional}."
                )
        lines.append("")
    return "\n".join(lines)


def main() -> None:
    tables = parse_ddl(DDL_FILE.read_text(encoding="utf-8"))
    actual_tables = set(tables) - {"__EFMigrationsHistory"}
    expected_tables = set(TABLE_TO_CLASS)
    if actual_tables != expected_tables:
        missing = sorted(expected_tables - actual_tables)
        unexpected = sorted(actual_tables - expected_tables)
        raise RuntimeError(f"Table mismatch. Missing={missing}, unexpected={unexpected}")

    foreign_key_count = sum(len(table.foreign_keys) for table in tables.values())
    if foreign_key_count != 76:
        raise RuntimeError(f"Expected 76 foreign keys, found {foreign_key_count}")

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    (OUTPUT_DIR / "BaseAuditableEntity.java").write_text(render_base_entity(), encoding="utf-8")
    for table_name, class_name in TABLE_TO_CLASS.items():
        (OUTPUT_DIR / f"{class_name}.java").write_text(
            render_entity(tables[table_name]), encoding="utf-8"
        )

    RELATIONSHIP_REPORT.parent.mkdir(parents=True, exist_ok=True)
    RELATIONSHIP_REPORT.write_text(render_relationship_report(tables), encoding="utf-8")
    print(f"Generated {len(TABLE_TO_CLASS)} entities and {foreign_key_count} relationships")


if __name__ == "__main__":
    main()
