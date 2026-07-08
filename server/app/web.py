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


def public_page(title: str, subtitle: str, body: str) -> HTMLResponse:
    return no_store(
        HTMLResponse(
            f"""
            <!doctype html><html lang="zh-CN"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>{title} - 三公鲜配</title><link rel="stylesheet" href="/admin-assets/dashboard.css">
            </head><body class="public-page">
            <main class="public-shell">
              <header class="login-brand"><img class="login-badge emblem-badge" src="/admin-assets/police-badge.svg" alt="警徽" /><h1>三公鲜配</h1><p>{subtitle}</p></header>
              {body}
            </main>
            <script src="/admin-assets/login.js" defer></script>
            </body></html>
            """,
        )
    )


@router.get("/", include_in_schema=False)
def root():
    return RedirectResponse("/admin/dashboard", status_code=302)


@router.get("/login", include_in_schema=False)
def login_page():
    return no_store(HTMLResponse(html_file("login.html")))


@router.get("/web/entry", include_in_schema=False)
def web_entry(request: Request):
    try:
        user = current_web_user(request)
    except HTTPException:
        return RedirectResponse("/login?expired=1", status_code=302)
    if user["role"] == "admin":
        return RedirectResponse("/admin/dashboard", status_code=302)
    if user["role"] == "unit_user":
        return RedirectResponse("/unit/home", status_code=302)
    raise HTTPException(status_code=403, detail="当前账号无权访问网页版")


@router.get("/download", include_in_schema=False)
def download_page():
    return public_page(
        "下载 App",
        "单位食材申领与配送协同平台",
        """
        <section class="public-panel app-download-card">
          <h2>三公鲜配 App</h2>
          <p>请安装三公鲜配 App，使用已登录账号扫码进入网页端。日常申领、收货确认和异常上报推荐在 App 内完成。</p>
          <dl id="downloadInfo" class="status-list">
            <dt>当前版本</dt><dd>正在获取...</dd>
            <dt>更新时间</dt><dd>正在获取...</dd>
            <dt>安装包大小</dt><dd>正在获取...</dd>
          </dl>
          <div class="page-toolbar">
            <a id="downloadApkButton" class="primary-link" href="/api/v1/app-update/latest/download" download>下载 Android App</a>
            <button id="copyDownloadLinkButton" class="secondary-button" type="button">复制下载链接</button>
            <a class="secondary-button as-link" href="/help">查看安装教程</a>
          </div>
          <p id="downloadStatus" class="muted">下载入口来自服务端发布配置。</p>
        </section>
        """,
    )


@router.get("/help", include_in_schema=False)
def help_index():
    return public_page(
        "帮助中心",
        "食材申领与配送平台",
        """
        <section class="public-panel">
          <h2>首次使用</h2>
          <div class="help-grid">
            <a class="quick-card" href="/help/admin"><strong>管理员教程</strong><span>单位、账号、食材、订单和发货照片</span></a>
            <a class="quick-card" href="/help/unit"><strong>子单位教程</strong><span>食材申领、采购清单、订单和确认收货</span></a>
            <a class="quick-card" href="/download"><strong>下载 App</strong><span>安装 Android App 后扫码登录网页端</span></a>
          </div>
        </section>
        """,
    )


@router.get("/help/admin", include_in_schema=False)
def help_admin():
    return public_page(
        "管理员教程",
        "管理员公测使用说明",
        """
        <section class="public-panel help-article">
          <h2>管理员常用流程</h2>
          <ol>
            <li>在“子单位管理”完善单位名称、编码和默认配送点。</li>
            <li>在“账号管理”创建子单位账号，初始密码只告知使用人。</li>
            <li>在“食材列表”维护名称、规格、价格、库存和供应状态。</li>
            <li>在“订单管理”按状态接单、开始备货、上传照片并确认发货。</li>
            <li>在“当前备货”和“单位配送”查看待处理清单并导出 Excel。</li>
            <li>在“系统状态”确认服务、备份和 Web 会话情况。</li>
          </ol>
        </section>
        """,
    )


@router.get("/help/unit", include_in_schema=False)
def help_unit():
    return public_page(
        "子单位教程",
        "子单位公测使用说明",
        """
        <section class="public-panel help-article">
          <h2>子单位常用流程</h2>
          <ol>
            <li>使用管理员分配的账号登录 App。</li>
            <li>在“食材申领”查看可申领食材、规格、价格和库存。</li>
            <li>把需要的食材加入采购清单，核对数量和默认配送点。</li>
            <li>提交订单后在“我的订单”查看状态。</li>
            <li>订单发货后查看发货照片，确认无误后点击“确认收货”。</li>
            <li>如数量或质量异常，按页面提示提交异常说明。</li>
          </ol>
        </section>
        """,
    )


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
                <title>无权限 - 三公鲜配</title><link rel="stylesheet" href="/admin-assets/dashboard.css">
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
