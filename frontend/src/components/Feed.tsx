import { useEffect, useState } from 'react'
import { errorText, getFeed, onStream, type DisclosureList, type FeedItem as Item, type FeedType } from '@/api'
import type { Selected } from '@/App'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardTitle } from '@/components/ui/card'
import { Tabs } from '@/components/ui/tabs'
import { cn } from '@/lib/format'

const LABEL: Record<FeedType, string> = { NEWS: '뉴스', DISCLOSURE: '공시', MOVE: '변동', MARKET: '시장' }
// 피드 탭 (토스 종목 화면 아래 탭처럼)
const TABS = [['NEWS', '뉴스'], ['DISCLOSURE', '공시'], ['MOVE', '급등락'], ['MARKET', '시장']] as const
const empty = 'text-muted-foreground p-6 text-center'

export function Feed({ selected, tick, select, className }: { selected: Selected; tick: number; select: (s: Selected) => void; className?: string }) {
  const [feedType, setFeedType] = useState<FeedType>('NEWS')
  const [nonce, setNonce] = useState(0) // 탭을 다시 누르면 새로 받는다
  const [moves, setMoves] = useState(0) // 큰 변동(SSE move)이 오면 다시 받는다
  const [items, setItems] = useState<Item[] | null>(null)
  const code = selected?.stockCode
  const disclosures = !!code && feedType === 'DISCLOSURE'
  useEffect(() => onStream('move', () => setMoves(m => m + 1)), [])

  // 시장 뉴스는 종목과 상관없어서 종목·업종 화면에서도 홈과 같은 목록을 받는다
  const query = feedType === 'MARKET' ? '' : code ? '?stockCode=' + encodeURIComponent(code)
    : selected?.industry ? '?industry=' + encodeURIComponent(selected.industry) : ''
  useEffect(() => {
    if (disclosures) return
    let stale = false
    getFeed(query).then(list => { if (!stale) setItems(list.filter(i => i.type === feedType)) })
    return () => { stale = true }
  }, [disclosures, query, feedType, tick, moves, nonce])

  const title = disclosures ? '최근 3개월 공시' : code ? '뉴스·공시' : selected ? selected.industry + ' 뉴스·공시' : '전체 뉴스·공시'
  return (
    <Card className={className}>
      <CardTitle className="mt-0">{title}</CardTitle>
      <Tabs items={TABS} value={feedType} onChange={t => { setFeedType(t); setItems(null); setNonce(n => n + 1) }} />
      {disclosures ? <Disclosures code={code} nonce={nonce} select={select} />
        : items && (items.length
          ? items.map((i, k) => <FeedItem key={(i.url ?? i.title) + k} item={i} select={select} />)
          : <div className={empty}>이 탭에 해당하는 항목이 없어요.</div>)}
    </Card>
  )
}

// 종목 페이지 공시 탭: 최근 3개월 공시를 DART(국내)·SEC(미국)에서 2건 먼저, '더 보기'로 10건씩 더 받는다
// 1분마다 도는 갱신에서 펼친 목록이 접히지 않도록 갱신(tick)과 무관하게 들고 있는다 (탭을 다시 누르면 새로 받음)
// 임원 지분 보고 같은 사소한 공시는 기본으로 숨긴다 (토글로 보기)
function Disclosures({ code, nonce, select }: { code: string; nonce: number; select: (s: Selected) => void }) {
  const [showMinor, setShowMinor] = useState(false)
  const [disc, setDisc] = useState<DisclosureList | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  async function load(offset: number, limit: number, keep: Item[], signal?: { stale: boolean }) {
    setLoading(true)
    const res = await fetch(`/api/disclosures?stockCode=${code}&offset=${offset}&limit=${limit}&minor=${showMinor}`)
    if (signal?.stale) return // 받는 사이 다른 종목으로 이동
    setLoading(false)
    if (!res.ok) { setError('공시를 불러오지 못했어요. (' + await errorText(res) + ')'); return }
    const page: DisclosureList = await res.json()
    setError('')
    setDisc({ items: [...keep, ...page.items], nextOffset: page.nextOffset })
  }
  useEffect(() => {
    const signal = { stale: false }
    setDisc(null)
    setError('')
    load(0, 2, [], signal)
    return () => { signal.stale = true }
  }, [code, nonce, showMinor])

  if (error) return <div className={empty}>{error}</div>
  if (!disc) return null
  return <>
    <label className="flex items-center gap-1.5 px-1.5 py-2 text-[13px] text-muted-foreground">
      <input type="checkbox" checked={showMinor} onChange={e => setShowMinor(e.target.checked)} />
      사소한 공시도 보기 (임원 지분 보고, 증권 발행 서류 등)
    </label>
    {disc.items.length ? disc.items.map((i, k) => <FeedItem key={(i.url ?? i.title) + k} item={i} select={select} />)
      : <div className={empty}>{showMinor ? '최근 3개월 동안 나온 공시가 없어요.' : '최근 3개월 동안 눈여겨볼 공시가 없어요.'}</div>}
    {disc.nextOffset != null && <Button variant="soft" size="lg" className="mt-2" disabled={loading}
      onClick={() => load(disc.nextOffset!, 10, disc.items)}>공시 더 보기</Button>}
  </>
}

function FeedItem({ item, select }: { item: Item; select: (s: Selected) => void }) {
  const [i, setI] = useState(item)
  const [translating, setTranslating] = useState<string | null>(null)
  useEffect(() => setI(item), [item])

  // 영어 기사: '번역'을 누르면 서버가 DeepL로 번역해 저장한다 (다음부터는 피드가 번역문을 준다)
  async function translate() {
    setTranslating('번역 중…')
    const res = await fetch(`/api/news/${i.newsId}/translate`, { method: 'POST' })
    if (!res.ok) { setTranslating('번역 실패: ' + await errorText(res)); return }
    const t: Item = await res.json()
    setI({ ...i, title: t.title, summary: t.summary, originalTitle: t.originalTitle })
    setTranslating(null)
  }

  const move = i.type === 'MOVE'
  return (
    <div className={cn('px-2 py-3.5 border-b border-muted last:border-b-0', move && 'bg-warn-soft rounded-xl border-b-transparent mb-1 cursor-pointer')}
      // 변동 항목을 누르면 그 종목 피드(관련 뉴스)로 이동
      onClick={move ? () => select({ stockCode: i.stockCode }) : undefined}>
      <div className="text-xs text-muted-foreground flex gap-2 items-center">
        <Badge variant={i.type}>{LABEL[i.type]}</Badge>
        {i.importance === 'HIGH' && <Badge variant="HIGH">중요</Badge>}
        <span>{i.stockName}</span>
        <span>{i.source ?? ''}</span>
        {/* DART는 접수 시각을 주지 않아 날짜만 (SEC는 초 단위 접수 시각이 있어 시각까지) */}
        <span>{new Date(i.at).toLocaleString('ko-KR', i.source === 'DART' ? { month: 'numeric', day: 'numeric' } : { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })}</span>
        {i.newsId && !i.originalTitle && <Button variant="soft" size="sm" className="ml-auto font-normal" disabled={!!translating} onClick={translate}>{translating ?? '번역'}</Button>}
      </div>
      {i.url
        ? <a className="block font-semibold my-1 hover:text-primary" href={i.url} target="_blank" rel="noopener noreferrer">{i.title}</a>
        : <div className={cn('my-1', move && 'font-bold')}>{i.title}</div>}
      {i.originalTitle && <p className="mt-0.5 mb-1 text-xs text-muted-foreground">원문: {i.originalTitle}</p>}
      <p className="text-[13px] text-muted-foreground">{i.summary}</p>
      {i.tip && <p className="mt-2 px-3 py-2 bg-[#fefce8] rounded-[10px] text-[#713f12] text-[13px]">볼 것: {i.tip}</p>}
    </div>
  )
}
