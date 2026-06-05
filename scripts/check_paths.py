import sqlite3
from pathlib import Path

db = Path(__file__).resolve().parents[1] / "data" / "mediamanager.db"
c = sqlite3.connect(db)

print("=== library_path ===")
for row in c.execute("SELECT library_id, path FROM library_path"):
    print(row)

print("\n=== sample active file_paths (prefix counts) ===")
rows = c.execute(
    "SELECT file_path FROM media_file WHERE deleted=0 LIMIT 500"
).fetchall()
prefixes = {}
for (p,) in rows:
    if not p:
        continue
    norm = p.replace("\\", "/")
    root = norm.split("/", 3)[:3]
    key = "/".join(root) if len(root) >= 3 else norm[:40]
    prefixes[key] = prefixes.get(key, 0) + 1
for k, v in sorted(prefixes.items(), key=lambda x: -x[1])[:10]:
    print(v, k)

print("\n=== enabling auth ===")
c.execute("UPDATE sys_config SET config_value = 'true' WHERE config_key = 'auth.enabled'")
c.commit()
print("auth.enabled updated to true")

print("\n=== media items with library ====")
for row in c.execute("SELECT id, library_id, title, type, hidden FROM media_item WHERE id IN (587, 838)"):
    print(row)







print("\n=== sys_user rows ===")
for row in c.execute("SELECT * FROM sys_user"):
    print(row)
