# Xe và chuyến giao hàng — gom nhiều phiếu, một transaction

[← Mục lục kho bài học](../README.md)

## Module: Chuyến giao (2026-11-03, V31)

Bài toán: mỗi sáng chủ cửa hàng chia các phiếu bán đã chốt cho từng xe, sắp thứ tự giao theo đường mình biết, in tờ danh sách cho lái xe. Xe chạy thì hàng rời kho, và đó là lúc trừ tồn. Trước đây phải bấm Hoàn tất từng phiếu, quên một phiếu là sổ kho lệch với thực tế.

### Bảng nối thay vì thêm cột vào phiếu bán

- Cách đầu tiên ai cũng nghĩ ra là thêm `delivery_trip_id` vào `stock_document`. Nhưng bảng đó dùng chung cho phiếu nhập, tồn đầu kỳ, kiểm kê…, và **thứ tự giao** cũng phải lưu ở đâu đó. Bảng `delivery_trip_stop (trip_id, document_id, stop_order)` gom cả hai thứ, và module bán hàng không phải biết gì về chuyến.
- `UNIQUE (document_id)` = "một phiếu chỉ nằm trên một chuyến". Ví dụ: PX000123 đã ở CG000004 thì không xếp vào CG000005 được nữa.

### `DEFERRABLE` — ràng buộc kiểm lúc COMMIT

- **Tình huống:** CG000004 có điểm 1 là trại Đồng Tâm, điểm 2 là đại lý Minh Phát. Đổi chỗ hai điểm cần hai câu `UPDATE`. Sau câu đầu, cả hai dòng cùng có `stop_order = 1`.
- PostgreSQL kiểm `UNIQUE` thường **ngay sau từng dòng**, nên báo lỗi dù đến cuối transaction đã hết trùng. `UNIQUE (trip_id, stop_order) DEFERRABLE INITIALLY DEFERRED` dời việc kiểm tới `COMMIT`. Hoãn không phải bỏ: test `dbShouldRejectDuplicateStopOrderAtCheckTime` chứng minh nếu còn trùng tới lúc kiểm thì DB vẫn chặn.
- **Vì sao `UNIQUE (document_id)` không cần hoãn:** service **sửa tại chỗ** (phiếu còn giữ thì `UPDATE` vị trí, phiếu bỏ thì `DELETE`, phiếu mới thì `INSERT`), không bao giờ xóa rồi thêm lại cùng một phiếu. Nếu làm kiểu "xóa hết rồi thêm lại" thì sẽ vỡ, vì khi flush **Hibernate chạy INSERT trước DELETE**. Đây là cùng cái bẫy với `stock_document_item` ở bài [Nhập kho](01-nhap-kho.md).
- Muốn kiểm ngay trong test: `SET CONSTRAINTS ALL IMMEDIATE` (rồi đặt lại `DEFERRED` để các câu sau không bị kiểm sớm).

### Xuất chuyến = hoàn tất nhiều phiếu trong một transaction

- `DeliveryTripService.dispatch` gọi `SalesOrderService.completeForDelivery` với `Propagation.MANDATORY`. Cách làm này dùng lại **đúng** các bước kiểm tra và trừ tồn của nút Hoàn tất, không có đường trừ tồn thứ hai. Gọi hàm đó ngoài transaction thì lỗi ngay.
- **Tất cả hoặc không gì (quy tắc cứng 4):** giả sử chuyến có 2 phiếu, PX1 cần 10 bao premix, PX2 cần 60 bao cám heo. Premix trừ xong rồi mới phát hiện cám heo thiếu → `BusinessException` → rollback **cả** premix. Nếu không có rollback toàn bộ, kho đã trừ 10 bao premix mà phiếu vẫn ở Nháp.
- **Test chứng minh rollback mà không trông vào may rủi** (`dispatchShouldRollBackDeductedLinesWhenLaterLineFails`):
  1. Transaction T0 khóa dòng tồn cám heo bằng `FOR UPDATE`.
  2. Xuất chuyến trừ xong premix rồi phải **chờ** ở dòng cám heo. Test hỏi `pg_stat_activity` để biết chắc lúc nào có phiên đang chờ khóa (`wait_event_type = 'Lock'`), thay vì `sleep`.
  3. T0 hạ tồn cám heo xuống 50 rồi commit.
  4. Câu `UPDATE … WHERE quantity >= 60` của xuất chuyến được PostgreSQL đánh giá lại trên dòng mới, không còn khớp, nên báo lỗi.
  5. Kết quả: premix vẫn 1000, không có `stock_transaction` nào.

### Thứ tự khóa — tránh deadlock

- Mọi thao tác khóa theo cùng một thứ tự: **chuyến → phiếu bán (id tăng dần) → dòng tồn (SP, kho tăng dần trên cả chuyến)**. Hoàn tất một phiếu riêng lẻ khóa "SP tăng dần trong một kho", cũng là một phần của cùng thứ tự đó.
- **Ví dụ khóa chéo nếu không sắp:** chuyến A trừ cám gà rồi tới cám heo, còn phiếu B (bấm Hoàn tất riêng) trừ cám heo rồi tới cám gà. A giữ cám gà và chờ cám heo, B giữ cám heo và chờ cám gà, nên cả hai chờ nhau mãi. PostgreSQL phát hiện ra và hủy một bên (lỗi deadlock). Dữ liệu không sai, nhưng người dùng nhận lỗi khó hiểu.
- Không thao tác nào khóa phiếu rồi mới khóa chuyến, nên giữa chuyến và phiếu không thể có vòng chờ.

### Báo lỗi theo tổng chuyến — kiểm tra để báo, không phải để chặn

- Chuyến có PX1 cần 30 bao và PX2 cần 40 bao cám heo, kho còn 50. Không có bước kiểm trước thì lỗi của PX2 sẽ ghi "còn 20, cần trừ 40". Số 20 là tồn **sau khi đã trừ PX1**, và sẽ bị rollback, nên người dùng mở kho thấy 50 mà không hiểu vì sao.
- `requireEnoughStock` cộng nhu cầu theo (SP, kho) rồi báo "còn 50, chuyến cần 70 (PX1: 30, PX2: 40)". Bước này chỉ là một câu `SELECT` thường, có thể lỗi thời trong tích tắc. Chốt chặn tồn âm **thật** vẫn là `UPDATE` có điều kiện. Hai việc tách rời: báo lỗi dễ hiểu và bảo đảm đúng.

### Khóa phiếu khi xếp chuyến — đã thử gỡ khóa

- Hai người cùng xếp PX000123 vào hai chuyến mới: `lockDocuments` khóa phiếu `FOR UPDATE`, nên người sau chờ, rồi đọc lại và thấy phiếu đã có chuyến, nhận thông báo "PX000123 đã ở chuyến CG…".
- **Đã thử đổi sang `findById` (bỏ khóa):** test `concurrentAssignShouldKeepOrderOnOneTrip` fail. Dữ liệu vẫn đúng, vì `UNIQUE (document_id)` ở DB chặn được, nhưng người dùng nhận `DataIntegrityViolationException: duplicate key…` thay vì câu dễ hiểu. Bài học: **ràng buộc DB giữ dữ liệu đúng, khóa + kiểm tra ở service giữ thông báo đúng**, nên cần cả hai.

### Quyền theo hành động nghiệp vụ

- Ma trận cũ: Quản lý kho chỉ **xem** phiếu bán. Chủ cửa hàng chốt rằng người xếp xe là người cho xe chạy, nên Xuất chuyến (vốn hoàn tất phiếu bán) thuộc Quản lý kho. Quyền gắn với **URL của hành động** (`POST /api/delivery-trips/{id}/dispatch`), không gắn với bảng dữ liệu bị đổi. Vì đây là ngoại lệ có chủ đích, nó được ghi vào BR-DLV-10 và có ô test trong `AuthorizationMatrixTest`.
- `GET /api/delivery-trips/drivers` trả tên và SĐT nhân viên, nên chỉ người lập chuyến được gọi. Nhân viên bán hàng vốn không xem được màn Nhân viên, nên không được mở "cửa sau" qua endpoint này.

### Frontend: kéo thả không cần thư viện

- HTML5 drag & drop có sẵn trong trình duyệt: hàng bảng có `draggable`; `onDragStart` nhớ vị trí kéo; `onDragOver` phải gọi `preventDefault()` thì mới thả được; `onDrop` gọi `moveStop(stops, from, to)`. Cách này không chạy trên màn hình cảm ứng, nên có thêm nút ↑ ↓. Thư viện như dnd-kit chỉ cần khi muốn kéo thả trên điện thoại.
- Mọi phép trên danh sách (`moveStop`, `addStop`, `availableOrders`, `totalTons`…) là **hàm thuần** ở `trip-stops.ts` và có test riêng, nên component chỉ còn việc vẽ.
- Cộng số thực: `0.1 + 0.2 = 0.30000000000000004`, nên `totalTons` làm tròn lại về 3 chữ số lẻ. Tiền thì không bao giờ cộng bằng số thực ở frontend (quy tắc cứng 3); tấn chỉ để hiển thị nên làm tròn là đủ.

### Lỗi đã gặp

- **Regex `\b` với tiếng Việt:** định viết test "không in giá" bằng `/đ\b/`. Trong JS (không có cờ `u`), `\b` chỉ hiểu chữ ASCII. "đ" bị coi là ký tự không phải chữ, nên "điểm" cũng khớp. Đã đổi sang kiểm "Đơn giá", "Thành tiền" và mẫu số tiền `\d{1,3}(\.\d{3})+`.
- **antd `setFieldsValue` trước khi `<Form>` hiện:** form chỉ hiện sau khi tải xong, nên đặt giá trị trong `useEffect` sẽ đặt vào một form chưa gắn. Đã đổi sang `initialValues` + `key` theo `updatedAt`: lưu xong thì form dựng lại với giá trị mới và tự hết trạng thái "đã sửa".

### Câu hỏi ôn tập (để cuối dự án)

1. Vì sao `UNIQUE (trip_id, stop_order)` phải `DEFERRABLE` còn `UNIQUE (document_id)` thì không? Nếu service đổi sang "xóa hết điểm giao rồi thêm lại" thì ràng buộc nào vỡ, vì sao?
2. Xuất chuyến đã trừ premix rồi mới thiếu cám heo: lần lượt điều gì xảy ra trong DB? Nếu `completeForDelivery` dùng `REQUIRES_NEW` cho từng phiếu thì kết quả khác thế nào?
3. Vẽ thứ tự khóa của Xuất chuyến và của Hoàn tất một phiếu. Cho một ví dụ deadlock xảy ra nếu Xuất chuyến khóa dòng tồn theo thứ tự phiếu thay vì theo (SP, kho).
4. `requireEnoughStock` đọc tồn không khóa. Vì sao vậy vẫn không thể bán âm? Nó có thể báo sai theo hướng nào?
5. Quản lý kho không bấm được Hoàn tất ở màn phiếu bán nhưng Xuất chuyến được. Quyền nên gắn với dữ liệu hay với hành động? Viết test nào để ngoại lệ này không bị "sửa nhầm" sau này?
