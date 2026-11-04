package com.grainwarehouse.portal.service;

import com.grainwarehouse.common.exception.BusinessException;
import com.grainwarehouse.common.exception.ForbiddenOperationException;
import com.grainwarehouse.common.exception.InvalidRequestException;
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

import java.time.Clock;
import java.time.LocalDate;
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
    private final Clock clock;

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

    /**
     * BR-POR-12: khóa dòng tài khoản rồi mới đếm đơn đang chờ — hai request đặt cùng lúc (bấm Gửi hai lần, hai máy)
     * chạy lần lượt, request sau đếm thấy đơn của request trước. Không khóa thì cả hai cùng thấy "4 đơn" và cùng tạo.
     */
    @Transactional
    public PortalOrderResponse create(Long accountId, PortalOrderRequest request) {
        requireDeliveryDateInRange(request.requestedDeliveryDate());
        CustomerAccount account = accountRepository.findForUpdateWithCustomerById(accountId)
                .orElseThrow(PortalOrderService::accountGone);
        Long customerId = requireReady(account).getCustomer().getId();
        if (stockDocumentRepository.countAppOrders(customerId, StockDocumentStatus.PENDING)
                >= PortalOrderLimits.MAX_PENDING_ORDERS) {
            throw new BusinessException("Bạn đang có " + PortalOrderLimits.MAX_PENDING_ORDERS
                    + " đơn chờ cửa hàng xác nhận. Vui lòng sửa đơn đang chờ hoặc đợi cửa hàng xác nhận rồi đặt tiếp");
        }
        return PortalOrderResponse.from(salesOrderService.createAppOrder(
                customerId, request.requestedDeliveryDate(), request.note(), request.saleRequestLines()));
    }

    @Transactional
    public PortalOrderResponse update(Long accountId, Long orderId, PortalOrderRequest request) {
        requireDeliveryDateInRange(request.requestedDeliveryDate());
        Long customerId = readyAccount(accountId).getCustomer().getId();
        return PortalOrderResponse.from(salesOrderService.updateAppOrder(
                customerId, orderId, request.requestedDeliveryDate(), request.note(), request.saleRequestLines()));
    }

    @Transactional
    public PortalOrderResponse cancel(Long accountId, Long orderId, String note) {
        Long customerId = readyAccount(accountId).getCustomer().getId();
        return PortalOrderResponse.from(salesOrderService.cancelAppOrder(customerId, orderId, note));
    }

    /** BR-POR-10: tài khoản bị khóa / khách ngừng hoạt động → 403 ACCOUNT_DISABLED, app đăng xuất. */
    private CustomerAccount activeAccount(Long accountId) {
        return requireActive(accountRepository.findWithCustomerById(accountId).orElseThrow(PortalOrderService::accountGone));
    }

    private static CustomerAccount requireActive(CustomerAccount account) {
        if (!account.isActive() || !account.getCustomer().isActive()) {
            throw new ForbiddenOperationException("ACCOUNT_DISABLED",
                    "Tài khoản app đã bị khóa. Vui lòng liên hệ cửa hàng");
        }
        return account;
    }

    /** BR-POR-03: đặt / sửa / hủy đơn chỉ sau khi khách đã đổi mật khẩu do cửa hàng cấp. */
    private CustomerAccount readyAccount(Long accountId) {
        return requireReady(activeAccount(accountId));
    }

    private static CustomerAccount requireReady(CustomerAccount account) {
        requireActive(account);
        if (account.isMustChangePassword()) {
            throw new ForbiddenOperationException("PASSWORD_CHANGE_REQUIRED",
                    "Vui lòng đổi mật khẩu do cửa hàng cấp trước khi đặt hàng");
        }
        return account;
    }

    /** BR-POR-12: ngày muốn giao từ hôm nay (giờ VN) đến tối đa 60 ngày tới; không hẹn ngày thì bỏ trống. */
    private void requireDeliveryDateInRange(LocalDate date) {
        if (date == null) {
            return;
        }
        LocalDate today = LocalDate.ofInstant(clock.instant(), CommonSpecifications.BUSINESS_ZONE);
        if (date.isBefore(today)) {
            throw new InvalidRequestException("requestedDeliveryDate", "Ngày muốn giao không được trước hôm nay");
        }
        if (date.isAfter(today.plusDays(PortalOrderLimits.MAX_DELIVERY_DAYS_AHEAD))) {
            throw new InvalidRequestException("requestedDeliveryDate",
                    "Chỉ đặt trước tối đa " + PortalOrderLimits.MAX_DELIVERY_DAYS_AHEAD + " ngày");
        }
    }

    private static ForbiddenOperationException accountGone() {
        return new ForbiddenOperationException("ACCOUNT_DISABLED", "Tài khoản app không còn tồn tại");
    }

    private static ResourceNotFoundException notFound(Long orderId) {
        return new ResourceNotFoundException("Không tìm thấy đơn hàng id=" + orderId);
    }
}
