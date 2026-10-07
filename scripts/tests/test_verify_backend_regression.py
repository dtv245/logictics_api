"""Ensure Maven success cannot hide missing PostgreSQL execution or new skips."""

import importlib.util
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET


spec = importlib.util.spec_from_file_location(
    "verify_backend_regression", Path(__file__).resolve().parents[1] / "verify_backend_regression.py")
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


class ReportGateTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.reports = Path(self.directory.name)

    def report(self, name, count, skipped=0, failures=0, errors=0, skipped_name="contextLoads"):
        suite = ET.Element("testsuite", name=name, tests=str(count), skipped=str(skipped),
                           failures=str(failures), errors=str(errors))
        for index in range(skipped):
            case = ET.SubElement(suite, "testcase", classname=name, name=skipped_name)
            ET.SubElement(case, "skipped", message="fixture")
        ET.ElementTree(suite).write(self.reports / ("TEST-" + name + ".xml"))

    def baseline(self, units=334, pg=40, pg_skips=0):
        self.report("fixture.UnitTests", units)
        self.report(runner.POSTGRES_SUITE, pg, skipped=pg_skips, skipped_name="postgresMethod")
        for name, count in runner.POSTGRES_BASELINES.items():
            if name != runner.POSTGRES_SUITE:
                self.report(name, count)
        self.report("com.company.logicstic.LogicsticApplicationTests", 1)

    def test_current_baseline_passes_with_precise_executed_count(self):
        self.baseline()
        result = runner.verify_reports(self.reports)
        self.assertEqual(runner.MIN_REPORTED_TESTS, result["reported"])
        self.assertEqual(runner.MIN_EXECUTED_TESTS, result["executed"])
        self.assertEqual(40, result["postgres_baseline_methods"])
        self.assertEqual(runner.MIN_POSTGRES_INTEGRATION_METHODS, result["postgres_integration_methods"])
        self.assertEqual(runner.POSTGRES_BASELINES, result["postgres_suites"])

    def test_new_tests_do_not_require_baseline_count_to_remain_fixed(self):
        self.baseline(units=336, pg=41)
        self.assertEqual(runner.MIN_REPORTED_TESTS+3, runner.verify_reports(self.reports)["reported"])

    def test_green_maven_with_postgres_skips_is_rejected(self):
        self.baseline(pg_skips=40)
        with self.assertRaisesRegex(runner.VerificationFailure, "PostgreSQL"):
            runner.verify_reports(self.reports)

    def test_smaller_baseline_is_rejected(self):
        self.baseline(units=333)
        with self.assertRaisesRegex(runner.VerificationFailure, "baseline shrank"):
            runner.verify_reports(self.reports)

    def test_an_additional_skipped_test_is_rejected(self):
        self.baseline()
        self.report("fixture.OtherTest", 1, skipped=1)
        with self.assertRaisesRegex(runner.VerificationFailure, "skip gate"):
            runner.verify_reports(self.reports)

    def test_failures_and_errors_are_not_hidden_by_counts(self):
        for failures, errors in ((1, 0), (0, 1)):
            with self.subTest(failures=failures, errors=errors):
                self.baseline()
                self.report("fixture.FailingTest", 1, failures=failures, errors=errors)
                with self.assertRaisesRegex(runner.VerificationFailure, "failures/errors"):
                    runner.verify_reports(self.reports)

    def test_missing_reports_cannot_be_a_success(self):
        with self.assertRaisesRegex(runner.VerificationFailure, "No fresh"):
            runner.verify_reports(self.reports)

    def test_v20_counts_no_longer_satisfy_current_checkpoint(self):
        self.report("fixture.UnitTests", 151)
        self.report(runner.POSTGRES_SUITE, 40)
        self.report("com.company.logicstic.LogicsticApplicationTests", 1, skipped=1)
        with self.assertRaisesRegex(runner.VerificationFailure, "PostgreSQL baseline"):
            runner.verify_reports(self.reports)

    def test_more_units_cannot_replace_postgres_domain_coverage(self):
        self.baseline(units=500)
        self.report("com.company.logicstic.integration.BillingPrimaryPostgresTest", 22)
        with self.assertRaisesRegex(runner.VerificationFailure, "PostgreSQL baseline"):
            runner.verify_reports(self.reports)

    def test_more_postgres_methods_cannot_replace_missing_domain_suite(self):
        self.baseline(pg=100)
        self.report("com.company.logicstic.integration.RatingSnapshotPostgresTest", 0)
        with self.assertRaisesRegex(runner.VerificationFailure, "PostgreSQL domain baseline"):
            runner.verify_reports(self.reports)

    def test_future_postgres_suites_increase_coverage_without_changing_baseline(self):
        self.baseline()
        self.report("com.company.logicstic.integration.OptimizationPostgresTest", 2)
        result = runner.verify_reports(self.reports)
        self.assertEqual(runner.MIN_POSTGRES_INTEGRATION_METHODS+2, result["postgres_integration_methods"])
        self.assertEqual(runner.MIN_REPORTED_TESTS+2, result["reported"])

    def test_future_postgres_skip_is_rejected(self):
        self.baseline()
        self.report("com.company.logicstic.integration.OptimizationPostgresTest", 2, skipped=1)
        with self.assertRaisesRegex(runner.VerificationFailure, "PostgreSQL baseline"):
            runner.verify_reports(self.reports)

    def test_more_tests_cannot_replace_optimization_audit_coverage(self):
        self.baseline(units=500, pg=41)
        self.report("com.company.logicstic.integration.OptimizationAuditPostgresTest", 10)
        with self.assertRaisesRegex(runner.VerificationFailure, "PostgreSQL domain baseline"):
            runner.verify_reports(self.reports)

    def test_duplicate_suite_cannot_inflate_coverage(self):
        self.baseline()
        report = self.reports / ("TEST-" + runner.POSTGRES_SUITE + ".xml")
        (self.reports / "TEST-duplicate.xml").write_bytes(report.read_bytes())
        with self.assertRaisesRegex(runner.VerificationFailure, "Duplicate Surefire suite"):
            runner.verify_reports(self.reports)

    def test_more_tests_cannot_replace_qualified_input_source_coverage(self):
        self.baseline(units=500, pg=41)
        self.report("com.company.logicstic.integration.OptimizationQualifiedInputPostgresTest", 10)
        with self.assertRaisesRegex(runner.VerificationFailure, "PostgreSQL domain baseline"):
            runner.verify_reports(self.reports)

    def test_more_units_cannot_replace_atomic_acceptance_postgres_coverage(self):
        self.baseline(units=500)
        self.report("com.company.logicstic.integration.OptimizationAcceptancePostgresTest", 10)
        with self.assertRaisesRegex(runner.VerificationFailure, "PostgreSQL baseline"):
            runner.verify_reports(self.reports)

    def test_more_units_cannot_replace_fleet_history_postgres_coverage(self):
        self.baseline(units=500)
        self.report("com.company.logicstic.integration.FleetUtilizationPostgresTest", 21)
        with self.assertRaisesRegex(runner.VerificationFailure, "PostgreSQL baseline"):
            runner.verify_reports(self.reports)


if __name__ == "__main__":
    unittest.main()
