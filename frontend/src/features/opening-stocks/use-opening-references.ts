import { useEffect, useState } from 'react'
import type { Product } from '../products/product-api'
import { productApi } from '../products/product-api'
import type { Warehouse } from '../warehouses/warehouse-api'
import { warehouseApi } from '../warehouses/warehouse-api'

/** Tối đa 100 bản ghi mỗi danh sách (giới hạn size của backend) — như các danh sách tham chiếu khác của MVP. */
const REFERENCE_SIZE = 100

export interface OpeningReferences {
  /** Gồm cả kho đã ngừng: bộ lọc cần tìm được phiếu cũ; form tự lọc lấy kho đang hoạt động */
  warehouses: Warehouse[]
  /** Chỉ SP đang kinh doanh (BR-OPN-04) */
  products: Product[]
}

/** Kho và SP cho ô lọc của danh sách và ô chọn trong form — tải MỘT lần ở trang danh sách. Lỗi → danh sách rỗng. */
export function useOpeningReferences(): OpeningReferences {
  const [references, setReferences] = useState<OpeningReferences>({ warehouses: [], products: [] })

  useEffect(() => {
    let ignore = false
    const query = { page: 0, size: REFERENCE_SIZE }
    Promise.all([warehouseApi.list(query), productApi.list(query, { active: true })])
      .then(([warehouses, products]) => {
        if (!ignore) setReferences({ warehouses: warehouses.items, products: products.items })
      })
      .catch(() => {
        // giữ danh sách rỗng
      })
    return () => {
      ignore = true
    }
  }, [])

  return references
}
