import { X } from 'lucide-react'
import { KST } from '@/lib/format'
import { ICON, INDICATORS, type Signal } from '@/lib/indicators'

// 보는 법 (지표 이름이나 메뉴의 '보는 법'을 누르면 차트 아래에 펼친다)
const GUIDES: Record<string, { what: string; read: string[]; caution: string; src: string }> = {
  ma: { what: '최근 N개 봉 종가의 평균을 이은 선. 5·20은 짧은 흐름, 60·120은 긴 흐름.',
    read: ['가격이 선 위면 그 기간에 산 사람들이 평균적으로 이익인 상태', '정배열(5>20>60>120)은 상승 추세, 역배열은 하락 추세', '상승 추세에선 20선 근처까지 내려왔다 다시 오르는 눌림이 자주 나온다'],
    caution: '평균이라 늦게 반응한다. 옆으로 기는 장에선 크로스가 자주 나왔다 사라진다(휩쏘).', src: '그랜빌 8법칙 (Granville, 1976)' },
  bb: { what: '20봉 평균선 위아래로 표준편차 2배만큼 띄운 띠. 가격은 대부분 이 띠 안에서 움직인다.',
    read: ['상단 근처는 평소보다 많이 오른 자리, 하단 근처는 많이 내린 자리', '띠가 좁아지면(스퀴즈) 쉬던 가격이 곧 크게 움직이는 일이 많다',
      '해석이 두 갈래다: 밴드 밖은 곧 돌아온다(반등·되돌림)는 시각과, 처음 닿은 건 그 방향 힘이 세다는 뜻이라 더 간다(추세 추종)는 시각',
      '밴드 밖에서 밴드를 끌고 내려가면 가장 가파른 하락. 이때 바닥을 미리 판단하지 않는다'],
    caution: '하단 이탈 = 싸다, 상단 돌파 = 비싸다가 아니다. 강한 추세에선 밴드를 타고 계속 간다(밴드 워킹). RSI·거래량과 같이 본다.',
    src: 'Bollinger on Bollinger Bands (2001) · 추세 추종 해석 예: 유튜브 지표추적자(알상무) 2026.10 코스피 하락 해설' },
  ichimoku: { what: '구름(빨강·파랑 선행스팬 사이)이 지지·저항 역할을 한다. 선행스팬1이 위면 빨간 구름(양운), 2가 위면 파란 구름(음운). 구름은 26봉 앞까지 미리 그려진다. 주황 전환선(9봉), 초록 기준선(26봉), 회색 후행스팬.',
    read: ['가격이 구름 위면 상승 추세, 아래면 하락 추세, 안이면 방향을 찾는 중', '전환선이 기준선 위로 올라가면 호전, 아래로 내려가면 역전', '구름이 두꺼울수록 뚫기 어렵다', '마지막 봉 오른쪽의 구름은 앞으로 26봉 동안의 지지·저항 자리. 색이 바뀌는 곳(구름 꼬임)은 추세가 바뀌기 쉬운 때로 본다'],
    caution: '선이 많아 복잡하다. 처음엔 가격이 구름 위인지 아래인지만 봐도 충분하다.', src: '호소다 고이치 (1969 공개)' },
  volume: { what: '그 봉 동안 거래된 주식 수. 빨강은 오른 봉, 파랑은 내린 봉, 주황선은 20봉 평균.',
    read: ['가격이 움직일 때 거래량이 실리면 믿을 만하다', '거래량 없이 나온 돌파는 실패하기 쉽다', '바닥에서 거래량이 크게 터지면 주인이 바뀌는 신호로 보기도 한다'],
    caution: '분봉의 마지막 봉은 아직 거래가 덜 쌓여서 작게 보인다.', src: 'Edwards & Magee, Technical Analysis of Stock Trends (1948)' },
  rsi: { what: '최근 14봉 동안 오른 폭과 내린 폭을 비교한 값 (0~100). 50보다 크면 오른 힘이 더 컸다.',
    read: ['70 이상은 과매수, 30 이하는 과매도 (아래 칸 점선)', '가격은 신고가인데 RSI는 낮아지면(다이버전스) 오르는 힘이 약해졌다는 신호', '50 위면 상승 쪽 힘이 우세'],
    caution: '강세장에선 70 위에 오래 머문다. 70을 넘었다고 바로 팔면 큰 상승을 놓칠 수 있다.', src: 'Wilder, New Concepts in Technical Trading Systems (1978)' },
  macd: { what: '12봉·26봉 지수이동평균의 차이(파랑)와 그 9봉 평균(시그널, 주황). 막대는 둘의 차이.',
    read: ['MACD가 시그널 위로 올라가면 오르는 힘이 커지는 중', '0선(점선) 위면 큰 흐름이 상승, 아래면 하락', '막대가 줄어들면 지금 방향의 힘이 빠지는 중'],
    caution: '이동평균으로 만들어서 늦다. 옆으로 기는 장에선 교차가 잦다.', src: 'Gerald Appel (1970년대)' },
}

export const signalDate = (t: number, intraday: boolean, year: '2-digit' | 'numeric') => new Date((t - KST) * 1000).toLocaleString('ko-KR',
  intraday ? { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' } : { year, month: 'numeric', day: 'numeric' })

const Icon = ({ s }: { s: Signal }) => <span style={{ color: s.color }}>{ICON[s.shape]}</span>

export function ChartGuide({ guideKey, on, signals, intraday, focus, onFocus, onClose }: {
  guideKey: string; on: boolean; signals: Signal[]; intraday: boolean; focus: Signal | null
  onFocus: (s: Signal) => void; onClose: () => void
}) {
  const ind = INDICATORS.find(i => i.key === guideKey)!, g = GUIDES[guideKey]
  const mine = signals.filter(s => s.key === guideKey)
  const kinds = [...new Map(mine.map(s => [s.text, s])).values()]
  const date = (t: number) => signalDate(t, intraday, '2-digit')
  const h4 = 'mb-1 text-xs text-muted-foreground font-semibold'
  return (
    <div className="mx-3.5 mb-1 rounded-2xl px-[18px] py-4 text-[13px] leading-[1.6] bg-muted">
      <div className="flex justify-between items-baseline gap-2">
        <h3 className="text-[15px] font-bold">{ind.name} 보는 법</h3>
        <button className="text-muted-foreground cursor-pointer" title="닫기" onClick={onClose}><X size={18} /></button>
      </div>
      <p className="mt-1 mb-2.5 text-muted-foreground">{g.what}</p>
      {!on && <div className="mt-2.5 px-2.5 py-2 rounded-lg bg-[#eff6ff] text-[#1e40af]">이 지표는 꺼져 있어요. 켜면 차트에 신호가 표시돼요.</div>}
      <div className="grid grid-cols-[repeat(auto-fit,minmax(240px,1fr))] gap-3">
        <div><h4 className={h4}>이렇게 읽어요</h4><ul className="list-disc pl-[18px]">{g.read.map(t => <li key={t}>{t}</li>)}</ul></div>
        <div><h4 className={h4}>차트 위 표시</h4>{kinds.length
          ? kinds.map(s => <div key={s.text} className="flex gap-2 items-baseline"><span className="text-sm w-4 text-center flex-none"><Icon s={s} /></span><span><b>{s.text + ' '}</b>{s.note}</span></div>)
          : <div>이 차트 구간엔 표시할 신호가 없어요.</div>}</div>
      </div>
      <div className="mt-2.5 px-2.5 py-2 bg-warn-soft rounded-lg text-warn">주의 · {g.caution}</div>
      {!!mine.length && <>
        <h4 className={h4 + ' mt-3'}>이 차트에서 찾은 신호 · 누르면 그 자리로 이동</h4>
        <div className="mt-1 flex flex-wrap gap-1.5">
          {mine.slice(-12).reverse().map(s => (
            <button key={s.time + s.text} onClick={() => onFocus(s)}
              className={'border bg-card rounded-full px-2.5 py-[3px] text-xs cursor-pointer ' + (focus === s ? 'border-[#111827] font-bold' : 'border-border')}>
              <Icon s={s} />{' ' + s.text + ' · ' + date(s.time)}
            </button>))}
        </div>
      </>}
      {focus && <div className="mt-2 px-2.5 py-2 bg-card border-l-[3px] border-current rounded" style={{ color: focus.color }}>
        <b>{date(focus.time) + ' ' + focus.text}</b><div className="text-foreground">{focus.note}</div>
      </div>}
      <div className="mt-2 text-[11px] text-muted-foreground">출처: {g.src} · 판단을 돕는 참고 정보이고 매매 신호가 아니에요</div>
    </div>
  )
}
