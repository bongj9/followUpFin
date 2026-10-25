import type { ComponentProps } from 'react'
import { cva, type VariantProps } from 'class-variance-authority'
import { cn } from '@/lib/format'

const badgeVariants = cva('text-[11px] font-bold px-2 py-0.5 rounded-md text-white', {
  variants: {
    variant: { NEWS: 'bg-news', DISCLOSURE: 'bg-disc', MOVE: 'bg-up', MARKET: 'bg-market', HIGH: 'bg-up' },
  },
})

export function Badge({ className, variant, ...props }: ComponentProps<'span'> & VariantProps<typeof badgeVariants>) {
  return <span className={cn(badgeVariants({ variant }), className)} {...props} />
}
