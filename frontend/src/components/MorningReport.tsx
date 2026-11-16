import { useEffect, useState } from 'react'
import { getBars, getReports, type Bar, type Report, type ReportCall, type ReportHolding } from '@/api'
import { won, type Position, type useAccount } from '@/components/Account'
import { Sec } from '@/components/Shows'
import { Card } from '@/components/ui/card'
import { followGains } from '@/lib/follow'
import { cn } from '@/lib/format'

// 매수 쪽은 빨강, 줄이는 쪽은 파랑 (한국식 상승·하락 색)
const ACTION_TONE: [RegExp, string][] = [[/매수|늘/, 'bg-up text-[#2a0d08]'], [/매도|축소|줄/, 'bg-down text-[#0b1530]']]
const tone = (action: string) => ACTION_TONE.find(([re]) => re.test(action))?.[1] ?? 'bg-muted text-foreground'
const label = (date: string) => new Date(date + 'T00:00:00').toLocaleDateString('ko-KR', { month: 'long', day: 'numeric', weekday: 'short' })
const h3 = 'text-[15px] font-bold mt-[18px] mb-1.5'

const signed = (n: number) => (n > 0 ? '+' : '') + won(n)

// 리포트의 목표 비중대로 그날 종가에 맞췄다면, 그대로 둔 것보다 다음 리포트까지 얼마 나았나 (계산은 lib/follow.ts)
function FollowGains({ reports, account, tick }: { reports: Report[]; account: ReturnType<typeof useAccount>; tick: number }) {
  const codes = [...new Set(reports.flatMap(r => r.holdings.filter(h => h.targetPct != null).map(h => h.code)))].join(',')
  const [bars, setBars] = useState<Record<string, Bar[]>>({})
  useEffect(() => {
    let stale = false
    // 한 종목이 실패해도 나머지는 계산한다. 평가액이 계속 바뀌니 봉도 tick마다 다시 받는다 (지금 값끼리 맞추려고)
    Promise.all(codes.split(',').filter(Boolean).map(async c => [c, await getBars(c, '1d').catch(() => null) ?? []] as const))
      .then(e => { if (!stale) setBars(Object.fromEntries(e)) })
    return () => { stale = true }
  }, [codes, tick])
  const valueNow = Object.fromEntries(account.positions.filter(p => p.value != null).map(p => [p.symbol, p.value!]))
  const rows = followGains(reports, bars, valueNow, account.cashKrw)
  if (!rows.length) return null
  const total = rows.reduce((s, r) => s + r.gain, 0)
  return <>
    <h3 className={h3}>리포트대로 했으면</h3>
    <ul>{rows.map(r => <li key={r.date}><b>{r.date.slice(5) + ' '}</b>{r.calls.join(', ')}
      <span className="text-muted-foreground">{` · ${r.date.slice(5)} 종가 → ${r.open ? '지금 (진행 중)' : r.to!.slice(5) + ' 종가'} `}</span>
      <b className={cn('tnum', r.gain > 0 ? 'up' : r.gain < 0 && 'down')}>{signed(r.gain)}</b></li>)}</ul>
    <p>{'모두 따랐다면 그대로 둔 것보다 '}<b className={cn('tnum', total > 0 ? 'up' : total < 0 && 'down')}>{signed(total)}</b>
      <span className="text-muted-foreground text-xs">{' · 비중은 주식+현금 기준. 판 돈은 현금으로 두고 환율 변동은 뺀 추정'}</span></p>
  </>
}

// 읽는 글: 글머리표 대신 줄마다 선으로 가르고, 한 줄이 한글 40자 안팎이 되게 폭을 묶는다
const body = 'max-w-[640px] text-[14px] leading-[1.7] text-foreground/90'
const row = 'py-2.5 border-t border-white/[0.06] first:border-t-0'
const Lines = ({ list }: { list: string[] }) => <div className={body}>{list.map((t, k) => <p key={k} className={row}>{t}</p>)}</div>

// 조건(왼쪽) → 그때 할 일(오른쪽)
const Watch = ({ list }: { list: ReportCall[] }) => !list.length ? null :
  <div className={body}>{list.map((w, k) => <div key={k} className={cn(row, 'grid grid-cols-[minmax(0,2fr)_minmax(0,3fr)] max-[720px]:grid-cols-1 gap-x-4 gap-y-0.5')}>
    <b className="font-semibold text-foreground">{w.if}</b><span>{w.then}</span></div>)}</div>

// 방송 인용 한 덩어리를 "10/15: …", "슈카 11/12: …"처럼 날짜로 시작하는 문장마다 끊는다
const quotes = (s: string) => s.split(/(?<=[.다요”"']) (?=(?:[가-힣]+ )?\d{1,2}\/\d{1,2})/)
// 예전 리포트엔 todo가 없고 첫 조건이 "오늘 밤 …"이다
const todoOf = (h: ReportHolding) => h.todo ?? (h.watch[0] && /^오늘/.test(h.watch[0].if) ? { when: h.watch[0].if, what: h.watch[0].then } : null)
const price = (n: number, currency: string) => currency === 'USD' ? '$' + n.toLocaleString('en-US', { maximumFractionDigits: 2 }) : won(n)

// 보유 종목 한 칸. 위에서부터: 이름·판단 → 숫자(문장에서 뺀 것) → 오늘 할 일 → 근거 → 방송 → 조건
function HoldingCard({ h, pos }: { h: ReportHolding; pos?: Position }) {
  const todo = todoOf(h), watch = todo && !h.todo ? h.watch.slice(1) : h.watch
  const stats: [string, string, string?][] = [
    ['비중 (주식 안)', h.weightPct != null ? h.weightPct + '%' : '-'],
    ...(h.targetPct != null ? [['목표 (현금 포함)', h.targetPct + '%'] as [string, string]] : []),
    ...(pos ? [
      ['평가액', pos.value != null ? won(pos.value) : '-'],
      ['손익률', pos.profitRate != null ? (pos.profitRate > 0 ? '+' : '') + pos.profitRate.toFixed(1) + '%' : '-', pos.profitRate == null ? undefined : pos.profitRate > 0 ? 'up' : 'down'],
      ['평단 → 지금', price(pos.averagePrice, pos.currency) + ' → ' + (pos.lastPrice != null ? price(pos.lastPrice, pos.currency) : '-')],
      ['수량', pos.quantity.toLocaleString('en-US') + '주'],
    ] as [string, string, string?][] : []),
  ]
  return (
    <section className="py-6 border-t border-muted first:border-t-0 first:pt-2">
      <header className="flex items-center gap-2.5 flex-wrap">
        <a className="text-[16px] font-bold text-foreground no-underline hover:underline" href={'#/stock/' + encodeURIComponent(h.code)}>{h.name}</a>
        <span className={cn('text-xs font-bold px-2 py-0.5 rounded-md', tone(h.action))}>{h.action}</span>
      </header>
      <dl className="grid grid-cols-[repeat(auto-fit,minmax(110px,1fr))] gap-x-4 gap-y-2.5 mt-3 rounded-xl bg-white/[0.04] px-3.5 py-3">
        {stats.map(([k, v, c]) => <div key={k}><dt className="text-[11px] text-muted-foreground">{k}</dt><dd className={cn('text-[14px] font-semibold tnum', c)}>{v}</dd></div>)}
      </dl>
      {todo && <div className="mt-3 rounded-xl bg-primary-soft px-3.5 py-3 max-w-[640px]">
        <div className="text-[11px] font-bold text-primary">{'오늘 할 일 · ' + todo.when}</div>
        <p className="text-[15px] font-semibold leading-[1.6] mt-0.5">{todo.what}</p>
      </div>}
      <Sec title="근거" className="!mt-5"><Lines list={h.why} /></Sec>
      {h.alsangmu && <Sec title="방송에서 한 말" sub="이름이 없으면 알상무" className="!mt-5"><Lines list={quotes(h.alsangmu)} /></Sec>}
      {!!watch.length && <Sec title="이렇게 되면 바꾼다" className="!mt-5"><Watch list={watch} /></Sec>}
    </section>
  )
}

// 아침 리포트: 보유 종목·시장을 지표로 판정한 숫자와 알상무 최신 뷰를 합쳐 오늘 할 일을 정리한 것 (아침 리포트 스킬이 매일 씀)
// 리포트가 없으면(볼트 미연결·아직 안 만듦) 카드를 숨긴다
// 계좌 전체·현금은 왼쪽 내 계좌 카드에 있고, 여기선 종목별 평가액만 붙인다
export function MorningReport({ tick, account }: { tick: number; account: ReturnType<typeof useAccount> }) {
  const [reports, setReports] = useState<Report[]>([])
  useEffect(() => { getReports().then(r => setReports(r?.reports ?? [])) }, [tick])
  const [today, ...past] = reports
  if (!today) return null

  const pos = new Map(account.positions.map(p => [p.symbol, p]))
  const ago = Math.floor((Date.now() - +new Date(today.date + 'T00:00:00')) / 864e5)
  // 비중 1% 미만은 접어 둔다 (소액 매수로 산 종목이 많아서)
  const big = today.holdings.filter(h => (h.weightPct ?? 100) >= 1), small = today.holdings.filter(h => (h.weightPct ?? 100) < 1)
  return (
    <Card className="[grid-area:report] prose-list text-[13px] leading-[1.6] bg-[linear-gradient(135deg,rgba(212,242,106,0.10),var(--color-card)_45%)]">
      <div className="inline-block text-xs font-bold text-up bg-card px-2.5 py-[3px] rounded-full">
        {`아침 리포트 · ${label(today.date)}`}
        {ago > 0 && <span className="text-warn font-normal ml-1.5">{`(${ago}일 전, 오늘 것 아직 없음)`}</span>}
      </div>
      <div className="text-xl font-extrabold leading-[1.4] tracking-[-0.03em] mt-2.5 mb-3">{today.headline}</div>
      <div className="flex gap-1.5 flex-wrap mb-2">
        {[`시장 · ${today.market.stance}`, today.market.cashPct != null && `현금 목표 · ${today.market.cashPct}%`,
          today.alsangmu && `알상무 ${today.alsangmu.show} (${today.alsangmu.date.slice(5)}) 반영`].filter(Boolean).map(t =>
          <span key={t as string} className="px-3 py-1 rounded-full bg-card text-xs font-semibold shadow-[0_0_0_1px_var(--color-border)]">{t}</span>)}
      </div>

      <Sec title="시장" className="!mt-5"><Lines list={today.market.why} /></Sec>
      {!!today.market.watch.length && <Sec title="시장이 이렇게 되면 바꾼다" className="!mt-5"><Watch list={today.market.watch} /></Sec>}

      <h3 className={h3}>보유 종목</h3>
      <div className="flex flex-col">{big.map(h => <HoldingCard key={h.code} h={h} pos={pos.get(h.code)} />)}</div>
      {!!small.length && <details>
        <summary>{`비중 1% 미만 ${small.length}종목`}</summary>
        <ul>{small.map(h => <li key={h.code}><b>{h.name}</b>{` ${h.action} · ${h.why.join(' ')}`}</li>)}</ul>
      </details>}

      {!!today.review?.length && <>
        <h3 className={h3}>지난 판단 채점</h3>
        <ul>{today.review.map((r, k) => <li key={k}><b>{r.date.slice(5) + ' '}</b>{r.call}{' → ' + r.result}</li>)}</ul>
      </>}
      <FollowGains reports={reports} account={account} tick={tick} />
      <div className="text-muted-foreground text-xs mt-3">지표와 영상 요약으로 만든 참고 자료예요. 지표는 고점·저점을 맞히지 못하고, 투자 판단과 책임은 본인 몫이에요.</div>
      {!!past.length && <details>
        <summary>{`지난 리포트 ${past.length}개`}</summary>
        {past.map(r => <details key={r.date}>
          <summary>{`${label(r.date)} · ${r.headline}`}</summary>
          <ul>{r.holdings.map(h => <li key={h.code}><b>{h.name}</b>{` ${h.action} · ${h.why[0] ?? ''}`}</li>)}</ul>
        </details>)}
      </details>}
    </Card>
  )
}

// 개요 맨 위 요약 카드: 헤드라인, 종목별 할 일, 현금 비중(지금 → 목표), 지난 판단 채점 3개. 전체는 아침 리포트 탭
const mark = (result: string) => /^맞았/.test(result) ? ['text-primary', '맞음'] : /미실행/.test(result) ? ['text-up', '미실행']
  : /틀렸/.test(result) ? ['text-up', '틀림'] : ['text-muted-foreground', '보류']
export function ReportHero({ tick, account }: { tick: number; account: ReturnType<typeof useAccount> }) {
  const [today, setToday] = useState<Report | null>(null)
  useEffect(() => { getReports().then(r => setToday(r?.reports[0] ?? null)) }, [tick])
  if (!today?.market) return null
  const total = account.stocks + (account.cashKrw ?? 0)
  const cashNow = account.cashKrw != null && total ? Math.round(account.cashKrw / total * 100) : null
  const target = today.market.cashPct
  const ago = Math.floor((Date.now() - +new Date(today.date + 'T00:00:00')) / 864e5)
  return (
    <Card className="grid grid-cols-[1.5fr_1fr] max-[900px]:grid-cols-1 gap-6 p-6 bg-[linear-gradient(135deg,rgba(212,242,106,0.10),var(--color-card)_45%)]">
      <div>
        <div className="text-[11px] font-semibold text-primary">
          {`아침 리포트 · ${label(today.date)} · 판단 「${today.market.stance}」`}
          {ago > 0 && <span className="text-warn font-normal ml-1.5">{`(${ago}일 전, 오늘 것 아직 없음)`}</span>}
        </div>
        <div className="text-[17px] font-semibold leading-[1.45] tracking-[-0.01em] mt-1.5 mb-3.5">{today.headline}</div>
        <div className="flex gap-2 flex-wrap">{today.holdings.filter(h => (h.weightPct ?? 100) >= 1).map(h =>
          <a key={h.code} href={'#/stock/' + encodeURIComponent(h.code)} className="px-3 py-2 rounded-2xl bg-white/[0.06] border border-border hover:bg-white/10">
            <b className={cn('text-xs', /매수|늘/.test(h.action) ? 'text-primary' : /매도|축소|줄/.test(h.action) ? 'text-up' : 'text-muted-foreground')}>{h.action}</b>
            <b className="text-xs ml-1">{h.code}</b>
            <small className="block text-[11px] text-muted-foreground">{h.weightPct != null ? `${h.weightPct}%` : ''}{h.targetPct != null ? ` → ${h.targetPct}%` : ''}</small>
          </a>)}</div>
      </div>
      <div className="rounded-[18px] p-4 bg-black/20 border border-border flex flex-col gap-2.5">
        <div className="flex justify-between text-xs"><span className="text-muted-foreground">현금 비중</span>{target != null && <span className="text-muted-foreground">{`목표 ${target}%`}</span>}</div>
        <div className="text-[28px] font-semibold tracking-[-0.02em] tnum">{cashNow == null ? '-' : cashNow + '%'}
          {target != null && <span className="text-sm text-muted-foreground">{` → ${target}%`}</span>}</div>
        <div className="h-2 rounded-full bg-white/10 relative">
          <i className="absolute inset-y-0 left-0 rounded-full bg-primary" style={{ width: (cashNow ?? 0) + '%' }} />
          {target != null && <em className="absolute -top-1 w-0.5 h-4 bg-white rounded" style={{ left: target + '%' }} />}
        </div>
        {!!today.review?.length && <ul className="mt-1.5">{today.review.slice(0, 3).map((r, k) => {
          const [c, t] = mark(r.result)
          return <li key={k} className="text-xs text-muted-foreground py-1.5 border-t border-white/[0.06] first:border-t-0"><b className={c}>{t}</b>{` · ${r.date.slice(5)} ${r.call}`}</li>
        })}</ul>}
        <a href="#/report" className="text-[11px] text-muted-foreground hover:text-foreground">리포트 전체 보기 →</a>
      </div>
    </Card>
  )
}
