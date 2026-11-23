#!/usr/bin/env bash
set -uo pipefail
log_file="$RUNNER_TEMP/docx-android-tests.log"
./gradlew :app:connectedDebugAndroidTest --stacktrace --console=plain > "$log_file" 2>&1
gradle_status=$?
cat "$log_file"

# Instrumentation writes visual evidence into the debuggable app's cache. Copy it
# into the workspace even after a failed assertion so Actions can upload it.
app_id="com.aistudio.filesclaw.apxqzy"
screenshot_dir="app/build/outputs/docx-test-screenshots"
mkdir -p "$screenshot_dir"
if command -v adb >/dev/null 2>&1; then
  screenshot_names=$(adb shell run-as "$app_id" ls cache/docx-test-screenshots 2>/dev/null | tr -d '\r' || true)
  while IFS= read -r screenshot_name; do
    [ -n "$screenshot_name" ] || continue
    case "$screenshot_name" in
      *.png) adb exec-out run-as "$app_id" cat "cache/docx-test-screenshots/$screenshot_name" > "$screenshot_dir/$screenshot_name" || true ;;
    esac
  done <<< "$screenshot_names"
fi

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
