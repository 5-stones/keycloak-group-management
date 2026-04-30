import { btnNeutral, btnPrimary } from '../lib/buttons'
import type { PageMeta } from '../api'

interface ThSortableProps {
  label: string
  field: string
  sortBy: string
  sortDir: 'asc' | 'desc'
  onSort: (field: string) => void
}

export function ThSortable({ label, field, sortBy, sortDir, onSort }: ThSortableProps) {
  const arrow = sortBy === field ? (sortDir === 'asc' ? '▲' : '▼') : ''
  return (
    <th
      onClick={() => onSort(field)}
      className="cursor-pointer select-none px-4 py-3 hover:bg-slate-100"
    >
      {label} <span className="text-[10px] text-slate-400">{arrow}</span>
    </th>
  )
}

interface PaginationProps {
  meta: PageMeta | null | undefined
  page: number
  setPage: (page: number) => void
  className?: string
}

export function Pagination({ meta, page, setPage, className = '' }: PaginationProps) {
  if (!meta) return null
  const showing =
    meta.totalCount === 0
      ? 'No results'
      : `Showing ${(meta.page - 1) * meta.pageSize + 1}–${Math.min(meta.page * meta.pageSize, meta.totalCount)} of ${meta.totalCount}`
  return (
    <div className={`flex items-center justify-between border-t border-slate-200 bg-slate-50 text-sm ${className}`}>
      <span className="text-xs text-slate-500">{showing}</span>
      {meta.totalPages > 1 && (
        <div className="inline-flex gap-1">
          <button
            onClick={() => page > 1 && setPage(page - 1)}
            disabled={page <= 1}
            className={`${btnNeutral} px-2 py-1 text-xs`}
          >
            Previous
          </button>
          {Array.from({ length: meta.totalPages }, (_, i) => i + 1).map((p) => (
            <button
              key={p}
              onClick={() => setPage(p)}
              className={
                p === page
                  ? `${btnPrimary} px-2 py-1 text-xs`
                  : `${btnNeutral} px-2 py-1 text-xs`
              }
            >
              {p}
            </button>
          ))}
          <button
            onClick={() => page < meta.totalPages && setPage(page + 1)}
            disabled={page >= meta.totalPages}
            className={`${btnNeutral} px-2 py-1 text-xs`}
          >
            Next
          </button>
        </div>
      )}
    </div>
  )
}
