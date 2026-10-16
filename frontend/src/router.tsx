import { Button, Result } from 'antd'
import { Link, Navigate, createBrowserRouter } from 'react-router-dom'
import { AppLayout } from './components/layout/AppLayout'
import { ProductListPage } from './features/products/ProductListPage'
import { WarehouseListPage } from './features/warehouses/WarehouseListPage'

export const router = createBrowserRouter([
  {
    path: '/',
    element: <AppLayout />,
    children: [
      { index: true, element: <Navigate to="/products" replace /> },
      { path: 'products', element: <ProductListPage /> },
      { path: 'warehouses', element: <WarehouseListPage /> },
      {
        path: '*',
        element: (
          <Result
            status="404"
            title="Không tìm thấy trang"
            extra={
              <Link to="/products">
                <Button type="primary">Về trang sản phẩm</Button>
              </Link>
            }
          />
        ),
      },
    ],
  },
])
