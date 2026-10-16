import { Table } from 'antd'
import type { TableProps } from 'antd'
import type { PageResponse } from '../../api/types'

interface DataTableProps<T> {
  columns: TableProps<T>['columns']
  page: PageResponse<T> | null
  loading: boolean
  rowKey: keyof T & string
  /** page bắt đầu từ 0, theo backend */
  onPageChange: (page: number, size: number) => void
}

/**
 * Bảng có phân trang theo contract-spec (ADR-0002, tầng 2).
 * Ant Design đánh số trang từ 1, Spring từ 0 → chuyển đổi tại đây, màn hình không phải lo.
 */
export function DataTable<T extends object>({ columns, page, loading, rowKey, onPageChange }: DataTableProps<T>) {
  return (
    <Table<T>
      columns={columns}
      dataSource={page?.items ?? []}
      rowKey={rowKey}
      loading={loading}
      scroll={{ x: 'max-content' }}
      pagination={{
        current: (page?.pagination.page ?? 0) + 1,
        pageSize: page?.pagination.size ?? 20,
        total: page?.pagination.totalElements ?? 0,
        showSizeChanger: true,
        pageSizeOptions: [10, 20, 50, 100], // backend giới hạn size tối đa 100
        showTotal: (total) => `Tổng ${total} bản ghi`,
        onChange: (current, size) => onPageChange(current - 1, size),
      }}
    />
  )
}
