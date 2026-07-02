import json
import os
import tempfile
import unittest
from pathlib import Path

import app as backend


class AdCompletionContractTests(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory(dir=str(Path(__file__).resolve().parent.parent))
        self.addCleanup(self.temp_dir.cleanup)
        self.temp_path = Path(self.temp_dir.name)

        backend.app.config.update(TESTING=True)
        backend.STORAGE_FILE = str(self.temp_path / "user_vault.json")
        backend.LEDGER_FILE = str(self.temp_path / "payout_ledger.json")
        backend.NOTIFICATIONS_FILE = str(self.temp_path / "notifications.json")
        backend.AUDIT_LOG_FILE = str(self.temp_path / "audit_logs.json")
        backend.SETTINGS_FILE = str(self.temp_path / "settings.json")

        vault = {
            "device-123": {
                "master_uid": "KL-TEST-1234",
                "coin_balance": 0,
                "trust_score": 100,
                "account_status": "ACTIVE",
                "ads_today": 0,
                "session_ads": 0,
                "last_calendar_reset": backend.datetime.now(backend.ist_timezone).strftime("%Y-%m-%d"),
            }
        }
        with open(backend.STORAGE_FILE, "w", encoding="utf-8") as fh:
            json.dump(vault, fh)

        self.client = backend.app.test_client()

    def test_log_completion_returns_android_compatible_payload(self):
        response = self.client.post(
            "/api/v1/ads/log-completion",
            json={"device_id": "device-123", "time_spent_seconds": 15},
        )

        self.assertEqual(response.status_code, 200)
        payload = response.get_json()
        self.assertTrue(payload["success"])
        self.assertEqual(payload["coins_earned"], 10)
        self.assertEqual(payload["new_balance"], 10)
        self.assertEqual(payload["ads_today"], 1)
        self.assertEqual(payload["session_ads"], 1)
        self.assertEqual(payload["trust_score"], 100)
        self.assertEqual(payload["message"], "Ad reward credited successfully.")
        self.assertIsNone(payload["break_until"])

    def test_redeem_returns_android_compatible_payload(self):
        with open(backend.STORAGE_FILE, "w", encoding="utf-8") as fh:
            json.dump(
                {
                    "device-123": {
                        "master_uid": "KL-TEST-1234",
                        "coin_balance": 300,
                        "trust_score": 100,
                        "account_status": "ACTIVE",
                        "ads_today": 0,
                        "session_ads": 0,
                        "last_calendar_reset": backend.datetime.now(backend.ist_timezone).strftime("%Y-%m-%d"),
                    }
                },
                fh,
            )

        response = self.client.post(
            "/api/v1/rewards/redeem",
            json={"device_id": "device-123", "coins_to_redeem": 300, "payment_method": "UPI", "upi_id": "test@upi"},
        )

        self.assertEqual(response.status_code, 200)
        payload = response.get_json()
        self.assertTrue(payload["success"])
        self.assertEqual(payload["coins_deducted"], 300)
        self.assertEqual(payload["coins_remaining"], 0)
        self.assertEqual(payload["payout_value"], 10.0)
        self.assertEqual(payload["currency"], "INR")
        self.assertEqual(payload["status"], "QUEUED")
        self.assertEqual(payload["message"], "Redeem request submitted successfully.")
        self.assertEqual(payload["estimated_delivery"], "Awaiting admin approval")

    def test_redeem_uses_android_payment_details_field(self):
        with open(backend.STORAGE_FILE, "w", encoding="utf-8") as fh:
            json.dump(
                {
                    "device-123": {
                        "master_uid": "KL-TEST-1234",
                        "coin_balance": 300,
                        "trust_score": 100,
                        "account_status": "ACTIVE",
                        "ads_today": 0,
                        "session_ads": 0,
                        "last_calendar_reset": backend.datetime.now(backend.ist_timezone).strftime("%Y-%m-%d"),
                    }
                },
                fh,
            )

        response = self.client.post(
            "/api/v1/rewards/redeem",
            json={
                "device_id": "device-123",
                "coins_to_redeem": 300,
                "payment_method": "UPI",
                "payment_details": "android@upi",
            },
        )

        self.assertEqual(response.status_code, 200)
        ledger = json.loads(Path(backend.LEDGER_FILE).read_text(encoding="utf-8"))
        self.assertEqual(ledger[-1]["upi_id"], "android@upi")
        self.assertEqual(ledger[-1]["payment_method"], "UPI")


if __name__ == "__main__":
    unittest.main()
