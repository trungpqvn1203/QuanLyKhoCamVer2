import { Button, theme } from 'antd'

/**
 * Mã bản ghi (SP000001, NCC001...) bằng font đơn cách để các mã thẳng cột, dễ dò.
 * Dùng trong khung chi tiết và bảng con.
 */
export function CodeText({ code }: { code: string }) {
  const { token } = theme.useToken()
  return <span style={{ fontFamily: token.fontFamilyCode }}>{code}</span>
}

/**
 * Mã ở cột đầu bảng, bấm để mở chi tiết.
 * Dùng Button dạng link (thẻ <button>) thay vì Typography.Link không có href:
 * Tab tới được, Enter/Space kích hoạt được, trình đọc màn hình đọc là "nút".
 */
export function CodeLink({ code, onClick }: { code: string; onClick: () => void }) {
  const { token } = theme.useToken()
  return (
    <Button
      type="link"
      onClick={onClick}
      aria-label={`Xem chi tiết ${code}`}
      style={{ padding: 0, height: 'auto', fontFamily: token.fontFamilyCode }}
    >
      {code}
    </Button>
  )
}
