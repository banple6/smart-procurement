from fastapi import APIRouter, Depends, Query

from ..dependencies import require_admin_user
from ..services import analytics


router = APIRouter(prefix="/admin/analytics", tags=["analytics"])


@router.get("/overview")
def analytics_overview(
    start_date: str | None = None,
    end_date: str | None = None,
    unit_id: str | None = None,
    category: str | None = None,
    limit: int = Query(default=10, ge=1, le=50),
    admin=Depends(require_admin_user),
):
    return analytics.overview(start_date, end_date, unit_id, category, limit)


@router.get("/units")
def analytics_units(
    start_date: str | None = None,
    end_date: str | None = None,
    category: str | None = None,
    sort: str = Query(default="amount", pattern="^(amount|orders|products)$"),
    admin=Depends(require_admin_user),
):
    return analytics.units(start_date, end_date, category, sort)


@router.get("/prices")
def analytics_prices(
    start_date: str | None = None,
    end_date: str | None = None,
    category: str | None = None,
    product_id: str | None = None,
    admin=Depends(require_admin_user),
):
    return analytics.prices(start_date, end_date, category, product_id)


@router.get("/inventory")
def analytics_inventory(admin=Depends(require_admin_user)):
    return analytics.inventory()


@router.get("/products/{product_id}")
def analytics_product(
    product_id: str,
    start_date: str | None = None,
    end_date: str | None = None,
    admin=Depends(require_admin_user),
):
    return analytics.product_detail(product_id, start_date, end_date)
