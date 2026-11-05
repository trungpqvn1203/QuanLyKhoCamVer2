# Nền tảng deploy: một request đi qua những tầng nào

[← Mục lục kho bài học](../README.md)

## Bài nền tảng trước CI/CD và HTTPS (2026-11-05)

Hai bài trước trả lời "deploy gồm những bước gì". Bài này đi xuống một tầng: **khi khách bấm "Đặt đơn" trên điện thoại,
máy móc thật sự làm gì**. Hiểu được đường đi này thì gặp lỗi sẽ đoán được hỏng ở tầng nào, và sẽ thấy CI/CD hay HTTPS
chỉ là gắn thêm vào một tầng cụ thể, không phải thứ gì bí ẩn.

### 1. Hành trình của một request — anh Tư đặt 20 bao cám từ điện thoại

Anh Tư mở app khách `/khach`, bấm gửi đơn → trình duyệt gửi `POST /api/portal/orders`:

```
Điện thoại anh Tư (4G)
  │ ① DNS: "grainwh.southeastasia.cloudapp.azure.com" là IP nào?  → 20.x.x.x
  │ ② TCP: mở kết nối tới 20.x.x.x cổng 80 (sau này 443)
  ▼
Azure NSG (tường lửa của cloud, nằm NGOÀI máy ảo) — cho cổng 22/80/443 đi qua
  ▼
Máy ảo Ubuntu — nhân Linux nhận gói tin
  │ ③ iptables (Docker tự ghi): "cổng 80 của máy → cổng 80 của container web"
  ▼
Container web (Nginx)
  │ ④ khớp location /api/ → proxy_pass http://backend:8080
  │    tên "backend" được DNS nội bộ của Docker (127.0.0.11) trả về IP container
  ▼
Container backend (Spring Boot :8080)
  │ ⑤ filter bảo mật đọc cookie GW_CAT → biết là khách nào → SalesOrderService
  │ ⑥ HikariCP mượn sẵn một kết nối tới db:5432 → INSERT phiếu PENDING → COMMIT
  ▼
Container db (PostgreSQL) — ghi xuống volume pgdata trên ổ đĩa máy ảo
```

Phản hồi đi ngược lại đúng con đường đó. Có **ba lớp "tường"** chồng lên nhau: NSG của Azure, iptables trên máy ảo,
và mạng Docker (`internal: true` cho DB). Một cổng chỉ thông khi cả ba lớp đều cho qua.

### 2. Đọc thông báo lỗi để biết tầng nào hỏng

Đây là phần dùng nhiều nhất khi vận hành. Mỗi tầng hỏng cho ra một loại lỗi **khác nhau**:

| Trình duyệt / curl thấy | Nghĩa là | Tầng hỏng thường gặp trong dự án |
|---|---|---|
| `NXDOMAIN` / "không tìm thấy địa chỉ" | ① DNS không ra IP | Gõ sai tên miền, bản ghi A chưa trỏ, DNS label Azure chưa đặt |
| **Timeout** (chờ mãi rồi báo hết giờ) | Gói tin bị **nuốt**, không ai trả lời | VM đang tắt (auto-shutdown 23:00), NSG chưa mở cổng, đổi IP động |
| **Connection refused** (báo lỗi ngay) | Tới được máy, nhưng **không tiến trình nào nghe** cổng đó | Container `web` chết, hoặc mở 443 trên NSG mà compose chưa publish `443` |
| **502 Bad Gateway** (trang lỗi Nginx) | Nginx sống, **không nói chuyện được với backend** | Backend đang khởi động / Flyway đang chạy migration / backend crash |
| **504 Gateway Timeout** | Backend nhận nhưng trả lời quá `proxy_read_timeout 60s` | Truy vấn chậm, bị khóa dòng chờ lâu |
| **500** + JSON envelope lỗi | Backend chạy, lỗi trong code hoặc DB | DB hết kết nối, bug |
| `NET::ERR_CERT_...` | (khi có HTTPS) chứng chỉ hết hạn / sai tên | Quên gia hạn Let's Encrypt, truy cập bằng IP thay vì tên miền |

**Timeout và refused khác nhau ở bản chất:** refused = máy trả lời "không có ai ở đây" → máy đang bật và tường lửa cho qua,
chỉ thiếu tiến trình. Timeout = không ai trả lời → nghi tường lửa hoặc máy tắt trước. Chỉ từ hai chữ đó đã loại được nửa số nguyên nhân.

Lệnh kiểm tra từng tầng, đi từ ngoài vào trong:

```bash
nslookup grainwh.southeastasia.cloudapp.azure.com     # ① (chạy trên máy mình)
curl -v http://<tên miền>/api/health                  # ②③④⑤⑥ cả đường — -v cho thấy dừng ở bước nào
# trên server:
sudo ss -tlnp | grep -E ':80|:443'                     # ③ tiến trình nào đang nghe cổng của máy
dc ps                                                  # container nào chết / unhealthy
dc exec web wget -qO- http://backend:8080/api/health   # ④ từ trong Nginx gọi thẳng backend, bỏ qua tầng ngoài
dc logs --tail=100 backend                             # ⑤⑥ lỗi Spring / Flyway / kết nối DB
```

### 3. Cổng, tiến trình và chữ `localhost`

- **Cổng (port)** là số hiệu để nhân Linux biết gói tin giao cho tiến trình nào. Tại một thời điểm một cổng chỉ có
  **một** tiến trình nghe → chạy hai bản Nginx cùng cổng 80 thì bản sau báo `address already in use` (lý do có biến `HTTP_PORT=8088` khi chạy local).
- **Mỗi container có mạng riêng, nên có `localhost` riêng.** Trong container backend, `localhost:8080` là chính Spring Boot
  → healthcheck của backend dùng `localhost` là đúng. Nhưng trong container web, `localhost:8080` là... chính Nginx, không có gì
  → vì vậy Nginx phải gọi `backend:8080`. Nhầm chỗ này là lỗi kinh điển khi mới dùng Docker.
- **`ports: "80:80"`** nghĩa là "cổng 80 của **máy ảo** → cổng 80 của **container**". Không có dòng này thì container vẫn chạy,
  vẫn nghe cổng 80 *bên trong*, nhưng thế giới bên ngoài không tới được — đó chính là cách `db` được giấu đi.

### 4. Container thật ra là gì

Container **không phải máy ảo**. Nó là một tiến trình Linux bình thường (chạy `ps aux` trên server vẫn thấy `java -jar /app/app.jar`),
được nhân Linux "bịt mắt" bằng hai cơ chế:

- **Namespaces** — cho tiến trình một góc nhìn riêng: cây thư mục riêng (thấy `/app/app.jar` chứ không thấy `/home/azureuser`),
  mạng riêng (localhost riêng như mục 3), danh sách tiến trình riêng.
- **cgroups** — giới hạn tài nguyên được dùng (RAM, CPU). Compose prod hiện **chưa đặt giới hạn**, nên một container ngốn RAM có thể kéo cả máy.

Hệ quả trực tiếp cho dự án:

- **Container dùng xong là bỏ.** Mọi thứ ghi vào hệ thống file của container sẽ mất khi `up --build` tạo container mới.
  Chỉ những gì nằm trong **volume** (`pgdata`) mới sống sót → không bao giờ để backend lưu file upload/xuất Excel vào trong container mà không có volume.
- **Image gồm nhiều lớp (layer) có cache.** `backend.Dockerfile` copy `pom.xml` và tải dependency *trước*, copy `src` *sau*.
  Sửa một file Java → Docker dùng lại lớp dependency (vài trăm MB) đã cache, chỉ build lại từ lớp `COPY src`. Đảo thứ tự hai dòng là mỗi lần build tải lại toàn bộ Maven.
- **Log ghi ra stdout**, Docker gom lại → xem bằng `dc logs`. Mặc định Docker **không giới hạn dung lượng log**; chạy vài tháng có thể đầy ổ 30 GB.
  (Khoảng trống đã biết — xử lý cùng module CD bằng `logging: options: max-size`.)

### 5. RAM: vì sao máy 2 GB cần swap

- JVM trong container tự đọc giới hạn cgroup; **không có giới hạn** thì nó nhìn RAM cả máy và mặc định lấy tối đa **25%** làm heap
  (`MaxRAMPercentage`). Trên `B1ms` 2 GB → heap khoảng 512 MB, đủ cho dự án này.
- Nhưng lúc **build** trên server, Maven (cũng là JVM) + `npm run build` chạy cùng lúc với Postgres và backend cũ → dễ vượt 2 GB.
  Linux khi cạn RAM sẽ gọi **OOM killer** giết tiến trình lớn nhất, thường là chính Postgres hoặc backend. Swap là vùng ổ đĩa đóng vai RAM phụ: chậm, nhưng không bị giết.
- **Đây là lý do thật sự để đưa việc build lên CI**: máy chạy GitHub Actions có 7–16 GB RAM (repo private được 2.000 phút miễn phí/tháng), server chỉ việc `pull` image đã build sẵn.

Lệnh xem tài nguyên: `free -h` (RAM/swap), `df -h` (ổ đĩa), `docker stats` (từng container), `docker system df` (image/volume chiếm bao nhiêu).

### 6. Linux tối thiểu cần hiểu, không chỉ gõ

- **`chmod 600 .env.prod`** — chỉ chủ file đọc/ghi được. `ls -l` hiện `-rw-------`. Các user khác trên máy (kể cả tiến trình bị chiếm quyền chạy dưới user khác) không đọc được mật khẩu DB.
- **Nhóm `docker` tương đương root.** Ai chạy được `docker` thì chạy được `docker run -v /:/host ...` và đọc mọi file trên máy.
  Vì vậy key SSH vào `azureuser` phải giữ kỹ như mật khẩu root. Điều này quan trọng ở module CD: key mà GitHub Actions dùng để SSH vào server cũng có quyền lực đó.
- **Ai khởi động lại container khi máy reboot?** `systemd` (trình quản lý dịch vụ của Ubuntu) bật `dockerd` lúc khởi động; `dockerd` thấy container có `restart: unless-stopped` thì chạy lại.
  Container bị `dc stop` thủ công thì sẽ *không* tự bật lại — đúng như tên `unless-stopped`.

### 7. Cấu hình theo môi trường: một image, nhiều nơi chạy

- Nguyên tắc (thường gọi là *12-factor app*): **image giống hệt nhau** ở mọi nơi, khác nhau chỉ ở **biến môi trường**.
  Backend đọc `SPRING_DATASOURCE_URL`, `JWT_SECRET`, `COOKIE_SECURE` lúc **chạy** → cùng một image chạy được local (8088, HTTP) và trên Azure (HTTPS).
- **Ngoại lệ: frontend.** Vite thay biến `VITE_*` vào JS lúc **build**, sau đó chỉ còn file tĩnh. Dự án né được vấn đề này bằng cách gọi API
  bằng đường dẫn tương đối `/api/...` → image frontend không chứa tên miền nào, đem đi đâu cũng chạy.
- Vì sao quan trọng với CI/CD: CI build image **một lần**, test trên đúng image đó, rồi đem **chính image đó** lên server.
  Nếu image phải build lại cho từng môi trường, thì thứ chạy trên server không còn là thứ đã được test.

### 8. Từ thao tác tay hiện nay đến CI/CD

Mỗi bước đang gõ tay qua SSH tương ứng một bước pipeline sẽ tự động hóa:

| Đang làm tay | Rủi ro khi làm tay | Trong pipeline |
|---|---|---|
| Nhớ chạy `mvn verify`, `npm run lint`, `tsc` trước khi push | Quên → code lỗi lên `main` | **CI**: chạy tự động mỗi lần push / mở PR, đỏ thì không merge |
| `--build` trên server 2 GB | OOM, build 5–10 phút, bản build khác bản đã test | Build image trên GitHub, đẩy lên registry (GHCR) |
| `pg_dump` trước khi cập nhật | Quên đúng lần có migration nguy hiểm | Bước bắt buộc trong script deploy, lỗi thì dừng |
| `git pull && up -d` | Gõ nhầm thư mục, quên `--env-file` | Script cố định, chạy qua SSH từ Actions |
| `curl /api/health` | Quên kiểm tra | Bước cuối pipeline; không healthy → báo đỏ |

- **CI (Continuous Integration)** — mỗi thay đổi đều được tự động build + test. Không đụng tới server.
- **Continuous Delivery** — luôn có sẵn bản sẵn sàng deploy; **người bấm nút** để lên server. Dự án chọn mức này (deployment.md).
- **Continuous Deployment** — merge vào `main` là tự lên server, không ai bấm. Chỉ an toàn khi test đủ dày và có rollback tự động.

### Câu hỏi ôn tập

1. Khách báo "app không vào được". Bạn hỏi lại đúng một câu để phân biệt lỗi NSG/VM tắt với lỗi container `web` chết. Câu đó là gì?
2. Vì sao healthcheck của backend gọi `localhost:8080`, còn Nginx phải gọi `backend:8080`? Đổi ngược lại thì chuyện gì xảy ra?
3. Ngay sau `dc up -d --build` mở trang thấy 502 khoảng nửa phút rồi tự hết. Tầng nào đang làm gì trong nửa phút đó?
4. Backend xuất file Excel lưu vào `/app/exports` trong container. Sau lần deploy tiếp theo file đi đâu? Sửa thế nào?
5. Vì sao thêm user vào nhóm `docker` gần như là cho user đó quyền root?
6. Vì sao build image trên CI rồi đem lên server an toàn hơn `--build` trên server, ngoài chuyện RAM?
