package com.grainwarehouse.inventory.service;

import com.grainwarehouse.common.exception.ResourceNotFoundException;
import com.grainwarehouse.common.response.PageResponse;
import com.grainwarehouse.inventory.dto.ProductStockDetailResponse;
import com.grainwarehouse.inventory.dto.ProductStockDetailResponse.WarehouseStock;
import com.grainwarehouse.inventory.dto.ProductStockResponse;
import com.grainwarehouse.inventory.dto.ProductStockSearchCriteria;
import com.grainwarehouse.inventory.dto.StockFigures;
import com.grainwarehouse.inventory.dto.StockStatus;
import com.grainwarehouse.inventory.entity.Inventory;
import com.grainwarehouse.inventory.entity.StockDocumentStatus;
import com.grainwarehouse.inventory.entity.StockDocumentType;
import com.grainwarehouse.inventory.repository.InventoryRepository;
import com.grainwarehouse.inventory.repository.ProductStockSpecifications;
import com.grainwarehouse.inventory.repository.QuantityByKey;
import com.grainwarehouse.inventory.repository.StockDocumentItemRepository;
import com.grainwarehouse.product.entity.Product;
import com.grainwarehouse.product.repository.ProductRepository;
import com.grainwarehouse.warehouse.entity.Warehouse;
import com.grainwarehouse.warehouse.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Tồn kho nhìn theo sản phẩm (BR-STK-01..06) — chỉ đọc.
 * Số tồn lấy từ snapshot inventory (đọc nhanh), "đang về" lấy từ dòng của phiếu nhập còn Nháp.
 * Mỗi trang danh sách chạy cố định số câu SQL (trang SP + đếm + 2 câu GROUP BY, thêm 2 nếu lọc theo kho),
 * không phụ thuộc số SP trên trang.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductStockService {

    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final InventoryRepository inventoryRepository;
    private final StockDocumentItemRepository stockDocumentItemRepository;

    public PageResponse<ProductStockResponse> list(ProductStockSearchCriteria criteria, Pageable pageable) {
        Page<Product> page = productRepository.findAll(ProductStockSpecifications.from(criteria), pageable);
        List<Long> ids = page.getContent().stream().map(Product::getId).toList();
        if (ids.isEmpty()) {
            return PageResponse.from(page, product -> null);
        }

        Map<Long, Long> totalStock = toMap(inventoryRepository.sumByProduct(ids, null));
        Map<Long, Long> totalIncoming = incomingByProduct(ids, null);
        Long warehouseId = criteria.warehouseId();
        Map<Long, Long> scopedStock = warehouseId == null ? Map.of() : toMap(inventoryRepository.sumByProduct(ids, warehouseId));
        Map<Long, Long> scopedIncoming = warehouseId == null ? Map.of() : incomingByProduct(ids, warehouseId);

        return PageResponse.from(page, product -> {
            StockFigures total = figures(product, totalStock, totalIncoming);
            StockFigures scoped = warehouseId == null ? null : figures(product, scopedStock, scopedIncoming);
            return ProductStockResponse.from(product, total, warehouseId, scoped);
        });
    }

    /** Tồn từng kho của một SP: kho có tồn > 0 hoặc có hàng đang về, sắp theo mã kho. */
    public ProductStockDetailResponse get(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm id=" + productId));

        Map<Long, Inventory> stockByWarehouse = inventoryRepository.findByProductIdWithWarehouse(productId).stream()
                .collect(Collectors.toMap(row -> row.getWarehouse().getId(), Function.identity()));
        Map<Long, Long> incomingByWarehouse = toMap(stockDocumentItemRepository.sumOrderedByWarehouse(
                productId, StockDocumentType.INBOUND, StockDocumentStatus.DRAFT));

        Set<Long> warehouseIds = new HashSet<>(incomingByWarehouse.keySet());
        stockByWarehouse.forEach((warehouseId, row) -> {
            if (row.getQuantity() > 0) {
                warehouseIds.add(warehouseId);
            }
        });
        // Kho chỉ có hàng đang về (chưa có dòng tồn) thì phải nạp riêng
        Map<Long, Warehouse> warehouses = warehouseRepository.findAllById(warehouseIds).stream()
                .collect(Collectors.toMap(Warehouse::getId, Function.identity()));

        List<WarehouseStock> rows = warehouseIds.stream()
                .map(warehouses::get)
                .sorted(Comparator.comparing(Warehouse::getCode))
                .map(warehouse -> {
                    Inventory row = stockByWarehouse.get(warehouse.getId());
                    long quantity = row == null ? 0 : row.getQuantity();
                    Instant updatedAt = row == null ? null : row.getUpdatedAt();
                    return new WarehouseStock(warehouse.getId(), warehouse.getCode(), warehouse.getName(),
                            warehouse.isActive(), quantity, StockFigures.tons(quantity, product.getNetWeightKg()),
                            incomingByWarehouse.getOrDefault(warehouse.getId(), 0L), updatedAt);
                })
                .toList();

        // Tổng tính từ mọi dòng tồn (kể cả kho ngừng, BR-STK-01) — cùng nguồn với danh sách
        long totalQuantity = stockByWarehouse.values().stream().mapToLong(Inventory::getQuantity).sum();
        long totalIncoming = incomingByWarehouse.values().stream().mapToLong(Long::longValue).sum();
        return new ProductStockDetailResponse(product.getId(), product.getCode(), product.getName(), product.getUnit(),
                product.getNetWeightKg(), product.getMinStockQuantity(), totalQuantity,
                StockFigures.tons(totalQuantity, product.getNetWeightKg()), totalIncoming,
                StockStatus.of(totalQuantity, product.getMinStockQuantity()), rows);
    }

    private Map<Long, Long> incomingByProduct(Collection<Long> productIds, Long warehouseId) {
        return toMap(stockDocumentItemRepository.sumOrderedByProduct(
                productIds, warehouseId, StockDocumentType.INBOUND, StockDocumentStatus.DRAFT));
    }

    private static StockFigures figures(Product product, Map<Long, Long> stock, Map<Long, Long> incoming) {
        return StockFigures.of(stock.getOrDefault(product.getId(), 0L), product.getNetWeightKg(),
                incoming.getOrDefault(product.getId(), 0L));
    }

    private static Map<Long, Long> toMap(List<QuantityByKey> rows) {
        return rows.stream().collect(Collectors.toMap(QuantityByKey::getId, QuantityByKey::getQuantity));
    }
}
