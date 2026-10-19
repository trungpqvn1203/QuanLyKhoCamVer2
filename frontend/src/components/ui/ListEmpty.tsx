import { PlusOutlined } from '@ant-design/icons'
import { Button, Empty } from 'antd'

interface ListEmptyProps {
  /** Danh từ của bản ghi, chữ thường: "nhà cung cấp", "sản phẩm", "kho" */
  noun: string
  /** Đang áp dụng bộ lọc → "không có kết quả" + nút xóa lọc; ngược lại → "chưa có dữ liệu" + nút thêm */
  filtering: boolean
  onClearFilters: () => void
  onCreate?: () => void
  createLabel?: string
}

/**
 * Nội dung bảng khi không có dòng nào (ADR-0002, tầng 2).
 * Phân biệt 2 tình huống để người dùng biết bước tiếp theo:
 * danh sách thật sự trống (cần thêm mới) và lọc không ra kết quả (cần nới điều kiện).
 */
export function ListEmpty({ noun, filtering, onClearFilters, onCreate, createLabel }: ListEmptyProps) {
  if (filtering) {
    return (
      <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={`Không có ${noun} phù hợp bộ lọc`}>
        <Button onClick={onClearFilters}>Xóa bộ lọc</Button>
      </Empty>
    )
  }
  return (
    <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={`Chưa có ${noun} nào`}>
      {onCreate && (
        <Button type="primary" icon={<PlusOutlined />} onClick={onCreate}>
          {createLabel ?? 'Thêm mới'}
        </Button>
      )}
    </Empty>
  )
}
