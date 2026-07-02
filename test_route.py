from app import app
import json

# Use Flask's test client
client = app.test_client()

# First login
login_resp = client.post('/api/v1/admin/login', json={'username': 'subhendugupta124548k', 'password': 'KryptoLoot@2026'})
print('Login:', login_resp.status_code)

# Try to get details - just use a simple request ID
resp = client.get('/api/v1/admin/redeems/TXN-1782892922-HOO')
print('GET /api/v1/admin/redeems/TXN-1782892922-HOO:', resp.status_code)
if resp.status_code == 200:
    print('Response:', resp.json)
else:
    print('Response:', resp.data[:200])

# Try with a simpler ID
resp2 = client.get('/api/v1/admin/redeems/1782892922')
print('GET /api/v1/admin/redeems/1782892922:', resp2.status_code)
if resp2.status_code == 200:
    print('Response:', resp2.json)
else:
    print('Response:', resp2.data[:200])
