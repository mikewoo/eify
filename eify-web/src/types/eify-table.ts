export interface TableColumn {
  prop: string
  label: string
  width?: number | string
  minWidth?: number | string
  fixed?: 'left' | 'right' | true
  sortable?: boolean
  align?: 'left' | 'center' | 'right'
  showOverflowTooltip?: boolean
  slot?: string
  render?: (row: any, column: any, index: number) => any
}

export interface PaginationParams {
  page: number
  size: number
}

export interface PageResult<T> {
  records: T[]
  total: number
}
