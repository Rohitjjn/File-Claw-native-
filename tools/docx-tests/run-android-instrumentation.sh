#!/usr/bin/env bash
set +e
log_file="$RUNNER_TEMP/docx-android-tests.log"
./gradlew :app:connectedDebugAndroidTest --stacktrace --console=plain > "$log_file" 2>&1
gradle_status=$?
set -e
cat "$log_file"
if [ "$gradle_status" -ne 0 ]; then
  {
    echo "## Android instrumentation compiler/test failure details"
    echo '```text'
    grep -nE '(^e: |^w: |error:|FAILED|Failure|failed|Exception|AssertionError|Tests run:)' "$log_file" | tail -n 100 || tail -n 240 "$log_file"
    echo '```'
  } >> "$GITHUB_STEP_SUMMARY"
  diagnostic=$(grep -E '(^e: |^w: |error:|FAILED|Failure|failed|Exception|AssertionError|Tests run:)' "$log_file" | tail -n 60 | tr '\n' ' ' | sed 's/%/%25/g')
  if [ -n "$diagnostic" ]; then
    echo "::error title=Android instrumentation Gradle failure::$diagnostic"
  fi
  exit "$gradle_status"
fi
