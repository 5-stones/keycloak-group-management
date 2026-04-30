import Select from 'react-select'
import {
  SELECT_CLASS_NAMES,
  SELECT_STYLES,
  SELECT_PORTAL_TARGET,
  rolesToOptions,
  type RoleOption,
} from '../lib/select'
import type { Vocabulary } from '../api'

interface RoleMultiSelectProps {
  vocabulary: Vocabulary
  value: Set<string>
  onChange: (next: Set<string>) => void
  ariaLabel: string
  minWidth?: number
}

export default function RoleMultiSelect({
  vocabulary,
  value,
  onChange,
  ariaLabel,
  minWidth = 220,
}: RoleMultiSelectProps) {
  const options = rolesToOptions(vocabulary.roles)
  const selected = options.filter((o) => value.has(o.value))
  return (
    <Select<RoleOption, true>
      isMulti
      isClearable={false}
      classNamePrefix="rs"
      classNames={SELECT_CLASS_NAMES}
      menuPortalTarget={SELECT_PORTAL_TARGET}
      aria-label={ariaLabel}
      options={options}
      value={selected}
      onChange={(opts) => onChange(new Set((opts || []).map((o) => o.value)))}
      placeholder="Select roles…"
      styles={{ ...SELECT_STYLES, container: (base) => ({ ...base, minWidth, flex: 1 }) }}
    />
  )
}
