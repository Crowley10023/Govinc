#!/bin/bash

# Setup Git hooks for version management
# Run this script once to install git hooks

HOOKS_DIR=".git/hooks"

if [ ! -d ".git" ]; then
    echo "Error: Not in a Git repository root"
    exit 1
fi

# Ensure hooks directory exists
mkdir -p "$HOOKS_DIR"

# Copy post-push hook
if [ -f ".git-hooks/post-push.sh" ]; then
    cp ".git-hooks/post-push.sh" "$HOOKS_DIR/post-push"
    chmod +x "$HOOKS_DIR/post-push"
    echo "✓ post-push hook installed"
else
    echo "✗ .git-hooks/post-push.sh not found"
fi

echo "Git hooks setup complete!"
echo "Version will be auto-incremented after each push."
