# Bảng thuật ngữ

| Thuật ngữ | Tiếng Anh | Định nghĩa |
|---|---|---|
| **Sản phẩm** | Product | Một loại cám cụ thể được quản lý trong hệ thống. Có mã, tên và đơn vị tính. |
| **Đơn vị tính** | Unit of Measure (UoM) | Đơn vị đo lường số lượng sản phẩm (ví dụ: bao, kg, tấn). |
| **Kho tập kết** | Central Warehouse | Kho chính nhận hàng từ nhà cung cấp và phân phối cho đại lý hoặc kho bán lẻ. |
| **Kho bán lẻ** | Retail Warehouse | Kho nhỏ phục vụ bán trực tiếp cho khách lẻ hoặc trang trại nhỏ. |
| **Nhà cung cấp** | Supplier | Đơn vị cung cấp cám cho hệ thống (nhà máy, thương lái). |
| **Đại lý cấp 1** | Level-1 Dealer | Đối tượng mua hàng số lượng lớn từ kho tập kết để phân phối tiếp. |
| **Trang trại / Người dân** | Farm / End Consumer | Người tiêu dùng cuối cùng, mua trực tiếp hoặc qua đại lý. |
| **Tồn kho** | Stock / Inventory | Số lượng hàng hóa hiện đang lưu trữ tại một kho cụ thể. |
| **Tồn khả dụng** | Available Stock | Số lượng tồn thực sự có thể xuất. Trong MVP, bằng tồn hiện tại (chưa có cơ chế reserve). |
| **Nhập kho** | Stock In / Goods Receipt | Nghiệp vụ đưa hàng vào kho, làm tăng tồn. Thường từ nhà cung cấp hoặc từ kho khác điều chuyển đến. |
| **Xuất kho** | Stock Out / Goods Issue | Nghiệp vụ lấy hàng ra khỏi kho, làm giảm tồn. Do bán hàng hoặc điều chuyển đi. |
| **Điều chuyển kho** | Stock Transfer | Di chuyển hàng từ kho này sang kho khác trong hệ thống. Tạo xuất ở kho nguồn và nhập ở kho đích. |
| **Chứng từ** | Stock Document | Phiếu ghi nhận một nghiệp vụ kho (phiếu nhập, phiếu xuất, phiếu điều chuyển). Chứa thông tin chung và các dòng chi tiết. |
| **Dòng chứng từ** | Document Item / Line Item | Một dòng hàng trong chứng từ, gồm sản phẩm, số lượng, đơn giá. Một chứng từ có thể có nhiều dòng. |
| **Giao dịch kho** | Stock Transaction | Bản ghi bất biến ghi lại một biến động tồn kho (tăng hoặc giảm) tại một kho cho một sản phẩm. Dùng để truy vết và đối chiếu. |
| **Kiểm kê** | Stock Count / Physical Inventory | Đếm thực tế hàng trong kho và so sánh với tồn hệ thống. Chênh lệch được xử lý bằng giao dịch điều chỉnh. _(Ngoài MVP nếu không đủ thời gian)_ |
| **Giao dịch đảo** | Reversal Transaction | Giao dịch hủy tác động của một giao dịch trước đó. Có tham chiếu đến giao dịch gốc. Dùng khi phát hiện sai sót. |
| **Giao dịch điều chỉnh** | Adjustment Transaction | Giao dịch tăng hoặc giảm tồn kho để khớp với thực tế (sau kiểm kê hoặc do hư hao). |
