import type { ComponentProps } from 'react'
import { cn } from '@/lib/format'

export function Card({ className, ...props }: ComponentProps<'section'>) {
  return <section className={cn('bg-card text-card-foreground rounded-card shadow-card p-5', className)} {...props} />
}

export function CardTitle({ className, ...props }: ComponentProps<'h2'>) {
  return <h2 className={cn('text-[17px] font-bold tracking-[-0.02em] mx-1 mb-3', className)} {...props} />
}
