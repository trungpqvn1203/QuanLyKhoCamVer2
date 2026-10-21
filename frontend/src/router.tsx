import { Button, Result } from 'antd'
import type { ReactNode } from 'react'
import { Link, Navigate, createBrowserRouter } from 'react-router-dom'
import { AppLayout } from './components/layout/AppLayout'
import { AttendancePage } from './features/attendance/AttendancePage'
import { LoginPage } from './features/auth/LoginPage'
import { RequireAuth, RequirePermission } from './features/auth/RequireAuth'
import type { Permission } from './features/auth/permissions'
import { CustomerDebtPage } from './features/customer-debts/CustomerDebtPage'
import { CustomerListPage } from './features/customers/CustomerListPage'
import { EmployeeListPage } from './features/employees/EmployeeListPage'
import { InboundReceiptListPage } from './features/inbound-receipts/InboundReceiptListPage'
import { PayrollPage } from './features/payroll/PayrollPage'
import { ProductListPage } from './features/products/ProductListPage'
import { SalesOrderListPage } from './features/sales-orders/SalesOrderListPage'
import { StockTransactionPage } from './features/stock-transactions/StockTransactionPage'
import { SupplierListPage } from './features/suppliers/SupplierListPage'
import { UserListPage } from './features/users/UserListPage'
import { WarehouseListPage } from './features/warehouses/WarehouseListPage'

/** Trang chỉ một số vai trò dùng — cùng quyền với menu (AppLayout) và với API (backend). */
function guarded(permission: Permission, page: ReactNode) {
  return <RequirePermission permission={permission}>{page}</RequirePermission>
}

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  {
    path: '/',
    element: (
      <RequireAuth>
        <AppLayout />
      </RequireAuth>
    ),
    children: [
      { index: true, element: <Navigate to="/products" replace /> },
      { path: 'products', element: <ProductListPage /> },
      { path: 'warehouses', element: <WarehouseListPage /> },
      { path: 'suppliers', element: guarded('suppliers', <SupplierListPage />) },
      { path: 'customers', element: <CustomerListPage /> },
      { path: 'inbound-receipts', element: guarded('inbound', <InboundReceiptListPage />) },
      { path: 'sales-orders', element: <SalesOrderListPage /> },
      { path: 'customer-debts', element: guarded('debts', <CustomerDebtPage />) },
      { path: 'stock-transactions', element: <StockTransactionPage /> },
      { path: 'employees', element: guarded('employees', <EmployeeListPage />) },
      { path: 'attendance', element: guarded('attendance', <AttendancePage />) },
      { path: 'payroll', element: guarded('payroll', <PayrollPage />) },
      { path: 'users', element: guarded('users', <UserListPage />) },
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
