import { useEffect, useState } from 'react'
import { getShow, type Daily } from '@/api'
import { Card } from '@/components/ui/card'

// 방송 뷰 (views/<show>.json, git 제외: 라이브를 편마다 요약한 개인 공부 노트)
// - shuka: 머니코믹스 「슈황」, 장 마감 후 국장 시황 위주
// - wepoll: 위폴 평일 아침 「바이킹스」(간밤 미국장) + 「버디버디」 게스트 뷰
// 종목 페이지에서 그 방송이 그 종목에 대해 한 말(최근 방송이 위로). 노트가 없으면 숨긴다. 홈의 오늘 방송 요약은 ViewsBoard
const SHOWS = {
  shuka: { said: '슈카가 이 종목에 대해 한 말', source: '머니코믹스 슈황 라이브 요약', area: '[grid-area:shuka]' },
  wepoll: { said: '위폴 방송에서 이 종목에 대해 한 말', source: '위폴 바이킹스·버디버디 아침 라이브 요약', area: '[grid-area:wepoll]' },
}

export function ShowView({ show, code, tick }: { show: keyof typeof SHOWS; code: string; tick: number }) {
  const s = SHOWS[show]
  const [note, setNote] = useState<{ daily: Daily[]; aliases?: Record<string, string> } | null>(null)
  useEffect(() => { getShow(show).then(setNote) }, [show, tick])
  if (!note?.daily.length) return null

  // 레버리지 ETF는 기초 종목 언급을 보여준다. daily가 최신순이라 그대로 펼치면 최근 방송이 위
  const key = note.aliases?.[code] ?? code
  const said = note.daily.flatMap(d => (d.stocks?.[key] ?? []).map(r => ({ ...r, d })))
  if (!said.length) return null
  return (
    <Card className={`${s.area} prose-list text-[13px]`}>
      <h2 className="text-[17px] font-bold tracking-[-0.02em]">{`${s.said}${key !== code ? ` (기초 종목 ${key})` : ''}`}</h2>
      <div className="text-muted-foreground text-xs mb-2">{`${s.source} · 그의 말을 요약한 것이고, 투자 판단은 본인 몫이에요`}</div>
      <ul>{said.map((r, k) => <li key={k}>{r.text}
        <a className="cite" target="_blank" rel="noopener" href={`https://www.youtube.com/watch?v=${r.d.id}&t=${r.cite.split(':').reduce((a, v) => a * 60 + +v, 0)}s`}>
          {`▶ ${r.d.date.slice(5).replace('-', '/')} ${r.cite}`}</a></li>)}</ul>
    </Card>
  )
}
