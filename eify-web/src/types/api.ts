export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

export interface ListStat {
  key: string
  label: string
  value: number | string
  class?: string
}
