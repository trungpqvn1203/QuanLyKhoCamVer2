package com.grainwarehouse.supplier.service;

import com.grainwarehouse.common.importing.ImportColumn;
import com.grainwarehouse.common.importing.ImportDefinition;
import com.grainwarehouse.common.importing.ImportRow;
import com.grainwarehouse.supplier.dto.CreateSupplierRequest;
import com.grainwarehouse.supplier.entity.Supplier;
import com.grainwarehouse.supplier.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** Mẫu nhập nhà cung cấp (BR-XLS). Liên kết NCC–SP không nhập qua file này — gán trên màn hình NCC. */
@Component
@RequiredArgsConstructor
public class SupplierImportDefinition implements ImportDefinition<CreateSupplierRequest, Supplier> {

    private final SupplierService supplierService;
    private final SupplierRepository supplierRepository;

    @Override
    public String templateId() {
        return "SUPPLIER_v1";
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
                ImportColumn.required("contactName", "Người liên hệ", "Tối đa 200 ký tự"),
                ImportColumn.optional("phone", "Số điện thoại", "Tối đa 20 ký tự").asText(),
                ImportColumn.optional("email", "Email", "Đúng định dạng email"),
                ImportColumn.optional("address", "Địa chỉ", "Tối đa 500 ký tự"));
    }

    @Override
    public List<String> example() {
        return List.of("Công ty TNHH Thức ăn Chăn nuôi An Phú", "Chị Lan", "0283 123 4567", "lan@anphu.vn",
                "KCN Tân Tạo, TP.HCM");
    }

    @Override
    public CreateSupplierRequest parse(ImportRow row) {
        return new CreateSupplierRequest(row.text("name"), row.text("contactName"), row.text("phone"),
                row.text("email"), row.text("address"));
    }

    @Override
    public String duplicateField() {
        return "name";
    }

    @Override
    public String duplicateKey(CreateSupplierRequest request) {
        return ImportRow.normalizeKey(request.name());
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
