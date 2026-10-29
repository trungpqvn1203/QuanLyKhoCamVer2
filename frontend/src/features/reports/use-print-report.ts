import { App } from 'antd'
import type { ReactElement } from 'react'
import { useState } from 'react'
import { ApiError } from '../../api/client'
import { formatDateTime } from '../../utils/format'
import { storeInfoApi } from '../store/store-info-api'
import type { StoreInfo } from '../store/store-info-api'

type ReportPrints = typeof import('./print/ReportPrints')

/**
 * In báo cáo A4 ngang (BR-RPT-06). Tải lười phần in (react-dom/server + mẫu in) như phiếu bán; lấy thông tin cửa
 * hàng để in đầu trang. `render` nhận module mẫu in đã tải, thông tin cửa hàng và thời điểm in.
 */
export function usePrintReport() {
  const { message } = App.useApp()
  const [printing, setPrinting] = useState(false)

  const print = async (
    title: string,
    render: (prints: ReportPrints, store: StoreInfo, printedAt: string) => ReactElement,
  ) => {
    setPrinting(true)
    try {
      const [{ printA4Landscape }, prints, store] = await Promise.all([
        import('../../utils/print'),
        import('./print/ReportPrints'),
        storeInfoApi.get(),
      ])
      printA4Landscape(title, render(prints, store, formatDateTime(new Date().toISOString())))
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : 'Không in được báo cáo')
    } finally {
      setPrinting(false)
    }
  }

  return { print, printing }
}
