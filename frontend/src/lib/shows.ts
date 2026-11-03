import { useEffect, useState } from 'react'
import { getNote, getShow, type Cited, type Daily, type Stock } from '@/api'

// 세 노트(알상무·슈카·위폴)의 방송을 한 목록으로. 개요 카드·영상 요약·종목 발언 타임라인·오른쪽 패널이 같이 쓴다
export type Src = 'alsangmu' | 'shuka' | 'wepoll'
export interface Episode extends Daily { src: Src }
export interface Said extends Cited { ep: Episode }

export const WHO: Record<Src, { who: string; ini: string; color: string; sub: string }> = {
  alsangmu: { who: '알상무', ini: '알', color: 'var(--color-al)', sub: '당일전략·지표추적자·RTS' },
  shuka: { who: '슈카', ini: '슈', color: 'var(--color-sh)', sub: '머니코믹스 슈황' },
  wepoll: { who: '박종훈의 바이킹스', ini: '위', color: 'var(--color-wp)', sub: '위폴 아침 라이브' },
}

export function useShows(tick: number) {
  const [data, setData] = useState<{ eps: Episode[]; aliases: Record<string, string> }>({ eps: [], aliases: {} })
  useEffect(() => {
    Promise.all([getNote(), getShow('shuka'), getShow('wepoll')]).then(([a, s, w]) => {
      const eps = ([['alsangmu', a], ['shuka', s], ['wepoll', w]] as const)
        .flatMap(([src, n]) => (n?.daily ?? []).map(d => ({ ...d, src })))
        .sort((x, y) => y.date.localeCompare(x.date))
      setData({ eps, aliases: { ...s?.aliases, ...w?.aliases, ...a?.aliases } })
    })
  }, [tick])
  return data
}

// 노트의 종목 키는 국내는 코드, 미국은 티커, 일부는 이름 그대로다
const KR: Record<string, string> = {
  '000660': 'SK하이닉스', '005380': '현대차', '005930': '삼성전자', '006400': '삼성SDI', '009150': '삼성전기', '010140': '삼성중공업',
  '012450': '한화에어로스페이스', '017670': 'SK텔레콤', '034020': '두산에너빌리티', '035420': 'NAVER', '035720': '카카오', '042660': '한화오션',
  '042700': '한미반도체', '066570': 'LG전자', '086520': '에코프로', '196170': '알테오젠', '207940': '삼성바이오로직스',
}
export const nameOf = (code: string, stocks: Stock[]) => stocks.find(s => s.stockCode === code)?.name ?? KR[code] ?? code

// 이 종목에 대해 한 말. 레버리지 ETF(MUU 등)는 기초 종목(MU) 발언을 같이 본다. 최신순
export function saidFor(eps: Episode[], aliases: Record<string, string>, code: string): Said[] {
  const keys = [code, aliases[code]].filter((k, i, a): k is string => !!k && a.indexOf(k) === i)
  return eps.flatMap(ep => keys.flatMap(k => (ep.stocks?.[k] ?? []).map(r => ({ ...r, ep }))))
}

// 가장 최근 방송 날짜 기준 7일 안
export const isRecent = (eps: Episode[], date: string) => !eps.length || (+new Date(eps[0].date) - +new Date(date)) / 864e5 <= 7
export const whoMentioned = (eps: Episode[], aliases: Record<string, string>, code: string) =>
  [...new Set(saidFor(eps, aliases, code).filter(r => isRecent(eps, r.ep.date)).map(r => r.ep.src))]

// 최근 7일 안에 두 사람 이상이 다룬 종목, 많이 겹친 순
export function consensus(eps: Episode[]) {
  const by = new Map<string, Set<Src>>()
  for (const e of eps) if (isRecent(eps, e.date)) for (const c of Object.keys(e.stocks ?? {})) by.set(c, (by.get(c) ?? new Set()).add(e.src))
  return [...by].filter(([, s]) => s.size >= 2).sort((a, b) => b[1].size - a[1].size)
}

export const dateLabel = (date: string) => new Date(date + 'T00:00:00').toLocaleDateString('ko-KR', { month: 'long', day: 'numeric', weekday: 'short' })
