import { useCallback, useEffect, useState } from 'react'
import { ApiError } from '../api/client'
import type { PageQuery, PageResponse } from '../api/types'

/**
 * Tải danh sách có phân trang từ API; dùng chung cho mọi màn hình danh sách.
 * - `reload()` gọi lại trang hiện tại (sau khi thêm/sửa/ngừng hoạt động).
 * - `fetchPage` đổi (ví dụ bộ lọc đổi) → tự tải lại; muốn về trang đầu thì gọi `changePage(0, size)`.
 * - `error` khác null khi lần tải gần nhất thất bại; màn hình hiển thị kèm nút thử lại.
 */
export function usePagedList<T>(fetchPage: (query: PageQuery) => Promise<PageResponse<T>>, initialSize = 20) {
  const [query, setQuery] = useState<PageQuery>({ page: 0, size: initialSize })
  const [reloadCount, setReloadCount] = useState(0)
  const [page, setPage] = useState<PageResponse<T> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    // Bỏ qua kết quả của request cũ: bấm trang 2 rồi trang 3 thật nhanh,
    // response trang 2 về sau cũng không được ghi đè bảng
    let ignore = false
    fetchPage(query)
      .then((result) => {
        if (ignore) return
        setPage(result)
        setError(null)
      })
      .catch((err: unknown) => {
        if (!ignore) setError(err instanceof ApiError ? err.message : 'Không tải được dữ liệu')
      })
      .finally(() => {
        if (!ignore) setLoading(false)
      })
    return () => {
      ignore = true
    }
  }, [fetchPage, query, reloadCount])

  const changePage = useCallback((pageIndex: number, size: number) => {
    setLoading(true)
    // Đổi số dòng/trang thì quay về trang đầu
    setQuery((prev) => ({ page: size === prev.size ? pageIndex : 0, size }))
  }, [])

  const reload = useCallback(() => {
    setLoading(true)
    setReloadCount((count) => count + 1)
  }, [])

  return { page, query, loading, error, changePage, reload }
}
