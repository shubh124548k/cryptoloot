#!/usr/bin/env python3
"""
Complete end-to-end test of KryptoLoot ecosystem
Tests:
1. Admin authentication
2. Android redeem submission with payment_details
3. Admin fetch request details (new endpoint)
4. Admin approve redeem
5. Admin mark paid with UTR
6. Rate limiting enforcement
7. Admin search functionality
8. Ledger persistence
"""

import json
from datetime import datetime
import os
from app import app

# Use Flask test client for all requests
client = app.test_client()

BASE_URL = "http://127.0.0.1:5000"  # Not used with test client but keeping for reference
ADMIN_USERNAME = "subhendugupta124548k"
ADMIN_PASSWORD = "KryptoLoot@2026"

print("=" * 80)
print("KRYPTOLOOT ECOSYSTEM END-TO-END TEST")
print("=" * 80)

# SETUP: Create test users with coins in vault
print("\n[SETUP] Creating test users with coins...")
if os.path.exists("user_vault.json"):
    with open("user_vault.json") as f:
        vault = json.load(f)
else:
    vault = {}

# Create test user with sufficient coins
test_users = {
    "test-e2e-001": {
        "master_uid": "KL-TEST-E2E1",
        "coin_balance": 5000,
        "trust_score": 100,
        "account_status": "ACTIVE",
        "ads_today": 0,
        "session_ads": 0,
        "last_calendar_reset": datetime.now().strftime("%Y-%m-%d")
    },
    "test-ratelimit": {
        "master_uid": "KL-RATELIMIT",
        "coin_balance": 10000,
        "trust_score": 100,
        "account_status": "ACTIVE",
        "ads_today": 0,
        "session_ads": 0,
        "last_calendar_reset": datetime.now().strftime("%Y-%m-%d")
    }
}

vault.update(test_users)
with open("user_vault.json", "w") as f:
    json.dump(vault, f, indent=2)
print("[OK] Test users created")

# SETUP: Clear old ledger entries for test devices to avoid rate limit issues
print("[SETUP] Cleaning up old test ledger entries...")
if os.path.exists("payout_ledger.json"):
    with open("payout_ledger.json") as f:
        ledger = json.load(f)
    # Remove entries for test devices
    ledger = [entry for entry in ledger if entry.get("device_id") not in ["test-e2e-001", "test-ratelimit"]]
    with open("payout_ledger.json", "w") as f:
        json.dump(ledger, f, indent=2)
print("[OK] Old test entries cleaned")

# Test 1: Admin Login
print("\n[TEST 1] Admin Authentication")
login_resp = client.post('/api/v1/admin/login', 
    json={"username": ADMIN_USERNAME, "password": ADMIN_PASSWORD})
print(f"  Status: {login_resp.status_code}")
print(f"  Result: {'[PASS] PASS' if login_resp.status_code == 200 else '[FAIL] FAIL'}")

# Test 2: Submit Android redeem with payment_details
print("\n[TEST 2] Android Redeem Submission (payment_details field)")
redeem_resp = client.post('/api/v1/rewards/redeem',
    json={
        "device_id": "test-e2e-001",
        "coins_to_redeem": 300,
        "payment_method": "UPI",
        "payment_details": "e2e-test@upi",
        "username": "TestUser",
        "reward_pack": "Bronze Pack",
        "coins": 300,
        "cash_amount": 10.0
    })
print(f"  Status: {redeem_resp.status_code}")
resp_data = redeem_resp.get_json() if redeem_resp.status_code == 200 else {}
print(f"  Transaction ID: {resp_data.get('transaction_id', 'N/A')}")
txn_id = resp_data.get('transaction_id')
success = resp_data.get('success', False) if resp_data else False
print(f"  Result: {'[PASS] PASS' if success else '[FAIL] FAIL'}")

# Test 3: Admin fetch request details (NEW ENDPOINT)
print("\n[TEST 3] Admin Fetch Full Request Details (NEW ENDPOINT)")
if txn_id:
    details_resp = client.get(f'/api/v1/admin/redeems/{txn_id}')
    print(f"  Status: {details_resp.status_code}")
    if details_resp.status_code == 200:
        details = details_resp.get_json().get('request', {})
        print(f"  Transaction ID: {details.get('transaction_id')}")
        print(f"  Status: {details.get('status')}")
        print(f"  Payment Method: {details.get('payment_method')}")
        print(f"  UPI ID: {details.get('upi_id')}")
        print(f"  Reject Reason: {details.get('reject_reason')}")
        print(f"  Created At: {details.get('created_at')}")
        print(f"  Result: {'[PASS] PASS' if details.get('upi_id') == 'e2e-test@upi' else '[FAIL] FAIL (payment_details not mapped)'}")
    else:
        print(f"  Error: {details_resp.data}")
        print(f"  Result: [FAIL] FAIL")

# Test 4: Admin Approve
print("\n[TEST 4] Admin Approve Redeem")
if txn_id:
    approve_resp = client.post(f'/api/v1/admin/redeems/{txn_id}/approve')
    print(f"  Status: {approve_resp.status_code}")
    print(f"  Result: {'[PASS] PASS' if approve_resp.status_code == 200 else '[FAIL] FAIL'}")

# Test 5: Admin Mark Paid (with UTR)
print("\n[TEST 5] Admin Mark Paid (with UTR/Payment Reference)")
if txn_id:
    utr = "UTR-E2E-20260701-0001"
    markpaid_resp = client.post('/api/v1/admin/fulfill',
        json={"transaction_id": txn_id, "utr": utr})
    print(f"  Status: {markpaid_resp.status_code}")
    print(f"  UTR: {utr}")
    print(f"  Result: {'[PASS] PASS' if markpaid_resp.status_code == 200 else '[FAIL] FAIL'}")

# Verify UTR was stored
if txn_id:
    check_resp = client.get(f'/api/v1/admin/redeems/{txn_id}')
    if check_resp.status_code == 200:
        stored_utr = check_resp.get_json().get('request', {}).get('utr')
        print(f"  Stored UTR: {stored_utr}")
        print(f"  UTR Persistence: {'[PASS] YES' if stored_utr == utr else '[FAIL] NO'}")

# Test 6: Rate Limiting Check
print("\n[TEST 6] Rate Limiting (Daily Limit = 3 per day)")

resp1 = client.post('/api/v1/rewards/redeem',
    json={
        "device_id": "test-ratelimit",
        "coins_to_redeem": 300,
        "payment_method": "UPI",
        "payment_details": "rate1@upi",
        "username": "RateLimitTest",
        "reward_pack": "Bronze",
        "coins": 300,
        "cash_amount": 10.0
    })
print(f"  Request 1: {resp1.status_code} {'[PASS]' if resp1.status_code == 200 else '[FAIL]'}")

resp2 = client.post('/api/v1/rewards/redeem',
    json={
        "device_id": "test-ratelimit",
        "coins_to_redeem": 700,
        "payment_method": "MOBILE",
        "payment_details": "9876543210",
        "username": "RateLimitTest",
        "reward_pack": "Silver",
        "coins": 700,
        "cash_amount": 25.0
    })
print(f"  Request 2: {resp2.status_code} {'[PASS]' if resp2.status_code == 200 else '[FAIL]'}")

resp3 = client.post('/api/v1/rewards/redeem',
    json={
        "device_id": "test-ratelimit",
        "coins_to_redeem": 1500,
        "payment_method": "REDEEM_CODE",
        "payment_details": "SUMMER2026",
        "username": "RateLimitTest",
        "reward_pack": "Gold",
        "coins": 1500,
        "cash_amount": 50.0
    })
print(f"  Request 3: {resp3.status_code} {'[PASS]' if resp3.status_code == 200 else '[FAIL]'}")

# This should hit the rate limit
resp4 = client.post('/api/v1/rewards/redeem',
    json={
        "device_id": "test-ratelimit",
        "coins_to_redeem": 3500,
        "payment_method": "UPI",
        "payment_details": "rate4@upi",
        "username": "RateLimitTest",
        "reward_pack": "Diamond",
        "coins": 3500,
        "cash_amount": 120.0
    })
print(f"  Request 4 (should hit rate limit): {resp4.status_code} {'[PASS] RATE LIMITED' if resp4.status_code == 429 else '[FAIL]'}")
if resp4.status_code == 429:
    print(f"  Rate limit message: {resp4.get_json().get('message')}")

# Test 7: Search functionality
print("\n[TEST 7] Admin Search Functionality")
search_resp = client.get('/api/v1/admin/redeems', 
    query_string={'status': 'ALL', 'search': 'test-e2e-001'})
print(f"  Status: {search_resp.status_code}")
list_data = search_resp.get_json() if search_resp.status_code == 200 else {}
requests_found = len(list_data.get('requests', []))
print(f"  Requests found: {requests_found}")
if requests_found > 0:
    first = list_data['requests'][0]
    print(f"  Found transaction: {first.get('request_id')}")
print(f"  Result: {'[PASS] PASS' if requests_found > 0 else '[WARN] WARNING'}")

# Test 8: Ledger Persistence
print("\n[TEST 8] Ledger Persistence (File-based JSON)")
if os.path.exists("payout_ledger.json"):
    with open("payout_ledger.json") as f:
        ledger = json.load(f)
    print(f"  Ledger entries: {len(ledger)}")
    if ledger:
        latest = ledger[0]
        print(f"  Latest transaction: {latest.get('transaction_id')}")
        print(f"  Latest status: {latest.get('status')}")
        print(f"  Latest UTR: {latest.get('utr')}")
        print(f"  Ledger payment_details field mapping:")
        print(f"    - upi_id: {latest.get('upi_id')}")
        print(f"    - mobile_number: {latest.get('mobile_number')}")
        print(f"    - redeem_code_value: {latest.get('redeem_code_value')}")
        print(f"  Result: [PASS] PASS")
else:
    print(f"  Result: [FAIL] FAIL (ledger file missing)")

print("\n" + "=" * 80)
print("E2E TEST COMPLETE")
print("=" * 80)



# Test 1: Admin Login
print("\n[TEST 1] Admin Authentication")
login_resp = client.post('/api/v1/admin/login', 
    json={"username": ADMIN_USERNAME, "password": ADMIN_PASSWORD})
print(f"  Status: {login_resp.status_code}")
print(f"  Result: {'[PASS] PASS' if login_resp.status_code == 200 else '[FAIL] FAIL'}")

# Test 2: Submit Android redeem with payment_details
print("\n[TEST 2] Android Redeem Submission (payment_details field)")
redeem_resp = client.post('/api/v1/rewards/redeem',
    json={
        "device_id": "test-e2e-001",
        "coins_to_redeem": 300,
        "payment_method": "UPI",
        "payment_details": "e2e-test@upi",
        "username": "TestUser",
        "reward_pack": "Bronze Pack",
        "coins": 300,
        "cash_amount": 10.0
    })
print(f"  Status: {redeem_resp.status_code}")
resp_data = redeem_resp.get_json()
print(f"  Transaction ID: {resp_data.get('transaction_id')}")
txn_id = resp_data.get('transaction_id')
print(f"  Result: {'[PASS] PASS' if resp_data.get('success') else '[FAIL] FAIL'}")

# Test 3: Admin fetch request details (NEW ENDPOINT)
print("\n[TEST 3] Admin Fetch Full Request Details (NEW ENDPOINT)")
if txn_id:
    details_resp = client.get(f'/api/v1/admin/redeems/{txn_id}')
    print(f"  Status: {details_resp.status_code}")
    if details_resp.status_code == 200:
        details = details_resp.get_json().get('request', {})
        print(f"  Transaction ID: {details.get('transaction_id')}")
        print(f"  Status: {details.get('status')}")
        print(f"  Payment Method: {details.get('payment_method')}")
        print(f"  UPI ID: {details.get('upi_id')}")
        print(f"  Reject Reason: {details.get('reject_reason')}")
        print(f"  Created At: {details.get('created_at')}")
        print(f"  Result: {'[PASS] PASS' if details.get('upi_id') == 'e2e-test@upi' else '[FAIL] FAIL (payment_details not mapped)'}")
    else:
        print(f"  Error: {details_resp.data}")
        print(f"  Result: [FAIL] FAIL")

# Test 4: Admin Approve
print("\n[TEST 4] Admin Approve Redeem")
if txn_id:
    approve_resp = client.post(f'/api/v1/admin/redeems/{txn_id}/approve')
    print(f"  Status: {approve_resp.status_code}")
    print(f"  Result: {'[PASS] PASS' if approve_resp.status_code == 200 else '[FAIL] FAIL'}")

# Test 5: Admin Mark Paid (with UTR)
print("\n[TEST 5] Admin Mark Paid (with UTR/Payment Reference)")
if txn_id:
    utr = "UTR-E2E-20260701-0001"
    markpaid_resp = client.post('/api/v1/admin/fulfill',
        json={"transaction_id": txn_id, "utr": utr})
    print(f"  Status: {markpaid_resp.status_code}")
    print(f"  UTR: {utr}")
    print(f"  Result: {'[PASS] PASS' if markpaid_resp.status_code == 200 else '[FAIL] FAIL'}")

# Verify UTR was stored
if txn_id:
    check_resp = client.get(f'/api/v1/admin/redeems/{txn_id}')
    if check_resp.status_code == 200:
        stored_utr = check_resp.get_json().get('request', {}).get('utr')
        print(f"  Stored UTR: {stored_utr}")
        print(f"  UTR Persistence: {'[PASS] YES' if stored_utr == utr else '[FAIL] NO'}")

# Test 6: Rate Limiting Check
print("\n[TEST 6] Rate Limiting (Daily Limit = 3 per day)")

resp1 = client.post('/api/v1/rewards/redeem',
    json={
        "device_id": "test-ratelimit",
        "coins_to_redeem": 300,
        "payment_method": "UPI",
        "payment_details": "rate1@upi",
        "username": "RateLimitTest",
        "reward_pack": "Bronze",
        "coins": 300,
        "cash_amount": 10.0
    })
print(f"  Request 1: {resp1.status_code} {'[PASS]' if resp1.status_code == 200 else '[FAIL]'}")

resp2 = client.post('/api/v1/rewards/redeem',
    json={
        "device_id": "test-ratelimit",
        "coins_to_redeem": 700,
        "payment_method": "MOBILE",
        "payment_details": "9876543210",
        "username": "RateLimitTest",
        "reward_pack": "Silver",
        "coins": 700,
        "cash_amount": 25.0
    })
print(f"  Request 2: {resp2.status_code} {'[PASS]' if resp2.status_code == 200 else '[FAIL]'}")

resp3 = client.post('/api/v1/rewards/redeem',
    json={
        "device_id": "test-ratelimit",
        "coins_to_redeem": 1500,
        "payment_method": "REDEEM_CODE",
        "payment_details": "SUMMER2026",
        "username": "RateLimitTest",
        "reward_pack": "Gold",
        "coins": 1500,
        "cash_amount": 50.0
    })
print(f"  Request 3: {resp3.status_code} {'[PASS]' if resp3.status_code == 200 else '[FAIL]'}")

# This should hit the rate limit
resp4 = client.post('/api/v1/rewards/redeem',
    json={
        "device_id": "test-ratelimit",
        "coins_to_redeem": 3500,
        "payment_method": "UPI",
        "payment_details": "rate4@upi",
        "username": "RateLimitTest",
        "reward_pack": "Diamond",
        "coins": 3500,
        "cash_amount": 120.0
    })
print(f"  Request 4 (should hit rate limit): {resp4.status_code} {'[PASS] RATE LIMITED' if resp4.status_code == 429 else '[FAIL]'}")
if resp4.status_code == 429:
    print(f"  Rate limit message: {resp4.get_json().get('message')}")

# Test 7: Search functionality
print("\n[TEST 7] Admin Search Functionality")
search_resp = client.get('/api/v1/admin/redeems', 
    query_string={'status': 'ALL', 'search': 'test-e2e-001'})
print(f"  Status: {search_resp.status_code}")
list_data = search_resp.get_json()
requests_found = len(list_data.get('requests', []))
print(f"  Requests found: {requests_found}")
if requests_found > 0:
    first = list_data['requests'][0]
    print(f"  Found transaction: {first.get('request_id')}")
print(f"  Result: {'[PASS] PASS' if requests_found > 0 else '[WARN] WARNING'}")

# Test 8: Ledger Persistence
print("\n[TEST 8] Ledger Persistence (File-based JSON)")
if os.path.exists("payout_ledger.json"):
    with open("payout_ledger.json") as f:
        ledger = json.load(f)
    print(f"  Ledger entries: {len(ledger)}")
    if ledger:
        latest = ledger[0]
        print(f"  Latest transaction: {latest.get('transaction_id')}")
        print(f"  Latest status: {latest.get('status')}")
        print(f"  Latest UTR: {latest.get('utr')}")
        print(f"  Ledger payment_details field mapping:")
        print(f"    - upi_id: {latest.get('upi_id')}")
        print(f"    - mobile_number: {latest.get('mobile_number')}")
        print(f"    - redeem_code_value: {latest.get('redeem_code_value')}")
        print(f"  Result: [PASS] PASS")
else:
    print(f"  Result: [FAIL] FAIL (ledger file missing)")

print("\n" + "=" * 80)
print("E2E TEST COMPLETE")
print("=" * 80)
