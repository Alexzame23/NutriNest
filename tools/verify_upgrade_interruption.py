"""Kill a SQLite writer mid NutriNest v3->v4 and verify recovery on a private copy."""
import hashlib
import json
import multiprocessing
import os
from pathlib import Path
import sqlite3
import tempfile
from contextlib import closing
from zipfile import ZipFile

from ksql import extract_exec_sql
from verify_migration import DB_KT, REPO


def migration_sql():
    source = Path(DB_KT).read_text(encoding="utf-8")
    start = source.index("private val MIGRATION_3_4")
    end = source.index("internal val MIGRATION_4_5", start)
    return extract_exec_sql(source[start:end])


def interrupt(path):
    db = sqlite3.connect(path)
    db.execute("PRAGMA foreign_keys=ON")
    db.execute("PRAGMA journal_mode=DELETE")
    db.execute("PRAGMA synchronous=FULL")
    db.execute("PRAGMA cache_size=1")
    db.execute("BEGIN IMMEDIATE")
    for sql in migration_sql():
        db.execute(sql)
        if sql == "DROP TABLE `profiles`":
            os._exit(24)
    raise AssertionError("Interruption point was not reached")


def rows(db, table):
    # Named projections remain stable when the profile rebuild moves columns.
    fields = sorted(row[1] for row in db.execute(f'PRAGMA table_info("{table}")'))
    projection = ",".join(f'"{name}"' for name in fields)
    return db.execute(f'SELECT {projection} FROM "{table}" ORDER BY {projection}').fetchall()


def main():
    qa = Path(REPO) / "docs/qa"
    archive = qa / "fixtures/24-upgrade-originals.zip"
    results = json.loads((qa / "24-upgrade-results.json").read_text(encoding="utf-8"))
    archive_hash = hashlib.sha256(archive.read_bytes()).hexdigest()
    with ZipFile(archive) as fixtures, tempfile.TemporaryDirectory() as directory:
        assert {r["from"] for r in results["upgrades"]} == set(range(1, 11))
        for entry in results["upgrades"]:
            data = fixtures.read(f"qa24-v{entry['from']}.db")
            assert hashlib.sha256(data).hexdigest() == entry["originalSha256"]
        copy = Path(directory) / "working-v3.db"
        copy.write_bytes(fixtures.read("qa24-v3.db"))
        with closing(sqlite3.connect(copy)) as db:
            before = list(db.iterdump())
            tables = [r[0] for r in db.execute(
                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'"
            )]
            historical = {t: rows(db, t) for t in tables}
        process = multiprocessing.Process(target=interrupt, args=(copy,))
        process.start()
        process.join(30)
        if process.is_alive():
            process.kill()
            process.join()
            raise RuntimeError("Writer stalled")
        assert process.exitcode == 24
        assert Path(str(copy) + "-journal").stat().st_size > 0
        with closing(sqlite3.connect(copy)) as db:
            assert db.execute("PRAGMA user_version").fetchone() == (3,)
            assert list(db.iterdump()) == before
            assert db.execute("PRAGMA integrity_check").fetchall() == [("ok",)]
            db.execute("PRAGMA foreign_keys=ON")
            db.execute("BEGIN")
            for sql in migration_sql():
                db.execute(sql)
            db.execute("PRAGMA user_version=4")
            db.commit()
            assert db.execute("PRAGMA user_version").fetchone() == (4,)
            assert db.execute("PRAGMA foreign_key_check").fetchall() == []
            assert db.execute("PRAGMA integrity_check").fetchall() == [("ok",)]
            assert historical == {t: rows(db, t) for t in tables}
    assert archive_hash == hashlib.sha256(archive.read_bytes()).hexdigest()
    print("v3 recovered after writer exit at DROP TABLE profiles; v4 retry preserved all historical rows")


if __name__ == "__main__":
    main()
