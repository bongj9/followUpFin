import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { ChartBar, House, Newspaper, Play, Star, Sunrise, Users, Wallet } from 'lucide-react'
import { getQuotes, getStocks, onStream, stream, type Quote, type Stock } from '@/api'
import { Account, useAccount, won } from '@/components/Account'
import { AlsangmuView } from '@/components/AlsangmuView'
import { Feed } from '@/components/Feed'
import { FlowChart } from '@/components/FlowChart'
import { Investors } from '@/components/Investors'
import { MorningReport, ReportHero } from '@/components/MorningReport'
import { OrderBook } from '@/components/OrderBook'
import { Consensus, EpisodeDrawer, Mentions, ShowCard, ShowsPage } from '@/components/Shows'
import { StockChart } from '@/components/StockChart'
import { StockHead } from '@/components/StockHead'
import { Ticker } from '@/components/Ticker'
import { Trades } from '@/components/Trades'
import { Watchlist } from '@/components/Watchlist'
import { useSide } from '@/components/useSide'
import { Card, CardTitle } from '@/components/ui/card'
import { cn, dirClass, signedPct } from '@/lib/format'
import { useShows, type Episode } from '@/lib/shows'

// selected: null(전체) | { stockCode } | { industry }
export type Selected = { stockCode: string; industry?: undefined } | { industry: string; stockCode?: undefined } | null
export type Quotes = Record<string, Quote>

// 주소로 화면을 정한다: #/ 개요, #/watch 관심 종목, #/shows 영상 요약, #/report 아침 리포트, #/feed 피드, #/account 계좌,
// #/stock/005930 종목, #/industry/이름 업종 피드. 새로고침·뒤로가기·링크 공유가 된다
const PAGES = [['', '개요', House], ['watch', '관심 종목', Star], ['shows', '영상 요약', Play], ['report', '아침 리포트', Sunrise],
  ['feed', '피드', Newspaper], ['account', '계좌', Wallet]] as const
type Page = typeof PAGES[number][0] | 'stock' | 'industry'
function parseHash(): { page: Page; selected: Selected } {
  const [, kind = '', value] = location.hash.match(/^#\/(\w*)(?:\/(.+))?$/) ?? []
  const v = value && decodeURIComponent(value)
  if (kind === 'stock' && v) return { page: 'stock', selected: { stockCode: v } }
  if (kind === 'industry' && v) return { page: 'industry', selected: { industry: v } }
  return { page: (PAGES.some(([k]) => k === kind) ? kind : '') as Page, selected: null }
}

export default function App() {
  const [{ page, selected }, setRoute] = useState(parseHash)
  const [tick, setTick] = useState(0) // 오를 때마다 목록·피드·지표를 다시 받는다 (1분마다, 종목 추가·삭제 뒤)
  const refresh = useCallback(() => setTick(t => t + 1), [])
  const [stocks, setStocks] = useState<Stock[]>([])
  const [quotes, setQuotes] = useState<Quotes>({}) // 종목 → 최신 시세 (/api/quotes + 실시간 체결)
  const [live, setLive] = useState('실시간 연결 중…')
  const [updated, setUpdated] = useState('')
  const [ep, setEp] = useState<Episode | null>(null) // 오른쪽 패널에 연 방송
  const closeEp = useCallback(() => setEp(null), [])

  const select = useCallback((target: Selected) => {
    const hash = target?.stockCode ? '#/stock/' + encodeURIComponent(target.stockCode)
      : target?.industry ? '#/industry/' + encodeURIComponent(target.industry) : '#/'
    if (location.hash === hash) refresh(); else location.hash = hash // 바뀌면 hashchange가 refresh
  }, [refresh])

  useEffect(() => {
    const onHash = () => { setRoute(parseHash()); setEp(null); window.scrollTo(0, 0); refresh() }
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

  const account = useAccount(tick)
  const { eps, aliases } = useShows(tick)
  const tab = page === 'stock' ? 'watch' : page === 'industry' ? 'feed' : page
  const code = selected?.stockCode

  return (
    <>
      <header className="flex items-center gap-4 px-7 pt-[18px] pl-[76px] max-[720px]:px-4 max-[720px]:flex-wrap">
        <a href="#/" className="flex items-center gap-2 font-bold text-[15px] w-40 max-[720px]:w-auto">
          <i className="grid place-items-center w-[26px] h-[26px] rounded-lg bg-primary text-primary-foreground not-italic text-sm">◆</i>followUpFin</a>
        <nav className="glass rounded-full flex gap-0.5 p-1 mx-auto overflow-x-auto max-[720px]:order-3 max-[720px]:w-full">
          {PAGES.map(([k, t]) => <a key={k} href={'#/' + k}
            className={cn('px-3.5 py-[7px] rounded-full text-xs whitespace-nowrap', k === tab ? 'bg-white/10 text-foreground shadow-[inset_0_0_0_1px_var(--color-border)]' : 'text-muted-foreground hover:text-foreground')}>{t}</a>)}
        </nav>
        <span className="text-[11px] text-muted-foreground text-right">{live}<span className="block max-[720px]:hidden">{updated}</span></span>
      </header>
      <nav className="glass fixed left-4 top-1/2 -translate-y-1/2 z-10 flex flex-col gap-1.5 px-[5px] py-2 rounded-full max-[720px]:hidden">
        {PAGES.map(([k, t, Icon]) => <a key={k} href={'#/' + k} title={t}
          className={cn('grid place-items-center w-[30px] h-[30px] rounded-full', k === tab ? 'bg-white/10 text-foreground' : 'text-muted-foreground hover:text-foreground')}><Icon size={15} /></a>)}
      </nav>

      <main className="px-7 pt-[22px] pb-12 pl-[76px] max-w-[1480px] max-[720px]:px-4">
        {page === 'stock' && code
          ? <div className="grid gap-5 items-start page-stock">
              <StockPage key={code} code={code} stocks={stocks} quotes={quotes} tick={tick} select={select} eps={eps} aliases={aliases} open={setEp} />
            </div>
          : <div className="flex flex-col gap-5">
              <Head page={tab} account={account} stocks={stocks} />
              <Ticker tick={tick} />
              {page === '' && <>
                <ReportHero tick={tick} account={account} />
                <Section title="오늘의 시황 뷰" sub="카드를 누르면 전체 요약 · 그들의 말을 옮긴 것이고 판단은 본인 몫이에요">
                  <div className="grid grid-cols-[repeat(auto-fill,minmax(320px,1fr))] gap-3.5">
                    {(['alsangmu', 'shuka', 'wepoll'] as const).map(s => <ShowCard key={s} src={s} eps={eps} stocks={stocks} open={setEp} />)}
                  </div>
                </Section>
                <Section title="여럿이 같이 언급한 종목" sub="최근 7일 · 누르면 종목별 발언"><Consensus eps={eps} stocks={stocks} /></Section>
                <Section title="알상무 코스피 판정" sub="알상무 차트 규칙으로 본 오늘 코스피"><AlsangmuView code={null} stocks={stocks} tick={tick} /></Section>
              </>}
              {page === 'watch' && <Watchlist stocks={stocks} quotes={quotes} positions={account.positions} eps={eps} aliases={aliases} select={select} refresh={refresh} />}
              {page === 'shows' && <ShowsPage eps={eps} stocks={stocks} open={setEp} />}
              {page === 'report' && <MorningReport tick={tick} account={account} />}
              {(page === 'feed' || page === 'industry') && <div className="grid grid-cols-[minmax(0,1fr)_minmax(0,1.4fr)] max-[1100px]:grid-cols-1 gap-5 items-start">
                <FlowChart tick={tick} />
                <Feed selected={selected} tick={tick} select={select} />
              </div>}
              {page === 'account' && <Account className="max-w-[560px]" account={account} select={select} />}
            </div>}
      </main>
      <EpisodeDrawer ep={ep} stocks={stocks} close={closeEp} />
    </>
  )
}

const Section = ({ title, sub, children }: { title: string; sub: string; children: ReactNode }) =>
  <section><h2 className="text-[15px] font-semibold tracking-[-0.01em] mb-3">{title}<small className="text-[11px] font-normal text-muted-foreground ml-2">{sub}</small></h2>{children}</section>

const TITLES: Record<string, string> = { '': '오늘의 브리핑', watch: '관심 종목 보드', shows: '영상 요약', report: '아침 리포트', feed: '뉴스·공시', account: '내 계좌' }

// 페이지 제목 + 핵심 숫자 3개 (팔로우 종목, 계좌 평가금액·오늘, 누적 손익)
function Head({ page, account, stocks }: { page: string; account: ReturnType<typeof useAccount>; stocks: Stock[] }) {
  const stat = (Icon: typeof Users, main: ReactNode, sub: string, chip?: ReactNode) =>
    <div className="flex items-center gap-3"><div className="glass grid place-items-center w-10 h-10 rounded-full text-muted-foreground"><Icon size={15} /></div>
      <div><b className="text-[17px] font-semibold tnum">{main}</b>{chip}<small className="block text-[11px] text-muted-foreground">{sub}</small></div></div>
  const chip = (text: string, tone: string) => <span className={cn('text-[10px] font-semibold px-[7px] py-0.5 rounded-full ml-1.5 align-[3px]', tone)}>{text}</span>
  const held = account.positions.filter(p => p.quantity >= 1).length
  return <section className="flex items-center gap-9 flex-wrap">
    <h1 className="text-[26px] font-semibold leading-[1.12] tracking-[-0.02em] w-40 whitespace-pre-line">{TITLES[page].replace(' ', '\n')}</h1>
    {stat(Users, stocks.length, '팔로우 중인 종목', held ? chip(`보유 ${held}`, 'bg-down text-[#0b1530]') : undefined)}
    {account.ready && <>
      {stat(Wallet, won(account.stocks + (account.cashKrw ?? 0)), '계좌 총자산 · 오늘',
        account.dailyRate == null ? undefined : chip(signedPct(account.dailyRate), account.dailyRate >= 0 ? 'bg-primary text-primary-foreground' : 'bg-down text-[#0b1530]'))}
      {stat(ChartBar, <span className={dirClass(account.profit)}>{(account.profit > 0 ? '+' : '') + won(account.profit)}{account.profitRate != null && ' · ' + signedPct(account.profitRate)}</span>, '누적 손익 (매입가 대비)')}
    </>}
  </section>
}

function StockPage({ code, stocks, quotes, tick, select, eps, aliases, open }: {
  code: string; stocks: Stock[]; quotes: Quotes; tick: number; select: (s: Selected) => void
  eps: Episode[]; aliases: Record<string, string>; open: (ep: Episode) => void
}) {
  const side = useSide(code)
  const quote = quotes[code]
  return (
    <>
      <StockHead className="[grid-area:head]" code={code} stock={stocks.find(s => s.stockCode === code)} quote={quote} select={() => { location.hash = '#/watch' }} />
      <Card className="[grid-area:chart]"><StockChart code={code} stocks={stocks} /></Card>
      <AlsangmuView code={code} stocks={stocks} tick={tick} />
      <Mentions className="[grid-area:said]" code={code} eps={eps} aliases={aliases} open={open} />
      {/* 차트를 내려 봐도 뉴스는 옆에 남는다 */}
      <Feed className="[grid-area:feed] min-[1101px]:sticky min-[1101px]:top-4 min-[1101px]:max-h-[calc(100vh-32px)] min-[1101px]:overflow-y-auto"
        selected={{ stockCode: code }} tick={tick} select={select} followed={stocks.some(s => s.stockCode === code)} />
      <div className="[grid-area:below] grid grid-cols-[repeat(auto-fit,minmax(280px,1fr))] gap-4 items-start">
        <Investors list={side.investors} />
        <Card><CardTitle>호가</CardTitle><OrderBook side={side} quote={quote} /></Card>
        <Card><CardTitle>체결</CardTitle><Trades side={side} /></Card>
      </div>
    </>
  )
}
