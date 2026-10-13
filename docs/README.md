# Bản đồ tài liệu — Grain Warehouse

Hướng dẫn tra cứu tài liệu theo mục đích công việc.

## Cấu trúc thư mục

```
docs/
├── README.md               ← Bạn đang ở đây
├── business/               # Nghiệp vụ (tra cứu xuyên suốt vòng đời)
│   ├── overview.md          # Bài toán, phạm vi MVP, tiêu chí hoàn thành
│   ├── rules.md             # Quy tắc nghiệp vụ (✅/⚠️/❓)
│   └── glossary.md          # Bảng thuật ngữ Việt - Anh
├── architecture/           # Thiết kế kỹ thuật
│   ├── system-design.md     # Kiến trúc tổng thể, module, transaction
│   └── database.md          # Schema, constraints, concurrency
├── api/                    # Hợp đồng giao tiếp FE ↔ BE
│   └── contract-spec.md     # Chuẩn ApiResponse, error code, pagination
├── roadmap/                # Kế hoạch phát triển
│   └── plan.md              # Lộ trình 4 tuần, checklist theo phase
├── maintenance/            # Vận hành, bảo trì & sự cố
│   ├── deployment.md        # Triển khai, CI/CD, secrets
│   ├── backup-restore.md    # Backup DB & phục hồi
│   └── troubleshooting.md   # Runbook xử lý lỗi Production
└── decisions/              # Architecture Decision Records
    └── ADR-0001-stack.md
```

## Hướng dẫn đọc theo mục đích (Targeted Context Loading)

> **Nguyên tắc:** Đọc đúng & đủ mọi tệp liên quan trực tiếp đến phạm vi tác vụ.
> Chỉ loại trừ các tài liệu hoàn toàn không dính líu.

| Mục đích công việc | Tài liệu cần đọc | Loại trừ |
|---|---|---|
| **Phát triển Module mới** (CRUD, nghiệp vụ) | `business/rules.md` (quy tắc module đó) · `api/contract-spec.md` (format API) · `architecture/database.md` (schema liên quan) · `architecture/system-design.md` (nếu liên quan cross-module) | `maintenance/*` · `roadmap/*` |
| **Sửa lỗi nghiệp vụ / Bug Fix** | `business/rules.md` (check vi phạm quy tắc nào) · `architecture/system-design.md` (boundary transaction) · `architecture/database.md` (nếu liên quan data) | `api/*` (trừ khi bug ở tầng API) · `maintenance/*` · `roadmap/*` |
| **Thay đổi Database / Migration** | `architecture/database.md` (quan hệ, index, CHECK) · `business/rules.md` (4 hard rules) | `api/*` · `maintenance/*` |
| **Tích hợp FE với BE** (API client, form, error handling) | `api/contract-spec.md` (chuẩn Response/Error) · `business/glossary.md` (nếu cần mapping tên trường) | `architecture/database.md` · `maintenance/*` |
| **Triển khai, Bảo trì & Sự cố** | `maintenance/deployment.md` · `maintenance/backup-restore.md` · `maintenance/troubleshooting.md` | `business/*` · `api/*` · `architecture/*` |
| **Review tiến độ / Chuyển Phase** | `roadmap/plan.md` · `NOTES.md` | Toàn bộ code & technical docs |
| **Quyết định kỹ thuật quan trọng** | `decisions/ADR-*.md` liên quan · tài liệu kỹ thuật bị ảnh hưởng | Tùy phạm vi quyết định |

## Quy tắc mở rộng

Khi dự án phình to, tách nhỏ file theo module trong cùng thư mục:
- `business/rules.md` → `business/rules-inventory.md`, `business/rules-pricing.md`
- `architecture/database.md` → `architecture/database-inventory.md`, `architecture/database-auth.md`

Cấu trúc thư mục giữ nguyên, chỉ thêm file.
