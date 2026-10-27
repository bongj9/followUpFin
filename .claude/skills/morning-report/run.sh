#!/bin/zsh -l
# 평일 08:30 launchd가 부르고, 못 만들었을 때를 대비해 09:30·10:30에도 부른다 (~/Library/LaunchAgents/com.bongj9.morning-report.plist, 로그 ~/Library/Logs/morning-report.log)
# 사람 없이 돌아서 권한 확인을 건너뛴다
export PATH="$HOME/.local/bin:/opt/homebrew/bin:/usr/local/bin:$PATH" # launchd는 .zshrc를 안 읽는다 (claude·node·docker)
cd "${0:A:h}/../../.." || exit 1
today=$(TZ=Asia/Seoul date +%F)
grep -q "\"date\": \"$today\"" ~/.claude/skills/alsangmu-update/web/views/report.json 2>/dev/null && exit 0 # 오늘 것 이미 있음
echo "== $(date)"
# 잠자기 중 잠깐 깬 사이(DarkWake)에 시작되면 다시 잠들어 멈춘다: 도는 동안 깨어 있게 하고, 그래도 멈추면 40분에 끊는다
caffeinate -i timeout 40m claude -p "아침 리포트를 만들어줘. .claude/skills/morning-report/SKILL.md 순서대로 하고, 마지막에 요약을 출력해" --permission-mode bypassPermissions
