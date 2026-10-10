#!/usr/bin/env bash
set -euo pipefail

REPO="${1:-KOKUTBON/xCAPITAL}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PAYLOAD="${ROOT_DIR}/.github/branch-protection/main.json"

if ! command -v gh >/dev/null 2>&1; then
  echo "gh CLI is required" >&2
  exit 1
fi

gh api \
  --method PUT \
  -H "Accept: application/vnd.github+json" \
  "/repos/${REPO}/branches/main/protection" \
  --input "${PAYLOAD}" >/dev/null

echo "Applied branch protection policy to ${REPO}:main"
