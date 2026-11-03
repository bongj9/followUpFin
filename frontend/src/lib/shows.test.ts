import { beforeAll, describe, expect, it, vi } from 'vitest'
import { consensus, isRecent, saidFor, whoMentioned, type Episode, type Src } from './shows'

const ep = (src: Src, date: string, stocks: Record<string, string[]>): Episode => ({
  src, date, id: src + date, title: '', headline: '',
  stocks: Object.fromEntries(Object.entries(stocks).map(([k, v]) => [k, v.map(text => ({ text, cite: '' }))])),
})

// useShows처럼 최신순
const eps = [
  ep('shuka', '2026-11-03', { '005930': ['삼전 하락'] }),
  ep('wepoll', '2026-11-02', { MU: ['마이크론 실적'], '005930': ['외국인 매도'] }),
  ep('alsangmu', '2026-10-28', { MU: ['6일 전'] }),
]

describe('shows', () => {
  beforeAll(() => vi.setSystemTime(new Date('2026-11-03T21:00:00')))

  it('isRecent: 오늘 포함 7일 (11/3이면 10/28부터)', () => {
    expect(isRecent('2026-10-28')).toBe(true)
    expect(isRecent('2026-10-27')).toBe(false)
  })

  it('saidFor: 레버리지 ETF는 기초 종목 발언을 같이 본다', () => {
    expect(saidFor(eps, { MUU: 'MU' }, 'MUU').map(r => r.text)).toEqual(['마이크론 실적', '6일 전'])
    expect(saidFor(eps, {}, 'MUU')).toEqual([])
  })

  it('whoMentioned: 7일 안에 다룬 사람만, 중복 없이', () => {
    expect(whoMentioned(eps, {}, '005930')).toEqual(['shuka', 'wepoll'])
    expect(whoMentioned([...eps, ep('shuka', '2026-10-27', { MU: ['7일 전'] })], {}, 'MU')).toEqual(['wepoll', 'alsangmu'])
  })

  it('consensus: 7일 안에 두 사람 이상', () => {
    expect(consensus(eps).map(([code, who]) => [code, [...who]])).toEqual([['005930', ['shuka', 'wepoll']], ['MU', ['wepoll', 'alsangmu']]])
    expect(consensus(eps.slice(0, 1))).toEqual([])
  })
})
