import { describe, expect, it } from 'vitest'
import type { PageResponse } from '../api/types'
import { fallbackPageIndex } from './use-paged-list'

function pageOf(itemCount: number, totalPages: number): PageResponse<number> {
  return {
    items: Array.from({ length: itemCount }, (_, index) => index),
    pagination: { page: 0, size: 20, totalElements: 0, totalPages, isFirst: false, isLast: true },
  }
}

describe('fallbackPageIndex', () => {
  it('trang có dữ liệu - giữ nguyên', () => {
    expect(fallbackPageIndex(2, pageOf(1, 3))).toBeNull()
  })

  it('trang đầu rỗng - giữ nguyên để hiện trạng thái trống', () => {
    expect(fallbackPageIndex(0, pageOf(0, 0))).toBeNull()
  })

  it('trang sau rỗng vì bản ghi cuối vừa bị lọc mất - lùi về trang cuối còn dữ liệu', () => {
    expect(fallbackPageIndex(2, pageOf(0, 2))).toBe(1)
  })

  it('không còn bản ghi nào - về trang đầu', () => {
    expect(fallbackPageIndex(3, pageOf(0, 0))).toBe(0)
  })
})
