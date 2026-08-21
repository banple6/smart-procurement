ORDER_STATUSES = {"pending", "accepted", "preparing", "shipped", "completed", "cancelled", "voided"}
SUPPLY_STATUSES = {"normal", "tight", "paused", "off_shelf"}
EDITABLE_SUPPLY_STATUSES = {"normal", "tight", "paused"}
PRODUCT_CATEGORIES = {"蔬菜", "水果", "肉禽", "水产", "粮油", "蛋奶", "调料", "其他"}
PRODUCT_UNITS = {"公斤", "斤", "箱", "袋", "个", "筐", "盒", "瓶", "份", "包"}
PRODUCT_STORAGE_METHODS = {"常温", "冷藏", "冷冻", "阴凉干燥"}
ROLES = {"admin", "unit_user"}

AUTO_PRODUCT_SPECS = {"散装", "预包装"}
BULK_PRODUCT_UNITS = {"公斤", "斤"}


def default_product_spec(unit: str) -> str:
    return "散装" if str(unit).strip() in BULK_PRODUCT_UNITS else "预包装"


def resolve_product_spec(unit: str, spec: str | None, previous_spec: str | None = None) -> str:
    value = str(spec or "").strip()
    if value and value not in AUTO_PRODUCT_SPECS:
        return value
    if spec is None and str(previous_spec or "").strip() not in {"", *AUTO_PRODUCT_SPECS}:
        return str(previous_spec).strip()
    return default_product_spec(unit)

ADMIN_TRANSITIONS = {
    "pending": {"accepted"},
    "accepted": {"preparing"},
    "preparing": {"shipped"},
    "shipped": {"completed"},
}

UNIT_TRANSITIONS = {
    "pending": {"cancelled"},
    "shipped": {"completed"},
}
