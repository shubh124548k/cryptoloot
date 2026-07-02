import os
from werkzeug.security import generate_password_hash, check_password_hash

# Simulate what app.py does
ADMIN_USERNAME = os.environ.get("ADMIN_USERNAME", "subhendugupta124548k")
ADMIN_PASSWORD_HASH = os.environ.get("ADMIN_PASSWORD_HASH") or generate_password_hash(
    os.environ.get("ADMIN_PASSWORD", "subhendugupta124548k@@124548k")
)

test_username = "subhendugupta124548k"
test_password = "subhendugupta124548k@@124548k"

print(f"Expected username: {ADMIN_USERNAME}")
print(f"Test username: {test_username}")
print(f"Username match: {test_username == ADMIN_USERNAME}")

print(f"\nPassword hash: {ADMIN_PASSWORD_HASH[:50]}...")
print(f"Password check: {check_password_hash(ADMIN_PASSWORD_HASH, test_password)}")

if test_username == ADMIN_USERNAME and check_password_hash(ADMIN_PASSWORD_HASH, test_password):
    print("\n✅ Login would succeed")
else:
    print("\n❌ Login would fail")
