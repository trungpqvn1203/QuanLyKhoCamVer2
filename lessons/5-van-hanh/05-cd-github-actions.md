# CD với GitHub Actions: build image, đẩy GHCR, tự deploy

[← Mục lục kho bài học](../README.md)

## Module: CD — push main là tự lên server (2026-11-10, ADR-0006)

**Trạng thái:** đã chạy thật 2026-11-10 — commit `b7c93b6`, server từ `cf88efa` (V24) lên V29, gián đoạn quan sát được < 1 phút. Job deploy đỏ hai lần: lần 1 sai `DEPLOY_KNOWN_HOSTS`; lần 2 app đã lên bản mới nhưng bước kiểm tra cuối gọi nhầm `localhost` (xem "Lỗi đã gặp"). Bản sửa ở nhánh `fix/deploy-health-check`.

Trước module này, mỗi lần cập nhật phải SSH vào VM gõ tay 5 lệnh (bài 01, mục 3). CD biến 5 lệnh đó thành một pipeline:

```
git push main
   │
   ▼ cd.yml
┌─ ci ──────────┐   ┌─ build ─────────────────────┐   ┌─ deploy ──────────────────────────────┐
│ gọi lại ci.yml│──►│ docker build backend + web  │──►│ ssh vào VM, chạy deploy.sh:           │
│ mvn verify    │   │ docker push ghcr.io/...:sha │   │ 1 git fetch + ff-only                 │
│ npm lint/test │   │                             │   │ 2 docker login ghcr.io                │
└───────────────┘   └─────────────────────────────┘   │ 3 pg_dump backup                      │
  đỏ → dừng, không    máy GitHub, không phải VM       │ 4 docker compose pull                 │
  build, không deploy                                 │ 5 up -d --wait (Flyway migrate)       │
                                                      │ 6 /api/health qua Nginx               │
                                                      └───────────────────────────────────────┘
```

### Khái niệm

- **Continuous Integration / Delivery / Deployment** — CI: mỗi push đều build + test. *Delivery*: luôn có bản sẵn sàng, **người bấm** để lên server. *Deployment*: merge `main` là **tự** lên server. Dự án chọn Deployment (ADR-0006) để học đủ luồng; muốn quay về Delivery chỉ cần bật *required reviewers* cho environment `production`, không sửa file.
- **Container registry (GHCR)** — "kho image", giống GitHub nhưng chứa image thay vì code. `docker push` đẩy lên, `docker pull` tải về. Image dự án: `ghcr.io/trungpqvn1203/grain-warehouse-backend:<mã commit>`.
- **Tag = mã commit** — nhìn tag biết ngay image build từ code nào. Không dùng `latest`: `latest` hôm nay và hôm qua là hai image khác nhau, rollback không biết quay về đâu.
- **Reusable workflow (`workflow_call`)** — `cd.yml` gọi `uses: ./.github/workflows/ci.yml` như gọi một hàm. Không chép lại các bước test; CI đổi thì CD tự dùng bản mới. `ci.yml` bỏ `main` khỏi `push` để một commit không chạy test hai lần.
- **`needs`** — `build` chờ `ci` xanh, `deploy` chờ `build` xanh. Một mắt xích đỏ → các bước sau bị bỏ qua.
- **Secret vs variable** — secret (`DEPLOY_SSH_KEY`, `DEPLOY_HOST`...) bị che `***` trong log, không đọc lại được kể cả chủ repo. Variable (`DEPLOY_PATH`) là cấu hình thường, ai xem repo cũng đọc được.
- **`GITHUB_TOKEN`** — GitHub tự tạo cho **mỗi job**, quyền theo khối `permissions`, **hết hạn khi job xong**. Job `build` có `packages: write` để push; job `deploy` chỉ `packages: read` + `contents: read`.
- **Environment `production`** — nhãn cho job deploy: tab Actions có lịch sử "bản nào lên production lúc nào", và là chỗ bật duyệt tay sau này.

### Hai cách build image — khác nhau ở đâu

| | **A. Build trên GitHub, đẩy GHCR** (đã chọn) | B. Build trên VM |
|---|---|---|
| CD làm gì trên VM | `docker compose pull` — chỉ **tải** image | `git pull && up -d --build` — tự động hóa đúng lệnh gõ tay cũ |
| Ai chạy Maven/npm | Runner GitHub (7 GB RAM, miễn phí) | VM 2 GB RAM — Postgres + JVM đang chạy, thêm Maven + npm → dễ OOM (bài 03 mục 5) |
| Thời gian VM bị "nặng" | Vài chục giây pull | 5–10 phút build, người dùng thấy chậm |
| Bản chạy trên server | **Đúng file image** đã build trong pipeline, cùng lần chạy với test | Build lại lần nữa trên VM — về lý thuyết giống, nhưng khác máy, khác thời điểm (dependency tải lại có thể khác) |
| Rollback | `IMAGE_TAG=<commit cũ>` → `pull` + `up` (vài giây, image còn trên GHCR) | `git checkout <cũ>` → build lại 5–10 phút |
| Thêm gì | Registry, đăng nhập GHCR, tên image khớp ở hai file | Không thêm gì |

Ví dụ cụ thể: thứ Hai push sửa màn công nợ. Cách A: lúc 9h00 runner build xong image `a1b2c3d`, 9h01 VM pull 200 MB, chị Lan thấy gián đoạn ~30 giây. Cách B: từ 9h00 đến 9h08 VM vừa build vừa phục vụ, màn tồn kho chậm hẳn; nếu hết RAM, Linux có thể giết **Postgres** đang chạy.

Câu nói gọn khi phỏng vấn: **"Build once, deploy many"** — build một lần, image đó đi qua test rồi lên server, không build lại ở đâu nữa.

### Đi qua từng quyết định trong file

**`cd.yml`**

- `paths-ignore` docs/lessons/md — sửa tài liệu không đổi image, deploy chỉ gây downtime vô ích.
- `concurrency: deploy-production, cancel-in-progress: false` — hai lần push sát nhau thì lần sau **xếp hàng**. Khác `ci.yml` (hủy lần cũ): hủy deploy giữa chừng có thể cắt ngang lúc Flyway đang migrate.
- `--label org.opencontainers.image.source=...` — gắn package GHCR với repo → package thừa hưởng quyền của repo, `GITHUB_TOKEN` của job deploy pull được.
- **`known_hosts` lấy từ secret, không `ssh-keyscan`** — `ssh-keyscan` tin bất cứ máy nào trả lời ở IP đó. Nếu kẻ xấu chen giữa (DNS sai, IP bị cấp lại cho người khác), runner sẽ gửi luôn `GITHUB_TOKEN` cho họ. Host key lấy từ lần SSH tay đã xác nhận là "chứng minh thư" của VM.
- `BatchMode yes` — SSH không bao giờ hỏi mật khẩu/xác nhận; sai là lỗi ngay thay vì treo đến timeout.
- **Chép `deploy.sh` từ commit đang deploy lên VM rồi mới chạy** — nếu chạy bản `deploy.sh` đang nằm trên VM thì đó là bản *cũ* (chưa `git fetch`). Sửa script mà lần deploy đó vẫn chạy script cũ → rất khó hiểu.

**`deploy.sh`**

- `set -euo pipefail` — lệnh nào lỗi thì dừng (`-e`), biến chưa gán là lỗi (`-u`), và `pg_dump | gzip`: pg_dump lỗi thì cả đường ống lỗi (`pipefail`). Thiếu `pipefail`, gzip vẫn thành công → file backup rỗng mà script tưởng ổn.
- **Token đi qua stdin** — `printf token | ssh vm "bash deploy.sh ..."`. Nếu truyền làm tham số, token hiện trong `ps aux` và có thể vào log. Script dùng token cho `git fetch` (qua biến `GIT_CONFIG_*`, không ghi vào `.git/config`) và `docker login`; `trap ... EXIT` luôn `docker logout`. VM **không giữ bí mật GitHub nào lâu dài**.
- `git merge --ff-only` — chỉ "tua tới" theo `main`. Ai đó sửa tay trên VM làm lệch lịch sử → dừng, không âm thầm ghi đè.
- **Backup trước, pull sau** — backup lỗi thì dừng hẳn, chưa có gì thay đổi. Lần đầu (db chưa chạy) thì bỏ qua.
- `IMAGE_TAG` ghi vào `.env.prod` **sau khi pull thành công** — để lần sau gõ tay `dc up -d` vẫn chạy đúng bản này. Ghi trước khi pull mà pull lỗi → `.env.prod` trỏ tới image không có trên máy.
- `up -d --no-build --wait` — `--no-build`: server không bao giờ build. `--wait`: chờ mọi container running/healthy, backend unhealthy → lệnh lỗi → in 80 dòng log ngay trong job Actions.
- Kiểm tra cuối bằng `wget http://localhost/api/health` **trong container web** — đi qua Nginx → backend → DB, đúng con đường của người dùng thật.
- **Không rollback tự động** — nếu V30 đã chạy rồi app lỗi, image cũ không biết cột mới → chạy image cũ cũng lỗi. Phải có người quyết định restore backup hay sửa tiến (bài 01 mục 4).

**`docker-compose.prod.yml`**: service có **cả** `image` lẫn `build`. Trên VM `pull` dùng `image`; ở máy local `up --build` dùng `build` rồi đặt tên theo `image` (tag `local`). Một file chạy được cả hai nơi.

**`.gitattributes`**: `*.sh text eol=lf`. Máy Windows có `core.autocrlf=true` → checkout sẽ đổi xuống dòng thành CRLF; bash trên Linux gặp `\r` báo `$'\r': command not found`.

### Bảo mật: mở cổng 22 cho mọi IP

Runner GitHub có IP thay đổi mỗi lần → NSG không thể chỉ cho "IP của mình" như trước. Đã chọn mở `Any` và bù bằng:
- Chỉ đăng nhập bằng key (`PasswordAuthentication no`) → bot dò mật khẩu cả ngày cũng vô ích.
- **Key riêng cho deploy**, không dùng key cá nhân → lộ thì chỉ cần xóa một dòng trong `authorized_keys`. *Dự án hiện dùng tạm key cá nhân `QuanKhoCam_key.pem` cho gọn — chạy được, nhưng secret lộ thì phải thu hồi luôn đường vào của chính mình.*
- **Hai loại key, đừng nhầm**: *host key* do **VM tự tạo**, chứng minh "tôi là VM thật" (→ `DEPLOY_KNOWN_HOSTS`). *Key đăng nhập* do **phía đăng nhập** tạo (hoặc Azure tạo hộ lúc tạo VM, chính là file `.pem`), chứng minh "tôi được vào" — private ở phía đăng nhập, public nằm trong `authorized_keys` của VM.

Hai phương án khác, khi nào nên dùng: **self-hosted runner** (cài agent lên VM, agent tự gọi ra GitHub nhận việc → không cần mở cổng vào nào); **Azure CLI** mở tạm IP runner trong lúc deploy rồi đóng. Cả hai an toàn hơn nhưng nhiều thành phần hơn.

### Lỗi đã gặp khi chạy lần đầu

**`No ED25519 host key is known for *** and you have requested strict checking. Host key verification failed.`** (bước *Upload deploy script*)

- Nghĩa: VM đưa ra host key loại ED25519; runner tìm trong `~/.ssh/known_hosts` (dựng từ secret `DEPLOY_KNOWN_HOSTS`) một dòng **bắt đầu đúng bằng chuỗi `DEPLOY_HOST`** và có key ED25519 — không thấy → `StrictHostKeyChecking yes` chặn ngay.
- Nguyên nhân: nội dung secret `DEPLOY_KNOWN_HOSTS` nhập ban đầu không phải dòng `<IP> ssh-ed25519 ...` khớp với `DEPLOY_HOST`.
- Sửa: lấy dòng từ `known_hosts` **của máy mình** — nơi đã lưu host key thật từ lần SSH đầu tiên mình tự gõ `yes`: `ssh-keygen -F 20.2.216.219 -f ~/.ssh/known_hosts | grep ssh-ed25519`. Update secret → *Re-run failed jobs* (không build lại: image đã có trên GHCR).
- Vì sao không bị hại gì: lỗi xảy ra trước khi kết nối được → `deploy.sh` chưa chạy, server không đổi. Chuỗi `needs` + `set -e` đảm bảo "đỏ là dừng ở chỗ an toàn".
- Bài học: secret không xem lại được → khi nghi sai, **ghi đè** bằng giá trị lấy từ nguồn đã kiểm chứng, đừng đoán.

**`6/6 ... wget: can't connect to remote host: Connection refused`** — app đã lên, job vẫn đỏ

- Log bước 5/6: cả 3 container `Healthy`. Từ Internet gọi `/api/health` vẫn 200. Chỉ lệnh kiểm tra cuối `wget http://localhost/api/health` (chạy trong container web) bị từ chối.
- Nguyên nhân: trong `nginx:alpine`, `getent hosts localhost` → **`::1`** (IPv6) đứng trước. `default.conf` chỉ `listen 80;` → Nginx chỉ nghe IPv4 → gõ cửa IPv6 thì không ai mở. Đã tái hiện trên máy bằng container Nginx tối giản: `wget localhost` lỗi, `wget 127.0.0.1` OK.
- Vì sao healthcheck của backend dùng `localhost:8080` lại chạy? Java/Tomcat mặc định nghe **cả** IPv4 lẫn IPv6.
- Sửa: dùng `127.0.0.1` (rõ ràng, không phụ thuộc cách phân giải tên). Cách khác: thêm `listen [::]:80;` vào Nginx — đổi cấu hình server chỉ vì một lệnh kiểm tra thì không đáng.
- Hậu quả lúc đó: bản mới **đã chạy**, `IMAGE_TAG` đã ghi; chỉ thiếu bước dọn image cũ. Không cần rollback.
- **Bài học lớn hơn:** mình đã kết luận "deploy thành công" chỉ từ quan sát bên ngoài (site đổi bản, health 200) mà chưa xem trạng thái job. Quan sát bên ngoài chứng minh *app chạy*, không chứng minh *pipeline xanh*. Pipeline đỏ nghĩa là lần sau không biết tin vào đâu — phải nhìn cả hai.

**Hai lầm tưởng của chính mình trong lúc làm** (đáng nhớ hơn cả lỗi):
- "Key SSH là do VM cung cấp" — chỉ đúng với **host key**. Key đăng nhập do phía đăng nhập tạo; file `.pem` trông như VM cấp vì Azure Portal tạo hộ lúc tạo VM.
- "CI xanh xong phải bấm tay CD" — không: một lần push `main` chạy cả chuỗi ci → build → deploy. Tách bước chỉ là cách thận trọng cho lần đầu.

**Cách kiểm chứng từ bên ngoài** (không cần vào tab Actions): tên file `assets/index-*.js` trong trang chủ đổi → image `web` mới đã chạy; `/api/health` 200 → backend mới đã qua Flyway và Hibernate `validate`, tức migration mới đã áp đúng (schema lệch thì backend không lên). Lưu ý `/actuator/health` qua Nginx trả **200 nhưng là `index.html`** (SPA fallback) — không phải bị lộ actuator; phải nhìn nội dung, không chỉ nhìn mã HTTP.

### Lựa chọn và phương án thay thế

- **`docker build` + `docker push` bằng lệnh thường** thay vì action `docker/build-push-action`: thấy rõ từng lệnh, giống hệt chạy tay. Đổi sang action khi cần cache layer giữa các lần chạy (build nhanh hơn) hoặc build đa kiến trúc.
- **`workflow_call`** thay vì `workflow_run` (CD tự bật khi CI xong): một lần chạy hiện đủ chuỗi ci → build → deploy trên một màn hình.
- **Backup trên cùng VM** — đủ để quay lại sau migration lỗi, *không* đủ khi mất VM. Đẩy backup ra ngoài (Azure Blob) là việc riêng.

### Đọc kết quả

Tab **Actions** → **CD** → lần chạy: sơ đồ 3 khối `ci → build → deploy`. Đỏ ở khối nào thì đọc bảng "Khi deploy đỏ" trong `docs/maintenance/deployment.md`. Tab **Code → Packages** (bên phải trang repo) thấy hai image và các tag.

### Kể trong phỏng vấn (~1 phút)

> "Mỗi lần push nhánh, GitHub Actions chạy `mvn verify` với Postgres thật và lint/test/build frontend. Merge vào main thì pipeline CD chạy lại CI, build hai Docker image trên runner, tag bằng mã commit rồi đẩy lên GitHub Container Registry. Job cuối SSH vào VM Azure, chạy script: backup DB bằng pg_dump, pull image, `docker compose up --wait`, Flyway tự migrate, rồi gọi health check qua Nginx. Server không build gì, không lưu token GitHub. Lỗi thì dừng và in log; rollback làm tay vì migration không tự lùi được."

### Câu hỏi ôn tập

1. Vì sao tag image bằng mã commit mà không dùng `latest`?
2. Build image trên VM thay vì trên GitHub thì mất những gì? Nêu ít nhất ba điểm.
3. `ci.yml` có `cancel-in-progress: true`, `cd.yml` lại `false`. Vì sao khác nhau?
4. Vì sao không dùng `ssh-keyscan` trong workflow để lấy host key của VM?
5. Token GitHub đi từ đâu tới VM, được dùng vào việc gì, và vì sao VM không cần lưu Personal Access Token?
6. Bỏ `pipefail` khỏi `deploy.sh` thì lỗi `pg_dump` sẽ bị che giấu thế nào?
7. Deploy xong mới phát hiện lỗi, và bản đó có migration V30. Vì sao pipeline không tự rollback? Các bước rollback tay là gì?
8. Mở cổng 22 cho mọi IP — rủi ro là gì, đã bù bằng gì, và hai phương án nào tránh được việc mở cổng?
