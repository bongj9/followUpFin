import { useEffect, useLayoutEffect, useRef, useState, type ComponentProps } from 'react'
import {
  BaselineSeries, CandlestickSeries, createChart, createSeriesMarkers, CrosshairMode, HistogramSeries, LineSeries, LineStyle, PriceScaleMode,
  type IChartApi, type ISeriesApi, type MouseEventParams, type SeriesType, type Time, type UTCTimestamp,
} from 'lightweight-charts'
import { getBars, onStream, type Bar, type Quote, type Stock } from '@/api'
import { ChartGuide, signalDate } from '@/components/ChartGuide'
import { cn, DOWN, fmt, KST, UP } from '@/lib/format'
import { ICON, INDICATORS, signals as findSignals, status, type IndicatorDef, type IndicatorLine, type Signal, type Toned } from '@/lib/indicators'

// 차트 (토스 시세, TradingView 오픈소스 Lightweight Charts). 토스증권 차트처럼 툴바·보조지표·그리기·자·종목비교
// 상승 빨강·하락 파랑. 차트 라이브러리는 UTC로 그리므로 시각은 한국 시간으로 옮겨서 넣는다 (캔들은 서버가 이미 옮겨서 줌)
const INTRADAY = [['tick', '틱'], ['1m', '1분'], ['5m', '5분'], ['15m', '15분'], ['30m', '30분'], ['1h', '1시간']] as const
const PERIODS = [['1d', '일'], ['1w', '주'], ['1M', '월'], ['1y', '년']] as const
const BUCKET: Record<string, number> = { '1m': 60, '5m': 300, '15m': 900, '30m': 1800, '1h': 3600 }
const COMPARE_COLORS = ['#f97316', '#10b981', '#a855f7', '#0ea5e9']
const MAIN_HEIGHT = 420, PANE_HEIGHT = 110
const ts = (n: number) => n as UTCTimestamp

// 차트 설정은 브라우저에 기억 (없거나 막혀 있으면 기본값)
interface Prefs { interval: string; intraday: string; type: 'candle' | 'line'; on: string[]; hidden: string[] }
function loadPrefs(): Prefs {
  const prefs: Prefs = { interval: '1m', intraday: '1m', type: 'candle', on: ['ma', 'volume'], hidden: [] }
  try { Object.assign(prefs, JSON.parse(localStorage.getItem('chartPrefs')!) ?? {}) } catch { /* 기본값 */ }
  return prefs
}

type Pt = { time: number; price: number }
const drawings = new Map<string, [Pt, Pt][]>() // '종목|주기' → [[점1, 점2], ...] (새로고침하면 사라짐)

interface Plot { ind: IndicatorDef; line: IndicatorLine; series: ISeriesApi<SeriesType> }
interface Compare { code: string; series: ISeriesApi<'Line'>; color: string; bars: Bar[] }
interface View {
  code: string; interval: string; type: Prefs['type']; chart: IChartApi; main: ISeriesApi<SeriesType>
  plots: Plot[]; inds: IndicatorDef[]; bars: Bar[]; compares: Compare[]; hover: boolean; times: string[]; signals: Signal[]
}

const point = (time: number, v: number | Toned) => typeof v === 'object' ? { time: ts(time), ...v } : { time: ts(time), value: v }
const toData = (bars: Bar[], vals: (number | Toned | null)[]) => vals.flatMap((val, i) => val == null ? [] : [point(bars[i].time, val)])
const mainData = (type: Prefs['type'], b: Bar) => type === 'candle' ? { ...b, time: ts(b.time) } : { time: ts(b.time), value: b.close }

// 체결 한 건을 봉 배열에 반영하고 바뀐 봉을 돌려준다 (늦게 온 체결이면 null)
function applyTrade(bars: Bar[], q: Quote, interval: string): Bar | null {
  const last = bars.at(-1)
  if (!last) return null
  const price = +q.lastPrice
  const t = Math.floor(Date.parse(q.timestamp) / 1000) + KST
  const size = BUCKET[interval]
  // 일·주·월·년봉은 토스 거래일 경계가 자정이 아니라 새 봉을 열지 않고 마지막 봉만 갱신
  const time = size ? t - t % size : last.time
  if (time < last.time) return null
  const qty = +(q.volume ?? 0)
  const bar = time === last.time
    ? { ...last, high: Math.max(last.high, price), low: Math.min(last.low, price), close: price, volume: last.volume + qty }
    : { time, open: price, high: price, low: price, close: price, volume: qty }
  if (time === last.time) bars[bars.length - 1] = bar; else bars.push(bar)
  return bar
}

const toolBtn = 'border-0 bg-transparent rounded-md px-[9px] py-[5px] text-[13px] cursor-pointer hover:bg-background'
function Btn({ active, className, ...props }: ComponentProps<'button'> & { active?: boolean }) {
  return <button className={cn(toolBtn, active && 'bg-muted font-bold', className)} {...props} />
}

export function StockChart({ code, stocks }: { code: string; stocks: Stock[] }) {
  const [prefs, setPrefs] = useState(loadPrefs)
  const [menu, setMenu] = useState<'ind' | 'compare' | null>(null)
  const [tool, setTool] = useState<'draw' | 'measure' | null>(null)
  const [compares, setCompares] = useState<string[]>([]) // 비교 중인 종목 코드
  const [reload, setReload] = useState(0)
  const [view, setView] = useState<View | null>(null)
  const [legendBar, setLegendBar] = useState<Bar | null>(null)
  const [statusRows, setStatusRows] = useState<[string, string, string][]>([])
  const [guideKey, setGuideKey] = useState<string | null>(null) // 펼친 지표
  const [guideFocus, setGuideFocus] = useState<Signal | null>(null) // 고른 신호
  const [tip, setTip] = useState<{ marks: Signal[]; date: string; x: number; y: number } | null>(null)
  const [measure, setMeasure] = useState<{ text: string; up: boolean; left: number; top: number } | null>(null)
  const viewRef = useRef<View | null>(null)
  const toolRef = useRef(tool); toolRef.current = tool
  const prefsRef = useRef(prefs); prefsRef.current = prefs
  const pending = useRef<Pt | null>(null) // 그리기·자의 첫 번째 클릭 지점
  const measureSeries = useRef<ISeriesApi<'Line'> | null>(null)
  const boxRef = useRef<HTMLDivElement>(null), chartRef = useRef<HTMLDivElement>(null)
  const tipRef = useRef<HTMLDivElement>(null), toolbarRef = useRef<HTMLDivElement>(null)

  useEffect(() => { try { localStorage.setItem('chartPrefs', JSON.stringify(prefs)) } catch { /* 저장 못 해도 동작 */ } }, [prefs])

  const tick = prefs.interval === 'tick'
  // 비교 중에는 % 눈금이라 가격 기준 지표(이동평균 등)는 빼고 아래 칸 지표만 남긴다
  const inds = tick ? [] : INDICATORS.filter(i => prefs.on.includes(i.key) && (i.pane || !compares.length))
  const panes = inds.filter(i => i.pane)
  const drawKey = code + '|' + prefs.interval

  // 표시가 있는 봉에 커서를 대면 그 옆에 말풍선
  function showTip(v: View, p: { time?: Time; point?: { x: number; y: number } }) {
    const marks = p.time == null || !p.point ? [] : v.signals.filter(s => s.time === p.time)
    if (!marks.length || !p.point) { setTip(null); return }
    const bar = v.bars.find(b => b.time === p.time)!
    const y = v.main.priceToCoordinate(marks[0].position === 'aboveBar' ? bar.high : bar.low) ?? p.point.y
    setTip({ marks, date: signalDate(p.time as number, !!BUCKET[v.interval], 'numeric'), x: p.point.x, y })
  }
  // 오른쪽에 자리가 없으면 왼쪽 (말풍선 크기를 재야 해서 그린 뒤에 자리를 잡는다)
  useLayoutEffect(() => {
    const box = tipRef.current
    if (!tip || !box || !chartRef.current) return
    const right = tip.x + 20, fits = right + box.offsetWidth < chartRef.current.clientWidth - 70 // 70: 가격 눈금
    box.style.left = (fits ? right : Math.max(4, tip.x - box.offsetWidth - 20)) + 'px'
    box.style.top = Math.max(4, Math.min(tip.y - box.offsetHeight / 2, MAIN_HEIGHT - box.offsetHeight - 4)) + 'px'
  }, [tip])

  function addLine(v: View, [a, b]: [Pt, Pt], color: string, lineStyle: LineStyle) {
    const s = v.chart.addSeries(LineSeries, { color, lineWidth: 2, lineStyle, priceLineVisible: false, lastValueVisible: false, crosshairMarkerVisible: false })
    s.setData([{ time: ts(a.time), value: a.price }, { time: ts(b.time), value: b.price }])
    return s
  }
  function clearMeasure() {
    if (measureSeries.current && viewRef.current) viewRef.current.chart.removeSeries(measureSeries.current)
    measureSeries.current = null
    setMeasure(null)
  }

  // 그리기·자: 두 번 클릭. 그리기는 추세선을 남기고, 자는 두 점 사이 등락률과 봉 개수를 보여준다
  function onChartClick(v: View, p: MouseEventParams) {
    const t = toolRef.current
    if (!t || !p.point || p.time == null) return
    const pt = { time: p.time as number, price: v.main.coordinateToPrice(p.point.y) as number }
    if (!pending.current) { pending.current = pt; return }
    const [a, b] = [pending.current, pt].sort((x, y) => x.time - y.time)
    pending.current = null
    if (a.time === b.time) return
    const key = v.code + '|' + v.interval
    if (t === 'draw') {
      drawings.set(key, [...(drawings.get(key) ?? []), [a, b]])
      addLine(v, [a, b], '#111827', LineStyle.Solid)
      return
    }
    clearMeasure()
    const change = (b.price / a.price - 1) * 100
    const count = v.bars.filter(x => x.time > a.time && x.time <= b.time).length
    measureSeries.current = addLine(v, [a, b], change >= 0 ? UP : DOWN, LineStyle.Dashed)
    setMeasure({ text: (change >= 0 ? '+' : '') + change.toFixed(2) + '% (' + (change >= 0 ? '+' : '') + fmt(b.price - a.price) + ') · ' + count + '봉',
      up: change >= 0, left: p.point.x + 12, top: p.point.y })
  }

  // 차트를 새로 만든다: 종목·주기·종류·지표·비교가 바뀌거나 '지우기'를 눌렀을 때
  useEffect(() => {
    const interval = prefs.interval, type = prefs.type
    const times: string[] = []
    const tickLabel = (i: Time) => times[(i as number) - 1] ? new Date(times[(i as number) - 1]).toLocaleTimeString('ko-KR') : ''
    const chart = createChart(chartRef.current!, {
      autoSize: true,
      layout: { fontSize: 11, panes: { separatorColor: '#e5e7eb' } },
      grid: { vertLines: { color: '#f1f3f5' }, horzLines: { color: '#f1f3f5' } },
      crosshair: { mode: CrosshairMode.Normal },
      timeScale: tick ? { tickMarkFormatter: tickLabel } : { timeVisible: !!BUCKET[interval] },
      localization: tick ? { timeFormatter: tickLabel } : {},
    })
    let stale = false
    const cleanup = () => {
      stale = true
      measureSeries.current = null
      setMeasure(null); setTip(null); setLegendBar(null); setStatusRows([]); setView(null)
      viewRef.current = null
      chart.remove()
    }
    if (tick) {
      const main = chart.addSeries(BaselineSeries, {
        topLineColor: UP, topFillColor1: 'rgba(220,38,38,0.2)', topFillColor2: 'rgba(220,38,38,0.02)',
        bottomLineColor: DOWN, bottomFillColor1: 'rgba(37,99,235,0.02)', bottomFillColor2: 'rgba(37,99,235,0.2)' })
      // 틱은 저장하지 않는다. 페이지를 연 뒤 들어오는 실시간 체결만 그린다
      const v: View = { code, interval, type, chart, main, times, plots: [], inds: [], bars: [], compares: [], hover: false, signals: [] }
      setView(viewRef.current = v)
      return cleanup
    }
    const main: ISeriesApi<SeriesType> = type === 'candle'
      ? chart.addSeries(CandlestickSeries, { upColor: UP, downColor: DOWN, borderVisible: false, wickUpColor: UP, wickDownColor: DOWN })
      : chart.addSeries(LineSeries, { color: '#111827', lineWidth: 2 })
    const hidden = prefsRef.current.hidden
    const plots: Plot[] = inds.flatMap(ind => {
      const paneIndex = ind.pane ? panes.indexOf(ind) + 1 : 0
      return ind.lines.map(line => ({ ind, line, series: chart.addSeries(line.hist ? HistogramSeries : LineSeries, {
        color: line.color, lineWidth: 1, ...line.options, priceLineVisible: false, lastValueVisible: !!ind.pane,
        crosshairMarkerVisible: false, visible: !hidden.includes(ind.key) }, paneIndex) as ISeriesApi<SeriesType> }))
    })
    // 높이 비율: 메인 420 : 아래 칸 110씩 (autoSize라 픽셀 대신 비율로 준다)
    chart.panes().forEach((p, i) => p.setStretchFactor(i === 0 ? MAIN_HEIGHT : PANE_HEIGHT))
    const v: View = { code, interval, type, chart, main, plots, inds, bars: [], compares: [], hover: false, times, signals: [] }
    viewRef.current = v
    chart.subscribeCrosshairMove(p => {
      showTip(v, p)
      v.hover = p.time != null
      setLegendBar((p.time != null && v.bars.find(b => b.time === p.time)) || v.bars.at(-1) || null)
    })
    chart.subscribeClick(p => onChartClick(v, p))
    chart.subscribeDblClick(p => onChartClick(v, p)) // 빠르게 두 번 누르면 두 번째는 더블클릭으로만 온다
    Promise.all([code, ...compares].map(c => getBars(c, interval))).then(([bars, ...others]) => {
      if (stale || !bars) return
      v.bars = bars
      main.setData(bars.map(b => mainData(type, b)) as never)
      plots.forEach(p => p.series.setData(toData(bars, p.line.calc(bars)) as never))
      // ponytail: 표시는 차트를 열 때만 다시 찾는다 (실시간 체결로 새로 생긴 크로스는 다시 열어야 보임). 지금 상태 칸은 체결마다 갱신
      v.signals = findSignals(bars, inds.map(i => i.key))
      // 차트엔 모양만 (글자를 달면 봉과 겹친다). 이름·설명은 범례(마우스 올린 봉)와 보는 법 카드에
      createSeriesMarkers(main, v.signals.map(({ time, position, shape, color }) => ({ time: ts(time), position, shape, color })))
      const guide = (key: string, price: number, title: string, color: string) => plots.find(p => p.ind.key === key && p.line.label)?.series
        .createPriceLine({ price, title, color, lineWidth: 1, lineStyle: LineStyle.Dashed, axisLabelVisible: false })
      guide('rsi', 70, '과매수', UP)
      guide('rsi', 30, '과매도', DOWN)
      guide('macd', 0, '0', '#9ca3af')
      compares.forEach((c, i) => {
        const color = COMPARE_COLORS[i % COMPARE_COLORS.length]
        const series = chart.addSeries(LineSeries, { color, lineWidth: 2, priceLineVisible: false })
        series.setData((others[i] ?? []).map(b => ({ time: ts(b.time), value: b.close })))
        v.compares.push({ code: c, series, color, bars: others[i] ?? [] })
      })
      // 종목비교는 토스처럼 % 눈금 (각 선의 화면 첫 값 대비 등락률)
      if (compares.length) main.priceScale().applyOptions({ mode: PriceScaleMode.Percentage })
      ;(drawings.get(code + '|' + interval) ?? []).forEach(pts => addLine(v, pts, '#111827', LineStyle.Solid))
      setView(v) // 범례·보는 법이 지표·비교 목록과 신호를 보도록
      setStatusRows(status(bars, v.inds.map(i => i.key)))
      setLegendBar(bars.at(-1) ?? null)
    })
    return cleanup
  }, [code, prefs.interval, prefs.type, prefs.on.join(), compares.join(), reload])

  // 체결 한 건 반영: 틱은 점 추가, 캔들은 현재 봉·지표·비교선 갱신
  // ponytail: 체결마다 지표 전체를 다시 계산한다 (봉 최대 수천 개라 충분히 빠름). 느려지면 마지막 값만 증분 계산할 것
  useEffect(() => onStream<Quote>('trade', q => {
    const v = viewRef.current
    if (!v) return
    if (v.interval === 'tick') {
      if (q.stockCode !== v.code) return
      if (!v.times.length) v.main.applyOptions({ baseValue: { type: 'price', price: +(q.prevClose ?? q.lastPrice) } })
      v.times.push(q.timestamp)
      v.main.update({ time: ts(v.times.length), value: +q.lastPrice })
      return
    }
    for (const c of v.compares) {
      if (c.code !== q.stockCode) continue
      const bar = applyTrade(c.bars, q, v.interval)
      if (bar) c.series.update({ time: ts(bar.time), value: bar.close })
    }
    if (q.stockCode !== v.code) return
    const bar = applyTrade(v.bars, q, v.interval)
    if (!bar) return
    v.main.update(mainData(v.type, bar) as never)
    v.plots.forEach(p => { const val = p.line.calc(v.bars).at(-1); if (val != null) p.series.update(point(bar.time, val) as never) })
    if (!v.hover) setLegendBar(bar)
    setStatusRows(status(v.bars, v.inds.map(i => i.key)))
  }), [])

  // 메뉴 밖을 누르면 닫기
  useEffect(() => {
    if (!menu) return
    const close = (e: MouseEvent) => { if (!toolbarRef.current?.contains(e.target as Node)) setMenu(null) }
    document.addEventListener('click', close)
    return () => document.removeEventListener('click', close)
  }, [menu])

  const toggleMenu = (m: 'ind' | 'compare') => setMenu(menu === m ? null : m)
  function chooseTool(t: 'draw' | 'measure') { setTool(tool === t ? null : t); pending.current = null }
  function changeInterval(k: string) {
    setPrefs(p => ({ ...p, interval: k, intraday: INTRADAY.some(([key]) => key === k) ? k : p.intraday }))
  }
  function toggleIndicator(key: string) {
    setPrefs(p => ({ ...p, on: p.on.includes(key) ? p.on.filter(k => k !== key) : [...p.on, key], hidden: p.hidden.filter(k => k !== key) }))
  }
  function toggleHidden(key: string) {
    const hidden = prefs.hidden.includes(key) ? prefs.hidden.filter(k => k !== key) : [...prefs.hidden, key]
    setPrefs({ ...prefs, hidden })
    view?.plots.filter(p => p.ind.key === key).forEach(p => p.series.applyOptions({ visible: !hidden.includes(key) }))
  }
  const toggleCompare = (c: string) => setCompares(cs => cs.includes(c) ? cs.filter(x => x !== c) : [...cs, c])
  function openGuide(key: string) { setGuideKey(guideKey === key ? null : key); setGuideFocus(null); setMenu(null) }

  // 신호 자리로 차트를 옮기고 그 봉에 십자선을 둔다
  function focusSignal(s: Signal) {
    const v = viewRef.current
    if (!v) return
    setGuideFocus(s)
    const i = v.bars.findIndex(b => b.time === s.time)
    v.chart.timeScale().setVisibleLogicalRange({ from: i - 60, to: i + 20 })
    // 범위 이동은 다음 화면에 반영되고 십자선은 화면 좌표로 남아서, 이동이 끝난 뒤에 둔다
    requestAnimationFrame(() => requestAnimationFrame(() => {
      v.chart.setCrosshairPosition(v.bars[i].close, ts(s.time), v.main)
      // 코드로 옮긴 십자선은 커서 위치를 주지 않아서 말풍선 자리를 직접 잡는다
      showTip(v, { time: ts(s.time), point: { x: v.chart.timeScale().timeToCoordinate(ts(s.time)) ?? 0, y: v.main.priceToCoordinate(v.bars[i].close) ?? 0 } })
    }))
  }

  const intraday = INTRADAY.some(([k]) => k === prefs.interval)
  const others = stocks.filter(s => s.stockCode !== code)
  const menuBox = 'absolute right-0 top-full z-10 bg-card border border-border rounded-xl px-3 py-2.5 shadow-[0_6px_20px_rgba(0,0,0,0.1)] grid gap-2 text-[13px] min-w-[180px]'

  return (
    <div ref={boxRef} className="chart-box">
      <div ref={toolbarRef} className="relative flex justify-between flex-wrap gap-1 pb-1.5 mb-1.5 border-b border-border">
        <div className="flex items-center gap-0.5 flex-wrap">
          <select className={cn(toolBtn, intraday && 'bg-muted font-bold')} title="분·틱 단위" value={prefs.intraday} onChange={e => changeInterval(e.target.value)}>
            {INTRADAY.map(([k, label]) => <option key={k} value={k}>{label}</option>)}
          </select>
          {PERIODS.map(([k, label]) => <Btn key={k} active={k === prefs.interval} onClick={() => changeInterval(k)}>{label}</Btn>)}
          <span className="w-px h-[18px] bg-border mx-1.5" />
          <Btn title="차트 종류 바꾸기" onClick={() => setPrefs({ ...prefs, type: prefs.type === 'candle' ? 'line' : 'candle' })}>{prefs.type === 'candle' ? '캔들' : '라인'}</Btn>
          <Btn title="그린 선과 자 지우기" onClick={() => { drawings.delete(drawKey); setReload(r => r + 1) }}>지우기</Btn>
        </div>
        <div className="flex items-center gap-0.5 flex-wrap">
          <Btn active={menu === 'ind'} onClick={() => toggleMenu('ind')}>+ 보조지표</Btn>
          <Btn active={tool === 'draw'} title="두 지점을 클릭해 추세선 긋기" onClick={() => chooseTool('draw')}>그리기</Btn>
          <Btn active={tool === 'measure'} title="두 지점을 클릭해 등락률·봉 개수 재기" onClick={() => chooseTool('measure')}>자</Btn>
          <Btn active={menu === 'compare' || compares.length > 0} onClick={() => toggleMenu('compare')}>종목비교</Btn>
          <Btn onClick={() => document.fullscreenElement ? document.exitFullscreen() : boxRef.current?.requestFullscreen()}>차트 크게보기</Btn>
        </div>
        {menu === 'ind' && <div className={menuBox}>
          {INDICATORS.map(ind => <label key={ind.key} className="flex gap-1.5 items-center cursor-pointer">
            <input type="checkbox" checked={prefs.on.includes(ind.key)} onChange={() => toggleIndicator(ind.key)} />{ind.name}
            <button className="ml-auto px-[7px] py-px text-xs text-muted-foreground bg-background rounded-md cursor-pointer" onClick={e => { e.preventDefault(); openGuide(ind.key) }}>보는 법</button>
          </label>)}
        </div>}
        {menu === 'compare' && <div className={menuBox}>
          {others.length ? others.map(s => <label key={s.stockCode} className="flex gap-1.5 items-center cursor-pointer">
            <input type="checkbox" checked={compares.includes(s.stockCode)} onChange={() => toggleCompare(s.stockCode)} />{s.name + ' ' + s.stockCode}
          </label>) : <span>비교할 다른 팔로우 종목이 없어요</span>}
        </div>}
      </div>
      <div className="chart-wrap relative" style={{ height: MAIN_HEIGHT + panes.length * PANE_HEIGHT }}>
        <Legend view={view} bar={legendBar} hidden={prefs.hidden} onHide={toggleHidden} onRemove={toggleIndicator} onGuide={openGuide} onUncompare={toggleCompare} />
        {measure && <div className={cn('absolute z-[4] text-xs font-bold bg-white border border-current rounded-md px-1.5 py-0.5 pointer-events-none whitespace-nowrap', measure.up ? 'up' : 'down')}
          style={{ left: measure.left, top: measure.top }}>{measure.text}</div>}
        {tip && <div ref={tipRef} className="absolute z-[5] pointer-events-none max-w-[280px] bg-card border border-border border-t-[3px] border-t-current rounded-lg px-2.5 py-2 text-xs leading-[1.5] shadow-[0_6px_20px_rgba(0,0,0,0.12)]"
          style={{ color: tip.marks[0].color }}>
          <div className="text-muted-foreground text-[11px]">{tip.date}</div>
          {tip.marks.map((s, k) => <p key={k} className={cn('mt-0.5 text-foreground', k > 0 && 'border-t border-dashed border-border pt-1 mt-1')}>
            <b className="block text-[13px]" style={{ color: s.color }}>{ICON[s.shape] + ' ' + s.text}</b>{s.note}
          </p>)}
        </div>}
        <div ref={chartRef} className="h-full" style={{ cursor: tool ? 'crosshair' : undefined }} />
      </div>
      {guideKey && view && <ChartGuide guideKey={guideKey} on={view.inds.some(i => i.key === guideKey)} signals={view.signals}
        intraday={!!BUCKET[view.interval]} focus={guideFocus} onFocus={focusSignal} onClose={() => openGuide(guideKey)} />}
      {!!statusRows.length && <div className="px-3.5 py-2.5 border-t border-[#f1f3f5] text-[13px] flex flex-col gap-1">
        {statusRows.map(([name, state, why]) => <div key={name}><b>{name + ' '}</b>{state + ' '}<span className="text-muted-foreground">{'· ' + why}</span></div>)}
      </div>}
    </div>
  )
}

// 범례: 시작·고가·저가·종가 + 지표마다 [보기/숨기기] [삭제] 이름 숫자
function Legend({ view: v, bar, hidden, onHide, onRemove, onGuide, onUncompare }: {
  view: View | null; bar: Bar | null; hidden: string[]
  onHide: (k: string) => void; onRemove: (k: string) => void; onGuide: (k: string) => void; onUncompare: (c: string) => void
}) {
  if (!v || !bar || v.interval === 'tick') return null
  const prev = v.bars[v.bars.indexOf(bar) - 1]
  const base = prev?.close ?? bar.open
  const dir = (val: number) => val >= base ? 'up' : 'down'
  const rate = prev ? (bar.close / prev.close - 1) * 100 : null
  const row = 'flex gap-1.5 items-center bg-white/85 px-1 py-px rounded w-fit'
  const mini = 'pointer-events-auto bg-[#f1f3f5] rounded w-[18px] h-[18px] text-[11px] leading-[18px] cursor-pointer text-muted-foreground p-0'
  // 지표는 한 줄에 모아서 차트를 덜 가린다. 이름을 누르면 보는 법
  const chips = INDICATORS.filter(ind => v.plots.some(p => p.ind === ind))
  return (
    <div className="absolute top-1 left-1.5 z-[3] pointer-events-none text-xs flex flex-col gap-[3px]">
      <div className={row}>
        {([['시작', bar.open], ['고가', bar.high], ['저가', bar.low], ['종가', bar.close]] as const).map(([k, val]) =>
          <span key={k}>{k + ' '}<b className={dir(val)}>{fmt(val)}</b></span>)}
        {rate != null && <b className={dir(bar.close)}>{(rate >= 0 ? '+' : '') + rate.toFixed(2) + '%'}</b>}
        <span>{'거래량 ' + bar.volume.toLocaleString('en-US')}</span>
      </div>
      {!!chips.length && <div className={row}>
        {chips.map(ind => {
          const off = hidden.includes(ind.key)
          return <span key={ind.key} className="inline-flex gap-1 items-center mr-1.5">
            <button className={mini} title={off ? '보이기' : '숨기기'} onClick={() => onHide(ind.key)}>{off ? '○' : '●'}</button>
            <button className={mini} title="지표 삭제" onClick={() => onRemove(ind.key)}>×</button>
            <span className={cn('pointer-events-auto cursor-pointer underline decoration-dotted underline-offset-[3px]', off && 'opacity-35')} title="보는 법" onClick={() => onGuide(ind.key)}>{ind.name}</span>
            {ind.lines.filter(l => l.label).map(l => <span key={l.label} className={cn(off && 'opacity-35')} style={{ color: l.color }}>{l.label}</span>)}
          </span>
        })}
      </div>}
      {v.compares.map(c => <div key={c.code} className={row}>
        <button className={mini} title="비교 해제" onClick={() => onUncompare(c.code)}>×</button>
        <span style={{ color: c.color }}>{'비교 ' + c.code + ' (%)'}</span>
      </div>)}
    </div>
  )
}
