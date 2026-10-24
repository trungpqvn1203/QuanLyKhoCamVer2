import { ApiError, apiDownload, apiRequest } from '../../api/client'

/** Một lỗi trong file (contract-spec: "Nhập Excel"). row = số dòng thật trong Excel (tiêu đề là dòng 1). */
export interface ImportRowError {
  row: number
  field?: string // không có = lỗi cả dòng
  column?: string
  message: string
}

export interface ImportPreview {
  totalRows: number
  validRows: number
  errors: ImportRowError[]
}

export interface ImportResult {
  importedCount: number
}

export interface ImportApi {
  downloadTemplate: () => Promise<void>
  /** Kiểm tra file, không ghi DB. */
  preview: (file: File) => Promise<ImportPreview>
  /** Ghi tất cả dòng hoặc không dòng nào (backend kiểm tra lại từ đầu). */
  commit: (file: File) => Promise<ImportResult>
}

function formData(file: File): FormData {
  const data = new FormData()
  data.append('file', file)
  return data
}

/** Cùng một bộ 3 endpoint cho mọi loại: {base}/import/template, {base}/import/preview, {base}/import. */
export function createImportApi(base: string, templateFileName: string): ImportApi {
  return {
    downloadTemplate: () => apiDownload(`${base}/import/template`, templateFileName),
    preview: (file) => apiRequest<ImportPreview>(`${base}/import/preview`, { method: 'POST', body: formData(file) }),
    commit: (file) => apiRequest<ImportResult>(`${base}/import`, { method: 'POST', body: formData(file) }),
  }
}

/**
 * Lỗi của bước xác nhận (400, details[].field = "rows[12].phone") → cùng dạng lỗi của preview để hiện chung bảng.
 * Xảy ra khi dữ liệu đổi giữa lúc kiểm tra và lúc nhập (ví dụ người khác vừa thêm SĐT trùng).
 * columnOf: tra tiêu đề cột từ field, lấy từ kết quả preview trước đó (backend không gửi lại tiêu đề).
 */
export function toImportRowErrors(error: unknown, columnOf: (field: string) => string | undefined): ImportRowError[] {
  if (!(error instanceof ApiError)) return []
  const rows: ImportRowError[] = []
  for (const detail of error.details) {
    const match = detail.field?.match(/^rows\[(\d+)\](?:\.(.+))?$/)
    if (!match) continue
    const field = match[2]
    rows.push({ row: Number(match[1]), field, column: field ? columnOf(field) : undefined, message: detail.message })
  }
  return rows
}

/** Lỗi ở mức file (sai mẫu, file rỗng, không phải .xlsx...) — details[].field = "file". */
export function fileErrorMessage(error: unknown): string | undefined {
  if (!(error instanceof ApiError)) return undefined
  return error.details.find((detail) => detail.field === 'file')?.message
}
