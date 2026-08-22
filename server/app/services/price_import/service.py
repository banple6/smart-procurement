from __future__ import annotations

import hashlib
import json
import os
import time
from collections import Counter
from decimal import Decimal, InvalidOperation
from pathlib import Path
from uuid import uuid4

from fastapi import HTTPException, UploadFile

from ...database import connect, decimal_text, private_upload_dir, transaction, one, write_audit
from ...models import EDITABLE_SUPPLY_STATUSES, PRODUCT_CATEGORIES, PRODUCT_UNITS, resolve_product_spec
from ...services.dashboard_cache import invalidate_dashboard_cache
from .matcher import load_product_indexes, match_product
from ..local_time import display_local_time
from .parser import MAX_FILE_BYTES, SUPPORTED_EXTENSIONS, detect_header, mapping_from_names, normalize_product_name, normalized_text, parse_price_cents, parse_workbook, price_header_unit, unit_conversion
from .semantic_analyzer import PROMPT_VERSION, SpreadsheetSemanticAnalyzer


def import_root() -> Path:
    root = Path(private_upload_dir()).resolve() / "price_imports"
    root.mkdir(parents=True, exist_ok=True)
    return root


def safe_filename(value: str) -> str:
    name = Path(value or "报价表").name
    return name[:180] or "报价表"


async def create_batch(upload: UploadFile, admin: dict) -> dict:
    filename = safe_filename(upload.filename)
    extension = Path(filename).suffix.lower()
    if extension not in SUPPORTED_EXTENSIONS:
        raise HTTPException(status_code=400, detail="仅支持 .xlsx、.xls、.csv 报价文件")
    payload = await upload.read(MAX_FILE_BYTES + 1)
    if not payload:
        raise HTTPException(status_code=400, detail="Excel 文件为空")
    if len(payload) > MAX_FILE_BYTES:
        raise HTTPException(status_code=400, detail="Excel 文件不能超过 10 MB")
    batch_id = str(uuid4())
    target = import_root() / f"{batch_id}{extension}"
    target.write_bytes(payload)
    digest = hashlib.sha256(payload).hexdigest()
    with transaction() as conn:
        conn.execute(
            "INSERT INTO price_import_batches(id, status, source_filename, file_path, file_sha256, uploaded_by) VALUES (?, 'UPLOADED', ?, ?, ?, ?)",
            (batch_id, filename, str(target), digest, admin["id"]),
        )
        write_audit(conn, admin["id"], admin["role"], "PRICE_IMPORT_UPLOADED", "price_import_batch", batch_id, after_json=json.dumps({"filename": filename}, ensure_ascii=False))
    return batch_out(batch_id)


def _batch(conn, batch_id: str) -> dict:
    batch = one(conn, "SELECT * FROM price_import_batches WHERE id = ?", (batch_id,))
    if not batch:
        raise HTTPException(status_code=404, detail="调价批次不存在")
    return batch


def _mapping_names(header: list[str], mapping: dict[str, int]) -> dict[str, str]:
    return {field: header[index] for field, index in mapping.items() if 0 <= index < len(header)}


def _fingerprint(sheet_name: str, header: list[str], mapping: dict[str, int]) -> str:
    normalized_headers = [normalized_text(value) for value in header]
    structural = {"headers": normalized_headers, "columns": len(header), "mapping": sorted(mapping.keys())}
    payload = json.dumps({"prompt_version": PROMPT_VERSION, "structure": structural}, ensure_ascii=False, sort_keys=True)
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


def _template_mapping(conn, fingerprint: str) -> dict | None:
    template = one(conn, "SELECT * FROM excel_import_templates WHERE fingerprint = ?", (fingerprint,))
    if not template:
        return None
    try:
        mapping = json.loads(template["column_mapping_json"])
        if not isinstance(mapping, dict):
            return None
        conn.execute("UPDATE excel_import_templates SET use_count = use_count + 1, last_used_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ?", (template["id"],))
        return mapping
    except (TypeError, ValueError, json.JSONDecodeError):
        return None


def _save_template(conn, fingerprint: str, header: list[str], mapping: dict[str, int], confidence: float, admin_id: str):
    mapping_names = _mapping_names(header, mapping)
    conn.execute(
        """
        INSERT INTO excel_import_templates(id, fingerprint, prompt_version, normalized_headers_json, sheet_signature, column_mapping_json, confidence, confirmed_by, confirmed_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
        ON CONFLICT(fingerprint) DO UPDATE SET column_mapping_json=excluded.column_mapping_json, confidence=excluded.confidence,
          confirmed_by=excluded.confirmed_by, confirmed_at=CURRENT_TIMESTAMP, updated_at=CURRENT_TIMESTAMP
        """,
        (str(uuid4()), fingerprint, PROMPT_VERSION, json.dumps([normalized_text(item) for item in header], ensure_ascii=False), json.dumps({"columns": len(header)}, ensure_ascii=False), json.dumps(mapping_names, ensure_ascii=False), confidence, admin_id),
    )


DEFAULT_NEW_PRODUCT_FIELDS = {
    "category": "其他",
    "spec": "散装",
    "stock_quantity": "0",
    "supply_status": "paused",
    "fallback_unit": "",
    "active": True,
}


def _canonical_product_unit(value: str) -> str:
    normalized = normalized_text(value)
    aliases = {"kg": "公斤", "千克": "公斤", "公斤": "公斤"}
    if normalized in aliases:
        return aliases[normalized]
    return next((unit for unit in PRODUCT_UNITS if normalized == normalized_text(unit)), "")


def _valid_stock_quantity(value: str) -> str | None:
    try:
        quantity = Decimal(str(value).strip())
    except (InvalidOperation, ValueError):
        return None
    return decimal_text(quantity) if quantity >= 0 else None


def _validate_new_product_defaults(fields: dict) -> dict:
    category = str(fields.get("category") or "").strip()
    spec = str(fields.get("spec") or "").strip()
    stock_quantity = _valid_stock_quantity(str(fields.get("stock_quantity") or ""))
    supply_status = str(fields.get("supply_status") or "").strip()
    fallback_unit = _canonical_product_unit(str(fields.get("fallback_unit") or "")) if fields.get("fallback_unit") else ""
    if category not in PRODUCT_CATEGORIES:
        raise HTTPException(status_code=400, detail="新增商品默认分类不正确")
    if not spec:
        raise HTTPException(status_code=400, detail="新增商品默认规格不能为空")
    if stock_quantity is None:
        raise HTTPException(status_code=400, detail="新增商品默认库存不能小于 0")
    if supply_status not in EDITABLE_SUPPLY_STATUSES:
        raise HTTPException(status_code=400, detail="新增商品默认供应状态不正确")
    return {"category": category, "spec": spec, "stock_quantity": stock_quantity, "supply_status": supply_status, "fallback_unit": fallback_unit, "active": bool(fields.get("active", True))}


def _batch_new_product_defaults(batch: dict) -> dict:
    try:
        stored = json.loads(batch.get("new_product_defaults_json") or "{}")
    except (TypeError, ValueError, json.JSONDecodeError):
        stored = {}
    values = dict(DEFAULT_NEW_PRODUCT_FIELDS)
    if isinstance(stored, dict):
        values.update({key: value for key, value in stored.items() if key in values})
    return _validate_new_product_defaults(values)


def _generated_product_code(batch_id: str, source_row: int) -> str:
    return f"IMP-{batch_id.replace('-', '')[:12].upper()}-{source_row}"


def _new_product_values(batch_id: str, source_row: int, source: dict, defaults: dict) -> tuple[dict, str, str]:
    category = source["category"].strip() or defaults["category"]
    unit = _canonical_product_unit(source["unit"]) or defaults["fallback_unit"]
    source_spec = source["spec"].strip()
    fallback_spec = defaults["spec"]
    spec = resolve_product_spec(unit, source_spec or fallback_spec)
    stock_quantity = _valid_stock_quantity(source["stock"]) if source["stock"].strip() else defaults["stock_quantity"]
    values = {
        "product_code": source["code"].strip() or _generated_product_code(batch_id, source_row),
        "category": category,
        "spec": spec,
        "unit": unit,
        "stock_quantity": stock_quantity or "",
        "supply_status": defaults["supply_status"],
        "active": defaults["active"],
    }
    if source["category"].strip() and category not in PRODUCT_CATEGORIES:
        return values, "NEEDS_REVIEW", "Excel 分类无法映射到系统分类，请确认后导入"
    if not unit:
        return values, "NEEDS_REVIEW", "缺少可用计量单位，请在批量默认设置中选择"
    if stock_quantity is None:
        return values, "NEEDS_REVIEW", "Excel 库存格式不正确，请确认后导入"
    return values, "READY", ""


def _build_rows(conn, batch_id: str, sheet_name: str, rows: list[list[str]], header_row: int, mapping: dict[str, int], defaults: dict):
    header = rows[header_row - 1]
    normalized_mapping = _mapping_names(header, mapping)
    inferred_unit = price_header_unit(header[mapping["price"]]) if "price" in mapping else ""
    product_indexes = load_product_indexes(conn)
    seen_existing: dict[str, list[tuple[str, int]]] = {}
    seen_new: dict[str, list[int]] = {}
    built = []
    for index, row in enumerate(rows[header_row:], start=header_row + 1):
        get = lambda field: row[mapping[field]].strip() if field in mapping and mapping[field] < len(row) else ""
        source_name, source_price = get("product_name"), get("price")
        source = {"code": get("product_code"), "name": source_name, "category": get("category"), "spec": get("spec"), "unit": get("unit") or inferred_unit, "stock": get("stock")}
        source_unit = source["unit"]
        if not source_name and not source_price:
            continue
        status, warning = "READY", ""
        cents = parse_price_cents(source_price)
        if not source_name:
            status, warning = "INVALID", "缺少商品名称"
        elif cents is None:
            status, warning = "INVALID", "执行价格为空、格式不正确或超出合理范围"
        result = match_product(product_indexes, source["code"], source_name) if status == "READY" else {"product": None, "method": "invalid"}
        product = result["product"]
        normalized_unit, normalized_cents, factor = source_unit, cents, "1"
        operation_type = "EXISTING_PRODUCT" if product else "NEW_PRODUCT"
        new_values = {"product_code": "", "category": "", "spec": "", "unit": "", "stock_quantity": "0", "supply_status": defaults["supply_status"], "active": defaults["active"]}
        if product and not bool(product["active"]):
            status, warning = "INVALID", "已找到系统商品，但商品当前已停用"
        elif product and cents is not None:
            conversion = unit_conversion(source_unit, product["unit"], cents)
            if conversion is None:
                status, warning = "INVALID", f"单位需要确认：Excel 单位“{source_unit or '未填写'}”无法自动换算为系统单位“{product['unit']}”"
            else:
                normalized_unit, normalized_cents, factor = conversion
        if status == "READY" and not product:
            if result.get("method") == "ambiguous_name":
                operation_type, status = "NEEDS_REVIEW", "NEEDS_REVIEW"
                warning = result.get("warning", "系统中存在多个同名商品，请确认")
            else:
                new_values, status, warning = _new_product_values(batch_id, index, source, defaults)
                normalized_unit, normalized_cents, factor = new_values["unit"] or source_unit, cents, "1"
        if product and status == "READY":
            seen_existing.setdefault(product["id"], []).append((source["spec"], normalized_cents))
        if operation_type == "NEW_PRODUCT" and status == "READY":
            seen_new.setdefault(f"name:{normalize_product_name(source_name)}", []).append(index)
            seen_new.setdefault(f"code:{normalized_text(new_values['product_code'])}", []).append(index)
        built.append({"row": index, "source": source, "price": source_price, "unit_normalized": normalized_unit or "", "cents": normalized_cents, "factor": factor, "product": product, "method": result["method"], "operation_type": operation_type, "new_values": new_values, "status": status, "warning": warning})
    duplicate_ids = {product_id for product_id, entries in seen_existing.items() if len(entries) > 1}
    duplicate_new_rows = {row_number for entries in seen_new.values() if len(entries) > 1 for row_number in entries}
    for row in built:
        if row["product"] and row["product"]["id"] in duplicate_ids and row["status"] != "INVALID":
            row["status"] = "DUPLICATE_CONFLICT"
            row["warning"] = "同一系统食材在 Excel 中存在多个规格或价格，需人工确认"
        if row["operation_type"] == "NEW_PRODUCT" and row["row"] in duplicate_new_rows:
            row["operation_type"], row["status"] = "NEEDS_REVIEW", "NEEDS_REVIEW"
            row["warning"] = "本批次存在重复的新商品名称或编码，请确认后导入"
    conn.execute("DELETE FROM price_import_rows WHERE batch_id = ?", (batch_id,))
    for row in built:
        product = row["product"]
        conn.execute(
            """INSERT INTO price_import_rows(id,batch_id,source_sheet_name,source_row,source_product_code,source_product_name,source_category,source_spec,source_unit,source_stock,source_price,normalized_unit,normalized_price_cents,conversion_factor,matched_product_id,matched_product_name,expected_old_price_cents,proposed_price_cents,match_method,match_confidence,operation_type,proposed_product_code,proposed_category,proposed_spec,proposed_unit,proposed_stock_quantity,proposed_supply_status,proposed_active,validation_status,warning)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
            (str(uuid4()), batch_id, sheet_name, row["row"], row["source"]["code"], row["source"]["name"], row["source"]["category"], row["source"]["spec"], row["source"]["unit"], row["source"]["stock"], row["price"], row["unit_normalized"], row["cents"], row["factor"], product["id"] if product else None, product["name"] if product else "", int(product["price_cents"]) if product else None, row["cents"], row["method"], 1 if product else 0, row["operation_type"], row["new_values"]["product_code"], row["new_values"]["category"], row["new_values"]["spec"], row["new_values"]["unit"], row["new_values"]["stock_quantity"], row["new_values"]["supply_status"], int(row["new_values"]["active"]), row["status"], row["warning"]),
        )
    return normalized_mapping, built, product_indexes.product_count


def analyze_batch(batch_id: str, admin: dict, sheet_name: str | None = None, mapping: dict[str, str] | None = None, header_row: int | None = None, analyzer: SpreadsheetSemanticAnalyzer | None = None) -> dict:
    analyzer = analyzer or SpreadsheetSemanticAnalyzer()
    with transaction() as conn:
        batch = _batch(conn, batch_id)
        conn.execute("UPDATE price_import_batches SET status='ANALYZING', error_message='', updated_at=CURRENT_TIMESTAMP WHERE id=?", (batch_id,))
    try:
        sheets = parse_workbook(Path(batch["file_path"]))
        selected = next((item for item in sheets if item.name == sheet_name), None) if sheet_name else None
        choices = [(item, detect_header(item.rows)) for item in sheets if item.rows]
        if not selected:
            choices.sort(key=lambda item: item[1]["score"], reverse=True)
            if len(choices) > 1 and choices[0][1]["score"] >= 10 and choices[0][1]["score"] == choices[1][1]["score"]:
                raise HTTPException(status_code=400, detail="存在多个可能的报价 Sheet，请手动选择报价 Sheet")
            selected = choices[0][0] if choices and choices[0][1]["score"] else None
        if not selected:
            raise HTTPException(status_code=400, detail="未找到可识别的报价 Sheet，请手动选择并确认字段")
        detected = detect_header(selected.rows)
        effective_header_row = header_row or detected["header_row"]
        if not effective_header_row or effective_header_row > len(selected.rows):
            raise HTTPException(status_code=400, detail="未找到表头，请手动确认字段")
        header = selected.rows[effective_header_row - 1]
        effective_mapping = mapping_from_names(header, mapping) if mapping else dict(detected["mapping"])
        recognition_level, llm_data = "L0_RULES", {"called": False}
        fingerprint = _fingerprint(selected.name, header, effective_mapping)
        with transaction() as conn:
            template = _template_mapping(conn, fingerprint)
            if template and not mapping:
                effective_mapping = mapping_from_names(header, template)
                recognition_level = "L1_TEMPLATE"
            elif ("product_name" not in effective_mapping or "price" not in effective_mapping or detected["ambiguous_price"]) and not mapping:
                candidate_rows = [{"row_index": item["header_row"], "values": selected.rows[item["header_row"] - 1]} for _, item in choices[:3] if item["header_row"]]
                dynamic_input = {"sheets": [item.name for item in sheets], "sheet_name": selected.name, "candidate_header_rows": candidate_rows, "sample_rows": selected.rows[effective_header_row: effective_header_row + 8]}
                try:
                    result = analyzer.analyze_schema(dynamic_input)
                    effective_header_row = int(result.payload.get("header_row") or effective_header_row)
                    header = selected.rows[effective_header_row - 1]
                    effective_mapping = mapping_from_names(header, {key: value for key, value in result.payload.get("columns", {}).items() if value})
                    recognition_level, llm_data = "L2_DEEPSEEK", {"called": True, "model": result.model, "latency": result.latency_ms, "prompt_tokens": result.prompt_tokens, "completion_tokens": result.completion_tokens}
                except Exception as exc:
                    raise HTTPException(status_code=400, detail="自动字段识别未完成，请手动确认 Excel 字段") from exc
            if "product_name" not in effective_mapping or "price" not in effective_mapping:
                raise HTTPException(status_code=400, detail="未能确定商品名称或执行价格列，请手动确认字段")
            defaults = _batch_new_product_defaults(_batch(conn, batch_id))
            matching_started = time.monotonic()
            mapping_names, built, product_count = _build_rows(conn, batch_id, selected.name, selected.rows, effective_header_row, effective_mapping, defaults)
            matching_duration_ms = int((time.monotonic() - matching_started) * 1000)
            summary = Counter(row["status"] for row in built)
            status = "READY_FOR_REVIEW"
            conn.execute("""UPDATE price_import_batches SET status=?, selected_sheet_name=?, header_row=?, column_mapping_json=?, template_fingerprint=?, recognition_level=?, llm_called=?, llm_model=?, llm_prompt_version=?, llm_latency_ms=?, llm_prompt_tokens=?, llm_completion_tokens=?, summary_json=?, matching_duration_ms=?, new_product_defaults_json=?, updated_at=CURRENT_TIMESTAMP WHERE id=?""", (status, selected.name, effective_header_row, json.dumps(mapping_names, ensure_ascii=False), fingerprint, recognition_level, int(llm_data["called"]), llm_data.get("model", ""), PROMPT_VERSION if llm_data["called"] else "", llm_data.get("latency"), llm_data.get("prompt_tokens"), llm_data.get("completion_tokens"), json.dumps(summary, ensure_ascii=False), matching_duration_ms, json.dumps(defaults, ensure_ascii=False), batch_id))
            if mapping or recognition_level in {"L0_RULES", "L2_DEEPSEEK"}:
                _save_template(conn, fingerprint, header, effective_mapping, 0.98 if recognition_level != "L2_DEEPSEEK" else 0.85, admin["id"])
            write_audit(conn, admin["id"], admin["role"], "PRICE_IMPORT_ANALYZED", "price_import_batch", batch_id, after_json=json.dumps({"recognition_level": recognition_level, "parsed_rows": len(built), "existing_rows": sum(row["operation_type"] == "EXISTING_PRODUCT" for row in built), "new_rows": sum(row["operation_type"] == "NEW_PRODUCT" for row in built), "review_rows": sum(row["operation_type"] == "NEEDS_REVIEW" for row in built), "invalid_rows": summary["INVALID"], "loaded_product_rows": product_count, "matching_duration_ms": matching_duration_ms}, ensure_ascii=False))
    except Exception as exc:
        with transaction() as conn:
            conn.execute("UPDATE price_import_batches SET status='FAILED', error_message=?, updated_at=CURRENT_TIMESTAMP WHERE id=?", (str(getattr(exc, "detail", exc))[:500], batch_id))
        raise
    return batch_out(batch_id)


def inspect_batch(batch_id: str) -> dict:
    with connect() as conn:
        batch = _batch(conn, batch_id)
    sheets = parse_workbook(Path(batch["file_path"]))
    return {
        "batch_id": batch_id,
        "sheets": [
            {"name": sheet.name, "header_candidate": detect_header(sheet.rows).get("header_row"), "preview": sheet.rows[:12]}
            for sheet in sheets
        ],
    }


def _refresh_summary(conn, batch_id: str) -> None:
    counts = Counter(
        row["validation_status"]
        for row in conn.execute("SELECT validation_status FROM price_import_rows WHERE batch_id=?", (batch_id,))
    )
    conn.execute(
        "UPDATE price_import_batches SET summary_json=?, updated_at=CURRENT_TIMESTAMP WHERE id=?",
        (json.dumps(counts, ensure_ascii=False), batch_id),
    )


def _refresh_duplicate_conflicts(conn, batch_id: str) -> None:
    """Keep one imported price change per system product in a batch."""
    rows = [dict(row) for row in conn.execute(
        "SELECT id, matched_product_id, validation_status FROM price_import_rows WHERE batch_id=?",
        (batch_id,),
    )]
    groups: dict[str, list[dict]] = {}
    for row in rows:
        if row["matched_product_id"] and row["validation_status"] not in {"INVALID", "IGNORED"}:
            groups.setdefault(row["matched_product_id"], []).append(row)
    conflicts = {row["id"] for items in groups.values() if len(items) > 1 for row in items}
    for row in rows:
        if row["id"] in conflicts:
            conn.execute(
                "UPDATE price_import_rows SET validation_status='DUPLICATE_CONFLICT', warning='同一系统商品在本批报价中出现多次，请保留一条并忽略其余项目', updated_at=CURRENT_TIMESTAMP WHERE id=?",
                (row["id"],),
            )
        elif row["validation_status"] == "DUPLICATE_CONFLICT":
            conn.execute(
                "UPDATE price_import_rows SET validation_status='READY', warning='', updated_at=CURRENT_TIMESTAMP WHERE id=?",
                (row["id"],),
            )


def _update_new_product_row(conn, row: dict, values: dict, reviewer_note: str = "") -> None:
    category = str(values.get("category") or "").strip()
    unit = _canonical_product_unit(str(values.get("unit") or ""))
    spec = resolve_product_spec(unit, str(values.get("spec") or ""))
    stock_quantity = _valid_stock_quantity(str(values.get("stock_quantity") or ""))
    supply_status = str(values.get("supply_status") or "").strip()
    product_code = str(values.get("product_code") or "").strip()
    if category not in PRODUCT_CATEGORIES:
        raise HTTPException(status_code=400, detail="新增商品分类不正确")
    if not spec:
        raise HTTPException(status_code=400, detail="新增商品规格不能为空")
    if unit not in PRODUCT_UNITS:
        raise HTTPException(status_code=400, detail="新增商品计量单位不正确")
    if stock_quantity is None:
        raise HTTPException(status_code=400, detail="新增商品库存不能小于 0")
    if supply_status not in EDITABLE_SUPPLY_STATUSES:
        raise HTTPException(status_code=400, detail="新增商品供应状态不正确")
    if not product_code:
        raise HTTPException(status_code=400, detail="新增商品编码不能为空")
    conn.execute(
        """UPDATE price_import_rows
        SET proposed_product_code=?, proposed_category=?, proposed_spec=?, proposed_unit=?, proposed_stock_quantity=?,
            proposed_supply_status=?, proposed_active=?, validation_status='READY', warning='', reviewer_note=?, updated_at=CURRENT_TIMESTAMP
        WHERE id=?""",
        (product_code, category, spec, unit, stock_quantity, supply_status, int(bool(values.get("active", True))), reviewer_note, row["id"]),
    )


def patch_new_product_defaults(batch_id: str, admin: dict, fields: dict) -> dict:
    defaults = _validate_new_product_defaults(fields)
    with transaction() as conn:
        batch = _batch(conn, batch_id)
        if batch["status"] not in {"UPLOADED", "READY_FOR_REVIEW", "FAILED"}:
            raise HTTPException(status_code=409, detail="该调价批次当前不能修改新增商品默认设置")
        rows = [dict(row) for row in conn.execute("SELECT * FROM price_import_rows WHERE batch_id=? AND operation_type='NEW_PRODUCT'", (batch_id,))]
        for row in rows:
            source = {"code": row["source_product_code"], "name": row["source_product_name"], "category": row["source_category"], "spec": row["source_spec"], "unit": row["source_unit"], "stock": row["source_stock"]}
            values, status, warning = _new_product_values(batch_id, int(row["source_row"]), source, defaults)
            conn.execute(
                """UPDATE price_import_rows SET proposed_product_code=?, proposed_category=?, proposed_spec=?, proposed_unit=?,
                proposed_stock_quantity=?, proposed_supply_status=?, proposed_active=?, validation_status=?, warning=?, updated_at=CURRENT_TIMESTAMP WHERE id=?""",
                (values["product_code"], values["category"], values["spec"], values["unit"], values["stock_quantity"], values["supply_status"], int(values["active"]), status, warning, row["id"]),
            )
        conn.execute("UPDATE price_import_batches SET new_product_defaults_json=?, updated_at=CURRENT_TIMESTAMP WHERE id=?", (json.dumps(defaults, ensure_ascii=False), batch_id))
        _refresh_summary(conn, batch_id)
        write_audit(conn, admin["id"], admin["role"], "PRICE_IMPORT_NEW_PRODUCT_DEFAULTS_UPDATED", "price_import_batch", batch_id, after_json=json.dumps(defaults, ensure_ascii=False))
    return batch_out(batch_id)


def patch_row(batch_id: str, row_id: str, admin: dict, body) -> dict:
    with transaction() as conn:
        _batch(conn, batch_id)
        row = one(conn, "SELECT * FROM price_import_rows WHERE id=? AND batch_id=?", (row_id, batch_id))
        if not row:
            raise HTTPException(status_code=404, detail="调价明细不存在")
        if body.ignore:
            conn.execute("UPDATE price_import_rows SET validation_status='IGNORED', reviewer_note=?, updated_at=CURRENT_TIMESTAMP WHERE id=?", (body.reviewer_note, row_id))
        elif body.matched_product_id:
            product = one(conn, "SELECT * FROM products WHERE id=? AND is_deleted=0", (body.matched_product_id,))
            if not product:
                raise HTTPException(status_code=400, detail="选择的系统食材不存在或已删除")
            if not bool(product["active"]):
                raise HTTPException(status_code=400, detail="所选系统食材当前已停用，不能批量修改价格")
            conversion = unit_conversion(row["source_unit"], product["unit"], int(row["normalized_price_cents"] or 0))
            if not conversion:
                raise HTTPException(status_code=400, detail="Excel 单位无法自动换算为所选食材单位")
            _, cents, factor = conversion
            conn.execute("UPDATE price_import_rows SET operation_type='EXISTING_PRODUCT', matched_product_id=?, matched_product_name=?, expected_old_price_cents=?, proposed_price_cents=?, normalized_unit=?, normalized_price_cents=?, conversion_factor=?, match_method='manual_selection', match_confidence=0, validation_status='READY', warning='', reviewer_note=?, updated_at=CURRENT_TIMESTAMP WHERE id=?", (product["id"], product["name"], product["price_cents"], cents, product["unit"], cents, factor, body.reviewer_note, row_id))
        elif row["operation_type"] == "NEW_PRODUCT" and any(value is not None for value in (body.product_code, body.category, body.spec, body.unit, body.stock_quantity, body.supply_status, body.active)):
            values = {
                "product_code": body.product_code if body.product_code is not None else row["proposed_product_code"],
                "category": body.category if body.category is not None else row["proposed_category"],
                "spec": body.spec if body.spec is not None else row["proposed_spec"],
                "unit": body.unit if body.unit is not None else row["proposed_unit"],
                "stock_quantity": body.stock_quantity if body.stock_quantity is not None else row["proposed_stock_quantity"],
                "supply_status": body.supply_status if body.supply_status is not None else row["proposed_supply_status"],
                "active": body.active if body.active is not None else bool(row["proposed_active"]),
            }
            _update_new_product_row(conn, row, values, body.reviewer_note)
        else:
            raise HTTPException(status_code=400, detail="请选择系统食材、修改新增商品资料或标记为忽略")
        _refresh_duplicate_conflicts(conn, batch_id)
        _refresh_summary(conn, batch_id)
        write_audit(conn, admin["id"], admin["role"], "PRICE_IMPORT_ROW_REVIEWED", "price_import_row", row_id)
    return batch_out(batch_id)


def apply_batch(batch_id: str, admin: dict) -> dict:
    try:
        with transaction() as conn:
            batch = _batch(conn, batch_id)
            if batch["status"] != "READY_FOR_REVIEW":
                raise HTTPException(status_code=409, detail="该调价批次当前不能应用")
            rows = [dict(row) for row in conn.execute("SELECT * FROM price_import_rows WHERE batch_id=? ORDER BY source_row", (batch_id,))]
            blockers = [row for row in rows if row["validation_status"] not in {"READY", "IGNORED"}]
            if blockers:
                raise HTTPException(status_code=409, detail="仍有待确认、未匹配或无效项目，不能批量应用")
            ready = [row for row in rows if row["validation_status"] == "READY"]
            if not ready:
                raise HTTPException(status_code=400, detail="没有可应用的商品或价格调整")
            conn.execute("UPDATE price_import_batches SET status='APPLYING', updated_at=CURRENT_TIMESTAMP WHERE id=?", (batch_id,))
            existing_rows = [row for row in ready if row["operation_type"] == "EXISTING_PRODUCT"]
            new_rows = [row for row in ready if row["operation_type"] == "NEW_PRODUCT"]
            product_ids = [row["matched_product_id"] for row in existing_rows]
            placeholders = ", ".join("?" for _ in product_ids)
            current_prices = {
                current["id"]: int(current["price_cents"])
                for current in conn.execute(
                    f"SELECT id, price_cents FROM products WHERE id IN ({placeholders})",
                    product_ids,
                )
            } if product_ids else {}
            conflicts = [
                row
                for row in existing_rows
                if current_prices.get(row["matched_product_id"]) != int(row["expected_old_price_cents"])
            ]
            if conflicts:
                raise HTTPException(status_code=409, detail="商品价格在审核期间已发生变化，请重新确认")
            indexes = load_product_indexes(conn)
            for row in new_rows:
                existing = match_product(indexes, row["proposed_product_code"], row["source_product_name"])
                if existing["product"]:
                    raise HTTPException(status_code=409, detail="系统商品目录已发生变化，请重新分析报价表")
            for row in existing_rows:
                conn.execute("UPDATE products SET price_cents=?, version=version+1, updated_at=CURRENT_TIMESTAMP WHERE id=? AND price_cents=?", (row["proposed_price_cents"], row["matched_product_id"], row["expected_old_price_cents"]))
            for row in existing_rows:
                conn.execute("INSERT INTO product_price_logs(id, product_id, old_price_cents, new_price_cents, actor_id) VALUES (?, ?, ?, ?, ?)", (str(uuid4()), row["matched_product_id"], row["expected_old_price_cents"], row["proposed_price_cents"], admin["id"]))
                conn.execute("INSERT INTO product_price_history(id, product_id, old_price_cents, new_price_cents, unit, source, batch_id, operator_user_id) VALUES (?, ?, ?, ?, ?, 'excel_import', ?, ?)", (str(uuid4()), row["matched_product_id"], row["expected_old_price_cents"], row["proposed_price_cents"], row["normalized_unit"], batch_id, admin["id"]))
            for row in new_rows:
                product_id = str(uuid4())
                conn.execute(
                    """INSERT INTO products(id, product_code, name, category, spec, unit, price_cents, stock_quantity,
                    reserved_quantity, min_order_quantity, quantity_step, warning_quantity, supply_status, active, created_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, '0', '1', '1', '0', ?, ?, ?)""",
                    (product_id, row["proposed_product_code"], row["source_product_name"], row["proposed_category"], row["proposed_spec"], row["proposed_unit"], row["proposed_price_cents"], row["proposed_stock_quantity"], row["proposed_supply_status"], int(row["proposed_active"]), admin["id"]),
                )
                conn.execute("INSERT INTO product_price_logs(id, product_id, old_price_cents, new_price_cents, actor_id) VALUES (?, ?, NULL, ?, ?)", (str(uuid4()), product_id, row["proposed_price_cents"], admin["id"]))
                conn.execute("INSERT INTO product_price_history(id, product_id, old_price_cents, new_price_cents, unit, source, batch_id, operator_user_id) VALUES (?, ?, 0, ?, ?, 'excel_import', ?, ?)", (str(uuid4()), product_id, row["proposed_price_cents"], row["proposed_unit"], batch_id, admin["id"]))
                conn.execute("UPDATE price_import_rows SET matched_product_id=?, matched_product_name=?, expected_old_price_cents=0, operation_type='NEW_PRODUCT', updated_at=CURRENT_TIMESTAMP WHERE id=?", (product_id, row["source_product_name"], row["id"]))
            conn.execute("UPDATE price_import_batches SET status='APPLIED', applied_by=?, applied_at=CURRENT_TIMESTAMP, updated_at=CURRENT_TIMESTAMP WHERE id=?", (admin["id"], batch_id))
            write_audit(conn, admin["id"], admin["role"], "PRICE_IMPORT_APPLIED", "price_import_batch", batch_id, after_json=json.dumps({"updated_count": len(existing_rows), "created_count": len(new_rows)}, ensure_ascii=False))
    except HTTPException as exc:
        if exc.status_code == 409 and "审核期间" in str(exc.detail):
            with transaction() as conn:
                conn.execute("UPDATE price_import_rows SET validation_status='PRICE_CONFLICT', warning='商品价格在审核期间已发生变化，请重新确认', updated_at=CURRENT_TIMESTAMP WHERE batch_id=? AND validation_status='READY' AND operation_type='EXISTING_PRODUCT'", (batch_id,))
                conn.execute("UPDATE price_import_batches SET status='READY_FOR_REVIEW', updated_at=CURRENT_TIMESTAMP WHERE id=?", (batch_id,))
        raise
    invalidate_dashboard_cache()
    return batch_out(batch_id)


def batch_out(batch_id: str) -> dict:
    with connect() as conn:
        batch = _batch(conn, batch_id)
        rows = [
            dict(row)
            for row in conn.execute(
                """
                SELECT r.*, p.unit AS system_unit, p.price_cents AS current_price_cents, p.active AS system_product_active
                FROM price_import_rows r
                LEFT JOIN products p ON p.id = r.matched_product_id
                WHERE r.batch_id=?
                ORDER BY r.source_row
                """,
                (batch_id,),
            )
        ]
    try:
        summary = json.loads(batch["summary_json"] or "{}")
    except json.JSONDecodeError:
        summary = {}
    batch["llm_called"] = bool(batch["llm_called"])
    batch["summary"] = summary
    batch["new_product_defaults"] = _batch_new_product_defaults(batch)
    for row in rows:
        row["excel_price_cents"] = parse_price_cents(row["source_price"])
        if row["operation_type"] == "NEW_PRODUCT":
            row["match_status"] = "NEW_PRODUCT"
        elif row["operation_type"] == "NEEDS_REVIEW" or row["validation_status"] == "NEEDS_REVIEW":
            row["match_status"] = "NEEDS_REVIEW"
        elif row["matched_product_id"] and row["match_method"] in {"exact_code", "exact_name"}:
            row["match_status"] = "AUTO_LINKED"
        elif row["matched_product_id"]:
            row["match_status"] = "MANUAL_SELECTED"
        else:
            row["match_status"] = "NEEDS_PRODUCT_SELECTION"
        row["apply_status"] = row["validation_status"]
    batch["metrics"] = {
        "parsed_rows": len(rows),
        "existing_product_rows": sum(row["operation_type"] == "EXISTING_PRODUCT" for row in rows),
        "new_product_rows": sum(row["operation_type"] == "NEW_PRODUCT" for row in rows),
        "needs_review_rows": sum(row["operation_type"] == "NEEDS_REVIEW" or row["validation_status"] == "NEEDS_REVIEW" for row in rows),
        "exception_rows": sum(row["validation_status"] not in {"READY", "IGNORED", "NEEDS_REVIEW"} for row in rows),
        "ready_rows": sum(row["validation_status"] == "READY" for row in rows),
        "ignored_rows": sum(row["validation_status"] == "IGNORED" for row in rows),
        "matching_duration_ms": batch.get("matching_duration_ms"),
    }
    batch["rows"] = rows
    return batch


def list_batches(limit: int = 30) -> list[dict]:
    with connect() as conn:
        rows = [dict(row) for row in conn.execute("SELECT id,status,source_filename,uploaded_by,selected_sheet_name,recognition_level,llm_called,summary_json,applied_at,created_at FROM price_import_batches ORDER BY created_at DESC LIMIT ?", (limit,))]
        for row in rows:
            row["created_at"] = display_local_time(row.get("created_at"))
            row["applied_at"] = display_local_time(row.get("applied_at"))
        return rows
