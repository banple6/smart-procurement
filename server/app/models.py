ORDER_STATUSES = {"pending", "accepted", "preparing", "shipped", "completed", "cancelled"}
SUPPLY_STATUSES = {"normal", "tight", "paused", "off_shelf"}
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

