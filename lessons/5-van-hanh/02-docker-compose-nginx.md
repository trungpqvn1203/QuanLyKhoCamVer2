# Đóng gói & triển khai: Docker Compose + Nginx

[← Mục lục kho bài học](../README.md)

## Module: Đóng gói & triển khai (2026-10-20, Docker Compose + Nginx)

### Khái niệm

- **Image vs container** — image là "bản đóng gói" chỉ đọc (JRE + `app.jar`); container là một lần chạy image đó. `docker compose up --build` dựng lại image từ code mới rồi thay container; dữ liệu Postgres không mất vì nằm trong **volume** `pgdata`, không nằm trong container.
- **Multi-stage build** — `backend.Dockerfile` giai đoạn 1 dùng image Maven (~800 MB) để build jar, giai đoạn 2 chỉ chép jar sang image JRE alpine (~200 MB). Server chạy image nhỏ, không chứa Maven hay mã nguồn.
- **Cache theo lớp** — chép `pom.xml` và tải dependency *trước* khi chép `src/`. Sửa một service rồi build lại → Docker dùng lại lớp dependency, không tải lại hàng trăm jar.
- **Reverse proxy** — người dùng chỉ nói chuyện với Nginx ở cổng 80. `/api/...` được Nginx chuyển tới `backend:8080` (tên service = tên máy trong mạng Docker); mọi đường dẫn khác trả file React. Nhờ chung một tên miền, cookie `GW_AT` gửi kèm mọi request API mà không cần CORS.
- **SPA fallback** — mở thẳng `http://.../inventory`: không có file `inventory` trên đĩa → `try_files ... /index.html` trả trang React, React Router mới hiển thị màn tồn kho. Thiếu dòng này F5 sẽ 404.
- **Mạng `internal: true`** — Postgres chỉ nằm trong mạng `internal`, không publish cổng. Từ Internet không có đường nào tới DB; backend là cầu nối duy nhất. Lưu ý: Docker publish cổng bằng iptables **vượt qua ufw**, nên "đã chặn ufw" không đủ nếu lỡ thêm `ports: 5432` cho DB.
- **Profile `prod` không có giá trị mặc định** — `application-dev.yml` có `${POSTGRES_HOST:localhost}`; `application-prod.yml` chỉ có `${SPRING_DATASOURCE_URL}`. Thiếu biến thì backend dừng ngay khi khởi động, thay vì âm thầm nối vào một DB sai. Tương tự `${JWT_SECRET:?...}` trong compose báo lỗi trước khi container nào chạy.
- **Cookie `Secure` và HTTP** — cookie đăng nhập có cờ `Secure` nên trình duyệt chỉ gửi qua HTTPS (ngoại lệ: `localhost`). Deploy bằng `http://<IP>` mà để `COOKIE_SECURE=true` → đăng nhập "thành công" nhưng request sau vẫn 401. Đây là lý do HTTPS (cần tên miền) là bước bắt buộc trước khi dùng thật.
- **`.dockerignore` theo Dockerfile** — build frontend dùng thư mục gốc làm context; không lọc thì `.env` (chứa mật khẩu) và `node_modules` bị gửi vào quá trình build. `frontend.Dockerfile.dockerignore` chỉ cho phép `frontend/` và `infra/nginx/`.
- **Healthcheck + `depends_on: condition`** — `web` chỉ khởi động khi `backend` trả 200 ở `/api/health`, `backend` chỉ khởi động khi `pg_isready`. Không có điều kiện này, backend có thể chạy trước khi Postgres sẵn sàng và Flyway lỗi kết nối.

### Bản chất từng bước deploy — đi theo hành trình từ code tới trình duyệt

Bức tranh chung: **máy bạn** (viết code) → **GitHub** (kho trung chuyển code) → **VM Azure** (máy bật 24/7, có IP public) → **Docker** (3 container) → **trình duyệt** của chị Lan ở cửa hàng.

#### Bước 1 — Máy ảo: một chiếc máy luôn bật, ai cũng gõ cửa được

- **VM là gì** — Azure có máy chủ vật lý trong datacenter; phần mềm *hypervisor* chia CPU/RAM/đĩa thành nhiều máy ảo. Bạn thuê một phần như vậy. Khác máy bạn ở hai điểm: **luôn bật** và có **IP public**, tức là bất kỳ ai trên Internet cũng gửi được gói tin tới nó. Vài phút sau khi tạo VM, bot đã bắt đầu dò cổng 22 bằng hàng nghìn mật khẩu. Vì vậy bảo mật phải làm ngay từ bước đầu, không để sau.
- **SSH key thay vì mật khẩu** — là một cặp khóa: *public key* nằm trên VM (`~/.ssh/authorized_keys`), *private key* (`.pem`) nằm trên máy bạn. Khi đăng nhập, VM gửi một "câu đố", máy bạn ký câu đố bằng private key, VM kiểm chữ ký bằng public key. **Private key không bao giờ đi qua mạng**, nên không có gì để dò hay nghe lén. `icacls` giới hạn quyền đọc file `.pem`: ssh từ chối dùng key mà người dùng khác trên máy cũng đọc được.
- **Network Security Group (firewall)** — là lớp chặn gói tin *trước khi* chúng tới VM. Chỉ mở 22 (chỉ cho IP của bạn), 80, 443. Cổng 5432 của Postgres không mở, nên dù mật khẩu DB có lộ, người ngoài cũng không có đường nào kết nối tới DB.
- **IP tĩnh + DNS name label** — DNS giống danh bạ: `grainwh.southeastasia.cloudapp.azure.com` → `20.x.x.x`. Nếu IP là động, mỗi lần Stop/Start VM sẽ có IP mới và tên miền trỏ sai chỗ. Ở Bước 5, HTTPS cũng cần một tên miền ổn định.
- **Swap** — là "RAM giả" trên ổ đĩa. Lúc build, Maven và npm chạy cùng lúc với Postgres và JVM, có thể vượt 2 GB RAM. Khi đó Linux kích hoạt *OOM killer* và giết một tiến trình (thường là build hoặc Postgres). Swap chậm nhưng giúp tiến trình không bị giết.
- **Vì sao cài Docker thay vì cài thẳng Java + Postgres + Nginx** — image đóng gói đúng JRE 21, đúng Postgres 16, đúng file `nginx.conf` mà bạn đã chạy thử ở local, nên chạy được ở local thì lên server cũng chạy giống hệt. Gỡ cũng sạch: xóa container là xong, không để lại phần mềm rải rác trên máy.

#### Bước 2 — Lấy code và bí mật: tách "chạy cái gì" khỏi "chạy với chìa khóa nào"

- **`git clone`** — VM tải code từ GitHub. Code có thể chia sẻ; **bí mật thì không bao giờ được đưa vào git**, vì lịch sử git giữ mọi phiên bản: xóa mật khẩu ở commit sau thì commit cũ vẫn còn trong mọi bản clone.
- **`.env.prod`** — cùng một image backend chạy ở local với `JWT_SECRET` A và trên server với `JWT_SECRET` B. Code không đổi, chỉ cấu hình đổi (nguyên tắc *12-factor app*). `chmod 600` để chỉ chủ file đọc được.
- **`JWT_SECRET` quan trọng cỡ nào** — đây là khóa ký access token. Ai có khóa này có thể tự ký một token `roles: ["SYSTEM_ADMIN"]` và vào được mọi màn hình, kể cả bảng lương, mà không cần mật khẩu. Vì vậy dùng `openssl rand` (48 byte ngẫu nhiên), và mỗi môi trường một khóa khác nhau. Đổi khóa thì mọi người phải đăng nhập lại.
- **Cạm bẫy `POSTGRES_PASSWORD`** — image Postgres **chỉ đọc biến này một lần**, khi volume còn trống, để khởi tạo DB. Sau đó sửa mật khẩu trong `.env.prod` thì DB vẫn giữ mật khẩu cũ, còn backend gửi mật khẩu mới → lỗi `password authentication failed`. Muốn đổi mật khẩu phải chạy `ALTER USER` trong DB.

#### Bước 3 — `docker compose up -d --build`: chuyện gì xảy ra, theo thứ tự

1. Compose đọc file compose và thay mọi `${...}` bằng giá trị trong `.env.prod`. Thiếu biến bắt buộc (`:?`) thì dừng ngay, chưa container nào chạy.
2. **Build image `backend`**: Maven biên dịch code thành `app.jar`, rồi chép jar sang image JRE. **Build image `web`**: `npm run build` biến React thành vài file HTML/JS/CSS tĩnh trong `dist/`, rồi chép sang image Nginx. Lưu ý: khi chạy, server **không có Node**. Frontend chỉ còn là file tĩnh; code React chạy trong *trình duyệt* của người dùng.
3. Tạo 2 mạng (`internal`, `web`) và volume `pgdata`.
4. Khởi động theo dây chuyền:
   - Bật `db`, chờ `pg_isready`.
   - Bật `backend`: Spring đọc biến môi trường và kết nối tới `db:5432`. Docker có DNS nội bộ, phân giải tên service `db` thành IP của container.
   - **Flyway** so bảng `flyway_schema_history` với các file `V1..V14` và chỉ chạy những file chưa có.
   - Hibernate `validate` kiểm tra bảng có khớp entity không, rồi `BootstrapAdminRunner` tạo admin nếu bảng `app_user` còn trống.
   - Khi `/api/health` trả 200 thì backend được tính là healthy, và lúc đó `web` mới khởi động.
5. `-d` = chạy nền (đóng SSH vẫn chạy). `restart: unless-stopped` = VM khởi động lại hoặc backend bị crash thì Docker tự bật lại.

#### Bước 4 — Một request đi qua những đâu (chị Lan mở màn tồn kho)

1. Trình duyệt hỏi DNS: `grainwh...cloudapp.azure.com` là IP nào → `20.x.x.x`.
2. Kết nối tới `20.x.x.x:80` → NSG cho qua → Docker chuyển tiếp từ cổng 80 của VM vào cổng 80 của container `web`.
3. Nginx thấy `/inventory` không phải file có sẵn, nên trả `index.html`. Trình duyệt tải tiếp `/assets/index-a1b2c3.js`; file này được cache 1 năm vì tên đã chứa hash, code đổi thì tên đổi.
4. React chạy trong trình duyệt và gọi `fetch('/api/inventory')` kèm cookie `GW_AT` → Nginx khớp `location /api/` → chuyển tới `http://backend:8080` → Spring Security kiểm JWT → `InventoryService` → SQL tới `db` → JSON đi ngược về theo đường cũ.

**Điểm mấu chốt:** trình duyệt chỉ biết *một* địa chỉ. Nó không hề biết có backend hay DB phía sau. Nhờ vậy frontend và API cùng origin (không cần CORS), còn DB thì người ngoài không nhìn thấy.

#### Bước 5 — HTTPS: vì sao bắt buộc trước khi dùng thật

- **HTTP gửi dữ liệu dạng văn bản thường** — bất kỳ ai đứng giữa đường truyền (wifi quán cà phê, router, nhà mạng) đều đọc được mật khẩu lúc đăng nhập và cookie `GW_AT`, rồi dùng cookie đó để mạo danh. **TLS** mã hóa đường truyền *và* chứng minh mình đang nói chuyện đúng server.
- **Chứng chỉ** — là một tờ giấy do CA (Let's Encrypt) ký, xác nhận "public key này thuộc về `grainwh...azure.com`". Trình duyệt tin CA nên tin server.
- **Let's Encrypt kiểm tra bạn là chủ tên miền bằng cách nào (HTTP-01)** — nó yêu cầu đặt một chuỗi bí mật tại `http://grainwh.../.well-known/acme-challenge/<token>`, rồi tự truy cập để đọc. Chỉ người điều khiển server mà tên miền đang trỏ tới mới làm được việc này. Vì vậy cần tên miền và cần mở cổng 80. Chứng chỉ có hạn 90 ngày, nên phải có tác vụ gia hạn tự động.
- **Cờ `Secure` trên cookie** — bảo trình duyệt "chỉ gửi cookie này qua HTTPS". Do đó với `http://IP` và `COOKIE_SECURE=true`, trình duyệt không gửi cookie lại → mọi API trả 401. `COOKIE_SECURE=false` chỉ để chạy thử, và cũng chính là để lộ cookie như mô tả ở trên.

#### Cập nhật phiên bản — vì sao làm theo đúng thứ tự đó

- **Backup trước** — migration mới có thể `ALTER`/`DROP` cột, và Flyway không hoàn tác được *dữ liệu*. File backup là nút "undo" duy nhất.
- **`git pull` → `up -d --build`** — Compose chỉ tạo lại container nào có image hoặc cấu hình thay đổi. `db` dùng image `postgres:16` không đổi nên vẫn chạy; dữ liệu nằm trong volume nên còn nguyên.
- **Gián đoạn ngắn** — khoảng 20 giây, lúc backend cũ đã dừng mà backend mới chưa healthy, Nginx trả lỗi 502. Với MVP thì chấp nhận được. Khi không được phép gián đoạn mới cần *blue-green deployment*: chạy song song bản mới, kiểm tra xong mới chuyển hướng traffic.
- **`docker image prune`** — mỗi lần build để lại image cũ vài trăm MB; ổ 30 GB sẽ đầy sau vài chục lần deploy.

### Lựa chọn và phương án thay thế

- **Một container Nginx vừa phục vụ React vừa proxy API** thay vì tách container frontend + container Nginx riêng: ít thành phần hơn. Tách khi cần đưa frontend lên CDN hoặc nhiều app sau một proxy.
- **Build image ngay trên VPS** (`--build`) thay vì đẩy image lên registry (GHCR) rồi `pull`: không cần thêm tài khoản/secret. Đổi sang registry khi có CI/CD — server không phải build, deploy nhanh và bản chạy chính là bản CI đã test.
- **Test không chạy trong `docker build`** (`-DskipTests`): test dùng Testcontainers cần Docker bên trong Docker. Chạy `mvn verify` trước khi deploy (sau này trong GitHub Actions).

### Câu hỏi ôn tập (để cuối dự án)

1. Vì sao `docker compose down -v` nguy hiểm còn `down` thì không?
2. Bỏ `try_files $uri $uri/ /index.html` thì người dùng gặp lỗi gì, ở thao tác nào?
3. Vì sao Spring Boot gọi được `jdbc:postgresql://db:5432/...` dù không có máy nào tên `db` trong mạng LAN?
4. Lộ `JWT_SECRET` nguy hiểm hơn lộ mật khẩu admin ở điểm nào? Phải làm gì ngay khi bị lộ?
5. Sửa `POSTGRES_PASSWORD` trong `.env.prod` rồi `up -d` thì chuyện gì xảy ra, vì sao?
6. Vì sao Let's Encrypt không cấp được chứng chỉ khi cổng 80 bị chặn?
