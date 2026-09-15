#!/bin/zsh
set -euo pipefail

if [[ $# -lt 1 || $# -gt 2 ]]; then
  print -u2 "用法: $0 <profile> [--update|--orientation-only|--bitmap-baseline|--bitmap-nodpi|--rss-interaction|--overdraw-solid|--overdraw-solid-update|--overdraw-interaction|--manga-layout|--file-path-layout|--launcher3|--launcher3-update]"
  exit 2
fi

repository_root=${0:a:h:h}
matrix="$repository_root/scripts/android-golden-matrix.tsv"
profile=$1
update=${2:-}
sdk_root=${ANDROID_SDK_ROOT:-/Users/back/Library/Android/sdk}
adb="$sdk_root/platform-tools/adb"
emulator="$sdk_root/emulator/emulator"

row=$(awk -F '\t' -v expected="$profile" 'NR > 1 && $3 == expected { print; exit }' "$matrix")
if [[ -z "$row" ]]; then
  print -u2 "未知 golden profile: $profile"
  exit 2
fi

IFS=$'\t' read -r expected_api avd _ theme density window device_kind <<< "$row"
if [[ "$update" != "" && "$update" != "--update" && "$update" != "--orientation-only" &&
  "$update" != "--bitmap-baseline" && "$update" != "--bitmap-nodpi" &&
  "$update" != "--rss-interaction" && "$update" != "--overdraw-solid" &&
  "$update" != "--overdraw-solid-update" && "$update" != "--overdraw-interaction" &&
  "$update" != "--manga-layout" && "$update" != "--file-path-layout" &&
  "$update" != "--launcher3" && "$update" != "--launcher3-update" ]]; then
  print -u2 "第二个参数只允许为 --update、--orientation-only、--bitmap-baseline、--bitmap-nodpi、--rss-interaction、--overdraw-solid、--overdraw-solid-update、--overdraw-interaction、--manga-layout、--file-path-layout、--launcher3 或 --launcher3-update"
  exit 2
fi

mkdir -p "$repository_root/app/build/golden-run/$profile"
emulator_log="$repository_root/app/build/golden-run/$profile/emulator.log"
existing_emulator_serials=$("$adb" devices | awk '$1 ~ /^emulator-/ { print $1 }')
"$emulator" "@$avd" -skin "$window" -no-window -no-audio -no-boot-anim -wipe-data \
  >"$emulator_log" 2>&1 &
emulator_pid=$!
serial=""

is_existing_emulator() {
  local candidate=$1 existing
  while IFS= read -r existing; do
    if [[ "$candidate" == "$existing" ]]; then
      return 0
    fi
  done <<< "$existing_emulator_serials"
  return 1
}

find_new_emulator_serial() {
  local candidate
  for candidate in ${(@f)$("$adb" devices | awk '$1 ~ /^emulator-/ && $2 == "device" { print $1 }')}; do
    if ! is_existing_emulator "$candidate"; then
      print -r -- "$candidate"
      return 0
    fi
  done
  return 1
}

device_ready() {
  local boot_completed boot_animation activity_service window_service package_service font_scale
  boot_completed=$("$adb" -s "$serial" shell getprop sys.boot_completed | tr -d '\r')
  boot_animation=$("$adb" -s "$serial" shell getprop init.svc.bootanim | tr -d '\r')
  activity_service=$("$adb" -s "$serial" shell service check activity 2>/dev/null | tr -d '\r')
  window_service=$("$adb" -s "$serial" shell service check window 2>/dev/null | tr -d '\r')
  package_service=$("$adb" -s "$serial" shell service check package 2>/dev/null | tr -d '\r')
  font_scale=$("$adb" -s "$serial" shell settings get system font_scale 2>/dev/null | tr -d '\r')
  [[ "$boot_completed" == "1" || "$boot_animation" == "stopped" ]] &&
    [[ "$activity_service" == *"found"* ]] &&
    [[ "$window_service" == *"found"* ]] &&
    [[ "$package_service" == *"found"* ]] &&
    [[ "$font_scale" != "" && "$font_scale" != *"Error"* ]]
}

display_configuration_matches() {
  local size_state density_state size_matches density_matches
  size_state=$("$adb" -s "$serial" shell wm size 2>/dev/null | tr -d '\r')
  density_state=$("$adb" -s "$serial" shell wm density 2>/dev/null | tr -d '\r')
  size_matches=false
  density_matches=false
  if [[ "$size_state" == *"Override size: $window"* ]] ||
    [[ "$size_state" != *"Override size:"* && "$size_state" == *"Physical size: $window"* ]]; then
    size_matches=true
  fi
  if [[ "$density_state" == *"Override density: $density"* ]] ||
    [[ "$density_state" != *"Override density:"* && "$density_state" == *"Physical density: $density"* ]]; then
    density_matches=true
  fi
  [[ "$size_matches" == true && "$density_matches" == true ]]
}

cleanup() {
  if [[ -n "$serial" ]]; then
    "$adb" -s "$serial" emu kill >/dev/null 2>&1 || true
  fi
  if kill -0 "$emulator_pid" 2>/dev/null; then
    kill "$emulator_pid" >/dev/null 2>&1 || true
  fi
  wait "$emulator_pid" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

for _ in {1..90}; do
  serial=$(find_new_emulator_serial || true)
  if [[ -n "$serial" ]] && device_ready; then
    break
  fi
  sleep 2
done
if [[ -z "$serial" ]]; then
  print -u2 "AVD 未在期限内启动: $avd"
  exit 1
fi
if [[ "$("$adb" -s "$serial" shell getprop ro.kernel.qemu | tr -d '\r')" != "1" ]]; then
  print -u2 "golden 脚本只允许运行在可丢弃模拟器"
  exit 1
fi
actual_api=$("$adb" -s "$serial" shell getprop ro.build.version.sdk | tr -d '\r')
if [[ "$actual_api" != "$expected_api" ]]; then
  print -u2 "API 不匹配: profile=$expected_api, device=$actual_api"
  exit 1
fi
if [[ "$device_kind" == "multiwindow" ]]; then
  "$adb" -s "$serial" shell settings put global development_settings_enabled 1
  "$adb" -s "$serial" shell settings put global enable_freeform_support 1
  "$adb" -s "$serial" shell settings put global force_resizable_activities 1
fi

for _ in {1..30}; do
  "$adb" -s "$serial" shell wm size "$window" >/dev/null 2>&1 || true
  "$adb" -s "$serial" shell wm density "$density" >/dev/null 2>&1 || true
  if display_configuration_matches; then
    break
  fi
  sleep 2
done
if ! display_configuration_matches; then
  print -u2 "无法应用显示参数: profile=$profile size=$window density=$density"
  exit 1
fi
if [[ "$device_kind" == "multiwindow" ]]; then
  "$adb" -s "$serial" shell settings put global development_settings_enabled 1
  "$adb" -s "$serial" shell settings put global enable_freeform_support 1
  "$adb" -s "$serial" shell settings put global force_resizable_activities 1
fi
"$adb" -s "$serial" reboot
"$adb" -s "$serial" wait-for-device
for _ in {1..90}; do
  if device_ready; then
    break
  fi
  sleep 2
done
if ! device_ready; then
  print -u2 "显示参数重启后设备未就绪: $profile"
  exit 1
fi
if ! display_configuration_matches; then
  print -u2 "重启后显示参数漂移: profile=$profile size=$window density=$density"
  exit 1
fi
"$adb" -s "$serial" shell settings put system font_scale 1.0
"$adb" -s "$serial" shell settings put global window_animation_scale 0
"$adb" -s "$serial" shell settings put global transition_animation_scale 0
"$adb" -s "$serial" shell settings put global animator_duration_scale 0
"$adb" -s "$serial" shell settings put secure immersive_mode_confirmations confirmed
"$adb" -s "$serial" shell pm clear io.legado.app.debug >/dev/null 2>&1 || true
"$adb" -s "$serial" shell pm clear io.legado.app.debug.test >/dev/null 2>&1 || true

JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home \
ANDROID_HOME="$sdk_root" \
ANDROID_SDK_ROOT="$sdk_root" \
  "$repository_root/gradlew" :app:assembleAppDebug :app:assembleAppDebugAndroidTest \
  --no-daemon --console=plain

app_apk=$(find "$repository_root/app/build/outputs/apk/app/debug" -maxdepth 1 -name '*.apk' -print -quit)
test_apk=$(find "$repository_root/app/build/outputs/apk/androidTest/app/debug" -maxdepth 1 -name '*.apk' -print -quit)
"$adb" -s "$serial" install -r -t "$app_apk"
"$adb" -s "$serial" install -r -t "$test_apk"

instrument_args=(
  -w -r
  -e class io.legado.app.quality.GoldenBaselineTest
  -e legado.golden.profile "$profile"
)
if [[ "$update" == "--orientation-only" ]]; then
  instrument_args=(
    -w -r
    -e class io.legado.app.quality.BehindOrientationInstrumentedTest
    -e legado.golden.profile "$profile"
  )
fi
if [[ "$update" == "--bitmap-baseline" || "$update" == "--bitmap-nodpi" ]]; then
  bitmap_mode=${update#--bitmap-}
  instrument_args=(
    -w -r
    -e class io.legado.app.quality.BitmapDensityInstrumentedTest
    -e legado.golden.profile "$profile"
    -e legado.bitmap.mode "$bitmap_mode"
  )
fi
if [[ "$update" == "--rss-interaction" ]]; then
  instrument_args=(
    -w -r
    -e class io.legado.app.quality.RssItemInteractionInstrumentedTest
    -e legado.golden.profile "$profile"
  )
fi
if [[ "$update" == "--overdraw-solid" || "$update" == "--overdraw-solid-update" ]]; then
  instrument_args=(
    -w -r
    -e class io.legado.app.quality.OverdrawSolidGoldenInstrumentedTest
    -e legado.golden.profile "$profile"
  )
fi
if [[ "$update" == "--overdraw-interaction" ]]; then
  instrument_args=(
    -w -r
    -e class io.legado.app.quality.OverdrawInteractionInstrumentedTest
    -e legado.golden.profile "$profile"
  )
fi
if [[ "$update" == "--manga-layout" ]]; then
  instrument_args=(
    -w -r
    -e class io.legado.app.quality.MangaMenuLayoutInstrumentedTest
    -e legado.golden.profile "$profile"
  )
fi
if [[ "$update" == "--file-path-layout" ]]; then
  instrument_args=(
    -w -r
    -e class io.legado.app.quality.FilePathLayoutInstrumentedTest
    -e legado.golden.profile "$profile"
  )
fi
if [[ "$update" == "--launcher3" || "$update" == "--launcher3-update" ]]; then
  instrument_args=(
    -w -r
    -e class io.legado.app.quality.Launcher3GoldenInstrumentedTest
    -e legado.golden.profile "$profile"
  )
fi
if [[ "$update" == "--update" || "$update" == "--overdraw-solid-update" ||
  "$update" == "--launcher3-update" ]]; then
  instrument_args+=(-e legado.golden.update true)
fi
instrument_log="$repository_root/app/build/golden-run/$profile/instrumentation.txt"
if [[ "$update" == "--orientation-only" ]]; then
  instrument_log="$repository_root/app/build/golden-run/$profile/orientation-instrumentation.txt"
fi
if [[ "$update" == "--bitmap-baseline" || "$update" == "--bitmap-nodpi" ]]; then
  instrument_log="$repository_root/app/build/golden-run/$profile/bitmap-${update#--bitmap-}-instrumentation.txt"
fi
if [[ "$update" == "--rss-interaction" ]]; then
  instrument_log="$repository_root/app/build/golden-run/$profile/rss-interaction-instrumentation.txt"
fi
if [[ "$update" == "--overdraw-solid" || "$update" == "--overdraw-solid-update" ]]; then
  instrument_log="$repository_root/app/build/golden-run/$profile/overdraw-solid-instrumentation.txt"
fi
if [[ "$update" == "--overdraw-interaction" ]]; then
  instrument_log="$repository_root/app/build/golden-run/$profile/overdraw-interaction-instrumentation.txt"
fi
if [[ "$update" == "--manga-layout" ]]; then
  instrument_log="$repository_root/app/build/golden-run/$profile/manga-layout-instrumentation.txt"
fi
if [[ "$update" == "--file-path-layout" ]]; then
  instrument_log="$repository_root/app/build/golden-run/$profile/file-path-layout-instrumentation.txt"
fi
if [[ "$update" == "--launcher3" || "$update" == "--launcher3-update" ]]; then
  instrument_log="$repository_root/app/build/golden-run/$profile/launcher3-instrumentation.txt"
fi
"$adb" -s "$serial" shell am instrument "${instrument_args[@]}" \
  io.legado.app.debug.test/androidx.test.runner.AndroidJUnitRunner | tee "$instrument_log"
if grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg=Process crashed' \
  "$instrument_log"; then
  diff_output="$repository_root/app/build/golden-diffs/$profile"
  mkdir -p "$diff_output"
  remote_diffs="/sdcard/Android/data/io.legado.app.debug/files/golden-diffs/$profile"
  if "$adb" -s "$serial" shell test -d "$remote_diffs"; then
    "$adb" -s "$serial" pull "$remote_diffs/." "$diff_output" || true
  else
    for fixture in direction icon_catalog bookshelf rss welcome transparent dialog manga_menu file_path overdraw_solid_activity overdraw_solid_items; do
      for artifact in current expected diff; do
        if "$adb" -s "$serial" shell run-as io.legado.app.debug \
          test -f "files/golden-diffs/$profile/$fixture/$artifact.png"; then
          mkdir -p "$diff_output/$fixture"
          "$adb" -s "$serial" exec-out run-as io.legado.app.debug \
            cat "files/golden-diffs/$profile/$fixture/$artifact.png" \
            >"$diff_output/$fixture/$artifact.png"
        fi
      done
    done
  fi
  print -u2 "profile instrumentation 失败: $instrument_log"
  exit 1
fi

if [[ "$update" == "--update" || "$update" == "--overdraw-solid-update" ||
  "$update" == "--launcher3-update" ]]; then
  output="$repository_root/app/build/golden-updates/$profile"
  mkdir -p "$output"
  remote_external="/sdcard/Android/data/io.legado.app.debug/files/golden-updates/$profile"
  if ! "$adb" -s "$serial" pull "$remote_external/." "$output"; then
    fixture_names=(direction icon_catalog bookshelf rss welcome transparent dialog manga_menu file_path)
    if [[ "$update" == "--overdraw-solid-update" ]]; then
      fixture_names=(overdraw_solid_activity overdraw_solid_items)
    elif [[ "$update" == "--launcher3-update" ]]; then
      fixture_names=(launcher3)
    fi
    for fixture in "${fixture_names[@]}"; do
      "$adb" -s "$serial" exec-out run-as io.legado.app.debug \
        cat "files/golden-updates/$profile/$fixture.png" >"$output/$fixture.png"
    done
  fi
  print "候选 golden 已输出到 $output；脚本不会改写版本化预期图片。"
fi

if [[ "$update" == "--bitmap-baseline" || "$update" == "--bitmap-nodpi" ]]; then
  bitmap_mode=${update#--bitmap-}
  bitmap_output="$repository_root/app/build/lint-cleanup-evidence/task-4-bitmap-$profile-$bitmap_mode.tsv"
  "$adb" -s "$serial" exec-out run-as io.legado.app.debug \
    cat "files/bitmap-density-$profile-$bitmap_mode.tsv" >"$bitmap_output"
fi

if [[ "$update" == "--orientation-only" ]]; then
  print "方向 profile 通过: api=$expected_api window=$window kind=$device_kind"
elif [[ "$update" == "--bitmap-baseline" || "$update" == "--bitmap-nodpi" ]]; then
  print "位图密度 profile 通过: api=$expected_api density=$density mode=${update#--bitmap-}"
elif [[ "$update" == "--rss-interaction" ]]; then
  print "RSS 交互 profile 通过: api=$expected_api theme=$theme density=$density"
elif [[ "$update" == "--overdraw-solid" || "$update" == "--overdraw-solid-update" ]]; then
  print "Overdraw 纯色背景 profile 通过: api=$expected_api theme=$theme density=$density"
elif [[ "$update" == "--overdraw-interaction" ]]; then
  print "Overdraw 交互 profile 通过: api=$expected_api theme=$theme density=$density"
elif [[ "$update" == "--manga-layout" ]]; then
  print "漫画菜单布局 profile 通过: api=$expected_api theme=$theme density=$density"
elif [[ "$update" == "--file-path-layout" ]]; then
  print "文件路径布局 profile 通过: api=$expected_api theme=$theme density=$density"
elif [[ "$update" == "--launcher3" || "$update" == "--launcher3-update" ]]; then
  print "launcher3 profile 通过: api=$expected_api theme=$theme density=$density"
else
  print "golden profile 通过: api=$expected_api theme=$theme density=$density window=$window kind=$device_kind"
fi
