package com.grainwarehouse.inventory.service;

import com.grainwarehouse.common.exception.BusinessException;
import com.grainwarehouse.inventory.entity.StockDocumentItem;
import com.grainwarehouse.inventory.entity.StockTransaction;
import com.grainwarehouse.inventory.entity.StockTransactionType;
import com.grainwarehouse.inventory.repository.InventoryRepository;
import com.grainwarehouse.inventory.repository.StockTransactionRepository;
import com.grainwarehouse.product.entity.Product;
import com.grainwarehouse.warehouse.entity.Warehouse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cửa duy nhất làm thay đổi tồn kho — dùng chung cho nhập, xuất (bán) và (sau này) điều chuyển.
 * Mỗi lần đổi tồn luôn đi kèm đúng một StockTransaction (BR-INV-04).
 * MANDATORY: bắt buộc chạy trong transaction của nghiệp vụ gọi tới (ví dụ hoàn tất phiếu) —
 * gọi ngoài transaction thì lỗi ngay, để cộng tồn của dòng 1 không thể commit riêng
 * khi dòng 2 thất bại (BR-INV-02, BR-TRF-02).
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final StockTransactionRepository stockTransactionRepository;

    /** Tăng tồn theo một dòng chứng từ (phiếu nhập hoàn tất). */
    public StockTransaction stockIn(StockDocumentItem item, Warehouse warehouse, int quantity) {
        return increase(item, warehouse, quantity, null);
    }

    /** Giảm tồn theo một dòng chứng từ (phiếu bán hoàn tất). Không đủ tồn → BusinessException (BR-INV-01). */
    public StockTransaction stockOut(StockDocumentItem item, Warehouse warehouse, int quantity) {
        return decrease(item, warehouse, quantity, null);
    }

    /** Ghi giao dịch ngược chiều với giao dịch gốc, trỏ về giao dịch gốc (BR-INV-06). */
    public StockTransaction reverse(StockTransaction original) {
        return original.getType() == StockTransactionType.IN
                ? decrease(original.getDocumentItem(), original.getWarehouse(), original.getQuantity(), original)
                : increase(original.getDocumentItem(), original.getWarehouse(), original.getQuantity(), original);
    }

    private StockTransaction increase(StockDocumentItem item, Warehouse warehouse, int quantity,
                                      StockTransaction reversalOf) {
        int after = inventoryRepository.increase(item.getProduct().getId(), warehouse.getId(), quantity);
        return stockTransactionRepository.save(new StockTransaction(
                item, warehouse, StockTransactionType.IN, quantity, after - quantity, after, reversalOf));
    }

    private StockTransaction decrease(StockDocumentItem item, Warehouse warehouse, int quantity,
                                      StockTransaction reversalOf) {
        Product product = item.getProduct();
        int after = inventoryRepository.decreaseIfEnough(product.getId(), warehouse.getId(), quantity)
                .orElseThrow(() -> insufficientStock(product, warehouse, quantity));
        return stockTransactionRepository.save(new StockTransaction(
                item, warehouse, StockTransactionType.OUT, quantity, after + quantity, after, reversalOf));
    }

    // BR-INV-01: báo rõ tồn hiện tại để người dùng biết thiếu bao nhiêu
    private BusinessException insufficientStock(Product product, Warehouse warehouse, int required) {
        int current = inventoryRepository.findQuantity(product.getId(), warehouse.getId()).orElse(0);
        return new BusinessException("Không đủ tồn: sản phẩm '" + product.getName() + "' (" + product.getCode()
                + ") tại kho '" + warehouse.getName() + "' (" + warehouse.getCode() + ") còn " + current
                + ", cần trừ " + required);
    }
}
