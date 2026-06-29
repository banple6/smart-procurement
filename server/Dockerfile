FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

WORKDIR /app
COPY requirements.txt /app/requirements.txt
RUN set -eux; \
    . /etc/os-release; \
    rm -f /etc/apt/sources.list.d/debian.sources; \
    printf 'deb https://mirrors.aliyun.com/debian/ %s main contrib non-free non-free-firmware\n' "$VERSION_CODENAME" > /etc/apt/sources.list; \
    printf 'deb https://mirrors.aliyun.com/debian/ %s-updates main contrib non-free non-free-firmware\n' "$VERSION_CODENAME" >> /etc/apt/sources.list; \
    printf 'deb https://mirrors.aliyun.com/debian-security %s-security main contrib non-free non-free-firmware\n' "$VERSION_CODENAME" >> /etc/apt/sources.list
RUN apt-get update \
    && apt-get install -y --no-install-recommends sqlite3 fonts-noto-cjk \
    && rm -rf /var/lib/apt/lists/*
RUN pip install --no-cache-dir \
    --index-url https://mirrors.aliyun.com/pypi/simple/ \
    --trusted-host mirrors.aliyun.com \
    -r /app/requirements.txt

COPY app /app/app
COPY backup.sh /app/backup.sh
RUN chmod +x /app/backup.sh
RUN mkdir -p /app/data /app/uploads /app/private_uploads /app/backups

EXPOSE 8000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
