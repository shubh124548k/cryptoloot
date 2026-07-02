import os
from werkzeug.security import generate_password_hash, check_password_hash

# Simulate what Flask is doing
password_input = "subhendugupta124548k@@124548k"
default_password = "subhendugupta124548k@@124548k"

hash_val = generate_password_hash(default_password)
print(f"Generated hash: {hash_val}")

result = check_password_hash(hash_val, password_input)
print(f"Password verification result: {result}")

# Try with a different hash (from what Flask loads)
hash_val2 = generate_password_hash("subhendugupta124548k@@124548k")
result2 = check_password_hash(hash_val2, password_input)
print(f"Password verification result (fresh hash): {result2}")
