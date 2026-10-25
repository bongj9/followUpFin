import { useEffect, useState } from 'react'
import { getInvestors, getOrderbook, getTrades, isKoreanCode, onStream, type InvestorFlow, type Orderbook, type Quote, type Trade } from '@/api'

export interface Side { book: Orderbook | null; trades: Trade[]; investors: InvestorFlow[] | null; error: string }

// 호가·체결·투자자. 처음엔 REST로 받고 이후는 SSE(orderbook, trade)로 갱신
// 호가·체결은 초당 여러 번 올 수 있어 화면은 한 프레임에 한 번만 다시 그린다
export function useSide(code: string): Side {
  const [side, setSide] = useState<Side>({ book: null, trades: [], investors: null, error: '' })
  useEffect(() => {
    let s: Side = { book: null, trades: [], investors: null, error: '' }
    let frame = 0, stale = false
    const flush = () => { frame ||= requestAnimationFrame(() => { frame = 0; setSide({ ...s }) }) }
    const offTrade = onStream<Quote>('trade', q => {
      if (q.stockCode !== code) return
      s.trades = [{ price: q.lastPrice, volume: q.volume ?? 0, timestamp: q.timestamp, currency: q.currency }, ...s.trades].slice(0, 50)
      flush()
    })
    const offBook = onStream<{ stockCode: string; book: Orderbook }>('orderbook', ({ stockCode, book }) => {
      if (stockCode !== code) return // 보고 있는 종목만 그린다
      s.book = book
      flush()
    })
    Promise.all([getOrderbook(code), getTrades(code),
      // 투자자별 매매동향은 국내 종목만 있다
      isKoreanCode(code) ? getInvestors(code) : null]).then(([book, trades, investors]) => {
      if (stale) return // 그사이 다른 종목을 골랐다
      s.investors = investors
      if (!book && !trades) s.error = '토스 시세를 불러올 수 없어요 (secret.yml 토스 키·허용 IP 확인)'
      s.book = s.book ?? book // 기다리는 동안 SSE로 더 최신 호가가 왔으면 그걸 쓴다
      // 기다리는 동안 SSE로 온 체결은 스냅샷에도 들어 있을 수 있어, 그보다 오래된 것만 붙인다
      const oldestLive = s.trades.at(-1)?.timestamp
      const older = (trades ?? []).filter(t => !oldestLive || Date.parse(t.timestamp) < Date.parse(oldestLive))
      s.trades = [...s.trades, ...older].slice(0, 50)
      s = { ...s }
      setSide(s)
    })
    return () => { stale = true; offTrade(); offBook(); cancelAnimationFrame(frame) }
  }, [code])
  return side
}
