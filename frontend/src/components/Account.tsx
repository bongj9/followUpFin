import { useEffect, useState } from 'react'
import { getCash, getIndicators, getPortfolio, type Holding, type Portfolio } from '@/api'
import type { Quotes, Selected } from '@/App'
import { Card } from '@/components/ui/card'
import { cn, dirClass, signedPct } from '@/lib/format'

export const won = (n: number) => Math.round(n).toLocaleString('ko-KR') + '원'

export interface Position extends Holding { purchase: number | null; value: number | null; profit: number | null; daily: number | null }

// 수량·평단·매입금액은 토스 계좌(10초마다 조회), 가격은 실시간 체결(웹소켓 → SSE, 시간외 포함)로 계산한다. 조회만 하고 주문은 하지 않는다
// 그래서 관심 종목 시세와 숫자가 항상 같고, 장 마감 뒤엔 토스 계좌 API(정규장 가격)와 다를 수 있다. 실시간 가격이 없으면 토스 값을 쓴다
// 달러 종목은 원/달러로 원화 환산 (원화 금액은 토스 앱 환율과 조금 다를 수 있음)
export function useAccount(tick: number, quotes: Quotes) {
  const [portfolio, setPortfolio] = useState<Portfolio | null>(null)
  const [cash, setCash] = useState<{ KRW?: number; USD?: number } | null>(null)
  const [fx, setFx] = useState<number | null>(null)
  useEffect(() => { getIndicators().then(is => setFx(is.find(i => i.label === '원/달러')?.last ?? null)) }, [tick])
  // 사고팔면 수량·현금이 바로 바뀌게 10초마다 따로 조회 (토스 한도 초당 5~6회라 여유). 탭을 안 보고 있으면 건너뛴다
  useEffect(() => {
    const load = () => { if (!document.hidden) { getPortfolio().then(p => p && setPortfolio(p)); getCash().then(setCash) } }
    load()
    const timer = setInterval(load, 10_000)
    document.addEventListener('visibilitychange', load)
    return () => { clearInterval(timer); document.removeEventListener('visibilitychange', load) }
  }, [])

  const krw = (v: number | null, currency: string) => v == null ? null : currency === 'USD' ? (fx == null ? null : v * fx) : v
  const positions: Position[] = (portfolio?.items ?? []).map(h => {
    const q = quotes[h.symbol], last = q?.lastPrice ?? h.lastPrice
    const purchase = h.purchaseAmount ?? h.quantity * h.averagePrice
    const value = last == null ? h.marketValue : h.quantity * last
    const profit = value == null ? null : value - purchase
    const daily = q?.change != null ? h.quantity * q.change : h.dailyProfitLoss // 전일 종가 대비
    return {
      ...h, lastPrice: last, profitRate: profit == null || !purchase ? null : profit / purchase * 100,
      dailyProfitRate: q?.changeRate ?? h.dailyProfitRate,
      purchase: krw(purchase, h.currency), value: krw(value, h.currency), profit: krw(profit, h.currency), daily: krw(daily, h.currency),
    }
  }).sort((a, b) => (b.value ?? 0) - (a.value ?? 0))
  const priced = positions.filter(p => p.value != null && p.purchase != null)
  const sum = (f: (p: Position) => number | null) => priced.reduce((s, p) => s + (f(p) ?? 0), 0)
  const stocks = sum(p => p.value), purchase = sum(p => p.purchase), profit = sum(p => p.profit), daily = sum(p => p.daily)
  const cashKrw = cash && fx != null ? (cash.KRW ?? 0) + (cash.USD ?? 0) * fx : null
  return {
    positions, cash, stocks, cashKrw, ready: priced.length > 0,
    profit, profitRate: purchase ? profit / purchase * 100 : null,
    daily, dailyRate: stocks - daily ? daily / (stocks - daily) * 100 : null, // 어제 평가액 대비
  }
}

const signedWon = (n: number) => (n > 0 ? '+' : '') + won(n)

// 토스 "내 계좌"처럼: 총자산 크게, 아래 평가손익, 주식·현금, 보유 종목(평가액·수익률)
export function Account({ account, select, className }: { account: ReturnType<typeof useAccount>; select: (s: Selected) => void; className?: string }) {
  const { positions, cash, stocks, profit, profitRate, daily, dailyRate, cashKrw, ready } = account
  if (!ready) return null
  const pct = (r: number | null) => r == null ? '' : ` (${signedPct(r)})`
  return (
    <Card className={className}>
      <div className="text-[13px] text-muted-foreground mx-1">내 계좌 · 토스증권</div>
      <div className="text-[26px] font-extrabold tracking-[-0.03em] tnum mx-1 mt-1">{won(stocks + (cashKrw ?? 0))}</div>
      <div className="grid grid-cols-[auto_1fr] gap-x-3 text-[13px] tnum mx-1 mt-1">
        <span className="text-muted-foreground">총 손익</span>
        <span className={cn('font-semibold', dirClass(profit))}>{signedWon(profit) + pct(profitRate)}</span>
        <span className="text-muted-foreground">오늘</span>
        <span className={cn('font-semibold', dirClass(daily))}>{signedWon(daily) + pct(dailyRate)}</span>
      </div>

      <div className="grid grid-cols-2 gap-2 mt-4">
        <div className="bg-muted rounded-xl px-3 py-2.5">
          <div className="text-xs text-muted-foreground">주식</div>
          <div className="text-sm font-bold tnum">{won(stocks)}</div>
        </div>
        <div className="bg-muted rounded-xl px-3 py-2.5" title={cash ? `원화 ${won(cash.KRW ?? 0)} · 달러 $${(cash.USD ?? 0).toLocaleString('en-US')}` : '현금 조회 실패'}>
          <div className="text-xs text-muted-foreground">현금 (주문 가능)</div>
          <div className="text-sm font-bold tnum">{cashKrw == null ? '-' : won(cashKrw)}</div>
        </div>
      </div>

      <div className="text-[13px] font-bold mx-1 mt-5 mb-1">{`보유 종목 ${positions.length}`}</div>
      {positions.map(p => (
        <div key={p.symbol} className="flex justify-between items-center gap-2 px-3 py-2.5 -mx-1 rounded-xl cursor-pointer transition-colors hover:bg-white/10"
          onClick={() => select({ stockCode: p.symbol })}
          title={p.lastPrice == null ? undefined : `현재가 ${p.currency === 'USD' ? '$' + p.lastPrice : won(p.lastPrice)} · 평단 ${p.currency === 'USD' ? '$' + p.averagePrice : won(p.averagePrice)}`}>
          <div className="min-w-0">
            <div className="text-sm font-semibold truncate">{p.name}</div>
            <div className="text-xs text-muted-foreground tnum">{`${p.quantity.toLocaleString('en-US')}주`}{stocks && p.value != null ? ` · ${(p.value / stocks * 100).toFixed(1)}%` : ''}</div>
          </div>
          <div className="text-right whitespace-nowrap tnum">
            <div className="text-sm font-semibold">{p.value == null ? '-' : won(p.value)}</div>
            <div className={cn('text-xs', dirClass(p.profitRate))}>
              {p.profit == null || p.profitRate == null ? '' : `${signedWon(p.profit)} (${signedPct(p.profitRate)})`}
            </div>
          </div>
        </div>
      ))}
    </Card>
  )
}
