type Source = 'direct' | 'inherited' | 'realm-admin'

export type RoleEntry = string | { name: string; source: Source }

interface RoleChipsProps {
  roles: readonly RoleEntry[]
}

function normalize(r: RoleEntry): { name: string; source: Source } {
  return typeof r === 'string' ? { name: r, source: 'direct' } : r
}

const STYLES: Record<Source, { className: string; title?: string; prefix?: string }> = {
  direct: {
    className:
      'inline-flex items-center rounded-md bg-indigo-50 px-2 py-0.5 text-xs font-medium text-indigo-700 ring-1 ring-inset ring-indigo-200',
  },
  inherited: {
    className:
      'inline-flex items-center rounded-md bg-white px-2 py-0.5 text-xs font-medium text-indigo-600 ring-1 ring-inset ring-indigo-200 italic',
    title: 'Inherited from a parent group',
    prefix: '↑',
  },
  'realm-admin': {
    className:
      'inline-flex items-center rounded-md bg-amber-50 px-2 py-0.5 text-xs font-medium text-amber-800 ring-1 ring-inset ring-amber-200',
    title: 'You hold this role on every group as a realm administrator',
    prefix: '★',
  },
}

export default function RoleChips({ roles }: RoleChipsProps) {
  return (
    <div className="flex flex-wrap gap-1">
      {roles.map((r) => {
        const { name, source } = normalize(r)
        const style = STYLES[source]
        return (
          <span
            key={`${name}:${source}`}
            className={style.className}
            title={style.title}
          >
            {style.prefix && <span aria-hidden className="mr-1 opacity-70">{style.prefix}</span>}
            {name}
          </span>
        )
      })}
    </div>
  )
}
