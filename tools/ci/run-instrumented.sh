#!/usr/bin/env bash
# Phase 2 / AUD-013: emulator runner entry for connectedDebugAndroidTest.
# Terse on success; surfaces errors as ::error annotations (the agent sandbox cannot read raw job logs).
set -uo pipefail
cd "$(dirname "$0")/../.." || exit 2

echo "== java =="; java -version 2>&1 | head -2
echo "== adb =="; which adb && adb devices

set +e
./gradlew connectedDebugAndroidTest --console=plain > /tmp/it.log 2>&1
code=$?
echo "== connectedDebugAndroidTest exit: $code =="
echo "== /tmp/it.log size: $(wc -c < /tmp/it.log 2>/dev/null || echo MISSING) =="

grep -E '^e: ' /tmp/it.log | head -40 | sed -E 's#^e: (file://)?([^:]+):([0-9]+):[0-9]+ (.*)#::error file=\2,line=\3::\4#'
grep -E 'FAILED|failing tests|IllegalStateException|Caused by' /tmp/it.log | head -20 | sed 's#^#::error ::#'
if [ "$code" -ne 0 ]; then
  head -60 /tmp/it.log | sed 's#^#::error ::#'
  echo "---- tail ----"
  tail -40 /tmp/it.log | sed 's#^#::error ::#'
fi
exit "$code"
