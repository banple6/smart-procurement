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
    wb.properties.title = "三公鲜配采购台账"
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


PREPARATION_HEADERS = [
    "商品编码",
    "商品名称",
    "规格",
    "单位",
    "申领数量",
    "实际数量",
    "单位数",
    "订单数",
]


def preparation_summary_workbook(rows: list[dict]) -> bytes:
    wb = Workbook()
    wb.properties.title = "三公鲜配今日备货单"
    ws = wb.active
    ws.title = "今日备货汇总"
    ws.append(PREPARATION_HEADERS)
    for row in rows:
        ws.append(
            [
                row["product_code"],
                row["product_name"],
                row["spec"],
                row["unit"],
                row["requested_quantity"],
                row["actual_quantity"],
                row["unit_count"],
                row["order_count"],
            ]
        )
    stream = BytesIO()
    wb.save(stream)
    return stream.getvalue()


def delivery_sheets_workbook(units: list[dict]) -> bytes:
    wb = Workbook()
    wb.properties.title = "三公鲜配配送单"
    summary = wb.active
    summary.title = "按商品汇总"
    summary.append(PREPARATION_HEADERS)
    product_totals: dict[tuple[str, str, str, str], dict] = defaultdict(
        lambda: {"requested_quantity": 0.0, "actual_quantity": 0.0, "unit_ids": set(), "order_ids": set()}
    )
    for unit in units:
        for order in unit["orders"]:
            for item in order["items"]:
                key = (item["product_code"], item["product_name"], item["spec"], item["unit"])
                product_totals[key]["requested_quantity"] += float(item["requested_quantity"])
                product_totals[key]["actual_quantity"] += float(item["actual_quantity"])
                product_totals[key]["unit_ids"].add(unit["unit_id"])
                product_totals[key]["order_ids"].add(order["order_id"])
    for (code, name, spec, unit_name), values in sorted(product_totals.items(), key=lambda item: item[0][1]):
        summary.append(
            [
                code,
                name,
                spec,
                unit_name,
                values["requested_quantity"],
                values["actual_quantity"],
                len(values["unit_ids"]),
                len(values["order_ids"]),
            ]
        )

    detail = wb.create_sheet("按单位配送")
    detail.append(["单位名称", "配送点", "订单编号", "状态", "商品编码", "商品名称", "规格", "单位", "申领数量", "实际数量", "调整原因"])
    for unit in units:
        for order in unit["orders"]:
            for item in order["items"]:
                detail.append(
                    [
                        unit["unit_name"],
                        unit["delivery_point"],
                        order["order_no"],
                        order["status"],
                        item["product_code"],
                        item["product_name"],
                        item["spec"],
                        item["unit"],
                        item["requested_quantity"],
                        item["actual_quantity"],
                        item["adjustment_reason"],
                    ]
                )
    stream = BytesIO()
    wb.save(stream)
    return stream.getvalue()
