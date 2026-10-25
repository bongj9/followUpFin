import { useState, type FormEvent } from 'react'
import { X } from 'lucide-react'
import { errorText, type ImportResult, type Quote, type Stock } from '@/api'
import type { Quotes, Selected } from '@/App'
import { Button } from '@/components/ui/button'
import { Card, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { changeText, cn, dirClass, priceText } from '@/lib/format'

const NO_INDUSTRY = '분류 없음'
const row = 'flex justify-between items-center px-3 py-2.5 rounded-xl cursor-pointer transition-colors hover:bg-background'
const active = 'bg-primary-soft text-primary font-semibold hover:bg-primary-soft'

// 큰 변동은 노란 배경
function QuoteBox({ q }: { q?: Quote }) {
  if (!q) return <span />
  return (
    <div className="ml-auto mr-1.5 text-right text-xs leading-[1.3] whitespace-nowrap" title={'전일 종가 ' + (q.prevClose ?? '-')}>
      <div>{priceText(q)}</div>
      <div className={cn(dirClass(q.changeRate), q.bigMove && 'font-bold px-1.5 rounded-md bg-[#fef3c7]')}>{changeText(q)}</div>
    </div>
  )
}

// 업종별로 묶어서 보여준다. 업종 제목을 누르면 그 업종 전체 피드
export function Watchlist({ stocks, quotes, selected, select, refresh, className }:
  { stocks: Stock[]; quotes: Quotes; selected: Selected; select: (s: Selected) => void; refresh: () => void; className?: string }) {
  const [error, setError] = useState('')
  const [adding, setAdding] = useState(false)
  const [importing, setImporting] = useState(false)
  const groups = [...Map.groupBy(stocks, s => s.industry ?? NO_INDUSTRY)].sort(([a], [b]) => a.localeCompare(b))

  async function unfollow(code: string) {
    await fetch('/api/stocks/' + encodeURIComponent(code), { method: 'DELETE' })
    if (selected?.stockCode === code) select(null); else refresh()
  }

  async function follow(ev: FormEvent<HTMLFormElement>) {
    ev.preventDefault()
    const form = ev.currentTarget
    const data = new FormData(form)
    setAdding(true) // 추가하면서 첫 뉴스까지 받아와 1~2초 걸린다
    const res = await fetch('/api/stocks', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ stockCode: String(data.get('stockCode')).trim(), name: String(data.get('name')).trim() }),
    })
    setAdding(false)
    setError(res.ok ? '' : ((await res.json().catch(() => null))?.message || '티커를 확인하세요'))
    if (res.ok) { form.reset(); refresh() }
  }

  // 토스 계좌에 들고 있는 종목을 관심종목에 한 번에 넣는다 (이미 있는 종목은 건너뜀)
  async function importHoldings() {
    setImporting(true) // 종목마다 첫 뉴스를 받아서 몇 초 걸린다
    const res = await fetch('/api/stocks/import-holdings', { method: 'POST' })
    const r: ImportResult | null = res.ok ? await res.json().catch(() => null) : null
    setImporting(false)
    setError(!r ? '가져오지 못했어요: ' + await errorText(res)
      : (r.added.length ? r.added.length + '개 추가: ' + r.added.join(', ') : '새로 추가할 보유 종목이 없어요')
        + (r.failed.length ? ' · 실패: ' + r.failed.join(', ') : ''))
    refresh()
  }

  return (
    <Card className={cn('self-start', className)}>
      <CardTitle>관심 종목</CardTitle>
      <div className={cn(row, !selected && active)} onClick={() => select(null)}>전체</div>
      {groups.map(([industry, list]) => (
        <div key={industry}>
          <div className={cn('flex justify-between mt-3 px-3 py-1.5 text-xs font-semibold text-muted-foreground rounded-[10px] cursor-pointer hover:bg-background',
            selected?.industry === industry && active)}
            onClick={() => industry !== NO_INDUSTRY && select({ industry })}>
            <span>{industry}</span><span>{list.length}</span>
          </div>
          {list.map(s => (
            <div key={s.stockCode} className={cn(row, 'pl-[18px]', selected?.stockCode === s.stockCode && active)}
              onClick={() => select({ stockCode: s.stockCode })}>
              <span>{s.name}<small className="text-muted-foreground font-normal ml-1.5">{s.stockCode}</small></span>
              <QuoteBox q={quotes[s.stockCode]} />
              <button className="text-muted-foreground cursor-pointer" title="팔로우 해제"
                onClick={ev => { ev.stopPropagation(); unfollow(s.stockCode) }}><X size={14} /></button>
            </div>
          ))}
        </div>
      ))}
      <form className="grid grid-cols-[1fr_1fr_auto] gap-1.5 mt-4 border-t border-muted pt-4" onSubmit={follow}>
        <Input name="stockCode" placeholder="티커 (NVDA)" maxLength={7} required />
        <Input name="name" placeholder="이름 (선택)" />
        <Button disabled={adding}>추가</Button>
      </form>
      <Button variant="soft" size="lg" className="mt-2" disabled={importing} onClick={importHoldings}>
        {importing ? '가져오는 중…' : '토스 보유 종목 가져오기'}
      </Button>
      <div className="text-up text-xs mx-1 mt-1.5">{error}</div>
    </Card>
  )
}
