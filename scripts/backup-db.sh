#!/usr/bin/env bash
# 운영 DB 백업 (docs/배포-가이드.md "백업·복구"). 매일 새벽 cron으로 돌려요:
#   15 4 * * * cd /srv/m-space && scripts/backup-db.sh >> deploy/backups/backup.log 2>&1
# 결과: deploy/backups/mspace-YYYYMMDD-HHMM.dump (pg_dump custom 형식), 14일 지난 파일은 지워요.
# 백업 파일에도 개인정보가 들어 있어서 폴더 권한을 700으로 두고, 서버 밖으로 옮길 때는 학교 지침을 따라요.
set -euo pipefail

cd "$(dirname "$0")/.."
COMPOSE=(docker compose -f deploy/docker-compose.prod.yml)
BACKUP_DIR=deploy/backups
KEEP_DAYS=${KEEP_DAYS:-14}

mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"

file="$BACKUP_DIR/mspace-$(date +%Y%m%d-%H%M).dump"
"${COMPOSE[@]}" exec -T db pg_dump -U mspace -d mspace -Fc > "$file.tmp"
mv "$file.tmp" "$file"
chmod 600 "$file"
echo "$(date '+%F %T') 백업 완료: $file ($(du -h "$file" | cut -f1))"

find "$BACKUP_DIR" -name 'mspace-*.dump' -mtime +"$KEEP_DAYS" -delete
