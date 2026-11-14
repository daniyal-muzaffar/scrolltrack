#!/usr/bin/env python3
"""Migration test: build a v1 scrolltrack.db, apply the v2 migration SQL
taken from the actual DbHelper.java onUpgrade(), and confirm every row and
setting survives. Exits non-zero on failure.

The v1 schema below mirrors DbHelper v1 (onCreate before this change).
scrolltrack_prefs lives in a separate XML file and is untouched by any DB
migration, so there is nothing to migrate there.
"""
import re
import sqlite3
import sys
import tempfile
from pathlib import Path

PROJECT = Path.home() / "workspace/apk-build/scrolltrack-app"
DBHELPER = PROJECT / "app/src/main/java/com/dudeyy/scrolltrack/data/db/DbHelper.java"

V1_SESSIONS = """CREATE TABLE sessions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    startTime INTEGER NOT NULL,
    endTime INTEGER NOT NULL DEFAULT 0,
    swipeCount INTEGER NOT NULL DEFAULT 0,
    packageName TEXT NOT NULL DEFAULT 'com.instagram.android',
    endedCleanly INTEGER NOT NULL DEFAULT 0,
    quality TEXT NOT NULL DEFAULT 'full')"""

V1_DAY_STATS = """CREATE TABLE day_stats (
    day TEXT PRIMARY KEY,
    swipeCount INTEGER NOT NULL DEFAULT 0,
    sessionCount INTEGER NOT NULL DEFAULT 0,
    totalDurationMs INTEGER NOT NULL DEFAULT 0,
    longestSessionMs INTEGER NOT NULL DEFAULT 0,
    notified50 INTEGER NOT NULL DEFAULT 0,
    notified80 INTEGER NOT NULL DEFAULT 0,
    notified100 INTEGER NOT NULL DEFAULT 0)"""

failures = []


def check(name, cond):
    print(("ok   " if cond else "FAIL ") + name)
    if not cond:
        failures.append(name)


def main():
    src = DBHELPER.read_text()

    # 1. The version was bumped and onUpgrade no longer drops anything.
    m = re.search(r"DB_VERSION\s*=\s*(\d+)", src)
    check("DB_VERSION is 2", m and int(m.group(1)) == 2)
    upgrade = src[src.index("public void onUpgrade"):]
    upgrade = upgrade[:upgrade.index("\n    }\n") + len("\n    }\n")]
    check("onUpgrade contains no DROP TABLE", "DROP TABLE" not in upgrade)

    # 2. Extract the migration statements from the real onUpgrade code.
    stmts = re.findall(r'execSQL\("([^"]+)"\)', upgrade)
    stmts = [s for s in stmts if s.startswith("ALTER TABLE")]
    check("onUpgrade has exactly 3 ALTER TABLE statements", len(stmts) == 3)
    for s in stmts:
        print("     migration sql: " + s)

    # 3. Build a v1 database with realistic rows.
    tmp = tempfile.mkdtemp()
    path = str(Path(tmp) / "scrolltrack.db")
    db = sqlite3.connect(path)
    db.execute(V1_SESSIONS)
    db.execute(V1_DAY_STATS)
    # Session spanning midnight (23:30 -> 00:30), a normal session, an orphan
    # left open by a killed process, and a time_only session.
    db.execute("INSERT INTO sessions (startTime,endTime,swipeCount,endedCleanly,quality)"
               " VALUES (1792434600000,1792438200000,87,1,'full')")
    db.execute("INSERT INTO sessions (startTime,endTime,swipeCount,endedCleanly,quality)"
               " VALUES (1792472400000,1792476000000,342,1,'full')")
    db.execute("INSERT INTO sessions (startTime,endTime,swipeCount,endedCleanly,quality)"
               " VALUES (1792490400000,0,12,0,'full')")
    db.execute("INSERT INTO sessions (startTime,endTime,swipeCount,endedCleanly,quality)"
               " VALUES (1792555200000,1792558800000,0,1,'time_only')")
    # Day stats incl. the Highest Daily Count record (342) and notify flags.
    db.execute("INSERT INTO day_stats VALUES ('2026-10-19',87,1,3600000,3600000,1,1,0)")
    db.execute("INSERT INTO day_stats VALUES ('2026-10-20',342,2,5400000,3600000,1,1,1)")
    db.execute("INSERT INTO day_stats VALUES ('2026-10-21',12,1,1800000,1800000,0,0,0)")
    db.commit()
    before_sessions = db.execute("SELECT * FROM sessions ORDER BY id").fetchall()
    before_days = db.execute("SELECT * FROM day_stats ORDER BY day").fetchall()
    db.execute("PRAGMA user_version = 1")
    db.close()

    # 4. Apply the real migration statements (as Android's onUpgrade would).
    db = sqlite3.connect(path)
    for s in stmts:
        db.execute(s)
    db.execute("PRAGMA user_version = 2")
    db.commit()

    after_sessions = db.execute("SELECT * FROM sessions ORDER BY id").fetchall()
    after_days = db.execute("SELECT * FROM day_stats ORDER BY day").fetchall()

    check("all 4 sessions survive", len(after_sessions) == 4)
    check("all 3 day_stats survive", len(after_days) == 3)
    check("session rows byte-identical for old columns",
          [r[:7] for r in after_sessions] == [r for r in before_sessions])
    check("day_stats rows identical for old columns",
          [r[:8] for r in after_days] == [r for r in before_days])
    check("Highest Daily Count (342) intact",
          db.execute("SELECT MAX(swipeCount) FROM day_stats").fetchone()[0] == 342)
    check("open orphan session still open (endTime=0)",
          db.execute("SELECT COUNT(*) FROM sessions WHERE endTime=0").fetchone()[0] == 1)

    cols_s = [r[1] for r in db.execute("PRAGMA table_info(sessions)")]
    cols_d = [r[1] for r in db.execute("PRAGMA table_info(day_stats)")]
    check("sessions gains scrollingDurationMs + lastSeenMs",
          "scrollingDurationMs" in cols_s and "lastSeenMs" in cols_s)
    check("day_stats gains scrollingDurationMs",
          "scrollingDurationMs" in cols_d)
    check("new columns default to 0 on old rows",
          db.execute("SELECT COUNT(*) FROM sessions WHERE scrollingDurationMs<>0"
                     " OR lastSeenMs<>0").fetchone()[0] == 0
          and db.execute("SELECT COUNT(*) FROM day_stats WHERE scrollingDurationMs<>0")
          .fetchone()[0] == 0)
    check("user_version is 2",
          db.execute("PRAGMA user_version").fetchone()[0] == 2)
    db.close()

    if failures:
        print("MIGRATION TEST FAILED: %d" % len(failures))
        sys.exit(1)
    print("MIGRATION TEST PASSED")


if __name__ == "__main__":
    main()
