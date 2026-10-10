"""HTTP regression checks for the statistical baseline, without live providers."""

import math
import unittest

from fastapi.testclient import TestClient

from app.main import app


class BaselineApiTest(unittest.TestCase):
    def setUp(self):
        self.client = TestClient(app)
        self.returns = [0.01, -0.005, 0.002] * 40
        self.addCleanup(self.client.close)

    def test_nonconstant_prediction_is_finite_and_bounded(self):
        response = self.client.post("/v1/predict", json={"returns": self.returns})
        self.assertEqual(response.status_code, 200)
        result = response.json()
        self.assertGreater(result["p_up"], 0.5)
        self.assertLess(result["p_up"], 1.0)
        self.assertGreater(result["volatility"], 0)
        for key in ("expected_return", "volatility", "p_up", "confidence"):
            self.assertTrue(math.isfinite(result[key]), key)
        self.assertEqual(result["model_version"], "baseline-v1")

    def test_constant_series_handles_zero_volatility(self):
        for value, probability in ((0.0, 0.5), (0.01, 1.0), (-0.01, 0.0)):
            with self.subTest(value=value):
                response = self.client.post("/v1/predict", json={"returns": [value] * 30})
                self.assertEqual(response.status_code, 200)
                self.assertEqual(response.json()["p_up"], probability)

    def test_short_prediction_input_is_a_client_error(self):
        response = self.client.post("/v1/predict", json={"returns": [0.01]})
        self.assertEqual(response.status_code, 422)

    def test_risk_and_evaluation_endpoints_accept_valid_series(self):
        for path in ("/v1/risk", "/v1/evaluate"):
            with self.subTest(path=path):
                response = self.client.post(path, json={"returns": self.returns})
                self.assertEqual(response.status_code, 200)
                self.assertEqual(response.json()["model_version"], "baseline-v1")

    def test_allocation_uses_asset_by_sample_input(self):
        response = self.client.post("/v1/rl/baseline", json={
            "returns": [self.returns, [-x for x in self.returns]],
            "cash_weight": 0.2,
        })
        self.assertEqual(response.status_code, 200)
        result = response.json()
        self.assertEqual(len(result["target_weights"]), 2)
        self.assertAlmostEqual(sum(result["target_weights"]) + result["cash_weight"], 1.0)
        self.assertTrue(all(0 <= weight <= 1 for weight in result["target_weights"]))


if __name__ == "__main__":
    unittest.main()
