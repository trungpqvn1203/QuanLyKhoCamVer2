# CI với GitHub Actions

[← Mục lục kho bài học](../README.md)

## Module: CI — tự build và test mỗi lần push (2026-11-05)

File: `.github/workflows/ci.yml`. Mục tiêu: **không code nào lên `main` mà chưa qua đúng bộ kiểm tra mà ta vẫn chạy tay**
(`mvn clean verify`, `npm run lint`, `npm test`, `npm run build`). CI chưa đụng tới server — đó là việc của module CD.

### Khái niệm

- **Workflow** — một file YAML trong `.github/workflows/`. GitHub đọc file này ngay trong commit được push,
  nên sửa workflow cũng chỉ là sửa code: có lịch sử, có review, rollback bằng git.
- **Trigger (`on:`)** — sự kiện làm workflow chạy: `push`, `pull_request`, `workflow_dispatch` (bấm tay), `schedule` (cron)...
- **Job** — một nhóm bước chạy trên **một máy ảo mới tinh** (runner `ubuntu-24.04`). Hai job `backend` và `frontend` chạy **song song**
  trên hai máy khác nhau, không chung ổ đĩa. Chạy xong máy bị xóa → mỗi lần chạy đều sạch như lần đầu.
- **Step** — từng lệnh trong job: hoặc `run:` (lệnh shell), hoặc `uses:` (dùng một *action* người khác viết sẵn, ví dụ `actions/checkout`).
- **Service container** — container chạy kèm job, ở đây là Postgres 16 cho test.

### Bản chất: CI chỉ là "một máy lạ làm lại đúng những gì mình làm trên máy mình"

Mỗi bước trong `ci.yml` đều tương ứng với một việc ta từng tự làm:

| Trên máy mình | Trong `ci.yml` | Vì sao phải nói rõ ra |
|---|---|---|
| Code đã có sẵn trong thư mục | `actions/checkout` | Runner trống trơn, phải clone repo về |
| Đã cài JDK 21, Node | `setup-java`, `setup-node` | Phiên bản phải **ghi cố định** (21, 22), không phụ thuộc máy ai |
| Postgres dev chạy sẵn ở `localhost:5432`, có DB `grain_warehouse_test` | `services: postgres` + `POSTGRES_DB: grain_warehouse_test` | Test của dự án (`application-test.yml`) cần **Postgres thật** để kiểm Flyway và CHECK/UNIQUE |
| Máy đặt múi giờ Việt Nam | `TZ: Asia/Ho_Chi_Minh` | Runner mặc định **UTC**. Lúc 0h–7h giờ VN, "hôm nay" ở UTC vẫn là hôm qua → test liên quan ngày ghi sổ, kỳ lương, công nợ quá hạn đỏ/xanh tùy giờ chạy |
| `node_modules` có sẵn | `npm ci` | Cài **đúng** bản trong `package-lock.json`; lock lệch `package.json` thì báo lỗi thay vì âm thầm cập nhật như `npm install` |

Nhìn bảng này sẽ thấy câu "chạy được trên máy em" thường là do máy mình có một thứ **ngầm định** (DB đã tạo sẵn, múi giờ,
thư viện cài tay) mà không ai ghi ra. **CI buộc mọi điều ngầm định phải được ghi thành chữ.** Đó là giá trị lớn nhất của nó, còn tự động chỉ là phần phụ.

### Đi qua từng quyết định trong file

- **`push` mọi nhánh, không dùng `pull_request`.** Dự án hay merge local. Chạy theo push thì nhánh `feature/*` có kết quả ngay *trước* khi merge.
  PR mở từ nhánh trong cùng repo vẫn hiện dấu ✅/❌ của commit đó. Nếu bật thêm `pull_request` thì mỗi commit sẽ chạy **hai lần**.
- **`paths-ignore` cho `docs/`, `lessons/`, `*.md`.** Sửa bài học không đổi code → không cần tốn 5–8 phút chạy. Đánh đổi: nếu sau này
  bật *branch protection* bắt buộc CI xanh, commit chỉ sửa tài liệu sẽ kẹt ở trạng thái "đang chờ". Gặp thì đổi sang lọc theo đường dẫn bên trong job.
- **`concurrency` + `cancel-in-progress`.** Push 3 lần liền lên cùng nhánh → chỉ lần mới nhất chạy hết. Tiết kiệm phút, và kết quả luôn ứng với code mới nhất.
- **`permissions: contents: read`.** Mỗi lần chạy GitHub cấp một `GITHUB_TOKEN`. Mặc định quyền có thể rộng. CI chỉ cần đọc code, nên thu hẹp lại:
  một action bên thứ ba bị chiếm quyền cũng không ghi được gì vào repo (*nguyên tắc quyền tối thiểu*). Module CD sẽ mở thêm `packages: write` để đẩy image, **chỉ cho job đó**.
- **Healthcheck cho service Postgres.** Không có nó, Actions bật container xong là chạy `mvn` ngay, lúc Postgres còn đang khởi tạo
  → test đầu tiên `Connection refused` (đúng loại lỗi "tới được máy nhưng chưa ai nghe cổng" ở bài 03).
- **`cache: maven` / `cache: npm`.** Lưu `~/.m2` và cache npm theo hash của `pom.xml`, `package-lock.json`. Lần đầu tải vài trăm MB, các lần sau lấy từ cache.
  Đây cùng là ý tưởng "lớp dependency trước, code sau" trong `backend.Dockerfile`.
- **Tải báo cáo Surefire khi đỏ (`if: failure()`).** Log Maven dài hàng nghìn dòng. Báo cáo `target/surefire-reports/` cho biết đúng test nào, assert nào.
- **Frontend chạy `npm run build` thay vì `npx tsc --noEmit`.** `tsconfig.json` của dự án có `"files": []` và chỉ trỏ tới
  `tsconfig.app.json`/`tsconfig.node.json` (*project references*). `tsc --noEmit` ở thư mục gốc vì vậy **không kiểm file nào** mà vẫn báo thành công.
  `tsc -b` (nằm trong `npm run build`) mới đi theo references và kiểm thật. (Lệnh trong CLAUDE.md nên đổi tương ứng.)

### Lỗi đã gặp khi làm module này: DB test dùng chung giữa các nhánh

Chạy thử `mvn clean verify` trên máy trước khi push: **860/904 test lỗi**, tất cả cùng một nguyên nhân:

```
FlywayValidateException: Detected applied migration not resolved locally: 28.
```

- Nhánh `feature/delivery-trip` có `V28` (xe + chuyến giao) và đã chạy test trên DB `grain_warehouse_test`, nên DB ghi nhận "đã áp V28".
- Nhánh `feature/ci` (tách từ `fix/portal-hardening`) không có V28, đi thẳng V27 → V29. Flyway thấy DB có một migration mà code không có, nên **từ chối khởi động**.
  Đây là hành vi đúng: Flyway không biết V28 đã đổi những gì trong DB.
- **Không phải lỗi code.** Cách kiểm: chạy lại trên một Postgres trống (container tạm ở cổng 55432) thì kết quả mới phản ánh đúng code.
- **Lần chạy lại đầu tiên vẫn đỏ, lần này vì `password authentication failed`.** Mình chỉ đặt `POSTGRES_PORT=55432`.
  `application.yml` có `spring.config.import: optional:file:../.env[.properties]` nên nạp `.env` của máy, trong đó có mật khẩu Postgres local (khác `postgres`).
  Thứ tự ưu tiên của Spring: **biến môi trường thật > file được import > giá trị mặc định `${...:postgres}`**. Cổng lấy từ biến môi trường (55432), mật khẩu lấy từ `.env`,
  nên ghép lại thành một cấu hình sai. Phải đặt đủ `POSTGRES_HOST/PORT/TEST_DB/USER/PASSWORD` thì mới khớp với CI.
  Trên CI không có `.env` (file nằm trong `.gitignore`), nên hai bộ cấu hình **khác nhau mà không ai thấy**. Đây thêm một "điều ngầm định" của máy local.
- **Còn đúng 1/904 test đỏ: `UserApiTest.shouldKeepAtLeastOneActiveAdmin`** (mong 409, nhận 200). Truy tới cùng: trên DB trống, `BootstrapAdminRunner`
  thấy bảng `app_user` rỗng, đọc `BOOTSTRAP_ADMIN_*` từ `.env` và **commit** một tài khoản `admin` thật. Test kiểm "không hạ quyền admin cuối cùng"
  lúc đó không còn đúng, vì `quan.tri` không phải admin cuối cùng nữa. Trên DB test cũ thì bảng đã có user từ trước nên runner không làm gì; trên CI không có `.env` nên runner cũng không làm gì.
  Vì vậy test này **chỉ đỏ ở máy dev có DB test mới**, đúng loại lỗi "lúc có lúc không" dễ bỏ qua nhất.
  Sửa: `application-test.yml` đặt `grain.auth.bootstrap-admin-username: ""`. File cấu hình theo profile ghi đè trực tiếp khóa `grain.auth.*`,
  thắng placeholder `${BOOTSTRAP_ADMIN_USERNAME:}` trong `application.yml` dù `.env` có giá trị.
  Cách tìm ra: không đoán theo log. Chạy riêng test trên DB trống rồi `select * from app_user`, thấy ngay một dòng `admin` không do test nào tạo.
- **Bài học:** một DB test dùng chung cho mọi nhánh tức là kết quả test phụ thuộc vào *nhánh nào chạy trước*. CI không gặp chuyện này
  vì mỗi lần chạy có một Postgres mới tinh. Ở máy mình, khi đổi qua lại giữa các nhánh có migration khác nhau: xóa/tạo lại `grain_warehouse_test`,
  hoặc chạy test trên container tạm như trên.
- Nó cũng báo trước một rủi ro khi merge: `feature/delivery-trip` dùng số **V28**, nhánh portal đã dùng **V29**. Nếu merge delivery-trip *sau*,
  thì trên server (đã chạy V29) Flyway sẽ từ chối V28 vì nó "đến muộn" (*out-of-order*). Cần đổi số migration của nhánh đó trước khi merge.

### Lựa chọn và phương án thay thế

- **Postgres làm service container** thay vì Testcontainers: test hiện kết nối DB qua biến môi trường, không cần thêm thư viện.
  Testcontainers đáng dùng khi muốn `mvn test` tự dựng DB ở mọi máy mà không cần chạy `docker compose` dev trước.
- **Chưa build Docker image trong CI.** Việc đó gộp vào module CD, nơi image build xong sẽ được đẩy lên GHCR. Build hai lần (một lần để kiểm, một lần để đẩy) là lãng phí.
- **GitHub Actions** thay vì GitLab CI / Jenkins: code đã ở GitHub, không phải dựng máy chủ CI riêng. Jenkins hợp khi phải chạy CI trong mạng nội bộ công ty.

### Đọc kết quả

Tab **Actions** của repo → chọn lần chạy → mỗi job có log từng step. Dấu ✅/❌ cũng hiện cạnh commit trong danh sách commit/PR.
Khi đỏ: xem step nào đỏ trước, rồi tải `surefire-reports` (backend) hoặc đọc log `npm test` (frontend).
Muốn chạy lại mà không push: nút **Re-run jobs**, hoặc **Run workflow** (nhờ `workflow_dispatch`).

### Câu hỏi ôn tập

1. Test chạy xanh trên máy bạn nhưng đỏ trên CI lúc 6h sáng, xanh lúc 9h sáng. Nghi ngờ đầu tiên là gì, và dòng nào trong `ci.yml` xử lý nó?
2. Vì sao CI dùng `npm ci` mà không dùng `npm install`?
3. Bỏ phần `options: --health-cmd ...` của service Postgres thì chuyện gì có thể xảy ra, và vì sao lỗi đó lúc có lúc không?
4. `permissions: contents: read` bảo vệ trước kịch bản tấn công nào?
5. Vì sao `npx tsc --noEmit` ở thư mục `frontend/` báo thành công dù có lỗi kiểu?
6. CI khác Continuous Delivery ở điểm nào? Module này mới làm được cái nào?
7. "Detected applied migration not resolved locally: 28" nghĩa là gì? Vì sao CI không bao giờ gặp lỗi này?
