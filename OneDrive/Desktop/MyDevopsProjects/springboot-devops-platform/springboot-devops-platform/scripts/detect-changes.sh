#!/usr/bin/env bash
set -euo pipefail
BASE="${GIT_BASE:-HEAD~1}"
HEAD="${GIT_HEAD:-HEAD}"
changed="$(git diff --name-only "$BASE" "$HEAD" || true)"
if [[ -z "$changed" ]]; then exit 0; fi
if echo "$changed" | grep -Eq '^(pom.xml|shared-library/|docker/|Jenkinsfile|scripts/)'; then
  printf '%s\n' api-gateway user-service auth-service product-service inventory-service cart-service order-service payment-service shipping-service notification-service
else
  printf '%s\n' "$changed" | awk -F/ '$1=="services" && $2!="" {print $2}' | sort -u
fi
