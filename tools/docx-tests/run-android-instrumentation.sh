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
  logcat_file="$RUNNER_TEMP/docx-android-logcat.txt"
  adb logcat -d -v threadtime -t 4000 > "$logcat_file" 2>/dev/null || true
  logcat_failures=$(grep -Ei 'FATAL EXCEPTION|Fatal signal|Process .* has died|crash_dump|AndroidRuntime|chromium|WebView.*(crash|error)|RENDERER_UNAVAILABLE|AndroidJUnitRunner|TestRunner|am_instrument|ActivityManager|libc.*(fatal|signal)|instrumentation.*(crash|fail|exception)' "$logcat_file" | tail -n 240 || true)
  {
    echo "## Android instrumentation compiler/test failure details"
    echo '```text'
    grep -nE '(^e: |^w: |error:|FAILED|Failure|failed|Exception|AssertionError|Tests run:)' "$log_file" | tail -n 100 || tail -n 240 "$log_file"
    echo '```'
    echo "## Emulator logcat diagnostics (filtered; last 240 matches)"
    echo '```text'
    if [ -n "$logcat_failures" ]; then
      printf '%s\n' "$logcat_failures"
    else
      echo 'No filtered crash markers were found. Recent emulator logcat follows:'
      tail -n 180 "$logcat_file"
    fi
    echo '```'
  } >> "$GITHUB_STEP_SUMMARY"
  if [ -n "$logcat_failures" ]; then
    printf '%s\n' "$logcat_failures"
  else
    echo 'No filtered crash markers were found. Recent emulator logcat follows:'
    tail -n 180 "$logcat_file"
  fi
  diagnostic=$(grep -E '(^e: |^w: |error:|FAILED|Failure|failed|Exception|AssertionError|Tests run:)' "$log_file" | tail -n 60 | tr '\n' ' ' | sed 's/%/%25/g')
  if [ -n "$diagnostic" ]; then
    echo "::error title=Android instrumentation Gradle failure::$diagnostic"
  fi
  exit "$gradle_status"
fi
