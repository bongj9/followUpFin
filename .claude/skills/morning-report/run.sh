#!/bin/zsh -l
# 평일 08:30 launchd가 부른다 (~/Library/LaunchAgents/com.bongj9.morning-report.plist, 로그 ~/Library/Logs/morning-report.log)
# Mac이 잠들어 있었으면 깨어날 때 한 번 돈다. 사람 없이 돌아서 권한 확인을 건너뛴다
export PATH="$HOME/.local/bin:/opt/homebrew/bin:/usr/local/bin:$PATH" # launchd는 .zshrc를 안 읽는다 (claude·node·docker)
cd "${0:A:h}/../../.." || exit 1
echo "== $(date)"
claude -p "아침 리포트를 만들어줘. .claude/skills/morning-report/SKILL.md 순서대로 하고, 마지막에 요약을 출력해" --permission-mode bypassPermissions
