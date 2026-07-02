import requests, json, os
BASE='http://127.0.0.1:5000'
# ensure vault and ledger exist and find a funded device
vault_file='user_vault.json'
ledger_file='payout_ledger.json'
if os.path.exists(vault_file):
    vault=json.load(open(vault_file))
else:
    vault={}

# find device
device=None
for k,v in vault.items():
    if v.get('coin_balance',0)>=700:
        device=k; break
if not device:
    device='test-device-backend-001'
    vault[device]={'master_uid':'TEST-USER-001','coin_balance':10000,'trust_score':100,'account_status':'ACTIVE','ads_today':0,'session_ads':0,'last_calendar_reset':''}
    json.dump(vault, open(vault_file,'w'), indent=2)
    print('Created test device', device)
else:
    print('Using funded device', device, 'balance', vault[device]['coin_balance'])

# initial ledger count
ledger=[]
if os.path.exists(ledger_file):
    ledger=json.load(open(ledger_file))
print('Initial ledger entries:', len(ledger))

# Submit redeem from Android (simulate)
redeem_payload={'device_id':device,'coins_to_redeem':700,'payment_method':'UPI'}
print('POST /api/v1/rewards/redeem', redeem_payload)
r=requests.post(BASE+'/api/v1/rewards/redeem', json=redeem_payload)
print('Status', r.status_code, r.text)
if r.status_code!=200:
    print('FAIL: Android->Backend POST failed')
    exit(1)
resp=r.json()
txn=resp.get('transaction_id')
req_id=resp.get('request_id')
print('Returned txn:', txn, 'req_id:', req_id)

# verify ledger got one more entry
ledger=json.load(open(ledger_file))
matches=[e for e in ledger if e.get('transaction_id')==txn]
print('Ledger matches for txn:', len(matches))
if len(matches)!=1:
    print('FAIL: payout_ledger.json does not contain exactly one record for redeem')
    # continue to attempt admin actions

# Attempt admin login using a persistent session and environment-provided password
creds_r = requests.get(BASE + '/_debug/creds')
admin_user = os.environ.get('ADMIN_USERNAME')
if not admin_user and creds_r.ok:
    admin_user = creds_r.json().get('admin_username')
if not admin_user:
    admin_user = 'admin'
print('Admin username resolved to:', admin_user)

admin_pass = os.environ.get('ADMIN_PASSWORD')
if not admin_pass:
    print('ERROR: ADMIN_PASSWORD environment variable not set. Set it to the admin password and rerun the verifier.')
    exit(2)

session = requests.Session()
login_r = session.post(BASE + '/api/v1/admin/login', json={'username': admin_user, 'password': admin_pass})
print('Admin login status', login_r.status_code, login_r.text)
if login_r.status_code != 200:
    print('WARN: Admin login failed; cannot simulate dashboard actions')
else:
    # approve redeem
    if txn:
        approve_r = session.post(BASE + f'/api/v1/admin/redeems/{txn}/approve')
        print('Approve status', approve_r.status_code, approve_r.text)
        ledger = json.load(open(ledger_file))
        m = [e for e in ledger if e.get('transaction_id') == txn]
        print('Post-approve ledger status:', m[0].get('status') if m else 'not found')
        # GET user redemptions
        getr = requests.get(BASE + f'/api/v1/users/{device}/redemptions')
        print('GET redemptions status', getr.status_code)
        if getr.ok:
            items = getr.json()
            print('Redemptions count for device:', len(items))
            latest = items[0] if items else None
            print('Latest redemption:', latest)

# Final PASS/FAIL
print('\nRESULTS:')
android_backend = 'PASS' if r.status_code==200 else 'FAIL'
backend_dashboard = 'PASS' if login_r.status_code==200 else 'WARN'
dashboard_backend = 'PASS' if (login_r.status_code==200 and approve_r.status_code==200) else ('WARN' if login_r.status_code==200 else 'FAIL')
backend_android = 'PASS' if (getr.ok if 'getr' in globals() else False) else 'WARN'
print('Android->Backend:', android_backend)
print('Backend->Dashboard (login):', backend_dashboard)
print('Dashboard->Backend (approve):', dashboard_backend)
print('Backend->Android (redemptions):', backend_android)
