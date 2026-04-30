// Inline-SVG icons rendered at 16×16. Each follows the Lucide / Heroicons
// stroke conventions so they line up visually with one another.

import type { SVGProps } from 'react'

const iconProps: SVGProps<SVGSVGElement> = {
  width: 16,
  height: 16,
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 2,
  strokeLinecap: 'round',
  strokeLinejoin: 'round',
  'aria-hidden': true,
}

export const IconPencil = () => (
  <svg {...iconProps}>
    <path d="M12 20h9" />
    <path d="M16.5 3.5a2.121 2.121 0 113 3L7 19l-4 1 1-4 12.5-12.5z" />
  </svg>
)

export const IconTrash = () => (
  <svg {...iconProps}>
    <path d="M3 6h18" />
    <path d="M8 6V4a2 2 0 012-2h4a2 2 0 012 2v2" />
    <path d="M19 6l-1 14a2 2 0 01-2 2H8a2 2 0 01-2-2L5 6" />
    <path d="M10 11v6" />
    <path d="M14 11v6" />
  </svg>
)

export const IconResend = () => (
  <svg {...iconProps}>
    <path d="M22 2L11 13" />
    <path d="M22 2l-7 20-4-9-9-4 20-7z" />
  </svg>
)

export const IconArrowRight = () => (
  <svg {...iconProps}>
    <path d="M5 12h14" />
    <path d="M12 5l7 7-7 7" />
  </svg>
)

export const IconPlus = () => (
  <svg {...iconProps}>
    <path d="M12 5v14" />
    <path d="M5 12h14" />
  </svg>
)
