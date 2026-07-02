import json
import os
import time
import requests

BASE_URL = "http://127.0.0.1:5000"
ADMIN_USER = os.environ.get("ADMIN_USERNAME", "subhendugupta124548k")
ADMIN_PASS = os.environ.get("ADMIN_PASSWORD", "KryptoLoot@2026")

mapping = {300: 10, 700: 25, 1500: 50, 3500: 120, 7000: 250}

def load_json(path):
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def main():
    session = requests.Session()
    now = int(time.time())
    device_id = f"test-device-e2e-{now}"
    print("device_id:", device_id)
    print("admin_user:", ADMIN_USER)

    ledger_before = load_json("payout_ledger.json")
    print("ledger_before_count:", len(ledger_before))

    r = session.post(f"{BASE_URL}/api/v1/auth/device-handshake", json={"device_id": device_id})
    print("handshake_status", r.status_code)
    r.raise_for_status()
    print("handshake_response", r.json())

    redeem_payload = {
        "device_id": device_id,
        "tier_id": "2",
        "payment_method": "UPI",
        "upi_id": "test@upi"
    }
    r = session.post(f"{BASE_URL}/api/v1/rewards/redeem", json=redeem_payload)
    print("redeem_status", r.status_code)
    r.raise_for_status()
    redeem = r.json()
    print("redeem_response", redeem)
    if "transaction_id" not in redeem:
        raise AssertionError("Redeem response missing transaction_id")
    txn_id = redeem["transaction_id"]
    print("txn_id", txn_id)

    ledger_after_redeem = load_json("payout_ledger.json")
    new_records = [e for e in ledger_after_redeem if e.get("transaction_id") == txn_id]
    print("ledger_new_records", len(new_records))
    if len(new_records) != 1:
        raise AssertionError(f"Expected exactly one ledger record for {txn_id}, found {len(new_records)}")
    record = new_records[0]
    print("ledger_record", {k: record.get(k) for k in ["transaction_id", "coins", "payout_value_rupees", "status", "device_id", "payment_method", "upi_id", "mobile_number", "redeem_code"]})

    coins = record.get("coins")
    payout = record.get("payout_value_rupees")
    if coins not in mapping:
        raise AssertionError(f"Unexpected coins value {coins}")
    if payout != mapping[coins]:
        raise AssertionError(f"Reward mismatch for coins {coins}: expected {mapping[coins]} got {payout}")
    if payout == 20:
        raise AssertionError("Found undefined cash amount ₹20")

    r = session.post(f"{BASE_URL}/api/v1/admin/login", json={"username": ADMIN_USER, "password": ADMIN_PASS})
    print("admin_login_status", r.status_code)
    r.raise_for_status()
    login = r.json()
    print("admin_login_response", login)
    if login.get("status") != "success":
        raise AssertionError("Admin login failed")

    r = session.get(f"{BASE_URL}/api/v1/admin/redeems", params={"status": "PENDING"})
    r.raise_for_status()
    pending = [x for x in r.json().get("requests", []) if x.get("request_id") == txn_id]
    print("pending_matches", len(pending))
    if len(pending) != 1:
        raise AssertionError("Request not found in PENDING admin redeems")

    r = session.post(f"{BASE_URL}/api/v1/admin/redeems/{txn_id}/approve")
    print("approve_status", r.status_code)
    r.raise_for_status()
    approve = r.json()
    print("approve_response", approve)
    if approve.get("status") != "success":
        raise AssertionError("Approve endpoint did not return success")

    r = session.get(f"{BASE_URL}/api/v1/admin/redeems", params={"status": "PROCESSING"})
    r.raise_for_status()
    processing = [x for x in r.json().get("requests", []) if x.get("request_id") == txn_id]
    print("processing_matches", len(processing))
    if len(processing) != 1:
        raise AssertionError("Request not found in PROCESSING admin redeems")

    utr = "UTR-12345-XYZ"
    r = session.post(f"{BASE_URL}/api/v1/admin/fulfill", json={"transaction_id": txn_id, "utr": utr})
    print("fulfill_status", r.status_code)
    r.raise_for_status()
    fulfill = r.json()
    print("fulfill_response", fulfill)
    if fulfill.get("status") != "success":
        raise AssertionError("Fulfill endpoint did not return success")

    r = session.get(f"{BASE_URL}/api/v1/admin/redeems", params={"status": "COMPLETED"})
    r.raise_for_status()
    completed = [x for x in r.json().get("requests", []) if x.get("request_id") == txn_id]
    print("completed_matches", len(completed))
    if len(completed) != 1:
        raise AssertionError("Request not found in COMPLETED admin redeems")

    ledger_final = load_json("payout_ledger.json")
    final = [e for e in ledger_final if e.get("transaction_id") == txn_id]
    print("final_ledger_matches", len(final))
    if len(final) != 1:
        raise AssertionError("Ledger contains duplicate or missing records for transaction")
    final_record = final[0]
    for field in ["utr", "paid_time", "completed_time", "paid_by"]:
        if field not in final_record or not final_record[field]:
            raise AssertionError(f"Ledger field {field} missing or empty")
    print("final_ledger_record", {k: final_record.get(k) for k in ["status", "utr", "paid_time", "completed_time", "paid_by"]})

    audit = load_json("audit_logs.json")
    audit_entries = [e for e in audit if e.get("request_id") == txn_id]
    print("audit_entries_count", len(audit_entries))
    if len(audit_entries) < 1:
        raise AssertionError("No audit log entries found for transaction")

    notifications = load_json("notifications.json")
    notif_entries = [n for n in notifications if n.get("request_id") == txn_id]
    print("notification_count", len(notif_entries))
    if len(notif_entries) == 0:
        raise AssertionError("No notification entries found for transaction")

    r = session.get(f"{BASE_URL}/api/v1/users/{device_id}/redemptions")
    r.raise_for_status()
    history_matches = [x for x in r.json() if x.get("queue_id") == txn_id or x.get("request_id") == txn_id]
    print("history_matches", len(history_matches))
    if len(history_matches) == 0:
        raise AssertionError("No history entry found for transaction")

    print("PASS")


if __name__ == "__main__":
    main()
