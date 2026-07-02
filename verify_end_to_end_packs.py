import json
import os
import time
from datetime import datetime

import requests

BASE_URL = "http://127.0.0.1:5000"
ADMIN_USER = os.environ.get("ADMIN_USERNAME", "subhendugupta124548k")
ADMIN_PASS = os.environ.get("ADMIN_PASSWORD", "KryptoLoot@2026")
DEVICE_ID = "verify-device-001"
DEVICE_BALANCE = 8200

PACKS = [
    {"tier_id": "1", "coins": 300, "expected_rupees": 10, "expected_name": "Bronze Pack"},
    {"tier_id": "2", "coins": 700, "expected_rupees": 25, "expected_name": "Silver Pack"},
    {"tier_id": "3", "coins": 1500, "expected_rupees": 50, "expected_name": "Gold Pack"},
    {"tier_id": "4", "coins": 3500, "expected_rupees": 120, "expected_name": "Diamond Pack"},
    {"tier_id": "5", "coins": 7000, "expected_rupees": 250, "expected_name": "Elite Pack"},
]

LEDGER_FILE = "payout_ledger.json"
VAULT_FILE = "user_vault.json"
NOTIFICATIONS_FILE = "notifications.json"
AUDIT_FILE = "audit_logs.json"


def load_json(path):
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def save_json(path, data):
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=4)


def reset_device_balance():
    vault = load_json(VAULT_FILE)
    if DEVICE_ID not in vault:
        raise AssertionError(f"Device {DEVICE_ID} not found in {VAULT_FILE}")
    vault[DEVICE_ID]["coin_balance"] = DEVICE_BALANCE
    vault[DEVICE_ID]["last_calendar_reset"] = datetime.now().strftime("%Y-%m-%d")
    save_json(VAULT_FILE, vault)


def get_dashboard(session):
    r = session.get(f"{BASE_URL}/api/v1/admin/dashboard")
    r.raise_for_status()
    return r.json()


def login_admin(session):
    r = session.post(f"{BASE_URL}/api/v1/admin/login", json={"username": ADMIN_USER, "password": ADMIN_PASS})
    r.raise_for_status()
    data = r.json()
    if data.get("status") != "success":
        raise AssertionError(f"Admin login failed: {data}")
    return data


def verify_pack(session, pack):
    print("\n=== VERIFY PACK", pack["expected_name"], "===")
    reset_device_balance()
    ledger_before = load_json(LEDGER_FILE)
    dashboard_before = login_and_get_dashboard(session)
    history_before = get_redemptions(session)
    notifications_before = load_json(NOTIFICATIONS_FILE)
    audit_before = load_json(AUDIT_FILE)

    # Device handshake and redeem
    r = session.post(f"{BASE_URL}/api/v1/auth/device-handshake", json={"device_id": DEVICE_ID})
    r.raise_for_status()
    handshake = r.json()
    print("handshake status", handshake.get("status"))

    redeem_payload = {
        "device_id": DEVICE_ID,
        "tier_id": pack["tier_id"],
        "payment_method": "UPI",
        "upi_id": "verify@upi"
    }
    r = session.post(f"{BASE_URL}/api/v1/rewards/redeem", json=redeem_payload)
    print("redeem http", r.status_code)
    r.raise_for_status()
    response = r.json()
    print("redeem response", response)
    if "transaction_id" not in response:
        raise AssertionError(f"Redeem response missing transaction_id: {response}")
    txn_id = response["transaction_id"]

    ledger_after_redeem = load_json(LEDGER_FILE)
    new_ledger = [e for e in ledger_after_redeem if e.get("transaction_id") == txn_id]
    if len(new_ledger) != 1:
        raise AssertionError(f"Expected exactly one ledger record for txn {txn_id}, found {len(new_ledger)}")
    ledger_record = new_ledger[0]
    if ledger_record.get("coins") != pack["coins"]:
        raise AssertionError(f"Ledger coins mismatch for txn {txn_id}: expected {pack['coins']} got {ledger_record.get('coins')}")
    if ledger_record.get("payout_value_rupees") != pack["expected_rupees"]:
        raise AssertionError(f"Ledger payout mismatch for txn {txn_id}: expected {pack['expected_rupees']} got {ledger_record.get('payout_value_rupees')}")
    if ledger_record.get("payment_method", "").upper() != "UPI":
        raise AssertionError(f"Ledger payment_method mismatch for txn {txn_id}")

    print("ledger record validated")

    # Dashboard pending view
    r = session.get(f"{BASE_URL}/api/v1/admin/redeems", params={"status": "PENDING"})
    r.raise_for_status()
    pending = [x for x in r.json().get("requests", []) if x.get("request_id") == txn_id]
    if len(pending) != 1:
        raise AssertionError(f"Request {txn_id} not displayed in PENDING dashboard")
    pending_item = pending[0]
    if pending_item.get("reward_pack") != pack["expected_name"]:
        raise AssertionError(f"Dashboard reward_pack mismatch: expected {pack['expected_name']} got {pending_item.get('reward_pack')}")
    if pending_item.get("cash_amount") != pack["expected_rupees"]:
        raise AssertionError(f"Dashboard cash_amount mismatch: expected {pack['expected_rupees']} got {pending_item.get('cash_amount')}")
    if pending_item.get("cash_amount") == 20:
        raise AssertionError("Dashboard shows undefined ₹20 amount")
    print("dashboard pending item validated")

    # Approve and processing
    r = session.post(f"{BASE_URL}/api/v1/admin/redeems/{txn_id}/approve")
    r.raise_for_status()
    approved = r.json()
    if approved.get("status") != "success":
        raise AssertionError(f"Approve failed: {approved}")
    r = session.get(f"{BASE_URL}/api/v1/admin/redeems", params={"status": "PROCESSING"})
    r.raise_for_status()
    processing = [x for x in r.json().get("requests", []) if x.get("request_id") == txn_id]
    if len(processing) != 1:
        raise AssertionError(f"Request {txn_id} not displayed in PROCESSING dashboard")
    print("request moved to PROCESSING")

    # Fulfill and complete
    utr_value = f"UTR-{pack['tier_id']}-{int(time.time())}"
    r = session.post(f"{BASE_URL}/api/v1/admin/fulfill", json={"transaction_id": txn_id, "utr": utr_value})
    r.raise_for_status()
    fulfill = r.json()
    if fulfill.get("status") != "success":
        raise AssertionError(f"Fulfill failed: {fulfill}")
    r = session.get(f"{BASE_URL}/api/v1/admin/redeems", params={"status": "COMPLETED"})
    r.raise_for_status()
    completed = [x for x in r.json().get("requests", []) if x.get("request_id") == txn_id]
    if len(completed) != 1:
        raise AssertionError(f"Request {txn_id} not displayed in COMPLETED dashboard")
    print("request marked COMPLETED")

    ledger_final = load_json(LEDGER_FILE)
    final_records = [e for e in ledger_final if e.get("transaction_id") == txn_id]
    if len(final_records) != 1:
        raise AssertionError(f"Expected single final ledger record for txn {txn_id}, found {len(final_records)}")
    final_record = final_records[0]
    for field in ["utr", "paid_time", "completed_time", "paid_by"]:
        if not final_record.get(field):
            raise AssertionError(f"Ledger missing field {field} for txn {txn_id}")
    print("ledger final record validated")

    notifications = load_json(NOTIFICATIONS_FILE)
    notif_for_txn = [n for n in notifications if n.get("request_id") == txn_id]
    statuses = [n.get("status") for n in notif_for_txn]
    if set(statuses) != {"PENDING", "PROCESSING", "COMPLETED"}:
        raise AssertionError(f"Notification statuses mismatch for txn {txn_id}: {statuses}")
    if len(notif_for_txn) != 3:
        raise AssertionError(f"Expected exactly 3 notifications for txn {txn_id}, found {len(notif_for_txn)}")
    print("notifications validated")

    history = get_redemptions(session)
    history_matches = [x for x in history if x.get("queue_id") == txn_id or x.get("request_id") == txn_id]
    if len(history_matches) != 1:
        raise AssertionError(f"Expected exactly one history entry for txn {txn_id}, found {len(history_matches)}")
    print("history entry validated")

    audit = load_json(AUDIT_FILE)
    audit_entries = [e for e in audit if e.get("request_id") == txn_id]
    if len(audit_entries) != 2:
        raise AssertionError(f"Expected 2 audit entries for txn {txn_id}, found {len(audit_entries)}")
    expected_transition = [(e.get("old_status"), e.get("new_status")) for e in audit_entries]
    if expected_transition != [("PENDING", "PROCESSING"), ("PROCESSING", "COMPLETED")]:
        raise AssertionError(f"Audit transitions mismatch for txn {txn_id}: {expected_transition}")
    for entry in audit_entries:
        for field in ["old_status", "new_status", "admin_username", "timestamp", "ip_address"]:
            if field not in entry:
                raise AssertionError(f"Audit entry missing {field} for txn {txn_id}")
    print("audit log validated")

    dashboard_after = get_dashboard(session)
    before_metrics = dashboard_before["metrics"]
    after_metrics = dashboard_after["metrics"]
    expected_monthly_increase = pack["expected_rupees"]
    if after_metrics["monthly_payout"] - before_metrics["monthly_payout"] != expected_monthly_increase:
        raise AssertionError(f"Monthly payout did not increase by {expected_monthly_increase}; before {before_metrics['monthly_payout']}, after {after_metrics['monthly_payout']}")
    if before_metrics["remaining_reward_pool"] - after_metrics["remaining_reward_pool"] != expected_monthly_increase:
        raise AssertionError(f"Reward pool did not decrease by {expected_monthly_increase}; before {before_metrics['remaining_reward_pool']}, after {after_metrics['remaining_reward_pool']}")
    print("dashboard metrics validated")

    print(f"PACK {pack['expected_name']} PASS")
    return True


def login_and_get_dashboard(session):
    login_admin(session)
    return get_dashboard(session)


def get_redemptions(session):
    r = session.get(f"{BASE_URL}/api/v1/users/{DEVICE_ID}/redemptions")
    r.raise_for_status()
    return r.json()


if __name__ == "__main__":
    session = requests.Session()
    results = {}
    if "ADMIN_PASSWORD" in os.environ:
        print("Using ADMIN_PASSWORD from environment")
    for pack in PACKS:
        try:
            results[pack["expected_name"]] = verify_pack(session, pack)
        except Exception as exc:
            print(f"PACK {pack['expected_name']} FAIL: {exc}")
            results[pack["expected_name"]] = False
    print("\n=== SUMMARY ===")
    for name, passed in results.items():
        print(name, "PASS" if passed else "FAIL")
    if not all(results.values()):
        raise SystemExit(1)
