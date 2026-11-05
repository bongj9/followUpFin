import { useEffect, useState } from 'react'
import { X } from 'lucide-react'
import type { Cited, Stock } from '@/api'
import { yt } from '@/components/AlsangmuView'
import { Card } from '@/components/ui/card'
import { cn, fmt } from '@/lib/format'
import { biasBoard, consensus, dateLabel, nameOf, saidFor, WHO, type Episode, type Src } from '@/lib/shows'

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

function Stance({ ep, full }: { ep: Episode; full?: boolean }) {
  const rows = ([['분위기', ep.stance?.odds], ['대응', ep.stance?.action], ['현금', ep.stance?.cash]] as const).filter(([, v]) => v)
  if (!rows.length) return null
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

export function Consensus({ eps, stocks }: { eps: Episode[]; stocks: Stock[] }) {
  const list = consensus(eps).slice(0, 12)
  if (!list.length) return null
  return <div className="flex flex-wrap gap-2">{list.map(([c, s]) =>
    <button key={c} className="glass rounded-full px-3 py-2 flex gap-2.5 items-center text-xs cursor-pointer hover:bg-white/10" onClick={() => go(c)}>
      <Avatars list={[...s]} />{nameOf(c, stocks)}<span className="text-muted-foreground text-[11px]">{`${s.size}명`}</span></button>)}</div>
}

// 오른쪽에서 밀려 나오는 방송 상세. ESC·바깥 클릭으로 닫는다
export function EpisodeDrawer({ ep, stocks, close }: { ep: Episode | null; stocks: Stock[]; close: () => void }) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && close()
    addEventListener('keydown', onKey)
    return () => removeEventListener('keydown', onKey)
  }, [close])
  const list = (title: string, rows?: Cited[]) => !!rows?.length && <>
    <h3 className="text-xs text-muted-foreground font-semibold mt-5 mb-1">{title}</h3>
    <ul>{rows.map((r, k) => <li key={k} className="text-[13px] py-[7px] border-t border-white/[0.06] first:border-t-0">{r.text}<CiteLink id={ep!.id} cite={r.cite} /></li>)}</ul></>
  return <>
    <div className={cn('fixed inset-0 z-30 bg-black/35 transition-opacity', ep ? 'opacity-100' : 'opacity-0 pointer-events-none')} onClick={close} />
    <aside className={cn('glass fixed z-40 top-3 right-3 bottom-3 w-[min(520px,92vw)] rounded-[26px] p-[22px] overflow-y-auto bg-[rgba(28,24,20,0.78)] transition-transform duration-300',
      ep ? 'translate-x-0' : 'translate-x-[110%]')} aria-hidden={!ep} role="dialog" aria-modal="true">
      {ep && <>
        <div className="flex justify-between items-center gap-3"><Who ep={ep} />
          <button className="glass grid place-items-center w-8 h-8 rounded-full cursor-pointer" onClick={close} title="닫기" autoFocus><X size={15} /></button></div>
        <a className="block text-[11px] text-muted-foreground mt-2.5" href={yt(ep.id)} target="_blank" rel="noopener">{ep.title + ' ↗'}</a>
        <div className="text-[15px] font-semibold leading-[1.5] mt-3 mb-3">{ep.headline}</div>
        <Stance ep={ep} full />
        {ep.bias && <p className="flex gap-2 items-baseline text-[12px] text-muted-foreground mt-3"><BiasTag b={ep.bias.bias} h={ep.bias.horizon} />{ep.bias.why}</p>}
        {ep.panel?.map((p, i) => <div key={i}>
          <h3 className="text-xs text-muted-foreground font-semibold mt-5 mb-1">{[p.name, p.role, p.topic && `'${p.topic}'`].filter(Boolean).join(' · ')}</h3>
          <p className="flex gap-2 items-baseline text-[13px] font-semibold"><BiasTag b={p.bias} h={p.horizon} />{p.view}</p>
          <ul>{p.reasons.map((r, k) => <li key={k} className="text-[13px] py-[7px] border-t border-white/[0.06] first:border-t-0">{r.text}<CiteLink id={ep.id} cite={r.cite} /></li>)}</ul>
          {p.action && <p className="text-[12px] text-muted-foreground"><b className="text-foreground mr-1.5">대응</b>{p.action}</p>}
        </div>)}
        {ep.guest && <>
          <h3 className="text-xs text-muted-foreground font-semibold mt-5 mb-1">{`게스트 · ${ep.guest.name}${ep.guest.firm ? ' · ' + ep.guest.firm : ''}`}</h3>
          <p className="flex gap-2 items-baseline text-[13px] font-semibold">{ep.guest.bias && <BiasTag b={ep.guest.bias.bias} h={ep.guest.bias.horizon} />}{ep.guest.headline}</p>
          <ul>{(ep.guest.points ?? []).map((r, k) => <li key={k} className="text-[13px] py-[7px] border-t border-white/[0.06] first:border-t-0">{r.text}<CiteLink id={ep.id} cite={r.cite} /></li>)}</ul>
        </>}
        {list('차트·시황', ep.chart)}{list('핵심 포인트', ep.points)}
        {!!ep.levels?.length && <>
          <h3 className="text-xs text-muted-foreground font-semibold mt-5 mb-1">주요 가격대</h3>
          <ul>{[...ep.levels].sort((a, b) => b.price - a.price).map((l, k) => <li key={k} className="flex gap-2.5 text-[13px] py-[7px] border-t border-white/[0.06] first:border-t-0">
            <b className="text-primary min-w-16">{fmt(l.price)}</b><span>{l.text}<CiteLink id={ep.id} cite={l.cite} /></span></li>)}</ul></>}
        {!!Object.keys(ep.stocks ?? {}).length && <>
          <h3 className="text-xs text-muted-foreground font-semibold mt-5 mb-1.5">다룬 종목 · 누르면 종목별 발언</h3>
          <div className="flex flex-wrap gap-1.5">{Object.keys(ep.stocks!).map(c =>
            <button key={c} className={cn(chip, 'cursor-pointer hover:bg-white/15')} onClick={() => { close(); go(c) }}>{nameOf(c, stocks)}</button>)}</div></>}
        <p className="text-[11px] text-muted-foreground mt-7">{`${WHO[ep.src].who} 방송에서 한 말을 요약한 것이에요. 자동 자막 기반이라 숫자·이름이 틀릴 수 있어요`}</p>
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
    <div className="flex gap-1.5 flex-wrap">{FILTERS.map(([k, t]) =>
      <button key={k} onClick={() => { setF(k); setShow(null) }} className={pill(k === f)}>{t}</button>)}</div>
    {shows.length > 1 && <div className="flex gap-1.5 flex-wrap">{[null, ...shows].map(s =>
      <button key={s ?? 'all'} onClick={() => setShow(s)} className={pill(s === show)}>{s ?? '모든 방송'}</button>)}</div>}
    {!list.length && <p className="text-muted-foreground">요약된 방송이 아직 없어요.</p>}
    {days.map(d => <div key={d} className="grid grid-cols-[110px_1fr] max-[720px]:grid-cols-1 gap-4">
      <div className="text-xs text-muted-foreground pt-3.5">{dateLabel(d)}</div>
      <div className="grid grid-cols-[repeat(auto-fill,minmax(300px,1fr))] gap-2.5">{list.filter(e => e.date === d).map(e =>
        <Card key={e.src + e.id} className="p-3.5 flex flex-col gap-2 cursor-pointer hover:bg-white/[0.08]" onClick={() => open(e)} role="button" tabIndex={0} onKeyDown={ev => ev.key === 'Enter' && open(e)}>
          <Who ep={e} extra={e.guest ? '게스트 ' + e.guest.name : e.panel?.length ? e.panel.map(p => p.name).join('·') : undefined} />
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
          <div>{r.text}<CiteLink id={r.ep.id} cite={r.cite} /></div></div>
      </li>)}</ul>
    </Card>
  )
}
