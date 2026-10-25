import { useEffect, useRef, useState } from 'react'
import { createChart, createSeriesMarkers, HistogramSeries, LineSeries, LineStyle } from 'lightweight-charts'
import { getFlows, type MarketFlow } from '@/api'
import { Card, CardTitle } from '@/components/ui/card'
import { Tabs } from '@/components/ui/tabs'
import { DOWN, UP } from '@/lib/format'
import { sma } from '@/lib/indicators'

const FLOW_MARKETS = [['KOSPI', '코스피'], ['KOSDAQ', '코스닥']] as const
const jo = (v: number) => v / 1e12

// 홈: 시장 전체 외국인 순매수. 위 칸은 누적과 그 4주(20영업일) 평균, 아래 칸은 하루치 (조원)
// 누적선이 평균선을 뚫고 올라오면 외국인이 사는 쪽으로 돌아선 것으로 본다
export function FlowChart({ tick, className }: { tick: number; className?: string }) {
  const [market, setMarket] = useState<'KOSPI' | 'KOSDAQ'>('KOSPI')
  const [flows, setFlows] = useState<MarketFlow[] | null>(null)
  const box = useRef<HTMLDivElement>(null)
  useEffect(() => { getFlows(market).then(setFlows) }, [market, tick])

  const ok = !!flows && flows.length >= 21
  let sum = 0
  const cum = ok ? flows.map(f => sum += f.foreigner) : []
  const avg = sma(cum, 20)
  const above = (i: number) => cum[i] > avg[i]!

  useEffect(() => {
    if (!ok || !box.current) return
    const chart = createChart(box.current, {
      autoSize: true, layout: { fontSize: 11, panes: { separatorColor: '#e5e7eb' } },
      grid: { vertLines: { color: '#f1f3f5' }, horzLines: { color: '#f1f3f5' } },
      localization: { priceFormatter: (v: number) => v.toFixed(1) + '조' } })
    const line = chart.addSeries(LineSeries, { color: '#111827', lineWidth: 2, priceLineVisible: false, title: '누적' })
    line.setData(flows.map((f, i) => ({ time: f.date, value: jo(cum[i]) })))
    chart.addSeries(LineSeries, { color: DOWN, lineWidth: 1, lineStyle: LineStyle.Dashed, priceLineVisible: false, lastValueVisible: false, title: '4주 평균' })
      .setData(flows.flatMap((f, i) => avg[i] == null ? [] : [{ time: f.date, value: jo(avg[i]!) }]))
    chart.addSeries(HistogramSeries, { priceLineVisible: false }, 1)
      .setData(flows.map(f => ({ time: f.date, value: jo(f.foreigner), color: f.foreigner >= 0 ? UP : DOWN })))
    chart.panes().forEach((p, i) => p.setStretchFactor(i === 0 ? 3 : 1))
    // 누적선이 평균선을 뚫은 날 표시
    createSeriesMarkers(line, flows.flatMap((f, i) => i === 0 || avg[i - 1] == null || above(i) === above(i - 1) ? []
      : [{ time: f.date, position: above(i) ? 'belowBar' as const : 'aboveBar' as const, shape: above(i) ? 'arrowUp' as const : 'arrowDown' as const, color: above(i) ? UP : DOWN }]))
    chart.timeScale().fitContent()
    return () => chart.remove()
  }, [flows])

  const n = cum.length - 1
  let days = 1
  if (ok) while (days <= n && avg[n - days] != null && above(n - days) === above(n)) days++
  const week = (key: 'foreigner' | 'institution' | 'individual') => jo(flows!.slice(-5).reduce((a, f) => a + f[key], 0))
  const signed = (v: number) => (v >= 0 ? '+' : '') + v.toFixed(1) + '조'

  return (
    <Card className={className}>
      <div className="flex justify-between items-center gap-2">
        <CardTitle className="m-0">외국인 수급</CardTitle>
        <Tabs items={FLOW_MARKETS} value={market} onChange={setMarket} />
      </div>
      <div className="text-[13px] mt-1.5 mb-2 flex flex-col gap-0.5">
        {flows && !ok && <div className="text-muted-foreground">수급 데이터를 받지 못했어요.</div>}
        {ok && <>
          <div>
            <b>{`누적선이 4주 평균선 ${above(n) ? '위' : '아래'} (${days}일째) `}</b>
            <span className="text-muted-foreground">{above(n) ? '· 외국인이 사는 쪽으로 돌아선 상태. 수급을 중시하는 투자자는 이때 비중을 늘린다'
              : '· 외국인이 아직 파는 흐름. 누적선이 평균선을 뚫고 올라오는지 지켜본다'}</span>
          </div>
          <div>{`최근 5일 순매수 · 외국인 ${signed(week('foreigner'))} · 기관 ${signed(week('institution'))} · 개인 ${signed(week('individual'))}`}</div>
          <div className="text-muted-foreground">{`${flows[0].date}부터 ${flows.length}영업일 누적 (기준일에 따라 수치는 달라지니 선의 방향을 보세요) · 마지막 ${flows[n].date}, 당일은 잠정치`}</div>
        </>}
      </div>
      <div ref={box} className="h-[260px] relative" />
    </Card>
  )
}
