import { cn } from '@/lib/format'

// 알약 모양 탭 (토스 종목 화면 아래 탭처럼)
export function Tabs<K extends string>({ items, value, onChange, className }:
  { items: readonly (readonly [K, string])[]; value: K; onChange: (k: K) => void; className?: string }) {
  return (
    <div className={cn('flex gap-1 mt-1 mb-2 p-1 bg-muted rounded-xl w-fit', className)}>
      {items.map(([k, label]) => (
        <button key={k} onClick={() => onChange(k)}
          className={cn('px-3.5 py-[7px] text-sm font-semibold text-muted-foreground cursor-pointer rounded-[9px] transition-colors',
            k === value && 'text-foreground font-bold bg-card shadow-[0_1px_3px_rgba(0,0,0,0.08)]')}>
          {label}
        </button>
      ))}
    </div>
  )
}
