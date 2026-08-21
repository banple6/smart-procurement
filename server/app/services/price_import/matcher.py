from __future__ import annotations

from collections import defaultdict
from dataclasses import dataclass

from .parser import normalize_product_name, normalized_text


@dataclass(frozen=True)
class ProductIndexes:
    by_code: dict[str, dict]
    by_normalized_name: dict[str, tuple[dict, ...]]
    product_count: int


def load_product_indexes(conn) -> ProductIndexes:
    """Load all non-deleted products once for a batch without N+1 queries."""
    products = [
        dict(row)
        for row in conn.execute(
            "SELECT id, product_code, name, unit, price_cents, active, is_deleted "
            "FROM products WHERE is_deleted = 0"
        )
    ]
    by_code: dict[str, dict] = {}
    names: dict[str, list[dict]] = defaultdict(list)
    for product in products:
        code = normalized_text(product["product_code"])
        if code:
            by_code.setdefault(code, product)
        name = normalize_product_name(product["name"])
        if name:
            names[name].append(product)
    return ProductIndexes(
        by_code=by_code,
        by_normalized_name={key: tuple(value) for key, value in names.items()},
        product_count=len(products),
    )


def match_product(indexes: ProductIndexes, source_code: str, source_name: str) -> dict:
    """Apply the V1 chain: exact code, exact normalized name, manual selection."""
    code = normalized_text(source_code)
    if code and (product := indexes.by_code.get(code)):
        return {"product": product, "method": "exact_code"}
    name = normalize_product_name(source_name)
    matches = indexes.by_normalized_name.get(name, ()) if name else ()
    if len(matches) == 1:
        return {"product": matches[0], "method": "exact_name"}
    if len(matches) > 1:
        return {
            "product": None,
            "method": "ambiguous_name",
            "warning": "系统中存在多个同名商品，请选择本次要调价的商品",
        }
    return {"product": None, "method": "needs_product_selection"}
