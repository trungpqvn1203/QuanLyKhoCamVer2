-- Migration V29: Tách ghi chú của khách khỏi ghi chú nội bộ (BR-POR-11)
-- Trước đây đơn app chỉ có một cột note: khách ghi vào, nhân viên xác nhận / sửa cũng ghi vào cùng cột đó
-- → ghi chú nội bộ ("khách nợ nhiều, thu trước") hiện ra app khách, và lời khách bị ghi đè.
-- Từ nay: customer_note = lời khách (chỉ app khách ghi, nhân viên chỉ đọc); note = ghi chú nội bộ (khách không thấy).

ALTER TABLE stock_document ADD COLUMN customer_note VARCHAR(500);

-- Chỉ đơn khách đặt qua app mới có lời khách
ALTER TABLE stock_document ADD CONSTRAINT ck_stock_document_customer_note
    CHECK (customer_note IS NULL OR order_channel = 'APP');

-- Dữ liệu cũ: chỉ chép đơn app CÒN Chờ xác nhận — chưa ai trong cửa hàng sửa, note chắc chắn là lời khách.
-- Đơn đã xác nhận có thể đã bị nhân viên ghi đè → không chép (mất lời khách cũ còn hơn lộ ghi chú nội bộ).
-- Đơn còn chờ: lời khách chuyển hẳn sang cột mới, note để trống cho ghi chú nội bộ.
UPDATE stock_document
SET customer_note = note,
    note          = NULL
WHERE order_channel = 'APP'
  AND status = 'PENDING'
  AND note IS NOT NULL;
