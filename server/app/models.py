ORDER_STATUSES = {"pending", "accepted", "preparing", "shipped", "completed", "cancelled"}
SUPPLY_STATUSES = {"normal", "tight", "paused", "off_shelf"}
EDITABLE_SUPPLY_STATUSES = {"normal", "tight", "paused"}
PRODUCT_CATEGORIES = {"蔬菜", "水果", "肉禽", "水产", "粮油", "蛋奶", "调料", "其他"}
PRODUCT_UNITS = {"公斤", "斤", "箱", "袋", "个", "筐", "盒", "瓶", "份", "包"}
PRODUCT_STORAGE_METHODS = {"常温", "冷藏", "冷冻", "阴凉干燥"}
ROLES = {"admin", "unit_user"}

ADMIN_TRANSITIONS = {
    "pending": {"accepted", "cancelled"},
    "accepted": {"preparing", "cancelled"},
    "preparing": {"shipped", "cancelled"},
    "shipped": {"completed"},
}

UNIT_TRANSITIONS = {
    "pending": {"cancelled"},
    "shipped": {"completed"},
}
