#!/usr/bin/env bash
set -euo pipefail

# make-admin.sh - Promote a user to ADMIN role in the database
# Usage: ./scripts/make-admin.sh <username_or_email>
#
# The script will read .build-setup.local (if present in repo root) for DB settings
# The .build-setup.local created by build-setup.sh uses variables:
#   DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD
# Environment variables override file values.

# Defaults (fallback)
DB_HOST=${DB_HOST:-govinc}
DB_PORT=${DB_PORT:-3306}
DB_NAME=${DB_NAME:-govinc}
DB_USER=${DB_USER:-govinc}
DB_PASS=${DB_PASS:-xxxxx}

BUILD_CONFIG_FILE=".build-setup.local"

# If build config file exists, source it and map DB_PASSWORD -> DB_PASS
if [ -f "$BUILD_CONFIG_FILE" ]; then
  # shellcheck source=.build-setup.local
  # disable SC1091 for dynamic file
  # shellcheck disable=SC1091
  source "$BUILD_CONFIG_FILE"
  echo "Loaded DB settings from $BUILD_CONFIG_FILE"
  # If file provided DB_HOST/DB_PORT/DB_NAME/DB_USER they will override defaults via variables above
  # Map DB_PASSWORD (used by build-setup) to DB_PASS used here, unless DB_PASS already set via env
  if [ -n "${DB_PASSWORD:-}" ] && [ -z "${DB_PASS:-}" ]; then
    DB_PASS="$DB_PASSWORD"
  fi
fi

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

# Run update and then show the affected row(s)
mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASS" "$DB_NAME" -e "UPDATE \`user\` SET role='ADMIN' WHERE $WHERE; SELECT id, name, email, role FROM \`user\` WHERE $WHERE;"

echo "Done."
