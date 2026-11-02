package com.grainwarehouse.portal.service;

import com.grainwarehouse.common.exception.ForbiddenOperationException;
import com.grainwarehouse.common.exception.ResourceNotFoundException;
import com.grainwarehouse.common.response.PageResponse;
import com.grainwarehouse.common.search.CommonSpecifications;
import com.grainwarehouse.inventory.entity.StockDocument;
import com.grainwarehouse.inventory.entity.StockDocumentStatus;
import com.grainwarehouse.inventory.entity.StockDocumentType;
import com.grainwarehouse.inventory.repository.StockDocumentRepository;
import com.grainwarehouse.portal.dto.PortalMeResponse;
import com.grainwarehouse.portal.dto.PortalOrderRequest;
import com.grainwarehouse.portal.dto.PortalOrderResponse;
import com.grainwarehouse.portal.dto.PortalOrderSummaryResponse;
import com.grainwarehouse.portal.dto.PortalProductResponse;
import com.grainwarehouse.portal.entity.CustomerAccount;
import com.grainwarehouse.portal.repository.CustomerAccountRepository;
import com.grainwarehouse.product.repository.ProductRepository;
import com.grainwarehouse.sales.dto.SalesOrderResponse;
import com.grainwarehouse.sales.service.SalesOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Các việc khách làm trên app (BR-POR-06..09). Hai nguyên tắc ở mọi hàm:
 * <ul>
 *   <li>customerId lấy từ token (controller truyền vào), KHÔNG BAO GIỜ từ URL / body.</li>
 *   <li>Kiểm tra lại tài khoản ở DB (khóa / khách ngừng / phải đổi mật khẩu): access token còn sống 15 phút sau khi
 *       cửa hàng khóa, kiểm ở đây thì khóa có hiệu lực ngay với việc đặt hàng.</li>
 * </ul>
 * Nghiệp vụ đơn (khóa phiếu, kiểm SP, phần khách đặt) dùng lại SalesOrderService — một chỗ cho mọi kênh.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PortalOrderService {

    private final CustomerAccountRepository accountRepository;
    private final StockDocumentRepository stockDocumentRepository;
    private final ProductRepository productRepository;
    private final SalesOrderService salesOrderService;

    public PortalMeResponse me(Long accountId) {
        return PortalMeResponse.from(activeAccount(accountId));
    }

    /** BR-POR-06: không giá, không tồn. */
    public List<PortalProductResponse> products() {
        return productRepository.findByActiveTrueOrderByNameAsc().stream().map(PortalProductResponse::from).toList();
    }

    public PageResponse<PortalOrderSummaryResponse> list(Long accountId, StockDocumentStatus status, Pageable pageable) {
        Long customerId = activeAccount(accountId).getCustomer().getId();
        Specification<StockDocument> spec = Specification.allOf(
                CommonSpecifications.equal("type", StockDocumentType.OUTBOUND),
                CommonSpecifications.equal("customer.id", customerId),
                CommonSpecifications.equal("status", status));
        return PageResponse.from(stockDocumentRepository.findAll(spec, pageable), PortalOrderSummaryResponse::from);
    }

    /** Đơn của khách khác → 404 như đơn không tồn tại. */
    public PortalOrderResponse get(Long accountId, Long orderId) {
        Long customerId = activeAccount(accountId).getCustomer().getId();
        SalesOrderResponse order;
        try {
            order = salesOrderService.get(orderId);
        } catch (ResourceNotFoundException e) {
            throw notFound(orderId);
        }
        if (!order.customerId().equals(customerId)) {
            throw notFound(orderId);
        }
        return PortalOrderResponse.from(order);
    }

    @Transactional
    public PortalOrderResponse create(Long accountId, PortalOrderRequest request) {
        Long customerId = readyAccount(accountId).getCustomer().getId();
        return PortalOrderResponse.from(salesOrderService.createAppOrder(
                customerId, request.requestedDeliveryDate(), request.note(), request.lines()));
    }

    @Transactional
    public PortalOrderResponse update(Long accountId, Long orderId, PortalOrderRequest request) {
        Long customerId = readyAccount(accountId).getCustomer().getId();
        return PortalOrderResponse.from(salesOrderService.updateAppOrder(
                customerId, orderId, request.requestedDeliveryDate(), request.note(), request.lines()));
    }

    @Transactional
    public PortalOrderResponse cancel(Long accountId, Long orderId, String note) {
        Long customerId = readyAccount(accountId).getCustomer().getId();
        return PortalOrderResponse.from(salesOrderService.cancelAppOrder(customerId, orderId, note));
    }

    /** BR-POR-10: tài khoản bị khóa / khách ngừng hoạt động → 403 ACCOUNT_DISABLED, app đăng xuất. */
    private CustomerAccount activeAccount(Long accountId) {
        CustomerAccount account = accountRepository.findWithCustomerById(accountId)
                .orElseThrow(() -> new ForbiddenOperationException("ACCOUNT_DISABLED", "Tài khoản app không còn tồn tại"));
        if (!account.isActive() || !account.getCustomer().isActive()) {
            throw new ForbiddenOperationException("ACCOUNT_DISABLED",
                    "Tài khoản app đã bị khóa. Vui lòng liên hệ cửa hàng");
        }
        return account;
    }

    /** BR-POR-03: đặt / sửa / hủy đơn chỉ sau khi khách đã đổi mật khẩu do cửa hàng cấp. */
    private CustomerAccount readyAccount(Long accountId) {
        CustomerAccount account = activeAccount(accountId);
        if (account.isMustChangePassword()) {
            throw new ForbiddenOperationException("PASSWORD_CHANGE_REQUIRED",
                    "Vui lòng đổi mật khẩu do cửa hàng cấp trước khi đặt hàng");
        }
        return account;
    }

    private static ResourceNotFoundException notFound(Long orderId) {
        return new ResourceNotFoundException("Không tìm thấy đơn hàng id=" + orderId);
    }
}
