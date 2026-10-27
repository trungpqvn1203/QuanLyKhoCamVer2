import type { ThemeConfig } from 'antd'

/** Nền sidebar: xanh rất đậm, cùng tông với màu chủ đạo thay vì xanh navy mặc định. */
const SIDER_BG = '#12261a'

/** Đường kẻ trên nền sidebar tối (trắng 10%). */
export const SIDER_DIVIDER = 'rgba(255, 255, 255, 0.1)'

/**
 * Theme tập trung của toàn ứng dụng (ADR-0002, tầng 1).
 * Đổi màu/bo góc/font ở đây → mọi màn hình cùng đổi. Đây là nơi DUY NHẤT được ghi mã màu.
 */
export const appTheme: ThemeConfig = {
  token: {
    colorPrimary: '#2f7d32', // xanh lá — gợi ngành nông nghiệp/thức ăn chăn nuôi
    colorBgLayout: '#f4f6f5', // nền trang xám nhạt
    borderRadius: 8,
    fontFamily:
      "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif",
  },
  components: {
    Layout: {
      headerBg: '#ffffff',
      siderBg: SIDER_BG,
    },
    Menu: {
      darkItemBg: SIDER_BG,
      darkItemColor: 'rgba(255, 255, 255, 0.72)',
      darkItemHoverBg: 'rgba(255, 255, 255, 0.08)',
      // Nền vùng xổ xuống của nhóm menu: mặc định antd là xanh đen #000c17, lệch hẳn nền xanh rêu → tối hơn sidebar một chút
      darkSubMenuItemBg: '#0d1d13',
      // Đường kẻ tách nhóm "Số dư đầu kỳ" — cùng màu đường kẻ dưới logo (chỉ dùng trong menu sidebar)
      colorSplit: SIDER_DIVIDER,
      itemBorderRadius: 6,
      itemMarginInline: 12,
      itemHeight: 40,
    },
    Table: {
      headerBg: '#f7f8f7', // nền tiêu đề cột trung tính, nhạt
      headerColor: 'rgba(0, 0, 0, 0.65)',
      headerSplitColor: 'transparent', // bỏ vạch dọc giữa các tiêu đề cột
      rowHoverBg: '#f3f8f3', // hover xanh rất nhạt
      borderColor: '#eef0ef', // chỉ đường kẻ ngang, mảnh
      cellPaddingBlock: 12,
    },
  },
}
