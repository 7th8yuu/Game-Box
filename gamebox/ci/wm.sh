#!/usr/bin/env bash
# Thử đúng các lệnh mà Wm.kt và Rt.applyGame dùng, để xem định dạng đầu ra thật trên emulator
echo "WM_PROBE before:"; adb shell wm size; adb shell wm density
adb shell wm size 192x384; adb shell wm density 96; sleep 2
echo "WM_PROBE after apply:"; adb shell wm size; adb shell wm density
adb shell wm size reset; adb shell wm density reset; sleep 2
echo "WM_PROBE after reset:"; adb shell wm size; adb shell wm density
echo "WM_PROBE game mode (số):"
for pk in com.android.chrome com.google.android.apps.maps; do
  echo "-- $pk"
  adb shell cmd game set --mode 2 --downscale 0.60 --fps disable $pk
  adb shell cmd game set --mode 2 --downscale 0.60 --fps 60 $pk
  adb shell cmd game mode performance $pk
  adb shell cmd game list-configs $pk
  adb shell cmd game reset $pk
  adb shell cmd game mode standard $pk
done
adb shell cmd game help | head -30
echo "WM_PROBE done"
