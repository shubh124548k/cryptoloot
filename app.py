from flask import Flask, request, jsonify
from flask_cors import CORS
from datetime import datetime, timedelta
import pytz
import threading
import json
import os
import random
import string

app = Flask(__name__)
CORS(app) 

MAXIMUM_DAILY_ADS = 100
MAXIMUM_SESSION_ADS = 20
SESSION_COOLDOWN_MINUTES = 40
STORAGE_FILE = "user_vault.json"
LEDGER_FILE = "payout_ledger.json"

file_lock = threading.Lock()
ist_timezone = pytz.timezone('Asia/Kolkata')

REWARD_PACKS = {
    "1": {"coins": 300, "rupees": 10},
    "2": {"coins": 600, "rupees": 20},
    "3": {"coins": 900, "rupees": 30},
    "4": {"coins": 1200, "rupees": 40},
    "5": {"coins": 1500, "rupees": 50},
    "6": {"coins": 3000, "rupees": 100},
    "7": {"coins": 6000, "rupees": 200},
    "8": {"coins": 15000, "rupees": 500},
    "9": {"coins": 24000, "rupees": 800}
}

def read_json_file(filename):
    with file_lock:
        if not os.path.exists(filename):
            with open(filename, "w") as f:
                json.dump({} if filename == STORAGE_FILE else [], f)
            return {} if filename == STORAGE_FILE else []
        try:
            with open(filename, "r") as f:
                return json.load(f)
        except Exception:
            return {} if filename == STORAGE_FILE else []

def write_json_file(filename, data):
    with file_lock:
        with open(filename, "w") as f:
            json.dump(data, f, indent=4)

def get_seconds_until_india_midnight():
    now_ist = datetime.now(ist_timezone)
    tomorrow_midnight = (now_ist + timedelta(days=1)).replace(hour=0, minute=0, second=0, microsecond=0)
    return int((tomorrow_midnight - now_ist).total_seconds())

@app.route('/api/v1/auth/device-handshake', methods=['POST'])
def device_handshake():
    data = request.json or {}
    device_id = data.get("device_id")
    if not device_id:
        return jsonify({"status": "error", "message": "Missing hardware signature."}), 400

    vault = read_json_file(STORAGE_FILE)
    now_ist = datetime.now(ist_timezone)
    today_date = now_ist.strftime('%Y-%m-%d')

    if device_id not in vault:
        part1 = ''.join(random.choices(string.ascii_uppercase + string.digits, k=4))
        part2 = ''.join(random.choices(string.ascii_uppercase + string.digits, k=4))
        vault[device_id] = {
            "master_uid": f"KL-{part1}-{part2}",
            "coin_balance": 0,
            "trust_score": 100,
            "account_status": "ACTIVE",
            "ads_today": 0,
            "session_ads": 0,
            "last_calendar_reset": today_date
        }
        write_json_file(STORAGE_FILE, vault)

    user = vault[device_id]
    if user.get("last_calendar_reset") != today_date:
        user["ads_today"] = 0
        user["session_ads"] = 0
        user["last_calendar_reset"] = today_date
        write_json_file(STORAGE_FILE, vault)

    seconds_left = get_seconds_until_india_midnight() if user["ads_today"] >= MAXIMUM_DAILY_ADS else 0

    return jsonify({
        "status": "success",
        "master_uid": user["master_uid"],
        "coin_balance": user["coin_balance"],
        "trust_score": user["trust_score"],
        "operational_status": user["account_status"],
        "ads_today": user["ads_today"],
        "session_ads": user["session_ads"],
        "daily_reset_seconds_remaining": seconds_left
    })

@app.route('/api/v1/rewards/redeem', methods=['POST'])
def process_coin_redemption():
    data = request.json or {}
    device_id = data.get("device_id")
    tier_id = str(data.get("tier_id"))

    if not device_id or tier_id not in REWARD_PACKS:
        return jsonify({"status": "error", "message": "Invalid configuration parameters."}), 400

    vault = read_json_file(STORAGE_FILE)
    if device_id not in vault:
        return jsonify({"status": "error", "message": "User profile missing."}), 404

    user = vault[device_id]
    pack = REWARD_PACKS[tier_id]
    if user["coin_balance"] < pack["coins"]:
        return jsonify({"status": "error", "message": "Insufficient coin balance."}), 400

    user["coin_balance"] -= pack["coins"]
    write_json_file(STORAGE_FILE, vault)

    ledger = read_json_file(LEDGER_FILE)
    txn_id = f"TXN-{int(datetime.now().timestamp())}-{''.join(random.choices(string.ascii_uppercase, k=3))}"
    
    payout_entry = {
        "transaction_id": txn_id,
        "device_id": device_id,
        "master_uid": user["master_uid"],
        "tier_id": tier_id,
        "payout_value_rupees": pack["rupees"],
        "status": "PENDING",
        "redeem_code": "PROCESSING_BY_ADMIN",
        "timestamp": datetime.now(ist_timezone).isoformat()
    }
    ledger.append(payout_entry)
    write_json_file(LEDGER_FILE, ledger)

    return jsonify({"status": "success", "new_balance": user["coin_balance"], "transaction_id": txn_id})

@app.route('/api/v1/admin/view-payouts', methods=['GET'])
def admin_view_payouts():
    return jsonify(read_json_file(LEDGER_FILE))

@app.route('/api/v1/admin/fulfill', methods=['POST'])
def admin_fulfill_payout():
    data = request.json or {}
    target_txn_id = data.get("transaction_id")
    real_voucher_string = data.get("redeem_code")

    if not target_txn_id or not real_voucher_string:
        return jsonify({"status": "error", "message": "Missing input fields."}), 400

    ledger = read_json_file(LEDGER_FILE)
    found = False
    for entry in ledger:
        if entry["transaction_id"] == target_txn_id and entry["status"] == "PENDING":
            entry["status"] = "COMPLETED"
            entry["redeem_code"] = real_voucher_string
            found = True
            break

    if not found:
        return jsonify({"status": "error", "message": "Transaction record not found."}), 404

    write_json_file(LEDGER_FILE, ledger)
    return jsonify({"status": "success"})

if __name__ == '__main__':
    import os
    port = int(os.environ.get("PORT", 5000))
    app.run(host='0.0.0.0', port=port, debug=False)
