import { useEffect, useState } from 'react'
import { getIndicators, type Indicator } from '@/api'
import { dirClass, DOWN, UP } from '@/lib/format'

function Spark({ values, color }: { values: number[]; color: string }) {
  const min = Math.min(...values), max = Math.max(...values), span = max - min || 1
  const points = values.map((v, i) => (i / (values.length - 1) * 60).toFixed(1) + ',' + (18 - (v - min) / span * 16).toFixed(1)).join(' ')
  return <svg viewBox="0 0 60 20" className="w-[60px] h-5" aria-hidden="true"><polyline points={points} fill="none" stroke={color} strokeWidth="1.5" /></svg>
}

// 상단 지표 띠 (코스피, 코스닥, 미국 지수 ETF, 원/달러). 그래프는 최근 2시간 1분봉 종가, 전일 종가보다 높으면 빨강
export function Ticker({ tick }: { tick: number }) {
  const [list, setList] = useState<Indicator[]>([])
  useEffect(() => { getIndicators().then(setList) }, [tick])
  if (!list.length) return null
  const item = (i: Indicator) => (
    <div key={i.label} className="flex items-center gap-2 px-[22px] py-2.5 text-[13px] whitespace-nowrap tnum border-r border-muted">
      <span>{i.label}</span>
      <b className="font-semibold">{i.last == null ? '-' : (+i.last).toLocaleString('en-US', { maximumFractionDigits: 2 })}</b>
      <span className={dirClass(i.changeRate)}>{i.changeRate == null ? '' : (i.changeRate > 0 ? '+' : '') + (+i.changeRate).toFixed(2) + '%'}</span>
      {i.spark.length > 1 && <Spark values={i.spark.map(Number)} color={i.changeRate! > 0 ? UP : i.changeRate! < 0 ? DOWN : '#9ca3af'} />}
    </div>
  )
  return (
    <div className="ticker overflow-hidden bg-card border-b border-muted" aria-label="주요 지표">
      <div className="track"><div className="flex">{list.map(item)}</div><div className="copy flex">{list.map(item)}</div></div>
    </div>
  )
}
