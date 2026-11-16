import type { Bar, Report } from '@/api'

// 리포트대로 했으면: 리포트 날 종가에 목표 비중(targetPct)으로 맞추고 다음 리포트 날 종가(마지막은 지금 값)까지 들고 있었을 때,
// 안 하고 그대로 둔 것보다 얼마 더 벌었나. 판 돈은 현금(수익 0), 산 돈은 현금에서 나왔다고 본다
// open: 끝 값이 지금 값(마지막 봉)이라 아직 바뀐다
export interface FollowRow { date: string; calls: string[]; gain: number; to: string | null; open: boolean }

const kst = (time: number) => new Date((time + 9 * 3600) * 1000).toISOString().slice(0, 10) // 봉 날짜 (국내·미국 다 맞게)
// 그 날(휴장이면 다음 거래일) 종가
const closeOn = (bars: Bar[], date: string) => bars.find(b => kst(b.time) >= date)

// bars: 종목별 일봉, valueNow: 종목별 지금 원화 평가액, cashNow: 지금 현금(원)
// 비중의 분모가 둘이다: weightPct는 주식 안에서, targetPct는 주식+현금 전체에서. 그래서 금액으로 바꿔 계산한다
//   살 금액(−면 팔 금액) = 목표% × (주식 + 현금) − 그 종목 평가액
// ponytail: 리포트에 그날 금액(valueKrw·price·account)이 없으면 지금 평가액·현금을 가격 비율로 되돌려 추정한다
// (그 뒤 수량·현금이 그대로라고 가정). 환율 변동은 뺀다
export function followGains(reports: Report[], bars: Record<string, Bar[]>, valueNow: Record<string, number>, cashNow: number | null): FollowRow[] {
  return reports.map((r, i) => {
    const next = reports[i - 1]?.date // 최신이 앞이라 다음 리포트는 앞 칸
    let gain = 0, open = false
    const calls: string[] = []
    for (const h of r.holdings) {
      const b = bars[h.code], now = valueNow[h.code], w = h.weightPct
      if (h.targetPct == null || w == null || !b?.length) continue
      const from = closeOn(b, r.date)
      const lastBar = b[b.length - 1]
      if (!from || from === lastBar) continue // 그 날 장이 아직이거나 지금 열려 있어 잴 게 없다
      const exit = (next && closeOn(b, next)) || lastBar
      if (exit === from) continue // 주말 리포트처럼 다음 리포트와 같은 장에 걸리면 잴 게 없다
      // 그날 종가 기준 이 종목 평가액. valueKrw는 리포트 쓴 시각(장 전) 값이라 그때 가격 → 그날 종가로 옮긴다
      const base = h.valueKrw != null ? h.valueKrw * (h.price ? from.close / h.price : 1)
        : now == null ? null : now * from.close / lastBar.close
      // 그날 주식 합: 리포트에 있으면 그 값, 없으면 이 종목 평가액 ÷ 주식 안 비중
      const stock = r.account?.stockKrw ?? (base != null && w ? base / (w / 100) : null)
      if (base == null || stock == null) continue
      const total = stock + (r.account?.cashKrw ?? cashNow ?? 0)
      const trade = h.targetPct / 100 * total - base
      open ||= exit === lastBar // 끝 값이 마지막 봉이면 아직 움직이는 값
      gain += trade * (exit.close / from.close - 1)
      calls.push(`${h.code} ${+(base / total * 100).toFixed(1)}→${h.targetPct}%`) // 둘 다 주식+현금 기준으로 보여준다
    }
    return { date: r.date, calls, gain: Math.round(gain), to: next ?? null, open }
  }).filter(r => r.calls.length)
}
