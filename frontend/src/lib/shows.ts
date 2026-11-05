import { useEffect, useState } from 'react'
import { getNote, getShow, type Bias, type Cited, type Daily, type Stock } from '@/api'

// 세 노트(알상무·슈카·위폴)의 방송을 한 목록으로. 개요 카드·영상 요약·종목 발언 타임라인·오른쪽 패널이 같이 쓴다
export type Src = 'alsangmu' | 'shuka' | 'wepoll'
export interface Episode extends Daily { src: Src }
export interface Said extends Cited { ep: Episode }

export const WHO: Record<Src, { who: string; ini: string; color: string; sub: string }> = {
  alsangmu: { who: '알상무', ini: '알', color: 'var(--color-al)', sub: '당일전략·지표추적자·RTS' },
  shuka: { who: '슈카', ini: '슈', color: 'var(--color-sh)', sub: '머니코믹스 슈황' },
  wepoll: { who: '박종훈', ini: '위', color: 'var(--color-wp)', sub: '위폴 바이킹스' },
}

export function useShows(tick: number) {
  const [data, setData] = useState<{ eps: Episode[]; aliases: Record<string, string> }>({ eps: [], aliases: {} })
  useEffect(() => {
    Promise.all([getNote(), getShow('shuka'), getShow('wepoll')]).then(([a, s, w]) => {
      const eps = ([['alsangmu', a], ['shuka', s], ['wepoll', w]] as const)
        .flatMap(([src, n]) => (n?.daily ?? []).map(d => ({ ...d, src })))
        .filter(d => d.date).sort((x, y) => y.date.localeCompare(x.date))
      setData({ eps, aliases: { ...s?.aliases, ...w?.aliases, ...a?.aliases } })
    })
  }, [tick])
  return data
}

// 노트의 종목 키는 국내는 코드, 미국은 티커, 일부는 이름 그대로다
// 슈카 요약 지시(daily-prompt.md)의 코드표 + 노트에 나온 코드
const KR: Record<string, string> = {
  '000270': '기아', '000660': 'SK하이닉스', '000810': '삼성화재', '003550': 'LG', '005380': '현대차', '005930': '삼성전자', '006400': '삼성SDI',
  '009150': '삼성전기', '010120': 'LS ELECTRIC', '010140': '삼성중공업', '012450': '한화에어로스페이스', '017670': 'SK텔레콤', '028260': '삼성물산',
  '032830': '삼성생명', '034020': '두산에너빌리티', '035420': 'NAVER', '035720': '카카오', '042660': '한화오션', '042700': '한미반도체', '051910': 'LG화학',
  '055550': '신한지주', '064350': '현대로템', '066570': 'LG전자', '068270': '셀트리온', '079550': 'LIG넥스원', '086520': '에코프로', '096770': 'SK이노베이션',
  '105560': 'KB금융', '196170': '알테오젠', '207940': '삼성바이오로직스', '247540': '에코프로비엠', '267260': 'HD현대일렉트릭', '272210': '한화시스템',
  '329180': 'HD현대중공업', '373220': 'LG에너지솔루션',
}
export const nameOf = (code: string, stocks: Stock[]) => stocks.find(s => s.stockCode === code)?.name ?? KR[code] ?? code

// 이 종목에 대해 한 말. 레버리지 ETF(MUU 등)는 기초 종목(MU) 발언을 같이 본다. 최신순
export function saidFor(eps: Episode[], aliases: Record<string, string>, code: string): Said[] {
  const keys = [code, aliases[code]].filter((k, i, a): k is string => !!k && a.indexOf(k) === i)
  return eps.flatMap(ep => keys.flatMap(k => (ep.stocks?.[k] ?? []).map(r => ({ ...r, ep }))))
}

// 오늘 포함 7일 안 (11/3이면 10/28~). 노트가 한동안 안 쌓이면 아바타·겹친 종목이 비는 게 맞다
export const isRecent = (date: string, now = Date.now()) => (now - +new Date(date + 'T00:00:00')) / 864e5 < 7
export const whoMentioned = (eps: Episode[], aliases: Record<string, string>, code: string) =>
  [...new Set(saidFor(eps, aliases, code).filter(r => isRecent(r.ep.date)).map(r => r.ep.src))]

// 최근 7일 안에 두 사람 이상이 다룬 종목, 많이 겹친 순
export function consensus(eps: Episode[]) {
  const by = new Map<string, Set<Src>>()
  for (const e of eps) if (isRecent(e.date)) for (const c of Object.keys(e.stocks ?? {})) by.set(c, (by.get(c) ?? new Set()).add(e.src))
  return [...by].filter(([, s]) => s.size >= 2).sort((a, b) => b[1].size - a[1].size)
}

// 누가 어느 날 롱·숏·중립으로 봤나. 진행자, 위폴 게스트, 패널을 한 줄씩 펼친다
export interface View { who: string; sub?: string; src: Src; bias: Bias['bias']; horizon?: string | null; why?: string; ep: Episode }
export function views(eps: Episode[]): View[] {
  return eps.flatMap(ep => [
    ...(ep.bias ? [{ who: WHO[ep.src].who, src: ep.src, ...ep.bias, ep }] : []),
    ...(ep.guest?.bias ? [{ who: ep.guest.name, sub: ep.guest.firm, src: ep.src, ...ep.guest.bias, ep }] : []),
    ...(ep.panel ?? []).map(p => ({ who: p.name, sub: p.role, src: ep.src, bias: p.bias, horizon: p.horizon, why: p.view, ep })),
  ])
}

// 최근 days일 안의 뷰를 사람별로 묶는다. 진행자 셋이 먼저, 나머지는 최근에 나온 순. 한 사람 안은 최신순
export function biasBoard(eps: Episode[], days = 14, now = Date.now()) {
  const hosts = Object.values(WHO).map(w => w.who)
  const by = new Map<string, View[]>()
  for (const v of views(eps)) if ((now - +new Date(v.ep.date + 'T00:00:00')) / 864e5 < days) by.set(v.who, [...(by.get(v.who) ?? []), v])
  const rank = (who: string) => hosts.includes(who) ? hosts.indexOf(who) : hosts.length
  return [...by].sort((a, b) => rank(a[0]) - rank(b[0]) || b[1][0].ep.date.localeCompare(a[1][0].ep.date))
}

export const dateLabel =(date: string) => new Date(date + 'T00:00:00').toLocaleDateString('ko-KR', { month: 'long', day: 'numeric', weekday: 'short' })
