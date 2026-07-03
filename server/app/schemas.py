from typing import Optional

from pydantic import BaseModel, Field


class LoginRequest(BaseModel):
    username: str
    password: str


class ChangePasswordRequest(BaseModel):
    old_password: str
    new_password: str


class WebQrScanRequest(BaseModel):
    qr_token: str
    device_name: str = ""
    app_version: str = ""


class UnitCreate(BaseModel):
    unit_code: str
    unit_name: str
    default_delivery_point: str = ""
    address_note: str = ""


class UnitUpdate(BaseModel):
    unit_code: Optional[str] = None
    unit_name: Optional[str] = None
    default_delivery_point: Optional[str] = None
    address_note: Optional[str] = None
    active: Optional[bool] = None


class StatusPatch(BaseModel):
    active: bool


class UserCreate(BaseModel):
    username: str
    password: str
    display_name: str
    role: str
    unit_id: Optional[str] = None
    must_change_password: bool = True


class UserUpdate(BaseModel):
    display_name: Optional[str] = None
    unit_id: Optional[str] = None
    must_change_password: Optional[bool] = None


class ResetPasswordRequest(BaseModel):
    new_password: str
    must_change_password: bool = True


class ProductCreate(BaseModel):
    product_code: Optional[str] = None
    name: str
    category: str
    spec: str
    unit: str
    price_cents: int = Field(ge=0)
    stock_quantity: str = "0"
    reserved_quantity: str = "0"
    min_order_quantity: str = "1"
    quantity_step: str = "1"
    warning_quantity: str = "0"
    origin: str = ""
    supplier: str = ""
    shelf_life: str = ""
    storage_method: str = ""
    description: str = ""
    supply_status: str = "normal"
    active: bool = True


class ProductUpdate(BaseModel):
    product_code: Optional[str] = None
    name: Optional[str] = None
    category: Optional[str] = None
    spec: Optional[str] = None
    unit: Optional[str] = None
    price_cents: Optional[int] = Field(default=None, ge=0)
    stock_quantity: Optional[str] = None
    min_order_quantity: Optional[str] = None
    quantity_step: Optional[str] = None
    warning_quantity: Optional[str] = None
    origin: Optional[str] = None
    supplier: Optional[str] = None
    shelf_life: Optional[str] = None
    storage_method: Optional[str] = None
    description: Optional[str] = None
    supply_status: Optional[str] = None
    active: Optional[bool] = None
    expected_updated_at: Optional[str] = None


class ProductStatusPatch(BaseModel):
    supply_status: str
    active: bool = True


class ProductPricePatch(BaseModel):
    price_cents: int = Field(gt=0, le=99999999)
    reason: str = ""
    expected_updated_at: Optional[str] = None


class ProductStockPatch(BaseModel):
    stock_quantity: str
    detail: str = ""


class ProductInventoryAdjust(BaseModel):
    mode: str
    quantity: str
    reason: str
    expected_updated_at: Optional[str] = None


class OrderItemRequest(BaseModel):
    product_id: str
    quantity: str


class OrderCreate(BaseModel):
    client_request_id: Optional[str] = None
    note: str = ""
    items: list[OrderItemRequest]


class OrderStatusPatch(BaseModel):
    status: str


class CutoffPatch(BaseModel):
    enabled: bool
    cutoff_time: str


class CutoffOverridePut(BaseModel):
    enabled: bool
    cutoff_time: str
    note: str = ""


class OrderItemActualQuantityPatch(BaseModel):
    actual_quantity: str
    reason: str
    expected_updated_at: Optional[str] = None


class ReceiptIssueResolve(BaseModel):
    resolution_note: str = ""
