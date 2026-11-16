#!/usr/bin/env bash
# Thử đúng lệnh đo FPS của Rt.fps() trên emulator. Dùng trang Tối ưu game của app (cuộn được) làm "game".
P=com.gamebox.helper
adb shell am start -S -W -n $P/.HubActivity --ei tab 2 --ei opt 0 >/dev/null
sleep 6
# Vuốt chậm liên tục trong nền để app sinh khung hình thật (app đứng yên thì không có khung, FPS = 0 là đúng)
adb shell "input swipe 160 500 160 100 9000" &
sleep 1.5
echo "FPS_PROBE layers:"
adb shell "dumpsys SurfaceFlinger --list | grep -F $P"
CMD='dumpsys SurfaceFlinger --list | grep -F "'$P'" | grep -v -e "^Background for" -e "^Bounds for" | head -n 8 | while IFS= read -r L; do echo "@@$L"; dumpsys SurfaceFlinger --latency "$L"; done'
for i in 1 2 3; do
  adb shell "$CMD" > /tmp/fps$i.txt
  python3 - "$i" <<'PY'
import sys
i = sys.argv[1]
best = 0; name = None; ts = []; out = []
def flush():
    global best, ts
    if name and len(ts) >= 2:
        last = max(ts); win = sorted(t for t in ts if t > last - 1_000_000_000)
        span = win[-1] - win[0]
        f = int((len(win) - 1) * 1e9 / span) if len(win) >= 3 and span > 200_000_000 else len(win)
        out.append((name, len(ts), f)); best = max(best, f)
    ts = []
for raw in open('/tmp/fps%s.txt' % i, errors='ignore'):
    l = raw.strip()
    if l.startswith('@@'):
        flush(); name = l[2:]; continue
    c = l.split()
    if len(c) >= 3:
        try:
            t = int(c[1])
        except ValueError:
            continue
        if 0 < t < 2**62: ts.append(t)
flush()
print('FPS_PROBE run', i, 'best =', best, 'layers =', out)
PY
  sleep 1.2
done
