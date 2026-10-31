#!/usr/bin/env bash
# 每日备份（11 册 §7.3 运维 SOP；crontab: 0 2 * * * /opt/protech/deploy/scripts/backup.sh）
# 3-2-1 对齐档案制度：本机 pg_dump + MinIO 增量由应用层附件策略承担 + 每周异机副本
set -euo pipefail
BACKUP_DIR=${BACKUP_DIR:-/opt/protech/backups}
STAMP=$(date +%Y%m%d-%H%M%S)
mkdir -p "$BACKUP_DIR"

# 1) PostgreSQL 全量
docker compose -f /opt/protech/deploy/docker-compose.yml exec -T postgres \
  pg_dump -U "${POSTGRES_USER:-protech}" "${POSTGRES_DB:-protech}" | gzip > "$BACKUP_DIR/protech-$STAMP.sql.gz"

# 2) 校验码（防静默损坏）
sha256sum "$BACKUP_DIR/protech-$STAMP.sql.gz" > "$BACKUP_DIR/protech-$STAMP.sql.gz.sha256"

# 3) 保留 30 天
find "$BACKUP_DIR" -name "protech-*.sql.gz" -mtime +30 -delete
find "$BACKUP_DIR" -name "*.sha256" -mtime +30 -delete

echo "[backup] OK protech-$STAMP.sql.gz"
