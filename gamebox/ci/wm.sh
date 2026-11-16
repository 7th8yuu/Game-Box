#!/usr/bin/env bash
# Thử đúng các lệnh mà Wm.kt và Rt.applyGame dùng, để xem định dạng đầu ra thật trên emulator
echo "WM_PROBE before:"; adb shell wm size; adb shell wm density
adb shell wm size 504x1120; adb shell wm density 224; sleep 2
echo "WM_PROBE after apply:"; adb shell wm size; adb shell wm density
adb shell wm size reset; adb shell wm density reset; sleep 2
echo "WM_PROBE after reset:"; adb shell wm size; adb shell wm density
echo "WM_PROBE game mode:"
adb shell cmd game set --mode performance --downscale 0.60 --fps disable com.android.chrome
adb shell cmd game mode performance com.android.chrome
adb shell cmd game list-configs com.android.chrome
adb shell cmd game reset com.android.chrome
adb shell cmd game mode standard com.android.chrome
echo "WM_PROBE done"
