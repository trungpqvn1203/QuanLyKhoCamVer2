/**
 * Tiền VND (BR-MON-03..05). Frontend chỉ tính để HIỂN THỊ TẠM khi đang nhập phiếu;
 * số chính thức luôn là số backend trả về (BigDecimal) sau khi lưu.
 */

const vndFormat = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 2 })

/** 2037037 → "2.037.037 đ"; 12345.5 → "12.345,5 đ". */
export function formatVnd(amount: number): string {
  return `${vndFormat.format(amount)} đ`
}

/**
 * Thành tiền dòng = số lượng × đơn giá, làm tròn HALF_UP về đồng — cùng quy tắc với
 * StockDocumentItem.lineAmount ở backend (BR-MON-05).
 * Tính bằng số nguyên "xu" (đơn giá × 100, Math.round) để tránh sai số dấu phẩy động:
 * 7 × 0.15 trong JavaScript ra 1.0499999…, làm tròn sẽ thành 1 đ hay 0 đ tùy may rủi;
 * 15 xu × 7 = 105 xu thì luôn làm tròn đúng lên 1 đ. Đơn giá tối đa 2 chữ số lẻ (backend kiểm tra).
 * Ví dụ 3 × 12.345,50 = 37.036,50 → 37.037.
 */
export function lineAmount(quantity: number, unitPrice: number): number {
  const cents = Math.round(unitPrice * 100)
  return Math.floor((cents * quantity + 50) / 100)
}

/**
 * Hiển thị trong ô nhập đơn giá kiểu Việt Nam: "1234567.5" → "1.234.567,5".
 * Ant Design gọi hàm này với chuỗi số chuẩn (dấu chấm thập phân) do parsePriceInput trả về.
 */
export function formatPriceInput(value: string | number | undefined): string {
  if (value === undefined || value === '') return ''
  const [integer, decimal] = String(value).split('.')
  const grouped = integer.replace(/\B(?=(\d{3})+(?!\d))/g, '.')
  return decimal === undefined ? grouped : `${grouped},${decimal}`
}

/** Ngược lại: "1.234.567,5" (người dùng gõ) → "1234567.5". */
export function parsePriceInput(text: string | undefined): string {
  return (text ?? '').replace(/\./g, '').replace(',', '.')
}
