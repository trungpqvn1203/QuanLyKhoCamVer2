import { App, Drawer, Tabs } from 'antd'
import { useCallback, useEffect, useState } from 'react'
import { ApiError } from '../../api/client'
import { CatalogPanel } from './CatalogPanel'
import { categoryApi } from './product-api'
import type { Category } from './product-api'

interface ProductConfigDrawerProps {
  open: boolean
  /** Đóng khung; `changed` = có sửa danh mục → trang tải lại ô chọn và bộ lọc */
  onClose: (changed: boolean) => void
}

/**
 * Cấu hình sản phẩm: các danh mục phụ mà sản phẩm bắt buộc chọn (BR-CAT-01..04). Gộp vào một khung trên trang
 * Sản phẩm thay vì thêm mục navbar.
 */
export function ProductConfigDrawer({ open, onClose }: ProductConfigDrawerProps) {
  const { message } = App.useApp()
  // null = chưa tải lần nào → bảng hiện vòng xoay; các lần mở sau giữ dữ liệu cũ trong lúc tải lại
  const [categories, setCategories] = useState<Category[] | null>(null)
  const [changed, setChanged] = useState(false)

  const loadCategories = useCallback(() => {
    categoryApi
      .listForSelect()
      .then((page) => setCategories(page.items))
      .catch((err: unknown) => message.error(err instanceof ApiError ? err.message : 'Không tải được danh mục'))
  }, [message])

  useEffect(() => {
    if (open) loadCategories()
  }, [open, loadCategories])

  const close = () => {
    onClose(changed)
    setChanged(false)
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
                onChanged={() => {
                  setChanged(true)
                  loadCategories()
                }}
              />
            ),
          },
        ]}
      />
    </Drawer>
  )
}
