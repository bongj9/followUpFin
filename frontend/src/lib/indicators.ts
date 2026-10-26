import type { Bar, MarketFlow } from '@/api'
import { DOWN, fmt, KST, UP } from './format'

// 지표 계산. 결과는 봉과 같은 길이의 배열, 계산이 안 되는 자리는 null
export type Series = (number | null)[]
export const closes = (bars: Bar[]) => bars.map(b => b.close)

export function sma(vals: number[], n: number): Series {
  let sum = 0
  return vals.map((v, i) => { sum += v - (i >= n ? vals[i - n] : 0); return i >= n - 1 ? sum / n : null })
}
export function ema(vals: Series, n: number): Series {
  const k = 2 / (n + 1)
  let e: number | null = null, seen = 0
  return vals.map(v => { if (v == null) return null; e = e == null ? v : v * k + e * (1 - k); return ++seen >= n ? e : null })
}
export function bollinger(vals: number[], n: number, side: 1 | -1): Series {
  return sma(vals, n).map((m, i) => {
    if (m == null) return null
    const sd = Math.sqrt(vals.slice(i - n + 1, i + 1).reduce((s, v) => s + (v - m) ** 2, 0) / n)
    return m + side * 2 * sd
  })
}
export function rsi(vals: number[], n = 14): Series {
  let gain = 0, loss = 0
  return vals.map((v, i) => {
    if (i === 0) return null
    const d = v - vals[i - 1]
    const up = Math.max(d, 0), down = Math.max(-d, 0)
    if (i <= n) { gain += up / n; loss += down / n } else { gain = (gain * (n - 1) + up) / n; loss = (loss * (n - 1) + down) / n }
    return i < n ? null : loss === 0 ? 100 : 100 - 100 / (1 + gain / loss)
  })
}
export function macd(vals: number[]): Series {
  const fast = ema(vals, 12), slow = ema(vals, 26)
  return fast.map((f, i) => f == null || slow[i] == null ? null : f - slow[i]!)
}
export const macdSignal = (vals: number[]) => ema(macd(vals), 9)
export const macdHist = (vals: number[]): Series => {
  const m = macd(vals), s = macdSignal(vals)
  return m.map((v, i) => v == null || s[i] == null ? null : v - s[i]!)
}
// n개 봉의 (최고가 + 최저가) / 2. 일목균형표용
export const midRange = (bars: Bar[], n: number): Series => bars.map((_, i) => {
  if (i < n - 1) return null
  const w = bars.slice(i - n + 1, i + 1)
  return (Math.max(...w.map(b => b.high)) + Math.min(...w.map(b => b.low))) / 2
})
// k > 0: k봉 앞으로 밀기 (선행), k < 0: 뒤로 당기기 (후행)
export const shift = (vals: Series, k: number): Series => vals.map((_, i) => vals[i - k] ?? null)

// 일목 선행스팬: 26봉 앞에 그린다. 길이 = 봉 수 + 26 (뒤 26개가 아직 안 온 미래 자리)
const lead = (vals: Series): Series => [...Array<null>(26).fill(null), ...vals]
export const spanA = (b: Bar[]) => { const t = midRange(b, 9), k = midRange(b, 26); return lead(t.map((v, i) => v == null || k[i] == null ? null : (v + k[i]!) / 2)) }
export const spanB = (b: Bar[]) => lead(midRange(b, 52))

export type Toned = { value: number; color: string }
const tone = (v: number, up: boolean): Toned => ({ value: v, color: up ? 'rgba(220,38,38,0.4)' : 'rgba(37,99,235,0.4)' })

// 보조지표. pane: 아래 별도 칸. lines의 label은 범례에 색깔 숫자로 표시. hist: 막대(히스토그램) 시리즈
// ahead: calc가 마지막 봉 뒤로 ahead봉만큼 더 낸다 (일목 선행스팬은 26봉 앞까지 그린다)
export interface IndicatorLine { label?: string; color?: string; hist?: boolean; ahead?: number; options?: object; calc: (b: Bar[]) => (number | Toned | null)[] }
export interface IndicatorDef { key: string; name: string; pane?: boolean; lines: IndicatorLine[] }
export const INDICATORS: IndicatorDef[] = [
  { key: 'ma', name: '이동평균선', lines: ([[5, '#16a34a'], [20, '#dc2626'], [60, '#f59e0b'], [120, '#8b5cf6']] as const)
      .map(([n, color]) => ({ label: String(n), color, calc: b => sma(closes(b), n) })) },
  { key: 'bb', name: '볼린저밴드', lines: [
    { label: '20', color: '#0ea5e9', calc: b => sma(closes(b), 20) },
    { label: '2', color: '#0ea5e9', calc: b => bollinger(closes(b), 20, 1) },
    { color: '#0ea5e9', calc: b => bollinger(closes(b), 20, -1) }] },
  { key: 'ichimoku', name: '일목균형표', lines: [
    { label: '0', color: '#f59e0b', calc: b => midRange(b, 9) },   // 전환선
    { label: '1', color: '#16a34a', calc: b => midRange(b, 26) },  // 기준선
    { label: '2', color: '#9ca3af', calc: b => shift(closes(b), -26) }, // 후행스팬
    { label: '3', color: '#dc2626', ahead: 26, calc: b => spanA(b) }, // 선행스팬1
    { label: '4', color: '#2563eb', ahead: 26, calc: b => spanB(b) }] }, // 선행스팬2
  { key: 'volume', name: '거래량', pane: true, lines: [
    { hist: true, options: { priceFormat: { type: 'volume' } }, calc: b => b.map(x => tone(x.volume, x.close >= x.open)) },
    { label: '20', color: '#f59e0b', calc: b => sma(b.map(x => x.volume), 20) }] },
  { key: 'rsi', name: 'RSI', pane: true, lines: [{ label: '14', color: '#7c3aed', calc: b => rsi(closes(b)) }] },
  { key: 'macd', name: 'MACD', pane: true, lines: [
    { hist: true, calc: b => macdHist(closes(b)).map(v => v == null ? null : tone(v, v >= 0)) },
    { label: '12', color: '#2563eb', calc: b => macd(closes(b)) },
    { label: '26 9', color: '#f97316', calc: b => macdSignal(closes(b)) }] },
]

// 판단 재료: 지표에서 눈여겨볼 순간을 찾아 봉 위에 표시한다. 사라·팔아라가 아니라 '이런 일이 있었다'만 알린다
// 켜 둔 지표만 본다. 결과는 { key(지표), time, position, shape, color, text(짧은 이름), note(설명) }
export type Shape = 'arrowUp' | 'arrowDown' | 'circle' | 'square'
export interface Signal { key: string; time: number; position: 'aboveBar' | 'belowBar'; shape: Shape; color: string; text: string; note: string }
export const ICON: Record<Shape, string> = { arrowUp: '▲', arrowDown: '▼', circle: '●', square: '■' }
const PURPLE = '#8b5cf6', AMBER = '#f59e0b'

export function signals(bars: Bar[], on: string[]): Signal[] {
  const c = closes(bars), out: Signal[] = []
  const mark = (key: string, i: number, position: Signal['position'], shape: Shape, color: string, text: string, note: string) =>
    out.push({ key, time: bars[i].time, position, shape, color, text, note })
  const golden = (key: string, i: number, text: string, note: string) => mark(key, i, 'belowBar', 'arrowUp', UP, text, note)
  const dead = (key: string, i: number, text: string, note: string) => mark(key, i, 'aboveBar', 'arrowDown', DOWN, text, note)
  // a가 b를 위로 뚫으면 양수, 아래로 뚫으면 음수
  const crossed = (a: Series, b: Series, i: number) => a[i - 1] != null && b[i - 1] != null && a[i] != null && b[i] != null
    ? Math.sign(a[i]! - b[i]!) - Math.sign(a[i - 1]! - b[i - 1]!) : 0
  const level = (vals: Series, n: number) => vals.map(v => v == null ? null : n)
  const each = (f: (i: number) => void) => { for (let i = 1; i < bars.length; i++) f(i) }
  if (on.includes('ma')) {
    for (const [f, l] of [[5, 20], [20, 60]]) {
      const fast = sma(c, f), slow = sma(c, l)
      each(i => {
        const d = crossed(fast, slow, i)
        if (d > 0) golden('ma', i, `골든크로스 ${f}·${l}`, `${f}선이 ${l}선을 위로 뚫음: 짧게 본 흐름이 길게 본 흐름보다 강해졌다. 옆으로 기는 장에선 자주 속는다`)
        if (d < 0) dead('ma', i, `데드크로스 ${f}·${l}`, `${f}선이 ${l}선을 아래로 뚫음: 짧게 본 흐름이 약해졌다. 이미 많이 내린 뒤 늦게 나오기도 한다`)
      })
    }
  }
  if (on.includes('bb')) {
    const mid = sma(c, 20), up = bollinger(c, 20, 1), lo = bollinger(c, 20, -1)
    const width = mid.map((m, i) => m == null ? null : (up[i]! - lo[i]!) / m)
    let squeezed = false
    each(i => {
      if (crossed(c, up, i) > 0) mark('bb', i, 'aboveBar', 'circle', UP, '상단 돌파', '종가가 볼린저 상단 위로: 평소보다 크게 오름. 과열로 보는 시각과, 처음 뚫은 거라 오르는 힘이 세다(더 간다)는 시각이 갈린다. 거래량이 같이 늘었는지 볼 것')
      if (crossed(c, lo, i) < 0) mark('bb', i, 'belowBar', 'circle', DOWN, '하단 이탈', '종가가 볼린저 하단 아래로: 평소보다 크게 내림. 반등 자리로 보는 시각과, 처음 닿은 거라 내리는 힘이 세다(더 간다)는 시각이 갈린다. 다음 봉들이 밴드 안으로 돌아오는지가 갈림길')
      // 스퀴즈: 밴드 폭이 최근 120봉 중 가장 좁을 때 (처음 좁아진 봉에만 표시)
      const recent = width.slice(Math.max(0, i - 119), i + 1).filter((w): w is number => w != null)
      const now = recent.length >= 120 && width[i]! <= Math.min(...recent)
      if (now && !squeezed) mark('bb', i, 'aboveBar', 'square', AMBER, '스퀴즈', '밴드 폭이 최근 120봉 중 가장 좁음: 움직임이 잦아든 상태. 이후 크게 움직이는 일이 많지만 방향은 모른다')
      squeezed = now
    })
  }
  if (on.includes('ichimoku')) {
    const t = midRange(bars, 9), k = midRange(bars, 26)
    each(i => {
      const d = crossed(t, k, i)
      if (d > 0) golden('ichimoku', i, '호전', '전환선이 기준선을 위로 뚫음 (호전): 단기 흐름이 좋아졌다. 구름 위에서 나오면 더 강하게 본다')
      if (d < 0) dead('ichimoku', i, '역전', '전환선이 기준선을 아래로 뚫음 (역전): 단기 흐름이 나빠졌다. 구름 아래에서 나오면 더 약하게 본다')
    })
  }
  if (on.includes('volume')) {
    const vol = bars.map(b => b.volume), avg = sma(vol, 20)
    const surge = (i: number) => avg[i - 1] != null && vol[i] >= 2 * avg[i - 1]!
    each(i => {
      if (surge(i) && !surge(i - 1)) mark('volume', i, 'belowBar', 'square', PURPLE, '거래량 급증',
        `거래량이 20봉 평균의 ${(vol[i] / avg[i - 1]!).toFixed(1)}배: 관심이 크게 몰림. 이 봉이 오른 봉인지 내린 봉인지가 중요하다`)
    })
  }
  if (on.includes('rsi')) {
    const r = rsi(c)
    each(i => {
      if (crossed(r, level(r, 70), i) > 0) mark('rsi', i, 'aboveBar', 'circle', UP, 'RSI 70 진입', 'RSI가 70 위로 (과매수): 최근 오른 힘이 강함. 강세장에선 여기 오래 머물기도 한다')
      if (crossed(r, level(r, 30), i) < 0) mark('rsi', i, 'belowBar', 'circle', DOWN, 'RSI 30 진입', 'RSI가 30 아래로 (과매도): 최근 내린 힘이 강함. 하락장에선 여기 오래 머물기도 한다')
    })
  }
  if (on.includes('macd')) {
    const m = macd(c), sig = macdSignal(c)
    each(i => {
      const d = crossed(m, sig, i)
      if (d > 0) golden('macd', i, 'MACD 골든', 'MACD가 시그널을 위로 뚫음: 오르는 힘이 커지기 시작. 0선 아래에서 나오면 반등, 위에서 나오면 추세가 이어지는 쪽으로 본다')
      if (d < 0) dead('macd', i, 'MACD 데드', 'MACD가 시그널을 아래로 뚫음: 오르는 힘이 줄기 시작')
    })
  }
  return out.sort((a, b) => a.time - b.time)
}

// 지금 상태: 켜 둔 지표마다 [이름, 상태, 보통 이렇게 읽음 · 주의할 점]
export function status(bars: Bar[], on: string[]): [string, string, string][] {
  const c = closes(bars), i = c.length - 1, last = c[i], rows: [string, string, string][] = []
  const pct = (a: number, b: number) => (a / b - 1) * 100
  const signed = (n: number) => (n >= 0 ? '+' : '') + n.toFixed(1)
  if (on.includes('ma')) {
    const [m5, m20, m60, m120] = [5, 20, 60, 120].map(n => sma(c, n)[i])
    if (m120 != null && m5 != null && m20 != null && m60 != null) {
      const order = m5 > m20 && m20 > m60 && m60 > m120 ? ['정배열 (5>20>60>120)', '짧게 보나 길게 보나 오르는 흐름. 추세가 이어지는 동안은 눌림(20선 근처)을 기다리는 사람이 많다']
        : m5 < m20 && m20 < m60 && m60 < m120 ? ['역배열 (5<20<60<120)', '짧게 보나 길게 보나 내리는 흐름. 반등이 나와도 위의 이동평균선에서 막히기 쉽다']
        : ['섞여 있음', '추세가 뚜렷하지 않거나 바뀌는 중. 크로스 신호가 자주 속는 구간']
      rows.push(['이동평균선', order[0] + ` · 현재가는 20선 대비 ${signed(pct(last, m20))}%`, order[1]])
    }
  }
  if (on.includes('bb')) {
    const mid = sma(c, 20), up = bollinger(c, 20, 1), lo = bollinger(c, 20, -1)
    if (mid[i] != null) {
      const z = 2 * (last - mid[i]!) / (up[i]! - mid[i]!)
      const width = mid.map((m, k) => m == null ? null : (up[k]! - lo[k]!) / m).slice(-120).filter((w): w is number => w != null)
      const rank = width.filter(w => w < width.at(-1)!).length / width.length
      const where = z >= 2 ? ['상단 위', '평소보다 많이 오른 자리(과열권). 단, 강한 상승장에선 상단을 타고 계속 오르기도 한다(밴드 워킹)']
        : z <= -2 ? ['하단 아래', '평소보다 많이 내린 자리. 반등을 기대하는 시각도 있지만, 밴드 밖에서 계속 내려가는 중이면 바닥 판단은 이르다']
        : [z >= 0 ? '중심선 위' : '중심선 아래', '밴드 안: 평소 움직임 범위']
      const squeeze = width.length >= 60 && rank <= 0.1 ? ' · 밴드 폭이 최근 중 가장 좁은 편(스퀴즈)' : ''
      rows.push(['볼린저밴드', `${where[0]} (${signed(z)}σ)${squeeze}`, where[1]])
    }
  }
  if (on.includes('ichimoku') && bars.length > 52 + 26) {
    const a = spanA(bars)[i]!, b = spanB(bars)[i]! // 지금 봉 자리의 구름 (26봉 전에 그려 둔 것)
    const where = last > Math.max(a, b) ? ['구름 위', '상승 추세로 본다. 구름이 아래에서 받쳐 주는 자리']
      : last < Math.min(a, b) ? ['구름 아래', '하락 추세로 본다. 구름이 위에서 누르는 자리']
      : ['구름 안', '방향을 찾는 중. 구름을 어느 쪽으로 빠져나가는지 본다']
    rows.push(['일목균형표', where[0], where[1]])
  }
  if (on.includes('volume')) {
    const avg = sma(bars.map(b => b.volume), 20)[i]
    if (avg) {
      const r = bars[i].volume / avg
      rows.push(['거래량', `20봉 평균의 ${r.toFixed(1)}배`, r >= 2 ? '평소보다 많이 거래됨. 가격 움직임에 힘이 실렸다는 뜻 (진행 중인 봉은 아직 덜 쌓였을 수 있음)'
        : r <= 0.5 ? '거래가 적음. 이때 나온 돌파·이탈은 믿기 어렵다' : '평소 수준'])
    }
  }
  if (on.includes('rsi')) {
    const r = rsi(c)[i]
    if (r != null) rows.push(['RSI', r.toFixed(0), r >= 70 ? '70 이상 과매수: 최근 오른 힘이 강함. 강세장에선 70 위에 오래 머물기도 한다'
      : r <= 30 ? '30 이하 과매도: 최근 내린 힘이 강함. 하락장에선 30 아래에 오래 머물기도 한다' : '30~70 중립'])
  }
  if (on.includes('macd')) {
    const m = macd(c)[i], sig = macdSignal(c)[i]
    if (m != null && sig != null) rows.push(['MACD', `${m >= sig ? '시그널 위' : '시그널 아래'} · 0선 ${m >= 0 ? '위' : '아래'}`,
      (m >= sig ? '오르는 힘이 커지는 중' : '오르는 힘이 줄어드는 중') + (m >= 0 ? ', 큰 흐름은 상승 쪽' : ', 큰 흐름은 하락 쪽') + '. 이동평균 기반이라 늦게 반응한다'])
  }
  return rows
}

// 코스피(또는 종목) 일봉·외국인 누적으로 지금 상태를 고르고, 화면에 보여줄 숫자도 같이 만든다 (알상무 뷰)
export interface MarketState { st: Record<string, string>; fact: Record<string, string>; price: number; date: string }
export function marketState(bars: Bar[], flows: MarketFlow[] | null): MarketState {
  const c = closes(bars), i = c.length - 1, b = bars[i], prev = bars[i - 1]
  const [m20, m60, m120] = [20, 60, 120].map(n => sma(c, n)) as [number[], number[], number[]]
  const lo = bollinger(c, 20, -1) as number[], up = bollinger(c, 20, 1) as number[]
  let sum = 0
  const cum = (flows ?? []).map(f => sum += f.foreigner), avg = sma(cum, 20), n = cum.length - 1
  const since = (test: (k: number) => boolean) => { let d = 1; while (d < i && test(i - d) === test(i)) d++; return d }
  const pct = (a: number, base: number) => (a / base - 1) * 100
  const range = b.high - b.low
  const st: Record<string, string> = {}, fact: Record<string, string> = {}
  if (flows) {
    st.flow = cum[n] > avg[n]! ? 'above' : 'below'
    fact.flow = `외국인 누적 ${(cum[n] / 1e12).toFixed(0)}조, 4주 평균선 ${st.flow === 'above' ? '위' : '아래'} · 최근 5일 ${(flows.slice(-5).reduce((a, f) => a + f.foreigner, 0) / 1e12).toFixed(1)}조`
  }
  const under = [m20, m60, m120].filter(m => c[i] < m[i]).length
  st.ma = under === 0 ? 'aboveAll' : under === 3 ? 'belowAll' : c[i] < m60[i] ? 'below60' : 'below20'
  fact.ma = `${fmt(c[i])} · 20일선 ${fmt(m20[i])}(${pct(c[i], m20[i]).toFixed(1)}%) · 60일선 ${fmt(m60[i])} · 120일선 ${fmt(m120[i])}`
  st.cross = m20[i] > m60[i] ? 'golden' : 'dead'
  fact.cross = `${st.cross === 'golden' ? '20일선 > 60일선 (골든크로스)' : '20일선 < 60일선 (데드크로스)'} ${since(k => m20[k] > m60[k])}일째`
  st.candle = c[i] - b.low <= range * 0.1 ? 'atLow' : b.high - c[i] <= range * 0.1 ? 'atHigh'
    : b.high <= prev.high && b.low >= prev.low ? 'inside' : 'normal'
  fact.candle = ({ atLow: '종가 = 저가', atHigh: '종가 = 고가', inside: '전날 봉 안에 갇힌 봉', normal: '보통 봉' } as Record<string, string>)[st.candle]
    + ` (시가 ${fmt(b.open)} · 고가 ${fmt(b.high)} · 저가 ${fmt(b.low)} · 종가 ${fmt(c[i])})`
  st.bb = c[i] < lo[i] && lo[i] < lo[i - 1] ? 'outsideLow' : c[i] > up[i] ? 'outsideHigh' : b.low <= lo[i] || c[i] < lo[i] ? 'touchLow' : 'inside'
  fact.bb = `하단 ${fmt(lo[i])} · 상단 ${fmt(up[i])} · 종가는 하단 대비 ${pct(c[i], lo[i]).toFixed(1)}%`
  return { st, fact, price: c[i], date: new Date((b.time - KST) * 1000).toLocaleDateString('ko-KR') }
}
