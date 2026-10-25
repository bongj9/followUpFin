import { useEffect, useState } from 'react'
import { getShuka, type Daily } from '@/api'
import { Today } from '@/components/AlsangmuView'
import { Card } from '@/components/ui/card'

// 슈카 뷰 (views/shuka.json, git 제외: 머니코믹스 「슈황」 라이브를 편마다 요약한 개인 공부 노트). 국장 시황 위주
// 홈은 오늘의 뷰 카드, 종목 페이지는 그가 그 종목에 대해 한 말(최근 방송이 위로). 노트가 없으면 숨긴다
export function ShukaView({ code, tick }: { code: string | null; tick: number }) {
  const [note, setNote] = useState<{ daily: Daily[]; aliases?: Record<string, string> } | null>(null)
  useEffect(() => { getShuka().then(setNote) }, [tick])
  if (!note?.daily.length) return null
  if (!code) return <Today daily={note.daily} who="슈카" chartTitle="시장을 이렇게 봤다" oddsLabel="분위기"
    className="[grid-area:shuka] bg-[linear-gradient(180deg,#ecfdf5,var(--color-card)_160px)]" />

  // 레버리지 ETF는 기초 종목 언급을 보여준다. daily가 최신순이라 그대로 펼치면 최근 방송이 위
  const key = note.aliases?.[code] ?? code
  const said = note.daily.flatMap(d => (d.stocks?.[key] ?? []).map(r => ({ ...r, d })))
  if (!said.length) return null
  return (
    <Card className="[grid-area:shuka] prose-list text-[13px]">
      <h2 className="text-[17px] font-bold tracking-[-0.02em]">{`슈카가 이 종목에 대해 한 말${key !== code ? ` (기초 종목 ${key})` : ''}`}</h2>
      <div className="text-muted-foreground text-xs mb-2">머니코믹스 슈황 라이브 요약 · 그의 말을 요약한 것이고, 투자 판단은 본인 몫이에요</div>
      <ul>{said.map((r, k) => <li key={k}>{r.text}
        <a className="cite" target="_blank" rel="noopener" href={`https://www.youtube.com/watch?v=${r.d.id}&t=${r.cite.split(':').reduce((a, v) => a * 60 + +v, 0)}s`}>
          {`▶ ${r.d.date.slice(5).replace('-', '/')} ${r.cite}`}</a></li>)}</ul>
    </Card>
  )
}
