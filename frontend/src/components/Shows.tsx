import { Fragment, useEffect, useState, type ReactNode } from 'react'
import { X } from 'lucide-react'
import type { Cited, Stock } from '@/api'
import { yt } from '@/components/AlsangmuView'
import { Card } from '@/components/ui/card'
import { cn, fmt } from '@/lib/format'
import { biasBoard, biasByDate, consensus, dateLabel, nameOf, saidFor, WHO, type Episode, type Src } from '@/lib/shows'

// 방송 요약 화면들 (views/*.json, git 제외: 실존 인물 영상을 편마다 요약한 개인 공부 노트). 그들의 말을 옮긴 것이고 판단은 본인 몫
const go = (code: string) => { location.hash = '#/stock/' + encodeURIComponent(code) }
const chip = 'text-[11px] px-2.5 py-[3px] rounded-full bg-white/[0.07] whitespace-nowrap'

export function Avatar({ src, small }: { src: Src; small?: boolean }) {
  return <div title={WHO[src].who} style={{ background: WHO[src].color }}
    className={cn('grid place-items-center rounded-full font-bold text-[#1c1c1c] shrink-0',
      small ? 'w-[22px] h-[22px] text-[10px] border-2 border-[#1a1714]' : 'w-[38px] h-[38px] text-[11px]')}>{WHO[src].ini}</div>
}
export const Avatars = ({ list }: { list: Src[] }) =>
  <span className="flex [&>*+*]:-ml-[7px]">{list.map(s => <Avatar key={s} src={s} small />)}</span>

const CiteLink = ({ id, cite }: { id: string; cite?: string }) => cite
  ? <a className="cite" target="_blank" rel="noopener" href={yt(id, cite)} onClick={e => e.stopPropagation()}>{'▶ ' + cite}</a> : null
// 요약 아래에 그가 실제로 한 말 (왼쪽 줄로 요약과 구분)
const Quote = ({ q }: { q?: string }) => q
  ? <span className="block mt-1.5 pl-3 border-l-2 border-primary/40 text-[13px] leading-[1.6] text-muted-foreground">{`“${q}”`}</span> : null

function Stance({ ep, full }: { ep: Episode; full?: boolean }) {
  const rows = ([['분위기', ep.stance?.odds], ['대응', ep.stance?.action], ['현금', ep.stance?.cash]] as const).filter(([, v]) => v)
  if (!rows.length) return null
  // 상세 패널: 이름표 | 내용 두 칸으로 읽기 쉽게
  if (full) return <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-2 rounded-xl bg-white/[0.04] px-3.5 py-3 text-[13px] leading-[1.6]">
    {rows.map(([k, v]) => <Fragment key={k}><dt className="font-semibold text-muted-foreground whitespace-nowrap">{k}</dt><dd className="text-foreground/90">{v}</dd></Fragment>)}</dl>
  return <div className="flex flex-col gap-1.5">{rows.map(([k, v]) =>
    <div key={k} className="text-[11px] px-2.5 py-[7px] rounded-xl bg-white/[0.045] text-muted-foreground">
      <p className={cn(!full && 'line-clamp-2')}><b className="text-foreground mr-1.5">{k}</b>{v}</p></div>)}</div>
}

const topStocks = (ep: Episode, n: number) => Object.entries(ep.stocks ?? {}).sort((a, b) => (b[1]?.length ?? 0) - (a[1]?.length ?? 0)).slice(0, n).map(([c]) => c)

function Who({ ep, extra }: { ep: Episode; extra?: string }) {
  return <div className="flex gap-2.5 items-center min-w-0"><Avatar src={ep.src} />
    <div className="min-w-0"><b className="text-sm">{WHO[ep.src].who}</b>
      <div className="text-[11px] text-muted-foreground truncate">{[ep.show, dateLabel(ep.date), extra].filter(Boolean).join(' · ')}</div></div></div>
}

// 개요의 사람별 카드: 그 사람의 최신 방송 한 편
export function ShowCard({ src, eps, stocks, open }: { src: Src; eps: Episode[]; stocks: Stock[]; open: (ep: Episode) => void }) {
  const mine = eps.filter(e => e.src === src)
  const ep = mine[0]
  if (!ep) return null
  const same = mine.filter(e => e.date === ep.date).length
  const ago = Math.floor((Date.now() - +new Date(ep.date + 'T00:00:00')) / 864e5)
  return (
    <Card className="flex flex-col gap-3 cursor-pointer transition hover:-translate-y-0.5 hover:bg-white/[0.075]" onClick={() => open(ep)} role="button" tabIndex={0} onKeyDown={ev => ev.key === 'Enter' && open(ep)}>
      <Who ep={ep} extra={same > 1 ? `같은 날 ${same}편` : undefined} />
      {ago > 1 && <div className="text-[11px] text-warn">{`${ago}일 전 방송 · 새 방송 아직 없음`}</div>}
      <div className="text-[13px] leading-[1.5] line-clamp-3">{ep.headline}</div>
      {ep.guest && <div className="text-[11px] text-muted-foreground">게스트 <b className="text-foreground">{ep.guest.name}</b>{ep.guest.firm && ' · ' + ep.guest.firm}</div>}
      <Stance ep={ep} />
      <div className="flex flex-wrap gap-1.5">{topStocks(ep, 4).map(c => <span key={c} className={chip}>{nameOf(c, stocks)}</span>)}</div>
    </Card>
  )
}

const BIAS = { 롱: 'text-up bg-[var(--color-up)]/10', 숏: 'text-down bg-[var(--color-down)]/10', 중립: 'text-muted-foreground bg-white/[0.07]' } as const
const BiasTag = ({ b, h }: { b: keyof typeof BIAS; h?: string | null }) =>
  <span className={cn('text-[11px] font-bold px-2 py-[2px] rounded-full whitespace-nowrap', BIAS[b])}>{b}{h && <span className="font-normal opacity-80">{' ' + h}</span>}</span>
const md = (date: string) => date.slice(5).replace('-', '/')

// 사람별 롱·숏: 진행자 셋 + 게스트·패널, 최근 2주. 칸을 누르면 그 방송
export function BiasBoard({ eps, open }: { eps: Episode[]; open: (ep: Episode) => void }) {
  const rows = biasBoard(eps)
  if (!rows.length) return <p className="text-muted-foreground text-xs">최근 2주 안에 판정된 방송이 없어요.</p>
  return <Card className="p-0 overflow-hidden"><ul>{rows.map(([who, vs]) =>
    <li key={who} className="grid grid-cols-[150px_1fr] max-[720px]:grid-cols-1 gap-3 px-4 py-3 border-t border-white/[0.06] first:border-t-0">
      <div className="flex gap-2.5 items-center min-w-0"><Avatar src={vs[0].src} small />
        <div className="min-w-0"><b className="text-[13px]">{who}</b>
          <div className="text-[11px] text-muted-foreground truncate">{vs[0].sub ?? vs[0].ep.show}</div></div></div>
      <div className="flex flex-col gap-1.5 min-w-0">{vs.slice(0, 4).map((v, k) =>
        <button key={k} className="flex gap-2 items-baseline text-left text-[12px] cursor-pointer hover:text-foreground text-muted-foreground min-w-0" onClick={() => open(v.ep)}>
          <span className="tnum w-10 shrink-0">{md(v.ep.date)}</span><BiasTag b={v.bias} h={v.horizon} />
          <span className="truncate">{v.why}</span></button>)}</div>
    </li>)}</ul></Card>
}

// 날짜 × 사람 롱·숏 표 (영상 요약 탭 맨 위). 칸을 누르면 그 방송
const COLS = [...Object.keys(WHO), 'guest'] as const
export function BiasTable({ eps, open }: { eps: Episode[]; open: (ep: Episode) => void }) {
  const rows = biasByDate(eps)
  if (!rows.length) return null
  const th = 'text-left text-[11px] text-muted-foreground font-semibold px-3 py-2'
  return <Card className="p-0 overflow-x-auto">
    <table className="w-full text-[12px] min-w-[640px]">
      <thead><tr><th className={th}>날짜</th>{COLS.map(c => <th key={c} className={th}>{c === 'guest' ? '게스트·패널' : WHO[c as Src].who}</th>)}</tr></thead>
      <tbody>{rows.map(([date, vs]) => <tr key={date} className="border-t border-white/[0.06] align-top">
        <td className="px-3 py-2 tnum text-muted-foreground whitespace-nowrap">{md(date)}</td>
        {COLS.map(c => <td key={c} className="px-3 py-2"><div className="flex flex-wrap gap-1.5">
          {vs.filter(v => c === 'guest' ? v.who !== WHO[v.src].who : v.who === WHO[c as Src].who).map((v, k) =>
            <button key={k} title={v.why} className="flex gap-1 items-center cursor-pointer hover:opacity-80" onClick={() => open(v.ep)}>
              {c === 'guest' && <span>{v.who}</span>}<BiasTag b={v.bias} h={c === 'guest' ? null : v.horizon} />
              {c !== 'guest' && v.ep.show && <span className="text-[10px] text-muted-foreground">{v.ep.show}</span>}</button>)}
        </div></td>)}
      </tr>)}</tbody>
    </table></Card>
}

export function Consensus({ eps, stocks }: { eps: Episode[]; stocks: Stock[] }) {
  const list = consensus(eps).slice(0, 12)
  if (!list.length) return null
  return <div className="flex flex-wrap gap-2">{list.map(([c, s]) =>
    <button key={c} className="glass rounded-full px-3 py-2 flex gap-2.5 items-center text-xs cursor-pointer hover:bg-white/10" onClick={() => go(c)}>
      <Avatars list={[...s]} />{nameOf(c, stocks)}<span className="text-muted-foreground text-[11px]">{`${s.size}명`}</span></button>)}</div>
}

// 게스트·패널 이름 (카드·패널 머리에 같이 쓴다)
const guests = (ep: Episode) => ep.guest ? '게스트 ' + ep.guest.name : ep.panel?.length ? ep.panel.map(p => p.name).join('·') : undefined

// 상세 패널의 한 구역: 라임 막대 + 굵은 제목으로 구역이 한눈에 갈린다
function Sec({ title, sub, className, children }: { title: string; sub?: string; className?: string; children: ReactNode }) {
  return <section className={cn('mt-8', className)}>
    <h3 className="flex items-center gap-2 text-[14px] font-bold mb-1"><span className="w-1 h-3.5 rounded-full bg-primary shrink-0" />{title}
      {sub && <span className="text-[12px] font-normal text-muted-foreground truncate">{sub}</span>}</h3>
    {children}</section>
}
// 요약 한 줄씩. 한글 문단이라 줄 간격을 넉넉히
const Rows = ({ id, rows }: { id: string; rows: Cited[] }) =>
  <ul>{rows.map((r, k) => <li key={k} className="text-[14px] leading-[1.7] text-foreground/90 py-2.5 border-t border-white/[0.06] first:border-t-0">
    {r.text}<CiteLink id={id} cite={r.cite} /><Quote q={r.quote} /></li>)}</ul>
const Lead = ({ children }: { children: ReactNode }) => <p className="flex gap-2 items-baseline text-[14px] font-semibold leading-[1.6] my-1.5">{children}</p>

// 오른쪽에서 밀려 나오는 방송 상세. ESC·바깥 클릭으로 닫는다
// 순서: 결론(헤드라인·롱숏·대응) → 팔로우 종목 얘기 → 패널 → 차트·시황 → 핵심 → 게스트 → 가격대 → 다룬 종목
export function EpisodeDrawer({ ep, stocks, close }: { ep: Episode | null; stocks: Stock[]; close: () => void }) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && close()
    addEventListener('keydown', onKey)
    return () => removeEventListener('keydown', onKey)
  }, [close])
  // 팔로우 종목에 대해 한 말은 맨 위에서 바로 보여준다 (나머지 종목은 아래 칩)
  const mine = Object.entries(ep?.stocks ?? {}).filter(([c]) => stocks.some(s => s.stockCode === c))
  return <>
    <div className={cn('fixed inset-0 z-30 bg-black/35 transition-opacity', ep ? 'opacity-100' : 'opacity-0 pointer-events-none')} onClick={close} />
    {/* 읽는 패널이라 불투명하게: 반투명이면 뒤 표·카드 글씨가 비쳐 겹친다. 머리는 고정, 본문만 스크롤 */}
    <aside className={cn('glass fixed z-40 top-3 right-3 bottom-3 w-[min(560px,94vw)] rounded-[26px] bg-card-solid flex flex-col overflow-hidden transition-transform duration-300',
      ep ? 'translate-x-0' : 'translate-x-[110%]')} aria-hidden={!ep} role="dialog" aria-modal="true">
      {ep && <>
        <header className="flex justify-between items-center gap-3 px-[22px] py-4 border-b border-white/[0.07]"><Who ep={ep} extra={guests(ep)} />
          <button className="glass grid place-items-center w-8 h-8 rounded-full cursor-pointer shrink-0" onClick={close} title="닫기" autoFocus><X size={15} /></button></header>
        <div className="flex-1 overflow-y-auto px-[22px] pt-4 pb-8">
          <a className="text-[12px] text-muted-foreground hover:text-foreground" href={yt(ep.id)} target="_blank" rel="noopener">{ep.title + ' ↗'}</a>
          <p className="text-[17px] font-semibold leading-[1.55] tracking-[-0.01em] mt-2.5">{ep.headline}</p>
          {ep.bias && <p className="flex gap-2 items-baseline text-[13px] leading-[1.6] text-foreground/80 mt-3"><BiasTag b={ep.bias.bias} h={ep.bias.horizon} /><span>{ep.bias.why}</span></p>}
          <div className="mt-4"><Stance ep={ep} full /></div>
          {!!mine.length && <Sec title="팔로우 종목에 대해 한 말" className="rounded-2xl bg-primary-soft/50 px-4 pt-3.5 pb-1.5">
            {mine.map(([c, rows]) => <div key={c} className="mt-2.5">
              <button className="text-[13px] font-bold text-primary cursor-pointer hover:underline" onClick={() => { close(); go(c) }}>{nameOf(c, stocks)}</button>
              <Rows id={ep.id} rows={rows} /></div>)}
          </Sec>}
          {ep.panel?.map((p, i) => <Sec key={i} title={p.name} sub={[p.role, p.topic && `'${p.topic}'`].filter(Boolean).join(' · ')}>
            <Lead><BiasTag b={p.bias} h={p.horizon} /><span>{p.view}</span></Lead>
            <Rows id={ep.id} rows={p.reasons} />
            {p.action && <p className="text-[13px] leading-[1.6] text-foreground/80 mt-1"><b className="text-foreground mr-1.5">대응</b>{p.action}</p>}
          </Sec>)}
          {!!ep.chart?.length && <Sec title="차트·시황"><Rows id={ep.id} rows={ep.chart} /></Sec>}
          {!!ep.points?.length && <Sec title="핵심 포인트"><Rows id={ep.id} rows={ep.points} /></Sec>}
          {ep.guest && <Sec title={'게스트 · ' + ep.guest.name} sub={ep.guest.firm}>
            <Lead>{ep.guest.bias && <BiasTag b={ep.guest.bias.bias} h={ep.guest.bias.horizon} />}<span>{ep.guest.headline}</span></Lead>
            <Rows id={ep.id} rows={ep.guest.points ?? []} />
          </Sec>}
          {!!ep.levels?.length && <Sec title="주요 가격대">
            <ul>{[...ep.levels].sort((a, b) => b.price - a.price).map((l, k) => <li key={k} className="flex gap-3 text-[14px] leading-[1.7] py-2 border-t border-white/[0.06] first:border-t-0">
              <b className="text-primary tnum min-w-16">{fmt(l.price)}</b><span className="text-foreground/90">{l.text}<CiteLink id={ep.id} cite={l.cite} /></span></li>)}</ul></Sec>}
          {!!Object.keys(ep.stocks ?? {}).length && <Sec title="다룬 종목" sub="누르면 종목별 발언">
            <div className="flex flex-wrap gap-1.5 mt-2">{Object.keys(ep.stocks!).map(c =>
              <button key={c} className={cn(chip, 'cursor-pointer hover:bg-white/15')} onClick={() => { close(); go(c) }}>{nameOf(c, stocks)}</button>)}</div></Sec>}
          <p className="text-[11px] text-muted-foreground mt-8">{`${WHO[ep.src].who} 방송에서 한 말을 요약한 것이에요. 자동 자막 기반이라 숫자·이름이 틀릴 수 있어요`}</p>
        </div>
      </>}
    </aside>
  </>
}

// 영상 요약 탭: 날짜별 타임라인 + 사람·방송 필터 (방송 목록은 노트에 있는 show에서)
const FILTERS = [['all', '전체'], ['alsangmu', '알상무'], ['shuka', '슈카'], ['wepoll', '위폴']] as const
export function ShowsPage({ eps, stocks, open }: { eps: Episode[]; stocks: Stock[]; open: (ep: Episode) => void }) {
  const [f, setF] = useState<typeof FILTERS[number][0]>('all')
  const [show, setShow] = useState<string | null>(null)
  const shows = f === 'all' ? [] : [...new Set(eps.filter(e => e.src === f).map(e => e.show).filter((s): s is string => !!s))]
  const list = eps.filter(e => (f === 'all' || e.src === f) && (!show || e.show === show))
  const days = [...new Set(list.map(e => e.date))]
  const pill = (on: boolean) => cn('glass rounded-full px-3 py-[7px] text-xs cursor-pointer', on ? 'bg-white/15 text-foreground' : 'text-muted-foreground')
  return <div className="flex flex-col gap-4">
    <BiasTable eps={eps} open={open} />
    <div className="flex gap-1.5 flex-wrap">{FILTERS.map(([k, t]) =>
      <button key={k} onClick={() => { setF(k); setShow(null) }} className={pill(k === f)}>{t}</button>)}</div>
    {shows.length > 1 && <div className="flex gap-1.5 flex-wrap">{[null, ...shows].map(s =>
      <button key={s ?? 'all'} onClick={() => setShow(s)} className={pill(s === show)}>{s ?? '모든 방송'}</button>)}</div>}
    {!list.length && <p className="text-muted-foreground">요약된 방송이 아직 없어요.</p>}
    {days.map(d => <div key={d} className="grid grid-cols-[110px_1fr] max-[720px]:grid-cols-1 gap-4">
      <div className="text-xs text-muted-foreground pt-3.5">{dateLabel(d)}</div>
      <div className="grid grid-cols-[repeat(auto-fill,minmax(300px,1fr))] gap-2.5">{list.filter(e => e.date === d).map(e =>
        <Card key={e.src + e.id} className="p-3.5 flex flex-col gap-2 cursor-pointer hover:bg-white/[0.08]" onClick={() => open(e)} role="button" tabIndex={0} onKeyDown={ev => ev.key === 'Enter' && open(e)}>
          <Who ep={e} extra={guests(e)} />
          <div className="text-[13px] leading-[1.5] line-clamp-3">{e.headline}</div>
          <div className="flex flex-wrap gap-1.5">{topStocks(e, 5).map(c => <span key={c} className={chip}>{nameOf(c, stocks)}</span>)}</div>
        </Card>)}</div>
    </div>)}
  </div>
}

// 종목 페이지: 누가 언제 이 종목에 대해 뭐라 했나 (모든 방송, 최신순)
export function Mentions({ code, eps, aliases, open, className }:
  { code: string; eps: Episode[]; aliases: Record<string, string>; open: (ep: Episode) => void; className?: string }) {
  const said = saidFor(eps, aliases, code)
  if (!said.length) return null
  const base = aliases[code] && aliases[code] !== code ? aliases[code] : null
  return (
    <Card className={cn('text-[13px]', className)}>
      <h2 className="text-[17px] font-bold tracking-[-0.02em]">누가 언제 뭐라 했나</h2>
      <div className="text-muted-foreground text-xs mb-2">{`방송 요약 ${said.length}건 · 최신순${base ? ` · 기초 종목 ${base} 포함` : ''} · 그들의 말을 옮긴 것이고 판단은 본인 몫이에요`}</div>
      <ul>{said.map((r, k) => <li key={k} className="grid grid-cols-[22px_1fr] gap-3 py-3 border-t border-white/[0.06] first:border-t-0">
        <Avatar src={r.ep.src} small />
        <div><button className="text-[11px] text-muted-foreground cursor-pointer hover:text-foreground" onClick={() => open(r.ep)}>
          {`${dateLabel(r.ep.date)} · ${WHO[r.ep.src].who}${r.ep.show ? ' ' + r.ep.show : ''}`}</button>
          <div>{r.text}<CiteLink id={r.ep.id} cite={r.cite} /><Quote q={r.quote} /></div></div>
      </li>)}</ul>
    </Card>
  )
}
