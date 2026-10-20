import { Button, Result } from 'antd'
import { Link, Navigate, createBrowserRouter } from 'react-router-dom'
import { AppLayout } from './components/layout/AppLayout'
import { AttendancePage } from './features/attendance/AttendancePage'
import { CustomerDebtPage } from './features/customer-debts/CustomerDebtPage'
import { CustomerListPage } from './features/customers/CustomerListPage'
import { EmployeeListPage } from './features/employees/EmployeeListPage'
import { InboundReceiptListPage } from './features/inbound-receipts/InboundReceiptListPage'
import { PayrollPage } from './features/payroll/PayrollPage'
import { ProductListPage } from './features/products/ProductListPage'
import { SalesOrderListPage } from './features/sales-orders/SalesOrderListPage'
import { SupplierListPage } from './features/suppliers/SupplierListPage'
import { WarehouseListPage } from './features/warehouses/WarehouseListPage'

export const router = createBrowserRouter([
  {
    path: '/',
    element: <AppLayout />,
    children: [
      { index: true, element: <Navigate to="/products" replace /> },
      { path: 'products', element: <ProductListPage /> },
      { path: 'warehouses', element: <WarehouseListPage /> },
      { path: 'suppliers', element: <SupplierListPage /> },
      { path: 'customers', element: <CustomerListPage /> },
      { path: 'inbound-receipts', element: <InboundReceiptListPage /> },
      { path: 'sales-orders', element: <SalesOrderListPage /> },
      { path: 'customer-debts', element: <CustomerDebtPage /> },
      { path: 'employees', element: <EmployeeListPage /> },
      { path: 'attendance', element: <AttendancePage /> },
      { path: 'payroll', element: <PayrollPage /> },
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
