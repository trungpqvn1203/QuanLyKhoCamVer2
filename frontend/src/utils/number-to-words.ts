/**
 * Đọc số tiền VND thành chữ tiếng Việt, in dòng "Bằng chữ" trên phiếu (BR-PRT-02).
 * Ví dụ 5_250_000 → "Năm triệu hai trăm năm mươi nghìn đồng"; 1_005 → "Một nghìn không trăm linh năm đồng".
 * Chỉ nhận số nguyên ≥ 0 (tiền đồng nguyên, BR-MON-03).
 */
const DIGITS = ['không', 'một', 'hai', 'ba', 'bốn', 'năm', 'sáu', 'bảy', 'tám', 'chín']
const BILLION = 1_000_000_000

/**
 * Đọc nhóm 3 chữ số. full = nhóm đứng sau một nhóm khác (1.005 → "không trăm linh năm"),
 * nên phải đọc đủ hàng trăm dù bằng 0.
 */
function readTriple(value: number, full: boolean): string[] {
  const hundreds = Math.floor(value / 100)
  const tens = Math.floor((value % 100) / 10)
  const units = value % 10
  const words: string[] = []

  if (full || hundreds > 0) words.push(DIGITS[hundreds], 'trăm')

  if (tens === 0) {
    // 105 → "một trăm linh năm"; 5 đứng một mình thì không có "linh"
    if (units > 0 && words.length > 0) words.push('linh')
  } else if (tens === 1) {
    words.push('mười')
  } else {
    words.push(DIGITS[tens], 'mươi')
  }

  if (units === 1 && tens > 1) words.push('mốt') // 21 → hai mươi mốt (11 vẫn là mười một)
  else if (units === 4 && tens > 1) words.push('tư') // 24 → hai mươi tư
  else if (units === 5 && tens > 0) words.push('lăm') // 15, 25 → mười lăm, hai mươi lăm
  else if (units > 0) words.push(DIGITS[units])

  return words
}

/** Phần dưới một tỷ: nhóm triệu – nghìn – đơn vị; nhóm bằng 0 bỏ qua (1.000.005 → "một triệu không trăm linh năm"). */
function readBelowBillion(value: number, full: boolean): string[] {
  const groups = [
    { value: Math.floor(value / 1_000_000), scale: 'triệu' },
    { value: Math.floor((value % 1_000_000) / 1000), scale: 'nghìn' },
    { value: value % 1000, scale: '' },
  ]
  const words: string[] = []
  let started = full
  for (const group of groups) {
    if (group.value === 0) continue
    words.push(...readTriple(group.value, started))
    if (group.scale) words.push(group.scale)
    started = true
  }
  return words
}

function readNumber(value: number): string[] {
  if (value < BILLION) return readBelowBillion(value, false)
  // Trên một tỷ: phần tỷ đọc như một số (có thể tới "nghìn tỷ"), phần còn lại đọc đủ hàng trăm
  const rest = value % BILLION
  return [...readNumber(Math.floor(value / BILLION)), 'tỷ', ...(rest > 0 ? readBelowBillion(rest, true) : [])]
}

export function vndToWords(amount: number): string {
  if (!Number.isSafeInteger(amount) || amount < 0) {
    throw new RangeError(`Số tiền không hợp lệ: ${amount}`)
  }
  const text = amount === 0 ? 'không' : readNumber(amount).join(' ')
  return `${text.charAt(0).toUpperCase()}${text.slice(1)} đồng`
}
