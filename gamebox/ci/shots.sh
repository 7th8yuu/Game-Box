#!/usr/bin/env bash
# Chụp từng màn hình trên emulator và in ra log dạng base64 (giữa SHOT_BEGIN/SHOT_END)
# để xem lại giao diện khi không tải được artifact. Chỉ chạy trên CI.
P=com.gamebox.helper
shot() {
  sleep "${2:-7}"
  adb exec-out screencap -p > /tmp/s.png
  # Có PIL thì nén JPEG cho nhẹ log, không thì gửi nguyên PNG
  python3 - <<'PY' || cp /tmp/s.png /tmp/s.out
from PIL import Image
im = Image.open('/tmp/s.png').convert('RGB')
im.thumbnail((800, 800))
im.save('/tmp/s.out', 'JPEG', quality=70)
PY
  echo "SHOT_BEGIN $1"; base64 /tmp/s.out; echo "SHOT_END $1"
}
hub()  { adb shell am start -S -W -n $P/.HubActivity "$@" >/dev/null; }
game() { adb shell am start -S -W -n $P/.MainActivity "$@" >/dev/null; }
# Các bài thử lệnh hệ thống chạy trước khi giả lập kích thước máy (vì wm.sh tự đổi và trả lại wm size)
bash "$(dirname "$0")/wm.sh" 2>&1
bash "$(dirname "$0")/fps.sh"
bash "$(dirname "$0")/probe2.sh" 2>&1
bash "$(dirname "$0")/probe3.sh" 2>&1
# Giả lập kích thước gần máy thật (720x1600, 360dp ngang) để bố cục giống điện thoại
adb shell wm size 720x1600
adb shell wm density 320
adb shell wm size
hub --ei tab 1;                 shot hub_home 10
hub --ei tab 0;                 shot hub_profile
hub --ei tab 2 --ei opt 0;      shot hub_map
hub --ei tab 2 --ei opt 1;      shot hub_touch
hub --ei tab 2 --ei opt 2;      shot hub_fix
hub --ei tab 1 --ez act true;   shot hub_act
hub --ei tab 1 --ez diag true;  shot hub_diag 6
for k in 0 1 2 3 4; do hub --ei tab 2 --ei opt 1 --ei lab $k; shot lab_$k 4; done
game --ei page 0;               shot gs_games 9
game --ei page 1;               shot gs_perf
game --ei page 2;               shot gs_bat
game --ei page 3;               shot gs_gfx
game --ei page 0 --ez tex true; shot gs_tex 8
# Overlay: nút nổi, thanh dính mép, bảng trượt ra (cần quyền vẽ đè đã cấp ở bước trước)
game --ei page 0 --ez smoke_overlay true --ei ov_dock 0 --ez ov_open false;  shot ov_float 9
game --ei page 1 --ez smoke_overlay true --ei ov_dock 1 --ez ov_open false;  shot ov_dock_left
game --ei page 1 --ez smoke_overlay true --ei ov_dock 2 --ez ov_open true;   shot ov_panel_right
game --ei page 0 --ez smoke_overlay true --ei ov_dock 0 --ez ov_open true;   shot ov_panel_float
