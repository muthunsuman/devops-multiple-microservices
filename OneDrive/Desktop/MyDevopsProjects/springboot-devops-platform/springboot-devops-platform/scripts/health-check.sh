#!/usr/bin/env bash
set -euo pipefail
URL="${1:?Usage: health-check.sh https://host/actuator/health}"
ATTEMPTS="${ATTEMPTS:-12}"
SLEEP_SECONDS="${SLEEP_SECONDS:-5}"
for ((i=1; i<=ATTEMPTS; i++)); do
  if curl --fail --silent --show-error --max-time 5 "$URL"; then
    echo
    echo "Health check passed."
    exit 0
  fi
  echo "Health check attempt $i/$ATTEMPTS failed."
  sleep "$SLEEP_SECONDS"
done
echo "Health check failed."
exit 1
