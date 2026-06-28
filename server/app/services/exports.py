from io import BytesIO

from openpyxl import Workbook


LEDGER_HEADERS = [
    "订单编号",
    "子单位",
    "配送点",
    "下单时间",
    "订单状态",
    "商品编码",
    "商品名称",
    "分类",
    "规格",
    "单位",
    "数量",
    "下单单价",
    "小计",
    "订单总金额",
    "接单时间",
    "发货时间",
    "完成时间",
    "备注",
]


def ledger_workbook(rows: list[dict]) -> bytes:
    wb = Workbook()
    ws = wb.active
    ws.title = "采购台账"
    ws.append(LEDGER_HEADERS)
    for row in rows:
        ws.append(
            [
                row["order_no"],
                row["unit_name_snapshot"],
                row["delivery_point_snapshot"],
                row["created_at"],
                row["status"],
                row["product_code_snapshot"],
                row["product_name_snapshot"],
                row["category_snapshot"],
                row["spec_snapshot"],
                row["unit_snapshot"],
                row["quantity"],
                row["price_cents_snapshot"] / 100,
                row["subtotal_cents"] / 100,
                row["total_cents"] / 100,
                row["accepted_at"],
                row["shipped_at"],
                row["completed_at"],
                row["note"],
            ]
        )
    stream = BytesIO()
    wb.save(stream)
    return stream.getvalue()
