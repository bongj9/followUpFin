import { describe, expect, it } from 'vitest'
import { bollinger, ema, rsi, sma, spanA, spanB } from './indicators'

// 기대값은 손으로 계산한 것 (지표 수식을 옮기다 깨지기 쉬워서)
describe('indicators', () => {
  it('sma', () => expect(sma([1, 2, 3, 4, 5, 6], 3)).toEqual([null, null, 2, 3, 4, 5]))

  it('ema: k = 2/(n+1), 첫 값에서 시작해 n개부터 낸다', () =>
    expect(ema([1, 2, 3, 4, 5, 6], 3)).toEqual([null, null, 2.25, 3.125, 4.0625, 5.03125]))

  it('bollinger: 평균 ± 2 × 모표준편차', () => {
    const vals = [2, 4, 4, 4, 5, 5, 7, 9] // 평균 5, 표준편차 2
    expect(bollinger(vals, 8, 1).at(-1)).toBe(9)
    expect(bollinger(vals, 8, -1).at(-1)).toBe(1)
  })

  it('rsi: 와일더 평활', () => expect(rsi([1, 2, 3, 2], 2)).toEqual([null, null, 100, 50]))

  it('선행스팬: 26봉 앞으로 밀어 그려서 봉 수 + 26개, i번째 봉의 값이 i + 26 자리에', () => {
    const bars = Array.from({ length: 60 }, (_, i) => ({ time: i, open: i, high: i + 1, low: i, close: i, volume: 0 }))
    const a = spanA(bars), b = spanB(bars)
    expect([a.length, b.length]).toEqual([86, 86])
    expect(a[25 + 26]).toBe(((26 + 17) / 2 + (26 + 0) / 2) / 2) // 25번째 봉: 전환선 (17~25 고저 중간) 21.5, 기준선 (0~25) 13 → 17.25
    expect(b[59 + 26]).toBe((60 + 8) / 2) // 52봉(8~59) 고가 60, 저가 8
  })
})
