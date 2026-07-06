from pathlib import Path

from fastapi import APIRouter, HTTPException, Request
from fastapi.responses import HTMLResponse, RedirectResponse, Response

from .dependencies import current_web_user


router = APIRouter(tags=["web-admin"])
STATIC_ROOT = Path(__file__).resolve().parent / "static" / "admin"


def no_store(response: Response) -> Response:
    response.headers["Cache-Control"] = "no-store, private"
    response.headers["Pragma"] = "no-cache"
    return response


def html_file(name: str) -> str:
    return (STATIC_ROOT / name).read_text(encoding="utf-8")


@router.get("/", include_in_schema=False)
def root():
    return RedirectResponse("/admin/dashboard", status_code=302)


@router.get("/login", include_in_schema=False)
def login_page():
    return no_store(HTMLResponse(html_file("login.html")))


def web_admin_user_or_response(request: Request):
    try:
        user = current_web_user(request)
    except HTTPException:
        return RedirectResponse("/login?expired=1", status_code=302)
    if user["role"] != "admin":
        return no_store(
            HTMLResponse(
                """
                <!doctype html><html lang="zh-CN"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <title>无权限 - 景荣鲜配</title><link rel="stylesheet" href="/admin-assets/dashboard.css">
                </head><body class="plain-state"><main><h1>无权限访问</h1><p>当前账号没有管理员权限。</p>
                <a class="primary-link" href="/login">重新扫码登录</a></main></body></html>
                """,
                status_code=403,
            )
        )
    return user


@router.get("/admin", include_in_schema=False)
def admin_root(request: Request):
    result = web_admin_user_or_response(request)
    if isinstance(result, Response):
        return result
    return RedirectResponse("/admin/dashboard", status_code=302)


@router.get("/admin/{path:path}", include_in_schema=False)
def admin_shell(path: str, request: Request):
    result = web_admin_user_or_response(request)
    if isinstance(result, Response):
        return result
    return no_store(HTMLResponse(html_file("dashboard.html")))


@router.head("/admin/{path:path}", include_in_schema=False)
def admin_shell_head(path: str, request: Request):
    result = web_admin_user_or_response(request)
    if isinstance(result, Response):
        return result
    return no_store(Response(status_code=200))
