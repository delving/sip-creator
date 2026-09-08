#!/bin/bash
# Post-commit hook — appends commit info to the active plan doc if one exists.
# This builds a running log of what was actually committed during a session,
# which helps reconstruct the "what happened" narrative for docs/plans/.

# Only run after git commit commands
INPUT=$(cat)
COMMAND=$(printf '%s' "$INPUT" | jq -r '.tool_input.command // ""' 2>/dev/null || echo "")
if ! printf '%s' "$COMMAND" | grep -q 'git commit'; then
  exit 0
fi

PLAN_DIR="docs/plans"
if [ ! -d "$PLAN_DIR" ]; then
  exit 0
fi

# Find the most recently modified plan file (the one for the active session)
ACTIVE_PLAN=$(ls -t "$PLAN_DIR"/*.md 2>/dev/null | head -1)
if [ -z "$ACTIVE_PLAN" ]; then
  exit 0
fi

# Get the commit just made
COMMIT_HASH=$(git rev-parse --short HEAD 2>/dev/null)
COMMIT_MSG=$(git log -1 --pretty=format:"%s" 2>/dev/null)
COMMIT_STATS=$(git diff --stat HEAD~1 HEAD 2>/dev/null | tail -1)
TIMESTAMP=$(date '+%Y-%m-%d %H:%M')

# Append to the plan file under a ## Commits section
if ! grep -q "## Commits" "$ACTIVE_PLAN" 2>/dev/null; then
  echo "" >> "$ACTIVE_PLAN"
  echo "## Commits" >> "$ACTIVE_PLAN"
fi

echo "- \`${COMMIT_HASH}\` ${TIMESTAMP}: ${COMMIT_MSG} (${COMMIT_STATS})" >> "$ACTIVE_PLAN"
