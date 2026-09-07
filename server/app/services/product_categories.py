from __future__ import annotations

import sqlite3

from fastapi import HTTPException


LEGACY_ANDROID_CATEGORIES = {"肉禽", "蛋奶"}
BOOTSTRAP_CATEGORIES = ["蔬菜", "水果", "肉禽", "肉类", "水产", "冻货", "粮油", "蛋奶", "鸡蛋", "牛奶", "调料", "其他"]
MAX_CATEGORY_NAME_LENGTH = 40


def normalize_category_name(value: object) -> str:
    name = str(value or "").strip()
    if not name:
        raise HTTPException(status_code=400, detail="分类名称不能为空")
    if len(name) > MAX_CATEGORY_NAME_LENGTH:
        raise HTTPException(status_code=400, detail=f"分类名称不能超过 {MAX_CATEGORY_NAME_LENGTH} 个字符")
    return name


def active_category_names(conn: sqlite3.Connection) -> set[str]:
    return {row["name"] for row in conn.execute("SELECT name FROM product_categories WHERE is_active = 1")}


def ensure_product_category(conn: sqlite3.Connection, value: object, existing_category: object = None) -> str:
    name = normalize_category_name(value)
    if name == str(existing_category or "").strip():
        return name
    if name not in active_category_names(conn):
        raise HTTPException(status_code=400, detail="食材分类不存在或已停用，请先在分类管理中启用")
    return name


def category_sort_orders(conn: sqlite3.Connection) -> dict[str, int]:
    return {row["name"]: int(row["sort_order"]) for row in conn.execute("SELECT name, sort_order FROM product_categories")}
