#!/usr/bin/env bash
set -euo pipefail
mkdir -p ui-evidence
adb shell wm size 1280x720
adb shell wm density "$1"
adb shell settings put system font_scale "$2"
adb install -r apk/debug/app-debug.apk
adb install -r apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.chuankangkk.wrenchlift.debug.test/androidx.test.runner.AndroidJUnitRunner | tee ui-evidence/instrumentation.txt
for preview in worker-remote-off worker-remote-on worker-params worker-guide; do
  if ! adb exec-out run-as com.chuankangkk.wrenchlift.debug cat "files/$preview.png" > "ui-evidence/$preview.png"; then
    rm -f "ui-evidence/$preview.png"
  fi
done
grep -Eq 'OK \([0-9]+ tests?\)' ui-evidence/instrumentation.txt
adb shell pm grant com.chuankangkk.wrenchlift.debug android.permission.ACCESS_FINE_LOCATION
adb shell pm grant com.chuankangkk.wrenchlift.debug android.permission.ACCESS_COARSE_LOCATION
if [ "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" -ge 33 ]; then
  adb shell pm grant com.chuankangkk.wrenchlift.debug android.permission.NEARBY_WIFI_DEVICES
fi
adb shell am start -W -n com.chuankangkk.wrenchlift.debug/com.chuankangkk.wrenchlift.MainActivity | tee ui-evidence/startup.txt
grep -q 'Status: ok' ui-evidence/startup.txt
adb shell dumpsys package com.chuankangkk.wrenchlift.debug | grep -E 'versionCode=|versionName=' > ui-evidence/version.txt
