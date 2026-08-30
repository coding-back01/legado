#!/usr/bin/env bash

set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "用法: $0 <模拟器序列号>" >&2
  exit 2
fi

serial=$1
repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$repo_root"

adb -s "$serial" get-state >/dev/null
if [[ "$serial" != emulator-* ]]; then
  echo "发布烟测拒绝非 emulator 序列号: $serial" >&2
  exit 1
fi
if [[ "$(adb -s "$serial" shell getprop ro.kernel.qemu | tr -d '\r')" != "1" ]]; then
  echo "发布烟测只允许在 Android 模拟器运行" >&2
  exit 1
fi

./gradlew :app:assembleAppDebug :app:assembleAppDebugAndroidTest \
  --build-cache --no-daemon --warning-mode all

shopt -s nullglob
app_apks=(app/build/outputs/apk/app/debug/*.apk)
test_apks=(app/build/outputs/apk/androidTest/app/debug/*.apk)
if [[ ${#app_apks[@]} -ne 1 || ${#test_apks[@]} -ne 1 ]]; then
  echo "需要精确一个 appDebug APK 和一个 androidTest APK" >&2
  exit 1
fi

adb -s "$serial" install -r "${app_apks[0]}"
adb -s "$serial" install -r "${test_apks[0]}"
adb -s "$serial" shell pm clear io.legado.app.debug >/dev/null
adb -s "$serial" shell pm clear io.legado.app.debug.test >/dev/null

instrumentation=$(
  adb -s "$serial" shell pm list instrumentation |
    tr -d '\r' |
    awk '/androidx.test.runner.AndroidJUnitRunner/ && /target=io.legado.app.debug/ {
      sub(/^instrumentation:/, "")
      sub(/ \(target=.*/, "")
      print
      exit
    }'
)
if [[ -z "$instrumentation" ]]; then
  echo "找不到 appDebug 的 AndroidJUnitRunner" >&2
  exit 1
fi

adb -s "$serial" shell am instrument -w -r \
  -e annotation io.legado.app.release.ReleaseSmoke \
  "$instrumentation"
