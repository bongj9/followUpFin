import { describe, expect, it } from 'vitest'
import { bollinger, ema, rsi, sma } from './indicators'

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
})
