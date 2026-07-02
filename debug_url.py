#!/usr/bin/env python3
import requests

session = requests.Session()
session.post('http://127.0.0.1:5000/api/v1/admin/login', json={'username': 'subhendugupta124548k', 'password': 'KryptoLoot@2026'})

# Submit a redeem
resp = requests.post('http://127.0.0.1:5000/api/v1/rewards/redeem',
    json={'device_id': 'test-url-debug', 'coins_to_redeem': 300, 'payment_method': 'UPI', 'payment_details': 'test@upi', 'username': 'Test', 'reward_pack': 'Bronze', 'coins': 300, 'cash_amount': 10.0})
print('Redeem response:', resp.status_code)
txn_id = resp.json().get('transaction_id')
print('Transaction ID:', txn_id)

# Get list
list_resp = session.get('http://127.0.0.1:5000/api/v1/admin/redeems', params={'status': 'ALL'})
print('List response:', list_resp.status_code)
requests_list = list_resp.json().get('requests', [])
if requests_list:
    req = requests_list[0]
    print('From list - transaction_id:', req.get('transaction_id'))
    print('From list - request_id:', req.get('request_id'))
    print('From list - request_id_numeric:', req.get('request_id_numeric'))
    
    # Try with transaction_id
    url1 = f'http://127.0.0.1:5000/api/v1/admin/redeems/{req.get("transaction_id")}'
    r1 = session.get(url1)
    print(f'URL with transaction_id: {url1} -> {r1.status_code}')
    if r1.status_code != 200:
        print(f'  Error: {r1.text[:100]}')
    
    # Try with request_id
    url2 = f'http://127.0.0.1:5000/api/v1/admin/redeems/{req.get("request_id")}'
    r2 = session.get(url2)
    print(f'URL with request_id: {url2} -> {r2.status_code}')
    if r2.status_code != 200:
        print(f'  Error: {r2.text[:100]}')
    
    # Try with request_id_numeric
    url3 = f'http://127.0.0.1:5000/api/v1/admin/redeems/{req.get("request_id_numeric")}'
    r3 = session.get(url3)
    print(f'URL with request_id_numeric: {url3} -> {r3.status_code}')
    if r3.status_code != 200:
        print(f'  Error: {r3.text[:100]}')
