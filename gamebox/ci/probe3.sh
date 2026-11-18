#!/usr/bin/env bash
# Chạy trọn luồng bấm "Chơi" trên emulator với cầu nối shell mô phỏng Shizuku (debug/Bridge.kt):
# chọn game, giảm độ phân giải 60%, mở game, kiểm tra wm size, rồi cho game thoát và kiểm tra tự khôi phục.
P=com.gamebox.helper
GAME=com.android.settings
APK=$(adb shell pm path $P | head -1 | cut -d: -f2 | tr -d '\r')
dims() { adb exec-out screencap -p | python3 -c "import sys,struct; d=sys.stdin.buffer.read(32); print('x'.join(map(str, struct.unpack('>II', d[16:24]))))"; }
wmstate() { echo "$(adb shell wm size | tr -d '\r' | tr '\n' ' ')| $(adb shell wm density | tr -d '\r' | tr '\n' ' ')"; }

echo "E2E_PROBE: bấm Chơi với cầu nối shell"
adb shell "run-as $P sh -c 'mkdir -p files; touch files/bridge'"
adb shell "pkill -f ShellServer" >/dev/null 2>&1
adb shell "CLASSPATH=$APK app_process /system/bin com.gamebox.helper.ShellServer 8765" > /tmp/shellsrv.log 2>&1 &
SRV=$!
sleep 3
echo "E2E ban đầu: $(wmstate) · ảnh chụp $(dims)"
adb logcat -c
adb shell am start -S -W -n $P/.HubActivity --es play $GAME --ei res 60 --ei rnd 2 >/dev/null
sleep 18
echo "E2E sau Chơi: $(wmstate) · ảnh chụp $(dims)"
echo "E2E tiến trình game: $(adb shell pidof $GAME | tr -d '\r')"
echo "E2E dịch vụ overlay: $(adb shell dumpsys activity services $P | grep -c 'com.gamebox.helper/.Overlay')"
echo "E2E cờ phiên:"; adb shell "run-as $P cat shared_prefs/gs.xml" | grep -E 'wm_|sess|res_' | sed 's/^/   /'
echo "E2E logcat:"; adb logcat -d | grep -E "AndroidRuntime|FATAL|GB_CREATE" | head -8 | sed 's/^/   /'
adb shell am force-stop $GAME
sleep 14
echo "E2E sau khi game thoát: $(wmstate)"
echo "E2E cờ sau:"; adb shell "run-as $P cat shared_prefs/gs.xml" | grep -E 'wm_|sess' | sed 's/^/   /'
echo "E2E máy chủ shell:"; head -n 5 /tmp/shellsrv.log | sed 's/^/   /'
kill $SRV >/dev/null 2>&1
adb shell "pkill -f ShellServer" >/dev/null 2>&1
adb shell wm size reset; adb shell wm density reset
adb shell "run-as $P rm -f files/bridge"
adb shell am force-stop $P
