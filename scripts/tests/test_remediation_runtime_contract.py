"""Required release contracts and server-independent OpenAPI drift detection."""
import copy
import unittest
from scripts.verify_remediation_runtime import canonical_openapi, require_contract


class RuntimeContractTest(unittest.TestCase):
    def setUp(self):
        self.api={"paths":{"/api/payments/{id}/cancel":{"post":{}},
                           "/api/payments/{id}":{"delete":{}}, "/api/internal/build":{"get":{}}},
                  "components":{"schemas":{"CreatePaymentRequest":{"required":["invoiceId","idempotencyKey"]},
                      **{name:{"required":["expectedVersion"]} for name in
                         ("UpdateLoadRequest","UpdateTripRequest","UpdateTruckRequest")}}}}
    def test_missing_financial_operation_rejects_old_runtime(self):
        require_contract(self.api)
        del self.api["paths"]["/api/payments/{id}/cancel"]
        with self.assertRaises(ValueError):require_contract(self.api)
    def test_optional_key_or_version_rejects_incompatible_contract(self):
        for name in self.api["components"]["schemas"]:
            with self.subTest(schema=name):
                api=copy.deepcopy(self.api)
                api["components"]["schemas"][name]["required"]=[]
                with self.assertRaises(ValueError):require_contract(api)
    def test_servers_normalize_but_schema_drift_remains_visible(self):
        other=copy.deepcopy(self.api)
        other["servers"]=[{"url":"https://canary.example.test"}]
        other["paths"]["/api/payments/{id}/cancel"]["servers"]=[{"url":"http://localhost"}]
        other["paths"]["/api/internal/build"]["get"]["servers"]=[{"url":"http://localhost"}]
        self.assertEqual(canonical_openapi(self.api),canonical_openapi(other))
        other["components"]["schemas"]["CreatePaymentRequest"]["required"]=["invoiceId"]
        self.assertNotEqual(canonical_openapi(self.api),canonical_openapi(other))
