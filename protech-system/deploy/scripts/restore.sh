#!/usr/bin/env bash
# 恢复（每半年演练：restore 至 staging 并抽查，NFR-05 RTO≤4h）
set -euo pipefail
FILE=${1:?用法: restore.sh /opt/protech/backups/protech-YYYYMMDD-HHMMSS.sql.gz}
cd /opt/protech/deploy
gunzip -c "$FILE" | docker compose exec -T postgres psql -U "${POSTGRES_USER:-protech}" -d "${POSTGRES_DB:-protech}"
echo "[restore] 完成——请抽查：登录/项目列表/库存流水/看板指标"
