import { useEffect, useRef, useState, type Ref } from 'react'
import { getBars, getFlows, getNote, isKoreanCode, type Bar, type Cited, type Daily, type MarketFlow, type Note, type Stock } from '@/api'
import { Card } from '@/components/ui/card'
import { fmt } from '@/lib/format'
import { marketState, type MarketState } from '@/lib/indicators'

const sec = (t: string) => t.split(':').reduce((a, v) => a * 60 + +v, 0)
const yt = (id: string, t?: string) => `https://www.youtube.com/watch?v=${id}` + (t ? `&t=${sec(t)}s` : '')
const tag = 'text-xs font-semibold px-2.5 py-0.5 rounded-full bg-white text-primary'

// 투자 관점 노트(views/*.json, git 제외: 실존 인물 영상 요약이라 공개 저장소에 안 올린다)를 지금 코스피에 대입한다
// 노트가 없으면 섹션을 숨긴다. 그가 지금 이렇게 말한 게 아니라 그의 규칙을 지금 데이터에 맞춰 보여주는 것
// 종목 페이지에선 같은 차트 규칙을 그 종목 일봉에 대입한다. 외국인 수급은 코스피 전체 것만 있어서 국내 종목에만 붙인다
// 홈(code null)은 오늘 뷰 + 뷰, 종목 페이지는 맨 위 한 줄 요약 + 뷰. 각자 격자 칸(today·peek·view)에 들어간다
export function AlsangmuView({ code, stocks, tick }: { code: string | null; stocks: Stock[]; tick: number }) {
  const target = code ?? 'KOSPI'
  const [data, setData] = useState<{ note: Note; bars: Bar[]; flows: MarketFlow[] } | null>(null)
  const viewRef = useRef<HTMLElement>(null)
  useEffect(() => {
    let stale = false
    Promise.all([getNote(), getBars(target, '1d'), target === 'KOSPI' || isKoreanCode(target) ? getFlows('KOSPI') : []])
      .then(([note, bars, flows]) => {
        if (stale) return // 기다리는 사이 다른 종목으로 옮겼다
        setData(note && bars && bars.length >= 121 ? { note, bars, flows } : null)
      })
    return () => { stale = true }
  }, [target, tick])
  if (!data) return null

  const { note, bars, flows } = data
  const name = code ? stocks.find(x => x.stockCode === code)?.name ?? code : '코스피'
  const m = marketState(bars, flows.length >= 21 ? flows : null)
  const said = code ? note.stocks?.[note.aliases?.[code] ?? code] : undefined
  return <>
    {code
      ? <div className="[grid-area:peek] flex gap-2.5 items-baseline flex-wrap text-[13px] px-4 py-3 bg-primary-soft rounded-[14px] cursor-pointer"
          onClick={() => viewRef.current?.scrollIntoView({ behavior: 'smooth' })}>
          {/* 종목 페이지 맨 위 한 줄 요약. 뷰 본문은 차트 아래라 스크롤해야 보여서 */}
          <b>{note.name}</b>
          <span className={tag}>{({ aboveAll: '이평선 위', below20: '20일선 아래', below60: '60일선 아래', belowAll: '이평선 모두 아래' } as Record<string, string>)[m.st.ma]}</span>
          <span className={tag}>{m.st.cross === 'golden' ? '골든크로스' : '데드크로스'}</span>
          <span className={tag}>{({ outsideLow: '볼린저 하단 밖', touchLow: '볼린저 하단', inside: '볼린저 안', outsideHigh: '볼린저 상단 밖' } as Record<string, string>)[m.st.bb]}</span>
          <span>{said?.summary ?? '영상에서 이 종목을 따로 다룬 적은 없어요. 차트 규칙만 대입'}</span>
          <span className="ml-auto text-link whitespace-nowrap">자세히 ↓</span>
        </div>
      : <Today note={note} />}
    <View ref={viewRef} note={note} m={m} code={code} name={name} />
  </>
}

// 알상무 오늘의 뷰: 가장 최근 영상 하나를 크게, 지난 날은 접어 둔다
// 영상 한 편씩 요약한 daily(최신이 앞, 아침 당일전략 · 저녁 지표추적자 · 금요일 RTS). 근거 링크는 그 영상의 해당 분으로 간다
function Today({ note }: { note: Note }) {
  const [day, ...past] = note.daily ?? []
  if (!day) return null
  const cite = (d: Daily, t: string) => <a className="cite" target="_blank" rel="noopener" href={yt(d.id, t)}>{'▶ ' + t}</a>
  const list = (d: Daily, items: Cited[]) => <ul>{items.map((r, k) => <li key={k}>{r.text}{cite(d, r.cite)}</li>)}</ul>
  const label = (d: Daily) => new Date(d.date + 'T00:00:00').toLocaleDateString('ko-KR', { month: 'long', day: 'numeric', weekday: 'short' })
  const ago = Math.round((Date.now() - +new Date(day.date + 'T00:00:00')) / 864e5)
  const h3 = 'text-[15px] font-bold mt-[18px] mb-1.5'
  return (
    <Card className="[grid-area:today] prose-list text-[13px] leading-[1.6] bg-[linear-gradient(180deg,var(--color-primary-soft),var(--color-card)_160px)]">
      <div className="inline-block text-xs font-bold text-primary bg-card px-2.5 py-[3px] rounded-full">
        {`알상무의 오늘 뷰 · ${label(day)} ${day.show ?? '지표추적자'}`}
        {ago > 1 && <span className="text-warn font-normal ml-1.5">{`(${ago}일 전 영상, 새 영상 아직 없음)`}</span>}
      </div>
      <div className="text-xl font-extrabold leading-[1.4] tracking-[-0.03em] mt-2.5 mb-3">{day.headline}</div>
      <div className="flex gap-1.5 flex-wrap mb-2">
        {([['현금', day.stance?.cash], ['확률', day.stance?.odds], ['행동', day.stance?.action]] as const).filter(([, v]) => v).map(([k, v]) =>
          <span key={k} className="px-3 py-1 rounded-full bg-card text-xs font-semibold shadow-[0_0_0_1px_var(--color-border)]">{`${k} · ${v}`}</span>)}
      </div>
      <h3 className={h3}>차트를 이렇게 봤다</h3>{list(day, day.chart ?? [])}
      <h3 className={h3}>그 밖에</h3>{list(day, day.points ?? [])}
      {!!day.levels?.length && <>
        <h3 className={h3}>그가 말한 코스피 레벨</h3>
        <ul>{[...day.levels].sort((a, b) => b.price - a.price).map((l, k) => <li key={k}><b>{fmt(l.price) + ' '}</b>{l.text}{cite(day, l.cite)}</li>)}</ul>
      </>}
      <div className="text-muted-foreground text-xs">
        {`영상: ${day.title} · 그의 말을 요약한 것이고, 투자 판단은 본인 몫이에요`}
        <a className="cite" href={yt(day.id)} target="_blank" rel="noopener">▶ 영상 보기</a>
      </div>
      {!!past.length && <details>
        <summary>{`지난 뷰 ${past.length}개`}</summary>
        {past.map(d => <details key={d.date + d.id}>
          <summary>{`${label(d)} ${d.show ?? ''} · ${d.headline}`}</summary>
          {list(d, [...(d.chart ?? []), ...(d.points ?? [])])}
        </details>)}
      </details>}
    </Card>
  )
}

const stepRow = 'grid grid-cols-[150px_1fr] max-[720px]:grid-cols-1 gap-x-3 gap-y-1 py-3 border-t border-muted'

function View({ ref, note, m, code, name }: { ref: Ref<HTMLElement>; note: Note; m: MarketState; code: string | null; name: string }) {
  const cite = (r: string) => {
    const [day, time] = r.split(' ')
    return <a className="cite" href={yt(note.videos[day], time)} target="_blank" rel="noopener">{`▶ ${day.slice(0, 2)}/${day.slice(2)} ${time}`}</a>
  }
  // 레버리지 ETF는 기초 종목 언급을 보여준다
  const base = code ? note.aliases?.[code] : undefined
  const said = code ? note.stocks?.[base ?? code] : undefined
  const own = code ? said?.levels ?? [] : note.levels
  const levels = [...own.map(l => ({ ...l, here: false })), { price: m.price, here: true, text: '', cite: '' }].sort((a, b) => b.price - a.price)
  return (
    <Card ref={ref} className="[grid-area:view] prose-list text-[13px]">
      <h2 className="text-[17px] font-bold tracking-[-0.02em]">{`${note.name} · 그의 판단 순서로 본 지금 ${name}`}</h2>
      <div className="text-muted-foreground text-xs">{`${note.source} · 영상 ${note.asOf}까지 · 지금 데이터 ${m.date} 종가`
        + (code ? ' · 지수용 차트 규칙을 이 종목 일봉에 대입' : '')}</div>
      <div className="mt-2.5 mb-3.5 px-3.5 py-3 bg-muted rounded-xl">
        {said?.summary ? `그가 본 ${base ? base + ' (이 ETF의 기초 종목)' : name}: ${said.summary}` : note.summary}
      </div>
      {note.steps.filter(step => m.st[step.key]).map(step => {
        const s = step.states[m.st[step.key]]
        // 종목 페이지에서 외국인 수급은 코스피 전체 것(그는 종목보다 시장을 먼저 본다)
        const fact = step.key === 'flow' && code ? '코스피 전체 · ' + m.fact.flow : m.fact[step.key]
        return (
          <div key={step.key} className={stepRow}>
            <b>{step.title.replace('코스피', name)}</b>
            <span className="font-bold">{fact}</span>
            <span className="col-start-2 max-[720px]:col-start-1 text-muted-foreground">{'그라면 → ' + s.read}{cite(s.cite)}</span>
          </div>
        )
      })}
      {!!said?.items?.length && <div className={stepRow}>
        <b>그가 이 종목에 대해 한 말</b>
        <ul>{said.items.map((r, k) => <li key={k}>{r.text}{cite(r.cite)}</li>)}</ul>
      </div>}
      {code && !said && <div className="text-muted-foreground text-xs">영상 20편에서 이 종목을 따로 다룬 부분은 없어요.</div>}
      {!!own.length && <div className={stepRow}>
        <b>⑥ 그가 말한 레벨</b>
        <div className="my-1.5 flex flex-col gap-0.5">
          {levels.map((l, k) => l.here
            ? <div key={k} className="grid grid-cols-[64px_1fr] gap-2 text-up font-bold border-y border-dashed border-up py-0.5"><b className="text-right">{fmt(l.price)}</b><span>← 지금</span></div>
            : <div key={k} className="grid grid-cols-[64px_1fr] gap-2"><b className="text-right">{fmt(l.price)}</b><span>{l.text}{cite(l.cite)}</span></div>)}
        </div>
      </div>}
      <details>
        <summary>비중 규칙 · 경계할 것</summary>
        <ul>{note.rules.map((r, k) => <li key={k}>{r.text}{cite(r.cite)}</li>)}</ul>
        <ul>{note.warnings.map((r, k) => <li key={k}>{r.text}{cite(r.cite)}</li>)}</ul>
      </details>
      <div className="mt-3 text-warn bg-warn-soft px-3.5 py-3 rounded-xl">
        {note.track + ' · 그가 지금 이렇게 말한 게 아니라, 영상 속 규칙을 지금 데이터에 대입한 것이에요'}
      </div>
    </Card>
  )
}
