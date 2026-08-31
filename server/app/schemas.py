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
    model_config = ConfigDict(extra="forbid")

    unit_code: str
    unit_name: str
    default_delivery_point: str = ""
    address_note: str = ""


class UnitUpdate(BaseModel):
    model_config = ConfigDict(extra="forbid")

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


class UnitUserCreate(BaseModel):
    model_config = ConfigDict(extra="forbid")

    username: str
    password: str
    display_name: str
    unit_id: str


class AdminUserCreate(BaseModel):
    model_config = ConfigDict(extra="forbid")

    username: str
    password: str
    display_name: str
    can_manage_accounts: bool = False
    can_issue_manager_invites: bool = False
    can_view_system_status: bool = False
    can_view_detailed_metrics: bool = False
    can_manage_backups: bool = False
    can_restore_backups: bool = False


class UserPermissionsUpdate(BaseModel):
    model_config = ConfigDict(extra="forbid")

    can_manage_accounts: bool = False
    can_issue_manager_invites: bool = False
    can_view_system_status: bool = False
    can_view_detailed_metrics: bool = False
    can_manage_backups: bool = False
    can_restore_backups: bool = False


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


class ProductBatchDelete(BaseModel):
    model_config = ConfigDict(extra="forbid")

    ids: list[str] = Field(min_length=1, max_length=1000)
    confirmed: bool = False


class ProductDeleteAll(BaseModel):
    model_config = ConfigDict(extra="forbid")

    confirmed: bool = False
    confirmation_text: str = Field(default="", max_length=20)
    expected_count: int = Field(ge=0, le=100000)


class PriceImportAnalyzeRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    sheet_name: Optional[str] = None
    mapping: dict[str, str] = Field(default_factory=dict)
    header_row: Optional[int] = Field(default=None, ge=1, le=10000)


class PriceImportRowPatch(BaseModel):
    model_config = ConfigDict(extra="forbid")

    matched_product_id: Optional[str] = None
    ignore: bool = False
    reviewer_note: str = Field(default="", max_length=300)
    product_code: Optional[str] = Field(default=None, max_length=120)
    category: Optional[str] = Field(default=None, max_length=40)
    spec: Optional[str] = Field(default=None, max_length=120)
    unit: Optional[str] = Field(default=None, max_length=20)
    stock_quantity: Optional[str] = Field(default=None, max_length=40)
    supply_status: Optional[str] = Field(default=None, max_length=20)
    active: Optional[bool] = None


class PriceImportDefaultsPatch(BaseModel):
    model_config = ConfigDict(extra="forbid")

    category: str = Field(min_length=1, max_length=40)
    spec: str = Field(min_length=1, max_length=120)
    stock_quantity: str = Field(default="0", max_length=40)
    supply_status: str = Field(default="paused", max_length=20)
    fallback_unit: str = Field(default="", max_length=20)
    active: bool = True


class PriceImportApplyRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    confirmed: bool


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


class OrderLifecycleReason(BaseModel):
    model_config = ConfigDict(extra="forbid")

    reason: str = Field(min_length=1, max_length=300)


class UnitQuotaSettings(BaseModel):
    model_config = ConfigDict(extra="forbid")

    enabled: bool
    default_monthly_quota_cents: int = Field(ge=0, le=10_000_000_000)
    # Optional so existing Web and Android clients remain compatible. New clients
    # use the server-issued version to avoid overwriting another admin's change.
    expected_version: Optional[int] = Field(default=None, ge=1)


class UnitQuotaAdjustment(BaseModel):
    model_config = ConfigDict(extra="forbid")

    delta_cents: int = Field(ge=-10_000_000_000, le=10_000_000_000)
    reason: str = Field(min_length=1, max_length=300)
    expected_version: Optional[int] = Field(default=None, ge=1)


class DeliveryBatchCreate(BaseModel):
    model_config = ConfigDict(extra="forbid")

    name: str = Field(default="", max_length=120)
    note: str = Field(default="", max_length=300)
    order_ids: list[str] = Field(min_length=1, max_length=1000)
    client_request_id: Optional[str] = Field(default=None, min_length=1, max_length=120)


class DeliveryBatchOrdersPatch(BaseModel):
    model_config = ConfigDict(extra="forbid")

    add_order_ids: list[str] = Field(default_factory=list, max_length=1000)
    remove_order_ids: list[str] = Field(default_factory=list, max_length=1000)
    expected_version: Optional[int] = Field(default=None, ge=1)
    client_request_id: Optional[str] = Field(default=None, min_length=1, max_length=120)


class DeliveryBatchReconcile(BaseModel):
    model_config = ConfigDict(extra="forbid")

    unit_id: str = Field(min_length=1, max_length=120)
    target_batch_id: str = Field(min_length=1, max_length=120)
    source_batch_ids: list[str] = Field(min_length=1, max_length=100)
    order_ids: list[str] = Field(min_length=1, max_length=1000)
    expected_versions: dict[str, int] = Field(min_length=2, max_length=101)
    client_request_id: str = Field(min_length=1, max_length=120)


class DeliveryBatchStatusPatch(BaseModel):
    model_config = ConfigDict(extra="forbid")

    status: str = Field(pattern="^(open|closed|cancelled)$")
    expected_version: Optional[int] = Field(default=None, ge=1)


class OrderSoftDeleteRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    reason: str = Field(default="管理员删除订单", max_length=300)


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
