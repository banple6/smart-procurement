from io import BytesIO
from collections import defaultdict

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
    ws.title = "订单台账"
    ws.append(LEDGER_HEADERS)
    summary: dict[tuple[str, str, str, str], dict[str, float | int | str]] = defaultdict(lambda: {"quantity": 0.0, "subtotal_cents": 0})
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
        key = (
            row["product_code_snapshot"],
            row["product_name_snapshot"],
            row["category_snapshot"],
            row["unit_snapshot"],
        )
        summary[key]["quantity"] = float(summary[key]["quantity"]) + float(row["quantity"])
        summary[key]["subtotal_cents"] = int(summary[key]["subtotal_cents"]) + int(row["subtotal_cents"])
    summary_ws = wb.create_sheet("商品需求汇总")
    summary_ws.append(["商品编码", "商品名称", "分类", "单位", "需求数量", "需求金额"])
    for (code, name, category, unit), values in sorted(summary.items(), key=lambda item: item[0][1]):
        summary_ws.append(
            [
                code,
                name,
                category,
                unit,
                values["quantity"],
                int(values["subtotal_cents"]) / 100,
            ]
        )
    stream = BytesIO()
    wb.save(stream)
    return stream.getvalue()
