import json
import urllib.request as u

base = 'http://192.168.1.2:5000'

def try_request(method, path, data=None, headers=None):
    url = base + path
    body = None
    if data is not None:
        body = json.dumps(data).encode('utf-8')
    req = u.Request(url, data=body, headers=headers or {}, method=method)
    try:
        with u.urlopen(req) as r:
            payload = r.read().decode()
            print(f'OK {method} {path} -> {r.status}')
            print(payload[:400])
    except Exception as e:
        print(f'ERR {method} {path} -> {e}')

print('TEST: POST /api/v1/auth/handshake')
try_request(
    'POST',
    '/api/v1/auth/handshake',
    data={'device_id': 'sync-test-device-001'},
    headers={'Content-Type': 'application/json'}
)

print('\nTEST: POST /api/v1/auth/device-handshake')
try_request(
    'POST',
    '/api/v1/auth/device-handshake',
    data={'device_id': 'sync-test-device-001'},
    headers={'Content-Type': 'application/json'}
)

print('\nTEST: POST /api/v1/rewards/redeem')
try_request(
    'POST',
    '/api/v1/rewards/redeem',
    data={
        'device_id': 'sync-test-device-001',
        'coins_to_redeem': 7000,
        'payment_method': 'UPI',
        'upi_id': 'sync@upi'
    },
    headers={'Content-Type': 'application/json'}
)

print('\nTEST: GET /api/v1/users/sync-test-device-001/redemptions')
try_request('GET', '/api/v1/users/sync-test-device-001/redemptions')

print('\nTEST: GET /api/v1/leaderboard?period=daily&limit=10')
try_request('GET', '/api/v1/leaderboard?period=daily&limit=10')
