import { DownloadOutlined, InboxOutlined } from '@ant-design/icons'
import { Alert, App, Button, Flex, Modal, Table, Typography, Upload } from 'antd'
import type { TableProps } from 'antd'
import { useState } from 'react'
import { ApiError } from '../../api/client'
import { fileErrorMessage, toImportRowErrors } from './import-api'
import type { ImportApi, ImportPreview, ImportRowError } from './import-api'

interface ImportExcelModalProps {
  open: boolean
  /** Tên loại dữ liệu, chữ thường: "sản phẩm" */
  noun: string
  api: ImportApi
  onClose: () => void
  /** Nhập xong — trang danh sách tải lại */
  onImported: () => void
}

const errorColumns: TableProps<ImportRowError>['columns'] = [
  { title: 'Dòng', dataIndex: 'row', width: 70, align: 'right' },
  { title: 'Cột', dataIndex: 'column', width: 160, render: (column?: string) => column ?? 'Cả dòng' },
  { title: 'Lỗi', dataIndex: 'message' },
]

/**
 * Nhập hàng loạt từ Excel (BR-XLS): ① tải file mẫu → ② chọn file → hệ thống kiểm tra ngay, chưa ghi gì
 * → ③ không còn lỗi mới bấm được "Nhập". Có lỗi thì sửa trong Excel rồi chọn lại file.
 */
export function ImportExcelModal({ open, noun, api, onClose, onImported }: ImportExcelModalProps) {
  const { message, notification } = App.useApp()
  const [file, setFile] = useState<File | null>(null)
  const [preview, setPreview] = useState<ImportPreview | null>(null)
  const [fileError, setFileError] = useState<string | null>(null)
  const [checking, setChecking] = useState(false)
  const [importing, setImporting] = useState(false)
  const [downloading, setDownloading] = useState(false)

  const reset = () => {
    setFile(null)
    setPreview(null)
    setFileError(null)
  }

  const close = () => {
    if (importing) return
    reset()
    onClose()
  }

  const downloadTemplate = async () => {
    setDownloading(true)
    try {
      await api.downloadTemplate()
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : 'Không tải được file mẫu')
    } finally {
      setDownloading(false)
    }
  }

  const check = async (selected: File) => {
    reset()
    setFile(selected)
    setChecking(true)
    try {
      setPreview(await api.preview(selected))
    } catch (err) {
      setFileError(fileErrorMessage(err) ?? (err instanceof ApiError ? err.message : 'Không kiểm tra được file'))
    } finally {
      setChecking(false)
    }
  }

  const runImport = async () => {
    if (!file || !preview) return
    setImporting(true)
    try {
      const result = await api.commit(file)
      notification.success({ title: `Đã nhập ${result.importedCount} ${noun}` })
      reset()
      onImported()
      onClose()
    } catch (err) {
      // Dữ liệu đổi kể từ lúc kiểm tra (ví dụ người khác vừa thêm bản ghi trùng) → hiện lỗi mới, chưa nhập gì
      const rowErrors = toImportRowErrors(
        err,
        (field) => preview.errors.find((error) => error.field === field)?.column,
      )
      if (rowErrors.length > 0) {
        const badRows = new Set(rowErrors.map((error) => error.row)).size
        setPreview({ ...preview, validRows: preview.totalRows - badRows, errors: rowErrors })
      } else {
        setFileError(fileErrorMessage(err) ?? (err instanceof ApiError ? err.message : `Không nhập được ${noun}`))
      }
    } finally {
      setImporting(false)
    }
  }

  const ready = preview !== null && preview.errors.length === 0

  return (
    <Modal
      open={open}
      title={`Nhập ${noun} từ Excel`}
      width={760}
      onCancel={close}
      maskClosable={false}
      destroyOnHidden
      footer={[
        <Button key="cancel" onClick={close} disabled={importing}>
          Hủy
        </Button>,
        <Button key="import" type="primary" disabled={!ready} loading={importing} onClick={runImport}>
          {ready ? `Nhập ${preview.totalRows} ${noun}` : 'Nhập'}
        </Button>,
      ]}
    >
      <Flex vertical gap={16}>
        <Flex justify="space-between" align="center" gap={12}>
          <Typography.Text>
            <Typography.Text strong>Bước 1.</Typography.Text> Tải file mẫu, điền dữ liệu từ dòng 2 (xem sheet
            “Hướng dẫn”). Không đổi tên hay thứ tự cột.
          </Typography.Text>
          <Button icon={<DownloadOutlined />} loading={downloading} onClick={downloadTemplate}>
            Tải file mẫu
          </Button>
        </Flex>

        <div>
          <Typography.Text strong>Bước 2.</Typography.Text> Chọn file đã điền — hệ thống kiểm tra ngay, chưa ghi gì.
          <Upload.Dragger
            accept=".xlsx"
            maxCount={1}
            showUploadList={false}
            disabled={checking || importing}
            // Không để Upload tự gửi file: trả false rồi tự gọi API kiểm tra
            beforeUpload={(selected) => {
              void check(selected)
              return false
            }}
            style={{ marginTop: 8 }}
          >
            <p className="ant-upload-drag-icon">
              <InboxOutlined />
            </p>
            <p className="ant-upload-text">{file ? file.name : 'Bấm hoặc kéo file .xlsx vào đây'}</p>
            <p className="ant-upload-hint">Tối đa 1.000 dòng, 2 MB. Chọn file khác để kiểm tra lại.</p>
          </Upload.Dragger>
        </div>

        {checking && <Alert type="info" title="Đang kiểm tra file…" />}
        {fileError && <Alert type="error" showIcon title={fileError} />}
        {preview && ready && (
          <Alert
            type="success"
            showIcon
            title={`${preview.totalRows} dòng hợp lệ. Bấm “Nhập ${preview.totalRows} ${noun}” để lưu.`}
          />
        )}
        {preview && !ready && (
          <>
            <Alert
              type="error"
              showIcon
              title={`${preview.totalRows - preview.validRows}/${preview.totalRows} dòng có lỗi — chưa nhập dòng nào`}
              description="Sửa các dòng dưới đây trong Excel, lưu lại rồi chọn lại file. Hệ thống chỉ nhập khi cả file không còn lỗi."
            />
            <Table<ImportRowError>
              size="small"
              rowKey={(error) => `${error.row}-${error.field ?? ''}-${error.message}`}
              columns={errorColumns}
              dataSource={preview.errors}
              pagination={{ pageSize: 8, hideOnSinglePage: true, showSizeChanger: false }}
            />
          </>
        )}
      </Flex>
    </Modal>
  )
}
