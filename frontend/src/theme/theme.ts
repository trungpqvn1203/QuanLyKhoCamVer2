import type { ThemeConfig } from 'antd'

/**
 * Theme tập trung của toàn ứng dụng (ADR-0002, tầng 1).
 * Đổi màu/bo góc/font ở đây → mọi màn hình cùng đổi.
 */
export const appTheme: ThemeConfig = {
  token: {
    colorPrimary: '#2f7d32', // xanh lá — gợi ngành nông nghiệp/thức ăn chăn nuôi
    borderRadius: 8,
    fontFamily:
      "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif",
  },
  components: {
    Layout: {
      siderBg: '#ffffff',
      headerBg: '#ffffff',
    },
  },
}
