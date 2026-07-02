import json
import os

# Load current data
with open('payout_ledger.json', 'r') as f:
    ledger = json.load(f)

with open('user_vault.json', 'r') as f:
    vault = json.load(f)

print("=" * 80)
print("PRODUCTION DATA CLEANUP - ANALYSIS")
print("=" * 80)

# Identify test data patterns
test_device_patterns = [
    'test-device-workflow-001',
    'test-device-backend-001',
    'test-device-e2e-',
    'sync-test-device-001',
    'trace-device-',
    'trace-android-',
    'android-contract-',
    'android-real-',
    'test-e2e-001',
    'test-ratelimit',
]

test_uid_patterns = [
    'KL-SSYD-CBM8',      # test-device-e2e-1782809210
    'KL-X5JH-JCPA',      # test-device-backend-001
    'KL-5HUY-ZPX4',      # sync-test-device-001
    'KL-UYKE-QA39',      # test
    'KL-57C4-DZ5V',      # test-device-workflow-001
    'KL-26FX-CMDL',      # test-device-e2e-1782883737
    'KL-PLXJ-50YS',      # test-device-e2e-1782884192
    'KL-OOJL-PW6I',      # trace-device-1782885765
    'KL-TRACE-5803',     # trace-device-funded-1782885803
    'KL-ANDROID-5946',   # trace-android-shape-1782885946
    'KL-ANDROID-8187',   # android-contract-1782888187
    'KL-ANDROID-8258',   # android-contract-1782888258
    'KL-REAL-0519',      # android-real-1782890519
    'KL-TEST-E2E1',      # test-e2e-001
    'KL-RATELIMIT',      # test-ratelimit
]

# Filter ledger - keep only real entries
def is_test_entry(entry):
    device_id = entry.get('device_id', '')
    master_uid = entry.get('master_uid', '')
    
    for pattern in test_device_patterns:
        if pattern in device_id:
            return True
    
    if master_uid in test_uid_patterns:
        return True
    
    return False

# Filter vault - keep only real users
def is_test_user(device_id, user_data):
    master_uid = user_data.get('master_uid', '')
    
    for pattern in test_device_patterns:
        if pattern in device_id:
            return True
    
    if master_uid in test_uid_patterns:
        return True
    
    return False

# Perform cleanup
test_ledger_entries = [e for e in ledger if is_test_entry(e)]
clean_ledger = [e for e in ledger if not is_test_entry(e)]

test_users = {k: v for k, v in vault.items() if is_test_user(k, v)}
clean_vault = {k: v for k, v in vault.items() if not is_test_user(k, v)}

print("\nLEDGER ANALYSIS:")
print(f"  Total entries: {len(ledger)}")
print(f"  Test entries: {len(test_ledger_entries)}")
print(f"  Real entries: {len(clean_ledger)}")
print(f"\n  Test entries to remove:")
for entry in test_ledger_entries:
    print(f"    - {entry['transaction_id']:20} | {entry['device_id']:30} | {entry['master_uid']}")

print("\n" + "=" * 80)
print("USER VAULT ANALYSIS:")
print(f"  Total users: {len(vault)}")
print(f"  Test users: {len(test_users)}")
print(f"  Real users: {len(clean_vault)}")
print(f"\n  Test users to remove:")
for device_id, user_data in test_users.items():
    print(f"    - {device_id:30} | {user_data.get('master_uid')}")

print("\n" + "=" * 80)
print("REAL USERS THAT WILL REMAIN:")
for device_id, user_data in clean_vault.items():
    balance = user_data.get('coin_balance', 0)
    print(f"    - {device_id:30} | {user_data.get('master_uid'):20} | Balance: {balance}")

print("\n" + "=" * 80)
print("\nPROCEEDING WITH CLEANUP...")
print("=" * 80)

# Write cleaned data
with open('payout_ledger.json', 'w') as f:
    json.dump(clean_ledger, f, indent=4)
    print(f"\n✅ Cleaned payout_ledger.json: {len(test_ledger_entries)} test entries removed")

with open('user_vault.json', 'w') as f:
    json.dump(clean_vault, f, indent=4)
    print(f"✅ Cleaned user_vault.json: {len(test_users)} test users removed")

# Create backup of cleaned data
with open('payout_ledger_clean_backup.json', 'w') as f:
    json.dump(clean_ledger, f, indent=4)
    print(f"✅ Created backup: payout_ledger_clean_backup.json")

with open('user_vault_clean_backup.json', 'w') as f:
    json.dump(clean_vault, f, indent=4)
    print(f"✅ Created backup: user_vault_clean_backup.json")

print("\n" + "=" * 80)
print("CLEANUP COMPLETE")
print("=" * 80)
print(f"\nProduction ledger now contains only {len(clean_ledger)} real entries")
print(f"Production vault now contains only {len(clean_vault)} real users")
