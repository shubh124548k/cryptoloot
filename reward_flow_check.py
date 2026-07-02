import json
import requests
BASE='http://127.0.0.1:5000'
device='2a06d22dada272ac'
p='user_vault.json'
with open(p) as f:
    vault=json.load(f)
before=vault[device]['coin_balance']
print('BEFORE', device, before)
r=requests.post(f'{BASE}/api/v1/auth/handshake', json={'device_id': device})
print('HANDSHAKE', r.status_code, r.json())
r2=requests.post(f'{BASE}/api/v1/auth/device-handshake', json={'device_id': device})
print('DEVICE_HANDSHAKE', r2.status_code, r2.json())
r3=requests.post(f'{BASE}/api/v1/ads/log-completion', json={'device_id': device, 'time_spent_seconds': 30})
print('AD_REWARD', r3.status_code, r3.json())
with open(p) as f:
    vault=json.load(f)
after=vault[device]['coin_balance']
print('AFTER', device, after)
print('DELTA', after-before)
