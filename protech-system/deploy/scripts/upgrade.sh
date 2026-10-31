#!/usr/bin/env bash
# 升级（先 migrate 后滚动；镜像 tag 滚动更新）
set -euo pipefail
cd /opt/protech/deploy
docker compose pull
docker compose run --rm api npx prisma migrate deploy
docker compose up -d api worker web
echo "[upgrade] 完成——健康检查: curl -f http://localhost/api/v1/monitor/health"
