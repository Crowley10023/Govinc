#!/usr/bin/env bash
set -euo pipefail

# make-admin.sh - Promote a database user to ADMIN role and check for duplicates.
# Reads database connection settings from app/src/main/resources/application.properties.
#
# Usage:
#   ./scripts/make-admin.sh
#   ./scripts/make-admin.sh <email_or_full_name>

IDENTITY="${1:-}"

# ── Locate application.properties ────────────────────────────────────────────

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
PROPS_FILE="$REPO_ROOT/app/src/main/resources/application.properties"

if [ ! -f "$PROPS_FILE" ]; then
  echo "ERROR: Cannot find application.properties at: $PROPS_FILE" >&2
  exit 1
fi

# ── Parse a key from application.properties ──────────────────────────────────

get_prop() {
  local key="$1"
  grep -E "^\s*${key}\s*=" "$PROPS_FILE" | head -1 | sed "s/^\s*${key}\s*=\s*//" | sed 's/[[:space:]]*$//'
}

# ── Read connection settings ──────────────────────────────────────────────────

JDBC_URL="$(get_prop 'spring\.datasource\.url')"
DB_USER="$(get_prop 'spring\.datasource\.username')"
DB_PASS="$(get_prop 'spring\.datasource\.password')"

if [ -z "$JDBC_URL" ]; then
  echo "ERROR: spring.datasource.url not found in application.properties" >&2
  exit 1
fi

# Parse jdbc:mariadb://host:port/dbname  (or jdbc:mysql://...)
if [[ "$JDBC_URL" =~ jdbc:[^:]+://([^:/]+):([0-9]+)/([^?;]+) ]]; then
  DB_HOST="${BASH_REMATCH[1]}"
  DB_PORT="${BASH_REMATCH[2]}"
  DB_NAME="${BASH_REMATCH[3]}"
elif [[ "$JDBC_URL" =~ jdbc:[^:]+://([^:/]+)/([^?;]+) ]]; then
  DB_HOST="${BASH_REMATCH[1]}"
  DB_PORT="3306"
  DB_NAME="${BASH_REMATCH[2]}"
else
  echo "ERROR: Cannot parse JDBC URL: $JDBC_URL" >&2
  exit 1
fi

echo ""
echo "Database : ${DB_USER}@${DB_HOST}:${DB_PORT}/${DB_NAME}"
echo "(settings read from application.properties)"
echo ""

# ── Verify mysql/mariadb client ───────────────────────────────────────────────

MYSQL_CMD=""
for candidate in mysql mariadb; do
  if command -v "$candidate" &>/dev/null; then
    MYSQL_CMD="$candidate"
    break
  fi
done

if [ -z "$MYSQL_CMD" ]; then
  echo "ERROR: mysql / mariadb client not found. Install mysql-client or mariadb-client." >&2
  exit 1
fi

# ── Temp credentials file (avoids password on process list) ──────────────────

TEMP_CFG="$(mktemp -t mysql_cfg_XXXXXX.cnf)"
chmod 600 "$TEMP_CFG"
cat > "$TEMP_CFG" <<EOF
[client]
user=${DB_USER}
password=${DB_PASS}
host=${DB_HOST}
port=${DB_PORT}
EOF

trap 'rm -f "$TEMP_CFG"' EXIT INT TERM

# ── Helper functions ──────────────────────────────────────────────────────────

run_sql() {
  "$MYSQL_CMD" --defaults-extra-file="$TEMP_CFG" --batch --silent "$DB_NAME" -e "$1"
}

run_sql_table() {
  "$MYSQL_CMD" --defaults-extra-file="$TEMP_CFG" "$DB_NAME" -e "$1"
}

esc_sql() {
  printf "%s" "$1" | sed "s/'/''/g"
}

# ── 1. Check for duplicate users ─────────────────────────────────────────────

echo "=== Checking for duplicate users ==="
echo ""

DUP_EMAILS="$(run_sql "SELECT email FROM \`user\` WHERE email IS NOT NULL AND email <> '' GROUP BY email HAVING COUNT(*) > 1;")"

if [ -z "$DUP_EMAILS" ]; then
  echo "No duplicate e-mail addresses found."
else
  echo "Duplicate e-mail addresses detected:"
  echo ""
  while IFS= read -r email <&3; do
    [ -z "$email" ] && continue
    ESC_EMAIL="$(esc_sql "$email")"
    echo "--- Duplicate entries for: $email ---"
    run_sql_table "SELECT id, first_name, last_name, email, role FROM \`user\` WHERE email='${ESC_EMAIL}';"
    echo ""

    IDS="$(run_sql "SELECT id FROM \`user\` WHERE email='${ESC_EMAIL}';" | tr '\n' ' ' | sed 's/ $//')"
    echo "Available IDs: $IDS"
    read -r -p "IDs to DELETE (space-separated, or ENTER to skip): " TO_DELETE
    if [ -z "$TO_DELETE" ]; then
      echo "Skipped."
    else
      for del_id in $TO_DELETE; do
        if [[ "$IDS" =~ (^| )$del_id( |$) ]] && [[ "$del_id" =~ ^[0-9]+$ ]]; then
          run_sql "DELETE FROM \`user\` WHERE id=${del_id};"
          echo "Deleted user id=${del_id}"
        else
          echo "ID ${del_id} not in duplicate list — skipped for safety."
        fi
      done
    fi
    echo ""
  done 3<<< "$DUP_EMAILS"
fi

# ── Also check for duplicate full names ──────────────────────────────────────

DUP_NAMES="$(run_sql "SELECT CONCAT(TRIM(first_name), ' ', TRIM(last_name)) AS full_name FROM \`user\` GROUP BY TRIM(first_name), TRIM(last_name) HAVING COUNT(*) > 1;")"

if [ -n "$DUP_NAMES" ]; then
  echo "Duplicate full names detected:"
  echo ""
  while IFS= read -r full_name <&3; do
    [ -z "$full_name" ] && continue
    fn_part="${full_name%% *}"
    ln_part="${full_name#* }"
    [ "$ln_part" = "$fn_part" ] && ln_part=""
    ESC_FN="$(esc_sql "$fn_part")"
    ESC_LN="$(esc_sql "$ln_part")"
    WHERE_NAME="TRIM(first_name)='${ESC_FN}' AND TRIM(last_name)='${ESC_LN}'"

    echo "--- Duplicate entries for: $full_name ---"
    run_sql_table "SELECT id, first_name, last_name, email, role FROM \`user\` WHERE ${WHERE_NAME};"
    echo ""

    IDS="$(run_sql "SELECT id FROM \`user\` WHERE ${WHERE_NAME};" | tr '\n' ' ' | sed 's/ $//')"
    echo "Available IDs: $IDS"
    read -r -p "IDs to DELETE (space-separated, or ENTER to skip): " TO_DELETE
    if [ -z "$TO_DELETE" ]; then
      echo "Skipped."
    else
      for del_id in $TO_DELETE; do
        if [[ "$IDS" =~ (^| )$del_id( |$) ]] && [[ "$del_id" =~ ^[0-9]+$ ]]; then
          run_sql "DELETE FROM \`user\` WHERE id=${del_id};"
          echo "Deleted user id=${del_id}"
        else
          echo "ID ${del_id} not in duplicate list — skipped for safety."
        fi
      done
    fi
    echo ""
  done 3<<< "$DUP_NAMES"
fi

# ── 2. Promote a user to ADMIN ────────────────────────────────────────────────

echo "=== Promote user to ADMIN ==="
echo ""

if [ -z "$IDENTITY" ]; then
  echo "Current users in the database:"
  run_sql_table "SELECT id, first_name, last_name, email, role FROM \`user\` ORDER BY id;"
  echo ""
  read -r -p "Enter e-mail address or full name to promote (or ENTER to skip): " IDENTITY
fi

if [ -z "$IDENTITY" ]; then
  echo "No user specified — skipping promotion."
  exit 0
fi

ESC_IDENT="$(esc_sql "$IDENTITY")"

if echo "$IDENTITY" | grep -q "@"; then
  WHERE="email='${ESC_IDENT}'"
else
  FN_PART="${IDENTITY%% *}"
  LN_PART="${IDENTITY#* }"
  [ "$LN_PART" = "$IDENTITY" ] && LN_PART=""
  ESC_FN="$(esc_sql "$FN_PART")"
  ESC_LN="$(esc_sql "$LN_PART")"
  WHERE="TRIM(first_name)='${ESC_FN}' AND TRIM(last_name)='${ESC_LN}'"
fi

PREVIEW="$(run_sql_table "SELECT id, first_name, last_name, email, role FROM \`user\` WHERE ${WHERE};")"

if [ -z "$PREVIEW" ]; then
  echo "No user found matching: $IDENTITY"
  exit 1
fi

echo "User(s) to be promoted:"
echo "$PREVIEW"
echo ""
read -r -p "Set role to ADMIN for the above user(s)? [y/N] " confirm
if [[ "$confirm" =~ ^[Yy]$ ]]; then
  run_sql "UPDATE \`user\` SET role='ADMIN' WHERE ${WHERE};"
  echo ""
  echo "Updated:"
  run_sql_table "SELECT id, first_name, last_name, email, role FROM \`user\` WHERE ${WHERE};"
  echo ""
  echo "Done."
else
  echo "Aborted."
fi
