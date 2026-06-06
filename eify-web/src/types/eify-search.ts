export interface SearchFieldOption {
  label: string
  value: string | number
}

export interface SearchField {
  key: string
  label: string
  description?: string
  inputType: 'text' | 'select'
  placeholder?: string
  options?: SearchFieldOption[]
  operators?: SearchOperator[]
  enabled?: boolean
}

export interface SearchOperator {
  value: string
  label: string
}

export interface SearchCondition {
  field: string
  fieldLabel: string
  operator: string
  operatorLabel?: string
  value: string | number
  displayValue: string
}

export interface AdvancedSearchOptions {
  fields?: SearchField[]
  showShortcut?: boolean
}
