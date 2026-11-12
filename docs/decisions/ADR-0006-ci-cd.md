# ADR-0006: CI/CD — build image trên GitHub, tự deploy khi push main

## Trạng thái

✅ **Đã chấp nhận — 2026-11-10.** Code: `.github/workflows/cd.yml`, `infra/deploy/deploy.sh`. **Đã chạy thật 2026-11-10**: commit `b7c93b6` lên production (V24 → V29), app đã lên bản mới; job deploy đỏ lần 1 do sai `DEPLOY_KNOWN_HOSTS`, lần 2 do bước kiểm tra cuối gọi `localhost` (→ IPv6) — sửa ở `fix/deploy-health-check`.

Thay thế định hướng cũ trong `deployment.md` ("Continuous Delivery — deploy cần bấm tay").

## Bối cảnh

- Trước đây deploy bằng tay: SSH → `pg_dump` → `git pull` → `up -d --build` → `curl /api/health`. Dễ quên backup, gõ nhầm thư mục, và build Maven + npm trên VM 2 GB RAM có nguy cơ OOM.
- CI (`ci.yml`) đã có, chưa đụng tới server.
- Dự án cá nhân để học; người dùng chọn tự động hoàn toàn để trải nghiệm đủ luồng.

## Quyết định

| Câu hỏi | Chọn | Phương án thay thế | Khi nào đổi |
|---|---|---|---|
| Build image ở đâu | **Trên runner GitHub, đẩy GHCR**, tag = mã commit | Build trên VM (`git pull && up --build` qua SSH) — ít bước hơn nhưng VM vẫn build, bản chạy không phải bản CI đã test, khó rollback | — |
| Khi nào deploy | **Tự động khi push `main`** (Continuous Deployment) | Bấm tay (`workflow_dispatch`, Continuous Delivery) | Khi có người dùng thật: bật *required reviewers* cho environment `production` → mỗi lần deploy chờ duyệt, không phải sửa workflow |
| Vào VM bằng cách nào | **SSH, NSG mở cổng 22 cho mọi IP**, chỉ cho đăng nhập bằng key (đã kiểm `sshd -T`: `passwordauthentication no`). Key: **tạm dùng chung key cá nhân `QuanKhoCam_key.pem`** (chọn 2026-11-10 cho gọn) — secret lộ thì phải thu hồi cả key của chủ; nên đổi sang key riêng (deployment.md bước 2) | Self-hosted runner trên VM (không mở cổng nào) · Azure CLI mở tạm IP runner rồi đóng | Khi log SSH bị dò quá nhiều hoặc VM có dữ liệu nhạy cảm hơn → self-hosted runner |
| Token trên server | **Không lưu**. `GITHUB_TOKEN` của job deploy truyền qua stdin, dùng để `git fetch` + pull GHCR, hết hạn khi job xong | Personal Access Token lưu trên VM | — |
| Kiểm tra trước deploy | Gọi lại `ci.yml` (`workflow_call`) trong cùng lần chạy | Kích hoạt bằng `workflow_run` khi CI xong | — |
| Lỗi khi deploy | Dừng, in log, **không rollback tự động** | Tự quay về image cũ | Không làm: migration đã chạy thì image cũ có thể không khớp schema — rollback cần người quyết định có restore DB hay không |

## Hệ quả

- Mỗi lần push `main` có code thay đổi → downtime ~30 giây và migration chạy ngay. Phải chắc `main` luôn xanh: làm trên nhánh, CI xanh mới merge.
- Mỗi lần deploy có một file `~/backups/pre-deploy-<thời gian>-<commit>.sql.gz` trên VM (giữ 30 ngày). Backup vẫn nằm trên **cùng VM** — mất VM là mất cả backup; backup ra ngoài là việc riêng.
- Tên image khai báo ở hai nơi phải khớp: `IMAGE_PREFIX` trong `cd.yml` và `image:` trong `docker-compose.prod.yml`.
- `.env.prod` có thêm dòng `IMAGE_TAG` do `deploy.sh` tự ghi.
- **Lần deploy đầu (server V24 → V29) bỏ trống V28** — đã chốt 2026-11-10: nhánh `feature/delivery-trip` phải **đổi `V28` thành số còn trống kế tiếp trước khi merge** vào `main` (ban đầu dự kiến `V30`; từ 2026-11-12 `V30` đã là điều chuyển kho nên sẽ là `V31` hoặc số lớn hơn), nếu không Flyway trên production từ chối migration "đến muộn" và backend không khởi động.
