import json
import re

print("=" * 80)
print("ACTUAL LEDGER FILE CONTENTS")
print("=" * 80)

with open('payout_ledger.json') as f:
    ledger = json.load(f)

for i, entry in enumerate(ledger, 1):
    txn = entry.get('transaction_id', '')
    match = re.match(r'TXN-(\d+)-', txn)
    short = '#' + match.group(1)[-4:] if match else 'NONE'
    device = entry.get('device_id', '')
    status = entry.get('status', '')
    print(f"{i}. {short:6} | {txn:25} | Device: {device:25} | Status: {status:10}")

print("\n" + "=" * 80)
print("FLASK ENDPOINT RESPONSE")
print("=" * 80 + "\n")

# Simulate what Flask returns
import sys
sys.path.insert(0, '.')
from app import app, build_request_payload, read_json_file, LEDGER_FILE, STORAGE_FILE

with app.app_context():
    vault = read_json_file(STORAGE_FILE)
    ledger = read_json_file(LEDGER_FILE, list)
    
    for i, entry in enumerate(ledger, 1):
        payload = build_request_payload(entry, vault)
        print(f"{i}. Request ID: {payload.get('request_id'):25} | Status: {payload.get('status'):10}")
