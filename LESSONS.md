# LESSONS — Bài học tích lũy

Ghi dồn sau mỗi module, ôn tập ở cuối dự án. Mỗi mục: **khái niệm → ví dụ trong dự án → vì sao quan trọng**.
`NOTES.md` vẫn là sổ tay riêng của bạn; file này do AI ghi để không bỏ sót kiến thức.

---

## Module: Công nợ nhà cung cấp (2026-10-25, migration V16)

- **Dữ liệu cũ khi thêm nghiệp vụ mới: cờ hay số giả?** Phiếu nhập đã hoàn tất trước module này phải "coi như đã trả đủ". Cách dễ nghĩ ra là gán `paid_amount = total_amount`. Nhưng `paid_amount` là **snapshot** của nhật ký `supplier_payment`: gán như vậy thì tổng không khớp với nhật ký (đã trả 10 triệu mà không có dòng trả nào), và phiếu cũ bị khóa đảo theo BR-SPAY-04. Cờ `tracks_debt = FALSE` nói thẳng điều đã xảy ra: "phiếu này không theo dõi công nợ". Mọi chỗ tính nợ (`getOutstandingAmount`, câu GROUP BY, bộ lọc `unpaidOnly`) đều phải tôn trọng cờ này, nên test `legacyReceiptShouldBeTreatedAsPaid` kiểm cả bốn chỗ.
- **Hibernate giữ bản cũ trong bộ nhớ.** Test giả lập phiếu cũ bằng `jdbcTemplate.update("... tracks_debt = FALSE")`. Phiếu đó vẫn nằm trong persistence context với `tracksDebt = true`, nên request sau đọc lại **object trong bộ nhớ**, không đọc DB. Phải gọi `flush()` (đẩy thay đổi của Hibernate xuống DB) trước câu UPDATE, rồi `clear()` (bỏ bản cũ) sau câu UPDATE.
- **Dùng lại mẫu, không dùng lại bảng.** Trả tiền NCC giống hệt thu tiền khách về cấu trúc (PAYMENT/REVERSAL, hai dòng khi trả kết hợp, trigger bất biến, khóa phiếu `FOR UPDATE` rồi mới kiểm tra). Code dùng chung kiểu `PaymentMethod` và DTO request, nhưng dữ liệu nằm ở **bảng riêng** `supplier_payment`. Lý do: chiều tiền và quyền khác nhau, và module bán hàng đã duyệt không bị đụng tới. Ở frontend, phần "Thanh toán" và hộp thoại trả/thu tiền được tách thành component dùng chung, chỉ khác chữ ("Thu" / "Trả").
- **Quy tắc chéo giữa hai nghiệp vụ.** Đảo phiếu nhập (nghiệp vụ kho) giờ phụ thuộc trả tiền (nghiệp vụ tiền): đã trả thì không đảo được. Nếu không chặn, đảo phiếu xong thì món nợ biến mất mà tiền đã chi vẫn nằm đó, không gắn với chứng từ nào. Đây cũng là lý do module **dòng tiền** phải làm sau công nợ: nó gom các khoản thu/chi từ `sale_payment` và `supplier_payment`.

---

## Thay đổi: NCC bỏ "người liên hệ", thêm "mã số thuế" (2026-10-24, migration V15)

- **Đổi nghĩa một cột = thêm cột mới + xóa cột cũ, không `RENAME`.** `ALTER TABLE ... RENAME COLUMN contact_name TO tax_code` chỉ đổi tên, dữ liệu giữ nguyên. Khi đó "Chị Lan" sẽ thành mã số thuế: dữ liệu sai, và còn vi phạm CHECK mới nên migration fail ngay. RENAME chỉ dùng khi **ý nghĩa giữ nguyên**, chỉ tên cột đổi.
- **Migration xóa dữ liệu không quay lại được.** Flyway chạy V15 ngay lúc backend khởi động trên server, nên `DROP COLUMN contact_name` xóa luôn tên người liên hệ trên production. Muốn quay lại thì chỉ còn cách khôi phục từ bản backup. Vì vậy phải **backup DB trước khi deploy** (docs/maintenance/backup-restore.md). Và không "sửa" lại V15 sau khi đã chạy: Flyway lưu checksum, sửa file cũ thì validate thất bại. Muốn đổi gì nữa thì viết V16.
- **Unique index một phần (partial index).** `CREATE UNIQUE INDEX ... ON supplier (tax_code) WHERE tax_code IS NOT NULL`: MST có nhập thì không được trùng, còn nhiều NCC chưa có MST vẫn hợp lệ. (Trong PostgreSQL, UNIQUE thường cũng coi các NULL là khác nhau; viết `WHERE` để ý đồ rõ ràng và index nhỏ hơn.) Đây là phiên bản "tùy chọn nhưng duy nhất" của `uk_customer_phone`.
- **Exception mang theo field.** `DuplicateResourceException` có thêm `field` (tùy chọn). API 409 vẫn trả như cũ, nhưng khi nhập Excel, lỗi "MST đã thuộc về NCC X" được gắn vào đúng cột "Mã số thuế" thay vì cột "Tên". Tương tự, `ImportDefinition.duplicateKeys()` trả về một map field → khóa, để mỗi file so trùng **từng** field riêng (tên và MST).
- **Đổi cấu trúc mẫu Excel thì tăng phiên bản** (`SUPPLIER_v1` → `v2`). Ai đang giữ file mẫu cũ sẽ nhận lỗi "không phải mẫu nhập", thay vì cột MST bị đọc nhầm thành "người liên hệ".

---

## Module: Nhập hàng loạt từ Excel (2026-10-24, ADR-0004)

### Khái niệm

- **File `.xlsx` là một file zip chứa các file XML.** Apache POI giải nén và đọc các XML đó thành `Workbook → Sheet → Row → Cell`. Vì thế đổi đuôi `.csv` thành `.xlsx` thì POI vẫn ném lỗi. Kẻ xấu có thể gửi một file nhỏ nhưng giải nén ra hàng GB ("zip bomb"); POI mặc định chặn file có tỉ lệ nén bất thường.
- **Excel lưu mọi con số dưới dạng `double`.** Gõ `25000.1` thì trong file là số nhị phân xấp xỉ. `new BigDecimal(25000.1)` ra `25000.100000000002182787…`. `BigDecimal.valueOf(double)` đúng với số gõ tay, nhưng ô công thức `=0.1+0.2` lại ra `0.30000000000000004`, trong khi Excel hiển thị `0.3` (Excel chỉ dùng 15 chữ số có nghĩa). Ở đây dùng `NumberToTextConverter.toText()` để lấy đúng chuỗi Excel hiển thị, rồi mới `new BigDecimal(String)`. Đây là quy tắc cứng số 3 (tiền dùng BigDecimal) áp dụng ở biên vào của dữ liệu.
- **Ô định dạng Text và số 0 đầu.** Gõ `0901234567` vào ô kiểu General thì Excel hiểu là số `901234567` và mất số 0. File mẫu đặt sẵn định dạng `@` (Text) cho cột SĐT bằng `setDefaultColumnStyle`. Đây là lý do **file mẫu do hệ thống sinh** tốt hơn để người dùng tự tạo file.
- **Dropdown (Data Validation) lấy danh sách từ sheet ẩn.** Liệt kê thẳng `"Bao,Kg"` chỉ được tối đa 255 ký tự, vài chục tên danh mục là vượt. Vì vậy danh sách được ghi vào sheet `_meta` (VERY_HIDDEN, không hiện trong menu Unhide) và dropdown trỏ tới vùng `'_meta'!$C$1:$C$n`. Dropdown chỉ là **gợi ý**: người dùng dán đè vẫn lọt, nên backend vẫn kiểm từng giá trị.
- **Dùng lại quy tắc thay vì viết lại.** `create()` của mỗi service được tách thành `prepareNew(request)` (kiểm tra + dựng entity chưa lưu) và `save()`. Form nhập tay và import cùng gọi `prepareNew`, và mỗi dòng Excel được ép thành đúng `CreateProductRequest`, nên đi qua cùng `@NotBlank`, `@Digits`. Ví dụ "đơn vị Kg phải 1 kg" (BR-PRD-06) chỉ được viết **một lần**. Nếu viết riêng cho import, sớm muộn hai bên sẽ lệch nhau.
- **"Tất cả hoặc không gì" = kiểm hết trước, ghi sau, trong một transaction.** `commit()` gom lỗi của cả 1.000 dòng trước, rồi mới gọi `saveAll`. Có lỗi thì ném exception trước khi có câu INSERT nào. Nếu UNIQUE ở DB chặn lúc ghi (có người nhập trùng cùng lúc) thì `@Transactional` rollback cả các dòng đã INSERT.
- **Exception nghiệp vụ trong transaction lồng nhau → "rollback-only".** `preview()` cố ý **không** có `@Transactional`. Lý do: khi `productService.prepareNew()` (có `@Transactional` ở class) ném `DuplicateResourceException` bên trong transaction của người gọi, Spring đánh dấu cả transaction ngoài là rollback-only. Hàm ngoài bắt exception, gom lỗi, rồi trả kết quả bình thường. Đến lúc commit, Spring ném `UnexpectedRollbackException` và danh sách lỗi không bao giờ tới người dùng. Ở `commit()` thì không sao, vì có lỗi là ta ném exception (rollback) ngay.
- **Lỗi phải chỉ được cho người dùng chỗ cần sửa.** Lỗi trả về theo số dòng **Excel** (tiêu đề là dòng 1, POI đếm từ 0 nên phải +1) kèm tên cột. Mỗi cột giữ lỗi đầu tiên: ô `"25kg"` báo "Phải là số", không báo thêm "không được để trống" (lỗi phụ sinh ra vì field bị gán null).

### Lỗi đã gặp

- Chạy cả bộ test: `UserApiTest.shouldKeepAtLeastOneActiveAdmin` thất bại (mong 409, nhận 200). Chạy lại trên code `main` (stash thay đổi) cũng thất bại → **lỗi có từ trước**, không do module này. Nghi DB test `grain_warehouse_test` còn sẵn một Quản lý hệ thống đã commit (ví dụ do chạy app bằng DB test), nên "người cuối cùng" thực ra không phải người cuối. Chưa sửa, chờ xác nhận.
- Thử đột biến: đảo thứ tự trong `commit()` (ghi trước, kiểm lỗi sau) → `shouldImportNothingWhenAnyRowFails` fail (mong 0 SP mới, thực tế 2). Như vậy test bắt đúng lỗi "nhập dở dang".

### Câu hỏi ôn tập (để cuối dự án)

1. Đọc ô số Excel thành BigDecimal: `new BigDecimal(double)`, `BigDecimal.valueOf(double)` và `NumberToTextConverter` khác nhau thế nào? Cho ví dụ ô công thức.
2. `preview()` mà có `@Transactional` thì người dùng thấy gì khi file có một tên trùng? Vì sao?
3. Vì sao bước "Nhập" phải kiểm tra lại từ đầu dù vừa preview không có lỗi?
4. Dropdown trong Excel đã giới hạn giá trị, tại sao backend vẫn phải kiểm?

---

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

---

## Module: Đăng nhập & phân quyền (2026-10-20, V14, ADR-0003)

### Spring Security hoạt động thế nào — đi theo một request

Ví dụ: chị Lan (Nhân viên bán hàng) bấm "Hoàn tất" phiếu PX000007 → `POST /api/sales-orders/7/complete`.

1. **Chuỗi filter (`SecurityFilterChain`)** — mọi request đi qua một dãy filter *trước* DispatcherServlet/controller. Spring Boot tự đăng ký chuỗi này; `SecurityConfig.securityFilterChain` quyết định có những filter nào.
2. **`CsrfFilter`** — request ghi phải có header `X-XSRF-TOKEN` bằng cookie `XSRF-TOKEN`. Thiếu → `AccessDeniedHandler` → 403. *Lý do tồn tại:* cookie đăng nhập được trình duyệt **tự gửi**, kể cả khi request do trang `kho-gia.vn` tạo ngầm. Trang đó không đọc được cookie của tên miền mình nên không đặt được header.
3. **`BearerTokenAuthenticationFilter`** — `CookieBearerTokenResolver` lấy JWT từ cookie `GW_AT` → `JwtAuthenticationProvider` → **`JwtDecoder`** kiểm chữ ký HS256, `exp`, `iss` (không truy vấn DB) → `JwtAuthenticationConverter` đổi claim `roles: ["SALES_STAFF"]` thành authority `ROLE_SALES_STAFF` → lưu vào **`SecurityContext`** (người đang gọi là ai, có quyền gì). Token hỏng/hết hạn → `AuthenticationEntryPoint` → 401.
4. **`AuthorizationFilter`** — so URL + method với `accessRules`: `POST /api/sales-orders/**` → `hasAnyRole(SALES, ADMIN)` → qua. Anh Hùng (Quản lý kho) gọi cùng URL → 403, **`SalesOrderService` không bao giờ chạy**.
5. Controller lấy người dùng bằng `@AuthenticationPrincipal Jwt jwt` (`jwt.getSubject()` = id).

**Đăng nhập** không có filter riêng: `AuthController` → `AuthenticationManager` → `DaoAuthenticationProvider` → `UserDetailsService` (đọc `app_user`) → `PasswordEncoder.matches` (BCrypt) → thành công thì `JwtEncoder` ký access token, `TokenService` sinh refresh token, rồi đặt cookie.

### Khái niệm

- **Access token vs refresh token** — access JWT sống 15 phút, xác minh chỉ bằng chữ ký nên nhanh, nhưng **không thu hồi được**. Refresh token sống tới 8 giờ không thao tác, mỗi lần dùng phải tra DB nên **thu hồi được**. Kết hợp: thiệt hại khi lộ access token giới hạn ở 15 phút; khóa tài khoản có hiệu lực trong ≤ 15 phút.
- **JWT chỉ được ký, không được mã hóa** — dán token vào jwt.io là đọc được `username`, `roles`. Chữ ký chỉ bảo đảm *không sửa được*. Không đặt dữ liệu nhạy cảm vào payload.
- **Cookie HttpOnly + Secure + SameSite=Strict** — HttpOnly: `document.cookie` không đọc được, nên XSS không lấy được token. Secure: chỉ gửi qua HTTPS (trình duyệt cho phép ngoại lệ `http://localhost`). SameSite=Strict: không gửi khi request đến từ trang khác. `Path=/api/auth` cho `GW_RT`: các API khác không bao giờ nhận refresh token.
- **Rotation + phát hiện dùng lại** — mỗi lần gia hạn, refresh token cũ bị đánh dấu "đã thay". Ví dụ kẻ gian chép được RT1: người thật gia hạn ra RT2; 1 giờ sau kẻ gian dùng RT1 → backend biết RT1 đã bị thay từ lâu → **thu hồi cả family**, cả RT2 của người thật. Hai bên đều phải đăng nhập lại, và kẻ gian mất quyền.
- **Thời gian ân hạn 30 giây (REFRESH_RACE)** — hai tab cùng gia hạn với RT1 trong vài mili giây. Không có ân hạn thì tab chậm hơn bị coi là kẻ trộm, người dùng bị đá ra. Trong 30 giây trả 409, không phát token mới, không thu hồi.
- **Băm khác nhau cho mật khẩu và token** — mật khẩu do người đặt, dễ đoán → BCrypt (chậm cố ý). Refresh token ngẫu nhiên 256 bit, không đoán được → SHA-256 đủ. DB bị lộ thì cả hai đều không dùng lại được.
- **Open redirect** — `/login?redirect=https://kho-gia.vn`: đăng nhập thật xong bị đưa sang trang giả. `safeRedirect` chỉ nhận đường dẫn bắt đầu bằng `/` (không phải `//`).
- **Ẩn nút ở frontend ≠ phân quyền** — `permissions.ts` chỉ để giao diện gọn. Gõ thẳng URL hoặc gọi API bằng curl vẫn bị backend chặn: `AuthorizationMatrixTest` có 99 ca (33 endpoint × 3 vai trò) + 33 ca chưa đăng nhập.

### Lỗi đã gặp

- **DSL `oauth2ResourceServer()` tắt CSRF ngầm** — DSL này giả định token nằm ở header `Authorization` (trình duyệt không tự gửi), nên tự bỏ kiểm CSRF cho mọi request có token. Ta để token trong cookie, nên điều đó thành lỗ hổng: `POST /api/customers` kèm cookie mà không có CSRF vẫn chạy tới controller (test nhận 400 thay vì 403). Sửa: tự lắp `BearerTokenAuthenticationFilter` bằng `http.addFilter(...)`, vẫn dùng `JwtDecoder` của thư viện. **Bài học:** mặc định của framework đúng *với giả định của nó*; đổi giả định (header → cookie) thì phải kiểm lại các mặc định.
- **UPDATE hàng loạt không cập nhật bộ nhớ của Hibernate** — `revokeFamily` (`@Modifying` JPQL) ghi thẳng DB. Entity `RefreshToken` đang nằm trong persistence context vẫn có `revokedAt = null`, nên lần đọc sau trong cùng transaction thấy bản cũ. Sửa: `clearAutomatically = true` (kèm `flushAutomatically` để ghi thay đổi đang chờ trước).
- **Thu hồi phải được commit dù trả 401** — phát hiện dùng lại token mà `throw` exception trong `@Transactional` thì rollback mất việc thu hồi. Vì vậy `refresh()` trả về kiểu `RefreshResult` (sealed interface: `Renewed` / `Race` / `Rejected`) và controller đổi thành HTTP status.
- **Postprocessor `csrf()` của spring-security-test sửa filter dùng chung** — nó thay `CsrfTokenRepository` của `CsrfFilter` (một bean cho cả Spring context) bằng bản lưu trong session. Từ test thứ hai trở đi, cookie `XSRF-TOKEN` thật không còn được tạo, nên test chạy riêng thì qua, chạy cả class thì lỗi. Luồng CSRF thật được tách sang `CsrfDoubleSubmitTest` với context riêng.
- **Thứ tự kiểm "bị khóa" của `DaoAuthenticationProvider`** — mặc định kiểm *trước* mật khẩu, nên đoán bừa mật khẩu vẫn biết được "tài khoản tồn tại và bị khóa". Đổi: `preAuthenticationChecks` rỗng, `postAuthenticationChecks = AccountStatusUserDetailsChecker`.
- **Access token vừa hết hạn làm hỏng chính lời gọi gia hạn** — cookie `GW_AT` có `Path=/api` nên vẫn được gửi tới `/api/auth/refresh`. Filter thấy token hết hạn → 401 ngay, đúng lúc cần gia hạn. Resolver bỏ qua các endpoint công khai.
- **`CHAR(64)` vs `String`** — Hibernate mặc định coi `String` là `VARCHAR` → `ddl-auto: validate` báo sai kiểu. Không sửa migration đã chạy (lệch checksum Flyway); thêm `@JdbcTypeCode(SqlTypes.CHAR)` ở entity.
- **VS Code ghi `.class` lỗi vào `target/`** (lại gặp, xem module Nhà cung cấp) → luôn `mvn clean` trước khi chạy test.
- **Đã thử bỏ khóa dòng** (`@Lock` trên `findForUpdateByTokenHash`): `RefreshConcurrencyTest` cho 5 luồng cùng gia hạn và cả 5 đều nhận token mới, tức một token cũ đẻ ra 5 phiên. Có khóa: đúng 1 `Renewed` + 4 `Race`.

### Câu hỏi ôn tập (để cuối dự án)

1. Vì sao lưu token trong `localStorage` thì không cần chống CSRF, còn lưu trong cookie thì bắt buộc? Đổi lại, `localStorage` mất gì?
2. Quản lý hệ thống khóa tài khoản lúc 9:00. Đến lúc nào người đó mới thật sự mất quyền? Muốn tức thì thì phải đánh đổi gì?
3. Kẻ gian chép được refresh token của chị Lan lúc 9:00. Mô tả chuyện xảy ra nếu chị Lan gia hạn lúc 9:05 và kẻ gian dùng token lúc 10:00.
4. Vì sao `AuthorizationMatrixTest` kiểm cả "sai vai trò → 403" chứ không chỉ "đúng vai trò → 200"?

---

## Module: Lịch sử biến động tồn kho (2026-10-20, không đổi schema)

- **Sổ cái (ledger) trả lời câu hỏi "vì sao"** — `inventory.quantity = 27` chỉ cho biết *bao nhiêu*; `stock_transaction` cho biết *vì sao*. Lọc Cám heo tại Kho HN, đọc từ dưới lên: nhập PN 20 (0→20), bán PX 5 (20→15), nhập PN 12 (15→27), bán PX 3 (27→24), đảo phiếu bán đó (24→27). "Tồn trước" mỗi dòng = "Tồn sau" của dòng ngay dưới; chỗ nào không khớp là dấu hiệu tồn bị sửa ngoài luồng chứng từ. `StockTransactionApiTest` kiểm đúng điều này trên luồng phiếu thật.
- **Tồn trước/sau là của MỘT kho** — khi chỉ lọc SP mà không lọc kho, các dòng của Kho HN và Kho HP xen kẽ nhau nên chuỗi trước/sau không nối liền. Muốn dò tồn phải lọc cả SP lẫn kho.
- **Sắp theo `id`, không theo `created_at`** — `@CreationTimestamp`/`now()` cho mọi dòng trong cùng một transaction cùng một thời điểm: hoàn tất phiếu 2 dòng → 2 giao dịch trùng `created_at`, sắp theo thời gian thì thứ tự không xác định. `id` (IDENTITY) luôn tăng theo đúng thứ tự ghi.
- **Ngày ghi sổ ≠ ngày chứng từ** — phiếu nhập ngày chứng từ 15/10 nhưng hoàn tất 20/10 thì tồn tăng vào 20/10. Bộ lọc ngày của trang lịch sử dùng `createdAt` (thời điểm tồn thực sự đổi), cột "Chứng từ" vẫn hiện ngày chứng từ để đối chiếu.
- **`@EntityGraph` với chuỗi quan hệ *-to-one** — mỗi dòng cần SP, kho, dòng chứng từ → chứng từ → NCC/khách. Toàn là quan hệ *-to-one nên JOIN FETCH không nhân bản dòng, phân trang vẫn làm trong SQL (`LIMIT`). Nếu fetch một collection (`items`) cùng phân trang, Hibernate phải phân trang trong bộ nhớ (cảnh báo HHH90003004).
- **Lấy id từ proxy LAZY không tốn truy vấn** — `getReversalOf().getId()`: Hibernate đã biết khóa ngoại nên trả id ngay, không SELECT bản ghi gốc.
- **Mở rộng helper chung thay vì viết riêng** — `keywordContains` trước chỉ nhận thuộc tính trực tiếp (`root.get("code")`); tìm theo mã phiếu cần `documentItem.document.code`. Sửa helper dùng chung `path()` (đã có cho `equal`) kèm test, theo quy ước search-pattern.
- **Index hiện có** — `idx_stock_transaction_product_warehouse (product_id, warehouse_id, created_at)` phục vụ lọc theo SP (+ kho). Lọc chỉ theo ngày hoặc chỉ theo kho chưa có index riêng; chưa thêm vì dữ liệu còn nhỏ — kiểm bằng `EXPLAIN ANALYZE` ở mục tối ưu Tuần 3.

### Câu hỏi ôn tập (để cuối dự án)

1. Vì sao chỉ lọc theo SP (không lọc kho) thì cột "Tồn trước" không nối liền với "Tồn sau" của dòng bên dưới?
2. Hai giao dịch của cùng một phiếu có `created_at` giống hệt nhau — vì sao? Hệ quả với việc sắp xếp?
3. Nếu ai đó chạy `UPDATE inventory SET quantity = 30` trực tiếp trên DB, trang lịch sử sẽ lộ ra điều đó ở đâu?

---

## Module: Bán hàng, bảng giá theo khách, công nợ — backend (2026-10-20, V13)

- **Snapshot + nhật ký, lần thứ hai** — `stock_document.paid_amount` đối với `sale_payment` giống như `inventory` đối với `stock_transaction`. Nhật ký (`sale_payment`) bất biến và đầy đủ, còn snapshot (`paid_amount`) cho phép đọc nhanh và đặt được ràng buộc ở DB: `CHECK (paid_amount <= total_amount)`. Ví dụ phiếu PX000007 tổng 10 triệu, thu 4 triệu tiền mặt rồi 3 triệu chuyển khoản: nhật ký có 2 dòng, `paid_amount = 7.000.000`, còn nợ 3 triệu. Nếu chỉ tính bằng `SUM` thì DB không có cột nào để đặt CHECK, và việc "không thu quá" chỉ còn trông vào code Java.
  _Đánh đổi:_ hai nơi phải luôn khớp nhau, nên **mọi** đường thu/hoàn tiền đều phải cập nhật cả hai trong cùng transaction.
- **Thu tiền cũng phải khóa phiếu** — thu tiền không làm thay đổi tồn kho, nhưng vẫn là "đọc số còn nợ → so sánh → ghi". Nếu hai người cùng thu 400.000 cho phiếu còn nợ 1 triệu mà không khóa, cả hai đều đọc thấy "còn 1 triệu" rồi cùng ghi. `SalesOrderConcurrencyTest` cho 5 luồng cùng thu: kết quả đúng 2 thành công, 3 luồng còn lại nhận thông báo "vượt số còn nợ". Điểm nguy hiểm: Hibernate ghi `paid_amount = 400000` (giá trị tuyệt đối, không phải `paid_amount + 400000`). Không khóa thì xảy ra **lost update**: nhật ký có 2 dòng × 400k, còn snapshot chỉ ghi 400k. CHECK `paid <= total` không phát hiện được, vì 400k vẫn nhỏ hơn 1 triệu. **Đã thử bỏ khóa:** cả 5 lần thu đều thành công, tức 2 triệu cho phiếu chỉ nợ 1 triệu; hoàn tất đồng thời cũng thành công 5/5, tồn bị trừ 5 lần. Test fail đúng như dự đoán.
- **Trả tiền kết hợp = nhiều dòng, không phải nhiều cột** — khách trả 300k tiền mặt + 500k chuyển khoản thì ghi 2 dòng, mỗi dòng có `method` riêng. Không thêm cột `cash_amount`, `transfer_amount`: cách đó khiến việc "hoàn khoản chuyển khoản ghi nhầm" và "tổng thu theo hình thức trong tháng" phức tạp hơn. Sau này có thêm ví điện tử chỉ cần nới CHECK, không phải thêm cột.
- **Bảng giá chỉ để gợi ý, giá thật nằm trên dòng phiếu** — `customer_product_price` không lưu lịch sử. Dòng phiếu đã chụp `unit_price` nên đổi bảng giá từ 310k lên 350k không làm phiếu cũ đổi theo (test `orderKeepsItsOwnPrice`). Cùng nguyên tắc với bản chụp đơn giá ở bảng lương.
- **Upsert một câu SQL** — `INSERT … ON CONFLICT (customer_id, product_id) DO UPDATE`. Nếu "tìm → chưa có thì INSERT" bằng hai câu, hai người cùng đặt giá lần đầu sẽ có một người dính lỗi trùng UNIQUE.
- **CHECK theo loại phiếu** — `ck_stock_document_customer` ràng buộc: `INBOUND` thì không có khách, `OUTBOUND` thì bắt buộc có khách và không có NCC. Một bảng dùng chung cho nhiều loại chứng từ vẫn giữ được ràng buộc riêng của từng loại, nhờ mẫu `type <> 'X' OR (điều kiện của X)`.

### Lỗi đã gặp

- **Overload varargs nuốt nhầm lời gọi** — trong test có `createDraft(String... lines)` và `createDraft(String body)`. Gọi `createDraft(line(...))` với **một** dòng thì Java chọn bản `String body`, vì bản không varargs được ưu tiên. Kết quả là 17 test nhận 400, vì JSON gửi lên chỉ là một dòng hàng. Cách sửa: đổi tên thành `createDraftFrom(String body)`. Bài học: không overload varargs với một bản có cùng kiểu tham số.
- **`@Modifying` không tự xóa persistence context** — sau câu upsert viết tay, đọc lại bảng giá trong cùng transaction vẫn thấy giá cũ (310.000 thay vì 305.000,50), vì Hibernate trả entity đang nằm trong bộ nhớ. Cách sửa: `@Modifying(flushAutomatically = true, clearAutomatically = true)`. Lỗi này cùng gốc với lỗi "test đọc dữ liệu cũ" ở module nhân viên.
- **PostgreSQL hủy cả transaction sau lỗi đầu tiên** — một test kiểm cả "chặn UPDATE" lẫn "chặn DELETE": câu thứ hai báo `current transaction is aborted` (SQL state 25P02) thay vì lỗi trigger. Cách sửa: mỗi test chỉ kiểm một vi phạm. (Muốn kiểm nhiều thì phải dùng SAVEPOINT.)

### Frontend (giao diện bán hàng, công nợ, bảng giá)

- **Một khung cho cả vòng đời phiếu** — `SalesOrderDrawer` dùng lại cách làm của phiếu nhập: Nháp hiện form; Hoàn tất hiện phần xem + Thu tiền / Hoàn khoản thu / Đảo phiếu. Mọi thao tác backend trả về toàn bộ phiếu, nên chỉ cần `setOrder(updated)` là màn hình khớp DB, không phải tự tính "còn nợ" ở trình duyệt.
- **Giá gợi ý ≠ giá ép buộc** — chọn SP thì giá riêng của khách được điền sẵn, và dưới ô có dòng "Giá riêng". Người bán sửa giá thì dòng đó chuyển thành cảnh báo "Giá riêng 310.000 đ", để thấy ngay mình đang bán lệch giá.
- **Cảnh báo ở trình duyệt, chặn ở backend** — cột "Tồn kho" và chữ "Vượt tồn" chỉ để báo sớm. Người khác có thể bán cùng lúc, nên chỉ `UPDATE … WHERE quantity >= :q` lúc hoàn tất mới là chốt chặn thật. Hộp thu tiền cũng kiểm "không vượt nợ" trên trình duyệt, nhưng backend kiểm lại sau khi khóa phiếu.
- **Chạy thử thật bằng Playwright** — các test đơn vị đều pass, nhưng khi chạy giao diện mới thấy phiếu **Đã đảo** vẫn hiện "Còn nợ 620.000 đ" màu đỏ. Nguyên nhân: backend tính `total − paid` mà không xét trạng thái. Đã sửa `getOutstandingAmount()` để trả 0 khi phiếu không ở trạng thái Hoàn tất, và thêm assertion vào test. Bài học: test chỉ kiểm được điều mình nghĩ ra để kiểm; nhìn vào màn hình thật mới lộ ra chỗ con số đúng công thức nhưng sai nghĩa nghiệp vụ.

### Câu hỏi ôn tập (để cuối dự án)

1. Vì sao phiếu đã thu tiền thì không cho đảo? Nếu cho đảo, `paid_amount` và CHECK `paid <= total` sẽ ra sao?
2. Nếu bỏ khóa `FOR UPDATE` ở `recordPayment` thì vì sao CHECK `paid <= total` không cứu được? Viết lại câu cập nhật thế nào để DB tự chặn mà không cần khóa? (Gợi ý: `UPDATE … SET paid_amount = paid_amount + :x WHERE … AND paid_amount + :x <= total_amount`.)
3. Vì sao bảng giá không cần lưu lịch sử giá mà vẫn tra được "tháng trước bán cho khách X giá bao nhiêu"?

---

## Module: Nhân viên, chấm công, bảng lương (2026-10-20)

- **Bản chụp (snapshot) thay vì tham chiếu** — bảng lương đã chốt lưu luôn *đơn giá lúc chốt* vào `payroll_item`, thay vì JOIN sang `employee.daily_wage` mỗi khi xem. Ví dụ: tháng 9 anh Bốc hưởng 52.500đ/tấn, tháng 10 được tăng lên 55.000đ. Nếu chỉ tham chiếu, lương tháng 9 đã trả sẽ tự đổi thành tiền theo giá mới. Đây cũng là lý do phiếu nhập lưu `unit_price` trên từng dòng. Nguyên tắc chung: **con số đã dùng để trả tiền thì phải được chụp lại tại thời điểm trả**.
- **Advisory lock của PostgreSQL** — `SELECT ... FOR UPDATE` chỉ khóa được *dòng đã có*. Chốt lương lại cần chặn cả việc *thêm* ô chấm công mới vào tháng đó, mà ô chưa có thì không có dòng nào để khóa. `pg_advisory_xact_lock(4201, 202610)` khóa một "con số" do ta tự quy ước (ở đây là loại khóa + yyyymm), và tự nhả khi transaction kết thúc. Khóa chỉ có tác dụng khi **mọi đường ghi** (chấm công, xóa ô, chốt, hủy chốt) đều lấy nó: giống bài học FOR UPDATE ở phiếu nhập, chỉ cần một đường quên khóa là hỏng.
- **Khóa rồi mới kiểm tra** — `isPayrollFinalized()` chỉ cho kết quả đúng khi gọi *sau* `lock()`. Nếu kiểm tra trước, chốt lương có thể commit ngay giữa lúc kiểm tra xong và lúc ghi. Đã thử bỏ khóa trong `finalizeMonth`: `PayrollConcurrencyTest` fail ở cả 2 lần chạy, bản chụp ghi 2 tấn trong khi DB có 6 tấn, tức là 4 ô chấm công đã lọt vào sau khi chụp.
- **Index một phần (partial unique index)** — "mỗi tháng chỉ một bảng lương còn hiệu lực, nhưng được giữ nhiều bản đã hủy" không viết được bằng `UNIQUE (period)`. Cách làm là `CREATE UNIQUE INDEX ... ON payroll (period) WHERE status = 'FINALIZED'`: chỉ các dòng thỏa `WHERE` mới tham gia ràng buộc.
- **Tránh phụ thuộc vòng giữa module** — payroll đọc chấm công để tính lương (payroll → attendance), còn chấm công cần biết "tháng đã chốt chưa". Nếu attendance gọi `PayrollService` thì thành vòng. Ở đây `AttendanceMonthLock` hỏi thẳng bảng `payroll` bằng SQL: hai module phụ thuộc chung vào *bảng*, còn *code* chỉ phụ thuộc một chiều.
- **Làm tròn từng phần, rồi mới cộng** (BR-PAY-01): 41,235 tấn × 52.500 = 2.164.837,5, làm tròn thành 2.164.838; cộng thêm tiền công là ra tổng. Nếu cộng trước rồi mới làm tròn thì tổng có thể lệch 1đ so với các dòng in trên phiếu lương. Khi đó người nhận cộng tay sẽ không khớp với con số cuối.

### Lỗi đã gặp

- **Test đọc dữ liệu cũ trong bộ nhớ đệm Hibernate**: test dùng `UPDATE employee SET active = false` bằng JdbcTemplate, rồi gọi API bảng công, nhưng kết quả vẫn ra `active: true`. Nguyên nhân: test chạy trong một transaction duy nhất, và Hibernate đã nạp nhân viên đó vào persistence context từ lần gọi trước. Câu UPDATE viết tay đi thẳng xuống DB nên Hibernate không biết. Ở môi trường thật, mỗi request có persistence context riêng nên không gặp lỗi này. Cách sửa: thao tác qua API (cho nghỉ việc bằng `DELETE /api/employees/{id}`), đúng như người dùng làm.

- **Form báo "chưa nhập đơn giá" dù đã gõ đủ (bạn phát hiện khi chạy thử)**: `Form.Item` gắn `value`/`onChange` vào component con *trực tiếp* của nó. Ô tiền được bọc trong `PriceInput` tự viết, nhưng `PriceInput` không chuyển tiếp hai prop này xuống `InputNumber`. Vì vậy ô vẫn hiện số người dùng gõ, còn form thì luôn thấy `undefined`. Cách sửa: `PriceInput(props)` rồi `<InputNumber {...props} />`. Quy tắc chung: component tự viết đặt trong `Form.Item` phải nhận và chuyển tiếp `value`/`onChange`. Test hiện có không bắt được lỗi này vì chỉ test hàm API, không test component. Muốn bắt được thì cần thư viện test giao diện (React Testing Library), mà thư viện này chưa có trong dự án.
- **Thanh cuộn ngang trong modal**: `Row gutter` tạo khoảng cách bằng margin âm hai bên. Thân modal lại đặt `overflow: auto`, nên phần margin thừa ra sinh thanh cuộn ngang. Trong modal nên dùng `Flex gap` thay cho `Row gutter`.

### Câu hỏi ôn tập (để cuối dự án)

1. Vì sao bảng lương đã chốt phải lưu đơn giá, chứ không JOIN sang `employee`?
2. `SELECT … FOR UPDATE` và `pg_advisory_xact_lock` khác nhau thế nào? Vì sao chốt lương dùng cái sau?
3. Nếu `AttendanceService.record` kiểm tra "đã chốt chưa" *trước* khi lấy khóa tháng thì kịch bản lỗi diễn ra thế nào?
4. Viết ràng buộc "mỗi tháng chỉ một bảng lương FINALIZED" bằng SQL.

---

## Sửa lỗi: mã tự sinh bị cắt cụt (2026-10-20, migration V10)

- **Triệu chứng**: đang làm module Nhân viên thì chạy test, 20 test của các module *cũ* (kho, NCC, tồn kho) đột nhiên báo `duplicate key uk_warehouse_code: (code)=(KHO105)`. Đây không phải lỗi của code vừa viết.
- **Nguyên nhân**: `lpad(text, n, '0')` **không chỉ đệm mà còn cắt** chuỗi dài hơn n ký tự. `'KHO' || lpad('1050', 3, '0')` cho ra `KHO105`, và mã 1051…1059 cũng cho ra `KHO105` → trùng. Lỗi nằm sẵn từ V2, nhưng chỉ lộ khi sequence vượt 999.
- **Vì sao DB test lộ trước DB thật**: test rollback dữ liệu, nhưng **sequence không rollback**. Gọi `nextval` thì giá trị đó mất vĩnh viễn, để hai transaction chạy song song không bao giờ nhận cùng một số. Sau vài chục lần `mvn test`, `warehouse_code_seq` trong DB test đã lên 1056, còn DB dev mới ở 10. Ở kho thật, lỗi sẽ xuất hiện khi tạo NCC thứ 1000.
- **Mình đã ghi sai trong V8**: "quá 9999 thì mã dài thêm". Mình viết mà không kiểm chứng. Bài học: hành vi của hàm thư viện phải chạy thử bằng một câu `SELECT` trước khi ghi vào tài liệu. Cũng chính câu `SELECT 'KHO' || lpad('1050',3,'0')` đã xác nhận nguyên nhân chỉ trong một giây.
- **Cách sửa: migration mới, không sửa migration cũ**. Flyway lưu checksum của từng file đã chạy; sửa V3 thì mọi DB đã chạy V3 sẽ báo `Validate failed` khi khởi động. V10 tạo hàm `format_code(prefix, n, width)`: đệm số 0 khi số ngắn hơn width, giữ nguyên khi dài hơn. Sau đó V10 đổi `DEFAULT` của 5 cột mã. Mã đã có giữ nguyên.
- **Test chặn tái phát**: `CodeGenerationPersistenceTest` đọc `DEFAULT` của mọi cột `code` trong `pg_attrdef` và bắt buộc phải dùng `format_code`. Bảng mới mà ai đó lại viết `lpad` thì test fail. Test kiểm hàm trực tiếp chứ không dùng `setval`, vì `setval` cũng không rollback, sẽ đẩy sequence thật lên mãi.

### Câu hỏi ôn tập (để cuối dự án)

1. Vì sao sequence không rollback theo transaction? Nếu nó rollback được thì hai transaction song song sẽ gặp chuyện gì?
2. Sửa một migration đã chạy thì Flyway phản ứng thế nào? Khi nào `flyway repair` là chấp nhận được?

---

## Module: Khách hàng (2026-10-20)

- **Chọn khóa chống trùng theo nghiệp vụ, không theo thói quen** — NCC chặn trùng tên, còn khách thì *không*. Lý do: hai trang trại khác xã có thể cùng tên "Trại heo Minh", nhưng hai khách không dùng chung một số điện thoại. Vì vậy `uk_customer_phone` là UNIQUE, còn tên thì được trùng. Nếu chặn trùng tên, người bán sẽ phải đặt tên giả kiểu "Trại heo Minh 2". Nếu chẳng chặn gì, một khách sẽ thành hai mã, và báo cáo doanh số theo khách bị chia đôi.
- **UNIQUE chỉ có tác dụng khi dữ liệu đã được chuẩn hóa** — UNIQUE so sánh từng ký tự, nên `0901 234 567` và `0901234567` được coi là hai giá trị khác nhau và lọt qua. Cách làm ở đây là *chuẩn hóa trước, rồi mới kiểm định dạng, kiểm trùng và lưu*. Ngoài ra còn có `CHECK (phone ~ '^0[0-9]{9,10}$')` để kể cả code khác (script seed, SQL tay) cũng không lưu được dạng chưa chuẩn hóa. Phương án khác là lưu nguyên văn và tạo `UNIQUE INDEX` trên biểu thức `regexp_replace(phone, '[ .-]', '', 'g')`. Cách đó giữ được cách gõ của người dùng, nhưng mọi câu tìm kiếm cũng phải dùng đúng biểu thức ấy.
- **Vì sao không dùng `@Pattern` cho SĐT** — Bean Validation chạy trên chuỗi *thô*, nên `@Pattern("^0\d{9,10}$")` sẽ từ chối `0901 234 567`, một cách gõ hợp lệ. Định dạng vì vậy được kiểm ở service sau bước chuẩn hóa. Khi sai thì ném `InvalidRequestException("phone", ...)`, frontend nhận cùng dạng 400 `details[{field}]` như mọi lỗi validation khác và tô đỏ đúng ô.
- **Kiểm trùng ở service + ràng buộc ở DB, cả hai đều cần** — service tìm khách đang giữ SĐT để báo lỗi *có ích* ("thuộc về khách 'Trại heo Minh' (KH0003), đang ngừng → hãy mở lại"). Tuy vậy, nếu hai request cùng SĐT đến đồng thời thì cả hai có thể cùng thấy "chưa có". Khi đó UNIQUE ở DB chặn request đến sau, và GlobalExceptionHandler trả 409.

### Lỗi đã gặp: test DB kiểm hai vi phạm trong một transaction

- Test kiểm `ck_customer_phone` gồm hai câu INSERT sai liên tiếp. Câu thứ hai không trả về lỗi CHECK mà trả `25P02 current transaction is aborted`. Nguyên nhân: sau lỗi đầu tiên, **PostgreSQL bỏ cả transaction** và từ chối mọi lệnh tiếp theo cho đến khi ROLLBACK. Test chạy trong `@Transactional` nên cả hai câu nằm chung một transaction. Cách sửa là tách mỗi vi phạm thành một test (hoặc dùng SAVEPOINT). Hành vi này khác với MySQL: ở MySQL, một câu lỗi không kéo theo cả transaction.
- Đã thử đột biến: cho `normalize` trả nguyên chuỗi thì 3 test API fail (lưu dạng chuẩn hóa, chặn trùng khi gõ khác cách, sửa khách).

### Câu hỏi ôn tập (để cuối dự án)

1. Vì sao NCC chặn trùng tên còn khách hàng thì chặn trùng SĐT? Khi nào cả hai cách đều sai?
2. Có `UNIQUE (phone)` mà không chuẩn hóa thì để lọt trường hợp nào? Vì sao còn cần thêm CHECK ở DB khi service đã chuẩn hóa rồi?
3. Trong PostgreSQL, sau một câu lệnh lỗi trong transaction thì câu tiếp theo nhận lỗi gì? SAVEPOINT giải quyết việc này ra sao?

---

## Module: Tồn kho trên trang Sản phẩm (2026-10-19)

- **Hướng phụ thuộc quyết định chỗ đặt code** — `inventory` đã dùng `Product` (dòng tồn trỏ tới SP). Nếu `ProductService` gọi ngược `InventoryRepository` để lấy số tồn, hai module phụ thuộc vòng: sửa module nào cũng phải biên dịch lại module kia, và không tách riêng được. Vì vậy API "SP kèm tồn" đặt ở `inventory` (`/api/inventory/products`), dùng lại nguyên `ProductSpecifications`. Phương án khác là *đảo phụ thuộc*: `product` khai báo interface `ProductStockReader`, `inventory` cài đặt nó. Cách đó đúng hơn khi product *bắt buộc* phải trả số tồn, nhưng thêm một lớp trừu tượng.
- **Tránh N+1 bằng GROUP BY theo trang** — trang 20 SP mà mỗi SP chạy một câu `SUM` thì thành 1 + 20 câu. Ở đây chỉ chạy một câu `SUM … WHERE product_id IN (20 id) GROUP BY product_id`, rồi ghép theo id trong Java. Số câu SQL cố định, không phụ thuộc số SP trên trang.
- **Lọc theo số tính toán phải làm trong SQL** — "Sắp hết" phụ thuộc tổng tồn, mà tổng không phải một cột. Nếu lọc sau khi tải trang thì tổng số kết quả và phân trang sai (giống bài học bộ lọc trước). Giải pháp là subquery trong `Specification`: `(SELECT COALESCE(SUM(quantity),0) FROM inventory WHERE product_id = p.id) <= p.min_stock_quantity`. `COALESCE` cần cho SP chưa từng nhập (SUM của 0 dòng là NULL, không phải 0).
- **Một quy tắc viết hai nơi thì phải có test giữ chúng khớp** — `StockStatus.of` (Java, để trả về) và `hasStockStatus` (SQL, để lọc) cùng diễn đạt "≤ mức tối thiểu". Đã thử đổi `≤` thành `<` trong SQL → `shouldFilterByStockStatus` fail (Cám heo tồn đúng 50 = mức 50 không còn được lọc ra là Sắp hết).
- **PUT = thay toàn bộ → thêm trường là phải rà mọi chỗ gọi PUT** — nút "Mở bán lại" tự dựng body PUT từ dữ liệu dòng. Thêm `minStockQuantity` vào API mà quên thêm ở đây thì mỗi lần mở bán lại sẽ âm thầm xóa mức tồn tối thiểu. `grep "productApi.update"` để tìm hết các chỗ.
- **Tải khi cần (lazy) cho popover** — 20 dòng × tồn từng kho mà tải sẵn thì tốn 20 request, trong khi người dùng thường chỉ rê chuột vài dòng. Gọi API ở `onOpenChange` lần đầu, rồi giữ kết quả. Đặt `key` theo số tồn để dữ liệu cũ không bị giữ sau khi danh sách tải lại.

### Lỗi đã gặp: không chọn kho mà ô tồn vẫn hiện 0, dưới ghi "Tổng mọi kho: 27"

- **Nguyên nhân gốc: backend trả `"warehouseId": null` thay vì bỏ trường**, trái với contract (NON_NULL). `@JsonInclude(NON_NULL)` chỉ gắn trên `ApiResponse`; annotation trên một lớp **không lan** sang các object lồng bên trong (`data`). Lỗi này có từ đầu dự án, ở mọi API (`"description": null`, `"email": null`...). Sửa ở một chỗ: `spring.jackson.default-property-inclusion: non_null`.
- **Nguyên nhân trực tiếp ở frontend**: `product.warehouseId !== undefined` mà `null !== undefined` là `true` → giao diện tưởng đang lọc kho, lấy `warehouseStockQuantity ?? 0` = 0. Với dữ liệu từ API, dùng `!= null` / `== null` (bắt cả `null` lẫn `undefined`). TypeScript khai báo `warehouseId?: number` nhưng **không kiểm tra lúc chạy**: kiểu chỉ là lời hứa, JSON thật có thể khác.
- **Vì sao test không bắt được**: `jsonPath("$.x").doesNotExist()` vẫn pass khi `x` có giá trị `null`. Test mới `JsonNullOmissionTest` kiểm tra trên **chuỗi JSON thô** (không có `"field": null`). Đã thử bỏ dòng cấu hình → 4/6 API fail.
- **Bài học UX**: ô số mà không ghi phạm vi ("tại kho nào") thì hai con số đúng vẫn trông như mâu thuẫn. Tiêu đề cột giờ đổi thành "Tồn tại KHO002" khi đang chọn kho.

### Câu hỏi ôn tập (để cuối dự án)

1. Vì sao `COALESCE(SUM(quantity), 0)` chứ không chỉ `SUM(quantity)` trong bộ lọc "Hết hàng"?
4. Trong JavaScript, `null !== undefined`, `null != undefined`, `null ?? 0` cho kết quả gì? Khi nào dùng `!=` thay vì `!==`?
5. Vì sao `@JsonInclude(NON_NULL)` trên `ApiResponse` không làm mất `"description": null` bên trong `data`?
2. Nếu đặt endpoint tồn kho trong module product thì phụ thuộc vòng xuất hiện ở đâu? Có những cách nào để tránh?
3. "Đang về" lấy SL đặt của phiếu Nháp. Khi phiếu đó hoàn tất với SL thực nhận khác SL đặt, con số nào thay đổi và thay đổi bao nhiêu?

---

## Module: Nhập kho — giao diện (2026-10-19)

- **Giao diện chỉ gợi ý, backend mới quyết định** — nút "Hoàn tất" chỉ hiện khi phiếu đang Nháp, nhưng đó là để người dùng đỡ bấm nhầm, không phải lớp bảo vệ. Ví dụ: anh A mở PN000005 lúc còn Nháp, trong lúc đó chị B hoàn tất. Anh A bấm "Hoàn tất" → backend trả 409 (khóa + kiểm tra lại trạng thái) → khung hiện lỗi. Không bao giờ dựa vào việc "ẩn nút" để giữ quy tắc nghiệp vụ.
- **`Form.List` + `Table`** — mỗi dòng hàng là một hàng của `Table`, mỗi ô là một `Form.Item` có tên dạng mảng `[chỉ số dòng, 'orderedQuantity']`. Toàn bộ giá trị form có dạng `{ lines: [{ productId, orderedQuantity, ... }] }`, khớp thẳng với body API.
- **Tên field lỗi phải đổi sang đường dẫn** — backend báo lỗi ở `lines[1].productId` (chuỗi). Form của Ant Design tìm ô theo mảng `['lines', 1, 'productId']`. Truyền nguyên chuỗi thì lỗi không hiện ở đâu cả. Đây là lỗi im lặng, rất khó phát hiện nếu không viết test cho `toFieldPath`.
- **Số thực trong JavaScript** — `7 * 0.15` ra `1.0499999…`, nên làm tròn có thể sai 1 đồng. Tính bằng "xu" (đơn giá × 100 rồi `Math.round`) thì 15 × 7 = 105 xu, luôn làm tròn đúng lên 1 đ. Số trên form vẫn chỉ là số tạm; số chính thức là `BigDecimal` của backend.
- **Ẩn lỗi bằng cách "sửa hộ" người dùng là nguy hiểm** — `InputNumber precision={0}` âm thầm biến 2,5 bao thành 3 bao, giống lỗi `accept-float-as-int` của Jackson ở backend. Báo lỗi "phải là số nguyên" thì người dùng tự quyết.
- **"Hôm nay" ở trình duyệt cũng phải theo giờ VN** — `new Date().toISOString()` là ngày UTC, nên trước 7h sáng ở VN vẫn ra hôm qua. `todayIso()` dùng `Intl` với `timeZone: 'Asia/Ho_Chi_Minh'`.
- **Suy ra state thay vì đồng bộ bằng effect** — lưu `{ supplierId, items }` của lần tải gần nhất thay vì hai state `linkedProducts` + `loading` được set trong effect. "Đang tải" = NCC đang chọn khác NCC của kết quả. Ít trạng thái hơn thì ít khả năng lệch nhau hơn (lint `set-state-in-effect` đã nhắc).
- **`key` để reset cả component** — mỗi lần mở khung phiếu, trang danh sách tăng `key` → React tạo `InboundReceiptDrawer` mới, mọi state (phiếu đã tải, lỗi, đang lưu) bắt đầu sạch, không cần viết code dọn.

### Câu hỏi ôn tập (để cuối dự án)

1. Nếu chỉ ẩn nút "Hoàn tất" ở frontend mà backend không khóa + kiểm tra trạng thái, có những cách nào để phiếu vẫn bị hoàn tất hai lần?
2. Vì sao ô sản phẩm lấy từ "SP đang gán cho NCC" thay vì tìm trong toàn bộ SP? Khi nào phải đổi cách làm?
3. "Lưu & hoàn tất" gọi 2 API liên tiếp (tạo nháp → hoàn tất). Nếu API thứ hai lỗi thì dữ liệu ở trạng thái nào, và giao diện cần hiện gì?

---

## Module: Nhập kho — backend (2026-10-19)

### Trạng thái phiếu & khóa

- **Kiểm tra trạng thái phải nằm SAU khóa, trong cùng transaction** — Anh A và chị B cùng mở phiếu PN000005 (Nháp, 100 bao cám heo) và cùng bấm "Hoàn tất". Nếu service đọc phiếu bằng `findById`, cả hai đều thấy `DRAFT` → cả hai cộng 100 bao → kho báo 200 bao trong khi chỉ nhận 100. Với `SELECT … FOR UPDATE` (`findForUpdate`), B phải **chờ** A commit, rồi đọc lại thấy `COMPLETED` → bị từ chối 409.
  _Đã kiểm chứng:_ tạm đổi `findForUpdate` thành `findById` → test 5 luồng cho **5/5 thành công** (tồn cộng 5 lần) → test fail. Khôi phục → 1/5.
- **Khóa chỉ có tác dụng khi mọi đường đều đi qua nó** — khóa của PostgreSQL không chặn ai *đọc thường*; nó chỉ bắt các transaction *cũng xin khóa* phải xếp hàng. Vì vậy `InboundReceiptService` có đúng một cửa `lockForChange(id, trạng thái cần, hành động)` cho sửa / hoàn tất / hủy / đảo. Thêm thao tác mới mà quên đi qua cửa này = lỗ hổng mới.
- **Pessimistic vs optimistic lock** — *Pessimistic* (`FOR UPDATE`): khóa trước, người đến sau chờ. Hợp khi xung đột gây hậu quả thật (cộng tồn hai lần). *Optimistic* (`@Version`): không khóa, lúc ghi so phiên bản, lệch thì báo lỗi để người dùng thử lại. Hợp với form sửa thông tin NCC (hiếm khi hai người sửa cùng lúc, sai thì sửa lại).
- **Persistence context có thể trả dữ liệu cũ** — trong cùng transaction, nếu phiếu đã được nạp trước đó, Hibernate trả lại *object trong bộ nhớ* dù câu SQL có `FOR UPDATE`. Vì vậy `lockForChange` phải là lần đọc phiếu đầu tiên trong transaction.
- **Đổi trạng thái bằng hành động, không bằng `PUT status`** — `POST /{id}/complete`, `/cancel`, `/reverse`: mỗi hành động có điều kiện và tác động tồn riêng. Nếu cho `PUT { status: "COMPLETED" }`, server phải đoán ý định từ chênh lệch dữ liệu.

### Cộng / trừ tồn nguyên tử

- **Lost update** — "đọc tồn 100 → Java tính 100 − 10 → ghi 90" chạy song song hai lần cho ra 90 thay vì 80. Phải để DB làm trong **một câu**: `UPDATE … SET quantity = quantity - :q WHERE … AND quantity >= :q RETURNING quantity`. Rỗng = không đủ tồn → `BusinessException` → rollback.
- **Upsert `INSERT … ON CONFLICT DO UPDATE`** — lần đầu nhập "Cám heo con 25kg" vào Kho Hà Nội thì chưa có dòng tồn. Hai phiếu hoàn tất cùng lúc mà dùng "kiểm tra có chưa → chưa thì INSERT" thì cả hai cùng INSERT → lỗi trùng UNIQUE. `ON CONFLICT` để PostgreSQL tự xử lý: một bên tạo, bên kia cộng vào. Test kiểm tra chuỗi `quantity_before → quantity_after` của 5 giao dịch nối liền nhau (không mất lần cộng nào).
- **`RETURNING` thay cho SELECT lại** — lấy tồn *sau khi đổi* ngay trong câu UPDATE để ghi `quantity_before/after`. SELECT riêng sau đó có thể thấy giá trị đã bị transaction khác đổi.
- **`Propagation.MANDATORY`** — `InventoryService` bắt buộc chạy *bên trong* transaction của nghiệp vụ gọi tới. Nếu nó tự mở transaction riêng (`REQUIRES_NEW`), phần cộng tồn của dòng 1 có thể đã commit trong khi dòng 2 lỗi → vi phạm "tất cả hoặc không có gì".
- **Lock ordering** — các dòng luôn được xử lý theo `product_id` tăng dần. Hai phiếu cùng chứa cám heo + cám gà sẽ khóa theo cùng thứ tự, nên không bao giờ rơi vào cảnh "A giữ heo chờ gà, B giữ gà chờ heo" (deadlock).

### Lịch sử bất biến — phòng thủ nhiều lớp

- `StockTransaction` không có setter → `@Immutable` (Hibernate không bao giờ sinh UPDATE) → **trigger DB** chặn `UPDATE`/`DELETE`/`TRUNCATE`, kể cả khi ai đó sửa tay bằng psql. Sai sót sửa bằng giao dịch đảo: phiếu PN000005 đảo → mỗi giao dịch IN có một OUT với `reversal_of_id` trỏ về nó. `UNIQUE (reversal_of_id)` bảo đảm không đảo hai lần, kể cả khi service có bug.
- **CHECK "giao dịch cân"** — `IN ⇒ after = before + qty`: DB tự bắt lỗi tính sai snapshot.
- Trigger `FOR EACH ROW` **không chạy với `TRUNCATE`** → cần thêm trigger `FOR EACH STATEMENT` riêng.

### Tiền & số lượng

- **HALF_UP vs HALF_EVEN** — 3 bao × 12.345,50 đ = 37.036,50 đ: HALF_UP → 37.037; HALF_EVEN (làm tròn ngân hàng) → 37.036. Test dùng các ca `.5` (0,5 → 1; 2,5 → 3) để nếu ai đổi `RoundingMode` thì test fail ngay.
- **Jackson âm thầm cắt số lẻ** — mặc định `"orderedQuantity": 2.5` vào field `Integer` thành `2` bao mà không báo lỗi. Tắt bằng `spring.jackson.deserialization.accept-float-as-int: false` → 400. Đây là kiểu lỗi chỉ phát hiện được khi viết test cho đúng quy tắc nghiệp vụ.
- **"Hôm nay" theo giờ Việt Nam** — server chạy UTC, lúc 0h30 sáng ở VN thì UTC vẫn là hôm qua. Lúc đó `@PastOrPresent` (theo múi giờ JVM) sẽ chặn nhầm ngày hôm nay. Kiểm tra trong service bằng `LocalDate.now(BUSINESS_ZONE)`.

### JPA

- **Sửa dòng tại chỗ, không xóa-hết-thêm-lại** — Hibernate không chạy SQL theo thứ tự bạn gọi mà xếp lại lúc flush: với `em.remove()`, DELETE chạy **sau** INSERT; riêng orphan removal (bỏ phần tử khỏi list) thì lại được xếp trước. Nếu "xóa dòng cám heo, thêm lại dòng cám heo" mà INSERT chạy trước DELETE → vi phạm `UNIQUE (document_id, product_id)`. Khớp theo `productId` (có thì cập nhật, mới thì thêm, thiếu thì bỏ) tránh hẳn câu hỏi thứ tự đó, đồng thời giữ id dòng. _(Thứ tự flush ở đây là kiến thức về Hibernate, chưa được kiểm chứng bằng test trong dự án.)_
- **Native query có `RETURNING` không dùng `@Modifying`** — `@Modifying` chỉ trả số dòng bị ảnh hưởng.

### Test

- **Test `@Transactional` không thấy được rollback của service** — service join vào transaction của test, nên phần đã trừ ở dòng 1 vẫn còn trong cùng transaction cho tới cuối test. Ca "đảo phiếu 2 dòng, dòng 2 thiếu tồn → dòng 1 không bị trừ" và các ca đa luồng phải ở lớp **không** `@Transactional` (`InboundReceiptConcurrencyTest`), commit thật, rồi tự dọn ở `@AfterEach`.
- **Dọn bảng bất biến trong DB test** — `ALTER TABLE … DISABLE TRIGGER` ngay trong transaction dọn dẹp: nếu lỗi giữa chừng thì rollback, trigger tự bật lại (DDL của PostgreSQL có transaction). Không bao giờ làm việc này với dữ liệu thật.
- **Đồng thời thật sự** — `CountDownLatch` làm "vạch xuất phát": mọi luồng sẵn sàng rồi mới cùng chạy, tăng khả năng chồng lấn.
- **Thử phá code để kiểm chứng test** — test đạt ngay lần đầu chưa chứng minh được gì. Cố ý bỏ khóa và thấy test fail mới biết test thực sự bảo vệ quy tắc.

### Công cụ / môi trường

- **IDE ghi đè `target/classes` bằng class hỏng** — sửa file bằng `sed` khi VS Code đang mở: trình biên dịch ECJ của IDE build lại, gặp lỗi giả "Flexible Constructor Bodies" (Lombok) và ghi ra class chứa `Unresolved compilation problems`. Maven thấy class mới hơn source nên không biên dịch lại → mọi test lỗi "Constructor threw exception". Dùng `mvn clean test` / `mvn clean verify` khi kết quả khó hiểu.

### Câu hỏi ôn tập (để cuối dự án)

1. Nếu `lockForChange` kiểm tra trạng thái *trước* rồi mới gọi `findForUpdate`, chuyện gì xảy ra khi hai người cùng bấm "Hoàn tất"?
2. Vì sao `InventoryService` dùng `Propagation.MANDATORY` thay vì `REQUIRED` hay `REQUIRES_NEW`?
3. Đảo phiếu khi tồn không đủ bị chặn ở mấy lớp? Lớp nào vẫn chặn được nếu ai đó xóa câu `WHERE quantity >= :q`?
4. Vì sao ca test rollback giữa chừng không viết được trong lớp test có `@Transactional`?
5. `ON CONFLICT DO UPDATE` giải quyết vấn đề gì mà "SELECT xem có chưa → INSERT" không giải quyết được?

---

## Module: Bộ lọc dùng chung + rà soát UI/UX danh sách (2026-10-19)

### Backend

- **Specification dùng chung, tên cột do code quyết định** — `CommonSpecifications.keywordContains(keyword, "code", "name")` dùng lại được cho SP, NCC, Kho. Tên thuộc tính được **viết cứng trong service**, không lấy từ request: nếu cho client gửi `?filter[field]=...` tùy ý, người dùng có thể lọc cột không có index (chậm) hoặc cột không nên lộ.
- **Lọc trong SQL, không lọc trên trình duyệt** — lọc 20 dòng đang xem thì `totalElements` và số trang sai. Ví dụ: 45 NCC, lọc "Ngừng hoạt động" trên trang 1 chỉ thấy 2 dòng, nhưng thực tế có 7 NCC ngừng nằm rải ở trang 2, 3.
- **Tìm không dấu bằng `unaccent` (V5)** — gõ "cam heo" ra "Cám heo con 25kg". Hai vế (cột và từ khóa) đều chuẩn hóa **trong SQL** bằng cùng hàm `lower(immutable_unaccent(...))`, tránh lệch giữa cách Java và PostgreSQL bỏ dấu.
- **`IMMUTABLE` vs `STABLE`** — PostgreSQL chỉ cho đánh index trên biểu thức dùng hàm `IMMUTABLE` (cùng đầu vào luôn cùng kết quả). `unaccent()` là `STABLE` vì từ điển có thể đổi → bọc lại, chỉ định rõ từ điển. `LIKE '%cam%'` (có `%` ở đầu) không dùng được B-tree; khi cần nhanh phải dùng `pg_trgm` + GIN.
- **Tiêu chí lọc là record, bind bằng `@ModelAttribute`** — `GET /api/suppliers?keyword=cam&active=true` → `SupplierSearchCriteria(keyword, active)`. Thêm tiêu chí = thêm field vào record + một dòng ở `XxxSpecifications.from`. Vẫn giữ GET: tìm kiếm là đọc dữ liệu; `POST /get-data` biến API thành RPC, mất F5/bookmark/cache.
- **Interface nhỏ thay cho base class (composition over inheritance)** — record không `extends` được; `BaseSearchRequest { keyword, active }` buộc mọi module mang `active` kể cả lịch sử giao dịch (không có trạng thái). `record SupplierSearchCriteria(...) implements HasKeyword, HasActive` chỉ nhận đúng phần mình có, và `CommonSpecifications.hasActive(criteria)` nhận thẳng interface.
- **Test giữ quy ước (convention test)** — `SearchConventionTest` quét classpath bằng `ClassPathScanningCandidateComponentProvider` của Spring (không thêm ArchUnit): `*SearchCriteria` phải là record, có `XxxSpecifications.from(...)`. Đã thử cố ý bỏ `HasActive` khỏi `WarehouseSearchCriteria` → test fail đúng chỗ. Kèm một test "phải tìm thấy 3 module" để tránh pass rỗng khi quét sai package.
- **Lọc ngày trên cột `Instant`** — "đến 15/10" phải gồm cả 23:59 ngày 15/10 giờ VN; 00:00 ngày 15/10 giờ VN là 17:00 ngày 14/10 UTC. `instantWithinDates` dùng `>= đầu ngày from` và `< đầu ngày (to + 1)`, không dùng `BETWEEN`.
- **Không đổi envelope chỉ vì "chuẩn khác"** — đề xuất `ApiResponse {statusCode, ...}` và `PagedResponse {pageIndex từ 1, ...}` trùng chức năng với cái đã có; đổi thì phải sửa FE, contract và 78 test mà không thêm giá trị. `statusCode` trong body lặp lại HTTP status.

### Frontend

- **Form lọc "chung" = khai báo cấu hình, không suy ra từ entity** — kiểu TypeScript bị xóa khi chạy, nên không thể "ném object `Supplier` vào" rồi tự sinh ô lọc. `FilterBar` nhận mảng `fields` (`keyword` / `select` / `status`); màn hình mới chỉ khai báo 2–3 dòng. Chỉ khai báo những tham số API thực sự hỗ trợ.
- **`type` thay vì `interface` cho bộ lọc** — `type SupplierFilters = {...}` gán được vào `Record<string, ...>`, còn `interface` thì không (interface có thể được mở rộng sau nên TS không cho ngầm định index signature).
- **Lùi trang khi trang hiện tại rỗng** — đang lọc "Đang kinh doanh", trang 3 còn 1 SP, ngừng kinh doanh SP đó → tải lại trang 3 rỗng. `fallbackPageIndex` đưa về trang 2 thay vì báo "không có kết quả".
- **Chống mất dữ liệu form** — `maskClosable={false}` + hỏi lại khi `form.isFieldsTouched()`. Form chưa sửa thì đóng ngay: hỏi thừa làm người dùng quen bấm "Đồng ý" mà không đọc.
- **Một bản ghi – một khung** — gộp khung gán/gỡ riêng vào tab trong `DetailDrawer`; menu `⋯` mở thẳng tab đó. Tránh hai drawer làm cùng một việc.
- **Accessibility của mã bấm được** — `Typography.Link` không có `href` là thẻ `<a>` không Tab tới được; đổi sang `Button type="link"` (thẻ `<button>`). `aria-label` của nút `⋯` kèm mã bản ghi để trình đọc màn hình phân biệt các dòng.

### Câu hỏi ôn tập (để cuối dự án)

1. Vì sao không cho client gửi tên cột tùy ý để lọc, dù làm vậy "chung" hơn?
2. Vì sao không tạo được index trên `lower(unaccent(name))` nhưng tạo được trên `lower(immutable_unaccent(name))`?
3. "Mở lại" gửi PUT với dữ liệu của dòng trong bảng. Nếu người khác vừa sửa tên NCC đó, chuyện gì xảy ra? Cần gì để phát hiện (gợi ý: optimistic locking / `version`)?

---

## Module: Nhà cung cấp (2026-10-16 → 2026-10-17)

### Backend

- **Khóa chính kép + `@EmbeddedId` / `@MapsId`** — `product_supplier` không có cột `id`, khóa chính là `(supplier_id, product_id)`. Gỡ "Cám heo con 25kg" khỏi NCC001 rồi gán lại vẫn chỉ có 1 dòng (đổi `active`). `@MapsId("productId")` cho biết cột `product_id` vừa là một phần khóa vừa là FK tới `product`.
  _Khi nào dùng `id` riêng:_ khi dòng nối có vòng đời riêng hoặc bảng khác cần trỏ tới nó (ví dụ bảng giá theo hợp đồng).
- **Thứ tự cột trong index ghép** — index `(supplier_id, product_id)` phục vụ được "NCC X có SP nào" nhưng **không** phục vụ "SP Y có NCC nào", vì `product_id` đứng sau. Cần thêm index riêng `idx_product_supplier_product`.
- **Flush của Hibernate** — `setActive(false)` chỉ đổi trong bộ nhớ; Hibernate ghi xuống DB khi commit hoặc trước một truy vấn JPA. Test `@Transactional` rollback nên không commit, `JdbcTemplate` đọc thẳng DB vẫn thấy giá trị cũ → phải `entityManager.flush()` trước khi đọc. Sẽ gặp lại khi trộn câu `UPDATE` viết tay (trừ tồn) với JPA.
- **`@EntityGraph` chống N+1** — danh sách 20 liên kết mà không JOIN sẵn `product`/`supplier` sẽ thành 1 + 20 câu SQL.
- **Phụ thuộc giữa module ≠ vị trí URL** — `GET /api/products/{id}/suppliers` đặt code trong module `supplier`, vì `supplier` đã phụ thuộc `product` (kiểm tra SP khi gán). Đặt ở `product` sẽ thành phụ thuộc vòng.
- **Một quy tắc, một chỗ kiểm tra** — gán/gỡ từ phía SP gọi lại API của NCC, không viết endpoint thứ hai → BR-SUP-05/06 chỉ được kiểm tra ở `SupplierService`.
- **Mã lỗi theo contract** — vi phạm nghiệp vụ (gán SP đã ngừng kinh doanh) là 409 `BUSINESS_RULE_VIOLATION`, không phải 400: dữ liệu gửi lên đúng định dạng, chỉ trạng thái hệ thống không cho phép.

### Frontend

- **Tìm kiếm phía server → `filterOption: false`** — gợi ý SP đã được backend lọc; nếu để `Select` lọc thêm trên trình duyệt có thể ẩn mất kết quả đúng. Ngược lại, danh sách NCC tải sẵn (≤ 100) thì lọc trên trình duyệt (`optionFilterProp: 'label'`).
- **Debounce** — chờ 400 ms sau phím cuối mới gọi API, tránh 1 request mỗi phím.
- **`key` để reset state** — `<SupplierProductsPanel key={supplier.id}>`: chuyển từ NCC A sang B, React tạo component mới, không giữ ô tìm kiếm/trang của A.
- **`useCallback` cho hàm tải dữ liệu** — `usePagedList` tải lại mỗi khi hàm đổi; không bọc `useCallback` thì mỗi lần render tạo hàm mới → gọi API liên tục.
- **Khả năng phát hiện chức năng (discoverability)** — chức năng gán SP ban đầu nằm cuối khung chi tiết nên người dùng tưởng chưa có; thêm mục riêng trong menu `⋯` mở thẳng khung gán.

### Công cụ / môi trường

- **Lỗi đỏ trong IDE ≠ lỗi build** — VS Code dùng trình biên dịch Eclipse (ECJ) riêng; ECJ mới + Lombok 1.18.34 báo nhầm "Flexible Constructor Bodies". Tin `mvn compile` trước, rồi mới nghi IDE lệch phiên bản. Sửa: nâng Lombok lên 1.18.42 + "Java: Clean Java Language Server Workspace".
- **Biên dịch tăng dần để lại bytecode cũ** — sửa tạm `WarehouseSearchCriteria` (bỏ `HasActive`) rồi khôi phục: Maven chỉ biên dịch lại file đổi, `WarehouseSpecifications.class` vẫn tham chiếu bản cũ → 82 test lỗi "Constructor threw exception" dù code đúng. `mvn clean verify` hết lỗi. Thấy lỗi khởi tạo bean khó hiểu sau khi đổi kiểu/chữ ký → thử `clean` trước khi sửa code.
- **`mvn clean` lỗi trên Windows** — thư mục `target/` bị IDE/app đang chạy khóa; chạy `mvn verify` không `clean`, hoặc tắt tiến trình đang giữ file.

### Câu hỏi ôn tập (để cuối dự án)

1. Nếu `product_supplier` dùng `id` tự tăng và **không** có UNIQUE `(supplier_id, product_id)`, hai người bấm "Gán" cùng lúc thì sao? Thiết kế hiện tại chặn ở đâu?
2. Vì sao truy vấn "SP Y do NCC nào cung cấp" không dùng được index của khóa chính?
3. Vì sao test `shouldSoftUnlink` fail lần đầu dù code service đúng?

---

## Các module trước (UI polish, 2026-10-16)

### Câu hỏi ôn tập

1. Skeleton loading khác spinner thế nào? Vì sao skeleton làm người dùng cảm thấy trang tải nhanh hơn?
2. Vì sao chỉ hiện thông báo thành công **sau khi** API trả `success: true`, không hiện ngay lúc bấm nút?
