import { dirClass, num } from '@/lib/format'
import type { Side } from './useSide'

const cols = 'grid grid-cols-[1fr_1fr_0.7fr] px-1.5 py-1 text-xs tnum [&>span:not(:first-child)]:text-right'

export function Trades({ side }: { side: Side }) {
  if (!side.trades.length) return <div className="text-muted-foreground p-6 text-center">{side.error || '아직 체결이 없어요'}</div>
  const time = (t: string) => new Date(t).toLocaleTimeString('en-GB') // 10:17:11
  return (
    <div className="max-h-[420px] overflow-y-auto">
      <div className={cols + ' text-muted-foreground border-b border-border'}><span>시간</span><span>체결가</span><span>수량</span></div>
      {side.trades.map((t, i) => {
        const older = side.trades[i + 1]
        return (
          <div key={t.timestamp + i} className={cols}>
            <span>{time(t.timestamp)}</span>
            <span className={older ? dirClass(t.price - older.price) : ''}>{num(t.price, t.currency)}</span>
            <span>{(+t.volume).toLocaleString('en-US')}</span>
          </div>
        )
      })}
    </div>
  )
}
