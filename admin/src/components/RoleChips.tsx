interface RoleChipsProps {
  roles: readonly string[]
}

export default function RoleChips({ roles }: RoleChipsProps) {
  return (
    <div className="flex flex-wrap gap-1">
      {roles.map((r) => (
        <span
          key={r}
          className="inline-flex items-center rounded-md bg-indigo-50 px-2 py-0.5 text-xs font-medium text-indigo-700 ring-1 ring-inset ring-indigo-200"
        >
          {r}
        </span>
      ))}
    </div>
  )
}
