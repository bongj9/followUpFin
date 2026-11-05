import { beforeAll, describe, expect, it, vi } from 'vitest'
import { biasBoard, consensus, isRecent, saidFor, whoMentioned, type Episode, type Src } from './shows'

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

  it('biasBoard: 진행자 먼저, 게스트·패널은 최근 순, 2주 밖은 뺀다', () => {
    const b = (bias: '롱' | '숏' | '중립') => ({ bias })
    const board = biasBoard([
      { ...ep('shuka', '2026-11-02', {}), panel: [{ name: '김장열', bias: '롱', view: '락바텀', reasons: [] }] },
      { ...ep('wepoll', '2026-11-01', {}), bias: b('중립'), guest: { name: '강재구', headline: '', points: [], bias: b('롱') } },
      { ...ep('alsangmu', '2026-10-31', {}), bias: b('숏') },
      { ...ep('alsangmu', '2026-10-01', {}), bias: b('롱') },
    ])
    expect(board.map(([who, vs]) => [who, vs.map(v => v.bias)])).toEqual([['알상무', ['숏']], ['박종훈', ['중립']], ['김장열', ['롱']], ['강재구', ['롱']]])
  })
})
