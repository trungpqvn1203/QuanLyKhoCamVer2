-- Migration V35: Bổ sung thông tin tài khoản ngân hàng vào store_info để in trên đầu phiếu K80 (BR-PRT-01)

ALTER TABLE store_info
    ADD COLUMN bank_account_holder VARCHAR(200),
    ADD COLUMN bank_account        VARCHAR(200);
