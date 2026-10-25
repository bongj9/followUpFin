// 아침 리포트 재료: 보유 종목과 시장 지수를 일봉·주봉 지표로 판정한 사실만 뽑는다. 판단(매수·매도·비중)은 리포트를 쓰는 쪽이 한다
// 화면과 같은 판정 함수(src/lib/indicators)를 써서 차트에서 보는 것과 숫자가 같다
// 실행: npx tsx scripts/report-facts.ts [http://localhost:8080] > facts.json
import type { Bar, Indicator, MarketFlow, Quote } from '../src/api'
import { marketState, signals, status } from '../src/lib/indicators'

const base = process.argv[2] ?? 'http://localhost:8080'
const ALL = ['ma', 'bb', 'ichimoku', 'volume', 'rsi', 'macd']
const MARKETS = [['KOSPI', '코스피'], ['KOSDAQ', '코스닥'], ['QQQ', '나스닥100 (QQQ)'], ['SPY', 'S&P500 (SPY)'], ['SOXX', '반도체 (SOXX)']]

async function get<T>(path: string, fallback: T): Promise<T> {
  const res = await fetch(base + path).catch(() => null)
  return res?.ok ? res.json() as Promise<T> : fallback
}
const bars = async (code: string, interval: string) =>
  (await get<Bar[]>(`/api/candles?symbol=${encodeURIComponent(code)}&interval=${interval}`, []))
    .map(b => ({ time: b.time, open: +b.open, high: +b.high, low: +b.low, close: +b.close, volume: +b.volume }))
const day = (time: number) => new Date(time * 1000).toISOString().slice(0, 10) // time은 이미 KST로 옮긴 초

// 지금 상태(이평선·볼린저·일목·거래량·RSI·MACD)와 최근 5봉 안에 나온 신호(크로스 등)
function chart(b: Bar[], daily: boolean) {
  if (b.length < 30) return null // 상장 직후처럼 봉이 너무 적으면 판정이 안 된다. 120선 등 모자란 항목은 status가 알아서 뺀다
  const since = b[b.length - 5].time
  return {
    close: b.at(-1)!.close, date: day(b.at(-1)!.time),
    ...daily && { rangeDays: b.length, rangeHigh: Math.max(...b.map(x => x.high)), rangeLow: Math.min(...b.map(x => x.low)) }, // 받은 일봉 전체(약 200일) 고저
    status: status(b, ALL).map(([name, now, read]) => ({ name, now, read })),
    recent: signals(b, ALL).filter(s => s.time >= since).map(s => ({ date: day(s.time), signal: s.note })),
  }
}

const [holdings, quotes, indicators] = await Promise.all([
  get<{ symbol: string; name: string; quantity: number; averagePrice: number; currency: string }[]>('/api/holdings', []),
  get<Quote[]>('/api/quotes', []),
  get<Indicator[]>('/api/indicators', []),
])
const fx = indicators.find(i => i.label === '원/달러')?.last ?? null
const quote = new Map(quotes.map(q => [q.stockCode, q]))

// ponytail: 토스가 한꺼번에 부르면 일부를 떨궈서 한 종목씩 차례로 부른다 (20여 종목이라 몇 초)
const market = []
for (const [code, name] of MARKETS) {
  const d = await bars(code, '1d'), w = await bars(code, '1w')
  const flows = code === 'KOSPI' || code === 'KOSDAQ' ? await get<MarketFlow[]>('/api/market-flows?symbol=' + code, []) : []
  // 코스피·코스닥은 알상무 규칙 순서(외국인 수급 → 이평선 → 크로스 → 볼린저)로 본 상태도 같이
  const rules = d.length >= 121 && flows.length >= 21 ? marketState(d, flows).fact : undefined
  market.push({ code, name, daily: chart(d, true), weekly: chart(w, false), rules })
}

const rows = []
for (const h of holdings) {
  const q = quote.get(h.symbol)
  const last = q?.lastPrice ?? null
  const krw = last == null ? null : h.quantity * last * (h.currency === 'USD' ? fx ?? NaN : 1)
  const d = await bars(h.symbol, '1d'), w = await bars(h.symbol, '1w')
  rows.push({
    code: h.symbol, name: h.name, currency: h.currency, quantity: h.quantity, averagePrice: h.averagePrice,
    last, changeRate: q?.changeRate ?? null, pnlPct: last == null ? null : +((last / h.averagePrice - 1) * 100).toFixed(1),
    valueKrw: krw == null || Number.isNaN(krw) ? null : Math.round(krw), daily: chart(d, true), weekly: chart(w, false),
  })
}
const total = rows.reduce((s, r) => s + (r.valueKrw ?? 0), 0)
const holdingsOut = rows.map(r => ({ ...r, weightPct: total && r.valueKrw != null ? +(r.valueKrw / total * 100).toFixed(1) : null }))
  .sort((a, b) => (b.weightPct ?? 0) - (a.weightPct ?? 0))

console.log(JSON.stringify({ asOf: new Date().toISOString(), fx, totalKrw: total, market, holdings: holdingsOut }, null, 2))
