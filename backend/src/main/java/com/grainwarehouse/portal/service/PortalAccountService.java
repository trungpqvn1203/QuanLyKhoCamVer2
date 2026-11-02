package com.grainwarehouse.portal.service;

import com.grainwarehouse.auth.entity.RevokeReason;
import com.grainwarehouse.auth.service.PasswordPolicy;
import com.grainwarehouse.common.exception.BusinessException;
import com.grainwarehouse.common.exception.DuplicateResourceException;
import com.grainwarehouse.common.exception.ResourceNotFoundException;
import com.grainwarehouse.customer.entity.Customer;
import com.grainwarehouse.customer.repository.CustomerRepository;
import com.grainwarehouse.portal.dto.PortalAccountResponse;
import com.grainwarehouse.portal.entity.CustomerAccount;
import com.grainwarehouse.portal.repository.CustomerAccountRepository;
import com.grainwarehouse.portal.repository.CustomerRefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Cửa hàng quản lý tài khoản app của khách (BR-POR-02, 10): tạo, đặt lại mật khẩu, khóa / mở khóa.
 * Khóa và đặt lại mật khẩu thu hồi MỌI phiên của khách ngay; access token đã phát còn tối đa 15 phút, nhưng các
 * API đặt hàng kiểm lại tài khoản ở DB mỗi lần (PortalOrderService) nên khách bị khóa không đặt được thêm đơn nào.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PortalAccountService {

    private final CustomerAccountRepository accountRepository;
    private final CustomerRefreshTokenRepository refreshTokenRepository;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public PortalAccountResponse get(Long customerId) {
        findCustomer(customerId);
        return accountRepository.findByCustomerId(customerId)
                .map(account -> PortalAccountResponse.from(account, clock.instant()))
                .orElseGet(() -> PortalAccountResponse.none(customerId));
    }

    /** BR-POR-02: khách đang hoạt động, chưa có tài khoản. Hai người tạo cùng lúc → UNIQUE customer_id chặn người sau. */
    @Transactional
    public PortalAccountResponse create(Long customerId, String password, String createdBy) {
        Customer customer = findCustomer(customerId);
        if (!customer.isActive()) {
            throw new BusinessException("Khách hàng '" + customer.getName() + "' (" + customer.getCode()
                    + ") đang ngừng hoạt động, không tạo tài khoản app được");
        }
        if (accountRepository.findByCustomerId(customerId).isPresent()) {
            throw new DuplicateResourceException("Khách hàng " + customer.getCode() + " đã có tài khoản app");
        }
        PasswordPolicy.checkPassword("password", password);
        CustomerAccount account = accountRepository.saveAndFlush(
                new CustomerAccount(customer, passwordEncoder.encode(password), createdBy));
        return PortalAccountResponse.from(account, clock.instant());
    }

    /** BR-POR-10: khách phải đổi lại mật khẩu; mọi thiết bị đang đăng nhập bị đăng xuất. */
    @Transactional
    public PortalAccountResponse resetPassword(Long customerId, String password) {
        CustomerAccount account = lockAccount(customerId);
        PasswordPolicy.checkPassword("password", password);
        account.resetPassword(passwordEncoder.encode(password));
        return revokeAndRespond(account);
    }

    @Transactional
    public PortalAccountResponse lock(Long customerId) {
        CustomerAccount account = lockAccount(customerId);
        account.lock();
        return revokeAndRespond(account);
    }

    @Transactional
    public PortalAccountResponse unlock(Long customerId) {
        CustomerAccount account = lockAccount(customerId);
        account.unlock();
        return PortalAccountResponse.from(accountRepository.saveAndFlush(account), clock.instant());
    }

    private PortalAccountResponse revokeAndRespond(CustomerAccount account) {
        Instant now = clock.instant();
        accountRepository.saveAndFlush(account);
        Long customerId = account.getCustomer().getId();
        refreshTokenRepository.revokeAllOfAccount(account.getId(), RevokeReason.ADMIN_ACTION, now);
        // revokeAllOfAccount xóa persistence context (clearAutomatically) → đọc lại bản mới
        return PortalAccountResponse.from(accountRepository.findByCustomerId(customerId).orElseThrow(), now);
    }

    private CustomerAccount lockAccount(Long customerId) {
        findCustomer(customerId);
        return accountRepository.findForUpdateByCustomerId(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Khách hàng này chưa có tài khoản app"));
    }

    private Customer findCustomer(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khách hàng id=" + customerId));
    }
}
