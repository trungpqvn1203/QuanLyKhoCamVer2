import { useEffect, useState } from 'react'
import type { Customer } from '../customers/customer-api'
import { customerApi } from '../customers/customer-api'
import type { Warehouse } from '../warehouses/warehouse-api'
import { warehouseApi } from '../warehouses/warehouse-api'

/** Kho chỉ có vài kho nên một trang 100 là đủ; khách hàng có thể > 100 (production 600+ khách) nên dùng `customerApi.listAll()`. */
const WAREHOUSE_REFERENCE_SIZE = 100

export interface SalesReferences {
  customers: Customer[]
  warehouses: Warehouse[]
}

/**
 * Khách và kho cho ô lọc danh sách và ô chọn trong form phiếu bán — tải một lần ở trang.
 * Lấy cả bản đã ngừng (bộ lọc cần tìm phiếu cũ); form tự lọc bản đang hoạt động. Lỗi tải → danh sách rỗng.
 *
 * Phương án hiện tại (Phương án 1): `customerApi.listAll()` tải hết các trang (mỗi trang 100 bản ghi) và lọc tại trình duyệt
 * (Ant Design `<Select>` có sẵn virtual scroll, mượt tới vài nghìn khách).
 * Đề xuất mở rộng sau này (Phương án 2 — Remote Search): khi danh sách khách hàng tăng lên hàng chục nghìn, chuyển sang
 * tìm kiếm từ xa có debounce (`GET /api/customers?keyword=...&size=50`) kết hợp tải lẻ khách đang chọn khi mở sửa phiếu.
 */
export function useSalesReferences(): SalesReferences {
  const [references, setReferences] = useState<SalesReferences>({ customers: [], warehouses: [] })

  useEffect(() => {
    let ignore = false
    Promise.all([customerApi.listAll(), warehouseApi.list({ page: 0, size: WAREHOUSE_REFERENCE_SIZE })])
      .then(([customers, warehouses]) => {
        if (!ignore) setReferences({ customers, warehouses: warehouses.items })
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
