#!/usr/bin/env python
"""Test redeem submission and approval workflow to verify no duplicates"""
import requests
import json

BASE_URL = "http://127.0.0.1:5000"

# Create a new session
session = requests.Session()

# Step 1: Submit a redeem request
print("Step 1: Submitting redeem request...")
device_handshake = session.post(
    f"{BASE_URL}/api/v1/auth/device-handshake",
    json={"device_id": "test-device-workflow-001"}
).json()
print(f"  Device handshake response: {device_handshake['status']}")

redeem_response = session.post(
    f"{BASE_URL}/api/v1/rewards/redeem",
    json={
        "device_id": "test-device-workflow-001",
        "tier_id": "2",
        "payment_method": "UPI",
        "upi_id": "test@upi"
    }
).json()
print(f"  Redeem response: {redeem_response['status']}")
if "transaction_id" in redeem_response:
    txn_id = redeem_response["transaction_id"]
    print(f"  Transaction ID: {txn_id}")

# Step 2: Login as admin
print("\nStep 2: Admin login...")
login_response = session.post(
    f"{BASE_URL}/api/v1/admin/login",
    json={
        "username": "subhendugupta124548k",
        "password": "subhendugupta124548k@@124548k"
    }
).json()
print(f"  Login response: {login_response['status']}")

# Step 3: Get pending requests before approval
print("\nStep 3: Getting redeems before approval...")
redeems_before = session.get(
    f"{BASE_URL}/api/v1/admin/redeems?status=PENDING"
).json()
print(f"  Pending requests before: {len(redeems_before['requests'])}")

# Step 4: Approve the request
print(f"\nStep 4: Approving request {txn_id}...")
approve_response = session.post(
    f"{BASE_URL}/api/v1/admin/redeems/{txn_id}/approve"
).json()
print(f"  Approve response: {approve_response['status']}")

# Step 5: Get pending requests after approval
print("\nStep 5: Getting redeems after approval...")
redeems_after = session.get(
    f"{BASE_URL}/api/v1/admin/redeems?status=PENDING"
).json()
print(f"  Pending requests after: {len(redeems_after['requests'])}")

# Step 6: Get processing requests to verify
print("\nStep 6: Getting processing redeems...")
redeems_processing = session.get(
    f"{BASE_URL}/api/v1/admin/redeems?status=PROCESSING"
).json()
processing_count = len([r for r in redeems_processing['requests'] if r['request_id'] == txn_id])
print(f"  Processing request count for {txn_id}: {processing_count}")

# Step 7: Mark the request paid with a UTR
print(f"\nStep 7: Marking request {txn_id} as paid...")
mark_paid_response = session.post(
    f"{BASE_URL}/api/v1/admin/fulfill",
    json={
        "transaction_id": txn_id,
        "utr": "UTR-12345-XYZ"
    }
).json()
print(f"  Mark paid response: {mark_paid_response['status']}")

# Step 8: Get completed requests to verify
print("\nStep 8: Getting completed redeems...")
redeems_completed = session.get(
    f"{BASE_URL}/api/v1/admin/redeems?status=COMPLETED"
).json()
completed_count = len([r for r in redeems_completed['requests'] if r['request_id'] == txn_id])
print(f"  Completed request count for {txn_id}: {completed_count}")

# Step 9: Get notifications and check for duplicates
print("\nStep 9: Checking notifications for duplicates...")
notifications = session.get(
    f"{BASE_URL}/api/v1/admin/notifications"
).json()
notifications_for_txn = [n for n in notifications['notifications'] if n['request_id'] == txn_id]
print(f"  Total notifications for {txn_id}: {len(notifications_for_txn)}")
if len(notifications_for_txn) != 3:
    print("  ❌ ERROR: Expected 3 notifications (PENDING, PROCESSING, COMPLETED)!")
else:
    print("  ✅ Notification count is correct")

# Step 10: Get audit log and check for duplicates
print("\nStep 10: Checking audit log for duplicates...")
audit_response = session.get(
    f"{BASE_URL}/api/v1/admin/audit"
).json()
audit_log = audit_response.get('audit_log', [])
audit_entries = [e for e in audit_log if e.get('request_id') == txn_id]
print(f"  Audit entries for {txn_id}: {len(audit_entries)}")
if len(audit_entries) != 2:
    print("  ❌ ERROR: Expected 2 audit entries (PROCESSING and COMPLETED)!")
else:
    print("  ✅ Audit entry count is correct")

# Step 9: Check the ledger directly for duplicate records
print("\nStep 9: Checking ledger for duplicates...")
with open("payout_ledger.json", "r") as f:
    ledger = json.load(f)
    duplicate_records = [e for e in ledger if e['transaction_id'] == txn_id]
    print(f"  Records in ledger for {txn_id}: {len(duplicate_records)}")
    if len(duplicate_records) > 1:
        print("  ❌ ERROR: Found duplicate transaction records!")
    else:
        print("  ✅ Single transaction record (no duplicates)")
    if duplicate_records:
        print(f"  Status: {duplicate_records[0]['status']}")

print("\n" + "="*60)
if len(audit_entries) == 2 and len(duplicate_records) == 1:
    print("✅ WORKFLOW TEST PASSED - No duplicate transaction or audit records created!")
else:
    print("❌ WORKFLOW TEST FAILED - Duplicate records detected or unexpected audit entries!")
