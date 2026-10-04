#!/usr/bin/env python3
"""Run the real PostgreSQL regression suite on a new disposable DB or a clone.

No source database is migrated or repaired. Test databases and diagnostic logs
are retained. Uses only Python's standard library, Docker and the Maven wrapper.
"""

import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import xml.etree.ElementTree as ET


PROJECT_ROOT = Path(__file__).resolve().parents[1]
MIN_REPORTED_TESTS = 456
MIN_EXECUTED_TESTS = 455
MIN_POSTGRES_METHODS = 40
POSTGRES_SUITE = "com.company.logicstic.integration.CostLedgerPostgresTest"
# V35 fleet checkpoint. A larger unit suite cannot compensate for a missing
# financial/PostgreSQL suite; preserve each completed domain's live coverage.
POSTGRES_BASELINES = {
    POSTGRES_SUITE: MIN_POSTGRES_METHODS,
    "com.company.logicstic.integration.RatingPolicyPostgresTest": 6,
    "com.company.logicstic.integration.LoadPickupBusinessDatePostgresTest": 7,
    "com.company.logicstic.integration.RatingMileagePostgresTest": 5,
    "com.company.logicstic.integration.RatingPreviewPostgresTest": 3,
    "com.company.logicstic.integration.RatingSnapshotPostgresTest": 6,
    "com.company.logicstic.integration.TaxAssessmentPostgresTest": 4,
    "com.company.logicstic.integration.BillingPrimaryPostgresTest": 23,
    "com.company.logicstic.integration.OptimizationAuditPostgresTest": 11,
    "com.company.logicstic.integration.OptimizationQualifiedInputPostgresTest": 11,
    "com.company.logicstic.integration.OptimizationRunPostgresTest": 11,
    "com.company.logicstic.integration.OptimizationAcceptancePostgresTest": 11,
    "com.company.logicstic.integration.FleetUtilizationPostgresTest": 22,
}
MIN_POSTGRES_INTEGRATION_METHODS = sum(POSTGRES_BASELINES.values())
LEGACY_SKIP = ("com.company.logicstic.LogicsticApplicationTests", "contextLoads")


class VerificationFailure(Exception):
    """The regression or migration gate did not pass."""


def verify_reports(report_dir):
    files = sorted(Path(report_dir).glob("TEST-*.xml"))
    if not files:
        raise VerificationFailure("No fresh Surefire XML reports found")
    totals = {"reported": 0, "failures": 0, "errors": 0, "skipped": 0}
    skips = []
    postgres_methods = 0
    postgres_skips = 0
    postgres_suites = {}
    seen_suites = set()
    for file in files:
        suite = ET.parse(file).getroot()
        if suite.tag != "testsuite":
            raise VerificationFailure(f"Unexpected Surefire report format: {file.name}")
        counts = {key: int(suite.get(attr, "0")) for key, attr in (
            ("reported", "tests"), ("failures", "failures"),
            ("errors", "errors"), ("skipped", "skipped"))}
        name = suite.get("name")
        if name in seen_suites:
            raise VerificationFailure(f"Duplicate Surefire suite: {name}")
        seen_suites.add(name)
        for key, count in counts.items():
            totals[key] += count
        for case in suite.findall("testcase"):
            if case.find("skipped") is not None:
                skips.append((case.get("classname", suite.get("name")), case.get("name")))
        if name in POSTGRES_BASELINES or (name or "").endswith("PostgresTest"):
            postgres_methods += counts["reported"] - counts["skipped"]
            postgres_skips += counts["skipped"]
            postgres_suites[name] = counts["reported"] - counts["skipped"]
    if totals["failures"] or totals["errors"]:
        raise VerificationFailure(f"Test failures/errors: {totals}")
    if postgres_skips or postgres_methods < MIN_POSTGRES_INTEGRATION_METHODS:
        raise VerificationFailure(
            f"PostgreSQL baseline did not execute: {postgres_methods} methods, {postgres_skips} skipped")
    for name, minimum in POSTGRES_BASELINES.items():
        executed = postgres_suites.get(name, 0)
        if executed < minimum:
            raise VerificationFailure(
                f"PostgreSQL domain baseline shrank: {name}: {executed} executed, minimum {minimum}")
    if totals["skipped"] != 1 or skips != [LEGACY_SKIP]:
        raise VerificationFailure(f"Legacy skip gate changed: {skips}")
    executed = totals["reported"] - totals["skipped"]
    if totals["reported"] < MIN_REPORTED_TESTS or executed < MIN_EXECUTED_TESTS:
        raise VerificationFailure(f"Regression baseline shrank: {totals['reported']} reported tests")
    return {**totals, "executed": executed,
            "postgres_baseline_methods": postgres_suites[POSTGRES_SUITE],
            "postgres_integration_methods": postgres_methods,
            "postgres_suites": postgres_suites}


def migration_versions(project_root):
    versions = []
    for path in (project_root / "src/main/resources/db/migration/tenant").glob("V*__*.sql"):
        match = re.fullmatch(r"V(\d+)__.+\.sql", path.name)
        if not match:
            raise VerificationFailure(f"Unsupported migration filename: {path.name}")
        versions.append(int(match.group(1)))
    if not versions or len(versions) != len(set(versions)):
        raise VerificationFailure("Missing or duplicate active tenant migration versions")
    return sorted(versions)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--upgrade-from", help="Clone a verified local codex_* DB; never migrate the source")
    parser.add_argument("--container", default="logistics-postgres")
    parser.add_argument("--host", default="localhost")
    parser.add_argument("--port", type=int, default=5433, help="Published PostgreSQL port")
    args = parser.parse_args()
    if args.upgrade_from and not re.fullmatch(r"codex_[a-z0-9_]+", args.upgrade_from):
        parser.error("--upgrade-from must name an explicitly disposable local codex_* database")

    def capture(command):
        return subprocess.check_output(command, text=True).strip()

    try:
        expected_versions = migration_versions(PROJECT_ROOT)
        container = json.loads(capture(["docker", "inspect", args.container]))[0]
        settings = dict(value.split("=", 1) for value in container["Config"]["Env"] if "=" in value)
        db_user = settings.get("POSTGRES_USER", "postgres")
        db_password = settings.get("POSTGRES_PASSWORD")
        if not db_password:
            raise VerificationFailure("Container POSTGRES_PASSWORD is required; its value is never printed")

        def versions(database):
            result = capture(["docker", "exec", args.container, "psql", "-U", db_user,
                              "-d", database, "-v", "ON_ERROR_STOP=1", "-Atc",
                              "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank"])
            return [int(version) for version in result.splitlines()]

        source_versions = versions(args.upgrade_from) if args.upgrade_from else []
        if source_versions and source_versions != expected_versions[:len(source_versions)]:
            raise VerificationFailure("Source migration history is not a prefix of current tenant migrations")
        if args.upgrade_from and not source_versions:
            raise VerificationFailure("Upgrade source has no verified Flyway version history")

        test_database = "codex_regression_" + datetime.now(timezone.utc).strftime("%Y%m%d%H%M%S%f")
        command = ["docker", "exec", args.container, "createdb", "-U", db_user]
        if args.upgrade_from:
            command.append("--template=" + args.upgrade_from)
        subprocess.run([*command, test_database], check=True)
        diagnostics = Path(tempfile.mkdtemp(prefix="logisticsx-regression-"))
        run_log = diagnostics / "maven.log"
        print(f"Disposable database: {test_database}", flush=True)
        print(f"Source versions: {source_versions or 'clean bootstrap'}; target: V{expected_versions[-1]}", flush=True)
        print(f"Diagnostics: {diagnostics}", flush=True)
        environment = os.environ.copy()
        environment.update(TASK_DB_URL=f"jdbc:postgresql://{args.host}:{args.port}/{test_database}",
                           TASK_DB_USER=db_user, TASK_DB_PASSWORD=db_password,
                           SPRING_FLYWAY_VALIDATE_ON_MIGRATE="true",
                           # Cached Spring test contexts must not exhaust PostgreSQL's connection limit.
                           # Four connections still execute concurrent transaction tests; production is unchanged.
                           SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE="4",
                           SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE="0")
        with run_log.open("w") as log:
            run = subprocess.run([str(PROJECT_ROOT / "mvnw"), "-q", "clean", "verify"],
                                 cwd=PROJECT_ROOT, env=environment, stdout=log, stderr=subprocess.STDOUT)
        if run.returncode:
            raise VerificationFailure(f"Maven exited {run.returncode}; inspect {run_log}")
        report = verify_reports(PROJECT_ROOT / "target/surefire-reports")
        if not re.search(r"Successfully validated \d+ migrations", run_log.read_text()):
            raise VerificationFailure(f"Flyway validation evidence missing; inspect {run_log}")
        actual_versions = versions(test_database)
        if actual_versions != expected_versions:
            raise VerificationFailure(f"Target Flyway history differs: {actual_versions}")
        summary = {"status": "PASS", "database": test_database, "source_database": args.upgrade_from,
                   "source_versions": source_versions, "validated_versions": actual_versions, **report}
        (diagnostics / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
        print(json.dumps(summary, indent=2), flush=True)
        return 0
    except (VerificationFailure, subprocess.CalledProcessError, ET.ParseError, OSError, ValueError) as error:
        print(f"FAIL: {error}", flush=True)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
