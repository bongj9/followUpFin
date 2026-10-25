import type { CSSProperties } from 'react'
import type { Level, Quote } from '@/api'
import { cn, dirClass, num } from '@/lib/format'
import type { Side } from './useSide'

const BOOK_DEPTH = 10

export function OrderBook({ side, quote }: { side: Side; quote?: Quote }) {
  if (!side.book) return <div className="text-muted-foreground p-6 text-center">{side.error || '호가를 불러오는 중…'}</div>
  const { currency } = side.book
  const asks = (side.book.asks ?? []).slice(0, BOOK_DEPTH).reverse() // 높은 가격이 위로
  const bids = (side.book.bids ?? []).slice(0, BOOK_DEPTH)
  const max = Math.max(1, ...[...asks, ...bids].map(l => +l.volume))
  const prev = quote?.prevClose == null ? null : +quote.prevClose
  const row = (l: Level, kind: 'ask' | 'bid', i: number) => {
    const vol = <span className="vol px-2 h-full flex items-center" style={{ '--w': (+l.volume / max * 100).toFixed(1) + '%' } as CSSProperties}>{(+l.volume).toLocaleString('en-US')}</span>
    const rate = prev ? (+l.price / prev - 1) * 100 : null
    const now = quote && +l.price === +quote.lastPrice
    return (
      <div key={kind + i} className={cn('lvl grid grid-cols-[1fr_auto_1fr] items-center h-7 text-xs tnum', kind, kind === 'ask' ? 'bg-[#f5f8ff]' : 'bg-[#fff6f6]')}>
        {kind === 'ask' ? vol : <span />}
        <span className={cn('flex flex-col items-center min-w-[84px] leading-[1.15] px-1.5 py-px rounded', dirClass(rate), now && 'shadow-[inset_0_0_0_1.5px_var(--color-foreground)]')}>
          <b>{num(l.price, currency)}</b>
          <small className="text-[10px] opacity-80">{rate == null ? '' : (rate > 0 ? '+' : '') + rate.toFixed(2) + '%'}</small>
        </span>
        {kind === 'bid' ? vol : <span />}
      </div>
    )
  }
  return <div>{asks.map((l, i) => row(l, 'ask', i))}{bids.map((l, i) => row(l, 'bid', i))}</div>
}
