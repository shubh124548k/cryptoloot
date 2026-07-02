import importlib
import json
import os
import sys
from pathlib import Path


def test_admin_redeems_search_matches_numeric_request_id(tmp_path):
    os.environ["STORAGE_FILE"] = str(tmp_path / "user_vault.json")
    os.environ["LEDGER_FILE"] = str(tmp_path / "payout_ledger.json")
    os.environ["NOTIFICATIONS_FILE"] = str(tmp_path / "notifications.json")
    os.environ["AUDIT_LOG_FILE"] = str(tmp_path / "audit_logs.json")
    os.environ["SETTINGS_FILE"] = str(tmp_path / "settings.json")
    os.environ["ADMIN_USERNAME"] = "admin"
    os.environ["ADMIN_PASSWORD"] = "pw"

    sys.modules.pop("app", None)
    app_module = importlib.import_module("app")
    app_module.app.testing = True

    client = app_module.app.test_client()

    vault = {
        "device-1": {
            "master_uid": "KL-TEST-001",
            "coin_balance": 1000,
            "trust_score": 100,
            "account_status": "ACTIVE",
            "ads_today": 0,
            "session_ads": 0,
            "last_calendar_reset": "2026-01-01",
        }
    }
    app_module.write_json_file(app_module.STORAGE_FILE, vault)
    ledger = [
        {
            "transaction_id": "TXN-1782814431-MYA",
            "device_id": "device-1",
            "master_uid": "KL-TEST-001",
            "tier_id": "2",
            "coins": 700,
            "payout_value_rupees": 25,
            "status": "PENDING",
            "payment_method": "UPI",
            "upi_id": "test@upi",
            "timestamp": "2026-06-30T15:43:51.200069+05:30",
            "updated_at": "2026-06-30T15:43:51.200069+05:30",
        }
    ]
    app_module.write_json_file(app_module.LEDGER_FILE, ledger)

    login_response = client.post(
        "/api/v1/admin/login",
        json={"username": "admin", "password": "pw"},
    )
    assert login_response.status_code == 200

    response = client.get("/api/v1/admin/redeems", query_string={"search": "1782814431"})
    assert response.status_code == 200
    payload = response.get_json()
    assert payload["requests"]
    assert payload["requests"][0]["request_id"] == "TXN-1782814431-MYA"
    assert payload["requests"][0]["transaction_id"] == "TXN-1782814431-MYA"
    assert payload["requests"][0]["request_id_numeric"] == 1782814431
