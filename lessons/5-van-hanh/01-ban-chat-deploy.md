# Bản chất deploy

[← Mục lục kho bài học](../README.md)

## Bản chất deploy — nhìn lại từ đầu (2026-10-27)

**Deploy = biến code trên máy mình thành một chương trình đang chạy liên tục trên một máy khác (server Azure `QuanKhoCam`), để người dùng mở trình duyệt là dùng được.** Mọi bước đều xoay quanh 3 thứ, và 3 thứ này được đối xử rất khác nhau:

| Thứ | Ở đâu trong dự án | Thay thế được không? |
|---|---|---|
| **Code** | GitHub → `git pull` về `/opt/grain-warehouse` | Được — build lại bao nhiêu lần cũng ra cùng kết quả |
| **Cấu hình / bí mật** | `.env.prod` trên server (mật khẩu DB, `JWT_SECRET`) — **không** nằm trong git | Giữ nguyên qua các lần cập nhật; mất thì phải tạo lại |
| **Dữ liệu** | Volume Docker `pgdata` (bảng phiếu nhập, công nợ, lương...) | **Không** — mất là mất thật. Chỉ cứu được bằng backup |

### 1. Từ code ra "thứ chạy được" (build)

- Backend: Maven biên dịch Java → một file `app.jar` (chứa cả Spring Boot, Flyway, các file migration `V1..V22`).
- Frontend: Vite biên dịch React/TS → thư mục `dist/` gồm HTML/JS/CSS tĩnh. Trình duyệt chỉ cần mấy file này, server không cần Node nữa.
- **Docker image** = file đã build + đúng môi trường để chạy nó (JRE 21 cho jar, Nginx cho file tĩnh). Dockerfile của dự án làm **hai giai đoạn**: giai đoạn 1 có Maven/Node để build, giai đoạn 2 chỉ copy kết quả sang một image nhỏ. Vì vậy image chạy thật không chứa mã nguồn hay công cụ build.

### 2. Chạy trên server (Docker Compose)

`infra/docker/docker-compose.prod.yml` dựng 3 container và nối dây giữa chúng:

```
Internet ──:80──► web (Nginx) ──/api/──► backend (Spring Boot :8080) ──► db (PostgreSQL :5432)
                  file tĩnh React        mạng "web" + "internal"          chỉ mạng "internal"
```

- Chỉ **cổng 80 của `web`** mở ra ngoài. `db` không có `ports` → từ Internet không ai chạm được DB; chỉ `backend` (cùng mạng `internal`) kết nối được.
- Nginx làm hai việc: trả file React, và chuyển mọi request `/api/...` sang `backend:8080` (reverse proxy). Nhờ vậy trình duyệt chỉ nói chuyện với một địa chỉ, không có lỗi CORS.
- `try_files ... /index.html`: bấm F5 ở `/stock-counts` thì Nginx không có file đó → trả `index.html` → React Router tự hiện đúng trang.
- `depends_on + healthcheck`: db khỏe rồi mới bật backend, backend trả `/api/health` rồi mới bật web.
- `restart: unless-stopped`: server khởi động lại thì container tự chạy lại.

### 3. Cập nhật phiên bản mới — điều gì thực sự xảy ra

```
backup DB ─► git pull ─► docker compose up -d --build ─► Flyway chạy V16..V22 ─► health check
```

1. **Backup trước** (`pg_dump` ra file `.sql.gz`): bước duy nhất bảo vệ được *dữ liệu*.
2. `git pull`: lấy code mới (V16..V22, module mới).
3. `up -d --build`: build lại image, **thay container cũ bằng container mới**. Volume `pgdata` không bị đụng → dữ liệu còn nguyên. Trong ~30–60 giây backend khởi động, người dùng sẽ thấy lỗi (downtime ngắn).
4. Backend khởi động → **Flyway** đọc bảng `flyway_schema_history` trong DB, thấy DB đang ở V15, chạy lần lượt V16 → V22 rồi Spring mới mở cổng. Migration lỗi giữa chừng → backend không lên, healthcheck đỏ.
5. Kiểm tra: `ps` (backend `healthy`), `curl /api/health`, mở trình duyệt.

### 4. Rollback — vì sao backup bắt buộc

- **Code** lùi được dễ: `git checkout <commit cũ>` rồi build lại.
- **DB đã migrate thì không lùi được bằng code.** Ví dụ V21 đã đổi `warehouse_id` thành nullable, V18 đánh dấu bảng lương cũ "đã trả" — bản code cũ không biết những thay đổi đó. Flyway (bản miễn phí) không có "undo". Cách duy nhất về trạng thái cũ là **restore file backup** rồi chạy code cũ.
- Vì vậy migration đã lên server thì **không bao giờ sửa file** (Flyway so checksum, sẽ từ chối khởi động — đúng lỗi đã gặp ở V21 trên máy local), muốn đổi phải viết migration mới.

### 5. Những lỗi deploy đã gặp trong dự án và nguyên nhân gốc

- `docker compose up -d --build` → *"no configuration file provided"*: lệnh không kèm `-f` sẽ tìm `docker-compose.yml` ở **thư mục đang đứng**; file prod nằm ở `infra/docker/docker-compose.prod.yml` → phải chạy từ thư mục gốc repo với `-f ... --env-file .env.prod` (hoặc alias `dc`).
- Clone repo không chỉ thư mục đích → code nằm ở `~/QuanLyKhoCamVer2` thay vì `/opt/grain-warehouse` như tài liệu; lệnh chạy ở thư mục này, `.env.prod` lại ở thư mục kia → lẫn lộn. Server chỉ nên có **một** bản checkout.
- `docker compose down -v`: `-v` xóa volume = xóa toàn bộ dữ liệu. Chỉ dùng `down`.

### 6. Hiện trạng (2026-10-27)

- Deploy đang làm **thủ công** qua SSH (thư mục `.github/workflows` còn trống — chưa có CI/CD tự động).
- Chưa có HTTPS (chưa có tên miền) → cookie đăng nhập cần `COOKIE_SECURE=false` khi chạy bằng HTTP + IP.
- Server đang ở **V15**; lần deploy tới sẽ chạy **V16 → V22** (công nợ NCC, tách tấn, công nợ NV, sổ quỹ, tồn đầu kỳ, công nợ đầu kỳ, kiểm kê).

### Câu hỏi ôn tập

1. Code, cấu hình, dữ liệu — cái nào nằm trong git, cái nào không, cái nào mất là mất thật?
2. Vì sao từ Internet không kết nối thẳng được vào PostgreSQL dù DB đang chạy trên cùng server?
3. Khi chạy `up -d --build`, container backend bị thay mới — vì sao dữ liệu vẫn còn?
4. Deploy V22 xong phát hiện lỗi nghiêm trọng, muốn quay về bản cũ: các bước là gì, và vì sao chỉ `git checkout` là không đủ?
