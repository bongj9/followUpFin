import { ArrowLeft } from 'lucide-react'
import type { Quote, Stock } from '@/api'
import type { Selected } from '@/App'
import { changeText, cn, dirClass, priceText } from '@/lib/format'

// 종목 헤더: 이름·코드·현재가·전일 대비 (토스 종목 화면 맨 위)
export function StockHead({ code, stock, quote, select, className }:
  { code: string; stock?: Stock; quote?: Quote; select: (s: Selected) => void; className?: string }) {
  return (
    <div className={cn('flex items-center gap-3 px-1', className)}>
      <button className="grid place-items-center w-10 h-10 bg-card shadow-card rounded-xl cursor-pointer hover:bg-background" title="전체 뉴스로" onClick={() => select(null)}>
        <ArrowLeft size={18} />
      </button>
      <div>
        <div className="flex gap-2 items-baseline">
          <b className="text-xl font-extrabold tracking-[-0.03em]">{stock?.name ?? code}</b>
          <small className="text-muted-foreground text-[13px]">{code + (stock?.industry ? ' · ' + stock.industry : '')}</small>
        </div>
        <div className="flex gap-2.5 items-baseline">
          <span className="text-[32px] font-extrabold tracking-[-0.03em]">{quote ? priceText(quote) : '-'}</span>
          <span className={cn('text-sm font-semibold', dirClass(quote?.changeRate))}>{quote ? changeText(quote) : ''}</span>
        </div>
      </div>
    </div>
  )
}
