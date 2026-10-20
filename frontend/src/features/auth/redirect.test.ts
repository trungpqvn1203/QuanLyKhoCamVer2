import { describe, expect, it } from 'vitest'
import { HOME_PATH, safeRedirect } from './redirect'

describe('safeRedirect', () => {
  it('giữ đường dẫn nội bộ kèm query', () => {
    expect(safeRedirect('/sales-orders?status=DRAFT')).toBe('/sales-orders?status=DRAFT')
  })

  it('chặn URL ngoài (open redirect), kể cả dạng //tên-miền', () => {
    expect(safeRedirect('https://kho-gia.vn')).toBe(HOME_PATH)
    expect(safeRedirect('//kho-gia.vn')).toBe(HOME_PATH)
    expect(safeRedirect('/\\kho-gia.vn')).toBe(HOME_PATH)
  })

  it('không có / quay lại chính trang đăng nhập → trang chủ', () => {
    expect(safeRedirect(null)).toBe(HOME_PATH)
    expect(safeRedirect('/login?redirect=/x')).toBe(HOME_PATH)
  })
})
