#!/usr/bin/env bash
# Chụp từng màn hình trên emulator và in ra log dạng base64 (giữa SHOT_BEGIN/SHOT_END)
# để xem lại giao diện khi không tải được artifact. Chỉ chạy trên CI.
P=com.gamebox.helper
shot() {
  sleep "${2:-7}"
  adb exec-out screencap -p > /tmp/s.png
  convert /tmp/s.png -resize '720x720>' -quality 65 /tmp/s.jpg
  echo "SHOT_BEGIN $1"; base64 /tmp/s.jpg; echo "SHOT_END $1"
}
hub()  { adb shell am start -S -W -n $P/.HubActivity "$@" >/dev/null; }
game() { adb shell am start -S -W -n $P/.MainActivity "$@" >/dev/null; }
adb shell wm size
hub --ei tab 1;                 shot hub_home 10
hub --ei tab 0;                 shot hub_profile
hub --ei tab 2 --ei opt 0;      shot hub_map
hub --ei tab 2 --ei opt 1;      shot hub_touch
hub --ei tab 2 --ei opt 2;      shot hub_fix
hub --ei tab 1 --ez act true;   shot hub_act
game --ei page 0;               shot gs_games 9
game --ei page 1;               shot gs_perf
game --ei page 2;               shot gs_bat
game --ei page 3;               shot gs_gfx
