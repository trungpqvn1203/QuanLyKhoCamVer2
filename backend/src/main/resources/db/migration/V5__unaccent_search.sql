-- Migration V5: tìm kiếm tiếng Việt không dấu ("cam" khớp "Cám", "dong" khớp "Đồng")
-- Dùng bởi CommonSpecifications.keywordContains (backend) — contract-spec: "Lọc danh sách"

-- Extension có sẵn trong gói contrib của PostgreSQL (image Docker chính thức đã kèm).
-- Cần quyền tạo extension khi chạy migration lần đầu.
CREATE EXTENSION IF NOT EXISTS unaccent;

-- unaccent() chỉ là STABLE (kết quả phụ thuộc từ điển có thể đổi) → không dùng được trong index.
-- Bọc lại thành IMMUTABLE, chỉ định rõ từ điển và schema, để sau này tạo được index
-- (ví dụ GIN + pg_trgm khi dữ liệu lớn). replace 'đ'/'Đ' cho chắc chắn, không phụ thuộc
-- phiên bản bộ luật của unaccent.
CREATE OR REPLACE FUNCTION immutable_unaccent(value TEXT)
    RETURNS TEXT
    LANGUAGE sql
    IMMUTABLE
    PARALLEL SAFE
    STRICT
AS $$
    SELECT public.unaccent('public.unaccent'::regdictionary, replace(replace(value, 'đ', 'd'), 'Đ', 'D'))
$$;
