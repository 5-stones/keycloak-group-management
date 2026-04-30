import type { ReactNode } from 'react'

interface SectionProps {
  title: ReactNode
  actions?: ReactNode
  children: ReactNode
}

export function Section({ title, actions, children }: SectionProps) {
  return (
    <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
      <div className="mb-4 flex items-center justify-between gap-3">
        <h2 className="text-base font-semibold text-slate-900">{title}</h2>
        {actions}
      </div>
      {children}
    </section>
  )
}

export function ErrorMessage({ children }: { children: ReactNode }) {
  return <p className="mt-3 text-sm text-red-600">{children}</p>
}
