/**
 * Số điện thoại — cùng quy tắc với PhoneNumbers ở backend (BR-CUS-06, BR-EMP-03): bỏ khoảng trắng,
 * chấm, gạch rồi phải là số 0 + 9–10 chữ số. Chỉ để báo lỗi sớm trên form — backend vẫn chuẩn hóa và kiểm lại.
 */
export function isValidPhone(raw: string): boolean {
  return /^0\d{9,10}$/.test(raw.replace(/[\s.-]/g, ''))
}

/** "0901234567" → "0901 234 567"; "02834567890" → "0283 456 7890". Chỉ để đọc dễ, không đổi dữ liệu. */
export function formatPhone(phone: string): string {
  return `${phone.slice(0, 4)} ${phone.slice(4, 7)} ${phone.slice(7)}`
}

export const PHONE_FORMAT_MESSAGE = 'Số điện thoại phải bắt đầu bằng 0 và có 10–11 chữ số'
