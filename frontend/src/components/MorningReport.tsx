import { useEffect, useState } from 'react'
import { getReports, type Report, type ReportCall } from '@/api'
import { Card } from '@/components/ui/card'
import { cn } from '@/lib/format'

// 매수 쪽은 빨강, 줄이는 쪽은 파랑 (한국식 상승·하락 색)
const ACTION_TONE: [RegExp, string][] = [[/매수|늘/, 'bg-up text-white'], [/매도|축소|줄/, 'bg-down text-white']]
const tone = (action: string) => ACTION_TONE.find(([re]) => re.test(action))?.[1] ?? 'bg-muted text-foreground'
const label = (date: string) => new Date(date + 'T00:00:00').toLocaleDateString('ko-KR', { month: 'long', day: 'numeric', weekday: 'short' })
const h3 = 'text-[15px] font-bold mt-[18px] mb-1.5'

const Watch = ({ list }: { list: ReportCall[] }) => !list.length ? null :
  <ul>{list.map((w, k) => <li key={k}><b>{w.if}</b>{' → ' + w.then}</li>)}</ul>

// 아침 리포트: 보유 종목·시장을 지표로 판정한 숫자와 알상무 최신 뷰를 합쳐 오늘 할 일을 정리한 것 (아침 리포트 스킬이 매일 씀)
// 리포트가 없으면(볼트 미연결·아직 안 만듦) 카드를 숨긴다
export function MorningReport({ tick }: { tick: number }) {
  const [reports, setReports] = useState<Report[]>([])
  useEffect(() => { getReports().then(r => setReports(r?.reports ?? [])) }, [tick])
  const [today, ...past] = reports
  if (!today) return null

  const ago = Math.floor((Date.now() - +new Date(today.date + 'T00:00:00')) / 864e5)
  // 비중 1% 미만은 접어 둔다 (소액 매수로 산 종목이 많아서)
  const big = today.holdings.filter(h => (h.weightPct ?? 100) >= 1), small = today.holdings.filter(h => (h.weightPct ?? 100) < 1)
  return (
    <Card className="[grid-area:report] prose-list text-[13px] leading-[1.6] bg-[linear-gradient(180deg,#fff1f2,var(--color-card)_160px)]">
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

      <h3 className={h3}>시장</h3>
      <ul>{today.market.why.map((w, k) => <li key={k}>{w}</li>)}</ul>
      <Watch list={today.market.watch} />

      <h3 className={h3}>보유 종목</h3>
      <div className="flex flex-col">
        {big.map(h => (
          <div key={h.code} className="grid grid-cols-[150px_1fr] max-[720px]:grid-cols-1 gap-x-3 gap-y-1 py-3 border-t border-muted">
            <div className="flex flex-col gap-1 items-start">
              <a className="font-bold text-foreground no-underline" href={'#/stock/' + encodeURIComponent(h.code)}>{h.name}</a>
              <span className="text-muted-foreground text-xs">
                {h.weightPct != null ? `비중 ${h.weightPct}%` : ''}{h.targetPct != null ? ` → ${h.targetPct}%` : ''}
              </span>
              <span className={cn('text-xs font-bold px-2 py-0.5 rounded-md', tone(h.action))}>{h.action}</span>
            </div>
            <div>
              <ul>{h.why.map((w, k) => <li key={k}>{w}</li>)}</ul>
              {h.alsangmu && <div className="text-muted-foreground mt-1">{'알상무: ' + h.alsangmu}</div>}
              <Watch list={h.watch} />
            </div>
          </div>
        ))}
      </div>
      {!!small.length && <details>
        <summary>{`비중 1% 미만 ${small.length}종목`}</summary>
        <ul>{small.map(h => <li key={h.code}><b>{h.name}</b>{` ${h.action} · ${h.why.join(' ')}`}</li>)}</ul>
      </details>}

      {!!today.review?.length && <>
        <h3 className={h3}>지난 판단 채점</h3>
        <ul>{today.review.map((r, k) => <li key={k}><b>{r.date.slice(5) + ' '}</b>{r.call}{' → ' + r.result}</li>)}</ul>
      </>}
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
