import requests
import time
import json

BASE='http://127.0.0.1:5000'
s=requests.Session()
device_id=f'test-device-e2e-{int(time.time())}'
print('device_id', device_id)
print('handshake', s.post(f'{BASE}/api/v1/auth/device-handshake', json={'device_id': device_id}).text)
print('ad', s.post(f'{BASE}/api/v1/ads/log-completion', json={'device_id': device_id, 'time_spent_seconds': 30}).text)
print('redeem', s.post(f'{BASE}/api/v1/rewards/redeem', json={'device_id': device_id, 'tier_id': '2', 'payment_method':'UPI', 'upi_id':'test@upi'}).text)
print('admin_login', s.post(f'{BASE}/api/v1/admin/login', json={'username':'subhendugupta124548k','password':'KryptoLoot@2026'}).text)
print('admin_redeems', s.get(f'{BASE}/api/v1/admin/redeems', params={'status':'ALL'}).text)
