package com.grainwarehouse.customer.service;

import com.grainwarehouse.common.importing.ImportColumn;
import com.grainwarehouse.common.importing.ImportDefinition;
import com.grainwarehouse.common.importing.ImportRow;
import com.grainwarehouse.common.text.PhoneNumbers;
import com.grainwarehouse.customer.dto.CreateCustomerRequest;
import com.grainwarehouse.customer.entity.Customer;
import com.grainwarehouse.customer.entity.CustomerType;
import com.grainwarehouse.customer.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Mẫu nhập khách hàng (BR-XLS). Chống trùng theo SĐT đã chuẩn hóa (BR-CUS-04, BR-CUS-06). */
@Component
@RequiredArgsConstructor
public class CustomerImportDefinition implements ImportDefinition<CreateCustomerRequest, Customer> {

    // Nhãn giống giao diện (CUSTOMER_TYPE_LABELS ở frontend); LinkedHashMap giữ thứ tự trong dropdown
    private static final Map<String, CustomerType> TYPES = new LinkedHashMap<>();

    static {
        TYPES.put("Đại lý", CustomerType.DEALER);
        TYPES.put("Trang trại", CustomerType.FARM);
        TYPES.put("Bán lẻ", CustomerType.RETAIL);
    }

    private final CustomerService customerService;
    private final CustomerRepository customerRepository;

    @Override
    public String templateId() {
        return "CUSTOMER_v1";
    }

    @Override
    public String entityLabel() {
        return "khách hàng";
    }

    @Override
    public String fileName() {
        return "mau-nhap-khach-hang.xlsx";
    }

    @Override
    public List<ImportColumn> columns() {
        return List.of(
                ImportColumn.required("name", "Tên khách hàng", "Tối đa 200 ký tự, được phép trùng tên"),
                ImportColumn.required("type", "Loại khách hàng", "Đại lý, Trang trại hoặc Bán lẻ")
                        .withOptions(List.copyOf(TYPES.keySet())),
                ImportColumn.required("phone", "Số điện thoại",
                        "Bắt đầu bằng 0, 10–11 chữ số; được dùng khoảng trắng, dấu chấm, gạch ngang. Không trùng khách đã có")
                        .asText(),
                ImportColumn.optional("contactName", "Người liên hệ", "Tối đa 200 ký tự"),
                ImportColumn.optional("address", "Địa chỉ", "Tối đa 500 ký tự"),
                ImportColumn.optional("note", "Ghi chú", "Tối đa 1000 ký tự"));
    }

    @Override
    public List<String> example() {
        return List.of("Đại lý Minh Phát", "Đại lý", "0901 234 567", "Anh Phát", "Ấp 3, Xã Tân Hòa", "");
    }

    @Override
    public CreateCustomerRequest parse(ImportRow row) {
        return new CreateCustomerRequest(row.text("name"), row.option("type", TYPES), row.text("phone"),
                row.text("contactName"), row.text("address"), row.text("note"));
    }

    @Override
    public String duplicateField() {
        return "phone";
    }

    /** "0901 234 567" và "0901.234.567" là cùng một số → phải bị coi là trùng. */
    @Override
    public String duplicateKey(CreateCustomerRequest request) {
        return PhoneNumbers.normalize(request.phone());
    }

    @Override
    public Customer prepare(CreateCustomerRequest request) {
        return customerService.prepareNew(request);
    }

    @Override
    public void saveAll(List<Customer> customers) {
        customerRepository.saveAll(customers);
    }
}
