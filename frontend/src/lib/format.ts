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
export const UP = '#dc2626', DOWN = '#2563eb'
