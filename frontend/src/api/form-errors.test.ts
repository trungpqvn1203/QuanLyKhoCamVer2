import { describe, expect, it } from 'vitest'
import { ApiError } from './client'
import { toFieldPath, toFormFieldErrors } from './form-errors'

describe('toFormFieldErrors', () => {
  it('chuyển details[] thành lỗi theo field, gộp nhiều lỗi cùng field', () => {
    const error = new ApiError(400, 'VALIDATION_FAILED', 'Dữ liệu không hợp lệ', [
      { field: 'name', message: 'Tên kho không được để trống' },
      { field: 'address', message: 'Địa chỉ kho không được để trống' },
      { field: 'name', message: 'Tên kho tối đa 200 ký tự' },
    ])

    expect(toFormFieldErrors(error)).toEqual([
      { name: ['name'], errors: ['Tên kho không được để trống', 'Tên kho tối đa 200 ký tự'] },
      { name: ['address'], errors: ['Địa chỉ kho không được để trống'] },
    ])
  })

  it('field trong mảng (phiếu nhập) - thành đường dẫn của Form.List', () => {
    const error = new ApiError(400, 'VALIDATION_FAILED', 'Dữ liệu không hợp lệ', [
      { field: 'lines[1].productId', message: 'Sản phẩm này đã có ở một dòng khác của phiếu' },
    ])

    expect(toFormFieldErrors(error)).toEqual([
      { name: ['lines', 1, 'productId'], errors: ['Sản phẩm này đã có ở một dòng khác của phiếu'] },
    ])
  })

  it('bỏ qua detail không có field', () => {
    const error = new ApiError(400, 'VALIDATION_FAILED', 'Dữ liệu không hợp lệ', [{ message: 'Lỗi chung' }])

    expect(toFormFieldErrors(error)).toEqual([])
  })

  it('lỗi không có details (ví dụ trùng tên) hoặc không phải ApiError - mảng rỗng', () => {
    expect(toFormFieldErrors(new ApiError(409, 'DUPLICATE_RESOURCE', 'Đã tồn tại'))).toEqual([])
    expect(toFormFieldErrors(new Error('khác'))).toEqual([])
  })
})

describe('toFieldPath', () => {
  it.each([
    ['name', ['name']],
    ['documentDate', ['documentDate']],
    ['lines', ['lines']],
    ['lines[0].orderedQuantity', ['lines', 0, 'orderedQuantity']],
    ['lines[12].unitPrice', ['lines', 12, 'unitPrice']],
  ])('%s → %j', (field, expected) => {
    expect(toFieldPath(field)).toEqual(expected)
  })
})
