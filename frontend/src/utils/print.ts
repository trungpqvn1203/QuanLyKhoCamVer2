import type { ReactElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'

/**
 * Khổ giấy cuộn K80 (máy in nhiệt): giấy rộng 80 mm, vùng in khoảng 72 mm, cao tự co theo nội dung.
 * Chữ đen, không nền, không màu — máy in nhiệt chỉ in được đen.
 */
const K80_CSS = `
@page { size: 80mm auto; margin: 0; }
* { box-sizing: border-box; }
body { margin: 0; padding: 3mm 4mm 6mm; width: 80mm; color: #000;
       font-family: Arial, Helvetica, sans-serif; font-size: 12px; line-height: 1.35; }
.center { text-align: center; }
.store { font-size: 14px; font-weight: 700; text-transform: uppercase; }
.title { font-size: 15px; font-weight: 700; margin: 6px 0 2px; }
.small { font-size: 11px; }
.bold { font-weight: 700; }
.row { display: flex; justify-content: space-between; gap: 8px; }
.row > :last-child { text-align: right; white-space: nowrap; }
.item { margin-top: 4px; }
.item-name { font-weight: 600; }
.total { font-size: 14px; font-weight: 700; }
.stamp { border: 1px solid #000; text-align: center; font-weight: 700; padding: 2px; margin: 4px 0; }
hr { border: 0; border-top: 1px dashed #000; margin: 6px 0; }
`

/**
 * In một mẫu React qua iframe ẩn bằng hộp thoại in của trình duyệt (BR-PRT-04) — không cần thư viện, máy in nào
 * cài driver trên máy tính đều in được. Tách khỏi trang chính để CSS in không lẫn với giao diện antd.
 * renderToStaticMarkup tự escape chữ (tên khách, ghi chú...) nên không chèn được HTML lạ.
 */
export function printK80(title: string, content: ReactElement): void {
  const html = `<!doctype html><html lang="vi"><head><meta charset="utf-8"><title>${escapeHtml(title)}</title>
<style>${K80_CSS}</style></head><body>${renderToStaticMarkup(content)}</body></html>`

  const frame = document.createElement('iframe')
  frame.setAttribute('aria-hidden', 'true')
  Object.assign(frame.style, { position: 'fixed', right: '0', bottom: '0', width: '0', height: '0', border: '0' })
  frame.onload = () => {
    const win = frame.contentWindow
    if (!win) return
    // Gỡ iframe sau khi đóng hộp thoại in; dự phòng sau 1 phút nếu trình duyệt không báo afterprint
    const cleanup = () => frame.remove()
    win.addEventListener('afterprint', cleanup)
    setTimeout(cleanup, 60_000)
    win.focus()
    win.print()
  }
  frame.srcdoc = html
  document.body.appendChild(frame)
}

function escapeHtml(text: string): string {
  return text.replace(/[&<>"']/g, (char) => `&#${char.charCodeAt(0)};`)
}
