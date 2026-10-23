import { describe, expect, it } from 'vitest'
import { ApiError, fileNameFrom } from '../../api/client'
import { fileErrorMessage, toImportRowErrors } from './import-api'

describe('toImportRowErrors', () => {
  const columns: Record<string, string> = { phone: 'Số điện thoại' }

  it('"rows[12].phone" → dòng 12, cột tra từ field; "rows[5]" → lỗi cả dòng', () => {
    const error = new ApiError(400, 'VALIDATION_FAILED', 'File có 2 lỗi', [
      { field: 'rows[12].phone', message: 'SĐT đã thuộc về khách hàng khác' },
      { field: 'rows[5]', message: 'Sản phẩm đơn vị KG phải có khối lượng tịnh bằng 1 kg' },
    ])

    expect(toImportRowErrors(error, (field) => columns[field])).toEqual([
      { row: 12, field: 'phone', column: 'Số điện thoại', message: 'SĐT đã thuộc về khách hàng khác' },
      { row: 5, field: undefined, column: undefined, message: 'Sản phẩm đơn vị KG phải có khối lượng tịnh bằng 1 kg' },
    ])
  })

  it('bỏ qua detail không phải lỗi dòng và lỗi không phải ApiError', () => {
    const error = new ApiError(400, 'VALIDATION_FAILED', 'x', [{ field: 'file', message: 'Sai mẫu' }])

    expect(toImportRowErrors(error, () => undefined)).toEqual([])
    expect(toImportRowErrors(new Error('x'), () => undefined)).toEqual([])
  })
})

describe('fileErrorMessage', () => {
  it('lấy message của detail field "file"', () => {
    const error = new ApiError(400, 'VALIDATION_FAILED', 'x', [{ field: 'file', message: 'File chưa có dòng dữ liệu nào' }])

    expect(fileErrorMessage(error)).toBe('File chưa có dòng dữ liệu nào')
    expect(fileErrorMessage(new ApiError(409, 'DUPLICATE_RESOURCE', 'x'))).toBeUndefined()
  })
})

describe('fileNameFrom', () => {
  it('đọc tên file từ Content-Disposition, có hoặc không có ngoặc kép', () => {
    expect(fileNameFrom('attachment; filename="mau-nhap-san-pham.xlsx"')).toBe('mau-nhap-san-pham.xlsx')
    expect(fileNameFrom('attachment; filename=mau.xlsx')).toBe('mau.xlsx')
    expect(fileNameFrom(null)).toBeUndefined()
  })
})
