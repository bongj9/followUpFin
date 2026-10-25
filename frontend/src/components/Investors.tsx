import type { InvestorFlow } from '@/api'
import { Card, CardTitle } from '@/components/ui/card'
import { dirClass } from '@/lib/format'

const cols = 'grid grid-cols-[72px_repeat(3,1fr)] px-2 py-[5px] text-[13px] tnum [&>span:not(:first-child)]:text-right'

// 투자자별 순매수 (차트 아래): 날짜 | 개인 | 외국인 | 기관
// 하루치 순매수 수량(주). 당일 잠정치는 개인이 아직 없다 (다음 영업일 새벽 확정)
export function Investors({ list }: { list: InvestorFlow[] | null }) {
  if (list === null) return null
  const rate = list.find(d => d.foreignerHoldingRate != null)?.foreignerHoldingRate
  const today = new Date().toLocaleDateString('sv-SE', { timeZone: 'Asia/Seoul' }) // 2026-10-23
  const cell = (v: number | null) => v == null
    ? <span>집계 중</span>
    : <span className={dirClass(v)}>{(v > 0 ? '+' : '') + v.toLocaleString('en-US')}</span>
  return (
    <Card>
      <CardTitle>투자자별 순매수 <span className="text-muted-foreground text-[11px] font-normal">{rate == null ? '' : '외국인 보유 ' + (rate * 100).toFixed(2) + '%'}</span></CardTitle>
      {!list.length ? <div className="text-muted-foreground p-6 text-center">투자자 동향을 불러올 수 없어요</div> : <>
        <div className={cols + ' text-muted-foreground border-b border-border'}>{['날짜', '개인', '외국인', '기관'].map(t => <span key={t}>{t}</span>)}</div>
        {list.map(d => (
          <div key={d.date} className={cols}>
            <span>{d.date.slice(5).replace('-', '.')}{d.date === today && <small className="text-muted-foreground text-[11px]"> 잠정</small>}</span>
            {cell(d.individual)}{cell(d.foreigner)}{cell(d.institution)}
          </div>
        ))}
      </>}
    </Card>
  )
}
