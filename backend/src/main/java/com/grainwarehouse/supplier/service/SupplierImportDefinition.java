package com.grainwarehouse.supplier.service;

import com.grainwarehouse.common.importing.ImportColumn;
import com.grainwarehouse.common.importing.ImportDefinition;
import com.grainwarehouse.common.importing.ImportRow;
import com.grainwarehouse.supplier.dto.CreateSupplierRequest;
import com.grainwarehouse.supplier.entity.Supplier;
import com.grainwarehouse.supplier.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Mẫu nhập nhà cung cấp (BR-XLS). Liên kết NCC–SP không nhập qua file này — gán trên màn hình NCC. */
@Component
@RequiredArgsConstructor
public class SupplierImportDefinition implements ImportDefinition<CreateSupplierRequest, Supplier> {

    private final SupplierService supplierService;
    private final SupplierRepository supplierRepository;

    @Override
    public String templateId() {
        // v2 (2026-10-24): bỏ cột "Người liên hệ", thêm "Mã số thuế" — file mẫu v1 bị từ chối thay vì đọc lệch cột
        return "SUPPLIER_v2";
    }

    @Override
    public String entityLabel() {
        return "nhà cung cấp";
    }

    @Override
    public String fileName() {
        return "mau-nhap-nha-cung-cap.xlsx";
    }

    @Override
    public List<ImportColumn> columns() {
        return List.of(
                ImportColumn.required("name", "Tên nhà cung cấp", "Tối đa 200 ký tự, không trùng nhà cung cấp đã có"),
                // asText: MST có thể bắt đầu bằng 0 (0101234567) — ô số sẽ làm mất số 0 đầu
                ImportColumn.optional("taxCode", "Mã số thuế",
                        "10 chữ số, hoặc 10 chữ số-3 chữ số cho chi nhánh (0101234567-001). Không trùng nhà cung cấp đã có")
                        .asText(),
                ImportColumn.optional("phone", "Số điện thoại", "Tối đa 20 ký tự").asText(),
                ImportColumn.optional("email", "Email", "Đúng định dạng email"),
                ImportColumn.optional("address", "Địa chỉ", "Tối đa 500 ký tự"));
    }

    @Override
    public List<String> example() {
        return List.of("Công ty TNHH Thức ăn Chăn nuôi An Phú", "0301234567", "0283 123 4567", "lienhe@anphu.vn",
                "KCN Tân Tạo, TP.HCM");
    }

    @Override
    public CreateSupplierRequest parse(ImportRow row) {
        return new CreateSupplierRequest(row.text("name"), row.text("taxCode"), row.text("phone"),
                row.text("email"), row.text("address"));
    }

    /** Tên và MST so riêng. MST bỏ khoảng trắng như service; để trống thì không so (HashMap nhận giá trị null). */
    @Override
    public Map<String, String> duplicateKeys(CreateSupplierRequest request) {
        Map<String, String> keys = new HashMap<>();
        keys.put("name", ImportRow.normalizeKey(request.name()));
        keys.put("taxCode", request.taxCode() == null ? null : request.taxCode().replaceAll("\\s", ""));
        return keys;
    }

    @Override
    public Supplier prepare(CreateSupplierRequest request) {
        return supplierService.prepareNew(request);
    }

    @Override
    public void saveAll(List<Supplier> suppliers) {
        supplierRepository.saveAll(suppliers);
    }
}
