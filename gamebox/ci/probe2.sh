#!/usr/bin/env bash
# Thử trên emulator (dưới quyền shell, cùng quyền với Shizuku) đúng mã đo FPS và đổi độ phân giải của app.
P=com.gamebox.helper
APK=$(adb shell pm path $P | head -1 | cut -d: -f2 | tr -d '\r')
cli() { adb shell "CLASSPATH=$APK app_process /system/bin com.gamebox.helper.$1 ${@:2}" 2>&1; }

echo "RECREATE_PROBE: đổi density khi app đang mở không được làm Activity bị tạo lại"
adb shell am start -S -W -n $P/.HubActivity >/dev/null; sleep 6
adb logcat -c
adb shell wm density 240; sleep 3
adb shell wm size 540x1200; sleep 3
echo "RECREATE_PROBE Hub onCreate sau khi đổi size+density: $(adb logcat -d -s GB_CREATE:I | grep -c 'Hub') (đúng = 0)"
adb shell wm size reset; adb shell wm density reset; sleep 2

echo "WMCLI_PROBE:"
cli WmCli 60 | sed 's/^/  /'

echo "SIM_PROBE: game giả GLSurfaceView"
adb shell am start -S -W -n $P/.SimActivity >/dev/null; sleep 5
echo "SIM layers:"; adb shell "dumpsys SurfaceFlinger --list | grep -F $P"
echo "SIM timestats thô (lọc gói):"
adb shell "dumpsys SurfaceFlinger --timestats -enable; sleep 2; dumpsys SurfaceFlinger --timestats -dump | grep -F -B4 -A8 -e $P | head -n 60"
echo "FPSCLI_PROBE:"
cli FpsCli $P 8 | sed 's/^/  /'
adb shell am force-stop $P
