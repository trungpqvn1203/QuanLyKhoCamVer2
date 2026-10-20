package com.grainwarehouse.sales.service;

import com.grainwarehouse.common.exception.ResourceNotFoundException;
import com.grainwarehouse.customer.repository.CustomerRepository;
import com.grainwarehouse.product.repository.ProductRepository;
import com.grainwarehouse.sales.dto.CustomerPriceResponse;
import com.grainwarehouse.sales.repository.CustomerProductPriceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Bảng giá riêng theo khách (BR-SAL-06..08). Chỉ là giá gợi ý khi lập phiếu — backend không ép giá dòng
 * phiếu phải bằng bảng giá, và đổi/xóa giá không ảnh hưởng phiếu đã lập (giá đã chụp trên dòng).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerPriceService {

    private final CustomerProductPriceRepository priceRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;

    public List<CustomerPriceResponse> list(Long customerId) {
        requireCustomer(customerId);
        return priceRepository.findByCustomerId(customerId).stream().map(CustomerPriceResponse::from).toList();
    }

    /** Đặt giá (thêm hoặc ghi đè). Trả lại cả bảng giá của khách để FE vẽ lại một lần. */
    @Transactional
    public List<CustomerPriceResponse> set(Long customerId, Long productId, BigDecimal unitPrice) {
        requireCustomer(customerId);
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Không tìm thấy sản phẩm id=" + productId);
        }
        priceRepository.upsert(customerId, productId, unitPrice);
        return list(customerId);
    }

    /** Bỏ giá riêng: SP đó quay về nhập giá tay. Chưa đặt giá → 404. */
    @Transactional
    public void remove(Long customerId, Long productId) {
        requireCustomer(customerId);
        if (priceRepository.deleteByCustomerIdAndProductId(customerId, productId) == 0) {
            throw new ResourceNotFoundException(
                    "Khách hàng id=" + customerId + " chưa có giá riêng cho sản phẩm id=" + productId);
        }
    }

    private void requireCustomer(Long customerId) {
        if (!customerRepository.existsById(customerId)) {
            throw new ResourceNotFoundException("Không tìm thấy khách hàng id=" + customerId);
        }
    }
}
