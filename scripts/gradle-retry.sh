#!/bin/bash
# Runs ./gradlew with the given arguments, retrying while the only failures are Maven Central
# "429 Too Many Requests" rate limits (common in Claude Code cloud sessions). Gradle keeps what it
# downloaded, so each retry gets further.
#   scripts/gradle-retry.sh testDebugUnitTest assembleDebug
cd "$(dirname "$0")/.." || exit 1
LOG=$(mktemp)
for i in $(seq 1 10); do
  ./gradlew "$@" --no-daemon -q --max-workers=2 > "$LOG" 2>&1 && { echo "OK (try $i)"; rm -f "$LOG"; exit 0; }
  if ! grep -q "status code 429" "$LOG"; then
    grep -v JAVA_TOOL_OPTIONS "$LOG" | grep -E '^e: |error:|FAILED|What went wrong|^> |Exception|expected' | head -40
    echo "Full log: $LOG"
    exit 1
  fi
  echo "Rate-limited by Maven (429), retry $i"; sleep 5
done
echo "Still rate-limited after 10 tries. Full log: $LOG"; exit 1
