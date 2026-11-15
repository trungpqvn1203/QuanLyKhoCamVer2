-- Migration V33: Cho phép tồn kho âm (xuất hàng trước khi kịp nhập hóa đơn; tồn đầu kỳ âm).
-- Tồn kho (inventory.quantity) và ảnh chụp biến động (stock_transaction.quantity_before/after) có thể âm;
-- stock_transaction.quantity vẫn > 0 (độ lớn biến động), loại IN/OUT quyết định dấu.
-- Với phiếu tồn đầu kỳ (OPENING_STOCK), số lượng dòng và thành tiền có thể âm; các loại phiếu khác vẫn >= 0.

ALTER TABLE inventory DROP CONSTRAINT ck_inventory_quantity_non_negative;

ALTER TABLE stock_transaction DROP CONSTRAINT ck_stock_transaction_snapshot;

ALTER TABLE stock_document_item DROP CONSTRAINT ck_stock_document_item_quantity;
ALTER TABLE stock_document_item DROP CONSTRAINT ck_stock_document_item_amount;

ALTER TABLE stock_document DROP CONSTRAINT ck_stock_document_total;
ALTER TABLE stock_document DROP CONSTRAINT ck_stock_document_paid;
ALTER TABLE stock_document ADD CONSTRAINT ck_stock_document_paid CHECK (
    (type = 'OPENING_STOCK' AND paid_amount = 0)
    OR (type <> 'OPENING_STOCK' AND total_amount >= 0 AND paid_amount >= 0 AND paid_amount <= total_amount)
);

