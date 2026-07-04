from pathlib import Path


def test_assets_are_revalidated_to_avoid_stale_web_js():
    config = Path(__file__).resolve().parents[1] / "nginx.conf"
    text = config.read_text()

    assert "location /assets/" in text
    assets_block = text.split("location /assets/", 1)[1].split("location /uploads/", 1)[0]
    assert 'alias /app/assets/;' in assets_block
    assert 'Cache-Control "no-cache, must-revalidate" always' in assets_block
    assert 'Pragma "no-cache" always' in assets_block
