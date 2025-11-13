#!/bin/bash

# Post-push hook to auto-increment version
# This hook runs after a successful push and increments the patch version

VERSION_FILE="version.txt"

if [ ! -f "$VERSION_FILE" ]; then
    exit 0
fi

# Read current version
CURRENT_VERSION=$(cat "$VERSION_FILE")

# Parse version components
IFS='.' read -ra VERSION_PARTS <<< "$CURRENT_VERSION"
MAJOR=${VERSION_PARTS[0]}
MINOR=${VERSION_PARTS[1]}
PATCH=${VERSION_PARTS[2]}

# Increment patch version
NEW_PATCH=$((PATCH + 1))
NEW_VERSION="$MAJOR.$MINOR.$NEW_PATCH"

# Update version file
echo "$NEW_VERSION" > "$VERSION_FILE"

echo "[post-push] Version bumped from $CURRENT_VERSION to $NEW_VERSION"
