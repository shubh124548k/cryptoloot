#!/usr/bin/env python
from werkzeug.security import check_password_hash
import os

# Test password
password = "subhendugupta124548k@@124548k"

# Hash we generated
hash_value = "scrypt:32768:8:1$1Vag8onvOXeh6RPp$d1c3accb45ab36fcbf8d20b22a90ff6feccc0587f68680aaee6665f50e9ec0a86cf88514ff82cb9bad8a4f6dcc92eed7e25165789cd08268fe97e7fc750e1c68"

result = check_password_hash(hash_value, password)
print(f"Password hash verification: {result}")

# Also test environment variables
os.environ["ADMIN_USERNAME"] = "subhendugupta124548k"
os.environ["ADMIN_PASSWORD_HASH"] = hash_value

import sys
sys.path.insert(0, ".")
from app import ADMIN_USERNAME, ADMIN_PASSWORD_HASH
print(f"ADMIN_USERNAME: {ADMIN_USERNAME}")
print(f"ADMIN_PASSWORD_HASH[:50]: {ADMIN_PASSWORD_HASH[:50]}")
print(f"Hash matches expected: {ADMIN_PASSWORD_HASH == hash_value}")

# Test login manually
from werkzeug.security import check_password_hash as check_hash
username = "subhendugupta124548k"
password = "subhendugupta124548k@@124548k"
username_match = (username == ADMIN_USERNAME)
password_match = check_hash(ADMIN_PASSWORD_HASH, password)
print(f"Username match: {username_match}")
print(f"Password match: {password_match}")
print(f"Would login succeed: {username_match and password_match}")
