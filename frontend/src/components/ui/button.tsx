import type { ComponentProps } from 'react'
import { cva, type VariantProps } from 'class-variance-authority'
import { cn } from '@/lib/format'

const buttonVariants = cva(
  'inline-flex items-center justify-center gap-1 font-[inherit] cursor-pointer transition-colors disabled:cursor-default disabled:text-muted-foreground',
  {
    variants: {
      variant: {
        default: 'bg-primary text-primary-foreground font-bold hover:brightness-95 disabled:text-primary-foreground/70',
        soft: 'bg-secondary text-secondary-foreground font-semibold',
        ghost: 'bg-transparent hover:bg-background',
      },
      size: {
        default: 'px-3.5 py-2.5 rounded-[10px] text-[13px]',
        lg: 'w-full p-3 rounded-xl text-sm',
        sm: 'px-2.5 py-0.5 rounded-full text-xs',
        tool: 'px-[9px] py-[5px] rounded-md text-[13px]',
      },
    },
    defaultVariants: { variant: 'default', size: 'default' },
  },
)

export function Button({ className, variant, size, ...props }: ComponentProps<'button'> & VariantProps<typeof buttonVariants>) {
  return <button className={cn(buttonVariants({ variant, size }), className)} {...props} />
}
