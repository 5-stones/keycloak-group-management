import type { ClassNamesConfig, StylesConfig } from 'react-select'

// Shared react-select configuration. Both the role pickers and the
// ConfigPage permission editor use the same visual styling and the same
// portal target.

export interface RoleOption {
  value: string
  label: string
}

export const SELECT_CLASS_NAMES: ClassNamesConfig<RoleOption, boolean> = {
  control: () => 'rs__control',
  multiValue: () => 'rs__multi-value',
  multiValueLabel: () => 'rs__multi-value__label',
  multiValueRemove: () => 'rs__multi-value__remove',
  option: ({ isFocused, isSelected }) =>
    `${isFocused ? 'rs__option--is-focused' : ''} ${isSelected ? 'rs__option--is-selected' : ''}`,
}

// Table wrappers use `overflow-hidden` for rounded corners, which clips the
// dropdown when it opens past the row. Rendering the menu in a portal keeps
// it visible and stacks above any other UI.
export const SELECT_STYLES: StylesConfig<RoleOption, boolean> = {
  menu: (base) => ({ ...base, zIndex: 9999 }),
  menuPortal: (base) => ({ ...base, zIndex: 9999 }),
}

export const SELECT_PORTAL_TARGET: HTMLElement | null =
  typeof document !== 'undefined' ? document.body : null

export function rolesToOptions(roles: readonly string[] | null | undefined): RoleOption[] {
  return (roles || []).map((r) => ({ value: r, label: r }))
}
