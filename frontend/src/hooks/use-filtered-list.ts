import { useCallback, useState } from 'react'
import type { PageQuery, PageResponse } from '../api/types'
import type { FilterValues } from '../utils/filters'
import { hasActiveFilters } from '../utils/filters'
import { usePagedList } from './use-paged-list'

/**
 * usePagedList + bộ lọc gửi lên backend; dùng chung cho các màn hình danh sách có FilterBar.
 * `fetchList` phải ổn định (ví dụ `supplierApi.list`), không tạo hàm mới mỗi lần render.
 * Đổi bộ lọc → về trang đầu.
 */
export function useFilteredList<T, F extends FilterValues>(
  fetchList: (query: PageQuery, filters: F) => Promise<PageResponse<T>>,
  initialSize = 20,
) {
  const [filters, setFilters] = useState<F>({} as F)
  // useCallback: chỉ tạo hàm mới khi bộ lọc đổi → usePagedList tự tải lại đúng lúc
  const fetchPage = useCallback((query: PageQuery) => fetchList(query, filters), [fetchList, filters])
  const list = usePagedList(fetchPage, initialSize)
  const { changePage, query } = list

  const applyFilters = useCallback(
    (next: F) => {
      setFilters(next)
      changePage(0, query.size)
    },
    [changePage, query.size],
  )

  return { ...list, filters, applyFilters, filtering: hasActiveFilters(filters) }
}
