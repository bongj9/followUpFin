import type { ComponentProps } from 'react'
import { cn } from '@/lib/format'

export function Input({ className, ...props }: ComponentProps<'input'>) {
  return <input className={cn('px-3 py-2.5 bg-input rounded-[10px] text-[13px] min-w-0 outline-none focus:bg-card focus:ring-2 focus:ring-ring', className)} {...props} />
}
