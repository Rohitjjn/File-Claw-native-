#!/usr/bin/env bash
set +e
log_file="$RUNNER_TEMP/docx-android-tests.log"
./gradlew :app:connectedDebugAndroidTest --stacktrace --console=plain > "$log_file" 2>&1
gradle_status=$?
set -e
cat "$log_file"
if [ "$gradle_status" -ne 0 ]; then
  {
    echo "## Android instrumentation Gradle output (last 160 lines)"
    echo '```text'
    tail -n 160 "$log_file"
    echo '```'
  } >> "$GITHUB_STEP_SUMMARY"
  diagnostic=$(grep -E 'FAILED|Failure|failed|Exception|AssertionError|Tests run:' "$log_file" | tail -n 30 | tr '\n' ' ' | sed 's/%/%25/g')
  if [ -n "$diagnostic" ]; then
    echo "::error title=Android instrumentation Gradle failure::$diagnostic"
  fi
  exit "$gradle_status"
fi
