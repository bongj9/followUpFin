import { describe, expect, it } from 'vitest'
import type { Bar, Report } from '@/api'
import { followGains } from './follow'

// 미국 봉은 UTC 자정 시각으로 온다
const bar = (date: string, close: number): Bar => ({ time: Date.parse(date + 'T00:00:00Z') / 1000, open: close, high: close, low: close, close, volume: 0 })
// weightPct는 주식 안 비중, targetPct는 주식+현금 기준
const report = (date: string, weightPct: number, targetPct: number | null, more: { valueKrw?: number; price?: number; account?: Report['account'] } = {}): Report => ({
  date, generatedAt: '', headline: '', market: { stance: '방어', why: [], watch: [] }, account: more.account,
  holdings: [{ code: 'MUU', name: 'MUU', action: '비중 축소', weightPct, targetPct, valueKrw: more.valueKrw, price: more.price, why: [], watch: [] }],
})

describe('followGains', () => {
  const bars = { MUU: [bar('2026-11-04', 20), bar('2026-11-10', 15), bar('2026-11-11', 16.5)] }

  it('현금이 없을 때 반 팔라 했는데 25% 빠졌으면 판 돈의 25%를 아낀 셈', () => {
    // 11/4: 그날 평가액 1,000만 원(주식의 80% → 주식 합 1,250만 원), 목표 40% = 500만 원 → 500만 원어치 팔고 11/10까지 20 → 15 (-25%)
    const [old] = followGains([report('2026-11-10', 80, null), report('2026-11-04', 80, 40, { valueKrw: 10_000_000 })], bars, {}, 0)
    expect(old).toEqual({ date: '2026-11-04', calls: ['MUU 80→40%'], gain: 1_250_000, to: '2026-11-10', open: false })
  })

  it('현금이 절반이면 주식 안 80%는 전체의 40%라 팔 게 없다', () => {
    const [old] = followGains([report('2026-11-10', 80, null), report('2026-11-04', 80, 40,
      { valueKrw: 10_000_000, account: { stockKrw: 12_500_000, cashKrw: 12_500_000 } })], bars, {}, 0)
    expect(old).toMatchObject({ calls: ['MUU 40→40%'], gain: 0 })
  })

  it('리포트 쓴 시각의 평가액은 그날 종가로 옮겨서 쓴다', () => {
    // 장 전 16일 때 800만 원 → 그날 종가 20이면 1,000만 원. 첫 테스트와 같은 결과
    const [old] = followGains([report('2026-11-10', 80, null), report('2026-11-04', 80, 40, { valueKrw: 8_000_000, price: 16 })], bars, {}, 0)
    expect(old.gain).toBe(1_250_000)
  })

  it('마지막 리포트는 지금 값까지, 그날 금액이 없으면 지금 평가액을 가격 비율로 되돌린다', () => {
    // 지금 1,100만 원(16.5) → 11/10엔 1,000만 원(15). 절반 팔았으면 15 → 16.5 (+10%)를 놓쳐 -50만 원
    const [last] = followGains([report('2026-11-10', 80, 40)], bars, { MUU: 11_000_000 }, null)
    expect(last).toMatchObject({ gain: -500_000, to: null, open: true })
  })

  it('그 날 장이 아직(마지막 봉)이면 잴 게 없어 뺀다', () => expect(followGains([report('2026-11-11', 80, 40)], bars, { MUU: 1 }, 0)).toEqual([]))

  it('바꾸라는 말이 없으면 뺀다', () => expect(followGains([report('2026-11-10', 80, null)], bars, { MUU: 1 }, 0)).toEqual([]))
})
