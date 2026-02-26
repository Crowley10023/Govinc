#!/usr/bin/env bash
set -euo pipefail

# make-admin.sh - Promote a user to ADMIN role in the database
# Usage: ./scripts/make-admin.sh <username_or_email>
#
# The script will read .build-setup.local (if present in repo root) for DB settings
# The .build-setup.local created by build-setup.sh uses variables:
#   DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD
# Environment variables override file values.

# Allow overriding the build config filename via env
BUILD_CONFIG_FILE="${BUILD_CONFIG_FILE:-.build-setup.local}"

# Defaults (fallback)
DB_HOST=${DB_HOST:-govinc}
DB_PORT=${DB_PORT:-3306}
DB_NAME=${DB_NAME:-govinc}
DB_USER=${DB_USER:-govinc}
# build-setup writes DB_PASSWORD; some scripts use DB_PASS. Support both.
DB_PASSWORD=${DB_PASSWORD:-}
DB_PASS=${DB_PASS:-}

# If build config file exists, source it and ensure both DB_PASSWORD and DB_PASS are populated
if [ -f "$BUILD_CONFIG_FILE" ]; then
  # shellcheck source=.build-setup.local
  # disable SC1091 for dynamic file
  # shellcheck disable=SC1091
  source "$BUILD_CONFIG_FILE"
  echo "Loaded DB settings from $BUILD_CONFIG_FILE"

  # If file provided DB_PASSWORD and DB_PASS is unset, set DB_PASS
  if [ -n "${DB_PASSWORD:-}" ] && [ -z "${DB_PASS:-}" ]; then
    DB_PASS="$DB_PASSWORD"
  fi
  # If DB_PASS is set (env or file) and DB_PASSWORD is unset, set DB_PASSWORD
  if [ -n "${DB_PASS:-}" ] && [ -z "${DB_PASSWORD:-}" ]; then
    DB_PASSWORD="$DB_PASS"
  fi
fi

# Apply defaults if still unset
DB_HOST=${DB_HOST:-govinc}
DB_PORT=${DB_PORT:-3306}
DB_NAME=${DB_NAME:-govinc}
DB_USER=${DB_USER:-govinc}
DB_PASS=${DB_PASS:-xxxxx}
DB_PASSWORD=${DB_PASSWORD:-$DB_PASS}

IDENT=${1:-}

if [ -z "$IDENT" ]; then
  echo "Usage: $0 <username_or_email>"
  exit 1
fi

# Escape single quotes for SQL
ESC_IDENT=$(printf "%s" "$IDENT" | sed "s/'/''/g")

# Decide whether input is an email
if echo "$IDENT" | grep -q "@"; then
  WHERE="email='${ESC_IDENT}'"
else
  WHERE="name='${ESC_IDENT}'"
fi

# Warn and require confirmation
echo "This will set role='ADMIN' for rows in table \`user\` where $WHERE on database ${DB_USER}@${DB_HOST}:${DB_PORT}/${DB_NAME}."
read -r -p "Are you sure you want to continue? [y/N] " confirm
if [[ ! "$confirm" =~ ^[Yy]$ ]]; then
  echo "Aborted."
  exit 1
fi

# Ensure mysql client exists
if ! command -v mysql &> /dev/null; then
  echo "Error: mysql client not found. Install mysql-client or mariadb-client to use this script."
  exit 1
fi

# Create a temporary MySQL client config to avoid exposing password on the process list
temp_cfg=$(mktemp -t mysql_cfg_XXXXXX.cnf)
cat > "$temp_cfg" <<EOF
[client]
user=$DB_USER
password=$DB_PASS
host=$DB_HOST
port=$DB_PORT
EOF
chmod 600 "$temp_cfg"

# Ensure the config is removed on exit
trap 'rm -f "$temp_cfg"' EXIT INT TERM

# Run update and then show the affected row(s)
mysql --defaults-extra-file="$temp_cfg" "$DB_NAME" -e "UPDATE \`user\` SET role='ADMIN' WHERE $WHERE; SELECT id, name, email, role FROM \`user\` WHERE $WHERE;"

echo "Done."
