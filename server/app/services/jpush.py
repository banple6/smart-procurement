import json
import os
from dataclasses import dataclass

import httpx


STATUS_MESSAGES = {
    "accepted": "订单已接单。",
    "preparing": "订单正在备货。",
    "shipped": "订单已发货，请及时确认收货。",
    "completed": "订单已完成。",
    "cancelled": "订单已取消。",
}


@dataclass(frozen=True)
class JPushResult:
    accepted: bool
    invalid_device: bool
    provider_message_id: str
    status_code: int
    error: str
    configuration_disabled: bool = False


class JPushClient:
    def __init__(
        self,
        *,
        app_key: str,
        master_secret: str,
        enabled: bool,
        api_url: str = "https://api.jpush.cn/v3/push",
        timeout_seconds: float = 8,
        transport=None,
    ):
        self.app_key = app_key
        self.master_secret = master_secret
        self.enabled = enabled
        self.api_url = api_url
        self.timeout_seconds = timeout_seconds
        self.transport = transport

    @classmethod
    def from_env(cls):
        enabled = os.getenv("JPUSH_ENABLED", "false").lower() in {"1", "true", "yes", "on"}
        return cls(
            app_key=os.getenv("JPUSH_APP_KEY", ""),
            master_secret=os.getenv("JPUSH_MASTER_SECRET", ""),
            enabled=enabled,
            api_url=os.getenv("JPUSH_API_URL", "https://api.jpush.cn/v3/push"),
            timeout_seconds=float(os.getenv("JPUSH_TIMEOUT_SECONDS", "8")),
        )

    def send(self, registration_id: str, event: dict) -> JPushResult:
        if not self.enabled:
            return JPushResult(False, False, "", 0, "推送服务未启用", True)
        if not self.app_key or not self.master_secret:
            return JPushResult(False, False, "", 0, "推送服务配置不完整", True)
        if not self.api_url.startswith("https://"):
            return JPushResult(False, False, "", 0, "推送服务地址必须使用 HTTPS", True)

        payload = self._payload(registration_id, event)
        try:
            with httpx.Client(transport=self.transport, timeout=self.timeout_seconds) as client:
                response = client.post(
                    self.api_url,
                    auth=(self.app_key, self.master_secret),
                    headers={"Content-Type": "application/json"},
                    json=payload,
                )
        except httpx.HTTPError:
            return JPushResult(False, False, "", 0, "极光服务连接失败")

        body = self._json(response)
        if response.is_success:
            message_id = str(body.get("msg_id") or "")
            return JPushResult(True, False, message_id, response.status_code, "")
        error = body.get("error") if isinstance(body.get("error"), dict) else {}
        code = int(error.get("code") or 0)
        message = str(error.get("message") or "推送请求失败")[:300]
        return JPushResult(False, code == 1011, "", response.status_code, message)

    def _payload(self, registration_id: str, event: dict) -> dict:
        event_type = event["event_type"]
        order_id = str(event.get("order_id") or "")
        event_id = str(event["event_id"])
        details = json.loads(event.get("payload_json") or "{}")
        if event_type == "ORDER_CREATED":
            title = "三公鲜配 · 新订单"
            alert = "收到新的采购订单，点击查看。"
            channel_id = "new_orders"
        else:
            title = "三公鲜配 · 订单进度"
            alert = STATUS_MESSAGES.get(str(details.get("status") or ""), "订单状态已更新。")
            channel_id = "order_updates"
        return {
            "platform": ["android"],
            "audience": {"registration_id": [registration_id]},
            "notification": {
                "android": {
                    "title": title,
                    "alert": alert,
                    "channel_id": channel_id,
                    "extras": {
                        "event_id": event_id,
                        "event_type": event_type,
                        "order_id": order_id,
                    },
                }
            },
            "options": {"time_to_live": 86400},
        }

    @staticmethod
    def _json(response: httpx.Response) -> dict:
        try:
            data = response.json()
            return data if isinstance(data, dict) else {}
        except (ValueError, json.JSONDecodeError):
            return {}
