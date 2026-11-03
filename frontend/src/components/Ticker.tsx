import { useEffect, useState } from 'react'
import { getIndicators, type Indicator } from '@/api'
import { cn, dirClass, DOWN, UP } from '@/lib/format'

function Spark({ values, color }: { values: number[]; color: string }) {
  const min = Math.min(...values), max = Math.max(...values), span = max - min || 1
  const points = values.map((v, i) => (i / (values.length - 1) * 60).toFixed(1) + ',' + (18 - (v - min) / span * 16).toFixed(1)).join(' ')
  return <svg viewBox="0 0 60 20" className="w-[44px] h-4" aria-hidden="true"><polyline points={points} fill="none" stroke={color} strokeWidth="1.5" /></svg>
}

// 시장 지표 줄 (코스피, 코스닥, 미국 지수 ETF, 원/달러): 알약 모양. 그래프는 최근 2시간 1분봉 종가, 전일 종가보다 높으면 빨강
export function Ticker({ tick }: { tick: number }) {
  const [list, setList] = useState<Indicator[]>([])
  useEffect(() => { getIndicators().then(setList) }, [tick])
  if (!list.length) return null
  return (
    <div className="flex gap-2 overflow-x-auto pb-0.5" aria-label="주요 지표">
      {list.map(i => (
        <div key={i.label} className="glass rounded-full flex items-center gap-2 px-3 py-[7px] text-xs whitespace-nowrap tnum">
          <span className="text-muted-foreground">{i.label}</span>
          <span>{i.last == null ? '-' : (+i.last).toLocaleString('en-US', { maximumFractionDigits: 2 })}</span>
          <b className={cn('font-semibold', dirClass(i.changeRate))}>{i.changeRate == null ? '' : (i.changeRate > 0 ? '+' : '') + (+i.changeRate).toFixed(2) + '%'}</b>
          {i.spark.length > 1 && <Spark values={i.spark.map(Number)} color={i.changeRate! > 0 ? UP : i.changeRate! < 0 ? DOWN : '#9ca3af'} />}
        </div>
      ))}
    </div>
  )
}
