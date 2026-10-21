import { App as AntApp, ConfigProvider } from 'antd'
import viVN from 'antd/locale/vi_VN'
import { RouterProvider } from 'react-router-dom'
import { AuthProvider } from './features/auth/AuthProvider'
import { router } from './router'
import { appTheme } from './theme/theme'

function App() {
  return (
    // ConfigProvider: theme + tiếng Việt cho component (phân trang, xác nhận...)
    // AntApp: cho phép dùng App.useApp().message trong component
    <ConfigProvider theme={appTheme} locale={viVN}>
      <AntApp>
        <AuthProvider>
          <RouterProvider router={router} />
        </AuthProvider>
      </AntApp>
    </ConfigProvider>
  )
}

export default App
