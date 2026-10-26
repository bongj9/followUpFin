// 서버 응답 모양 (src/main/java 의 컨트롤러·레코드). BigDecimal은 JSON 숫자로 온다

export interface Stock { stockCode: string; name: string; industry: string | null; followedAt: string; korean: boolean }

// change: 전일 종가 대비 등락폭, changeRate: 등락률(%). 전일 종가를 모르면 둘 다 null
// volume: 실시간 체결 한 건의 수량 (현재가 조회로 만든 시세는 null)
export interface Quote {
  stockCode: string; lastPrice: number; prevClose: number | null; change: number | null; changeRate: number | null
  currency: string; timestamp: string; bigMove: boolean; volume: number | null
}

export interface Indicator { label: string; last: number | null; changeRate: number | null; spark: number[] }

// time은 KST로 옮긴 초 단위 (서버가 이미 옮겨서 줌)
export interface Bar { time: number; open: number; high: number; low: number; close: number; volume: number }

export type FeedType = 'NEWS' | 'DISCLOSURE' | 'MOVE' | 'MARKET'
// importance·tip은 공시에만, newsId는 번역할 수 있는 영어 기사에만, originalTitle은 번역한 기사의 원래 제목
export interface FeedItem {
  type: FeedType; stockCode: string; stockName: string; source: string | null; title: string; summary: string
  url: string | null; at: string; importance: string | null; tip: string | null; newsId: number | null; originalTitle: string | null
}
export interface DisclosureList { items: FeedItem[]; nextOffset: number | null }

// 호가. asks는 낮은 가격순, bids는 높은 가격순
export interface Level { price: number; volume: number }
export interface Orderbook { timestamp: string; currency: string; asks: Level[]; bids: Level[] }
export interface Trade { price: number; volume: number; timestamp: string; currency: string }

// 하루치 순매수 수량(주). 당일 잠정치는 개인·기타법인이 null
export interface InvestorFlow {
  date: string; individual: number | null; foreigner: number | null; institution: number | null
  otherCorporation: number | null; foreignerHoldingRate: number | null
}
// 시장 전체 순매수 금액(원), 오래된 순
export interface MarketFlow { date: string; individual: number; foreigner: number; institution: number }
export interface ImportResult { added: string[]; failed: string[] }

// 알상무 노트 (views/alsangmu.json, git 제외)
export interface Cited { text: string; cite: string }
export interface NoteLevel { price: number; text: string; cite: string }
export interface Daily {
  date: string; id: string; title: string; headline: string; show?: string
  stance?: { cash?: string | null; odds?: string | null; action?: string | null }
  chart?: Cited[]; points?: Cited[]; levels?: NoteLevel[]; stocks?: Record<string, Cited[]>
  guest?: { name: string; firm?: string; headline: string; points: Cited[] } | null // 위폴 버디버디 게스트
}
export interface Note {
  name: string; source: string; asOf: string; videos: Record<string, string>; summary: string
  steps: { key: string; title: string; states: Record<string, { read: string; cite: string }> }[]
  levels: NoteLevel[]; rules: Cited[]; warnings: Cited[]; track: string
  aliases?: Record<string, string>
  stocks?: Record<string, { summary?: string; items?: Cited[]; levels?: NoteLevel[] }>
  daily?: Daily[]
}

// 아침 리포트 (views/report.json, git 제외: 보유 비중이 들어 있어 볼트에 둔다). reports는 최신이 앞
// 숫자는 scripts/report-facts.ts가 뽑고, 판단(action·why·watch)은 아침 리포트 스킬이 쓴다
export interface ReportCall { if: string; then: string }
export interface ReportHolding {
  code: string; name: string; action: string; weightPct: number | null; targetPct?: number | null
  why: string[]; watch: ReportCall[]; alsangmu?: string | null
}
export interface Report {
  date: string; generatedAt: string; headline: string
  market: { stance: string; cashPct?: number | null; why: string[]; watch: ReportCall[] }
  holdings: ReportHolding[]
  alsangmu?: { date: string; show: string; headline: string } | null
  review?: { date: string; call: string; result: string }[]
}

// 서버가 준 오류 메시지 (없으면 상태 코드)
export const errorText = async (res: Response) => (await res.json().catch(() => null))?.message ?? String(res.status)

const json = <T>(url: string, fallback: T): Promise<T> =>
  fetch(url).then(r => (r.ok ? r.json() : fallback)).catch(() => fallback)

export const isKoreanCode = (code: string) => /^\d{6}$/.test(code)
const sym = (code: string) => '?symbol=' + encodeURIComponent(code)

export const getStocks = (): Promise<Stock[]> => fetch('/api/stocks').then(r => r.json())
export const getQuotes = () => json<Quote[]>('/api/quotes', [])
export const getIndicators = () => json<Indicator[]>('/api/indicators', [])
export const getNote = () => json<Note | null>('/views/alsangmu.json', null)
export const getShow = (show: string) => json<{ name: string; source: string; asOf: string; aliases?: Record<string, string>; daily: Daily[] } | null>(`/views/${show}.json`, null)
export const getReports = () => json<{ reports: Report[] } | null>('/views/report.json', null)
export const getOrderbook = (code: string) => json<Orderbook | null>('/api/orderbook' + sym(code), null)
export const getTrades = (code: string) => json<Trade[] | null>('/api/trades' + sym(code), null)
export const getInvestors = (code: string) => json<InvestorFlow[]>('/api/investors' + sym(code), [])

export async function getBars(code: string, interval: string): Promise<Bar[] | null> {
  const res = await fetch('/api/candles' + sym(code) + '&interval=' + interval)
  if (!res.ok) return null
  return (await res.json() as Bar[]).map(b => ({ time: b.time, open: +b.open, high: +b.high, low: +b.low, close: +b.close, volume: +b.volume }))
}

export const getFeed = (query: string): Promise<FeedItem[]> => fetch('/api/feed' + query).then(r => r.json())

// 홈에 돌아올 때마다 토스를 부르지 않게 10분 기억한다. 실패는 기억하지 않는다
const flowCache = new Map<string, { at: number; flows: Promise<MarketFlow[]> }>()
export function getFlows(market: string) {
  let hit = flowCache.get(market)
  if (!hit || Date.now() - hit.at > 600_000) {
    hit = { at: Date.now(), flows: json<MarketFlow[]>('/api/market-flows' + sym(market), []) }
    flowCache.set(market, hit)
    hit.flows.then(f => { if (!f.length) flowCache.delete(market) })
  }
  return hit.flows
}

// 서버가 토스 실시간 체결(trade)·호가(orderbook)·큰 변동(move)을 SSE로 넘겨준다. 끊기면 EventSource가 알아서 다시 붙는다
let source: EventSource | null = null
export function stream() {
  return source ??= new EventSource('/api/stream')
}
export function onStream<T>(event: 'trade' | 'orderbook' | 'move', fn: (data: T) => void) {
  const listener = (ev: MessageEvent) => fn(JSON.parse(ev.data || 'null'))
  stream().addEventListener(event, listener)
  return () => stream().removeEventListener(event, listener)
}
