"""Restore media_file rows wrongly marked deleted when the file still exists on disk."""
from __future__ import annotations

import sqlite3
from pathlib import Path

import sys

ROOT = Path(__file__).resolve().parents[1]
DB = ROOT / "data" / "mediamanager.db"

# Parameterize host media folder
HOST_MEDIA = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(r"E:\2024软考系统架构设计师VIP套餐视频")

PREFIXES = ("/home/media", "/home/test_media")


def to_host(file_path: str) -> Path:
    norm = file_path.replace("\\", "/")
    for prefix in PREFIXES:
        if norm.lower().startswith(prefix.lower()):
            rel = norm[len(prefix) :].lstrip("/")
            return HOST_MEDIA / rel.replace("/", "\\")
    return Path(norm)


def main() -> None:
    conn = sqlite3.connect(DB)
    cur = conn.cursor()
    restored_files = 0
    restored_items: set[int] = set()

    rows = cur.execute(
        "SELECT mf.id, mf.media_item_id, mf.file_path FROM media_file mf "
        "JOIN media_item mi ON mi.id = mf.media_item_id "
        "WHERE mf.deleted = 1"
    ).fetchall()

    for file_id, item_id, file_path in rows:
        if not file_path:
            continue
        host = to_host(file_path)
        if not host.is_file():
            continue
        norm = file_path.replace("\\", "/")
        new_path = norm
        if norm.lower().startswith("/home/test_media"):
            new_path = "/home/media" + norm[len("/home/test_media") :]
        conflict = cur.execute(
            "SELECT id FROM media_file WHERE file_path = ? AND deleted = 0 AND id != ?",
            (new_path, file_id),
        ).fetchone()
        if conflict:
            continue
        cur.execute(
            "UPDATE media_file SET deleted = 0, deleted_at = NULL, file_path = ? WHERE id = ?",
            (new_path, file_id),
        )
        restored_files += 1
        restored_items.add(item_id)

    for item_id in restored_items:
        has_active = cur.execute(
            "SELECT 1 FROM media_file WHERE media_item_id = ? AND deleted = 0 LIMIT 1",
            (item_id,),
        ).fetchone()
        if has_active:
            cur.execute("UPDATE media_item SET hidden = 0 WHERE id = ?", (item_id,))

    conn.commit()
    print(f"restored_files={restored_files}, unhidden_items={len(restored_items)}")


if __name__ == "__main__":
    main()
