import { describe, expect, it } from 'vitest'
import { trendLine } from './MarketRead'

// 120일 동안 1씩 오르다 마지막 날만 바꿔서 이평선 위치를 만든다 (20일선 = 최근 20일 평균)
const ramp = (last: number) => [...Array.from({ length: 129 }, (_, k) => 100 + k), last]

describe('trendLine', () => {
  it('세 이평선 위면 상승 추세', () => expect(trendLine(ramp(230))).toMatch(/정배열 → 상승 추세/))
  it('20일선만 깨면 단기 조정', () => expect(trendLine(ramp(215))).toMatch(/단기 조정/))
  it('20·60일선 깨면 중기 조정', () => expect(trendLine(ramp(185))).toMatch(/^20·60일선 아래/))
  it('다 깼어도 선이 정배열이면 추세 확정 전', () => expect(trendLine(ramp(100))).toMatch(/확정 전/))
  it('역배열 아래면 하락 추세', () => expect(trendLine([...Array.from({ length: 129 }, (_, k) => 300 - k), 100])).toMatch(/역배열 → 하락 추세/))
  it('20·120일선 아래·60일선 위는 엇갈림', () => {
    // 앞 70일은 높게 두어 120일선만 위에, 최근은 오르다 마지막 날 살짝 밀려 20일선 아래·60일선 위
    const c = [...Array(70).fill(300), ...Array.from({ length: 59 }, (_, k) => 100 + k * 2), 190]
    expect(trendLine(c)).toMatch(/엇갈림/)
  })
})
