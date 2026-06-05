"""One-off: align flyway_schema_history checksum for V17 with current migration file."""
import sqlite3

DB = r"d:/Project/MediaManager/data/mediamanager.db"
# Matches org.flywaydb checksum for V17__sys_config_ai.sql bundled in the application JAR
CHECKSUM = 920685620

conn = sqlite3.connect(DB)
cur = conn.execute(
    "SELECT checksum FROM flyway_schema_history WHERE version = '17'"
).fetchone()
if cur is None:
    raise SystemExit("V17 not found in flyway_schema_history")
old = cur[0]
conn.execute(
    "UPDATE flyway_schema_history SET checksum = ? WHERE version = '17'",
    (CHECKSUM,),
)
conn.commit()
new = conn.execute(
    "SELECT checksum FROM flyway_schema_history WHERE version = '17'"
).fetchone()[0]
print(f"flyway V17 checksum: {old} -> {new}")
for v, cs in conn.execute(
    "SELECT version, checksum FROM flyway_schema_history ORDER BY installed_rank"
):
    print(f"  v{v}: {cs}")
conn.close()
