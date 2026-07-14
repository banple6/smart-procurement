from typing import Optional
from urllib.parse import unquote

from pydantic import BaseModel, ConfigDict, Field


class LoginRequest(BaseModel):
    username: str
    password: str


class ChangePasswordRequest(BaseModel):
    old_password: str
    new_password: str


class InviteInspectRequest(BaseModel):
    invite_token: str


class PhoneSendCodeRequest(BaseModel):
    phone: str
    purpose: str = "register"
    invite_token: str = ""


class PhoneVerifyCodeRequest(BaseModel):
    phone: str
    code: str
    purpose: str = "register"
    invite_token: str = ""


class RegisterWithInviteRequest(BaseModel):
    model_config = ConfigDict(extra="allow")

    invite_token: str
    username: str
    display_name: str
    password: str
    phone: str = ""
    phone_verification_ticket: str = ""


class StepUpRequest(BaseModel):
    password: str
    purpose: str


class AdminInviteCreate(BaseModel):
    invite_type: str
    unit_id: Optional[str] = None
    expires_in_hours: int = Field(default=72, ge=1, le=168)
    max_uses: int = Field(default=1, ge=1, le=10)
    phone_required: bool = False
    allowed_phone: str = ""


class ManagerRegistrationReview(BaseModel):
    review_note: str = ""


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
    model_config = ConfigDict(extra="forbid")

    product_code: str
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
    model_config = ConfigDict(extra="forbid")

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
    expected_version: Optional[int] = None


class ProductStatusPatch(BaseModel):
    model_config = ConfigDict(extra="forbid")

    supply_status: str
    active: bool = True
    expected_version: Optional[int] = None


class ProductPricePatch(BaseModel):
    price_cents: int = Field(ge=0)
    expected_version: Optional[int] = None


class ProductStockPatch(BaseModel):
    stock_quantity: str
    detail: str = ""
    expected_version: Optional[int] = None


class OrderItemRequest(BaseModel):
    product_id: str
    quantity: str


class OrderCreate(BaseModel):
    client_request_id: Optional[str] = None
    note: str = ""
    items: list[OrderItemRequest]


class OrderStatusPatch(BaseModel):
    status: str
    expected_status: Optional[str] = None
    expected_version: Optional[int] = None


class PushDeviceRegister(BaseModel):
    model_config = ConfigDict(extra="forbid")

    registration_id: str = Field(min_length=8, max_length=255)
    installation_id: str = Field(min_length=8, max_length=80)
    platform: str = Field(default="android", pattern="^android$")
    app_version: str = Field(default="", max_length=40)


class WebQrScanRequest(BaseModel):
    qr_token: str = ""
    qr_content: str = ""
    raw_value: str = ""
    device_name: str = ""
    app_version: str = ""

    def model_post_init(self, __context):
        if not self.qr_token:
            raw = self.qr_content or self.raw_value
            token = ""
            marker = "token="
            if raw.startswith("jingrongxianpei://web-login?") and marker in raw:
                token = unquote(raw.split(marker, 1)[1].split("&", 1)[0])
            object.__setattr__(self, "qr_token", token)


class CutoffPatch(BaseModel):
    enabled: bool = False
    cutoff_time: str = "10:30"


class CutoffOverridePut(BaseModel):
    enabled: bool = False
    cutoff_time: str = "10:30"
    note: str = ""
