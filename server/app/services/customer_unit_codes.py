from __future__ import annotations

import sqlite3
from dataclasses import asdict, dataclass


CUSTOMER_UNIT_CODES = (
    ("001", "三河市公安局"),
    ("002", "三河市公安局燕郊分局"),
    ("003", "燕郊特巡警大队"),
    ("004", "三河特巡警大队"),
    ("005", "刑警大队"),
    ("006", "新集派出所"),
    ("007", "皇庄派出所"),
    ("008", "杨庄派出所"),
    ("009", "段甲岭派出所"),
    ("010", "黄土庄派出所"),
    ("011", "泃阳派出所"),
    ("012", "泃阳西派出所"),
    ("013", "鼎盛东派出所"),
    ("014", "李旗庄派出所"),
    ("015", "齐心庄派出所"),
    ("016", "高楼派出所"),
    ("017", "康城派出所"),
    ("018", "燕郊派出所"),
    ("019", "燕顺路派出所"),
    ("020", "迎宾北派出所"),
    ("021", "行宫东派出所"),
)


def normalize_unit_name(value: str) -> str:
    return " ".join(str(value or "").strip().split())


@dataclass(frozen=True)
class UnitCodeMatch:
    unit_code: str
    unit_name: str
    status: str
    unit_id: str = ""
    current_unit_code: str = ""
    detail: str = ""

    def payload(self) -> dict:
        return asdict(self)


def preview_customer_unit_codes(conn: sqlite3.Connection) -> list[UnitCodeMatch]:
    units = [dict(row) for row in conn.execute("SELECT id, unit_code, unit_name FROM units ORDER BY id")]
    by_name: dict[str, list[dict]] = {}
    for unit in units:
        by_name.setdefault(normalize_unit_name(unit["unit_name"]), []).append(unit)

    matches: list[UnitCodeMatch] = []
    for desired_code, desired_name in CUSTOMER_UNIT_CODES:
        candidates = by_name.get(normalize_unit_name(desired_name), [])
        if not candidates:
            matches.append(UnitCodeMatch(desired_code, desired_name, "MISSING"))
            continue
        if len(candidates) != 1:
            matches.append(
                UnitCodeMatch(
                    desired_code,
                    desired_name,
                    "AMBIGUOUS",
                    detail=f"匹配到 {len(candidates)} 个同名单位",
                )
            )
            continue
        unit = candidates[0]
        owner = next((row for row in units if row["unit_code"] == desired_code and row["id"] != unit["id"]), None)
        if owner:
            matches.append(
                UnitCodeMatch(
                    desired_code,
                    desired_name,
                    "CODE_CONFLICT",
                    unit_id=unit["id"],
                    current_unit_code=unit["unit_code"],
                    detail=f"编码已被单位 {owner['unit_name']} 使用",
                )
            )
            continue
        matches.append(
            UnitCodeMatch(
                desired_code,
                desired_name,
                "MATCHED",
                unit_id=unit["id"],
                current_unit_code=unit["unit_code"],
            )
        )
    return matches


def apply_customer_unit_codes(conn: sqlite3.Connection, preview: list[UnitCodeMatch] | None = None) -> int:
    matches = preview or preview_customer_unit_codes(conn)
    if len(matches) != len(CUSTOMER_UNIT_CODES) or any(match.status != "MATCHED" for match in matches):
        raise ValueError("21 个单位未全部唯一匹配，禁止回填单位编码")

    # Temporary values avoid transient UNIQUE failures if existing customer
    # codes are being reassigned between two matched units in one transaction.
    for index, match in enumerate(matches, start=1):
        conn.execute(
            "UPDATE units SET unit_code = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
            (f"__CUSTOMER_CODE_BACKFILL_{index:03d}__", match.unit_id),
        )
    for match in matches:
        cursor = conn.execute(
            "UPDATE units SET unit_code = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
            (match.unit_code, match.unit_id),
        )
        if cursor.rowcount != 1:
            raise RuntimeError(f"单位编码回填失败: {match.unit_name}")
    return len(matches)
