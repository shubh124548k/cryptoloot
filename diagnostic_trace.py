"""
KryptoLoot Sync Diagnostic Tool
Traces one request through the FULL pipeline:
API -> mergeRemoteRedemptions -> ViewModel -> HistoryScreen filter
Prints actual values at each step.
"""
import json, sys, os

API_RESPONSE = [
    {"request_id": 1782985428, "queue_id": "TXN-1782985428-KSI", "status": "COMPLETED", "admin_reply": "", "code_value": "ok done", "coin_cost": 7000, "payout_value": 250.0, "created_at": "2026-07-02T15:13:48.644604+05:30", "reward_name": "Elite Pack", "cash_amount": 250.0, "completed_at": "2026-07-02 15:15:15", "coins_before": 0, "coins_after": 0, "device_id": "2a06d22dada272ac", "transaction_type": "REDEEM", "description": "Redeem request via redeem code"},
    {"request_id": 1782985779, "queue_id": "TXN-1782985779-MBK", "status": "COMPLETED", "admin_reply": "", "code_value": "PROCESSING_BY_ADMIN", "coin_cost": 1500, "payout_value": 50.0, "created_at": "2026-07-02T15:19:39.708361+05:30", "reward_name": "Gold Pack", "cash_amount": 50.0, "completed_at": "2026-07-02 15:21:02", "coins_before": 0, "coins_after": 0, "device_id": "2a06d22dada272ac", "transaction_type": "REDEEM"},
    {"request_id": 1782988428, "queue_id": "TXN-1782988428-NUI", "status": "COMPLETED", "admin_reply": "SAMPLE12345CODE", "code_value": "PROCESSING_BY_ADMIN", "coin_cost": 3500, "payout_value": 120.0, "created_at": "2026-07-02T16:03:48.384343+05:30", "reward_name": "Diamond Pack", "cash_amount": 120.0, "completed_at": "2026-07-02 16:04:46", "coins_before": 0, "coins_after": 0, "device_id": "2a06d22dada272ac", "transaction_type": "REDEEM"},
    {"request_id": 1782995062, "queue_id": "TXN-1782995062-MHG", "status": "COMPLETED", "admin_reply": "thank you for using app!", "code_value": "TEST-FREE-GIFT-2026", "coin_cost": 300, "payout_value": 10.0, "created_at": "2026-07-02T17:54:22.517964+05:30", "reward_name": "Bronze Pack", "cash_amount": 10.0, "completed_at": "2026-07-02 17:54:51", "coins_before": 0, "coins_after": 0, "device_id": "2a06d22dada272ac", "transaction_type": "REDEEM"},
    {"request_id": 1782995791, "queue_id": "TXN-1782995791-MPN", "status": "COMPLETED", "admin_reply": "thank you", "code_value": "TEST-FREE-GIFT-2026", "coin_cost": 700, "payout_value": 25.0, "created_at": "2026-07-02T18:06:31.792878+05:30", "reward_name": "Silver Pack", "cash_amount": 25.0, "completed_at": "2026-07-02 18:06:49", "coins_before": 0, "coins_after": 0, "device_id": "2a06d22dada272ac", "transaction_type": "REDEEM"},
]

def simulate_merge_remote_redemptions(remote_items, existing_transactions=None):
    """Simulate UserRepository.mergeRemoteRedemptions(remote)"""
    print("=" * 70)
    print("  Android: UserRepository.mergeRemoteRedemptions(remote)")
    print("=" * 70)
    
    existing = existing_transactions or []
    
    # Filter out redemption transactions
    non_redemption = [t for t in existing if t.get("type") not in [
        "REDEEM_REQUEST", "REDEEM_PROCESSING", "REDEEM_APPROVED", 
        "REDEEM_REJECTED", "QUEUE_COMPLETED"
    ]]
    
    print(f"  Existing transactions: {len(existing)}")
    print(f"  Non-redemption (kept): {len(non_redemption)}")
    print(f"  Remote items to merge: {len(remote_items)}")
    print()
    
    # Map each remote item to TransactionRecord
    merged_redemptions = []
    for item in remote_items:
        status = item["status"].strip().upper()
        tx_type = {"PENDING": "REDEEM_REQUEST", "QUEUED": "REDEEM_REQUEST", 
                    "PROCESSING": "REDEEM_REQUEST", "APPROVED": "REDEEM_APPROVED",
                    "COMPLETED": "QUEUE_COMPLETED", "PAID": "QUEUE_COMPLETED",
                    "REJECTED": "REDEEM_REJECTED"}.get(status, "REDEEM_REQUEST")
        
        tx = {
            "id": f"txn-{item['request_id']}-{item['queue_id'] or '0'}",
            "queueId": item["queue_id"],
            "type": tx_type,
            "status": status,
            "adminReply": item.get("admin_reply") or "",
            "reward_name": item.get("reward_name"),
            "created_at": item["created_at"],
            "coins": item["coin_cost"],
            "payout": item["payout_value"],
        }
        merged_redemptions.append(tx)
    
    print(f"  TransactionRecords created from API:")
    for tx in sorted(merged_redemptions, key=lambda x: x["created_at"], reverse=True):
        print(f"    id={tx['id']}")
        print(f"      queueId={tx['queueId']}")
        print(f"      type={tx['type']}")
        print(f"      status={tx['status']}")
        print(f"      adminReply='{tx['adminReply']}'")
    
    # Merge
    merged = non_redemption.copy()
    for redemption in sorted(merged_redemptions, key=lambda x: x["created_at"], reverse=True):
        existing_idx = next((i for i, t in enumerate(merged) if t.get("queueId") == redemption["queueId"]), -1)
        if existing_idx >= 0:
            merged[existing_idx] = redemption
            print(f"    UPDATED existing entry for queueId={redemption['queueId']}")
        else:
            merged.insert(0, redemption)
            print(f"    ADDED new entry for queueId={redemption['queueId']}")
    
    print()
    print(f"  Merged transaction count: {len(merged)}")
    print(f"  Redemption entries: {len([t for t in merged if t.get('type') in ['REDEEM_REQUEST','QUEUE_COMPLETED','REDEEM_APPROVED','REDEEM_REJECTED']])}")
    
    return merged

def simulate_viewmodel_mapping(merged_txs):
    """Simulate CoinsViewModel onEach{} mapping TransactionRecord -> RedemptionHistoryItem"""
    print()
    print("=" * 70)
    print("  Android: CoinsViewModel maps TransactionRecord -> RedemptionHistoryItem")
    print("=" * 70)
    
    items = []
    for tx in merged_txs:
        # ViewModel: request_id = tx.id.filter { it.isDigit() }.toIntOrNull() ?: 0
        digits = ''.join(c for c in tx["id"] if c.isdigit())
        request_id = 0  # Always overflows for 20-digit numbers
        if digits:
            try:
                val = int(digits)
                if val > 2147483647:
                    request_id = 0  # Int overflow
                else:
                    request_id = val
            except ValueError:
                request_id = 0
        
        item = {
            "request_id": request_id,
            "queue_id": tx["queueId"],
            "status": tx["status"],
            "transaction_type": tx["type"],
            "admin_reply": tx["adminReply"],
            "reward_name": tx.get("reward_name"),
            "created_at": tx["created_at"],
        }
        items.append(item)
    
    for item in items:
        print(f"  queue_id:             {item['queue_id']}")
        print(f"  request_id:           {item['request_id']}")
        print(f"  transaction_type:     '{item['transaction_type']}'")
        print(f"  admin_reply:          '{item['admin_reply']}'")
        print(f"  status:               '{item['status']}'")
        print(f"  created_at:           {item['created_at']}")
        print()
    
    return items

def simulate_history_screen_filter(items):
    """Simulate HistoryScreen filter"""
    print("=" * 70)
    print("  Android: HistoryScreen filter")
    print("=" * 70)
    
    VALID_TYPES = ["REDEEM_REQUEST", "REDEEM_PROCESSING", "REDEEM_APPROVED", 
                    "REDEEM_REJECTED", "QUEUE_COMPLETED"]
    
    before = len(items)
    filtered = [i for i in items if i["transaction_type"] in VALID_TYPES]
    after = len(filtered)
    
    print(f"  Items before filter: {before}")
    print(f"  Items after filter:  {after}")
    print()
    
    for item in filtered:
        type_ok = item["transaction_type"] in VALID_TYPES
        print(f"  queue_id:             {item['queue_id']}")
        print(f"  transaction_type:     '{item['transaction_type']}' -> Filter {'PASS' if type_ok else 'FAIL'}")
        print(f"  Sorted by created_at: {item['created_at']}")
        print()
    
    return filtered

def simulate_loaddata_path():
    """Simulate the complete loadData() -> getRedemptionHistory() -> merge -> ViewModel -> filter path"""
    print()
    print("=" * 70)
    print("  FULL PIPELINE SIMULATION: loadData()")
    print("=" * 70)
    print()
    
    # Step 1: API returns 5 items
    print("=" * 70)
    print("  STEP 0: API returns 5 items via getRedemptions(deviceId)")
    print("=" * 70)
    print(f"  Count: {len(API_RESPONSE)}")
    print()
    
    # Simulate no local pending payments being in transaction list
    # (mergeRemoteRedemptions filters them out anyway)
    existing = []
    
    # Step 2: mergeRemoteRedemptions
    merged = simulate_merge_remote_redemptions(API_RESPONSE, existing)
    
    # Step 3: ViewModel mapping
    items = simulate_viewmodel_mapping(merged)
    
    # Step 4: HistoryScreen filter
    filtered = simulate_history_screen_filter(items)
    
    print("=" * 70)
    print("  FINAL VERDICT")
    print("=" * 70)
    print(f"  API returns:  {len(API_RESPONSE)} COMPLETED items")
    print(f"  Merge creates: {len(merged)} TransactionRecords")
    print(f"  ViewModel maps: {len(items)} RedemptionHistoryItems")
    print(f"  Filter passes:  {len(filtered)} items to LazyColumn")
    print()
    
    if len(filtered) == len(API_RESPONSE):
        print("  >>> ALL ITEMS SURVIVE THE PIPELINE <<<")
        print("  The merge pipeline is CORRECT.")
        print("  The bug must be EARLIER (API not called, merge not triggered, etc.)")
    else:
        print(f"  >>> BUG: {len(API_RESPONSE) - len(filtered)} items lost! <<<")
    
    print()
    print("  Items in LazyColumn (sorted by created_at descending):")
    for i, item in enumerate(sorted(filtered, key=lambda x: x["created_at"], reverse=True)):
        print(f"    #{i+1}: {item['reward_name']} ₹{item['queue_id'].split('-')[-1] if '-' in (item.get('queue_id') or '') else '?'} "
              f"request_id={item['request_id']} admin_reply='{item['admin_reply']}'")


def simulate_polling_path_with_missing_local_payments():
    """
    Simulate the polling path when local payments don't exist for some remote items.
    This is the critical test: if admin creates requests on the dashboard,
    local RedeemPayments won't exist, so pollRemoteRedemptions won't call mergeRemoteRedemptions.
    """
    print()
    print("=" * 70)
    print("  ALTERNATIVE PATH: pollRemoteRedemptions")
    print("=" * 70)
    print()
    
    # Simulate that the Android device has NO local RedeemPayment entries
    # (e.g., because requests were created on admin dashboard, not from Android)
    local_payments = []  # Empty local payments
    
    print("  Scenario: Admin created all 5 on dashboard (no local RedeemPayments)")
    print(f"  Local payments count: {len(local_payments)}")
    print()
    
    has_changes = False
    for item in API_RESPONSE:
        remote_txn_id = item["queue_id"] or str(item["request_id"])
        matches = [p for p in local_payments if 
                   p.get("transactionId") == remote_txn_id or 
                   p.get("orderId") == remote_txn_id]
        
        if matches:
            print(f"  {item['queue_id']}: LOCAL MATCH FOUND -> status change -> hasChanges=True")
            has_changes = True
        else:
            print(f"  {item['queue_id']}: NO LOCAL MATCH -> SKIPPED (hasChanges unchanged)")
    
    print()
    print(f"  hasChanges = {has_changes}")
    print(f"  mergeRemoteRedemptions called: {has_changes}")
    print()
    
    if not has_changes:
        print("  >>> BUG: pollRemoteRedemptions does NOT call mergeRemoteRedemptions")
        print("  >>> when no local RedeemPayment matches exist!")
        print("  >>> Android would see ZERO items from polling.")
        print()
        print("  BUT: loadData() bypasses this check entirely.")
        print("  loadData() -> getRedemptionHistory() calls mergeRemoteRedemptions directly.")
        print()
        print("  Therefore: if loadData() IS called (e.g., tapping History tab),")
        print("  all 5 items WILL appear.")
        print()
        print("  Root cause candidate: loadData() might not be triggered,")
        print("  or might fail silently (timeout/exception).")
    
    print()
    print("  Simulating loadData() after failed poll:")
    print("  loadData() -> getRedemptionHistory() -> mergeRemoteRedemptions(remote)")
    print("  -> ALL 5 items merged regardless of local payments")
    print("  -> 5 items appear in history")
    print()
    print("  Conclusion: The bug is NOT in pollRemoteRedemptions logic,")
    print("  because loadData() bypasses it.")


if __name__ == "__main__":
    simulate_loaddata_path()
    print()
    print()
    simulate_polling_path_with_missing_local_payments()
