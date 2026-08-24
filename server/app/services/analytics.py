from __future__ import annotations

from collections import defaultdict
from dataclasses import dataclass
from datetime import date, datetime, time, timedelta, timezone
from decimal import Decimal, InvalidOperation
from zoneinfo import ZoneInfo

from fastapi import HTTPException

from ..database import all_rows, connect, decimal_text, one


SHANGHAI = ZoneInfo("Asia/Shanghai")
UTC = timezone.utc
EXCLUDED_ORDER_STATUSES = {"cancelled", "voided"}
OPEN_ISSUE_STATUSES = {"open", "processing"}
RISK_TEXT = {
    "out_of_stock": "库存不足",
    "warning": "低于预警",
    "tight": "库存紧张",
    "paused": "暂停供应",
    "normal": "正常",
}


@dataclass(frozen=True)
class AnalyticsRange:
    start_date: date
    end_date: date
    start_utc: datetime
    end_utc: datetime

    @property
    def days(self) -> int:
        return (self.end_date - self.start_date).days + 1

    def payload(self) -> dict:
        return {
            "start_date": self.start_date.isoformat(),
            "end_date": self.end_date.isoformat(),
            "days": self.days,
            "timezone": "Asia/Shanghai",
        }


def parse_range(start_date: str | None, end_date: str | None) -> AnalyticsRange:
    today = datetime.now(SHANGHAI).date()
    try:
        end = date.fromisoformat(end_date) if end_date else today
        start = date.fromisoformat(start_date) if start_date else end - timedelta(days=29)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail="日期格式不正确，请使用 YYYY-MM-DD") from exc
    if start > end:
        raise HTTPException(status_code=400, detail="开始日期不能晚于结束日期")
    if (end - start).days >= 365:
        raise HTTPException(status_code=400, detail="单次查询最多支持 365 天")
    start_local = datetime.combine(start, time.min, tzinfo=SHANGHAI)
    end_local = datetime.combine(end + timedelta(days=1), time.min, tzinfo=SHANGHAI)
    return AnalyticsRange(start, end, start_local.astimezone(UTC), end_local.astimezone(UTC))


def _sql_time(value: datetime) -> str:
    return value.replace(tzinfo=None).strftime("%Y-%m-%d %H:%M:%S")


def _parse_utc(value: str) -> datetime:
    parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    return parsed.replace(tzinfo=UTC) if parsed.tzinfo is None else parsed.astimezone(UTC)


def _decimal(value) -> Decimal:
    try:
        return Decimal(str(value or "0"))
    except (InvalidOperation, ValueError):
        return Decimal("0")


def _actual_quantity(row: dict) -> Decimal:
    for key in ("actual_quantity", "requested_quantity", "quantity"):
        value = row.get(key)
        if value is not None and str(value).strip() != "":
            return _decimal(value)
    return Decimal("0")


def _percent(current: int | Decimal, previous: int | Decimal) -> float | None:
    previous_value = Decimal(str(previous))
    if previous_value == 0:
        return None
    value = (Decimal(str(current)) - previous_value) * Decimal("100") / previous_value
    return float(value.quantize(Decimal("0.1")))


def _clauses(period: AnalyticsRange, unit_id: str | None = None) -> tuple[str, list]:
    parts = [
        "COALESCE(o.is_deleted, 0) = 0",
        "o.status NOT IN ('cancelled', 'voided')",
        "o.created_at >= ?",
        "o.created_at < ?",
    ]
    params: list = [_sql_time(period.start_utc), _sql_time(period.end_utc)]
    if unit_id:
        parts.append("o.unit_id = ?")
        params.append(unit_id)
    return " AND ".join(parts), params


def _range_rows(conn, period: AnalyticsRange, unit_id: str | None, category: str | None):
    where, params = _clauses(period, unit_id)
    orders = all_rows(
        conn,
        f"""
        SELECT o.id, o.order_no, o.unit_id, o.unit_name_snapshot, o.total_cents, o.created_at, o.status
        FROM orders o
        WHERE {where}
        ORDER BY o.created_at, o.id
        """,
        params,
    )
    items = all_rows(
        conn,
        f"""
        SELECT o.id AS order_id, o.unit_id, o.unit_name_snapshot, o.created_at,
               i.product_id, i.product_name_snapshot, i.category_snapshot, i.unit_snapshot,
               i.quantity, i.requested_quantity, i.actual_quantity,
               i.price_cents_snapshot, i.subtotal_cents
        FROM orders o
        JOIN order_items i ON i.order_id = o.id
        WHERE {where}
          {"AND i.category_snapshot = ?" if category else ""}
        ORDER BY o.created_at, o.id, i.id
        """,
        [*params, category] if category else params,
    )
    if category:
        matching = {row["order_id"] for row in items}
        orders = [row for row in orders if row["id"] in matching]
    return orders, items


def _period_summary(conn, period: AnalyticsRange, unit_id: str | None, category: str | None) -> dict:
    orders, items = _range_rows(conn, period, unit_id, category)
    total_cents = sum(int(row["subtotal_cents"] or 0) for row in items) if category else sum(
        int(row["total_cents"] or 0) for row in orders
    )
    return {
        "valid_order_count": len(orders),
        "total_cents": total_cents,
        "unit_count": len({row["unit_id"] for row in orders}),
        "product_count": len({(row["product_id"], row["unit_snapshot"]) for row in items}),
    }


def _comparison_range(period: AnalyticsRange) -> AnalyticsRange:
    previous_end = period.start_date - timedelta(days=1)
    previous_start = previous_end - timedelta(days=period.days - 1)
    return parse_range(previous_start.isoformat(), previous_end.isoformat())


def _trend(orders: list[dict], items: list[dict], period: AnalyticsRange, category: str | None) -> list[dict]:
    order_ids_by_day: dict[str, set[str]] = defaultdict(set)
    amount_by_day: dict[str, int] = defaultdict(int)
    if category:
        for row in items:
            day = _parse_utc(row["created_at"]).astimezone(SHANGHAI).date().isoformat()
            order_ids_by_day[day].add(row["order_id"])
            amount_by_day[day] += int(row["subtotal_cents"] or 0)
    else:
        for row in orders:
            day = _parse_utc(row["created_at"]).astimezone(SHANGHAI).date().isoformat()
            order_ids_by_day[day].add(row["id"])
            amount_by_day[day] += int(row["total_cents"] or 0)
    return [
        {
            "date": (period.start_date + timedelta(days=offset)).isoformat(),
            "order_count": len(order_ids_by_day[(period.start_date + timedelta(days=offset)).isoformat()]),
            "total_cents": amount_by_day[(period.start_date + timedelta(days=offset)).isoformat()],
        }
        for offset in range(period.days)
    ]


def _demand_rank(items: list[dict], limit: int = 10) -> list[dict]:
    totals: dict[tuple[str, str, str, str], Decimal] = defaultdict(lambda: Decimal("0"))
    orders: dict[tuple[str, str, str, str], set[str]] = defaultdict(set)
    units: dict[tuple[str, str, str, str], set[str]] = defaultdict(set)
    subtotals: dict[tuple[str, str, str, str], int] = defaultdict(int)
    for row in items:
        key = (
            row["product_id"],
            row["product_name_snapshot"],
            row["category_snapshot"],
            row["unit_snapshot"],
        )
        totals[key] += _actual_quantity(row)
        orders[key].add(row["order_id"])
        units[key].add(row["unit_id"])
        subtotals[key] += int(row["subtotal_cents"] or 0)
    ranked = sorted(totals.items(), key=lambda item: (-item[1], item[0][1], item[0][3]))[:limit]
    return [
        {
            "product_id": key[0],
            "product_name": key[1],
            "category": key[2],
            "unit": key[3],
            "quantity": decimal_text(quantity),
            "unit_count": len(units[key]),
            "order_count": len(orders[key]),
            "subtotal_cents": subtotals[key],
        }
        for key, quantity in ranked
    ]


def overview(
    start_date: str | None,
    end_date: str | None,
    unit_id: str | None,
    category: str | None,
    limit: int = 10,
) -> dict:
    period = parse_range(start_date, end_date)
    previous = _comparison_range(period)
    with connect() as conn:
        orders, items = _range_rows(conn, period, unit_id, category)
        current = _period_summary(conn, period, unit_id, category)
        prior = _period_summary(conn, previous, unit_id, category)
        issue_where, issue_params = _clauses(period, unit_id)
        category_filter = "AND EXISTS (SELECT 1 FROM order_items oi WHERE oi.order_id=o.id AND oi.category_snapshot=?)" if category else ""
        issues = one(
            conn,
            f"""
            SELECT COUNT(*) AS count
            FROM receipt_issues r JOIN orders o ON o.id=r.order_id
            WHERE r.status IN ('open', 'processing') AND {issue_where} {category_filter}
            """,
            [*issue_params, category] if category else issue_params,
        )["count"]
        inventory_params: list = []
        inventory_category = ""
        if category:
            inventory_category = "AND category=?"
            inventory_params.append(category)
        inventory_rows = all_rows(
            conn,
            f"""
            SELECT stock_quantity, reserved_quantity, warning_quantity, supply_status, active
            FROM products WHERE COALESCE(is_deleted, 0)=0 {inventory_category}
            """,
            inventory_params,
        )
    current["open_receipt_issues"] = int(issues or 0)
    current["inventory_alert_count"] = sum(
        1
        for row in inventory_rows
        if (_decimal(row["stock_quantity"]) - _decimal(row["reserved_quantity"]) <= _decimal(row["warning_quantity"]))
        or row["supply_status"] in {"tight", "paused"}
        or not bool(row["active"])
    )
    return {
        "range": period.payload(),
        "filters": {"unit_id": unit_id or "", "category": category or ""},
        "summary": current,
        "comparison": {
            "valid_order_count_percent": _percent(current["valid_order_count"], prior["valid_order_count"]),
            "total_cents_percent": _percent(current["total_cents"], prior["total_cents"]),
            "unit_count_percent": _percent(current["unit_count"], prior["unit_count"]),
            "product_count_percent": _percent(current["product_count"], prior["product_count"]),
        },
        "trend": _trend(orders, items, period, category),
        "demand_rank": _demand_rank(items, limit),
    }


def units(start_date: str | None, end_date: str | None, category: str | None, sort: str) -> dict:
    period = parse_range(start_date, end_date)
    with connect() as conn:
        orders, items = _range_rows(conn, period, None, category)
        issue_where, issue_params = _clauses(period)
        issues = all_rows(
            conn,
            f"""
            SELECT o.unit_id, COUNT(*) AS count
            FROM receipt_issues r JOIN orders o ON o.id=r.order_id
            WHERE r.status IN ('open', 'processing') AND {issue_where}
              {"AND EXISTS (SELECT 1 FROM order_items oi WHERE oi.order_id=o.id AND oi.category_snapshot=?)" if category else ""}
            GROUP BY o.unit_id
            """,
            [*issue_params, category] if category else issue_params,
        )
    amount_by_unit: dict[str, int] = defaultdict(int)
    order_ids_by_unit: dict[str, set[str]] = defaultdict(set)
    products_by_unit: dict[str, set[tuple[str, str]]] = defaultdict(set)
    names: dict[str, str] = {}
    if category:
        for row in items:
            names[row["unit_id"]] = row["unit_name_snapshot"]
            order_ids_by_unit[row["unit_id"]].add(row["order_id"])
            amount_by_unit[row["unit_id"]] += int(row["subtotal_cents"] or 0)
            products_by_unit[row["unit_id"]].add((row["product_id"], row["unit_snapshot"]))
    else:
        for row in orders:
            names[row["unit_id"]] = row["unit_name_snapshot"]
            order_ids_by_unit[row["unit_id"]].add(row["id"])
            amount_by_unit[row["unit_id"]] += int(row["total_cents"] or 0)
        for row in items:
            products_by_unit[row["unit_id"]].add((row["product_id"], row["unit_snapshot"]))
    issue_map = {row["unit_id"]: int(row["count"] or 0) for row in issues}
    result = [
        {
            "unit_id": unit_id,
            "unit_name": names[unit_id],
            "order_count": len(order_ids_by_unit[unit_id]),
            "total_cents": amount_by_unit[unit_id],
            "product_count": len(products_by_unit[unit_id]),
            "open_receipt_issues": issue_map.get(unit_id, 0),
            "open_issue_count": issue_map.get(unit_id, 0),
        }
        for unit_id in names
    ]
    sort_key = {
        "amount": lambda row: (-row["total_cents"], row["unit_name"]),
        "orders": lambda row: (-row["order_count"], row["unit_name"]),
        "products": lambda row: (-row["product_count"], row["unit_name"]),
    }[sort]
    result.sort(key=sort_key)
    return {"range": period.payload(), "category": category or "", "sort": sort, "items": result}


def _price_events(conn, period: AnalyticsRange, product_id: str | None = None) -> list[dict]:
    params: list = [_sql_time(period.end_utc)]
    product_filter = ""
    if product_id:
        product_filter = "AND l.product_id = ?"
        params.append(product_id)
    return all_rows(
        conn,
        f"""
        SELECT l.id, l.product_id, l.old_price_cents, l.new_price_cents, l.created_at,
               p.name AS product_name, p.category, p.unit
        FROM product_price_logs l
        JOIN products p ON p.id=l.product_id
        WHERE l.created_at < ? AND COALESCE(p.is_deleted, 0)=0 {product_filter}
        ORDER BY l.product_id, l.created_at, l.id
        """,
        params,
    )


def prices(start_date: str | None, end_date: str | None, category: str | None, product_id: str | None) -> dict:
    period = parse_range(start_date, end_date)
    with connect() as conn:
        events = _price_events(conn, period, product_id)
    grouped: dict[str, list[dict]] = defaultdict(list)
    for event in events:
        if category and event["category"] != category:
            continue
        grouped[event["product_id"]].append(event)
    items = []
    for _, values in grouped.items():
        before = [event for event in values if _parse_utc(event["created_at"]) < period.start_utc]
        within = [event for event in values if period.start_utc <= _parse_utc(event["created_at"]) < period.end_utc]
        initial = int(before[-1]["new_price_cents"]) if before else (
            int(within[0]["old_price_cents"]) if within and within[0]["old_price_cents"] is not None else None
        )
        end_price = int(values[-1]["new_price_cents"])
        period_prices = ([initial] if initial is not None else []) + [int(event["new_price_cents"]) for event in within]
        item = values[-1]
        items.append({
            "product_id": item["product_id"],
            "product_name": item["product_name"],
            "category": item["category"],
            "unit": item["unit"],
            "initial_price_cents": initial,
            "current_price_cents": end_price,
            "min_price_cents": min(period_prices) if period_prices else end_price,
            "max_price_cents": max(period_prices) if period_prices else end_price,
            "change_percent": _percent(end_price, initial) if initial not in (None, 0) else None,
            "change_cents": end_price - initial if initial is not None else None,
            "is_new": initial is None,
            "change_count": len(within),
        })
    items.sort(key=lambda row: (row["change_percent"] is None, -(abs(row["change_percent"] or 0)), row["product_name"]))
    return {"range": period.payload(), "category": category or "", "product_id": product_id or "", "items": items}


def inventory() -> dict:
    demand_end = datetime.now(SHANGHAI).date()
    demand_period = parse_range((demand_end - timedelta(days=13)).isoformat(), demand_end.isoformat())
    with connect() as conn:
        products = all_rows(
            conn,
            """
            SELECT id, product_code, name, category, spec, unit, stock_quantity, reserved_quantity,
                   warning_quantity, supply_status, active, updated_at
            FROM products WHERE COALESCE(is_deleted, 0)=0
            """,
        )
        _, demand_items = _range_rows(conn, demand_period, None, None)
    demand: dict[tuple[str, str], Decimal] = defaultdict(lambda: Decimal("0"))
    for row in demand_items:
        demand[(row["product_id"], row["unit_snapshot"])] += _actual_quantity(row)
    result = []
    risk_count: dict[str, int] = defaultdict(int)
    for product in products:
        stock = _decimal(product["stock_quantity"])
        reserved = _decimal(product["reserved_quantity"])
        available = stock - reserved
        warning = _decimal(product["warning_quantity"])
        average = demand[(product["id"], product["unit"])] / Decimal(str(demand_period.days))
        if available <= 0:
            risk = "out_of_stock"
        elif warning > 0 and available <= warning:
            risk = "warning"
        elif product["supply_status"] == "tight":
            risk = "tight"
        elif not bool(product["active"]) or product["supply_status"] == "paused":
            risk = "paused"
        else:
            risk = "normal"
        risk_count[risk] += 1
        estimated = available / average if average > 0 else None
        result.append({
            "product_id": product["id"],
            "product_code": product["product_code"],
            "product_name": product["name"],
            "category": product["category"],
            "spec": product["spec"],
            "unit": product["unit"],
            "stock_quantity": decimal_text(stock),
            "reserved_quantity": decimal_text(reserved),
            "available_quantity": decimal_text(available),
            "warning_quantity": decimal_text(warning),
            "average_daily_demand": decimal_text(average),
            "estimated_days_available": decimal_text(estimated.quantize(Decimal("0.1"))) if estimated is not None else None,
            "supply_status": product["supply_status"],
            "active": bool(product["active"]),
            "risk": risk,
            "risk_level": risk,
            "risk_text": RISK_TEXT[risk],
            "updated_at": product["updated_at"],
        })
    priority = {"out_of_stock": 0, "warning": 1, "tight": 2, "paused": 3, "normal": 4}
    result.sort(key=lambda row: (priority[row["risk"]], _decimal(row["available_quantity"]), row["product_name"]))
    return {
        "as_of": datetime.now(SHANGHAI).isoformat(timespec="seconds"),
        "demand_window": demand_period.payload(),
        "summary": {"product_count": len(result), **{key: risk_count.get(key, 0) for key in priority}},
        "items": result,
    }


def product_detail(product_id: str, start_date: str | None, end_date: str | None) -> dict:
    period = parse_range(start_date, end_date)
    with connect() as conn:
        product = one(conn, "SELECT * FROM products WHERE id=? AND COALESCE(is_deleted, 0)=0", (product_id,))
        if not product:
            raise HTTPException(status_code=404, detail="食材不存在")
        events = _price_events(conn, period, product_id)
        _, items = _range_rows(conn, period, None, None)
    product_items = [row for row in items if row["product_id"] == product_id and row["unit_snapshot"] == product["unit"]]
    quantities: dict[str, Decimal] = defaultdict(lambda: Decimal("0"))
    amounts: dict[str, int] = defaultdict(int)
    units_total: dict[tuple[str, str], Decimal] = defaultdict(lambda: Decimal("0"))
    for row in product_items:
        day = _parse_utc(row["created_at"]).astimezone(SHANGHAI).date().isoformat()
        quantity = _actual_quantity(row)
        quantities[day] += quantity
        amounts[day] += int(row["subtotal_cents"] or 0)
        units_total[(row["unit_id"], row["unit_name_snapshot"])] += quantity
    demand_trend = [
        {
            "date": (period.start_date + timedelta(days=offset)).isoformat(),
            "quantity": decimal_text(quantities[(period.start_date + timedelta(days=offset)).isoformat()]),
            "subtotal_cents": amounts[(period.start_date + timedelta(days=offset)).isoformat()],
        }
        for offset in range(period.days)
    ]
    price_history = [
        {
            "created_at": event["created_at"],
            "old_price_cents": event["old_price_cents"],
            "new_price_cents": event["new_price_cents"],
        }
        for event in events
        if period.start_utc <= _parse_utc(event["created_at"]) < period.end_utc
    ]
    unit_rank = sorted(units_total.items(), key=lambda item: (-item[1], item[0][1]))
    available = _decimal(product["stock_quantity"]) - _decimal(product["reserved_quantity"])
    period_order_ids = {row["order_id"] for row in product_items}
    period_quantity = sum((_actual_quantity(row) for row in product_items), Decimal("0"))
    period_amount = sum(int(row["subtotal_cents"] or 0) for row in product_items)
    before = [event for event in events if _parse_utc(event["created_at"]) < period.start_utc]
    within = [event for event in events if period.start_utc <= _parse_utc(event["created_at"]) < period.end_utc]
    range_start_price = int(before[-1]["new_price_cents"]) if before else (
        int(within[0]["old_price_cents"]) if within and within[0]["old_price_cents"] is not None else None
    )
    current_price = int(events[-1]["new_price_cents"]) if events else int(product["price_cents"])
    range_prices = ([range_start_price] if range_start_price is not None else []) + [
        int(event["new_price_cents"]) for event in within
    ]
    return {
        "range": period.payload(),
        "product": {
            "id": product["id"], "product_code": product["product_code"], "name": product["name"],
            "category": product["category"], "spec": product["spec"], "unit": product["unit"],
            "price_cents": int(product["price_cents"]), "stock_quantity": product["stock_quantity"],
            "reserved_quantity": product["reserved_quantity"], "supply_status": product["supply_status"],
        },
        "inventory": {
            "stock_quantity": decimal_text(product["stock_quantity"]),
            "reserved_quantity": decimal_text(product["reserved_quantity"]),
            "available_quantity": decimal_text(available),
            "warning_quantity": decimal_text(product["warning_quantity"]),
            "supply_status": product["supply_status"],
        },
        "current": {
            "price_cents": current_price,
            "stock_quantity": decimal_text(product["stock_quantity"]),
            "reserved_quantity": decimal_text(product["reserved_quantity"]),
            "available_quantity": decimal_text(available),
            "warning_quantity": decimal_text(product["warning_quantity"]),
            "supply_status": product["supply_status"],
        },
        "price": {
            "current_cents": current_price,
            "range_start_cents": range_start_price,
            "min_cents": min(range_prices) if range_prices else current_price,
            "max_cents": max(range_prices) if range_prices else current_price,
            "change_cents": current_price - range_start_price if range_start_price is not None else None,
            "change_percent": _percent(current_price, range_start_price) if range_start_price not in (None, 0) else None,
            "is_new": range_start_price is None,
        },
        "period": {
            "order_count": len(period_order_ids),
            "quantity": decimal_text(period_quantity),
            "amount_cents": period_amount,
            "unit_count": len({row["unit_id"] for row in product_items}),
            "unit": product["unit"],
        },
        "price_history": price_history,
        "demand_trend": demand_trend,
        "unit_rank": [
            {"unit_id": key[0], "unit_name": key[1], "quantity": decimal_text(quantity), "unit": product["unit"]}
            for key, quantity in unit_rank
        ],
        "unit_demand": [
            {"unit_id": key[0], "unit_name": key[1], "quantity": decimal_text(quantity), "unit": product["unit"]}
            for key, quantity in unit_rank
        ],
    }
