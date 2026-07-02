import json

with open('payout_ledger.json', 'r') as f:
    ledger = json.load(f)

print(f"Total entries: {len(ledger)}\n")
print("Device IDs and UIDs:")
for entry in ledger:
    print(f"  {entry['device_id']:30} | {entry['master_uid']:20} | {entry['status']}")

test_patterns = ['test', 'trace', 'android-', 'KL-TEST', 'KL-RATELIMIT']
test_entries = [e for e in ledger if any(p.lower() in e['device_id'].lower() or p in e['master_uid'] for p in test_patterns)]
print(f"\nTest entries found: {len(test_entries)}")
for e in test_entries:
    print(f"  {e['device_id']} | {e['master_uid']}")
    
real_entries = [e for e in ledger if e not in test_entries]
print(f"\nReal entries remaining: {len(real_entries)}")
for e in real_entries:
    print(f"  {e['device_id']} | {e['master_uid']}")
