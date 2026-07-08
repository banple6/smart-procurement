import os
import random
from pathlib import Path
from uuid import NAMESPACE_URL, uuid5


NAMESPACE = "LOADTEST"
SEED = 20260707


def require_loadtest_environment():
    if os.getenv("APP_ENV") != "loadtest":
        raise SystemExit("Refusing to run outside APP_ENV=loadtest")
    if os.getenv("LOAD_TEST_ALLOWED", "").lower() not in {"1", "true", "yes", "on"}:
        raise SystemExit("Refusing to run without LOAD_TEST_ALLOWED=true")
    if os.getenv("DATA_NAMESPACE") != NAMESPACE:
        raise SystemExit("Refusing to run without DATA_NAMESPACE=LOADTEST")
    db_path = os.getenv("DATABASE_PATH", "")
    if not db_path or "loadtest" not in db_path.lower():
        raise SystemExit("Refusing to run because DATABASE_PATH is not loadtest-scoped")


def deterministic_id(name: str) -> str:
    return str(uuid5(NAMESPACE_URL, f"smart-procurement:{NAMESPACE}:{name}"))


def rng() -> random.Random:
    return random.Random(SEED)


def ensure_dirs():
    for key in ("UPLOAD_DIR", "PRIVATE_UPLOAD_DIR", "BACKUP_DIR", "APP_UPDATE_RELEASE_DIR"):
        value = os.getenv(key)
        if value:
            Path(value).mkdir(parents=True, exist_ok=True)
