#!/usr/bin/env bash
set -Eeuo pipefail

readonly database_path=/var/lib/reserve-sin/reserve.db
readonly backup_directory=/var/backups/reserve-sin
readonly retention_days=14

if ! command -v sqlite3 >/dev/null 2>&1; then
    echo "sqlite3 is required for Reserve_Sin backups" >&2
    exit 1
fi

if [[ ! -f "$database_path" ]]; then
    echo "Reserve_Sin database does not exist: $database_path" >&2
    exit 1
fi

install -d -m 0700 "$backup_directory"

readonly timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
readonly backup_path="$backup_directory/reserve-sin-$timestamp.sqlite3"
readonly temporary_path="$backup_path.tmp"

cleanup() {
    rm -f -- "$temporary_path"
}
trap cleanup EXIT

sqlite3 "$database_path" ".backup '$temporary_path'"

if [[ "$(sqlite3 "$temporary_path" 'PRAGMA integrity_check;')" != "ok" ]]; then
    echo "Reserve_Sin backup integrity check failed" >&2
    exit 1
fi

chmod 0600 "$temporary_path"
mv -- "$temporary_path" "$backup_path"
find "$backup_directory" -maxdepth 1 -type f -name 'reserve-sin-*.sqlite3' -mtime +13 -delete

echo "Reserve_Sin backup created: $backup_path"
