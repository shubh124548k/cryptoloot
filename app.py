from flask import Flask, request, jsonify, session, send_file
from flask_cors import CORS
from werkzeug.security import generate_password_hash, check_password_hash
from datetime import datetime, timedelta
import pytz
import threading
import json
import os
import random
import string
import logging
import sys
import traceback

# Production logging configuration
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.StreamHandler(sys.stdout),
        logging.FileHandler('app.log', mode='w')
    ]
)
logger = logging.getLogger(__name__)

app = Flask(__name__)
CORS(app)

# Production security configuration
app.secret_key = os.environ.get("ADMIN_SECRET_KEY", "change-me-in-production-please-use-env-var")
app.config["SESSION_COOKIE_HTTPONLY"] = True
app.config["SESSION_COOKIE_SAMESITE"] = "Lax"
app.config["SESSION_COOKIE_SECURE"] = os.environ.get("FLASK_ENV") == "production"
app.config["PERMANENT_SESSION_LIFETIME"] = timedelta(hours=8)

MAXIMUM_DAILY_ADS = 100
MAXIMUM_SESSION_ADS = 20
SESSION_COOLDOWN_MINUTES = 40
STORAGE_FILE = os.environ.get("STORAGE_FILE", "user_vault.json")
LEDGER_FILE = os.environ.get("LEDGER_FILE", "payout_ledger.json")
NOTIFICATIONS_FILE = os.environ.get("NOTIFICATIONS_FILE", "notifications.json")
AUDIT_LOG_FILE = os.environ.get("AUDIT_LOG_FILE", "audit_logs.json")
SETTINGS_FILE = os.environ.get("SETTINGS_FILE", "settings.json")
DEFAULT_SETTINGS = {
    "monthly_budget": 10000,
    "reward_pool_target": 10000,
}

file_lock = threading.Lock()
ist_timezone = pytz.timezone("Asia/Kolkata")
active_admin_sessions = set()
SERVER_STARTUP_TIME = datetime.now(ist_timezone).isoformat()
BACKEND_PID = os.getpid()

logger.info(f"Initializing KryptoLoot Admin Backend")
logger.info(f"Storage: {STORAGE_FILE}, Ledger: {LEDGER_FILE}, Notifications: {NOTIFICATIONS_FILE}, Audit: {AUDIT_LOG_FILE}")

REWARD_PACKS = {
    "1": {"coins": 300, "rupees": 10, "name": "Bronze Pack"},
    "2": {"coins": 700, "rupees": 25, "name": "Silver Pack"},
    "3": {"coins": 1500, "rupees": 50, "name": "Gold Pack"},
    "4": {"coins": 3500, "rupees": 120, "name": "Diamond Pack"},
    "5": {"coins": 7000, "rupees": 250, "name": "Elite Pack"},
}

ADMIN_USERNAME = os.environ.get("ADMIN_USERNAME", "subhendugupta124548k")
ADMIN_PASSWORD_HASH = os.environ.get("ADMIN_PASSWORD_HASH") or generate_password_hash(
    os.environ.get("ADMIN_PASSWORD", "KryptoLoot@2026")
)


def read_json_file(filename, default_factory=None):
    with file_lock:
        if not os.path.exists(filename):
            default = {} if filename == STORAGE_FILE else []
            if default_factory is not None:
                default = default_factory()
            with open(filename, "w") as f:
                json.dump(default, f)
            return default
        try:
            with open(filename, "r") as f:
                return json.load(f)
        except Exception:
            default = {} if filename == STORAGE_FILE else []
            if default_factory is not None:
                default = default_factory()
            return default


def write_json_file(filename, data):
    with file_lock:
        with open(filename, "w") as f:
            json.dump(data, f, indent=4)


def read_settings_file():
    return read_json_file(SETTINGS_FILE, lambda: DEFAULT_SETTINGS.copy())


def write_settings_file(settings):
    write_json_file(SETTINGS_FILE, settings)


def get_seconds_until_india_midnight():
    now_ist = datetime.now(ist_timezone)
    tomorrow_midnight = (now_ist + timedelta(days=1)).replace(hour=0, minute=0, second=0, microsecond=0)
    return int((tomorrow_midnight - now_ist).total_seconds())


def timestamp_string(dt=None):
    base = dt or datetime.now(ist_timezone)
    return base.strftime("%Y-%m-%d %H:%M:%S")


STATUS_ALIAS = {
    "APPROVED": "PROCESSING",
}


def normalize_status(raw_status):
    if not raw_status:
        return "PENDING"
    status = str(raw_status).strip().upper()
    return STATUS_ALIAS.get(status, status)


def get_pack_info(tier_id):
    return REWARD_PACKS.get(str(tier_id), {})


def find_pack_by_coins(coins):
    for tier_id, pack in REWARD_PACKS.items():
        if pack.get("coins") == coins:
            return tier_id, pack
    return None, {}


def derive_request_id_from_txn(txn_id):
    digits = "".join(ch for ch in str(txn_id) if ch.isdigit())
    if not digits:
        return 0
    try:
        return int(digits)
    except ValueError:
        return int(digits[:9]) if len(digits) > 9 else 0


def build_request_payload(entry, vault):
    user = vault.get(entry.get("device_id", ""), {})
    status = normalize_status(entry.get("status", "PENDING"))
    payment_method = entry.get("payment_method", "UPI")
    payment_details = entry.get("upi_id") or entry.get("mobile_number") or entry.get("redeem_code") or ""
    tier_id = str(entry.get("tier_id", ""))
    pack_info = get_pack_info(tier_id)
    reward_pack_name = pack_info.get("name") or f"Pack {tier_id}"
    transaction_id = entry.get("transaction_id") or ""
    request_id_numeric = derive_request_id_from_txn(transaction_id)
    return {
        "request_id": transaction_id,
        "transaction_id": transaction_id,
        "request_id_numeric": request_id_numeric,
        "username": user.get("display_name") or user.get("username") or user.get("master_uid", entry.get("master_uid", "unknown")),
        "user_uid": entry.get("device_id", ""),
        "reward_pack": reward_pack_name,
        "coins": entry.get("coins", pack_info.get("coins", 0)),
        "cash_amount": entry.get("payout_value_rupees", pack_info.get("rupees", 0)),
        "payment_method": payment_method,
        "payment_details": payment_details,
        "payment_reference": entry.get("utr") or entry.get("redeem_code") or "",
        "created_at": entry.get("timestamp"),
        "updated_at": entry.get("updated_at") or entry.get("timestamp"),
        "status": status,
        "reject_reason": entry.get("reject_reason"),
        "approved_by": entry.get("approved_by"),
        "paid_by": entry.get("paid_by"),
        "paid_time": entry.get("paid_time"),
        "completed_time": entry.get("completed_time"),
        "utr": entry.get("utr"),
    }


def build_redemption_history_item(entry):
    tier_id = str(entry.get("tier_id", ""))
    pack_info = get_pack_info(tier_id)
    transaction_id = entry.get("transaction_id", "")
    digits = "".join([ch for ch in str(transaction_id) if ch.isdigit()])
    if digits:
        try:
            request_id = int(digits)
        except ValueError:
            request_id = 0
    else:
        request_id = 0
        created_at = entry.get("timestamp") or entry.get("updated_at")
        if created_at:
            try:
                request_id = int(datetime.fromisoformat(created_at).timestamp())
            except Exception:
                request_id = 0

    status = normalize_status(entry.get("status", "PENDING"))
    created_at = entry.get("timestamp") or entry.get("updated_at") or ""
    completed_at = entry.get("completed_time") or entry.get("paid_time")
    return {
        "request_id": request_id,
        "coin_cost": entry.get("coins", pack_info.get("coins", 0)),
        "payout_value": float(entry.get("payout_value_rupees", pack_info.get("rupees", 0))),
        "status": status,
        "code_value": entry.get("redeem_code") if status == "COMPLETED" else entry.get("redeem_code"),
        "created_at": created_at,
        "transaction_type": "REDEEM",
        "description": entry.get("payment_method", "") and f"Redeem request via {entry.get('payment_method')}" or "Redeem request",
        "queue_id": entry.get("transaction_id"),
        "coins_before": entry.get("coins_before", 0),
        "coins_after": entry.get("coins_after", 0),
        "completed_at": completed_at,
        "device_id": entry.get("device_id"),
        "server_synced": False,
        "version_number": 1,
        "reward_name": pack_info.get("name") or f"Pack {tier_id}",
        "cash_amount": float(entry.get("payout_value_rupees", pack_info.get("rupees", 0)))
    }


def create_notification(request_id, title, message, status, reward_pack):
    notifications = read_json_file(NOTIFICATIONS_FILE, list)
    if any(item.get("request_id") == request_id and item.get("status") == status for item in notifications):
        return notifications
    notification = {
        "id": f"notif-{request_id}-{status.lower()}",
        "request_id": request_id,
        "title": title,
        "message": message,
        "status": status,
        "reward_pack": reward_pack,
        "timestamp": timestamp_string(),
        "is_read": False,
    }
    notifications.insert(0, notification)
    write_json_file(NOTIFICATIONS_FILE, notifications)
    return notifications


def log_admin_action(admin_username, request_id, old_status, new_status, action, reason=None, utr=None):
    logs = read_json_file(AUDIT_LOG_FILE, list)
    logs.insert(0, {
        "id": f"audit-{request_id}-{len(logs) + 1}",
        "admin_username": admin_username,
        "request_id": request_id,
        "old_status": old_status,
        "new_status": new_status,
        "timestamp": timestamp_string(),
        "action": action,
        "reason": reason,
        "utr": utr,
        "ip_address": request.remote_addr or "",
    })
    write_json_file(AUDIT_LOG_FILE, logs)
    return logs


def check_redeem_rate_limits(device_id, user, tier_id):
    """
    Check rate limits for redeem requests:
    - Max 3 redeems per day
    - Max 10 redeems per month
    - No duplicate pending redeems for same tier
    - Max 1 redeem per hour per tier
    """
    try:
        ledger = read_json_file(LEDGER_FILE, list)
        user_redeems = [entry for entry in ledger if entry.get("device_id") == device_id]
        
        today = datetime.now(ist_timezone).strftime("%Y-%m-%d")
        month = datetime.now(ist_timezone).strftime("%Y-%m")
        hour_ago = datetime.now(ist_timezone) - timedelta(hours=1)
        
        # Check daily limit (3 per day)
        today_redeems = [r for r in user_redeems if r.get("timestamp", "").startswith(today)]
        if len(today_redeems) >= 3:
            return {"allowed": False, "reason": "Daily redeem limit (3) reached. Try again tomorrow."}
        
        # Check monthly limit (10 per month)
        month_redeems = [r for r in user_redeems if r.get("timestamp", "").startswith(month)]
        if len(month_redeems) >= 10:
            return {"allowed": False, "reason": "Monthly redeem limit (10) reached. Try again next month."}
        
        # Check for duplicate pending redeem with same tier
        pending_same_tier = [
            r for r in user_redeems 
            if r.get("status", "").upper() in ["PENDING", "PROCESSING"] and r.get("tier_id") == str(tier_id)
        ]
        if pending_same_tier:
            return {"allowed": False, "reason": "A pending redeem for this pack already exists."}
        
        # Check hourly cooldown per tier
        recent_same_tier = [
            r for r in user_redeems 
            if r.get("tier_id") == str(tier_id) and 
            (datetime.fromisoformat(r.get("timestamp", "")) > hour_ago if r.get("timestamp") else False)
        ]
        if recent_same_tier:
            return {"allowed": False, "reason": "Please wait at least 1 hour before redeeming this pack again."}
        
        return {"allowed": True, "reason": "Rate limits OK"}
    except Exception as e:
        logger.error(f"Error checking rate limits: {e}")
        return {"allowed": True, "reason": "Rate limit check skipped due to error"}


def require_admin():
    if not session.get("admin_authenticated"):
        return None
    session["last_active"] = timestamp_string()
    return session.get("admin_username")


@app.route('/api/debug/storage', methods=['GET'])
def debug_storage():
    ledger_path = os.path.abspath(LEDGER_FILE)
    vault_path = os.path.abspath(STORAGE_FILE)
    return jsonify({
        "ledger_path": ledger_path,
        "vault_path": vault_path,
        "backend_pid": BACKEND_PID,
        "working_directory": os.getcwd(),
        "startup_time": SERVER_STARTUP_TIME,
    })


@app.route("/admin")
def admin_root():
    return send_file("admin.html")


@app.route("/admin/")
def admin_root_slash():
    return send_file("admin.html")


@app.route('/api/v1/auth/handshake', methods=['POST'])
def auth_handshake():
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
            "last_calendar_reset": today_date,
        }
        write_json_file(STORAGE_FILE, vault)

    user = vault[device_id]
    if user.get("last_calendar_reset") != today_date:
        user["ads_today"] = 0
        user["session_ads"] = 0
        user["last_calendar_reset"] = today_date
        write_json_file(STORAGE_FILE, vault)

    if data.get("is_vpn_flag") and user.get("trust_score", 0) >= 100:
        user["trust_score"] = max(user.get("trust_score", 100) - 25, 0)
    if data.get("is_emulator_flag") and user.get("trust_score", 0) >= 75:
        user["trust_score"] = max(user.get("trust_score", 100) - 25, 0)
    write_json_file(STORAGE_FILE, vault)

    seconds_left = get_seconds_until_india_midnight() if user["ads_today"] >= MAXIMUM_DAILY_ADS else 0

    return jsonify({
        "status": "success",
        "device_id": device_id,
        "current_balance": user["coin_balance"],
        "trust_score": user["trust_score"],
        "operational_status": user["account_status"],
        "ads_today": user["ads_today"],
        "session_ads": user["session_ads"],
        "break_until": None,
        "daily_cap": MAXIMUM_DAILY_ADS,
        "session_cap": MAXIMUM_SESSION_ADS,
        "coins_per_ad": 10,
        "min_redeem": 300
    })


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
            "last_calendar_reset": today_date,
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
        "daily_reset_seconds_remaining": seconds_left,
    })


@app.route('/api/v1/ads/log-completion', methods=['POST'])
def log_ad_completion():
    data = request.json or {}
    device_id = data.get("device_id")
    time_spent_seconds = data.get("time_spent_seconds")
    logger.info(f"device_id = {device_id}")
    logger.info(f"time_spent_seconds = {time_spent_seconds}")

    if not device_id:
        return jsonify({"status": "error", "message": "Missing device_id."}), 400
    if not isinstance(time_spent_seconds, int):
        return jsonify({"status": "error", "message": "Invalid ad completion payload."}), 400
    if time_spent_seconds < 10:
        return jsonify({"status": "error", "message": "Ad watch duration too short to award coins."}), 400

    vault = read_json_file(STORAGE_FILE)
    if device_id not in vault:
        return jsonify({"status": "error", "message": "User profile missing."}), 404

    user = vault[device_id]
    if user.get("account_status", "ACTIVE") != "ACTIVE":
        return jsonify({"status": "error", "message": "Account not active."}), 403

    now_ist = datetime.now(ist_timezone)
    today_date = now_ist.strftime('%Y-%m-%d')
    if user.get("last_calendar_reset") != today_date:
        user["ads_today"] = 0
        user["session_ads"] = 0
        user["last_calendar_reset"] = today_date

    if user.get("ads_today", 0) >= MAXIMUM_DAILY_ADS:
        return jsonify({"status": "error", "message": "Daily ad limit reached."}), 429
    if user.get("session_ads", 0) >= MAXIMUM_SESSION_ADS:
        return jsonify({"status": "error", "message": "Session ad limit reached."}), 429

    last_ad_timestamp = user.get("last_ad_timestamp", 0)
    last_ad_duration = user.get("last_ad_duration", 0)
    current_ts = int(now_ist.timestamp())
    if last_ad_duration == time_spent_seconds and current_ts - int(last_ad_timestamp) < 20:
        return jsonify({"status": "error", "message": "Duplicate ad completion detected."}), 409

    coins_earned = 10
    user["coin_balance"] = user.get("coin_balance", 0) + coins_earned
    user["ads_today"] = user.get("ads_today", 0) + 1
    user["session_ads"] = user.get("session_ads", 0) + 1
    user["lifetime_coins"] = user.get("lifetime_coins", 0) + coins_earned
    user["total_coins_earned"] = user.get("total_coins_earned", 0) + coins_earned
    user["total_ads_watched"] = user.get("total_ads_watched", 0) + 1
    user["last_ad_timestamp"] = current_ts
    user["last_ad_duration"] = time_spent_seconds

    old_coin_balance = user.get("coin_balance", 0) - coins_earned
    new_coin_balance = user.get("coin_balance", 0)
    logger.info(f"old coin_balance = {old_coin_balance}")
    logger.info(f"new coin_balance = {new_coin_balance}")
    write_json_file(STORAGE_FILE, vault)

    seconds_left = get_seconds_until_india_midnight() if user["ads_today"] >= MAXIMUM_DAILY_ADS else 0
    response_payload = jsonify({
        "status": "success",
        "success": True,
        "coins_earned": coins_earned,
        "new_balance": user["coin_balance"],
        "ads_today": user["ads_today"],
        "session_ads": user["session_ads"],
        "break_until": None,
        "trust_score": user["trust_score"],
        "message": "Ad reward credited successfully.",
        "master_uid": user["master_uid"],
        "coin_balance": user["coin_balance"],
        "operational_status": user["account_status"],
        "daily_reset_seconds_remaining": seconds_left,
    })

    logger.info(f"HTTP response = {response_payload.get_data(as_text=True)}")
    return response_payload


@app.route('/api/v1/users/<device_id>/redemptions', methods=['GET'])
def get_redemptions_for_user(device_id):
    ledger = read_json_file(LEDGER_FILE, list)
    entries = [entry for entry in ledger if entry.get("device_id") == device_id]
    history = [build_redemption_history_item(entry) for entry in entries]
    return jsonify(history)


@app.route('/api/v1/rewards/redeem', methods=['POST'])
def process_coin_redemption():
    try:
        data = request.json or {}
        logger.error("========== REDEEM REQUEST START ==========")
        logger.error(f"Incoming JSON: {data}")
        logger.info(f"REDEEM REQUEST: {request.remote_addr} {data}")
        device_id = data.get("device_id")
        tier_id = data.get("tier_id")
        coins_to_redeem = data.get("coins_to_redeem")
        reward_pack = data.get("reward_pack")
        pack = None
        selected_tier = None

        if tier_id is not None:
            selected_tier = str(tier_id)
            pack = get_pack_info(selected_tier)
        elif isinstance(coins_to_redeem, int):
            selected_tier, pack = find_pack_by_coins(coins_to_redeem)
        
        logger.info(f"device_id = {device_id}")
        logger.info(f"tier_id = {tier_id}")
        logger.info(f"coins_to_redeem = {coins_to_redeem} ({type(coins_to_redeem).__name__})")
        logger.info(f"reward_pack = {reward_pack}")
        logger.info(f"selected_tier = {selected_tier}")
        logger.info(f"pack = {pack}")

        logger.error("REDEEM REJECT: Invalid configuration parameters")
        logger.error(f"device_id={device_id}")
        logger.error(f"tier_id={tier_id}")
        logger.error(f"coins_to_redeem={coins_to_redeem}")
        logger.error(f"selected_tier={selected_tier}")
        logger.error(f"pack={pack}")

        if not device_id or not pack:
            logger.error("========== REDEEM DEBUG ==========")
            logger.error(f"device_id={device_id}")
            logger.error(f"user_exists={device_id in vault if 'vault' in locals() else 'N/A'}")
            logger.error(f"coins_to_redeem={coins_to_redeem}")
            logger.error(f"selected_tier={selected_tier}")
            logger.error(f"pack={pack}")
            logger.error("RETURNING 400 : Invalid configuration")
            logger.error("=" * 80)
            logger.error("RETURN EXECUTED")
            logger.error("HTTP STATUS = 400")
            logger.error(f"RETURN VALUE = {{'status': 'error', 'message': 'Invalid configuration parameters.'}}")
            return jsonify({"status": "error", "message": "Invalid configuration parameters."}), 400

        vault = read_json_file(STORAGE_FILE)
        logger.error("REDEEM REJECT: User profile missing")
        logger.error(f"device_id={device_id}")
        logger.error(f"user_exists={device_id in vault}")
        if device_id not in vault:
            logger.error("========== REDEEM DEBUG ==========")
            logger.error(f"device_id={device_id}")
            logger.error(f"user_exists={device_id in vault if 'vault' in locals() else 'N/A'}")
            logger.error(f"coins_to_redeem={coins_to_redeem}")
            logger.error(f"selected_tier={selected_tier}")
            logger.error(f"pack={pack}")
            logger.error("RETURNING 404 : User missing")
            logger.error("=" * 80)
            logger.error("RETURN EXECUTED")
            logger.error("HTTP STATUS = 404")
            logger.error(f"RETURN VALUE = {{'status': 'error', 'message': 'User profile missing.'}}")
            return jsonify({"status": "error", "message": "User profile missing."}), 404

        user = vault[device_id]
        logger.error(f"backend_balance={user.get('coin_balance')}")
        logger.error(f"required_coins={pack['coins']}")
        if user["coin_balance"] < pack["coins"]:
            logger.error("========== REDEEM DEBUG ==========")
            logger.error(f"device_id={device_id}")
            logger.error(f"user_exists={device_id in vault if 'vault' in locals() else 'N/A'}")
            logger.error(f"coins_to_redeem={coins_to_redeem}")
            logger.error(f"selected_tier={selected_tier}")
            logger.error(f"pack={pack}")
            logger.error(f"backend_balance={user.get('coin_balance')}")
            logger.error(f"required_balance={pack.get('coins') if pack else None}")
            logger.error("RETURNING 400 : Insufficient balance")
            logger.error("REDEEM REJECT: Insufficient coin balance")
            logger.error("=" * 80)
            logger.error("RETURN EXECUTED")
            logger.error("HTTP STATUS = 400")
            logger.error(f"RETURN VALUE = {{'status': 'error', 'message': 'Insufficient coin balance.'}}")
            return jsonify({"status": "error", "message": "Insufficient coin balance."}), 400

        # === RATE LIMITING CHECKS ===
        rate_limit_check = check_redeem_rate_limits(device_id, user, selected_tier)
        if not rate_limit_check["allowed"]:
            logger.warning(f"Rate limit triggered for {device_id}: {rate_limit_check['reason']}")
            return jsonify({"status": "error", "message": rate_limit_check["reason"]}), 429

        user["coin_balance"] -= pack["coins"]
        write_json_file(STORAGE_FILE, vault)

        ledger = read_json_file(LEDGER_FILE, list)
        txn_id = f"TXN-{int(datetime.now().timestamp())}-{''.join(random.choices(string.ascii_uppercase, k=3))}"

        payment_method = data.get("payment_method", "UPI")
        payment_details = data.get("payment_details")
        if payment_details is None:
            payment_details = data.get("upi_id") or data.get("mobile_number") or data.get("redeem_code_value")

        payout_entry = {
            "transaction_id": txn_id,
            "device_id": device_id,
            "master_uid": user["master_uid"],
            "username": user.get("display_name") or user.get("username") or user["master_uid"],
            "tier_id": selected_tier,
            "coins": pack["coins"],
            "payout_value_rupees": pack["rupees"],
            "status": "PENDING",
            "redeem_code": "PROCESSING_BY_ADMIN",
            "payment_method": payment_method,
            "upi_id": payment_details if payment_method.upper() == "UPI" else None,
            "mobile_number": payment_details if payment_method.upper() == "MOBILE" else None,
            "redeem_code_value": payment_details if payment_method.upper() == "REDEEM_CODE" else None,
            "timestamp": datetime.now(ist_timezone).isoformat(),
            "updated_at": datetime.now(ist_timezone).isoformat(),
        }
        logger.error("Redeem validation passed")
        logger.error("Writing payout entry to payout_ledger.json")
        logger.error(payout_entry)
        ledger.append(payout_entry)
        write_json_file(LEDGER_FILE, ledger)
        logger.error("Ledger successfully written")

        create_notification(
            txn_id,
            "Redeem Submitted",
            f"Request {txn_id} is pending approval.",
            "PENDING",
            pack.get("name", f"Pack {selected_tier}"),
        )

        logger.error("=" * 80)
        logger.error("RETURN EXECUTED")
        logger.error("HTTP STATUS = 200")
        logger.error(f"RETURN VALUE = {{'status': 'success', 'success': True, 'request_id': derive_request_id_from_txn(txn_id), 'transaction_id': txn_id, 'coins_deducted': pack['coins'], 'coins_remaining': user['coin_balance'], 'payout_value': float(pack['rupees']), 'currency': 'INR', 'status': 'QUEUED', 'message': 'Redeem request submitted successfully.', 'estimated_delivery': 'Awaiting admin approval'}}")
        return jsonify({
            "status": "success",
            "success": True,
            "request_id": derive_request_id_from_txn(txn_id),
            "transaction_id": txn_id,
            "coins_deducted": pack["coins"],
            "coins_remaining": user["coin_balance"],
            "payout_value": float(pack["rupees"]),
            "currency": "INR",
            "status": "QUEUED",
            "message": "Redeem request submitted successfully.",
            "estimated_delivery": "Awaiting admin approval",
        })

    except Exception:
        logger.error(traceback.format_exc())
        raise

    vault = read_json_file(STORAGE_FILE)
    if device_id not in vault:
        logger.error("=" * 80)
        logger.error("RETURN EXECUTED")
        logger.error("HTTP STATUS = 404")
        logger.error(f"RETURN VALUE = {{'status': 'error', 'message': 'User profile missing.'}}")
        return jsonify({"status": "error", "message": "User profile missing."}), 404

    user = vault[device_id]
    pack = REWARD_PACKS[tier_id]
    if user["coin_balance"] < pack["coins"]:
        logger.error("=" * 80)
        logger.error("RETURN EXECUTED")
        logger.error("HTTP STATUS = 400")
        logger.error(f"RETURN VALUE = {{'status': 'error', 'message': 'Insufficient coin balance.'}}")
        return jsonify({"status": "error", "message": "Insufficient coin balance."}), 400

    user["coin_balance"] -= pack["coins"]
    write_json_file(STORAGE_FILE, vault)

    ledger = read_json_file(LEDGER_FILE, list)
    txn_id = f"TXN-{int(datetime.now().timestamp())}-{''.join(random.choices(string.ascii_uppercase, k=3))}"

    payment_method = data.get("payment_method", "UPI")
    payment_details = data.get("payment_details")
    if payment_details is None:
        payment_details = data.get("upi_id") or data.get("mobile_number") or data.get("redeem_code_value")

    payout_entry = {
        "transaction_id": txn_id,
        "device_id": device_id,
        "master_uid": user["master_uid"],
        "tier_id": tier_id,
        "coins": pack["coins"],
        "payout_value_rupees": pack["rupees"],
        "status": "PENDING",
        "redeem_code": "PROCESSING_BY_ADMIN",
        "payment_method": payment_method,
        "upi_id": payment_details if payment_method.upper() == "UPI" else None,
        "mobile_number": payment_details if payment_method.upper() == "MOBILE" else None,
        "redeem_code_value": payment_details if payment_method.upper() == "REDEEM_CODE" else None,
        "timestamp": datetime.now(ist_timezone).isoformat(),
        "updated_at": datetime.now(ist_timezone).isoformat(),
    }
    ledger.append(payout_entry)
    write_json_file(LEDGER_FILE, ledger)

    create_notification(
        txn_id,
        "Redeem Submitted",
        f"Request {txn_id} is pending approval.",
        "PENDING",
        f"Pack {tier_id}",
    )

    logger.error("=" * 80)
    logger.error("RETURN EXECUTED")
    logger.error("HTTP STATUS = 200")
    logger.error(f"RETURN VALUE = {{'status': 'success', 'new_balance': user['coin_balance'], 'transaction_id': txn_id}}")
    return jsonify({"status": "success", "new_balance": user["coin_balance"], "transaction_id": txn_id})


@app.route('/api/v1/admin/login', methods=['POST'])
def admin_login():
    data = request.json or {}
    username = (data.get("username") or "").strip()
    password = (data.get("password") or "").strip()

    if not username or not password:
        logger.warning(f"Login attempt with missing credentials from {request.remote_addr}")
        return jsonify({"status": "error", "message": "Unauthorized access."}), 401

    # Verify credentials - server-side only
    username_match = (username == ADMIN_USERNAME)
    password_match = check_password_hash(ADMIN_PASSWORD_HASH, password)
    
    if username_match and password_match:
        session.clear()
        session.permanent = True
        session["admin_authenticated"] = True
        session["admin_username"] = username
        session["login_time"] = timestamp_string()
        active_admin_sessions.add(username)
        logger.info(f"Admin login successful: {username} from {request.remote_addr}")
        return jsonify({"status": "success", "username": username})

    logger.warning(f"Failed login attempt for user {username} from {request.remote_addr}")
    return jsonify({"status": "error", "message": "Unauthorized access."}), 401


@app.route('/api/v1/admin/logout', methods=['POST'])
def admin_logout():
    admin_name = session.get("admin_username")
    login_time = session.get("login_time")
    session.clear()
    if admin_name:
        active_admin_sessions.discard(admin_name)
        logger.info(f"Admin logout: {admin_name}")
    return jsonify({"status": "success"})


@app.route('/api/v1/admin/me', methods=['GET'])
def admin_me():
    username = require_admin()
    if not username:
        return jsonify({"status": "error", "authenticated": False}), 401
    return jsonify({"status": "success", "authenticated": True, "username": username})


@app.route('/_debug/creds', methods=['GET'])
def debug_creds():
    """DEBUG ENDPOINT - Only available in development"""
    if os.environ.get("FLASK_ENV") != "development":
        return jsonify({"status": "error", "message": "Not available in production."}), 403
    return jsonify({
        "admin_username": ADMIN_USERNAME,
        "admin_password_hash_start": str(ADMIN_PASSWORD_HASH)[:50] + "..."
    })


@app.route('/api/v1/admin/dashboard', methods=['GET'])
def admin_dashboard():
    username = require_admin()
    if not username:
        return jsonify({"status": "error", "message": "Unauthorized access."}), 401

    vault = read_json_file(STORAGE_FILE)
    ledger = read_json_file(LEDGER_FILE, list)
    notifications = read_json_file(NOTIFICATIONS_FILE, list)
    today = datetime.now(ist_timezone).strftime("%Y-%m-%d")

    pending = [entry for entry in ledger if normalize_status(entry.get("status", "PENDING")) == "PENDING"]
    processing = [entry for entry in ledger if normalize_status(entry.get("status", "PENDING")) == "PROCESSING"]
    rejected_today = [entry for entry in ledger if normalize_status(entry.get("status", "PENDING")) == "REJECTED" and str(entry.get("updated_at", "")).startswith(today)]
    completed_today = [entry for entry in ledger if normalize_status(entry.get("status", "PENDING")) == "COMPLETED" and str(entry.get("updated_at", "")).startswith(today)]
    completed_entries = [entry for entry in ledger if normalize_status(entry.get("status", "PENDING")) == "COMPLETED"]
    total_revenue = sum(entry.get("payout_value_rupees", 0) for entry in completed_entries)
    settings = read_settings_file()
    monthly_budget = settings.get("monthly_budget", DEFAULT_SETTINGS["monthly_budget"])
    reward_pool_target = settings.get("reward_pool_target", DEFAULT_SETTINGS["reward_pool_target"])
    completed_amount = sum(entry.get("payout_value_rupees", 0) for entry in completed_entries if str(entry.get("updated_at", "")).startswith(today))
    rejected_count = len([entry for entry in ledger if normalize_status(entry.get("status", "PENDING")) == "REJECTED"])
    total_processed = len(processing) + len(completed_entries) + rejected_count
    approval_rate = int(((len(processing) + len(completed_entries)) / total_processed) * 100) if total_processed else 0
    reject_rate = int((rejected_count / total_processed) * 100) if total_processed else 0

    return jsonify({
        "status": "success",
        "metrics": {
            "pending_requests": len(pending),
            "processing_requests": len(processing),
            "completed_today": len(completed_today),
            "rejected_today": len(rejected_today),
            "revenue": total_revenue,
            "todays_payout": completed_amount,
            "monthly_payout": total_revenue,
            "monthly_budget": monthly_budget,
            "reward_pool_target": reward_pool_target,
            "remaining_reward_pool": max(reward_pool_target - total_revenue, 0),
            "pending_amount": sum(entry.get("payout_value_rupees", 0) for entry in pending),
            "total_redeems": len(ledger),
            "todays_redeems": sum(1 for entry in ledger if str(entry.get("timestamp", "")).startswith(today)),
            "active_users": len([user for user in vault.values() if user.get("account_status") == "ACTIVE"]),
            "online_users": len(active_admin_sessions),
            "approval_rate": approval_rate,
            "reject_rate": reject_rate,
            "completed_amount": total_revenue,
        },
        "notifications": notifications[:8],
        "audit_log": read_json_file(AUDIT_LOG_FILE, list)[:8],
        "settings": settings,
    })


@app.route('/api/v1/admin/redeems', methods=['GET'])
def admin_redeems():
    username = require_admin()
    if not username:
        return jsonify({"status": "error", "message": "Unauthorized access."}), 401

    vault = read_json_file(STORAGE_FILE)
    ledger = read_json_file(LEDGER_FILE, list)
    status_filter = (request.args.get("status") or "all").upper()
    status_alias = {
        "APPROVED": "PROCESSING",
        "PROCESSING": "PROCESSING",
    }
    if status_filter in status_alias:
        status_filter = status_alias[status_filter]

    search = (request.args.get("search") or "").strip().lower()
    sort = request.args.get("sort") or "newest"

    items = [build_request_payload(entry, vault) for entry in ledger]
    if status_filter != "ALL":
        items = [item for item in items if item["status"].upper() == status_filter]
    if search:
        search = search.lower()
        items = [
            item for item in items
            if search in (item.get("request_id") or "").lower()
            or search in (item.get("transaction_id") or "").lower()
            or str(item.get("request_id_numeric") or "").lower() == search
            or search in (item.get("username") or "").lower()
            or search in (item.get("user_uid") or "").lower()
        ]

    if sort == "oldest":
        items = sorted(items, key=lambda item: item.get("created_at") or "")
    elif sort == "highest_amount":
        items = sorted(items, key=lambda item: item.get("cash_amount", 0), reverse=True)
    elif sort == "lowest_amount":
        items = sorted(items, key=lambda item: item.get("cash_amount", 0))
    else:
        items = sorted(items, key=lambda item: (item.get("created_at") or ""), reverse=True)

    return jsonify({"status": "success", "requests": items})


@app.route('/api/v1/admin/redeems/<request_id>', methods=['GET'])
def admin_get_redeem_details(request_id):
    """Fetch full details of a single redeem request"""
    username = require_admin()
    if not username:
        return jsonify({"status": "error", "message": "Unauthorized access."}), 401

    try:
        ledger = read_json_file(LEDGER_FILE, list)
        target = next(
            (
                entry
                for entry in ledger
                if str(entry.get("transaction_id") or "") == str(request_id)
                or str(derive_request_id_from_txn(entry.get("transaction_id"))) == str(request_id)
            ),
            None,
        )
        if not target:
            return jsonify({"status": "error", "message": "Request not found."}), 404

        # Build comprehensive response with all fields
        vault = read_json_file(STORAGE_FILE)
        user = vault.get(target.get("device_id", ""), {})
        response = {
            "transaction_id": target.get("transaction_id"),
            "request_id": target.get("transaction_id"),
            "request_id_numeric": derive_request_id_from_txn(target.get("transaction_id", "")),
            "device_id": target.get("device_id"),
            "username": target.get("username") or user.get("display_name") or user.get("username") or user.get("master_uid") or target.get("master_uid") or "unknown",
            "user_uid": target.get("user_uid"),
            "master_uid": target.get("master_uid"),
            "coins": target.get("coins"),
            "cash_amount": target.get("payout_value_rupees"),
            "payout_value": target.get("payout_value_rupees"),
            "reward_pack": target.get("reward_pack"),
            "payment_method": target.get("payment_method"),
            "payment_details": target.get("payment_details"),
            "upi_id": target.get("upi_id"),
            "mobile_number": target.get("mobile_number"),
            "redeem_code_value": target.get("redeem_code_value"),
            "status": normalize_status(target.get("status", "PENDING")),
            "created_at": target.get("timestamp"),
            "submitted_at": target.get("timestamp"),
            "updated_at": target.get("updated_at"),
            "approved_by": target.get("approved_by"),
            "approved_at": target.get("approved_at"),
            "rejected_at": target.get("rejected_at"),
            "reject_reason": target.get("reject_reason"),
            "completed_at": target.get("completed_at"),
            "paid_time": target.get("paid_time"),
            "paid_by": target.get("paid_by"),
            "payment_reference": target.get("utr"),  # UTR field
            "utr": target.get("utr"),
            "redeem_code": target.get("redeem_code"),
            "tier_id": target.get("tier_id"),
        }
        
        return jsonify({"status": "success", "request": response})
    except Exception as e:
        logger.error(f"Error fetching request details {request_id}: {str(e)}")
        return jsonify({"status": "error", "message": "Internal server error."}), 500


@app.route('/api/v1/admin/redeems/<request_id>/approve', methods=['POST'])
def admin_approve_redeem(request_id):
    username = require_admin()
    if not username:
        return jsonify({"status": "error", "message": "Unauthorized access."}), 401

    try:
        ledger = read_json_file(LEDGER_FILE, list)
        target = next(
            (
                entry
                for entry in ledger
                if str(entry.get("transaction_id") or "") == str(request_id)
                or str(derive_request_id_from_txn(entry.get("transaction_id"))) == str(request_id)
            ),
            None,
        )
        if not target:
            logger.warning(f"Approve attempt on non-existent request: {request_id} by {username}")
            return jsonify({"status": "error", "message": "Request not found."}), 404

        old_status = normalize_status(target.get("status", "PENDING"))
        if old_status != "PENDING":
            logger.warning(f"Approve attempt on {old_status} request {request_id} by {username}")
            return jsonify({"status": "error", "message": "Only pending requests can be approved."}), 409

        # Update single record atomically
        target["status"] = "PROCESSING"
        target["updated_at"] = datetime.now(ist_timezone).isoformat()
        target["approved_by"] = username
        write_json_file(LEDGER_FILE, ledger)
        
        # Create exactly one notification
        create_notification(
            request_id,
            "Payment Processing",
            f"Request {request_id} is being processed for payment.",
            "PROCESSING",
            f"Pack {target.get('tier_id', '?')}",
        )
        
        # Log audit trail
        log_admin_action(username, request_id, old_status, "PROCESSING", "approve")
        logger.info(f"Request {request_id} moved to PROCESSING by {username}")
        
        return jsonify({"status": "success"})
    except Exception as e:
        logger.error(f"Error approving request {request_id}: {str(e)}")
        return jsonify({"status": "error", "message": "Internal server error."}), 500


@app.route('/api/v1/admin/redeems/<request_id>/reject', methods=['POST'])
def admin_reject_redeem(request_id):
    username = require_admin()
    if not username:
        return jsonify({"status": "error", "message": "Unauthorized access."}), 401

    try:
        data = request.json or {}
        reason = (data.get("reason") or "").strip()
        if not reason:
            return jsonify({"status": "error", "message": "A rejection reason is required."}), 400

        ledger = read_json_file(LEDGER_FILE, list)
        target = next(
            (
                entry
                for entry in ledger
                if str(entry.get("transaction_id") or "") == str(request_id)
                or str(derive_request_id_from_txn(entry.get("transaction_id"))) == str(request_id)
            ),
            None,
        )
        if not target:
            logger.warning(f"Reject attempt on non-existent request: {request_id} by {username}")
            return jsonify({"status": "error", "message": "Request not found."}), 404

        old_status = normalize_status(target.get("status", "PENDING"))
        if old_status != "PENDING":
            logger.warning(f"Reject attempt on {old_status} request {request_id} by {username}")
            return jsonify({"status": "error", "message": "Only pending requests can be rejected."}), 409

        # Update single record atomically
        target["status"] = "REJECTED"
        target["updated_at"] = datetime.now(ist_timezone).isoformat()
        target["reject_reason"] = reason
        write_json_file(LEDGER_FILE, ledger)
        
        # Create exactly one notification
        create_notification(
            request_id,
            "Redeem Rejected",
            f"Request {request_id} was rejected. {reason}",
            "REJECTED",
            f"Pack {target.get('tier_id', '?')}",
        )
        
        # Log audit trail
        log_admin_action(username, request_id, old_status, "REJECTED", "reject", reason)
        logger.info(f"Request {request_id} rejected by {username}: {reason}")
        
        return jsonify({"status": "success"})
    except Exception as e:
        logger.error(f"Error rejecting request {request_id}: {str(e)}")
        return jsonify({"status": "error", "message": "Internal server error."}), 500


@app.route('/api/v1/admin/notifications', methods=['GET'])
def admin_notifications():
    username = require_admin()
    if not username:
        return jsonify({"status": "error", "message": "Unauthorized access."}), 401
    return jsonify({"status": "success", "notifications": read_json_file(NOTIFICATIONS_FILE, list)})


@app.route('/api/v1/admin/audit', methods=['GET'])
def admin_audit_log():
    username = require_admin()
    if not username:
        return jsonify({"status": "error", "message": "Unauthorized access."}), 401
    return jsonify({"status": "success", "audit_log": read_json_file(AUDIT_LOG_FILE, list)})


@app.route('/api/v1/admin/view-payouts', methods=['GET'])
def admin_view_payouts():
    username = require_admin()
    if not username:
        return jsonify({"status": "error", "message": "Unauthorized access."}), 401
    return jsonify(read_json_file(LEDGER_FILE, list))


@app.route('/api/v1/admin/fulfill', methods=['POST'])
def admin_fulfill_payout():
    username = require_admin()
    if not username:
        return jsonify({"status": "error", "message": "Unauthorized access."}), 401

    data = request.json or {}
    target_txn_id = data.get("transaction_id")
    utr_value = (data.get("utr") or data.get("transaction_reference") or data.get("redeem_code") or "").strip()

    if not target_txn_id or not utr_value:
        return jsonify({"status": "error", "message": "Missing transaction ID or payment reference."}), 400

    ledger = read_json_file(LEDGER_FILE, list)
    target = next(
        (
            entry
            for entry in ledger
            if str(entry.get("transaction_id") or "") == str(target_txn_id)
            or str(derive_request_id_from_txn(entry.get("transaction_id"))) == str(target_txn_id)
        ),
        None,
    )
    if not target:
        logger.warning(f"Fulfill attempt on non-existent request: {target_txn_id} by {username}")
        return jsonify({"status": "error", "message": "Request not found."}), 404

    current_status = normalize_status(target.get("status", "PENDING"))
    if current_status == "COMPLETED":
        return jsonify({"status": "success", "message": "This redeem request has already been completed."})
    if current_status != "PROCESSING":
        return jsonify({"status": "error", "message": "Only processing requests can be marked paid."}), 409

    target["status"] = "COMPLETED"
    target["updated_at"] = datetime.now(ist_timezone).isoformat()
    target["paid_by"] = username
    target["paid_time"] = timestamp_string()
    target["completed_time"] = timestamp_string()
    target["utr"] = utr_value
    if str(target.get("payment_method", "")).upper() == "REDEEM CODE":
        target["redeem_code"] = utr_value

    write_json_file(LEDGER_FILE, ledger)

    create_notification(
        target_txn_id,
        "Payment Sent Successfully",
        f"Request {target_txn_id} has been completed. Reference: {utr_value}",
        "COMPLETED",
        f"Pack {target.get('tier_id', '?')}",
    )
    log_admin_action(username, target_txn_id, current_status, "COMPLETED", "fulfill", utr=utr_value)
    logger.info(f"Request {target_txn_id} completed by {username} with reference {utr_value}")
    return jsonify({"status": "success"})


@app.route('/api/v1/admin/settings', methods=['POST'])
def admin_update_settings():
    username = require_admin()
    if not username:
        return jsonify({"status": "error", "message": "Unauthorized access."}), 401

    data = request.json or {}
    monthly_budget = data.get("monthly_budget")
    reward_pool_target = data.get("reward_pool_target")

    settings = read_settings_file()
    updated = False
    if monthly_budget is not None:
        try:
            settings["monthly_budget"] = int(monthly_budget)
            updated = True
        except (TypeError, ValueError):
            return jsonify({"status": "error", "message": "Invalid monthly budget."}), 400
    if reward_pool_target is not None:
        try:
            settings["reward_pool_target"] = int(reward_pool_target)
            updated = True
        except (TypeError, ValueError):
            return jsonify({"status": "error", "message": "Invalid reward pool target."}), 400

    if not updated:
        return jsonify({"status": "error", "message": "No valid settings provided."}), 400

    write_settings_file(settings)
    log_admin_action(username, "settings", "N/A", "N/A", "update_settings", None, None)
    return jsonify({"status": "success", "settings": settings})


if __name__ == '__main__':
    # Development server configuration
    port = int(os.environ.get("PORT", 5000))
    host = os.environ.get("HOST", "0.0.0.0")
    debug = os.environ.get("FLASK_ENV") == "development"
    
    logger.info(f"Starting KryptoLoot Admin Backend")
    logger.info(f"Environment: {os.environ.get('FLASK_ENV', 'development')}")
    logger.info(f"Host: {host}, Port: {port}")
    logger.info(f"Debug: {debug}")
    
    app.run(host=host, port=port, debug=debug, use_reloader=debug)

# WSGI export for production servers (Gunicorn, Waitress, etc.)
# Usage: gunicorn app:app --workers 4 --bind 0.0.0.0:5000
# Usage: waitress-serve --port=5000 --host=0.0.0.0 app:app
if os.environ.get("FLASK_ENV") == "production":
    logger.info("Running in production mode")
