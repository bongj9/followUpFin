import { useState, type FormEvent } from 'react'
import { ChartLine, Newspaper, Search, Star, X } from 'lucide-react'
import { errorText, type ImportResult, type Stock } from '@/api'
import type { Quotes, Selected } from '@/App'
import type { Position } from '@/components/Account'
import { Avatars } from '@/components/Shows'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { cn, dirClass, priceText, signedPct } from '@/lib/format'
import { isRecent, saidFor, whoMentioned, type Episode } from '@/lib/shows'

const NO_INDUSTRY = '분류 없음'
// 업종은 Finnhub 영어 이름으로 온다. 화면에만 한글로 (필터·업종 피드는 원래 이름)
const KO: Record<string, string> = { Semiconductors: '반도체', Technology: '기술', Automobiles: '자동차', Banking: '은행', Pharmaceuticals: '제약', Retail: '유통', Media: '미디어' }
const ko = (i: string) => KO[i] ?? i
const PALETTE = ['#dfe8ff', '#f4dfd6', '#e8f0d0', '#efe3c6', '#e5dcf5', '#d8efe9']
const pill = 'glass rounded-full px-3 py-[7px] text-xs cursor-pointer'
const SORTS = { rate: '등락률순', weight: '보유 비중순', talk: '언급 많은순' } as const

// 관심 종목 카드 그리드. 보유 종목은 별이 라임이고 막대가 계좌 비중, 나머지는 막대가 오늘 변동폭
// 카드 오른쪽 위 아바타 = 최근 7일 안에 그 종목을 방송에서 다룬 사람
export function Watchlist({ stocks, quotes, positions, eps, aliases, select, refresh }: {
  stocks: Stock[]; quotes: Quotes; positions: Position[]; eps: Episode[]; aliases: Record<string, string>
  select: (s: Selected) => void; refresh: () => void
}) {
  const [error, setError] = useState('')
  const [adding, setAdding] = useState(false)
  const [importing, setImporting] = useState(false)
  const [query, setQuery] = useState('')
  const [industry, setIndustry] = useState<string | null>(null)
  const [sort, setSort] = useState<keyof typeof SORTS>('rate')
  const [showAdd, setShowAdd] = useState(false)

  const held = new Map(positions.filter(p => p.quantity >= 1).map(p => [p.symbol, p]))
  const totalValue = positions.reduce((s, p) => s + (p.value ?? 0), 0)
  const weight = (code: string) => totalValue ? (held.get(code)?.value ?? 0) / totalValue : 0
  const industries = [...new Set(stocks.map(s => s.industry ?? NO_INDUSTRY))].sort()
  const key = {
    rate: (s: Stock) => Math.abs(quotes[s.stockCode]?.changeRate ?? 0),
    weight: (s: Stock) => weight(s.stockCode),
    talk: (s: Stock) => saidFor(eps, aliases, s.stockCode).filter(r => isRecent(eps, r.ep.date)).length,
  }[sort]
  const q = query.trim().toLowerCase()
  const list = stocks
    .filter(s => !industry || (s.industry ?? NO_INDUSTRY) === industry)
    .filter(s => !q || (s.name + s.stockCode + (s.industry ?? '')).toLowerCase().includes(q))
    .sort((a, b) => key(b) - key(a))

  async function unfollow(code: string) {
    await fetch('/api/stocks/' + encodeURIComponent(code), { method: 'DELETE' })
    refresh()
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

  return <div className="flex flex-col gap-3.5">
    <div className="flex gap-2 items-center flex-wrap">
      <label className="glass rounded-full w-[260px] px-3.5 py-[9px] flex gap-2 items-center text-muted-foreground">
        <Search size={14} className="shrink-0" />
        <input className="bg-transparent outline-none text-xs text-foreground w-full placeholder:text-muted-foreground/70" placeholder="종목명, 티커, 업종 검색…"
          value={query} onChange={e => setQuery(e.target.value)} />
      </label>
      {[null, ...industries].map(i => <button key={i ?? 'all'} onClick={() => setIndustry(i)}
        className={cn(pill, industry === i ? 'bg-white/15 text-foreground' : 'text-muted-foreground')}>{i ? ko(i) : '전체'}</button>)}
      {industry && industry !== NO_INDUSTRY && <button className="text-xs text-primary cursor-pointer" onClick={() => select({ industry })}>이 업종 뉴스 →</button>}
      <div className="flex-1" />
      <button className={cn(pill, 'text-muted-foreground')} onClick={() => setSort(s => s === 'rate' ? 'weight' : s === 'weight' ? 'talk' : 'rate')}>{SORTS[sort]}</button>
    </div>

    <div className="flex gap-2 items-center flex-wrap">
      <button className="bg-primary text-primary-foreground font-semibold rounded-full px-4 py-[9px] text-xs cursor-pointer shadow-[0_6px_24px_rgba(212,242,106,0.18)]"
        onClick={() => setShowAdd(v => !v)}>＋ 종목 추가</button>
      {showAdd && <form className="flex gap-1.5 items-center" onSubmit={follow}>
        <Input name="stockCode" placeholder="티커 (NVDA)" maxLength={7} required className="w-32" />
        <Input name="name" placeholder="이름 (선택)" className="w-32" />
        <Button disabled={adding}>추가</Button>
        <Button type="button" variant="soft" disabled={importing} onClick={importHoldings}>{importing ? '가져오는 중…' : '토스 보유 종목 가져오기'}</Button>
      </form>}
      <span className="text-up text-xs">{error}</span>
    </div>

    <div className="grid grid-cols-[repeat(auto-fill,minmax(240px,1fr))] gap-3.5">
      {list.map((s, n) => {
        const quote = quotes[s.stockCode], h = held.get(s.stockCode), w = weight(s.stockCode)
        const who = whoMentioned(eps, aliases, s.stockCode)
        const ini = (/^\d/.test(s.stockCode) ? s.name : s.stockCode).slice(0, 2).toUpperCase()
        const rate = h ? h.profitRate : quote?.changeRate
        return (
          <Card key={s.stockCode} className="p-4 flex flex-col gap-3 cursor-pointer transition hover:-translate-y-0.5 hover:bg-white/[0.075]"
            onClick={() => select({ stockCode: s.stockCode })}>
            <div className="flex justify-between items-center">
              <div className="grid place-items-center w-[38px] h-[38px] rounded-full font-bold text-[11px] text-[#1c1c1c]" style={{ background: PALETTE[n % PALETTE.length] }}>{ini}</div>
              <div className="flex gap-2 items-center">
                {!!who.length && <span title="최근 7일 방송에서 다룸"><Avatars list={who} /></span>}
                <span className={cn('grid place-items-center w-[26px] h-[26px] rounded-full border', h ? 'bg-primary border-primary text-primary-foreground' : 'border-border text-muted-foreground')}
                  title={h ? '보유 중' : '관심'}><Star size={12} /></span>
              </div>
            </div>
            <div className="min-w-0">
              <div className="text-[15px] font-semibold tracking-[-0.01em] truncate">{s.name}</div>
              <div className="text-[11px] text-muted-foreground truncate">{[ko(s.industry ?? NO_INDUSTRY), s.stockCode, h && `${h.quantity.toLocaleString('en-US')}주 보유`].filter(Boolean).join(' · ')}</div>
            </div>
            <div className="grid grid-cols-2 gap-2 px-3 py-2.5 rounded-[14px] bg-white/[0.045] border border-white/[0.06] tnum">
              <div><small className="block text-[10px] text-muted-foreground/70">현재가</small><b className="text-[13px] font-semibold">{quote ? priceText(quote) : '-'}</b></div>
              <div><small className="block text-[10px] text-muted-foreground/70">{h ? '보유 손익' : '오늘'}</small>
                <b className={cn('text-[13px] font-semibold', dirClass(rate), quote?.bigMove && !h && 'px-1.5 rounded-md bg-warn-soft')}>{rate == null ? '-' : signedPct(rate)}</b></div>
            </div>
            <div className="flex items-center gap-2.5 text-[10px] text-muted-foreground">
              <div className="flex-1 h-1 rounded-full bg-white/10 overflow-hidden">
                <i className="block h-full rounded-full" style={h
                  ? { width: Math.max(w * 100, 2) + '%', background: 'var(--color-primary)' }
                  : { width: Math.min(Math.abs(quote?.changeRate ?? 0) * 12, 100) + '%', background: 'var(--color-down)' }} />
              </div>
              {h ? `비중 ${(w * 100).toFixed(1)}%` : '오늘 변동'}
            </div>
            <div className="flex justify-between items-center">
              <span className="text-[11px] px-2.5 py-1 rounded-full border border-border text-muted-foreground truncate max-w-[50%]">{ko(s.industry ?? NO_INDUSTRY)}</span>
              <div className="flex gap-1.5">
                <span className="grid place-items-center w-[26px] h-[26px] rounded-full border border-border text-muted-foreground" title="차트"><ChartLine size={12} /></span>
                <span className="grid place-items-center w-[26px] h-[26px] rounded-full border border-border text-muted-foreground" title="뉴스·공시"><Newspaper size={12} /></span>
                <button className="grid place-items-center w-[26px] h-[26px] rounded-full border border-border text-muted-foreground cursor-pointer hover:text-up" title="팔로우 해제"
                  onClick={ev => { ev.stopPropagation(); unfollow(s.stockCode) }}><X size={12} /></button>
              </div>
            </div>
          </Card>
        )
      })}
      {!list.length && <p className="text-muted-foreground">맞는 종목이 없어요.</p>}
    </div>
  </div>
}
