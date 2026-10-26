import { useCallback, useEffect, useRef, useState } from 'react'
import { getQuotes, getStocks, onStream, stream, type Quote, type Stock } from '@/api'
import { AlsangmuView } from '@/components/AlsangmuView'
import { Feed } from '@/components/Feed'
import { FlowChart } from '@/components/FlowChart'
import { Investors } from '@/components/Investors'
import { MorningReport } from '@/components/MorningReport'
import { OrderBook } from '@/components/OrderBook'
import { ShowView } from '@/components/ShowView'
import { ViewsBoard } from '@/components/ViewsBoard'
import { StockChart } from '@/components/StockChart'
import { StockHead } from '@/components/StockHead'
import { Ticker } from '@/components/Ticker'
import { Trades } from '@/components/Trades'
import { Watchlist } from '@/components/Watchlist'
import { useSide } from '@/components/useSide'
import { Card, CardTitle } from '@/components/ui/card'
import { cn } from '@/lib/format'

// selected: null(전체) | { stockCode } | { industry }
export type Selected = { stockCode: string; industry?: undefined } | { industry: string; stockCode?: undefined } | null
export type Quotes = Record<string, Quote>

// 주소로 화면을 정한다 (#/, #/stock/005930, #/industry/이름). 새로고침·뒤로가기·링크 공유가 된다
function parseHash(): Selected {
  const [, kind, value] = location.hash.match(/^#\/(stock|industry)\/(.+)$/) ?? []
  const v = value && decodeURIComponent(value)
  return kind === 'stock' ? { stockCode: v } : kind === 'industry' ? { industry: v } : null
}

export default function App() {
  const [selected, setSelected] = useState(parseHash)
  const [tick, setTick] = useState(0) // 오를 때마다 목록·피드·지표를 다시 받는다 (1분마다, 종목 추가·삭제 뒤)
  const refresh = useCallback(() => setTick(t => t + 1), [])
  const [stocks, setStocks] = useState<Stock[]>([])
  const [quotes, setQuotes] = useState<Quotes>({}) // 종목 → 최신 시세 (/api/quotes + 실시간 체결)
  const [live, setLive] = useState('실시간 연결 중…')
  const [updated, setUpdated] = useState('')

  const select = useCallback((target: Selected) => {
    const hash = target?.stockCode ? '#/stock/' + encodeURIComponent(target.stockCode)
      : target?.industry ? '#/industry/' + encodeURIComponent(target.industry) : '#/'
    if (location.hash === hash) refresh(); else location.hash = hash // 바뀌면 hashchange가 refresh
  }, [refresh])

  useEffect(() => {
    const onHash = () => { setSelected(parseHash()); window.scrollTo(0, 0); refresh() }
    window.addEventListener('hashchange', onHash)
    const timer = setInterval(refresh, 60_000)
    return () => { window.removeEventListener('hashchange', onHash); clearInterval(timer) }
  }, [refresh])

  // 시세가 실패해도 종목 목록은 보여준다
  useEffect(() => {
    Promise.all([getStocks(), getQuotes()]).then(([list, latest]) => {
      setStocks(list)
      setQuotes(q => ({ ...q, ...Object.fromEntries(latest.map(x => [x.stockCode, x])) }))
      setUpdated(new Date().toLocaleTimeString('ko-KR') + ' 갱신')
    })
  }, [tick])

  // 체결은 초당 여러 번 올 수 있어 시세는 한 프레임에 한 번만 반영한다
  const pendingQuotes = useRef<Quotes>({})
  useEffect(() => {
    const s = stream()
    s.onopen = () => setLive('● 실시간')
    s.onerror = () => setLive('실시간 재연결 중…')
    let frame = 0
    const off = onStream<Quote>('trade', q => {
      pendingQuotes.current[q.stockCode] = q
      frame ||= requestAnimationFrame(() => {
        frame = 0
        const batch = pendingQuotes.current
        pendingQuotes.current = {}
        setQuotes(prev => ({ ...prev, ...batch }))
      })
    })
    return () => { off(); cancelAnimationFrame(frame) }
  }, [])

  const code = selected?.stockCode
  return (
    <>
      <header className="sticky top-0 z-20 px-6 py-3.5 max-[720px]:px-4 max-[720px]:py-3 bg-white/80 backdrop-blur-lg backdrop-saturate-[180%] flex justify-between items-center gap-2">
        <h1 className="text-xl font-extrabold tracking-[-0.03em] cursor-pointer" onClick={() => select(null)}>종목 브리핑</h1>
        <span className="text-xs text-muted-foreground">{live}<span className="max-[720px]:hidden"> · <span className="text-[13px]">{updated}</span></span></span>
      </header>
      <Ticker tick={tick} />
      {/* 두 페이지. 홈: 관심종목 | 뉴스·공시 / 종목: 헤더, 차트 | 이 종목 뉴스·공시, 아래 투자자·호가·체결 */}
      <main className={cn('grid gap-5 px-6 pt-5 pb-10 max-w-[1440px] mx-auto items-start max-[720px]:px-4 max-[720px]:py-3', code ? 'page-stock' : 'page-home')}>
        {code
          ? <StockPage key={code} code={code} stocks={stocks} quotes={quotes} tick={tick} select={select} />
          : <>
              <Watchlist className="[grid-area:watch]" stocks={stocks} quotes={quotes} selected={selected} select={select} refresh={refresh} />
              <ViewsBoard tick={tick} />
              <MorningReport tick={tick} />
              <FlowChart className="[grid-area:flow]" tick={tick} />
              <AlsangmuView code={null} stocks={stocks} tick={tick} />
              <Feed className="[grid-area:feed]" selected={selected} tick={tick} select={select} />
            </>}
      </main>
    </>
  )
}

function StockPage({ code, stocks, quotes, tick, select }: { code: string; stocks: Stock[]; quotes: Quotes; tick: number; select: (s: Selected) => void }) {
  const side = useSide(code)
  const quote = quotes[code]
  return (
    <>
      <StockHead className="[grid-area:head]" code={code} stock={stocks.find(s => s.stockCode === code)} quote={quote} select={select} />
      <Card className="[grid-area:chart]"><StockChart code={code} stocks={stocks} /></Card>
      <AlsangmuView code={code} stocks={stocks} tick={tick} />
      <ShowView show="shuka" code={code} tick={tick} />
      <ShowView show="wepoll" code={code} tick={tick} />
      {/* 차트를 내려 봐도 뉴스는 옆에 남는다 */}
      <Feed className="[grid-area:feed] min-[1101px]:sticky min-[1101px]:top-4 min-[1101px]:max-h-[calc(100vh-32px)] min-[1101px]:overflow-y-auto"
        selected={{ stockCode: code }} tick={tick} select={select} />
      <div className="[grid-area:below] grid grid-cols-[repeat(auto-fit,minmax(280px,1fr))] gap-4 items-start">
        <Investors list={side.investors} />
        <Card><CardTitle>호가</CardTitle><OrderBook side={side} quote={quote} /></Card>
        <Card><CardTitle>체결</CardTitle><Trades side={side} /></Card>
      </div>
    </>
  )
}
