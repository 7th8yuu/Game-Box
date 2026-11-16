#!/usr/bin/env bash
# Thử đúng các lệnh mà Wm.kt và Rt.applyGame dùng, để xem định dạng đầu ra thật trên emulator
echo "WM_PROBE before:"; adb shell wm size; adb shell wm density
adb shell wm size 192x384; adb shell wm density 96; sleep 2
echo "WM_PROBE after apply:"; adb shell wm size; adb shell wm density
adb shell wm size reset; adb shell wm density reset; sleep 2
echo "WM_PROBE after reset:"; adb shell wm size; adb shell wm density
echo "WM_PROBE game mode (thứ tự mới):"
for pk in com.android.chrome; do
  echo "-- $pk: set không --mode rồi mode custom"
  adb shell cmd game set --downscale 0.60 --fps 60 $pk
  adb shell cmd game list-modes $pk
  adb shell cmd game mode custom $pk
  adb shell cmd game list-modes $pk
  adb shell cmd game list-configs $pk
  adb shell cmd game reset $pk
  adb shell cmd game mode standard $pk
  echo "-- chỉ downscale, không fps"
  adb shell cmd game set --mode 2 --downscale 0.50 $pk
  adb shell cmd game list-configs $pk
  adb shell cmd game reset $pk
done
echo "WM_PROBE done"
