import { Alert, Button, Table } from 'antd'
import type { TableProps } from 'antd'
import type { ReactNode } from 'react'
import type { PageResponse } from '../../api/types'

interface DataTableProps<T> {
  columns: TableProps<T>['columns']
  page: PageResponse<T> | null
  loading: boolean
  rowKey: keyof T & string
  /** page bắt đầu từ 0, theo backend */
  onPageChange: (page: number, size: number) => void
  /** Lỗi lần tải gần nhất → hiện khung lỗi + nút thử lại phía trên bảng */
  error?: string | null
  onRetry?: () => void
  /** Nội dung khi không có dòng nào (ví dụ "Không có kết quả phù hợp") */
  emptyText?: ReactNode
  /** Chiều cao các phần thêm phía trên bảng trong khung (ví dụ thanh lọc) */
  extraReservedHeight?: number
}

/**
 * Phần chiều cao màn hình KHÔNG thuộc thân bảng: header (64) + padding vùng nội dung (2×24)
 * + padding khung trắng (2×24) + PageHeader (~72) + tiêu đề cột (~56) + thanh phân trang (~64).
 * Sửa layout (AppLayout, PageHeader) thì cập nhật lại số này.
 */
const NON_TABLE_HEIGHT = 352

/**
 * Bảng có phân trang theo contract-spec (ADR-0002, tầng 2).
 * Ant Design đánh số trang từ 1, Spring từ 0 → chuyển đổi tại đây, màn hình không phải lo.
 * Thân bảng cuộn bên trong: tiêu đề cột và thanh phân trang luôn nhìn thấy.
 * Kiểu dáng (nền tiêu đề, hover, đường kẻ) cấu hình tập trung ở theme/theme.ts.
 */
export function DataTable<T extends object>({
  columns,
  page,
  loading,
  rowKey,
  onPageChange,
  error,
  onRetry,
  emptyText,
  extraReservedHeight = 0,
}: DataTableProps<T>) {
  const reserved = NON_TABLE_HEIGHT + extraReservedHeight + (error ? 56 : 0)

  return (
    <>
      {error && (
        <Alert
          type="error"
          showIcon
          title={error}
          style={{ marginBottom: 16 }}
          action={
            onRetry && (
              <Button size="small" onClick={onRetry}>
                Thử lại
              </Button>
            )
          }
        />
      )}
      <Table<T>
        columns={columns}
        dataSource={page?.items ?? []}
        rowKey={rowKey}
        loading={loading}
        locale={emptyText ? { emptyText } : undefined}
        // x: màn hình hẹp thì cuộn ngang thay vì ép chữ; y: cuộn dọc trong khung, tối thiểu 240px
        scroll={{ x: 'max-content', y: `max(240px, calc(100vh - ${reserved}px))` }}
        pagination={{
          current: (page?.pagination.page ?? 0) + 1,
          pageSize: page?.pagination.size ?? 20,
          total: page?.pagination.totalElements ?? 0,
          showSizeChanger: true,
          pageSizeOptions: [10, 20, 50, 100], // backend giới hạn size tối đa 100
          showTotal: (total, [from, to]) => (total > 0 ? `${from}–${to} / ${total} bản ghi` : ''),
          onChange: (current, size) => onPageChange(current - 1, size),
        }}
      />
    </>
  )
}
