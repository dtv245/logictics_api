#!/usr/bin/env python3
"""Seed synthetic development data up to 1,000 rows per public business table.

Existing rows are preserved. Schema history tables are excluded, and known
singleton configuration tables are kept at one row. Runs against a named local
PostgreSQL database through Docker; all generated data is synthetic.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import sys
import uuid
from datetime import date, datetime, timedelta, timezone

TARGET_COUNT = 1000
SINGLETON_TARGETS = {"tenant_payroll_settings": 1}
EXCLUDED = {"flyway_schema_history", "__EFMigrationsHistory"}
# These records are derived from audited application workflows. Directly
# fabricating them would create invalid financial, dispatch, or compliance data.
WORKFLOW_REQUIRED = {
    "accepted_rating_snapshots", "contract_load_rating_mileage",
    "customer_rate_rule_versions", "driver_pay_policies",
    "fleet_mileage_attributions", "fleet_policy_versions",
    "invoice_charge_claims", "invoice_line_items",
    "invoice_rebill_credit_evidence", "invoice_tax_assessments", "invoices",
    "optimization_accept_commands", "optimization_acceptances",
    "optimization_assignments", "optimization_policy_versions",
    "optimization_qualified_forecast_costs", "optimization_qualified_inputs",
    "optimization_runs", "payment_command_events", "payments",
    "payroll_payment_events", "payroll_payments",
    "payroll_run_item_settlements", "payroll_run_items", "payroll_runs",
    "payroll_supplements", "payslips", "settlement_billing_adjustments",
    "settlement_lines", "settlement_revenue_commands", "settlements",
    "vehicle_status_events",
}
NAMESPACE = uuid.UUID("6ba7b810-9dad-11d1-80b4-00c04fd430c8")


def qident(s: str) -> str:
    return '"' + s.replace('"', '""') + '"'


def sql_val(v):
    if v is None:
        return "NULL"
    if isinstance(v, bool):
        return "TRUE" if v else "FALSE"
    if isinstance(v, (int, float)):
        return str(v)
    if isinstance(v, (dict, list)):
        v = json.dumps(v, ensure_ascii=False, separators=(",", ":"))
        return "'" + v.replace("'", "''") + "'::jsonb"
    return "'" + str(v).replace("'", "''") + "'"


class Seeder:
    def __init__(self, container: str, database: str, user: str):
        self.container = container
        self.database = database
        self.user = user
        self.parent_rows = {}
        self.failures = []

    def psql(self, sql: str, check=True) -> str:
        cmd = ["docker", "exec", "-i", self.container, "psql", "-X", "-v", "ON_ERROR_STOP=1",
               "-U", self.user, "-d", self.database, "-A", "-t"]
        p = subprocess.run(cmd, input=sql.encode(), stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        if p.returncode and check:
            raise RuntimeError(p.stderr.decode(errors="replace").strip())
        if p.returncode:
            raise RuntimeError(p.stderr.decode(errors="replace").strip())
        return p.stdout.decode().strip()

    def json_query(self, sql: str):
        out = self.psql(f"SELECT COALESCE(json_agg(row_to_json(q)), '[]'::json) FROM ({sql}) q;")
        return json.loads(out or "[]")

    def load_schema(self):
        tables = self.json_query("""
          SELECT table_name FROM information_schema.tables
          WHERE table_schema='public' AND table_type='BASE TABLE'
          ORDER BY table_name
        """)
        names = [x["table_name"] for x in tables if x["table_name"] not in EXCLUDED]
        cols = self.json_query("""
          SELECT table_name,column_name,ordinal_position,data_type,udt_name,is_nullable,
                 column_default,is_identity,is_generated,character_maximum_length,numeric_precision
          FROM information_schema.columns WHERE table_schema='public'
          ORDER BY table_name,ordinal_position
        """)
        fks = self.json_query("""
          SELECT child.relname AS child_table, parent.relname AS parent_table,
                 array_agg(ca.attname ORDER BY k.ord) AS child_cols,
                 array_agg(pa.attname ORDER BY k.ord) AS parent_cols
          FROM pg_constraint c
          JOIN pg_class child ON child.oid=c.conrelid
          JOIN pg_namespace ns ON ns.oid=child.relnamespace
          JOIN pg_class parent ON parent.oid=c.confrelid
          JOIN LATERAL unnest(c.conkey,c.confkey) WITH ORDINALITY k(child_att,parent_att,ord) ON true
          JOIN pg_attribute ca ON ca.attrelid=child.oid AND ca.attnum=k.child_att
          JOIN pg_attribute pa ON pa.attrelid=parent.oid AND pa.attnum=k.parent_att
          WHERE c.contype='f' AND ns.nspname='public'
          GROUP BY child.relname,parent.relname,c.oid
          ORDER BY child.relname,parent.relname
        """)
        checks = self.json_query("""
          SELECT t.relname AS table_name, pg_get_constraintdef(c.oid) AS definition
          FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid
          JOIN pg_namespace n ON n.oid=t.relnamespace
          WHERE c.contype='c' AND n.nspname='public'
        """)
        enums = self.json_query("""
          SELECT t.typname AS type_name,array_agg(e.enumlabel ORDER BY e.enumsortorder) AS labels
          FROM pg_type t JOIN pg_enum e ON e.enumtypid=t.oid
          JOIN pg_namespace n ON n.oid=t.typnamespace WHERE n.nspname='public'
          GROUP BY t.typname
        """)
        self.cols = {t: [] for t in names}
        for c in cols:
            if c["table_name"] in self.cols:
                self.cols[c["table_name"]].append(c)
        self.fks = [x for x in fks if x["child_table"] in self.cols and x["parent_table"] in self.cols]
        self.checks = {t: [] for t in names}
        for c in checks:
            if c["table_name"] in self.checks:
                self.checks[c["table_name"]].append(c["definition"])
        self.enums = {x["type_name"]: x["labels"] for x in enums}
        self.names = names

    def row_count(self, table):
        return int(self.psql(f"SELECT count(*) FROM public.{qident(table)}"))

    def order_tables(self):
        deps = {t: set() for t in self.names}
        for fk in self.fks:
            if fk["parent_table"] != fk["child_table"]:
                deps[fk["child_table"]].add(fk["parent_table"])
        result, remaining = [], set(self.names)
        while remaining:
            ready = sorted(t for t in remaining if not (deps[t] & remaining))
            if not ready:  # cycles: nullable FK edges will be left empty where needed
                ready = sorted(remaining)
            result.extend(ready)
            remaining.difference_update(ready)
        return result

    def load_parent_rows(self, fk):
        key = (fk["parent_table"], tuple(fk["parent_cols"]))
        if key not in self.parent_rows:
            cols = ",".join(qident(c) for c in fk["parent_cols"])
            sql = f"SELECT COALESCE(json_agg(json_build_array({cols})), '[]'::json) FROM public.{qident(fk['parent_table'])}"
            self.parent_rows[key] = json.loads(self.psql(sql) or "[]")
        return self.parent_rows[key]

    def check_candidates(self, table, column):
        candidates = []
        for definition in self.checks.get(table, []):
            if not re.search(rf'(?i)(?:"?{re.escape(column)}"?\s*\)?(?:::\w+)?\s*(?:=|<>|IN|BETWEEN|ANY|LIKE)|"?{re.escape(column)}"?\)::text\s*(?:=|<>|IN|BETWEEN|ANY|LIKE))', definition):
                continue
            if not re.search(r"(?i)\bIN\b|\bANY\s*\(|\bBETWEEN\b", definition):
                continue
            candidates.extend(re.findall(r"'((?:''|[^'])*)'", definition))
        return list(dict.fromkeys(x.replace("''", "'") for x in candidates))

    def value_for(self, table, col, idx, fk_map):
        name, typ, udt = col["column_name"], col["data_type"], col["udt_name"]
        if table == "loads" and name == "pickup_business_date_change_id":
            return None  # business-date history must be chained after Load creation
        if name in fk_map:
            fk, member = fk_map[name]
            # A composite FK with any nullable member is inactive when that
            # member is NULL. Generate required non-null key fields normally
            # (notably loads.id), and leave the nullable audit/reference NULL.
            if len(fk["child_cols"]) > 1:
                nullability = {x["column_name"]: x["is_nullable"] for x in self.cols[table]}
                if any(nullability.get(x) == "YES" for x in fk["child_cols"]):
                    if col["is_nullable"] == "YES":
                        return None
                    # Fall through to generate this required field independently.
                else:
                    rows = self.load_parent_rows(fk)
                    if rows:
                        return rows[idx % len(rows)][member]
                    if col["is_nullable"] == "YES":
                        return None
                    raise ValueError(f"no referenced rows for {table}.{name} -> {fk['parent_table']}")
            else:
                rows = self.load_parent_rows(fk)
                if rows:
                    return rows[idx % len(rows)][member]
                if col["is_nullable"] == "YES":
                    return None
                raise ValueError(f"no referenced rows for {table}.{name} -> {fk['parent_table']}")
        token = f"seed-{table}-{name}-{idx:05d}"
        seed_uuid = str(uuid.uuid5(NAMESPACE, token))
        lname = name.lower()
        if lname.endswith("hash") or lname.endswith("_hash"):
            return hashlib.sha256(token.encode()).hexdigest()
        if lname == "expiry_date":
            if table == "employment_contracts" and idx % 3 == 2:
                return None  # INDEFINITE contracts have no expiry date
            return date.today() + timedelta(days=365 + idx % 365)
        if col["is_nullable"] == "YES":
            return None
        if typ == "USER-DEFINED" and udt in self.enums:
            labels = self.enums[udt]
            return labels[idx % len(labels)]
        if typ in ("uuid",):
            return seed_uuid
        if typ in ("character varying", "character", "text"):
            allowed = self.check_candidates(table, name)
            if allowed:
                return allowed[idx % len(allowed)]
            if "email" in lname:
                value = f"seed+{table}-{name}-{idx:05d}@example.test"
            elif "phone" in lname or lname.endswith("_tel"):
                value = f"+1555{idx:07d}"
            elif "url" in lname or "uri" in lname:
                value = f"https://example.test/{table}/{idx}"
            elif lname in ("status", "state"):
                value = "active" if table == "driver_licenses" else "ACTIVE"
            elif lname.startswith("is_") or lname.endswith("_flag"):
                value = "true"
            elif lname in ("country", "country_code"):
                value = "US"
            elif lname.endswith("_currency") or lname == "currency":
                value = "USD"
            elif lname.endswith("_code") or lname == "code":
                value = f"{table[:6].upper()}{idx:05d}"
            else:
                value = f"DEMO {table} {name} {idx:05d}"
            limit = col["character_maximum_length"]
            if limit:
                limit = int(limit)
                if len(value) > limit:
                    suffix = f"{idx:05d}"
                    value = (value[:max(0, limit-len(suffix))] + suffix)[-limit:]
            return value
        if typ == "boolean":
            return idx % 2 == 0
        if typ == "date":
            if any(x in lname for x in ("birth", "dob")):
                return date(1970, 1, 1) + timedelta(days=idx * 7)
            if any(x in lname for x in ("expir", "due")):
                return date.today() + timedelta(days=30 + idx % 365)
            return date.today() - timedelta(days=idx % 365)
        if "timestamp" in typ:
            now = datetime.now(timezone.utc)
            return (now - timedelta(minutes=idx)).isoformat()
        if typ in ("smallint", "integer", "bigint"):
            if "remaining_points" in lname:
                return idx % 13
            if "year" in lname:
                return 2020 + idx % 10
            if any(x in lname for x in ("count", "quantity", "units")):
                return 1 + idx % 20
            return 10000 + idx
        if typ in ("numeric", "decimal", "real", "double precision"):
            if "latitude" in lname:
                return 37.5 + (idx % 100) / 1000
            if "longitude" in lname:
                return -122.4 + (idx % 100) / 1000
            if "rate" in lname or "percent" in lname or "tax" in lname:
                return 5 + idx % 20
            if any(x in lname for x in ("amount", "cost", "price", "pay", "revenue", "total", "balance")):
                return 100 + (idx % 9000) / 100
            return 1 + idx % 1000
        if typ == "jsonb" or typ == "json":
            return {"fixture": True, "table": table, "row": idx}
        if typ in ("ARRAY",):
            return []
        if typ == "bytea":
            return "\\x"
        if typ == "time without time zone":
            return "08:00:00" if "open" in lname or "start" in lname else "17:00:00"
        if typ == "interval":
            return "08:00:00" if "start" in lname else "16:00:00"
        if typ == "inet":
            return f"192.0.2.{1 + idx % 254}"
        if typ in ("USER-DEFINED",):
            return "DEMO"
        return None

    def seed_table(self, table):
        target = SINGLETON_TARGETS.get(table, TARGET_COUNT)
        current = self.row_count(table)
        if current >= target:
            print(f"{table}: {current}/{target} already present")
            return
        if table in WORKFLOW_REQUIRED:
            print(f"{table}: {current}/{target}; requires valid application workflow, skipped")
            return
        needed = target - current
        cols = self.cols[table]
        writable = [c for c in cols if c["is_identity"] == "NO" and c["is_generated"] == "NEVER" and not c["column_default"]]
        fk_map = {}
        for fk in self.fks:
            if fk["child_table"] == table:
                for i, cname in enumerate(fk["child_cols"]):
                    fk_map[cname] = (fk, i)
        rows = []
        for idx in range(current, current + needed):
            vals = []
            for c in writable:
                try:
                    v = self.value_for(table, c, idx, fk_map)
                except ValueError as e:
                    self.failures.append((table, str(e)))
                    print(f"{table}: SKIP ({e})")
                    return
                if v is None and c["is_nullable"] == "NO":
                    self.failures.append((table, f"required column {c['column_name']} has no generated value"))
                    print(f"{table}: SKIP (cannot populate required {c['column_name']})")
                    return
                vals.append(v)
            rows.append(vals)
        colsql = ",".join(qident(c["column_name"]) for c in writable)
        chunks = [rows[i:i+200] for i in range(0, len(rows), 200)]
        try:
            for chunk in chunks:
                val_sql = ",\n".join("(" + ",".join(sql_val(x) for x in row) + ")" for row in chunk)
                statement = f"INSERT INTO public.{qident(table)} ({colsql}) VALUES {val_sql} ON CONFLICT DO NOTHING;"
                self.psql("BEGIN;\n" + statement + "\nCOMMIT;")
        except Exception as e:
            self.failures.append((table, str(e)))
            print(f"{table}: ERROR {str(e).splitlines()[-1][:300]}")
            return
        after = self.row_count(table)
        print(f"{table}: {after}/{target} (+{after-current})")
        if after < target:
            self.failures.append((table, f"ended at {after}/{target}"))

    def run(self, only=None):
        self.load_schema()
        print(f"Target database: {self.database}; business tables: {len(self.names)}")
        for table in self.order_tables():
            if only and table not in only:
                continue
            self.seed_table(table)
        print("\nFinal row counts:")
        under = []
        for table in (only if only else self.names):
            target = SINGLETON_TARGETS.get(table, TARGET_COUNT)
            count = self.row_count(table)
            print(f"{table}: {count}")
            if count < target:
                under.append((table, count, target))
        if under:
            print("\nTables below target:")
            for t, c, n in under:
                print(f"{t}: {c}/{n}")
        if self.failures:
            print(f"\nFailed or incomplete tables: {len(self.failures)}")
            for t, msg in self.failures:
                print(f"{t}: {msg}")
        if under:
            return 2
        return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--container", default="logistics-postgres")
    parser.add_argument("--database", default="us_logisticsx")
    parser.add_argument("--user", default="postgres")
    parser.add_argument("--only", nargs="+", help="Seed only these table names")
    args = parser.parse_args()
    try:
        code = Seeder(args.container, args.database, args.user).run(set(args.only) if args.only else None)
    except Exception as e:
        print(f"Seeder failed: {e}", file=sys.stderr)
        return 1
    return code


if __name__ == "__main__":
    sys.exit(main())
