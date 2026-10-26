---
name: morning-report
description: "아침 리포트" — 보유 종목과 시장(코스피·코스닥·나스닥·S&P·반도체)을 이평선·볼린저·일목·RSI·MACD로 판정한 숫자와 알상무 최신 뷰를 합쳐, 종목별 유지·분할 매수·비중 축소 같은 오늘의 할 일과 현금 비중을 followUpFin 홈 "아침 리포트" 카드에 쓴다. 사용자가 "아침 리포트", "오늘 리포트", "매수 매도 정리해줘"라고 할 때, 그리고 평일 아침 자동 실행(launchd)에서 쓴다.
---

# 아침 리포트

숫자는 스크립트가 뽑고, 판단만 내가 쓴다. 숫자를 지어내지 않는다. 결과에는 보유 수량·비중이 들어가므로 공개 저장소가 아니라 볼트에 쓴다.

- 저장소 `R`: 이 스킬이 있는 followUpFin 루트
- 사실 스크립트: `$R/frontend/scripts/report-facts.ts` (화면과 같은 지표 판정 함수 사용)
- 알상무 노트: `~/.claude/skills/alsangmu-update/web/views/alsangmu.json`
- 결과: `~/.claude/skills/alsangmu-update/web/views/report.json` (앱이 `/views/report.json`으로 읽음, 볼트라 동기화·백업됨)

## 1. 서버 확인

```bash
curl -sf localhost:8080/api/stocks >/dev/null || echo DOWN
```

DOWN이면 띄운다: `docker info` 실패 시 `open -a Docker` 후 될 때까지 기다리고, `cd $R && docker compose up -d`, `nohup ./gradlew bootRun > /tmp/followupfin-boot.log 2>&1 &` 후 `/api/stocks`가 응답할 때까지(최대 3분) 기다린다. 끝내 안 뜨면 리포트를 쓰지 말고 이유를 보고한다.

## 2. 사실 뽑기

```bash
cd $R/frontend && npx tsx scripts/report-facts.ts > <scratchpad>/facts.json
```

`holdings`가 비었으면(토스 키 없음·실패) 시장 부분만 쓴다. 들어 있는 것: 시장별 `daily`/`weekly`(`status` 지금 상태, `recent` 최근 5봉 신호, 일봉엔 52주 고저), 코스피·코스닥 `rules`(알상무 규칙 순서의 수급·이평선·크로스·볼린저 사실), 보유 종목별 수량·평단·현재가·손익률·원화 평가액·비중(`weightPct`).

## 3. 알상무 최신 뷰와 지난 리포트 읽기

- `alsangmu.json`의 `daily[0]`(날짜·방송·headline·stance 현금·levels·stocks), `rules`(비중 규칙), `stocks[코드]`(레버리지 ETF는 `aliases`로 기초 종목을 찾는다)
- 슈카 노트 `~/.claude/skills/alsangmu-update/web/views/shuka.json`(없으면 건너뜀)의 `daily[0]`과 보유 종목·코스피에 대한 `daily[].stocks` 언급. 국장 분위기를 보는 두 번째 시각으로 쓰고, 알상무와 엇갈리면 그렇다고 쓴다
- 위폴 노트 `~/.claude/skills/alsangmu-update/web/views/wepoll.json`(없으면 건너뜀)의 `daily[0]`(진행자의 간밤 미국장 뷰와 `guest` 게스트 이름·주장)과 보유 종목 언급. 미국·반도체를 보는 세 번째 시각으로 쓴다. 방송이 07:00~08:30이라 아침 실행 땐 전날 편일 수 있으니 날짜를 적는다
- 기존 `report.json`이 있으면 최근 리포트들의 `watch` 조건과 `action`을 오늘 종가와 비교해 채점할 거리를 찾는다

## 4. 판단 쓰기

원칙:
- **단정하지 않는다.** "고점·저점"이라 말하지 않고 "과열권·하단 밖" 같은 상태와 그 상태에서 할 일을 쓴다. 모든 행동엔 언제 판단을 바꿀지(`watch`: 가격 레벨이나 지표 조건 → 할 일)를 붙인다
- **근거는 facts.json 숫자나 알상무 뷰(날짜 명시)에서만.** why 한 줄에 사실 하나와 그 해석
- 일봉으로 행동을, 주봉으로 큰 방향을 본다. 둘이 엇갈리면 그렇다고 쓴다
- **비중이 큰 종목부터, 길게.** 비중 1% 미만은 why 한 줄로 짧게
- 레버리지 ETF(MUU·SNXX·AMDL·KORU·SOXL 등)는 기초 종목 차트와 함께 보고, 횡보·하락장에서 녹는 구조와 한 종목 쏠림 위험을 비중 판단에 반영한다
- 현금 비중(`cashPct`)은 알상무 현금 비중에서 출발해 시장 사실로 가감하고, 왜 다른지 why에 쓴다. 그의 원칙(빠질 때 나눠 사기, 종가 근처 매수)은 할 일 문장에 녹인다
- `cashPct`는 (주식 평가액 + 현금) 중 현금 비율. 현금 잔고를 모르면 지금 현금 0으로 보고, 목표 금액 = 주식 평가액 × cashPct / (100 − cashPct)
- `date`·`generatedAt`은 지어내지 말고 `TZ=Asia/Seoul date +%F`, `date -Iseconds`로 넣는다
- action은 이 중 하나: `유지` · `분할 매수` · `비중 축소` · `분할 매도` · `관망`. 바꾸면 `targetPct`(목표 비중 %)를 같이

모양 (`api.ts`의 `Report`):

```json
{
  "date": "YYYY-MM-DD (KST 오늘)", "generatedAt": "ISO 시각",
  "headline": "오늘 할 일 한 문장",
  "market": { "stance": "방어|중립|공격", "cashPct": 40, "why": ["..."], "watch": [{ "if": "코스피 종가 5,500 이탈", "then": "현금 10%p 더 확보" }] },
  "holdings": [{ "code": "MUU", "name": "MUU (마이크론 2배)", "action": "비중 축소", "weightPct": 73.8, "targetPct": 50,
                 "why": ["..."], "watch": [{ "if": "...", "then": "..." }], "alsangmu": "10/23 RTS: 마이크론은 ..." }],
  "alsangmu": { "date": "2026-10-23", "show": "RTS", "headline": "..." },
  "review": [{ "date": "지난 리포트 날짜", "call": "그때 판단", "result": "오늘 보니 맞았다/틀렸다 + 숫자" }]
}
```

## 5. 반영

`<scratchpad>/report.json`에 오늘 것 하나를 쓰고 합친다 (같은 날짜는 덮어쓰고, 최신이 앞, 30개까지):

```bash
python3 - <scratchpad>/report.json <<'EOF'
import json, os, sys
out = os.path.expanduser('~/.claude/skills/alsangmu-update/web/views/report.json')
new = json.load(open(sys.argv[1]))
for k in ('date', 'headline', 'market', 'holdings'): assert k in new, k
old = json.load(open(out))['reports'] if os.path.exists(out) else []
reports = [new] + [r for r in old if r['date'] != new['date']]
json.dump({'reports': reports[:30]}, open(out, 'w'), ensure_ascii=False, indent=1)
print(len(reports[:30]), 'reports')
EOF
```

서버 재시작은 필요 없다. `curl -s localhost:8080/views/report.json | head -c 200`으로 앱이 읽는지 확인한다.

## 6. 보고 (한국어)

headline, 시장 태도·현금 목표, 비중 큰 종목들의 action 한 줄씩, 채점 결과. 참고 자료이고 투자 판단은 본인 몫이라는 점을 유지한다.
