import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'
import type { Quote } from '@/api'

export const cn = (...inputs: ClassValue[]) => twMerge(clsx(inputs))

// 한국식 표기: 상승 빨강, 하락 파랑
export const num = (v: number | string, currency?: string) => currency === 'USD'
  ? Number(v).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
  : Number(v).toLocaleString('ko-KR')
export const priceText = (q: Quote) => q.currency === 'USD' ? '$' + num(q.lastPrice, 'USD') : num(q.lastPrice) + '원'
export const dirClass = (rate: number | null | undefined) => rate == null ? '' : rate > 0 ? 'up' : rate < 0 ? 'down' : ''
export function changeText(q: Quote) {
  const rate = q.changeRate
  if (rate == null) return ''
  const sign = rate > 0 ? '+' : ''
  return sign + num(q.change ?? 0, q.currency) + ' (' + sign + rate.toFixed(2) + '%)'
}
export const fmt = (n: number) => n.toLocaleString('en-US', { maximumFractionDigits: 2 })
export const signedPct = (rate: number) => (rate > 0 ? '+' : '') + rate.toFixed(2) + '%'

// 차트 라이브러리는 UTC로 그리므로 시각은 한국 시간으로 옮겨서 넣는다. 화면에 날짜를 쓸 땐 되돌린다
export const KST = 9 * 3600
export const UP = '#ff8a7a', DOWN = '#8fb4ff'
// 차트 라이브러리는 배경·글자색을 직접 받는다 (CSS 변수를 못 읽음)
export const CHART = { layout: { background: { color: 'transparent' }, textColor: 'rgba(242,241,238,0.6)', fontSize: 11, panes: { separatorColor: 'rgba(255,255,255,0.1)' } },
  grid: { vertLines: { color: 'rgba(255,255,255,0.05)' }, horzLines: { color: 'rgba(255,255,255,0.05)' } } }
export const INK = '#f2f1ee'
