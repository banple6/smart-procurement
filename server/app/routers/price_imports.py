from fastapi import APIRouter, Depends, File, HTTPException, UploadFile

from ..dependencies import require_admin_user
from ..schemas import PriceImportAnalyzeRequest, PriceImportApplyRequest, PriceImportDefaultsPatch, PriceImportRowPatch
from ..services.price_import import service


router = APIRouter(prefix="/admin/price-imports", tags=["price-imports"])


@router.post("")
async def upload_price_import(file: UploadFile = File(...), admin=Depends(require_admin_user)):
    return await service.create_batch(file, admin)


@router.get("")
def price_import_history(admin=Depends(require_admin_user)):
    return {"items": service.list_batches()}


@router.get("/{batch_id}")
def price_import_detail(batch_id: str, admin=Depends(require_admin_user)):
    return service.batch_out(batch_id)


@router.get("/{batch_id}/structure")
def price_import_structure(batch_id: str, admin=Depends(require_admin_user)):
    return service.inspect_batch(batch_id)


@router.post("/{batch_id}/analyze")
def analyze_price_import(batch_id: str, body: PriceImportAnalyzeRequest, admin=Depends(require_admin_user)):
    return service.analyze_batch(batch_id, admin, body.sheet_name, body.mapping, body.header_row)


@router.patch("/{batch_id}/rows/{row_id}")
def review_price_import_row(batch_id: str, row_id: str, body: PriceImportRowPatch, admin=Depends(require_admin_user)):
    return service.patch_row(batch_id, row_id, admin, body)


@router.patch("/{batch_id}/new-product-defaults")
def update_price_import_new_product_defaults(batch_id: str, body: PriceImportDefaultsPatch, admin=Depends(require_admin_user)):
    return service.patch_new_product_defaults(batch_id, admin, body.model_dump())


@router.post("/{batch_id}/apply")
def apply_price_import(batch_id: str, body: PriceImportApplyRequest, admin=Depends(require_admin_user)):
    if not body.confirmed:
        raise HTTPException(status_code=400, detail="请确认后再应用价格调整")
    return service.apply_batch(batch_id, admin)
