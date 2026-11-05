import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch

path = Path(__file__).resolve().parents[1] / "lark_base_schema.py"
spec = importlib.util.spec_from_file_location("lark_base_schema", path)
schema_tool = importlib.util.module_from_spec(spec)
spec.loader.exec_module(schema_tool)

class LarkBaseSchemaTest(unittest.TestCase):
    def config(self):
        data = {"LARK_BASE_ENVIRONMENT": "sandbox", "LARK_APP_ID": "fixture-app",
                "LARK_APP_SECRET": "fixture-credential", "LARK_BASE_APP_TOKEN": "base-fixture",
                "LARK_BASE_LOADS_TABLE_ID": "loads-fixture", "LARK_BASE_DRIVERS_TABLE_ID": "drivers-fixture"}
        for domain in schema_tool.catalog().values():
            for field in domain["fields"]:
                data[field["fieldEnv"]] = field["proposal"]
        return data
    def test_placeholder_refuses_before_any_network(self):
        config = self.config()
        config["LARK_BASE_APP_TOKEN"] = "bascnxxxxxxxxxxxx"
        with patch.object(schema_tool, "fetch_json") as network:
            self.assertRaises(ValueError, schema_tool.export_schema, config)
            network.assert_not_called()
    def test_export_sanitizer_retains_no_ids_or_tokens(self):
        result = schema_tool.sanitize_field({"field_id": "private", "field_name": "Name", "type": 3,
                    "property": {"options": [{"id": "private", "name": "ACTIVE", "token": "private"}]}})
        self.assertNotIn("private", str(result))
        self.assertEqual(["ACTIVE"], result["options"])
    def test_validation_reports_missing_mismatched_and_required_unmapped(self):
        report = {"domains": {"load": {"fields": [{"name": "Load Name", "type": 2},
                    {"name": "Required extra", "type": 1, "required": True}]}}}
        result = schema_tool.validate(report, self.config())
        self.assertFalse(result["valid"])
        self.assertTrue(any("TYPE_MISMATCH" in x for x in result["errors"]))
        self.assertTrue(any("FIELD_MISSING" in x for x in result["errors"]))
        self.assertTrue(any("REQUIRED_UNMAPPED" in x for x in result["errors"]))
    def test_matching_catalog_validates_without_network(self):
        domains = {}
        for name, definition in schema_tool.catalog().items():
            domains[name] = {"fields": [{"name": f["proposal"], "type": f["type"], "required": f["required"],
                                        "options": f["options"]} for f in definition["fields"]]}
        with patch.object(schema_tool, "fetch_json") as network:
            self.assertTrue(schema_tool.validate({"domains": domains}, self.config())["valid"])
            network.assert_not_called()

