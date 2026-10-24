package com.grainwarehouse.customer.service;

import com.grainwarehouse.common.exception.DuplicateResourceException;
import com.grainwarehouse.common.exception.InvalidRequestException;
import com.grainwarehouse.common.exception.ResourceNotFoundException;
import com.grainwarehouse.common.response.PageResponse;
import com.grainwarehouse.common.text.PhoneNumbers;
import com.grainwarehouse.customer.dto.CreateCustomerRequest;
import com.grainwarehouse.customer.dto.CustomerResponse;
import com.grainwarehouse.customer.dto.CustomerSearchCriteria;
import com.grainwarehouse.customer.dto.UpdateCustomerRequest;
import com.grainwarehouse.customer.entity.Customer;
import com.grainwarehouse.customer.entity.CustomerType;
import com.grainwarehouse.customer.repository.CustomerRepository;
import com.grainwarehouse.customer.repository.CustomerSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository customerRepository;

    public PageResponse<CustomerResponse> list(CustomerSearchCriteria criteria, Pageable pageable) {
        return PageResponse.from(
                customerRepository.findAll(CustomerSpecifications.from(criteria), pageable), CustomerResponse::from);
    }

    public CustomerResponse get(Long id) {
        return CustomerResponse.from(findOrThrow(id));
    }

    @Transactional
    public CustomerResponse create(CreateCustomerRequest request) {
        // Sau save(), code đã có giá trị nhờ @Generated (BR-CUS-02)
        return CustomerResponse.from(customerRepository.save(prepareNew(request)));
    }

    /**
     * Kiểm tra quy tắc nghiệp vụ và dựng khách hàng CHƯA lưu — dùng chung cho form tạo tay và nhập Excel,
     * để hai đường nhập không bao giờ lệch quy tắc.
     */
    public Customer prepareNew(CreateCustomerRequest request) {
        String phone = normalizePhone(request.phone());
        customerRepository.findByPhone(phone).ifPresent(this::throwDuplicate);

        Customer customer = new Customer();
        apply(customer, request.name(), request.type(), phone, request.contactName(), request.address(),
                request.note());
        return customer;
    }

    @Transactional
    public CustomerResponse update(Long id, UpdateCustomerRequest request) {
        Customer customer = findOrThrow(id);
        String phone = normalizePhone(request.phone());
        customerRepository.findByPhoneAndIdNot(phone, id).ifPresent(this::throwDuplicate);

        apply(customer, request.name(), request.type(), phone, request.contactName(), request.address(),
                request.note());
        customer.setActive(request.active());
        return CustomerResponse.from(customerRepository.saveAndFlush(customer));
    }

    /** BR-CUS-05: không xóa cứng — phiếu bán sau này vẫn tham chiếu được khách đã ngừng. */
    @Transactional
    public void deactivate(Long id) {
        findOrThrow(id).setActive(false);
    }

    private static void apply(Customer customer, String name, CustomerType type, String phone,
                              String contactName, String address, String note) {
        customer.setName(name.trim());
        customer.setType(type);
        customer.setPhone(phone);
        customer.setContactName(blankToNull(contactName));
        customer.setAddress(blankToNull(address));
        customer.setNote(blankToNull(note));
    }

    /**
     * BR-CUS-06: chuẩn hóa rồi mới kiểm định dạng và kiểm trùng.
     * Lỗi định dạng → 400 gắn vào field phone (giống lỗi Bean Validation) để form hiện đúng ô.
     */
    private static String normalizePhone(String raw) {
        String phone = PhoneNumbers.normalize(raw);
        if (!PhoneNumbers.isValid(phone)) {
            throw new InvalidRequestException("phone",
                    "Số điện thoại phải bắt đầu bằng 0 và có 10–11 chữ số (được dùng khoảng trắng, dấu chấm, gạch ngang)");
        }
        return phone;
    }

    /**
     * BR-CUS-04, BR-CUS-05: SĐT trùng (kể cả với khách đã ngừng) bị từ chối.
     * Khách trùng đang ngừng → hướng dẫn mở lại để giữ nguyên mã và lịch sử mua hàng.
     * Hai request cùng SĐT đồng thời: request sau vi phạm uk_customer_phone → GlobalExceptionHandler trả 409.
     */
    private void throwDuplicate(Customer existing) {
        String message = "Số điện thoại " + existing.getPhone() + " đã thuộc về " + label(existing);
        if (!existing.isActive()) {
            throw new DuplicateResourceException("phone", message
                    + ", khách này đang ngừng hoạt động. Hãy mở lại khách hàng thay vì tạo mới");
        }
        throw new DuplicateResourceException("phone", message);
    }

    private static String label(Customer customer) {
        return "khách hàng '" + customer.getName() + "' (" + customer.getCode() + ")";
    }

    // Trường tùy chọn: chuỗi rỗng/toàn khoảng trắng lưu thành NULL → bị bỏ khỏi JSON (NON_NULL)
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Customer findOrThrow(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khách hàng id=" + id));
    }
}
