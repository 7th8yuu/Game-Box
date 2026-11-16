#!/usr/bin/env bash
# Thử đúng lệnh đo FPS của Rt.fps() trên emulator. Dùng chính app làm "game" vì nền động luôn vẽ lại.
P=com.gamebox.helper
adb shell am start -S -W -n $P/.MainActivity >/dev/null
sleep 8
# Vuốt liên tục trong nền để app có khung hình thật (app đứng yên thì không sinh khung nên FPS = 0 là đúng)
adb shell "input swipe 900 300 100 300 6000" &
sleep 1
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
  sleep 1
done
head -4 /tmp/fps1.txt
