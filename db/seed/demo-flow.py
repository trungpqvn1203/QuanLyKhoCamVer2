"""
Dữ liệu demo theo LUỒNG NGHIỆP VỤ — CHỈ dùng cho máy dev (local), không chạy trên production.

Khác demo-data.sql (chèn thẳng danh mục): script này gọi API thật như người dùng bấm trên web, nên mọi quy tắc
nghiệp vụ (trừ tồn, công nợ, chốt lương, sổ quỹ...) đều chạy đúng như thật.

Luồng tạo ra (01/09/2026 → nay):
  1. Đặt đầu kỳ sổ quỹ 01/09: tiền mặt 50tr, tài khoản 300tr
  2. Nhập hàng từ 2 NCC → trả NCC một phần → còn nợ NCC
  3. Bán cho 3 khách → thu tiền một phần → còn nợ khách; 1 phiếu bán nháp (chưa trừ tồn)
  4. Chấm công tháng 9 + tháng 10 (ngày công, tấn bốc xuống, tấn giao hàng)
  5. Tạm ứng → chốt lương tháng 9 → trả lương đầu tháng 10 có trừ tạm ứng
  6. Phiếu thu/chi khác (tiền điện, xăng, góp vốn...) + 1 phiếu ghi nhầm đã hoàn

Cách chạy (backend đang chạy ở localhost:8080, đăng nhập bằng tài khoản Quản lý hệ thống):
  python db/seed/demo-flow.py
  # hoặc đặt sẵn: GW_USERNAME, GW_PASSWORD, GW_BASE_URL (mặc định http://localhost:8080)

Chạy một lần: danh mục có sẵn thì dùng lại; nếu NCC demo đã có phiếu nhập thì dừng, không nhân đôi chứng từ.
"""

import getpass
import json
import os
import sys
import urllib.error
import urllib.request
from datetime import date, timedelta
from decimal import Decimal

BASE_URL = os.environ.get("GW_BASE_URL", "http://localhost:8080")
MARKER_CUSTOMER = "Đại lý Minh Phát (demo)"

cookies: dict[str, str] = {}


# ---------------------------------------------------------------- HTTP

class ApiFailure(Exception):
    def __init__(self, status, body):
        super().__init__(f"HTTP {status}: {body}")
        self.status = status
        self.body = body


def call(method, path, body=None):
    headers = {"Accept": "application/json"}
    if cookies:
        headers["Cookie"] = "; ".join(f"{k}={v}" for k, v in cookies.items())
    if method != "GET" and "XSRF-TOKEN" in cookies:
        headers["X-XSRF-TOKEN"] = cookies["XSRF-TOKEN"]
    data = None
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    request = urllib.request.Request(BASE_URL + path, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(request) as response:
            remember_cookies(response.headers.get_all("Set-Cookie") or [])
            raw = response.read()
    except urllib.error.HTTPError as err:
        remember_cookies(err.headers.get_all("Set-Cookie") or [])
        raw = err.read().decode("utf-8", "replace")
        try:
            raw = json.loads(raw).get("error", raw)
        except ValueError:
            pass
        raise ApiFailure(err.code, raw) from None
    if not raw:
        return None
    return json.loads(raw).get("data")


def remember_cookies(set_cookie_headers):
    # Tự giữ cookie (cookie-secure=true thì cookiejar của Python không gửi lại qua http)
    for header in set_cookie_headers:
        name, _, value = header.split(";", 1)[0].partition("=")
        if value:
            cookies[name.strip()] = value.strip()
        else:
            cookies.pop(name.strip(), None)


def get(path):
    return call("GET", path)


def post(path, body=None):
    return call("POST", path, body if body is not None else {})


def put(path, body):
    return call("PUT", path, body)


def items(data):
    return data["items"] if isinstance(data, dict) and "items" in data else data


def find_by_name(path, name):
    query = urllib.request.quote(name)
    for row in items(get(f"{path}?keyword={query}&size=100")):
        if row["name"].lower() == name.lower():
            return row
    return None


def step(text):
    print(f"\n== {text}")


def d(iso):
    return iso  # ngày gửi API dạng yyyy-MM-dd


# ---------------------------------------------------------------- Danh mục

def ensure(path, name, body, label):
    row = find_by_name(path, name)
    if row:
        print(f"  có sẵn {label}: {name} (id {row['id']})")
        return row["id"]
    created = post(path, body)
    print(f"  tạo {label}: {name} (id {created['id']})")
    return created["id"]


def ensure_supplier(name, tax_code, phone, address):
    row = find_by_name("/api/suppliers", name)
    if row:
        print(f"  có sẵn NCC: {name} (id {row['id']})")
        return row["id"]
    body = {"name": name, "taxCode": tax_code, "phone": phone, "address": address}
    try:
        created = post("/api/suppliers", body)
    except ApiFailure as err:
        if err.status != 409:
            raise
        body["taxCode"] = None  # MST đã có NCC khác dùng → bỏ MST cho bản demo
        created = post("/api/suppliers", body)
    print(f"  tạo NCC: {name} (id {created['id']})")
    return created["id"]


def link_product(supplier_id, product_id):
    try:
        post(f"/api/suppliers/{supplier_id}/products", {"productId": product_id})
    except ApiFailure as err:
        if err.status != 409:  # đã liên kết từ trước
            raise


# ---------------------------------------------------------------- Main

def login():
    username = os.environ.get("GW_USERNAME") or input("Tên đăng nhập (Quản lý hệ thống): ")
    password = os.environ.get("GW_PASSWORD") or getpass.getpass("Mật khẩu: ")
    get("/api/auth/csrf")
    user = post("/api/auth/login", {"username": username, "password": password})
    print(f"Đã đăng nhập: {user['username']} {user.get('roles')}")


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    login()

    step("1. Danh mục: nhóm hàng, sản phẩm, kho, NCC, khách, nhân viên")
    cat_pig = ensure("/api/categories", "Cám heo", {"name": "Cám heo"}, "nhóm hàng")
    cat_chicken = ensure("/api/categories", "Cám gà", {"name": "Cám gà"}, "nhóm hàng")
    cat_duck = ensure("/api/categories", "Cám vịt", {"name": "Cám vịt"}, "nhóm hàng")

    def product(name, category_id, kg):
        return ensure("/api/products", name,
                      {"name": name, "categoryId": category_id, "unit": "BAG", "netWeightKg": kg, "minStockQuantity": 20},
                      "sản phẩm")

    # Sản phẩm riêng cho demo: không đụng tới trạng thái sản phẩm thật đang có
    pig = product("Cám heo thịt 40kg (demo)", cat_pig, 40)
    chicken = product("Cám gà thịt 25kg (demo)", cat_chicken, 25)
    duck = product("Cám vịt đẻ 25kg (demo)", cat_duck, 25)

    warehouse = ensure("/api/warehouses", "Kho tổng Hà Nội",
                       {"name": "Kho tổng Hà Nội", "type": "CENTRAL", "address": "KCN Phú Nghĩa, Chương Mỹ, Hà Nội"},
                       "kho")

    ncc_a = ensure_supplier("Công ty CP Thức ăn chăn nuôi Việt Phát (demo)", "0109912345",
                            "0243 555 1234", "KCN Quang Minh, Mê Linh, Hà Nội")
    ncc_b = ensure_supplier("Công ty TNHH Dinh dưỡng Hồng Hà (demo)", "0108876543-001",
                            "0221 377 8899", "KCN Như Quỳnh, Văn Lâm, Hưng Yên")
    for p in (pig, chicken):
        link_product(ncc_a, p)
    link_product(ncc_b, duck)

    if items(get(f"/api/inbound-receipts?supplierId={ncc_a}&size=1")):
        print("\nNCC demo đã có phiếu nhập → luồng demo đã chạy trước đó, dừng để không nhân đôi chứng từ.")
        return

    def customer(name, kind, phone, contact, address):
        return ensure("/api/customers", name,
                      {"name": name, "type": kind, "phone": phone, "contactName": contact, "address": address},
                      "khách")

    dealer = customer(MARKER_CUSTOMER, "DEALER", "0912 345 678", "Anh Phát", "Chợ Bến, Chương Mỹ, Hà Nội")
    farm = customer("Trang trại gà Đồng Tâm (demo)", "FARM", "0987 654 321", "Chị Tâm", "Mỹ Đức, Hà Nội")
    retail = customer("Cửa hàng cám Bà Lan (demo)", "RETAIL", "0966 111 222", "Bà Lan", "Thanh Oai, Hà Nội")

    def employee(name, phone, daily, unload, delivery):
        return ensure("/api/employees", name,
                      {"name": name, "phone": phone, "dailyWage": daily, "unloadTonRate": unload,
                       "deliveryTonRate": delivery},
                      "nhân viên")

    an = employee("Nguyễn Văn An (demo)", "0901 000 111", 300000, 50000, 70000)
    binh = employee("Trần Văn Bình (demo)", "0901 000 222", 280000, 50000, 70000)
    cuc = employee("Lê Thị Cúc (demo)", "0901 000 333", 250000, 0, 0)

    step("2. Sổ quỹ: đầu kỳ 01/09/2026 — tiền mặt 50.000.000, tài khoản 300.000.000")
    opening = get("/api/cash-book/opening")
    if opening.get("configured"):
        print(f"  sổ quỹ đã có đầu kỳ từ {opening['startDate']} → giữ nguyên"
              + ("" if opening["startDate"] <= "2026-09-01" else " (CHÚ Ý: chứng từ demo trước ngày này sẽ không vào sổ)"))
    else:
        put("/api/cash-book/opening", {"startDate": d("2026-09-01"), "openingCash": 50000000, "openingBank": 300000000})
        print("  đã đặt đầu kỳ")

    step("3. Nhập hàng (tăng tồn) và trả tiền NCC")

    def inbound(supplier_id, day, lines, note):
        receipt = post("/api/inbound-receipts", {
            "supplierId": supplier_id, "warehouseId": warehouse, "documentDate": d(day), "note": note,
            "lines": [{"productId": p, "orderedQuantity": q, "unitPrice": price} for p, q, price in lines]})
        receipt = post(f"/api/inbound-receipts/{receipt['id']}/complete")
        print(f"  {receipt['code']} {day}: hoàn tất, tổng {fmt(receipt.get('totalAmount'))}")
        return receipt["id"]

    def pay_supplier(receipt_id, day, cash=0, transfer=0, note=None):
        post(f"/api/inbound-receipts/{receipt_id}/payments",
             {"paymentDate": d(day), "cashAmount": cash, "transferAmount": transfer, "note": note})
        print(f"    trả NCC {day}: TM {fmt(cash)} · CK {fmt(transfer)}")

    pn1 = inbound(ncc_a, "2026-09-05", [(pig, 200, 380000), (chicken, 150, 290000)], "Nhập đầu vụ")
    pay_supplier(pn1, "2026-09-10", transfer=80000000, note="Chuyển khoản đợt 1")
    pay_supplier(pn1, "2026-10-05", transfer=39500000, note="Chuyển khoản đợt 2 — hết nợ phiếu này")
    pn2 = inbound(ncc_b, "2026-09-12", [(duck, 200, 270000)], "Nhập cám vịt")
    pay_supplier(pn2, "2026-09-20", cash=20000000, note="Trả trước một phần, còn nợ")
    inbound(ncc_a, "2026-10-08", [(pig, 100, 385000)], "Nhập bổ sung — chưa trả tiền")

    step("4. Bán hàng (trừ tồn) và thu tiền khách")

    def sale(customer_id, day, lines, note, complete=True):
        order = post("/api/sales-orders", {
            "customerId": customer_id, "warehouseId": warehouse, "documentDate": d(day), "note": note,
            "lines": [{"productId": p, "quantity": q, "unitPrice": price} for p, q, price in lines]})
        if complete:
            order = post(f"/api/sales-orders/{order['id']}/complete")
        print(f"  {order['code']} {day}: {'hoàn tất' if complete else 'NHÁP (chưa trừ tồn, chưa thành nợ)'},"
              f" tổng {fmt(order.get('totalAmount'))}")
        return order["id"]

    def collect(order_id, day, cash=0, transfer=0, note=None):
        post(f"/api/sales-orders/{order_id}/payments",
             {"paymentDate": d(day), "cashAmount": cash, "transferAmount": transfer, "note": note})
        print(f"    thu khách {day}: TM {fmt(cash)} · CK {fmt(transfer)}")

    pb1 = sale(dealer, "2026-09-08", [(pig, 80, 420000)], "Đại lý lấy hàng đầu tháng")
    collect(pb1, "2026-09-08", cash=10000000, note="Đặt cọc khi lấy hàng")
    collect(pb1, "2026-09-25", transfer=23600000, note="Thanh toán nốt")
    pb2 = sale(farm, "2026-09-15", [(chicken, 100, 320000)], "Giao tận trại")
    collect(pb2, "2026-09-30", transfer=20000000, note="Trại trả một phần, còn nợ")
    pb3 = sale(retail, "2026-10-03", [(duck, 20, 300000)], "Khách lẻ")
    collect(pb3, "2026-10-03", cash=6000000, note="Trả đủ tiền mặt")
    pb4 = sale(dealer, "2026-10-15", [(pig, 60, 420000), (duck, 40, 300000)], "Đại lý lấy hàng giữa tháng")
    collect(pb4, "2026-10-20", cash=5000000, note="Trả trước một phần")
    sale(farm, "2026-10-22", [(chicken, 50, 320000)], "Đơn đang chờ xác nhận", complete=False)

    step("5. Chấm công tháng 9 và tháng 10")
    today = date.today()
    count = 0
    for day in daterange(date(2026, 9, 1), min(date(2026, 10, 25), today - timedelta(days=1))):
        if day.weekday() == 6:  # Chủ nhật nghỉ
            continue
        n = day.day
        put(f"/api/attendance/{an}/{day.isoformat()}", {
            "status": "HALF" if n % 11 == 0 else "FULL",
            "unloadTons": str(Decimal(n % 4) * Decimal("1.5")), "deliveryTons": str(Decimal(n % 3))})
        put(f"/api/attendance/{binh}/{day.isoformat()}",
            {"status": "ABSENT"} if n % 13 == 0 else {
                "status": "FULL", "unloadTons": str(Decimal(n % 2) * 2), "deliveryTons": str(Decimal(n % 5) * Decimal("0.8"))})
        put(f"/api/attendance/{cuc}/{day.isoformat()}", {"status": "FULL"})
        count += 1
    print(f"  đã chấm {count} ngày làm việc × 3 nhân viên")

    step("6. Tạm ứng, chốt lương tháng 9, trả lương có trừ tạm ứng")

    def advance(employee_id, day, cash, note):
        post(f"/api/employees/{employee_id}/advances", {"paymentDate": d(day), "cashAmount": cash, "note": note})
        print(f"  tạm ứng {day}: {fmt(cash)} — {note}")

    advance(an, "2026-09-15", 2000000, "An ứng tiền đóng học cho con")
    advance(binh, "2026-09-20", 1500000, "Bình ứng sửa xe máy")
    advance(an, "2026-10-10", 1000000, "An ứng thêm (trừ vào lương tháng 10)")

    try:
        sheet = post("/api/payrolls/2026-09/finalize")
        print(f"  chốt lương 09/2026: tổng {fmt(sheet['totalAmount'])}")
    except ApiFailure as err:
        print(f"  KHÔNG chốt được lương 09/2026 ({err.body}) → bỏ qua phần trả lương")
        sheet = None

    if sheet:
        def pay_salary(employee_id, day, deduction=0, transfer_share=None, note=None):
            base = f"/api/payrolls/2026-09/items/{employee_id}"
            outstanding = int(Decimal(str(get(base)["outstandingAmount"])))
            rest = outstanding - deduction
            transfer = rest if transfer_share is None else int(rest * transfer_share) // 1000 * 1000
            post(f"{base}/payments", {"paymentDate": d(day), "cashAmount": 0, "transferAmount": transfer,
                                      "advanceDeduction": deduction, "note": note})
            left = outstanding - deduction - transfer
            print(f"  trả lương {day}: CK {fmt(transfer)} · trừ ứng {fmt(deduction)} · còn nợ {fmt(left)}")

        pay_salary(an, "2026-10-05", deduction=2000000, note="Trả đủ lương tháng 9, trừ hết tạm ứng")
        pay_salary(binh, "2026-10-05", deduction=1000000, transfer_share=Decimal("0.6"),
                   note="Trả 60%, trừ 1tr tạm ứng; phần còn lại trả sau")
        pay_salary(cuc, "2026-10-05", note="Trả đủ lương tháng 9")

    step("7. Phiếu thu / chi khác")
    categories = {c["name"]: c["id"] for c in items(get("/api/cash-categories"))}

    def category(name, direction):
        if name not in categories:
            categories[name] = post("/api/cash-categories", {"name": name, "direction": direction})["id"]
        return categories[name]

    def voucher(direction, account, cat, day, amount, counterparty, note):
        v = post("/api/cash-vouchers", {
            "direction": direction, "account": account, "categoryId": category(cat, direction), "amount": amount,
            "voucherDate": d(day), "counterparty": counterparty, "note": note})
        print(f"  {v['code']} {day}: {cat} {fmt(amount)} ({'tiền mặt' if account == 'CASH' else 'tài khoản'})")
        return v["id"]

    voucher("OUT", "BANK", "Tiền điện", "2026-09-10", 3200000, "EVN Hà Nội", "Tiền điện kho tháng 8")
    voucher("OUT", "CASH", "Xăng dầu xe", "2026-09-18", 1500000, "Cây xăng Chúc Sơn", "Đổ dầu xe giao hàng")
    voucher("IN", "BANK", "Góp vốn chủ", "2026-10-01", 100000000, "Chủ kho", "Bổ sung vốn lưu động")
    voucher("OUT", "BANK", "Tiền điện", "2026-10-10", 3450000, "EVN Hà Nội", "Tiền điện kho tháng 9")
    wrong = voucher("OUT", "CASH", "Sửa chữa", "2026-10-12", 4500000, "Garage Hùng", "Thay lốp xe tải")
    post(f"/api/cash-vouchers/{wrong}/reverse", {"reason": "Ghi sai số tiền, hóa đơn là 4.050.000"})
    print("    → đã hoàn phiếu ghi nhầm (phiếu -H)")
    voucher("OUT", "CASH", "Sửa chữa", "2026-10-12", 4050000, "Garage Hùng", "Thay lốp xe tải (ghi lại đúng)")
    voucher("IN", "CASH", "Thu khác", "2026-10-18", 800000, "Đại lý ve chai", "Bán bao bì cũ")

    step("Kết quả")
    summary = get("/api/cash-book/summary")
    if summary.get("configured"):
        print(f"  Tiền mặt:  {fmt(summary['cash']['balance'])}")
        print(f"  Tài khoản: {fmt(summary['bank']['balance'])}")
    print("\nXong. Mở web (localhost:5173) xem: Sổ quỹ, Công nợ khách / NCC / nhân viên, Tồn kho, Bảng lương 09/2026.")


def daterange(start, end):
    day = start
    while day <= end:
        yield day
        day += timedelta(days=1)


def fmt(value):
    if value is None:
        return "-"
    return f"{int(Decimal(str(value))):,}".replace(",", ".")


if __name__ == "__main__":
    try:
        main()
    except ApiFailure as err:
        print(f"\nLỖI API: {err}", file=sys.stderr)
        sys.exit(1)
