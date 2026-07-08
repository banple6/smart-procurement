from pathlib import Path


def test_assets_are_revalidated_to_avoid_stale_web_js():
    config = Path(__file__).resolve().parents[1] / "nginx.conf"
    text = config.read_text()

    assert "location /assets/" in text
    assets_block = text.split("location /assets/", 1)[1].split("location /uploads/", 1)[0]
    assert 'alias /app/assets/;' in assets_block
    assert 'Cache-Control "no-cache, must-revalidate" always' in assets_block
    assert 'Pragma "no-cache" always' in assets_block


def test_nginx_gzip_is_enabled_for_text_assets_only():
    config = Path(__file__).resolve().parents[1] / "nginx.conf"
    text = config.read_text()

    assert "gzip on;" in text
    assert "gzip_min_length 1024;" in text
    assert "gzip_comp_level 5;" in text
    assert "application/json" in text
    assert "application/javascript" in text
    assert "text/css" in text
    assert "image/svg+xml" in text
    assert "image/jpeg" not in text
    assert "image/png" not in text
    assert "image/webp" not in text
