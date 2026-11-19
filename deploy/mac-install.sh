#!/bin/zsh
# 이 맥에서 followUpFin을 상시 실행한다: 로그인하면 켜지고, 죽으면 launchd가 다시 켠다. 코드를 바꾼 뒤 다시 실행하면 새 버전으로 바뀐다
# 로그 ~/Library/Logs/followupfin.log · 끄기: launchctl bootout gui/$(id -u)/com.bongj9.followupfin
# DB는 Docker Desktop의 compose 컨테이너(restart: unless-stopped)라 Docker Desktop이 로그인 때 켜져 있어야 한다
set -e
cd "${0:A:h}/.."
label=com.bongj9.followupfin
home="$HOME/Library/Application Support/followUpFin"
plist="$HOME/Library/LaunchAgents/$label.plist"
java="$(/usr/libexec/java_home -v 21)/bin/java"

./gradlew -q bootJar
mkdir -p "$home"
cp build/libs/followUpFin-*-SNAPSHOT.jar(om[1]) "$home/followUpFin.jar" # 개발 중 다시 빌드해도 돌고 있는 앱은 안 바뀐다

# caffeinate -s: 전원이 연결돼 있을 때만 잠자기를 막는다 (배터리일 땐 잠들고, 깨면 이어서 수집)
# WorkingDirectory: secret.yml을 ./secret.yml로 읽는다
cat > "$plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
  <key>Label</key><string>$label</string>
  <key>ProgramArguments</key><array>
    <string>/usr/bin/caffeinate</string><string>-s</string>
    <string>$java</string><string>-Dfile.encoding=UTF-8</string><string>-jar</string><string>$home/followUpFin.jar</string>
  </array>
  <key>WorkingDirectory</key><string>$PWD</string>
  <key>RunAtLoad</key><true/>
  <key>KeepAlive</key><true/>
  <key>ThrottleInterval</key><integer>30</integer>
  <key>StandardOutPath</key><string>$HOME/Library/Logs/followupfin.log</string>
  <key>StandardErrorPath</key><string>$HOME/Library/Logs/followupfin.log</string>
</dict></plist>
PLIST

launchctl bootout "gui/$(id -u)/$label" 2>/dev/null || true
launchctl bootstrap "gui/$(id -u)" "$plist"
echo "등록됨: $label (로그 ~/Library/Logs/followupfin.log)"
