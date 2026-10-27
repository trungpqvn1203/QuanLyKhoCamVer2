# Công nợ nhà cung cấp

[← Mục lục kho bài học](../README.md)

## Module: Công nợ nhà cung cấp (2026-10-25, migration V16)

- **Dữ liệu cũ khi thêm nghiệp vụ mới: cờ hay số giả?** Phiếu nhập đã hoàn tất trước module này phải "coi như đã trả đủ". Cách dễ nghĩ ra là gán `paid_amount = total_amount`. Nhưng `paid_amount` là **snapshot** của nhật ký `supplier_payment`: gán như vậy thì tổng không khớp với nhật ký (đã trả 10 triệu mà không có dòng trả nào), và phiếu cũ bị khóa đảo theo BR-SPAY-04. Cờ `tracks_debt = FALSE` nói thẳng điều đã xảy ra: "phiếu này không theo dõi công nợ". Mọi chỗ tính nợ (`getOutstandingAmount`, câu GROUP BY, bộ lọc `unpaidOnly`) đều phải tôn trọng cờ này, nên test `legacyReceiptShouldBeTreatedAsPaid` kiểm cả bốn chỗ.
- **Hibernate giữ bản cũ trong bộ nhớ.** Test giả lập phiếu cũ bằng `jdbcTemplate.update("... tracks_debt = FALSE")`. Phiếu đó vẫn nằm trong persistence context với `tracksDebt = true`, nên request sau đọc lại **object trong bộ nhớ**, không đọc DB. Phải gọi `flush()` (đẩy thay đổi của Hibernate xuống DB) trước câu UPDATE, rồi `clear()` (bỏ bản cũ) sau câu UPDATE.
- **Dùng lại mẫu, không dùng lại bảng.** Trả tiền NCC giống hệt thu tiền khách về cấu trúc (PAYMENT/REVERSAL, hai dòng khi trả kết hợp, trigger bất biến, khóa phiếu `FOR UPDATE` rồi mới kiểm tra). Code dùng chung kiểu `PaymentMethod` và DTO request, nhưng dữ liệu nằm ở **bảng riêng** `supplier_payment`. Lý do: chiều tiền và quyền khác nhau, và module bán hàng đã duyệt không bị đụng tới. Ở frontend, phần "Thanh toán" và hộp thoại trả/thu tiền được tách thành component dùng chung, chỉ khác chữ ("Thu" / "Trả").
- **Quy tắc chéo giữa hai nghiệp vụ.** Đảo phiếu nhập (nghiệp vụ kho) giờ phụ thuộc trả tiền (nghiệp vụ tiền): đã trả thì không đảo được. Nếu không chặn, đảo phiếu xong thì món nợ biến mất mà tiền đã chi vẫn nằm đó, không gắn với chứng từ nào. Đây cũng là lý do module **dòng tiền** phải làm sau công nợ: nó gom các khoản thu/chi từ `sale_payment` và `supplier_payment`.
