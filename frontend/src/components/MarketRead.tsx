import { useEffect, useState } from 'react'
import { getBars, type Bar } from '@/api'
import { closes, rsi, sma, status } from '@/lib/indicators'
import { cn, dirClass, signedPct } from '@/lib/format'

// 토스에 미국 지수가 없어 같은 지수를 따라가는 ETF 일봉으로 읽는다 (등락률·추세는 지수와 같다)
const INDEX = [['QQQ', '나스닥100'], ['SPY', 'S&P500'], ['DIA', '다우30'], ['IWM', '러셀2000']] as const

// 20·60·120일선 중 어디 위/아래인지로 추세를 한 줄로. 숫자는 다음 지지·저항선
export function trendLine(c: number[]) {
  const i = c.length - 1, last = c[i], [m20, m60, m120] = [20, 60, 120].map(n => sma(c, n)[i]!)
  const gap = (m: number) => `${((last / m - 1) * 100).toFixed(1)}%`, at = (m: number) => m.toFixed(2)
  const up = [last > m20, last > m60, last > m120].map(Number).join('')
  const text = ({
    '111': m20 > m60 && m60 > m120 ? `세 이평선 위, 정배열 → 상승 추세. 20일선(${at(m20)}, ${gap(m20)})까지 눌림은 흔하다`
      : `세 이평선 위로 올라섰지만 선들이 아직 정렬 전 → 상승 전환 시도. 20일선 ${at(m20)}을 지키는지 본다`,
    '011': `20일선(${at(m20)}) 아래 ${gap(m20)} → 큰 흐름은 위, 단기 조정 중. 다음 지지는 60일선 ${at(m60)}`,
    '001': `20·60일선 아래 → 중기 조정. 120일선 ${at(m120)}(${gap(m120)})이 마지막 지지`,
    '010': `20일선(${at(m20)}) 아래, 60일선 위, 120일선 ${at(m120)} 아래 → 추세가 엇갈림. 60일선 ${at(m60)}을 지키는지 본다`,
    '000': m20 < m60 && m60 < m120 ? `세 이평선 아래, 역배열 → 하락 추세. 반등해도 20일선 ${at(m20)}이 먼저 저항`
      : `세 이평선을 한꺼번에 깼다(20일선 대비 ${gap(m20)}) → 선들은 아직 평평해 하락 추세 확정 전. 20일선 ${at(m20)} 회복이 먼저`,
  } as Record<string, string>)[up] ?? `20일선 위로 올라왔지만 ${last > m60 ? '120일선 ' + at(m120) : '60일선 ' + at(m60)} 아래 → 반등 중, 위 이평선이 저항`
  const r = rsi(c)[i]!
  return text + (r <= 30 ? ` · RSI ${r.toFixed(0)} 과매도` : r >= 70 ? ` · RSI ${r.toFixed(0)} 과열` : '')
}

// 뉴스·공시 "시장" 탭 위: 지수마다 이평선·RSI·MACD·볼린저 판정과 이평선 해석 한 줄 (종목 차트 "지금 상태"와 같은 규칙)
export function MarketRead({ tick }: { tick: number }) {
  const [bars, setBars] = useState<(Bar[] | null)[]>([])
  useEffect(() => { Promise.all(INDEX.map(([s]) => getBars(s, '1d'))).then(setBars) }, [tick])
  return <div className="flex flex-col gap-2.5 mb-4">
    {INDEX.map(([sym, name], k) => {
      const b = bars[k]
      if (!b || b.length < 121) return null // 120일선까지 있어야 판정이 맞다
      const c = closes(b), rows = status(b, ['ma', 'rsi', 'macd', 'bb'])
      const rate = (c.at(-1)! / c.at(-2)! - 1) * 100
      return <div key={sym} className="px-3.5 py-3 rounded-[14px] bg-white/[0.045] border border-white/[0.06]">
        <div className="flex items-baseline gap-2 text-[13px]">
          <b>{name}</b><span className="text-[11px] text-muted-foreground">{sym}</span>
          <span className="ml-auto tnum">{c.at(-1)!.toFixed(2)}</span><span className={cn('tnum', dirClass(rate))}>{signedPct(rate)}</span>
        </div>
        <div className="flex flex-wrap gap-1.5 mt-2">
          {rows.map(([label, state, hint]) => <span key={label} title={hint} className="text-[11px] px-2 py-0.5 rounded-full border border-border text-muted-foreground">
            {label} <span className="text-foreground">{state.split(' · ')[0]}</span></span>)}
        </div>
        <p className="text-xs text-muted-foreground mt-2">{trendLine(c)}</p>
      </div>
    })}
  </div>
}
