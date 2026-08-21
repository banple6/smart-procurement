from collections import OrderedDict
from decimal import Decimal, ROUND_HALF_UP

from ..database import all_rows, decimal_text, one


SCOPE_STATUSES = {
    "all": ("pending", "accepted", "preparing", "shipped", "completed"),
    "picking": ("accepted", "preparing"),
    "shipped": ("shipped", "completed"),
}


def _quantity(value, fallback) -> Decimal:
    text = str(value or "").strip()
    return Decimal(text or str(fallback or "0"))


def _money_cents(price_cents: int, quantity: Decimal) -> int:
    return int((Decimal(price_cents) * quantity).quantize(Decimal("1"), rounding=ROUND_HALF_UP))


def _flat_rows(conn, batch_id: str, scope: str) -> list[dict]:
    statuses = SCOPE_STATUSES.get(scope)
    if not statuses:
        raise ValueError(f"unsupported batch aggregation scope: {scope}")
    placeholders = ",".join("?" for _ in statuses)
    return all_rows(
        conn,
        f"""
        SELECT
          orders.id AS order_id,
          orders.order_no,
          orders.status AS order_status,
          orders.unit_id,
          orders.unit_name_snapshot AS unit_name,
          orders.delivery_point_snapshot AS delivery_point,
          order_items.product_id,
          order_items.product_name_snapshot AS product_name,
          order_items.category_snapshot AS category,
          order_items.spec_snapshot AS spec,
          order_items.unit_snapshot AS quantity_unit,
          order_items.quantity,
          order_items.requested_quantity,
          order_items.actual_quantity,
          order_items.price_cents_snapshot,
          order_items.subtotal_cents
        FROM delivery_batch_orders
        JOIN orders ON orders.id = delivery_batch_orders.order_id
        JOIN order_items ON order_items.order_id = orders.id
        WHERE delivery_batch_orders.batch_id = ?
          AND orders.is_deleted = 0
          AND orders.status IN ({placeholders})
        ORDER BY orders.unit_name_snapshot, orders.order_no,
                 order_items.category_snapshot, order_items.product_name_snapshot, order_items.rowid
        """,
        (batch_id, *statuses),
    )


def aggregate_batch(conn, batch_id: str, scope: str = "all") -> dict:
    batch = one(conn, "SELECT * FROM delivery_batches WHERE id = ?", (batch_id,))
    if not batch:
        raise LookupError("批次不存在")
    rows = _flat_rows(conn, batch_id, scope)
    units: OrderedDict[str, dict] = OrderedDict()
    products: OrderedDict[tuple, dict] = OrderedDict()
    document_lines: OrderedDict[tuple, dict] = OrderedDict()
    order_ids: set[str] = set()

    for row in rows:
        requested = _quantity(row.get("requested_quantity"), row.get("quantity"))
        actual = _quantity(row.get("actual_quantity"), row.get("quantity"))
        price_cents = int(row.get("price_cents_snapshot") or 0)
        line_amount = _money_cents(price_cents, actual)
        order_ids.add(row["order_id"])

        unit_entry = units.setdefault(
            row["unit_id"],
            {
                "unit_id": row["unit_id"],
                "unit_name": row["unit_name"],
                "delivery_point": row["delivery_point"],
                "order_ids": set(),
                "items": OrderedDict(),
                "total_cents": 0,
            },
        )
        unit_entry["order_ids"].add(row["order_id"])
        unit_key = (
            row["product_id"],
            row["category"],
            row["product_name"],
            row["spec"],
            row["quantity_unit"],
        )
        unit_item = unit_entry["items"].setdefault(
            unit_key,
            {
                "product_id": row["product_id"],
                "product_name": row["product_name"],
                "category": row["category"],
                "spec": row["spec"],
                "unit": row["quantity_unit"],
                "requested_quantity": Decimal("0"),
                "actual_quantity": Decimal("0"),
                "subtotal_cents": 0,
            },
        )
        unit_item["requested_quantity"] += requested
        unit_item["actual_quantity"] += actual
        unit_item["subtotal_cents"] += line_amount
        unit_entry["total_cents"] += line_amount

        product_key = unit_key
        product_entry = products.setdefault(
            product_key,
            {
                "product_id": row["product_id"],
                "product_name": row["product_name"],
                "category": row["category"],
                "spec": row["spec"],
                "unit": row["quantity_unit"],
                "requested_quantity": Decimal("0"),
                "actual_quantity": Decimal("0"),
                "subtotal_cents": 0,
                "unit_breakdown": OrderedDict(),
            },
        )
        product_entry["requested_quantity"] += requested
        product_entry["actual_quantity"] += actual
        product_entry["subtotal_cents"] += line_amount
        breakdown = product_entry["unit_breakdown"].setdefault(
            row["unit_id"],
            {
                "unit_id": row["unit_id"],
                "unit_name": row["unit_name"],
                "requested_quantity": Decimal("0"),
                "actual_quantity": Decimal("0"),
            },
        )
        breakdown["requested_quantity"] += requested
        breakdown["actual_quantity"] += actual

        document_key = (*product_key, price_cents)
        document = document_lines.setdefault(
            document_key,
            {
                "product_id": row["product_id"],
                "product_name": row["product_name"],
                "category": row["category"],
                "spec": row["spec"],
                "unit": row["quantity_unit"],
                "price_cents": price_cents,
                "requested_quantity": Decimal("0"),
                "actual_quantity": Decimal("0"),
                "subtotal_cents": 0,
            },
        )
        document["requested_quantity"] += requested
        document["actual_quantity"] += actual
        document["subtotal_cents"] += line_amount

    by_unit = []
    for entry in units.values():
        items = []
        for item in entry["items"].values():
            items.append(
                {
                    **item,
                    "requested_quantity": decimal_text(item["requested_quantity"]),
                    "actual_quantity": decimal_text(item["actual_quantity"]),
                    "quantity": decimal_text(item["actual_quantity"]),
                }
            )
        by_unit.append(
            {
                "unit_id": entry["unit_id"],
                "unit_name": entry["unit_name"],
                "delivery_point": entry["delivery_point"],
                "order_count": len(entry["order_ids"]),
                "total_cents": entry["total_cents"],
                "items": items,
            }
        )

    by_product = []
    for entry in products.values():
        breakdown = [
            {
                **value,
                "requested_quantity": decimal_text(value["requested_quantity"]),
                "actual_quantity": decimal_text(value["actual_quantity"]),
                "quantity": decimal_text(value["actual_quantity"]),
            }
            for value in entry["unit_breakdown"].values()
        ]
        by_product.append(
            {
                **{key: value for key, value in entry.items() if key != "unit_breakdown"},
                "requested_quantity": decimal_text(entry["requested_quantity"]),
                "actual_quantity": decimal_text(entry["actual_quantity"]),
                "total_quantity": decimal_text(entry["actual_quantity"]),
                "unit_count": len(breakdown),
                "unit_breakdown": breakdown,
            }
        )

    normalized_document_lines = [
        {
            **line,
            "requested_quantity": decimal_text(line["requested_quantity"]),
            "actual_quantity": decimal_text(line["actual_quantity"]),
            "quantity": decimal_text(line["actual_quantity"]),
        }
        for line in document_lines.values()
    ]
    return {
        "batch": batch,
        "scope": scope,
        "order_count": len(order_ids),
        "unit_count": len(by_unit),
        "product_count": len(by_product),
        "total_cents": sum(line["subtotal_cents"] for line in normalized_document_lines),
        "by_unit": by_unit,
        "by_product": by_product,
        "document_lines": normalized_document_lines,
    }
