import logging


LOGGER = logging.getLogger(__name__)

ORDER_STATUS_DEFINITIONS = {
    "pending": {"label": "待接单", "stage": 1, "terminal": False},
    "accepted": {"label": "已接单", "stage": 2, "terminal": False},
    "preparing": {"label": "备货中", "stage": 3, "terminal": False},
    "shipped": {"label": "已发货", "stage": 4, "terminal": False},
    "completed": {"label": "已完成", "stage": 5, "terminal": True},
    "cancelled": {"label": "已取消", "stage": 0, "terminal": True},
}


def order_status_payload(status: str) -> dict:
    definition = ORDER_STATUS_DEFINITIONS.get(status)
    if definition is None:
        LOGGER.warning("unknown_order_status status=%s", status)
        return {"status_label": "未知状态", "status_stage": 0, "is_terminal": False}
    return {
        "status_label": definition["label"],
        "status_stage": definition["stage"],
        "is_terminal": definition["terminal"],
    }
