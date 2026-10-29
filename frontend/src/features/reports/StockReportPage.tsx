import { App, Tabs } from 'antd'
import { useEffect, useState } from 'react'
import { ApiError } from '../../api/client'
import { PageHeader } from '../../components/layout/PageHeader'
import { categoryApi } from '../products/product-api'
import type { Category } from '../products/product-api'
import { warehouseApi } from '../warehouses/warehouse-api'
import type { Warehouse } from '../warehouses/warehouse-api'
import { StockByWarehouseTab } from './StockByWarehouseTab'
import { StockMovementTab } from './StockMovementTab'

/** Báo cáo tồn kho (BR-RPT-04..06): tồn hiện tại theo kho và nhập – xuất – tồn theo kỳ, in A4. */
export function StockReportPage() {
  const { message } = App.useApp()
  const [categories, setCategories] = useState<Category[]>([])
  const [warehouses, setWarehouses] = useState<Warehouse[]>([])

  useEffect(() => {
    const failed = (fallback: string) => (err: unknown) =>
      message.error(err instanceof ApiError ? err.message : fallback)
    categoryApi
      .listForSelect()
      .then((page) => setCategories(page.items))
      .catch(failed('Không tải được danh mục'))
    // Gồm cả kho đã ngừng: hàng cũ ở đó vẫn có trong sổ
    warehouseApi
      .list({ page: 0, size: 100 })
      .then((page) => setWarehouses(page.items))
      .catch(failed('Không tải được danh sách kho'))
  }, [message])

  return (
    <>
      <PageHeader title="Báo cáo tồn kho" description="Hàng đang nằm ở kho nào, và nhập – xuất – tồn trong từng kỳ" />
      <Tabs
        destroyOnHidden={false}
        items={[
          { key: 'by-warehouse', label: 'Tồn theo kho', children: <StockByWarehouseTab categories={categories} /> },
          {
            key: 'movement',
            label: 'Nhập – xuất – tồn',
            children: <StockMovementTab categories={categories} warehouses={warehouses} />,
          },
        ]}
      />
    </>
  )
}
