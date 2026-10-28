import { App, Drawer, Tabs, Typography } from 'antd'
import { useCallback, useEffect, useState } from 'react'
import { ApiError } from '../../api/client'
import { CatalogPanel } from './CatalogPanel'
import { categoryApi, productUnitApi } from './product-api'
import type { Category, ProductUnit } from './product-api'

interface ProductConfigDrawerProps {
  open: boolean
  /** Đóng khung; `changed` = có sửa danh mục / đơn vị → trang tải lại ô chọn và bộ lọc */
  onClose: (changed: boolean) => void
}

const weightFormat = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 3 })

/**
 * Cấu hình sản phẩm: các danh mục phụ mà sản phẩm bắt buộc chọn — danh mục (BR-CAT-01..04) và đơn vị tính
 * (BR-UNIT-01..04). Gộp vào một khung trên trang Sản phẩm thay vì thêm mục navbar.
 */
export function ProductConfigDrawer({ open, onClose }: ProductConfigDrawerProps) {
  const { message } = App.useApp()
  // null = chưa tải lần nào → bảng hiện vòng xoay; các lần mở sau giữ dữ liệu cũ trong lúc tải lại
  const [categories, setCategories] = useState<Category[] | null>(null)
  const [units, setUnits] = useState<ProductUnit[] | null>(null)
  const [changed, setChanged] = useState(false)

  const loadCategories = useCallback(() => {
    categoryApi
      .listForSelect()
      .then((page) => setCategories(page.items))
      .catch((err: unknown) => message.error(err instanceof ApiError ? err.message : 'Không tải được danh mục'))
  }, [message])

  const loadUnits = useCallback(() => {
    productUnitApi
      .listForSelect()
      .then((page) => setUnits(page.items))
      .catch((err: unknown) => message.error(err instanceof ApiError ? err.message : 'Không tải được đơn vị tính'))
  }, [message])

  useEffect(() => {
    if (!open) return
    loadCategories()
    loadUnits()
  }, [open, loadCategories, loadUnits])

  const close = () => {
    onClose(changed)
    setChanged(false)
  }

  const afterChange = (reload: () => void) => () => {
    setChanged(true)
    reload()
  }

  return (
    <Drawer open={open} title="Cấu hình sản phẩm" size={560} onClose={close} destroyOnHidden>
      <Tabs
        items={[
          {
            key: 'categories',
            label: 'Danh mục',
            children: (
              <CatalogPanel<Category>
                items={categories ?? []}
                loading={categories === null}
                noun="danh mục"
                placeholder="Danh mục mới, ví dụ: Cám cá"
                maxLength={100}
                onCreate={categoryApi.create}
                onUpdate={categoryApi.update}
                onChanged={afterChange(loadCategories)}
              />
            ),
          },
          {
            key: 'units',
            label: 'Đơn vị tính',
            children: (
              <CatalogPanel<ProductUnit>
                items={units ?? []}
                loading={units === null}
                noun="đơn vị"
                placeholder="Đơn vị mới, ví dụ: Túi, Thùng"
                maxLength={50}
                onCreate={productUnitApi.create}
                onUpdate={productUnitApi.update}
                onChanged={afterChange(loadUnits)}
                extraColumns={[
                  {
                    title: 'Khối lượng',
                    dataIndex: 'fixedNetWeightKg',
                    width: 150,
                    // BR-UNIT-03: chỉ đơn vị hệ thống có khối lượng cố định; còn lại nhập theo từng SP
                    render: (fixed?: number) => (
                      <Typography.Text type="secondary">
                        {fixed == null ? 'Nhập theo từng SP' : `Luôn ${weightFormat.format(fixed)} kg`}
                      </Typography.Text>
                    ),
                  },
                ]}
              />
            ),
          },
        ]}
      />
    </Drawer>
  )
}
