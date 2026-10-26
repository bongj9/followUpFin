import { useEffect, useState } from 'react'
import { getNote, getShow, type Cited, type Daily } from '@/api'
import { yt } from '@/components/AlsangmuView'
import { Card } from '@/components/ui/card'
import { cn, fmt } from '@/lib/format'

// 홈 "오늘의 시황 뷰": 세 사람의 최신 방송을 나란히 (views/*.json, git 제외: 영상을 편마다 요약한 개인 공부 노트)
// - 알상무: 같은 날 올라온 방송(당일전략·지표추적자·RTS)을 프로그램별로
// - 위폴: 박종훈의 바이킹스(간밤 미국장) + 버디버디 게스트(이름·소속·뷰)
// - 슈카: 슈황 (장 마감 뒤 국장)
// 핵심 3줄만 펼쳐 두고 나머지는 접는다. 노트가 하나도 없으면 숨긴다
const COLS = [
  { key: 'alsangmu', who: '알상무', sub: '유튜브 알상무 · 당일전략·지표추적자·RTS', color: 'var(--color-primary)' },
  { key: 'wepoll', who: '박종훈의 바이킹스', sub: '위폴 평일 아침 라이브 · 버디버디 게스트', color: '#2563eb' },
  { key: 'shuka', who: '슈카', sub: '머니코믹스 슈황 · 장 마감 뒤 국장', color: '#059669' },
] as const
type Key = typeof COLS[number]['key']

const label = (date: string) => new Date(date + 'T00:00:00').toLocaleDateString('ko-KR', { month: 'long', day: 'numeric', weekday: 'short' })
const chip = 'px-2.5 py-0.5 rounded-full bg-card text-xs font-semibold shadow-[0_0_0_1px_var(--color-border)]'

function Cites({ d, items }: { d: Daily; items: Cited[] }) {
  return <ul>{items.map((r, k) => <li key={k}>{r.text}
    <a className="cite" target="_blank" rel="noopener" href={yt(d.id, r.cite)}>{'▶ ' + r.cite}</a></li>)}</ul>
}

// 3개만 보이고 나머지는 '더 보기'
function Folded({ d, items }: { d: Daily; items: Cited[] }) {
  return <>
    <Cites d={d} items={items.slice(0, 3)} />
    {items.length > 3 && <details><summary>{`더 보기 ${items.length - 3}개`}</summary><Cites d={d} items={items.slice(3)} /></details>}
  </>
}

function Episode({ d, color, host }: { d: Daily; color: string; host?: string }) {
  const stance = ([['현금', d.stance?.cash], ['분위기', d.stance?.odds], ['대응', d.stance?.action]] as const).filter(([, v]) => v)
  return (
    <div className="py-3 first:pt-0 border-t first:border-t-0 border-muted">
      <div className="flex items-center gap-1.5 text-xs">
        <span className="font-bold text-white px-2 py-0.5 rounded-md" style={{ background: color }}>{d.show ?? '방송'}</span>
        {host && <span className="text-muted-foreground">{'진행 ' + host}</span>}
        <a className="cite ml-auto" target="_blank" rel="noopener" href={yt(d.id)}>▶ 영상</a>
      </div>
      <div className="text-[15px] font-bold leading-[1.45] tracking-[-0.02em] my-2">{d.headline}</div>
      {!!stance.length && <div className="flex flex-col gap-1 mb-2">
        {stance.map(([k, v]) => <div key={k} className="text-xs"><b className="mr-1.5">{k}</b>{v}</div>)}
      </div>}
      {!!d.levels?.length && <div className="flex gap-1.5 flex-wrap mb-2">
        {[...d.levels].sort((a, b) => b.price - a.price).map((l, k) =>
          <span key={k} className={chip} title={l.text}>{`코스피 ${fmt(l.price)}`}</span>)}
      </div>}
      <Folded d={d} items={[...(d.chart ?? []), ...(d.points ?? [])]} />
      {d.guest && <div className="mt-3 rounded-xl bg-muted px-3.5 py-3">
        <div className="text-[11px] font-semibold text-muted-foreground">게스트 · 버디버디</div>
        <div className="mt-0.5"><b className="text-[15px]">{d.guest.name}</b>
          {d.guest.firm && <span className="text-muted-foreground ml-1.5">{d.guest.firm}</span>}</div>
        <div className="font-semibold leading-[1.45] my-1.5">{d.guest.headline}</div>
        <Folded d={d} items={d.guest.points} />
      </div>}
    </div>
  )
}

function Column({ col, daily }: { col: typeof COLS[number]; daily: Daily[] }) {
  if (!daily.length) return null
  // 알상무는 하루에 여러 방송이라 가장 최근 날짜의 것을 모두, 나머지는 한 편
  const latest = col.key === 'alsangmu' ? daily.filter(d => d.date === daily[0].date) : daily.slice(0, 1)
  const past = daily.slice(latest.length)
  const ago = Math.floor((Date.now() - +new Date(daily[0].date + 'T00:00:00')) / 864e5)
  return (
    <section className="min-w-0 rounded-2xl border border-border bg-card px-4 py-4 border-t-4" style={{ borderTopColor: col.color }}>
      <h3 className="text-[17px] font-extrabold tracking-[-0.02em]">{col.who}</h3>
      <div className="text-muted-foreground text-xs">{col.sub}</div>
      <div className="text-xs font-semibold mt-1.5 mb-3" style={{ color: col.color }}>
        {label(daily[0].date)}
        {ago > 1 && <span className="text-warn font-normal ml-1.5">{`(${ago}일 전, 새 방송 아직 없음)`}</span>}
      </div>
      {latest.map(d => <Episode key={d.id} d={d} color={col.color} host={col.key === 'wepoll' ? '박종훈' : undefined} />)}
      {!!past.length && <details className="mt-2">
        <summary>{`지난 방송 ${past.length}개`}</summary>
        <ul>{past.map(d => <li key={d.id}>
          <b>{`${d.date.slice(5).replace('-', '/')} ${d.show ?? ''} `}</b>{d.headline}
          {d.guest && <span className="text-muted-foreground">{` · 게스트 ${d.guest.name}: ${d.guest.headline}`}</span>}
          <a className="cite" target="_blank" rel="noopener" href={yt(d.id)}>▶</a></li>)}</ul>
      </details>}
    </section>
  )
}

export function ViewsBoard({ tick }: { tick: number }) {
  const [notes, setNotes] = useState<Partial<Record<Key, Daily[]>>>({})
  useEffect(() => {
    Promise.all([getNote(), getShow('wepoll'), getShow('shuka')]).then(([a, w, s]) =>
      setNotes({ alsangmu: a?.daily ?? [], wepoll: w?.daily ?? [], shuka: s?.daily ?? [] }))
  }, [tick])
  if (!COLS.some(c => notes[c.key]?.length)) return null
  return (
    <Card className="[grid-area:views] prose-list text-[13px] leading-[1.6]">
      <h2 className="text-[17px] font-bold tracking-[-0.02em]">오늘의 시황 뷰</h2>
      <div className="text-muted-foreground text-xs mb-3">세 사람의 최신 방송을 요약한 것이에요. 그들의 말을 옮긴 것이고, 투자 판단은 본인 몫이에요</div>
      <div className={cn('grid gap-3 items-start grid-cols-3 max-[1100px]:grid-cols-1')}>
        {COLS.map(c => <Column key={c.key} col={c} daily={notes[c.key] ?? []} />)}
      </div>
    </Card>
  )
}
