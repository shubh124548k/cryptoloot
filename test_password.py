from werkzeug.security import check_password_hash, generate_password_hash

password = "subhendugupta124548k@@124548k"
hash_val = generate_password_hash(password)
print("Hash:", hash_val)
print("Verification:", check_password_hash(hash_val, password))
