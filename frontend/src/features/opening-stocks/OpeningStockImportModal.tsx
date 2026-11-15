import { Col, Form, Input, Row, Select } from 'antd'
import { useState } from 'react'
import { todayIso } from '../../utils/format'
import { ImportExcelModal } from '../imports/ImportExcelModal'
import type { ImportApi } from '../imports/import-api'
import type { Warehouse } from '../warehouses/warehouse-api'
import { openingStockApi } from './opening-stock-api'
import type { OpeningStock } from './opening-stock-api'

interface OpeningStockImportModalProps {
  open: boolean
  warehouses: Warehouse[]
  /** Có phiếu nháp đang mở → thay toàn bộ dòng của phiếu đó; null/undefined → tạo phiếu nháp mới */
  document?: OpeningStock | null
  initialWarehouseId?: number
  initialDocumentDate?: string
  initialNote?: string
  onClose: () => void
  onSuccess: (saved: OpeningStock) => void
}

/**
 * Nhập dòng tồn đầu kỳ từ Excel (BR-OPN-Q01): dùng chung quy trình 2 bước của ImportExcelModal.
 * Khi tạo phiếu mới từ Excel, người dùng chọn kho, ngày đầu kỳ và ghi chú trước khi lưu nháp.
 */
export function OpeningStockImportModal({
  open,
  warehouses,
  document,
  initialWarehouseId,
  initialDocumentDate,
  initialNote,
  onClose,
  onSuccess,
}: OpeningStockImportModalProps) {
  const [warehouseId, setWarehouseId] = useState<number | undefined>(initialWarehouseId)
  const [documentDate, setDocumentDate] = useState<string>(initialDocumentDate || todayIso())
  const [note, setNote] = useState<string>(initialNote ?? '')

  const isReplacingDraft = Boolean(document)
  const warehouseOptions = warehouses
    .filter((warehouse) => warehouse.active)
    .map((warehouse) => ({ value: warehouse.id, label: `${warehouse.code} · ${warehouse.name}` }))

  const dateInvalid = !isReplacingDraft && (!documentDate || documentDate > todayIso())
  const commitDisabled = !isReplacingDraft && (!warehouseId || dateInvalid)

  const api: ImportApi = {
    downloadTemplate: openingStockApi.downloadImportTemplate,
    preview: openingStockApi.previewImport,
    commit: async (file) => {
      const saved = document
        ? await openingStockApi.importIntoDraft(document.id, file)
        : await openingStockApi.importNewDraft({
            file,
            warehouseId: warehouseId!,
            documentDate,
            note,
          })
      onSuccess(saved)
      return { importedCount: saved.lineCount }
    },
  }

  const extraContent = !isReplacingDraft ? (
    <Form layout="vertical">
      <Row gutter={12}>
        <Col xs={24} md={14}>
          <Form.Item label="Kho" required style={{ marginBottom: 12 }}>
            <Select<number>
              placeholder="Chọn kho nhập tồn đầu kỳ"
              options={warehouseOptions}
              value={warehouseId}
              onChange={setWarehouseId}
              showSearch={{ optionFilterProp: 'label' }}
            />
          </Form.Item>
        </Col>
        <Col xs={24} md={10}>
          <Form.Item
            label="Ngày tồn đầu kỳ"
            required
            validateStatus={dateInvalid ? 'error' : undefined}
            help={dateInvalid ? 'Không được chọn ngày tương lai' : undefined}
            style={{ marginBottom: 12 }}
          >
            <Input type="date" max={todayIso()} value={documentDate} onChange={(e) => setDocumentDate(e.target.value)} />
          </Form.Item>
        </Col>
      </Row>
      <Form.Item label="Ghi chú" style={{ marginBottom: 0 }}>
        <Input
          maxLength={500}
          value={note}
          onChange={(e) => setNote(e.target.value)}
          placeholder="Ví dụ: số liệu kiểm đếm ngày 31/10"
        />
      </Form.Item>
    </Form>
  ) : undefined

  return (
    <ImportExcelModal
      open={open}
      noun="dòng tồn đầu kỳ"
      api={api}
      warningMessage={isReplacingDraft ? 'Dữ liệu trong file sẽ thay thế toàn bộ dòng hiện tại của phiếu nháp này.' : undefined}
      extraContent={extraContent}
      commitDisabled={commitDisabled}
      onClose={onClose}
      onImported={() => {}}
    />
  )
}

