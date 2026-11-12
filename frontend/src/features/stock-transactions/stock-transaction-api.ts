import { apiRequest, withQuery } from '../../api/client'
import type { PageQuery, PageResponse } from '../../api/types'
import { toFilterParams } from '../../utils/filters'

/** IN tăng tồn, OUT giảm tồn — số lượng luôn dương. */
export type StockTransactionType = 'IN' | 'OUT'

/** Loại chứng từ gây ra biến động. */
export type StockDocumentType = 'INBOUND' | 'OUTBOUND' | 'OPENING_STOCK' | 'STOCK_COUNT' | 'TRANSFER'

export const DOCUMENT_TYPE_LABELS: Record<StockDocumentType, string> = {
  INBOUND: 'Phiếu nhập',
  OUTBOUND: 'Phiếu bán',
  OPENING_STOCK: 'Tồn đầu kỳ',
  STOCK_COUNT: 'Kiểm kê',
  TRANSFER: 'Điều chuyển',
}

export const TRANSACTION_TYPE_LABELS: Record<StockTransactionType, string> = {
  IN: 'Tăng tồn',
  OUT: 'Giảm tồn',
}

/** Một dòng lịch sử (contract-spec: "Lịch sử biến động tồn"). Trường null bị bỏ khỏi JSON → tùy chọn. */
export interface StockTransaction {
  id: number
  createdAt: string
  type: StockTransactionType
  quantity: number
  /** Tồn trước/sau TẠI KHO của dòng này, không phải tổng mọi kho */
  quantityBefore: number
  quantityAfter: number
  /** Có = giao dịch đảo, trỏ về giao dịch gốc (BR-INV-06) */
  reversalOfId?: number
  productId: number
  productCode: string
  productName: string
  unitName: string
  warehouseId: number
  warehouseCode: string
  warehouseName: string
  documentId: number
  documentCode: string
  documentType: StockDocumentType
  documentDate: string
  supplierId?: number
  supplierName?: string
  customerId?: number
  customerName?: string
  /** Chỉ phiếu điều chuyển: kho ở ĐẦU KIA của lần chuyển (dòng tại kho nguồn ghi kho đích và ngược lại) */
  counterpartWarehouseId?: number
  counterpartWarehouseName?: string
}

export type StockTransactionFilters = {
  productId?: number
  warehouseId?: number
  type?: StockTransactionType
  documentType?: StockDocumentType
  /** Mã chứng từ */
  keyword?: string
  /** Ngày ghi sổ 'YYYY-MM-DD' (giờ VN) */
  createdFrom?: string
  createdTo?: string
}

export type MovementKind =
  | 'INBOUND'
  | 'INBOUND_REVERSAL'
  | 'SALE'
  | 'SALE_REVERSAL'
  | 'OPENING'
  | 'OPENING_REVERSAL'
  | 'COUNT_SURPLUS'
  | 'COUNT_SHORTAGE'
  | 'COUNT_REVERSAL'
  | 'TRANSFER_OUT'
  | 'TRANSFER_IN'
  | 'TRANSFER_REVERSAL'

export const MOVEMENT_LABELS: Record<MovementKind, string> = {
  COUNT_SURPLUS: 'Kiểm kê thừa',
  COUNT_SHORTAGE: 'Kiểm kê thiếu',
  COUNT_REVERSAL: 'Đảo kiểm kê',
  INBOUND: 'Nhập kho',
  INBOUND_REVERSAL: 'Đảo phiếu nhập',
  SALE: 'Bán hàng',
  SALE_REVERSAL: 'Đảo phiếu bán',
  OPENING: 'Tồn đầu kỳ',
  OPENING_REVERSAL: 'Đảo tồn đầu kỳ',
  TRANSFER_OUT: 'Chuyển đi',
  TRANSFER_IN: 'Chuyển đến',
  TRANSFER_REVERSAL: 'Đảo điều chuyển',
}

/** Nghiệp vụ đọc được cho người dùng, ghép từ loại chứng từ + có phải giao dịch đảo hay không. */
export function movementKind(
  transaction: Pick<StockTransaction, 'documentType' | 'reversalOfId'> & Partial<Pick<StockTransaction, 'type'>>,
): MovementKind {
  const reversal = transaction.reversalOfId !== undefined
  switch (transaction.documentType) {
    case 'INBOUND':
      return reversal ? 'INBOUND_REVERSAL' : 'INBOUND'
    case 'OPENING_STOCK':
      return reversal ? 'OPENING_REVERSAL' : 'OPENING'
    // Kiểm kê có cả hai chiều: thừa (IN) và thiếu (OUT)
    case 'STOCK_COUNT':
      return reversal ? 'COUNT_REVERSAL' : transaction.type === 'IN' ? 'COUNT_SURPLUS' : 'COUNT_SHORTAGE'
    // Một phiếu điều chuyển sinh hai dòng: OUT ở kho nguồn (chuyển đi), IN ở kho đích (chuyển đến)
    case 'TRANSFER':
      return reversal ? 'TRANSFER_REVERSAL' : transaction.type === 'IN' ? 'TRANSFER_IN' : 'TRANSFER_OUT'
    default:
      return reversal ? 'SALE_REVERSAL' : 'SALE'
  }
}

/**
 * Cột "Đối tác" của một dòng lịch sử: NCC, khách, hoặc với điều chuyển là kho ở đầu kia kèm chiều hàng đi.
 * Giao dịch đảo đi ngược chiều giao dịch gốc nên mũi tên suy từ chiều IN/OUT của chính dòng đó.
 */
export function partnerLabel(
  transaction: Pick<StockTransaction, 'type' | 'supplierName' | 'customerName' | 'counterpartWarehouseName'>,
): string | undefined {
  if (transaction.counterpartWarehouseName) {
    return transaction.type === 'OUT'
      ? `→ ${transaction.counterpartWarehouseName}`
      : `← ${transaction.counterpartWarehouseName}`
  }
  return transaction.supplierName ?? transaction.customerName
}

/** Số lượng có dấu theo chiều: IN 20 → 20, OUT 5 → -5. */
export function signedQuantity(transaction: Pick<StockTransaction, 'type' | 'quantity'>): number {
  return transaction.type === 'IN' ? transaction.quantity : -transaction.quantity
}

const BASE = '/api/inventory/transactions'

export const stockTransactionApi = {
  /** Mới ghi sổ lên đầu (theo id — các dòng của một lần hoàn tất phiếu trùng thời điểm) */
  list: ({ page, size }: PageQuery, filters: StockTransactionFilters = {}) =>
    apiRequest<PageResponse<StockTransaction>>(withQuery(BASE, { page, size, sort: 'id,desc', ...toFilterParams(filters) })),
}
